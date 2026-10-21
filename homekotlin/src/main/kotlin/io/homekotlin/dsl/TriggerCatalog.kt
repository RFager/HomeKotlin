package io.homekotlin.dsl

import io.homekotlin.ha.TriggerTypeInfo

object TriggerCatalog {
    fun supportedTypes(): List<TriggerTypeInfo> = listOf(
        TriggerTypeInfo(
            type = "cron",
            displayName = "Cron Schedule",
            description = "Triggers on a standard 5-part cron schedule or human macros (@daily, @hourly, @weekly, etc.).",
            exampleDsl = "trigger.cron(\"0 7 * * 1-5\") // Weekdays at 07:00",
            parameters = mapOf(
                "expression" to "Cron expression: minute (0-59), hour (0-23), day-of-month (1-31), month (1-12 or JAN-DEC), day-of-week (0-7 or SUN-SAT)"
            )
        ),
        TriggerTypeInfo(
            type = "time",
            displayName = "Time of Day",
            description = "Triggers at a specific 24-hour clock time (HH:mm) or cron pattern.",
            exampleDsl = "trigger.time(\"07:30\")",
            parameters = mapOf(
                "time" to "Clock time in 24-hour format (e.g. \"07:30\") or cron expression"
            )
        ),
        TriggerTypeInfo(
            type = "state",
            displayName = "Entity State Change",
            description = "Triggers when any Home Assistant entity changes state. Optionally filter by previous ('from') or new ('to') state.",
            exampleDsl = "trigger.state(\"binary_sensor.front_door\", to = \"on\")",
            parameters = mapOf(
                "entityId" to "Target entity identifier (e.g. \"binary_sensor.motion\")",
                "from" to "Optional previous state filter (e.g. \"off\")",
                "to" to "Optional target state filter (e.g. \"on\")"
            )
        ),
        TriggerTypeInfo(
            type = "numeric_state",
            displayName = "Numeric State Threshold",
            description = "Triggers when a sensor's numeric state value crosses above or below a specified threshold.",
            exampleDsl = "trigger.numeric(\"sensor.living_room_temperature\", above = 24.5)",
            parameters = mapOf(
                "entityId" to "Sensor entity identifier",
                "above" to "Threshold above which the trigger fires upon crossing",
                "below" to "Threshold below which the trigger fires upon crossing"
            )
        ),
        TriggerTypeInfo(
            type = "interval",
            displayName = "Recurring Interval",
            description = "Triggers recurrently every specified duration (e.g. 15.seconds, 5.minutes, 1.hours).",
            exampleDsl = "trigger.interval(5.minutes)",
            parameters = mapOf(
                "duration" to "Kotlin Duration instance (e.g. 5.minutes)"
            )
        ),
        TriggerTypeInfo(
            type = "sun",
            displayName = "Solar Event (Sunrise / Sunset)",
            description = "Triggers when the sun rises or sets, with optional positive or negative minute offsets.",
            exampleDsl = "trigger.sun(\"sunset\", offsetMinutes = -30) // 30 min before sunset",
            parameters = mapOf(
                "event" to "\"sunrise\" or \"sunset\"",
                "offsetMinutes" to "Offset in minutes relative to the event (negative = before, positive = after)"
            )
        ),
        TriggerTypeInfo(
            type = "manual",
            displayName = "Manual Trigger",
            description = "Allows manual invocation on demand from the Ingress web dashboard or REST API.",
            exampleDsl = "ManualTrigger(\"User Dashboard\")",
            parameters = mapOf(
                "triggeredBy" to "Name or origin of the user/system initiating execution"
            )
        )
    )
}
