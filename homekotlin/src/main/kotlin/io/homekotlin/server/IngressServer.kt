package io.homekotlin.server

import io.homekotlin.codegen.EntityCodeGenerator
import io.homekotlin.config.AppConfig
import io.homekotlin.ha.HaClient
import io.homekotlin.ha.HaStateStore
import io.homekotlin.scripting.AutomationRegistry
import io.homekotlin.scripting.ScriptEngine
import io.homekotlin.dsl.TriggerCatalog
import io.homekotlin.ha.ActiveTriggerInfo
import io.homekotlin.ha.TriggersCatalogDto
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sse.*
import io.ktor.sse.*
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.slf4j.LoggerFactory
import java.lang.management.ManagementFactory

@Serializable
data class AutomationDto(
    val id: String,
    val name: String,
    val description: String,
    val sourceFile: String?,
    val triggers: List<String>,
    val conditionsCount: Int,
    val enabled: Boolean,
    val lastTriggered: Long?,
    val lastTriggerSource: String?,
    val executionCount: Long,
    val lastDurationMs: Long?,
    val lastError: String?
)

@Serializable
data class SystemStatusDto(
    val connectionState: String,
    val haUrl: String,
    val isDemoMode: Boolean,
    val activeAutomations: Int,
    val totalEntities: Int,
    val memoryUsageMb: Long,
    val uptimeSeconds: Long,
    val isSupervisor: Boolean
)

class IngressServer(
    private val config: AppConfig,
    private val haClient: HaClient,
    private val stateStore: HaStateStore,
    private val registry: AutomationRegistry,
    private val scriptEngine: ScriptEngine,
    private val codeGenerator: EntityCodeGenerator
) {
    private val logger = LoggerFactory.getLogger(IngressServer::class.java)
    private val startTime = System.currentTimeMillis()
    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    private var server: EmbeddedServer<NettyApplicationEngine, NettyApplicationEngine.Configuration>? = null

    fun start() {
        server = embeddedServer(Netty, port = config.port, host = "0.0.0.0") {
            install(ContentNegotiation) {
                json(json)
            }
            install(CORS) {
                anyHost()
                allowHeader(HttpHeaders.ContentType)
                allowHeader(HttpHeaders.Authorization)
                allowHeader("X-Ingress-Path")
                allowMethod(HttpMethod.Options)
                allowMethod(HttpMethod.Put)
                allowMethod(HttpMethod.Post)
                allowMethod(HttpMethod.Get)
                allowMethod(HttpMethod.Delete)
            }
            install(SSE)

            routing {
                // Serve embedded single-page dashboard HTML/CSS/JS
                get("/") {
                    val html = javaClass.getResourceAsStream("/web/index.html")?.bufferedReader()?.readText()
                    if (html != null) {
                        call.respondText(html, ContentType.Text.Html)
                    } else {
                        call.respondText("HomeKotlin Web UI not found", ContentType.Text.Plain)
                    }
                }

                get("/app.css") {
                    val css = javaClass.getResourceAsStream("/web/app.css")?.bufferedReader()?.readText() ?: ""
                    call.respondText(css, ContentType.Text.CSS)
                }

                get("/app.js") {
                    val js = javaClass.getResourceAsStream("/web/app.js")?.bufferedReader()?.readText() ?: ""
                    call.respondText(js, ContentType.Text.JavaScript)
                }

                route("/api") {
                    get("/status") {
                        val runtime = Runtime.getRuntime()
                        val usedMemory = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)
                        val uptimeSec = (System.currentTimeMillis() - startTime) / 1000

                        val status = SystemStatusDto(
                            connectionState = haClient.status.value.state.name,
                            haUrl = config.haUrl,
                            isDemoMode = config.demoMode,
                            activeAutomations = registry.count(),
                            totalEntities = stateStore.count(),
                            memoryUsageMb = usedMemory,
                            uptimeSeconds = uptimeSec,
                            isSupervisor = config.isSupervisor
                        )
                        call.respond(status)
                    }

                    get("/automations") {
                        val list = registry.all().map { a ->
                            AutomationDto(
                                id = a.id,
                                name = a.name,
                                description = a.description,
                                sourceFile = a.sourceFile,
                                triggers = a.triggers.map { it.description },
                                conditionsCount = a.conditions.size,
                                enabled = a.enabled,
                                lastTriggered = a.lastTriggered,
                                lastTriggerSource = a.lastTriggerSource,
                                executionCount = a.executionCount,
                                lastDurationMs = a.lastDurationMs,
                                lastError = a.lastError
                            )
                        }
                        call.respond(list)
                    }

                    post("/automations/{id}/toggle") {
                        val id = call.parameters["id"] ?: return@post call.respond(HttpStatusCode.BadRequest)
                        val newState = registry.toggle(id)
                        if (newState != null) {
                            call.respond(mapOf("id" to id, "enabled" to newState))
                        } else {
                            call.respond(HttpStatusCode.NotFound, mapOf("error" to "Automation not found"))
                        }
                    }

                    post("/automations/{id}/trigger") {
                        val id = call.parameters["id"] ?: return@post call.respond(HttpStatusCode.BadRequest)
                        val a = registry.get(id) ?: return@post call.respond(HttpStatusCode.NotFound)

                        val result = registry.executeAutomation(
                            automation = a,
                            trigger = io.homekotlin.dsl.ManualTrigger("Web Dashboard"),
                            triggerEvent = null,
                            stateStore = stateStore,
                            services = io.homekotlin.dsl.HaServices(haClient)
                        )
                        call.respond(result)
                    }

                    get("/entities") {
                        val query = call.request.queryParameters["q"] ?: ""
                        val domain = call.request.queryParameters["domain"]

                        val entities = if (!domain.isNullOrBlank()) {
                            stateStore.filterByDomain(domain)
                        } else {
                            stateStore.search(query)
                        }
                        call.respond(entities)
                    }

                    get("/scripts") {
                        call.respond(scriptEngine.allScripts())
                    }

                    post("/scripts/reload") {
                        scriptEngine.loadAll()
                        call.respond(mapOf("status" to "reloaded", "count" to scriptEngine.allScripts().size))
                    }

                    post("/codegen") {
                        val code = codeGenerator.generateCode()
                        call.respond(mapOf("code" to code))
                    }

                    get("/history") {
                        call.respond(registry.history())
                    }

                    get("/services") {
                        val domain = call.request.queryParameters["domain"]
                        val query = call.request.queryParameters["q"]?.lowercase()

                        var services = haClient.getServices()
                        if (!domain.isNullOrBlank()) {
                            services = services.filter { it.domain.equals(domain, ignoreCase = true) }
                        }
                        if (!query.isNullOrBlank()) {
                            services = services.filter {
                                it.serviceId.lowercase().contains(query) ||
                                (it.name?.lowercase()?.contains(query) == true) ||
                                (it.description?.lowercase()?.contains(query) == true)
                            }
                        }
                        call.respond(services)
                    }

                    get("/triggers") {
                        val supported = TriggerCatalog.supportedTypes()
                        val active = registry.all().flatMap { auto ->
                            auto.triggers.map { t ->
                                ActiveTriggerInfo(
                                    automationId = auto.id,
                                    automationName = auto.name,
                                    triggerType = t::class.simpleName ?: "Trigger",
                                    description = t.description,
                                    enabled = auto.enabled
                                )
                            }
                        }

                        val dto = TriggersCatalogDto(
                            supportedTypes = supported,
                            activeTriggers = active,
                            totalActive = active.size,
                            totalEntitiesAvailableForStateTriggers = stateStore.count()
                        )
                        call.respond(dto)
                    }

                    get("/devices/{deviceId}/triggers") {
                        val deviceId = call.parameters["deviceId"] ?: return@get call.respond(HttpStatusCode.BadRequest)
                        val triggers = haClient.getDeviceTriggers(deviceId)
                        call.respond(triggers)
                    }

                    // Real-time Server-Sent Events stream for live dashboard updates
                    sse("/stream") {
                        val clientScope = this

                        // Stream HA state changes
                        val stateJob = clientScope.launch {
                            stateStore.events.collect { event ->
                                send(
                                    ServerSentEvent(
                                        event = "ha_event",
                                        data = json.encodeToString(event)
                                    )
                                )
                            }
                        }

                        // Stream automation execution logs
                        val execJob = clientScope.launch {
                            registry.executionEvents.collect { logEntry ->
                                send(
                                    ServerSentEvent(
                                        event = "execution",
                                        data = json.encodeToString(logEntry)
                                    )
                                )
                            }
                        }

                        // Stream connection state updates
                        val statusJob = clientScope.launch {
                            haClient.status.collect { status ->
                                send(
                                    ServerSentEvent(
                                        event = "status",
                                        data = buildJsonObject {
                                            put("state", status.state.name)
                                            put("url", status.url)
                                            put("reconnectAttempts", status.reconnectAttempts)
                                            put("isDemoMode", status.isDemoMode)
                                        }.toString()
                                    )
                                )
                            }
                        }

                        stateJob.join()
                        execJob.join()
                        statusJob.join()
                    }
                }
            }
        }.start(wait = false)

        logger.info("HomeKotlin Ingress Web Server listening on http://0.0.0.0:${config.port}")
    }

    fun stop() {
        server?.stop(1000, 2000)
    }
}
