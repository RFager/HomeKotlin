package io.homekotlin.dsl

import io.homekotlin.ha.StateChangeEvent

data class NumericStateTrigger(
    val entityId: String,
    val above: Double? = null,
    val below: Double? = null
) : Trigger {
    override val description: String
        get() = buildString {
            append("Numeric value for $entityId")
            if (above != null) append(" > $above")
            if (below != null) append(" < $below")
        }

    fun matches(event: StateChangeEvent): Boolean {
        if (event.entityId != entityId) return false
        val newNum = event.newState?.doubleValue() ?: return false
        val oldNum = event.oldState?.doubleValue()

        if (above != null) {
            val isNowAbove = newNum > above
            val wasAbove = oldNum != null && oldNum > above
            if (!isNowAbove || wasAbove) return false
        }

        if (below != null) {
            val isNowBelow = newNum < below
            val wasBelow = oldNum != null && oldNum < below
            if (!isNowBelow || wasBelow) return false
        }

        return true
    }
}
