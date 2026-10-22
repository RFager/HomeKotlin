package io.homekotlin.ha

import kotlinx.serialization.Serializable

@Serializable
data class ActiveTriggerInfo(
    val automationId: String,
    val automationName: String,
    val triggerType: String,
    val description: String,
    val enabled: Boolean
)
