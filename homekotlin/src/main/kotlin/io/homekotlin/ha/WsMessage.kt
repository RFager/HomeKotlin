package io.homekotlin.ha

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

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
