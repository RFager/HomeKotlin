package io.homekotlin.ha

import kotlinx.serialization.Serializable

@Serializable
data class TriggerTypeInfo(
    val type: String,
    val displayName: String,
    val description: String,
    val exampleDsl: String,
    val parameters: Map<String, String> = emptyMap()
)
