package io.homekotlin.ha

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class HaServiceField(
    val name: String? = null,
    val description: String? = null,
    val example: JsonElement? = null,
    val required: Boolean = false
)
