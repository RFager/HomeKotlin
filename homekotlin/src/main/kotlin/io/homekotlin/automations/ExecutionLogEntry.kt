package io.homekotlin.automations

import kotlinx.serialization.Serializable

@Serializable
data class ExecutionLogEntry(
    val id: String = java.util.UUID.randomUUID().toString(),
    val automationId: String,
    val automationName: String,
    val timestamp: Long = System.currentTimeMillis(),
    val triggerSource: String,
    val durationMs: Long,
    val success: Boolean,
    val error: String? = null
)
