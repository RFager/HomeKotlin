package io.homekotlin.dsl

import io.homekotlin.ha.HaState
import io.homekotlin.ha.HaStateStore
import io.homekotlin.ha.StateChangeEvent
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

data class Automation(
    val id: String,
    val name: String,
    val description: String = "",
    val sourceFile: String? = null,
    val triggers: List<Trigger> = emptyList(),
    val conditions: List<suspend ActionContext.() -> Boolean> = emptyList(),
    val action: suspend ActionContext.() -> Unit,
    var enabled: Boolean = true,
    var lastTriggered: Long? = null,
    var lastTriggerSource: String? = null,
    var executionCount: Long = 0,
    var lastDurationMs: Long? = null,
    var lastError: String? = null
)

class ActionContext(
    val automation: Automation,
    val states: HaStateStore,
    val services: HaServices,
    val trigger: Trigger,
    val triggerEvent: StateChangeEvent? = null
) {
    val log: Logger = LoggerFactory.getLogger("Automation[${automation.name}]")

    init {
        io.homekotlin.generated.Entity.stateStore = states
    }

    fun state(entityId: CharSequence): HaState? = states[entityId.toString()]

    fun isState(entityId: CharSequence, expected: String): Boolean =
        states.isState(entityId.toString(), expected)

    @JvmName("entityState")
    fun CharSequence.state(): HaState? = states[this.toString()]

    @JvmName("entityIsState")
    fun CharSequence.isState(expected: String): Boolean =
        states.isState(this.toString(), expected)

    val CharSequence.isOn: Boolean
        get() = states.isState(this.toString(), "on")

    val CharSequence.isOff: Boolean
        get() = states.isState(this.toString(), "off")

    fun isSunBelowHorizon(): Boolean =
        states["sun.sun"]?.state?.equals("below_horizon", ignoreCase = true) == true

    fun isSunAboveHorizon(): Boolean =
        states["sun.sun"]?.state?.equals("above_horizon", ignoreCase = true) == true

    suspend fun delay(duration: Duration) {
        kotlinx.coroutines.delay(duration.inWholeMilliseconds.milliseconds)
    }

    suspend fun waitUntil(
        timeout: Duration,
        checkInterval: Duration = Duration.parse("500ms"),
        condition: suspend () -> Boolean
    ): Boolean {
        return withTimeoutOrNull(timeout) {
            while (!condition()) {
                delay(checkInterval)
            }
            true
        } ?: false
    }
}

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

class AutomationBuilder(val name: String, val id: String = name.toSlug()) {
    var description: String = ""
    val trigger = TriggerBuilder()
    private val conditions = mutableListOf<suspend ActionContext.() -> Boolean>()
    private var actionBlock: (suspend ActionContext.() -> Unit)? = null

    fun description(text: String) {
        description = text
    }

    fun condition(block: suspend ActionContext.() -> Boolean) {
        conditions.add(block)
    }

    fun action(block: suspend ActionContext.() -> Unit) {
        actionBlock = block
    }

    fun build(sourceFile: String? = null): Automation {
        val action = actionBlock ?: { /* no-op action for draft/logging */ }
        return Automation(
            id = id,
            name = name,
            description = description,
            sourceFile = sourceFile,
            triggers = trigger.triggers.toList(),
            conditions = conditions.toList(),
            action = action
        )
    }
}

fun String.toSlug(): String =
    lowercase()
        .replace(Regex("[^a-z0-9]+"), "_")
        .trim('_')
