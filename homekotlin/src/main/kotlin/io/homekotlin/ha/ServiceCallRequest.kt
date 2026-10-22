package io.homekotlin.ha

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class ServiceCallRequest(
    val domain: String,
    val service: String,
    val target: Map<String, JsonElement>? = null,
    @SerialName("service_data") val serviceData: Map<String, JsonElement> = emptyMap()
)
