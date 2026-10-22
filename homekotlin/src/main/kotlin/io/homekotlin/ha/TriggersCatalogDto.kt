package io.homekotlin.ha

import kotlinx.serialization.Serializable

@Serializable
data class TriggersCatalogDto(
    val supportedTypes: List<TriggerTypeInfo>,
    val activeTriggers: List<ActiveTriggerInfo>,
    val totalActive: Int,
    val totalEntitiesAvailableForStateTriggers: Int
)
