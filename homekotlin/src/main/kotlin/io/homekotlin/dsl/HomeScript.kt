package io.homekotlin.dsl

import kotlin.time.Duration

open class HomeScript {
    internal val registeredAutomations = mutableListOf<Automation>()

    open fun configure() {}

    fun automation(name: String, id: String = name.toSlug(), block: AutomationBuilder.() -> Unit): Automation {
        val builder = AutomationBuilder(name, id)
        builder.block()
        val built = builder.build()
        registeredAutomations.add(built)
        return built
    }

    fun listenState(
        entityId: CharSequence,
        from: String? = null,
        to: String? = null,
        name: String = "Listen $entityId" + if (to != null) " -> $to" else "",
        mode: AutomationMode = AutomationMode.SINGLE,
        maxRuns: Int = 10,
        action: suspend ActionContext.(Trigger) -> Unit
    ): Automation {
        return automation(name) {
            mode(mode, maxRuns)
            trigger.state(entityId, from = from, to = to)
            action {
                action(trigger)
            }
        }
    }

    fun numericState(
        entityId: CharSequence,
        above: Double? = null,
        below: Double? = null,
        name: String = "Numeric $entityId" + (if (above != null) " > $above" else "") + (if (below != null) " < $below" else ""),
        mode: AutomationMode = AutomationMode.SINGLE,
        maxRuns: Int = 10,
        action: suspend ActionContext.(Trigger) -> Unit
    ): Automation {
        return automation(name) {
            mode(mode, maxRuns)
            trigger.numeric(entityId, above = above, below = below)
            action {
                action(trigger)
            }
        }
    }

    fun every(
        interval: Duration,
        name: String = "Every $interval",
        mode: AutomationMode = AutomationMode.SINGLE,
        maxRuns: Int = 10,
        action: suspend ActionContext.(Trigger) -> Unit
    ): Automation {
        return automation(name) {
            mode(mode, maxRuns)
            trigger.interval(interval)
            action {
                action(trigger)
            }
        }
    }

    fun cron(
        expression: String,
        name: String = "Cron $expression",
        mode: AutomationMode = AutomationMode.SINGLE,
        maxRuns: Int = 10,
        action: suspend ActionContext.(Trigger) -> Unit
    ): Automation {
        return automation(name) {
            mode(mode, maxRuns)
            trigger.cron(expression)
            action {
                action(trigger)
            }
        }
    }

    fun at(
        timeOrCron: String,
        name: String = "At $timeOrCron",
        mode: AutomationMode = AutomationMode.SINGLE,
        maxRuns: Int = 10,
        action: suspend ActionContext.(Trigger) -> Unit
    ): Automation {
        return automation(name) {
            mode(mode, maxRuns)
            trigger.time(timeOrCron)
            action {
                action(trigger)
            }
        }
    }
}
