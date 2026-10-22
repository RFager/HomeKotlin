package io.homekotlin.ha

import kotlinx.serialization.Serializable

@Serializable
data class HaServiceInfo(
    val domain: String,
    val service: String,
    val name: String? = null,
    val description: String? = null,
    val fields: Map<String, HaServiceField> = emptyMap(),
    val target: HaServiceTarget? = null
) {
    val serviceId: String get() = "$domain.$service"
}
