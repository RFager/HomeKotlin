package io.homekotlin.dsl

import java.time.LocalDateTime

data class TimeTrigger(
    val time: String // e.g. "22:00", "07:30", or full cron "0 7 * * 1-5"
) : Trigger {
    val cronTrigger: CronTrigger = CronTrigger(CronExpression.normalize(time))
    override val description: String = if (time.contains(" ")) "Cron '$time'" else "At $time (cron: ${cronTrigger.expression})"

    fun matches(dateTime: LocalDateTime): Boolean = cronTrigger.matches(dateTime)
}
