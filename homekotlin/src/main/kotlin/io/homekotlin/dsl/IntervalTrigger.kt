package io.homekotlin.dsl

import kotlin.time.Duration

data class IntervalTrigger(
    val interval: Duration
) : Trigger {
    override val description: String = "Every $interval"
}
