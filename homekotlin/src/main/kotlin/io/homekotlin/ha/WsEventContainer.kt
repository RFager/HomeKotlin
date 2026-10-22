package io.homekotlin.ha

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class WsEventContainer(
    @SerialName("event_type") val eventType: String,
    val data: WsEventData? = null,
    @SerialName("time_fired") val timeFired: String? = null
)
