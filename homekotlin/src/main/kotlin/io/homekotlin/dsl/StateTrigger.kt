package io.homekotlin.dsl

import io.homekotlin.ha.StateChangeEvent

data class StateTrigger(
    val entityId: String,
    val from: String? = null,
    val to: String? = null
) : Trigger {
    override val description: String
        get() = buildString {
            append("State change for $entityId")
            if (from != null) append(" from '$from'")
            if (to != null) append(" to '$to'")
        }

    fun matches(event: StateChangeEvent): Boolean {
        if (event.entityId != entityId) return false
        if (from != null && !event.oldState?.state.equals(from, ignoreCase = true)) return false
        if (to != null && !event.newState?.state.equals(to, ignoreCase = true)) return false
        return true
    }
}
