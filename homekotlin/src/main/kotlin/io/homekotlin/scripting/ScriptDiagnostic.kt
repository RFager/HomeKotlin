package io.homekotlin.scripting

import kotlinx.serialization.Serializable

@Serializable
data class ScriptDiagnostic(
    val line: Int?,
    val column: Int?,
    val message: String,
    val severity: String
)
