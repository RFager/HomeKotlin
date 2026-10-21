package io.homekotlin.dsl

import kotlin.time.Duration

open class HomeScript {
    internal val registeredAutomations = mutableListOf<Automation>()

    companion object {
        val currentScriptCapture = java.util.concurrent.ConcurrentLinkedQueue<Automation>()
    }

    open fun configure() {}

    fun automation(name: String, id: String = name.toSlug(), block: AutomationBuilder.() -> Unit): Automation {
        val builder = AutomationBuilder(name, id)
        builder.block()
        val built = builder.build(sourceFile = this::class.simpleName)
        registeredAutomations.add(built)
        currentScriptCapture.add(built)
        return built
    }

    fun listenState(
        entityId: String,
        from: String? = null,
        to: String? = null,
        name: String = "Listen $entityId" + if (to != null) " -> $to" else "",
        action: suspend ActionContext.(Trigger) -> Unit
    ): Automation {
        return automation(name) {
            trigger.state(entityId, from = from, to = to)
            action {
                action(trigger)
            }
        }
    }

    fun numericState(
        entityId: String,
        above: Double? = null,
        below: Double? = null,
        name: String = "Numeric $entityId" + (if (above != null) " > $above" else "") + (if (below != null) " < $below" else ""),
        action: suspend ActionContext.(Trigger) -> Unit
    ): Automation {
        return automation(name) {
            trigger.numeric(entityId, above = above, below = below)
            action {
                action(trigger)
            }
        }
    }

    fun every(
        interval: Duration,
        name: String = "Every $interval",
        action: suspend ActionContext.(Trigger) -> Unit
    ): Automation {
        return automation(name) {
            trigger.interval(interval)
            action {
                action(trigger)
            }
        }
    }
}
