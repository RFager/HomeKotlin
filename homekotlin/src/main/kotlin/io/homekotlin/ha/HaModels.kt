package io.homekotlin.ha

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

enum class ConnectionState {
    DISCONNECTED,
    CONNECTING,
    AUTHENTICATING,
    CONNECTED,
    RECONNECTING,
    ERROR
}

data class ConnectionStatus(
    val state: ConnectionState = ConnectionState.DISCONNECTED,
    val url: String = "",
    val connectedAt: Long? = null,
    val reconnectAttempts: Int = 0,
    val lastError: String? = null,
    val isDemoMode: Boolean = false
)

@Serializable
data class HaState(
    @SerialName("entity_id") val entityId: String,
    val state: String,
    val attributes: Map<String, JsonElement> = emptyMap(),
    @SerialName("last_changed") val lastChanged: String? = null,
    @SerialName("last_updated") val lastUpdated: String? = null
) {
    val friendlyName: String
        get() = attributes["friendly_name"]?.jsonPrimitive?.content ?: entityId

    val domain: String
        get() = entityId.substringBefore('.', "")

    val objectId: String
        get() = entityId.substringAfter('.', "")

    val isOn: Boolean
        get() = state.equals("on", ignoreCase = true)

    val isOff: Boolean
        get() = state.equals("off", ignoreCase = true)

    val isUnavailable: Boolean
        get() = state.equals("unavailable", ignoreCase = true) || state.equals("unknown", ignoreCase = true)

    fun doubleValue(): Double? = state.toDoubleOrNull()
    fun intValue(): Int? = state.toIntOrNull()
    fun booleanValue(): Boolean? = when (state.lowercase()) {
        "on", "true", "yes", "1" -> true
        "off", "false", "no", "0" -> false
        else -> null
    }

    fun getAttrString(key: String): String? = attributes[key]?.jsonPrimitive?.content
    fun getAttrDouble(key: String): Double? = attributes[key]?.jsonPrimitive?.doubleOrNull
    fun getAttrInt(key: String): Int? = attributes[key]?.jsonPrimitive?.intOrNull
    fun getAttrBoolean(key: String): Boolean? = attributes[key]?.jsonPrimitive?.booleanOrNull
}

@Serializable
data class StateChangeEvent(
    val entityId: String,
    val oldState: HaState?,
    val newState: HaState?,
    val timestamp: Long = System.currentTimeMillis()
)

@Serializable
data class ServiceCallRequest(
    val domain: String,
    val service: String,
    val target: Map<String, JsonElement>? = null,
    @SerialName("service_data") val serviceData: Map<String, JsonElement> = emptyMap()
)

// Internal Home Assistant WebSocket Message DTOs
@Serializable
data class WsMessage(
    val id: Long? = null,
    val type: String,
    val message: String? = null,
    val success: Boolean? = null,
    @SerialName("access_token") val accessToken: String? = null,
    @SerialName("event_type") val eventType: String? = null,
    val domain: String? = null,
    val service: String? = null,
    @SerialName("service_data") val serviceData: Map<String, JsonElement>? = null,
    val target: Map<String, JsonElement>? = null,
    val result: JsonElement? = null,
    val event: WsEventContainer? = null
)

@Serializable
data class WsEventContainer(
    @SerialName("event_type") val eventType: String,
    val data: WsEventData? = null,
    @SerialName("time_fired") val timeFired: String? = null
)

@Serializable
data class WsEventData(
    @SerialName("entity_id") val entityId: String? = null,
    @SerialName("old_state") val oldState: HaState? = null,
    @SerialName("new_state") val newState: HaState? = null
)
