package io.homekotlin.ha

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class WsEventData(
    @SerialName("entity_id") val entityId: String? = null,
    @SerialName("old_state") val oldState: HaState? = null,
    @SerialName("new_state") val newState: HaState? = null
)
