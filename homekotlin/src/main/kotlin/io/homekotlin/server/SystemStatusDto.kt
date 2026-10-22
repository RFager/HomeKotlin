package io.homekotlin.server

import kotlinx.serialization.Serializable

@Serializable
data class SystemStatusDto(
    val connectionState: String,
    val haUrl: String,
    val isDemoMode: Boolean,
    val activeAutomations: Int,
    val totalEntities: Int,
    val memoryUsageMb: Long,
    val uptimeSeconds: Long,
    val isSupervisor: Boolean
)
