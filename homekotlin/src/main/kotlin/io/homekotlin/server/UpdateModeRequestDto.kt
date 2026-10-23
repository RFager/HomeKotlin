package io.homekotlin.server

import kotlinx.serialization.Serializable

@Serializable
data class UpdateModeRequestDto(
    val mode: String,
    val maxRuns: Int? = null
)
