package io.homekotlin.dsl

import kotlin.time.Duration

class TriggerBuilder {
    internal val triggers = mutableListOf<Trigger>()

    fun state(entityId: CharSequence, from: String? = null, to: String? = null) {
        triggers.add(StateTrigger(entityId = entityId.toString(), from = from, to = to))
    }

    fun numeric(entityId: CharSequence, above: Double? = null, below: Double? = null) {
        triggers.add(NumericStateTrigger(entityId = entityId.toString(), above = above, below = below))
    }

    fun interval(duration: Duration) {
        triggers.add(IntervalTrigger(duration))
    }

    fun cron(expression: String) {
        triggers.add(CronTrigger(expression))
    }

    fun time(timeOrCron: String) {
        triggers.add(CronTrigger(CronExpression.normalize(timeOrCron)))
    }

    fun sun(event: String, offsetMinutes: Int = 0) {
        triggers.add(SunTrigger(event, offsetMinutes))
    }
}
