package io.homekotlin.ha

import kotlinx.serialization.Serializable

@Serializable
data class StateChangeEvent(
    val entityId: String,
    val oldState: HaState?,
    val newState: HaState?,
    val timestamp: Long = System.currentTimeMillis()
)
