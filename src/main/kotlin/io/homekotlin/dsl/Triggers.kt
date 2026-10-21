package io.homekotlin.dsl

import io.homekotlin.ha.StateChangeEvent
import kotlin.time.Duration

sealed interface Trigger {
    val description: String
}

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

data class IntervalTrigger(
    val interval: Duration
) : Trigger {
    override val description: String = "Every $interval"
}

data class CronTrigger(
    val expression: String // e.g. "0 7 * * 1-5" or "@daily"
) : Trigger {
    val cron: CronExpression = CronExpression.parse(expression)
    override val description: String = "Cron '$expression'"

    fun matches(dateTime: java.time.LocalDateTime): Boolean = cron.matches(dateTime)
}

data class TimeTrigger(
    val time: String // e.g. "22:00", "07:30", or full cron "0 7 * * 1-5"
) : Trigger {
    val cronTrigger: CronTrigger = CronTrigger(CronExpression.normalize(time))
    override val description: String = if (time.contains(" ")) "Cron '$time'" else "At $time (cron: ${cronTrigger.expression})"

    fun matches(dateTime: java.time.LocalDateTime): Boolean = cronTrigger.matches(dateTime)
}

data class SunTrigger(
    val event: String, // "sunset" or "sunrise"
    val offsetMinutes: Int = 0
) : Trigger {
    override val description: String = "On $event" + if (offsetMinutes != 0) " (offset: ${offsetMinutes}m)" else ""
}

data class ManualTrigger(
    val triggeredBy: String = "User (Web Dashboard)"
) : Trigger {
    override val description: String = "Manual trigger by $triggeredBy"
}
