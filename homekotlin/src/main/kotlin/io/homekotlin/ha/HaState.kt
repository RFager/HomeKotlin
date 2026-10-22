package io.homekotlin.ha

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

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
