package io.homekotlin.dsl

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
