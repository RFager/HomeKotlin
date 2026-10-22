package io.homekotlin.ha

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class HaServiceTarget(
    val entity: JsonElement? = null,
    val device: JsonElement? = null,
    val area: JsonElement? = null
)
