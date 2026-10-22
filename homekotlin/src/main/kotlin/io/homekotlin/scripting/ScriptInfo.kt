package io.homekotlin.scripting

import kotlinx.serialization.Serializable

@Serializable
data class ScriptInfo(
    val filename: String,
    val path: String,
    val status: ScriptStatus,
    val automationCount: Int = 0,
    val error: String? = null,
    val diagnostics: List<ScriptDiagnostic> = emptyList(),
    val lastLoadedAt: Long = System.currentTimeMillis()
)
