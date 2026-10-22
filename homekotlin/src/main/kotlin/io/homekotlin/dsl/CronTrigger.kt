package io.homekotlin.dsl

import java.time.LocalDateTime

data class CronTrigger(
    val expression: String // e.g. "0 7 * * 1-5" or "@daily"
) : Trigger {
    val cron: CronExpression = CronExpression.parse(expression)
    override val description: String = "Cron '$expression'"

    fun matches(dateTime: LocalDateTime): Boolean = cron.matches(dateTime)
}
