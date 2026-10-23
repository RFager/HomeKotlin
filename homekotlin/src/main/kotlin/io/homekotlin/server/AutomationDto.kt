package io.homekotlin.server

import kotlinx.serialization.Serializable

@Serializable
data class AutomationDto(
    val id: String,
    val name: String,
    val description: String,
    val sourceFile: String?,
    val mode: String,
    val modeTitle: String,
    val modeDescription: String,
    val maxRuns: Int,
    val runningCount: Int,
    val triggers: List<String>,
    val conditionsCount: Int,
    val enabled: Boolean,
    val lastTriggered: Long?,
    val lastTriggerSource: String?,
    val executionCount: Long,
    val lastDurationMs: Long?,
    val lastError: String?
)
