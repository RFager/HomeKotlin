package io.homekotlin.dsl

import java.time.LocalDateTime

/**
 * Represents a standard 5-field Unix cron expression:
 *   minute (0-59)
 *   hour (0-23)
 *   day of month (1-31)
 *   month (1-12 or JAN-DEC)
 *   day of week (0-7, where 0 and 7 are Sunday; or SUN-SAT)
 *
 * Supports:
 *   - Wildcard: '*' or '?'
 *   - Step values: '* / 15', '1-5/2'
 *   - Ranges: '1-5', 'MON-FRI'
 *   - Lists: '1,15,30', 'MON,WED,FRI'
 *   - Macros: '@hourly', '@daily', '@midnight', '@weekly', '@monthly', '@yearly', '@annually'
 *   - Simple time: 'HH:mm' format (e.g. '07:00' -> '0 7 * * *')
 */
class CronExpression private constructor(
    val raw: String,
    private val minuteField: CronField,
    private val hourField: CronField,
    private val dayOfMonthField: CronField,
    private val monthField: CronField,
    private val dayOfWeekField: CronField
) {
    fun matches(dateTime: LocalDateTime): Boolean {
        if (!minuteField.matches(dateTime.minute)) return false
        if (!hourField.matches(dateTime.hour)) return false
        if (!monthField.matches(dateTime.monthValue)) return false

        // Standard Unix cron semantics for day-of-month and day-of-week:
        // If both are restricted (neither is wildcard), match if EITHER matches (OR condition).
        // If one is wildcard, match the restricted one.
        // If both are wildcard, matches every day.
        val domMatches = dayOfMonthField.matches(dateTime.dayOfMonth)
        val dow = dateTime.dayOfWeek.value // 1 (Mon) to 7 (Sun)
        val dowMatches = dayOfWeekField.matches(dow) || (dow == 7 && dayOfWeekField.matches(0))

        return when {
            dayOfMonthField.isWildcard && dayOfWeekField.isWildcard -> true
            !dayOfMonthField.isWildcard && !dayOfWeekField.isWildcard -> domMatches || dowMatches
            !dayOfMonthField.isWildcard -> domMatches
            else -> dowMatches
        }
    }

    override fun toString(): String = raw

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CronExpression) return false
        return raw == other.raw
    }

    override fun hashCode(): Int = raw.hashCode()

    companion object {
        fun normalize(timeOrCron: String): String {
            val trimmed = timeOrCron.trim()
            if (trimmed.startsWith("@")) return trimmed
            // Convert simple 24h time like "07:00" or "7:30" to standard cron
            if (trimmed.matches(Regex("""^\d{1,2}:\d{2}$"""))) {
                val parts = trimmed.split(":")
                val hour = parts[0].toInt()
                val minute = parts[1].toInt()
                return "$minute $hour * * *"
            }
            return trimmed
        }

        fun parse(expression: String): CronExpression {
            val normalized = normalize(expression)
            val expanded = when (normalized.lowercase()) {
                "@hourly" -> "0 * * * *"
                "@daily", "@midnight" -> "0 0 * * *"
                "@weekly" -> "0 0 * * 0"
                "@monthly" -> "0 0 1 * *"
                "@yearly", "@annually" -> "0 0 1 1 *"
                else -> normalized
            }

            val parts = expanded.trim().split(Regex("""\s+"""))
            if (parts.size != 5) {
                throw IllegalArgumentException(
                    "Invalid cron expression: '$expression'. Expected 5 fields (minute hour day-of-month month day-of-week), but got ${parts.size}."
                )
            }

            return CronExpression(
                raw = expression,
                minuteField = CronField.parse(parts[0], 0, 59, emptyMap()),
                hourField = CronField.parse(parts[1], 0, 23, emptyMap()),
                dayOfMonthField = CronField.parse(parts[2], 1, 31, emptyMap()),
                monthField = CronField.parse(parts[3], 1, 12, MONTH_ALIASES),
                dayOfWeekField = CronField.parse(parts[4], 0, 7, DOW_ALIASES)
            )
        }

        private val MONTH_ALIASES = mapOf(
            "JAN" to 1, "FEB" to 2, "MAR" to 3, "APR" to 4,
            "MAY" to 5, "JUN" to 6, "JUL" to 7, "AUG" to 8,
            "SEP" to 9, "OCT" to 10, "NOV" to 11, "DEC" to 12
        )

        private val DOW_ALIASES = mapOf(
            "SUN" to 0, "MON" to 1, "TUE" to 2, "WED" to 3,
            "THU" to 4, "FRI" to 5, "SAT" to 6
        )
    }
}

internal class CronField(
    val isWildcard: Boolean,
    private val allowedValues: Set<Int>
) {
    fun matches(value: Int): Boolean = isWildcard || allowedValues.contains(value)

    companion object {
        fun parse(
            token: String,
            min: Int,
            max: Int,
            aliases: Map<String, Int>
        ): CronField {
            val upper = token.uppercase()
            if (upper == "*" || upper == "?") {
                return CronField(isWildcard = true, allowedValues = emptySet())
            }

            val values = mutableSetOf<Int>()
            val subTokens = upper.split(",")
            for (sub in subTokens) {
                if (sub.startsWith("*/")) {
                    val step = sub.removePrefix("*/").toIntOrNull()
                        ?: throw IllegalArgumentException("Invalid step in cron token: '$sub'")
                    if (step <= 0) throw IllegalArgumentException("Step must be positive in: '$sub'")
                    for (v in min..max step step) {
                        values.add(v)
                    }
                } else if ("-" in sub) {
                    val (rangePart, stepPart) = if ("/" in sub) {
                        val split = sub.split("/")
                        split[0] to (split[1].toIntOrNull() ?: throw IllegalArgumentException("Invalid step: '$sub'"))
                    } else {
                        sub to 1
                    }
                    val rangeSplit = rangePart.split("-")
                    if (rangeSplit.size != 2) throw IllegalArgumentException("Invalid range in: '$sub'")
                    val start = resolveValue(rangeSplit[0], aliases)
                    val end = resolveValue(rangeSplit[1], aliases)
                    if (start > end) throw IllegalArgumentException("Range start must be <= end in: '$sub'")
                    for (v in start..end step stepPart) {
                        if (v in min..max) values.add(v)
                    }
                } else {
                    val single = resolveValue(sub, aliases)
                    if (single !in min..max) {
                        throw IllegalArgumentException("Value $single out of range ($min..$max) in cron token: '$token'")
                    }
                    values.add(single)
                }
            }

            return CronField(isWildcard = false, allowedValues = values)
        }

        private fun resolveValue(text: String, aliases: Map<String, Int>): Int {
            return aliases[text]
                ?: text.toIntOrNull()
                ?: throw IllegalArgumentException("Unrecognized value '$text' in cron field")
        }
    }
}
