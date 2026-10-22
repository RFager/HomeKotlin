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
