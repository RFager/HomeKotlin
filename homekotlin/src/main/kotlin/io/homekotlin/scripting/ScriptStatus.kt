package io.homekotlin.scripting

import kotlinx.serialization.Serializable

@Serializable
enum class ScriptStatus {
    LOADED,
    ERROR,
    EMPTY
}
