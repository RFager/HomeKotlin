package io.homekotlin.ha

import io.homekotlin.config.AppConfig
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.random.Random

class HaClient(
    val config: AppConfig,
    val stateStore: HaStateStore
) {
    private val logger = LoggerFactory.getLogger(HaClient::class.java)
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    private val idCounter = AtomicLong(1)
    private val pendingResponses = ConcurrentHashMap<Long, CompletableDeferred<JsonElement>>()

    private val _status = MutableStateFlow(
        ConnectionStatus(
            state = if (config.demoMode) ConnectionState.CONNECTED else ConnectionState.DISCONNECTED,
            url = config.haUrl,
            isDemoMode = config.demoMode
        )
    )
    val status: StateFlow<ConnectionStatus> = _status.asStateFlow()

    private var clientJob: Job? = null
    private var session: DefaultClientWebSocketSession? = null

    fun start(scope: CoroutineScope) {
        if (config.demoMode) {
            logger.info("Starting in DEMO / SIMULATION mode (no live Home Assistant connection configured)")
            initDemoStates(scope)
            return
        }

        clientJob = scope.launch {
            var attempt = 0
            while (isActive) {
                try {
                    attempt++
                    _status.value = _status.value.copy(
                        state = if (attempt > 1) ConnectionState.RECONNECTING else ConnectionState.CONNECTING,
                        reconnectAttempts = attempt
                    )
                    connectAndListen()
                    attempt = 0
                } catch (e: Exception) {
                    logger.warn("HA connection failed: ${e.message}. Reconnecting in 5s...")
                    _status.value = _status.value.copy(
                        state = ConnectionState.ERROR,
                        lastError = e.message
                    )
                    delay(5000)
                }
            }
        }
    }

    private suspend fun connectAndListen() {
        val client = HttpClient(CIO) {
            install(WebSockets) {
                pingIntervalMillis = 20_000
            }
        }

        val url = config.haUrl
        logger.info("Connecting to Home Assistant WebSocket at $url ...")

        client.webSocket(urlString = url) {
            session = this
            var authenticated = false

            for (frame in incoming) {
                if (frame !is Frame.Text) continue
                val text = frame.readText()
                val element = json.parseToJsonElement(text).jsonObject

                val type = element["type"]?.jsonPrimitive?.content

                when (type) {
                    "auth_required" -> {
                        _status.value = _status.value.copy(state = ConnectionState.AUTHENTICATING)
                        val authPayload = json.encodeToString(
                            buildJsonObject {
                                put("type", "auth")
                                put("access_token", config.haToken)
                            }
                        )
                        send(Frame.Text(authPayload))
                    }

                    "auth_ok" -> {
                        authenticated = true
                        _status.value = _status.value.copy(
                            state = ConnectionState.CONNECTED,
                            connectedAt = System.currentTimeMillis(),
                            lastError = null
                        )
                        logger.info("Successfully authenticated with Home Assistant!")

                        // Subscribe to state change events
                        val subId = idCounter.getAndIncrement()
                        val subMsg = buildJsonObject {
                            put("id", subId)
                            put("type", "subscribe_events")
                            put("event_type", "state_changed")
                        }
                        send(Frame.Text(json.encodeToString(subMsg)))

                        // Request all current entity states
                        val statesId = idCounter.getAndIncrement()
                        val statesMsg = buildJsonObject {
                            put("id", statesId)
                            put("type", "get_states")
                        }
                        send(Frame.Text(json.encodeToString(statesMsg)))
                    }

                    "auth_invalid" -> {
                        val msg = element["message"]?.jsonPrimitive?.content ?: "Invalid authentication token"
                        _status.value = _status.value.copy(
                            state = ConnectionState.ERROR,
                            lastError = msg
                        )
                        logger.error("Authentication rejected by Home Assistant: $msg")
                        return@webSocket
                    }

                    "result" -> {
                        val id = element["id"]?.jsonPrimitive?.longOrNull
                        val success = element["success"]?.jsonPrimitive?.booleanOrNull ?: false
                        val resultData = element["result"]

                        if (id != null) {
                            pendingResponses.remove(id)?.complete(resultData ?: JsonNull)

                            // Check if this result is the initial get_states dump
                            if (success && resultData is JsonArray) {
                                try {
                                    val statesList = json.decodeFromJsonElement<List<HaState>>(resultData)
                                    stateStore.bulkUpdate(statesList)
                                    logger.info("Loaded ${statesList.size} entities from Home Assistant")
                                } catch (e: Exception) {
                                    logger.error("Failed to parse get_states result: ${e.message}")
                                }
                            }
                        }
                    }

                    "event" -> {
                        val eventContainer = element["event"]?.jsonObject
                        val eventType = eventContainer?.get("event_type")?.jsonPrimitive?.content
                        if (eventType == "state_changed") {
                            val data = eventContainer["data"]?.jsonObject
                            val newStateJson = data?.get("new_state")
                            if (newStateJson != null && newStateJson !is JsonNull) {
                                try {
                                    val newState = json.decodeFromJsonElement<HaState>(newStateJson)
                                    stateStore.updateState(newState)
                                } catch (e: Exception) {
                                    logger.warn("Failed to decode state_changed event: ${e.message}")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    suspend fun callService(
        domain: String,
        service: String,
        serviceData: Map<String, Any?> = emptyMap(),
        target: Map<String, Any?>? = null
    ): Boolean {
        if (config.demoMode) {
            logger.info("[DEMO CALL_SERVICE] $domain.$service -> data: $serviceData, target: $target")
            simulateServiceCall(domain, service, serviceData, target)
            return true
        }

        val s = session ?: run {
            logger.warn("Cannot call service $domain.$service: WebSocket session is not active")
            return false
        }

        val reqId = idCounter.getAndIncrement()
        val deferred = CompletableDeferred<JsonElement>()
        pendingResponses[reqId] = deferred

        val jsonServiceData = buildJsonObject {
            serviceData.forEach { (k, v) ->
                when (v) {
                    is Number -> put(k, v)
                    is Boolean -> put(k, v)
                    is String -> put(k, v)
                    else -> put(k, JsonPrimitive(v?.toString()))
                }
            }
        }

        val msg = buildJsonObject {
            put("id", reqId)
            put("type", "call_service")
            put("domain", domain)
            put("service", service)
            put("service_data", jsonServiceData)
            if (target != null) {
                put("target", buildJsonObject {
                    target.forEach { (k, v) ->
                        when (v) {
                            is List<*> -> put(k, buildJsonArray {
                                v.forEach { item -> add(JsonPrimitive(item?.toString())) }
                            })
                            else -> put(k, JsonPrimitive(v?.toString()))
                        }
                    }
                })
            }
        }

        return try {
            s.send(Frame.Text(json.encodeToString(msg)))
            withTimeout(10_000) {
                deferred.await()
            }
            true
        } catch (e: Exception) {
            logger.error("Service call $domain.$service failed or timed out: ${e.message}")
            false
        } finally {
            pendingResponses.remove(reqId)
        }
    }

    private fun initDemoStates(scope: CoroutineScope) {
        val initialEntities = listOf(
            HaState("sun.sun", "below_horizon", mapOf("friendly_name" to JsonPrimitive("Sun"))),
            HaState("light.living_room", "off", mapOf("friendly_name" to JsonPrimitive("Living Room Light"), "brightness" to JsonPrimitive(0))),
            HaState("light.porch", "off", mapOf("friendly_name" to JsonPrimitive("Porch Lights"), "brightness" to JsonPrimitive(0))),
            HaState("light.kitchen", "on", mapOf("friendly_name" to JsonPrimitive("Kitchen Ceiling"), "brightness" to JsonPrimitive(255))),
            HaState("switch.coffee_maker", "off", mapOf("friendly_name" to JsonPrimitive("Espresso Machine"))),
            HaState("binary_sensor.front_door", "off", mapOf("friendly_name" to JsonPrimitive("Front Door Sensor"), "device_class" to JsonPrimitive("door"))),
            HaState("binary_sensor.living_room_motion", "off", mapOf("friendly_name" to JsonPrimitive("Living Room Motion"), "device_class" to JsonPrimitive("motion"))),
            HaState("sensor.living_room_temperature", "21.5", mapOf("friendly_name" to JsonPrimitive("Living Room Temperature"), "unit_of_measurement" to JsonPrimitive("°C"))),
            HaState("sensor.outdoor_temperature", "14.2", mapOf("friendly_name" to JsonPrimitive("Outdoor Temperature"), "unit_of_measurement" to JsonPrimitive("°C"))),
            HaState("climate.living_room", "heat", mapOf("friendly_name" to JsonPrimitive("Living Room Thermostat"), "current_temperature" to JsonPrimitive(21.5), "temperature" to JsonPrimitive(22.0)))
        )
        stateStore.bulkUpdate(initialEntities)

        // Periodic simulation for demo/testing mode
        scope.launch {
            while (isActive) {
                delay(12_000)
                // Randomly toggle motion sensor or adjust temperature
                if (Random.nextBoolean()) {
                    val currentMotion = stateStore["binary_sensor.living_room_motion"]?.state == "on"
                    val newMotion = if (currentMotion) "off" else "on"
                    stateStore.updateState(
                        HaState("binary_sensor.living_room_motion", newMotion, mapOf("friendly_name" to JsonPrimitive("Living Room Motion"), "device_class" to JsonPrimitive("motion")))
                    )
                } else {
                    val currentTemp = stateStore["sensor.living_room_temperature"]?.doubleValue() ?: 21.5
                    val delta = (Random.nextInt(-3, 4) * 0.1)
                    val newTemp = String.format("%.1f", currentTemp + delta).replace(',', '.')
                    stateStore.updateState(
                        HaState("sensor.living_room_temperature", newTemp, mapOf("friendly_name" to JsonPrimitive("Living Room Temperature"), "unit_of_measurement" to JsonPrimitive("°C")))
                    )
                }
            }
        }
    }

    private suspend fun simulateServiceCall(
        domain: String,
        service: String,
        serviceData: Map<String, Any?>,
        target: Map<String, Any?>?
    ) {
        val entityId = (serviceData["entity_id"] as? String)
            ?: (target?.get("entity_id") as? String)
            ?: return

        val existing = stateStore[entityId] ?: return
        val newStateStr = when (service) {
            "turn_on" -> "on"
            "turn_off" -> "off"
            "toggle" -> if (existing.isOn) "off" else "on"
            else -> existing.state
        }

        val updatedAttrs = existing.attributes.toMutableMap()
        if (serviceData.containsKey("brightness")) {
            updatedAttrs["brightness"] = JsonPrimitive(serviceData["brightness"]?.toString()?.toIntOrNull() ?: 255)
        }

        stateStore.updateState(existing.copy(state = newStateStr, attributes = updatedAttrs))
    }
}
