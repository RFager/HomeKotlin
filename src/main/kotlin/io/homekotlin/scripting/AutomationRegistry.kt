package io.homekotlin.scripting

import io.homekotlin.dsl.*
import io.homekotlin.ha.HaStateStore
import io.homekotlin.ha.StateChangeEvent
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedDeque
import kotlin.system.measureTimeMillis

@Serializable
data class ExecutionLogEntry(
    val id: String = java.util.UUID.randomUUID().toString(),
    val automationId: String,
    val automationName: String,
    val timestamp: Long = System.currentTimeMillis(),
    val triggerSource: String,
    val durationMs: Long,
    val success: Boolean,
    val error: String? = null
)

class AutomationRegistry {
    private val logger = LoggerFactory.getLogger(AutomationRegistry::class.java)
    private val automations = ConcurrentHashMap<String, Automation>()
    private val executionHistory = ConcurrentLinkedDeque<ExecutionLogEntry>()
    private val maxHistorySize = 100

    private val _executionEvents = MutableSharedFlow<ExecutionLogEntry>(extraBufferCapacity = 100)
    val executionEvents: SharedFlow<ExecutionLogEntry> = _executionEvents.asSharedFlow()

    private val timerJobs = ConcurrentHashMap<String, Job>()

    fun register(automation: Automation) {
        automations[automation.id] = automation
        logger.info("Registered automation '${automation.name}' [${automation.id}] from ${automation.sourceFile ?: "internal"}")
    }

    fun unregisterBySource(sourceFile: String) {
        val toRemove = automations.values.filter { it.sourceFile == sourceFile }
        for (a in toRemove) {
            timerJobs.remove(a.id)?.cancel()
            automations.remove(a.id)
            logger.info("Unregistered automation '${a.name}' (${a.id})")
        }
    }

    fun get(id: String): Automation? = automations[id]

    fun all(): List<Automation> = automations.values.toList().sortedBy { it.name }

    fun count(): Int = automations.size

    fun toggle(id: String): Boolean? {
        val a = automations[id] ?: return null
        a.enabled = !a.enabled
        return a.enabled
    }

    fun history(): List<ExecutionLogEntry> = executionHistory.toList()

    fun startEventDispatcher(
        scope: CoroutineScope,
        stateStore: HaStateStore,
        services: HaServices
    ) {
        scope.launch {
            stateStore.events.collect { event ->
                for (automation in automations.values) {
                    if (!automation.enabled) continue

                    // Check if any trigger matches this event
                    val matchingTrigger = automation.triggers.firstOrNull { trigger ->
                        when (trigger) {
                            is StateTrigger -> trigger.matches(event)
                            is NumericStateTrigger -> trigger.matches(event)
                            else -> false
                        }
                    } ?: continue

                    launch {
                        executeAutomation(
                            automation = automation,
                            trigger = matchingTrigger,
                            triggerEvent = event,
                            stateStore = stateStore,
                            services = services
                        )
                    }
                }
            }
        }
    }

    fun startTimerDispatcher(
        scope: CoroutineScope,
        stateStore: HaStateStore,
        services: HaServices
    ) {
        // Run periodic clock check for interval and cron/time triggers
        scope.launch {
            while (isActive) {
                // Align timer check to the start of the next minute to avoid drift
                val nowMillis = System.currentTimeMillis()
                val nextMinuteMillis = ((nowMillis / 60_000) + 1) * 60_000
                val delayMs = (nextMinuteMillis - nowMillis).coerceAtLeast(500)
                delay(delayMs)

                val now = LocalDateTime.now()

                for (automation in automations.values) {
                    if (!automation.enabled) continue

                    for (trigger in automation.triggers) {
                        when (trigger) {
                            is CronTrigger -> {
                                if (trigger.matches(now)) {
                                    launch {
                                        executeAutomation(
                                            automation = automation,
                                            trigger = trigger,
                                            triggerEvent = null,
                                            stateStore = stateStore,
                                            services = services
                                        )
                                    }
                                }
                            }
                            is TimeTrigger -> {
                                if (trigger.matches(now)) {
                                    launch {
                                        executeAutomation(
                                            automation = automation,
                                            trigger = trigger,
                                            triggerEvent = null,
                                            stateStore = stateStore,
                                            services = services
                                        )
                                    }
                                }
                            }
                            is IntervalTrigger -> {
                                // Manage dedicated interval job if not already running
                                if (!timerJobs.containsKey(automation.id)) {
                                    val job = scope.launch {
                                        while (isActive && automation.enabled) {
                                            delay(trigger.interval)
                                            executeAutomation(
                                                automation = automation,
                                                trigger = trigger,
                                                triggerEvent = null,
                                                stateStore = stateStore,
                                                services = services
                                            )
                                        }
                                    }
                                    timerJobs[automation.id] = job
                                }
                            }
                            else -> {}
                        }
                    }
                }
            }
        }
    }

    suspend fun executeAutomation(
        automation: Automation,
        trigger: Trigger,
        triggerEvent: StateChangeEvent?,
        stateStore: HaStateStore,
        services: HaServices
    ): ExecutionLogEntry {
        val context = ActionContext(
            automation = automation,
            states = stateStore,
            services = services,
            trigger = trigger,
            triggerEvent = triggerEvent
        )

        // Evaluate conditions
        val conditionsMet = automation.conditions.all { cond ->
            try {
                cond(context)
            } catch (e: Exception) {
                logger.warn("Condition evaluation failed for '${automation.name}': ${e.message}")
                false
            }
        }

        if (!conditionsMet) {
            return ExecutionLogEntry(
                automationId = automation.id,
                automationName = automation.name,
                triggerSource = trigger.description,
                durationMs = 0,
                success = false,
                error = "Conditions not met"
            )
        }

        var executionError: String? = null
        val duration = measureTimeMillis {
            try {
                automation.action(context)
            } catch (e: Exception) {
                executionError = e.message ?: e.toString()
                logger.error("Execution failed for automation '${automation.name}': ${e.message}", e)
            }
        }

        automation.lastTriggered = System.currentTimeMillis()
        automation.lastTriggerSource = trigger.description
        automation.executionCount++
        automation.lastDurationMs = duration
        automation.lastError = executionError

        val entry = ExecutionLogEntry(
            automationId = automation.id,
            automationName = automation.name,
            triggerSource = trigger.description,
            durationMs = duration,
            success = executionError == null,
            error = executionError
        )

        executionHistory.addFirst(entry)
        while (executionHistory.size > maxHistorySize) {
            executionHistory.removeLast()
        }

        _executionEvents.emit(entry)
        return entry
    }
}
