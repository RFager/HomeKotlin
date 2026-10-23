package io.homekotlin.automations

import io.homekotlin.dsl.*
import io.homekotlin.ha.HaStateStore
import io.homekotlin.ha.StateChangeEvent
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedDeque

class AutomationRegistry(
    coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
) {
    private val logger = LoggerFactory.getLogger(AutomationRegistry::class.java)
    private val automations = ConcurrentHashMap<String, Automation>()
    private val executionHistory = ConcurrentLinkedDeque<ExecutionLogEntry>()
    private val maxHistorySize = 100

    private val _executionEvents = MutableSharedFlow<ExecutionLogEntry>(extraBufferCapacity = 100)
    val executionEvents: SharedFlow<ExecutionLogEntry> = _executionEvents.asSharedFlow()

    private val timerJobs = ConcurrentHashMap<String, Job>()

    private val runner = AutomationRunner(coroutineScope) { entry ->
        executionHistory.addFirst(entry)
        while (executionHistory.size > maxHistorySize) {
            executionHistory.removeLast()
        }
        _executionEvents.emit(entry)
    }

    fun register(automation: Automation) {
        automations[automation.id] = automation
        logger.info("Registered automation '${automation.name}' [${automation.id}] (mode: ${automation.mode.title}) from ${automation.sourceFile ?: "internal"}")
    }

    fun register(script: HomeScript) {
        script.configure()
        val sourceName = script::class.simpleName + ".kt"
        for (a in script.registeredAutomations) {
            register(a.copy(sourceFile = sourceName))
        }
    }

    fun unregisterBySource(sourceFile: String) {
        val toRemove = automations.values.filter { it.sourceFile == sourceFile }
        for (a in toRemove) {
            runner.cancelAll(a.id)
            timerJobs.remove(a.id)?.cancel()
            automations.remove(a.id)
            logger.info("Unregistered automation '${a.name}' (${a.id})")
        }
    }

    fun clear() {
        runner.clear()
        timerJobs.values.forEach { it.cancel() }
        timerJobs.clear()
        automations.clear()
        logger.info("Cleared all registered automations")
    }

    fun get(id: String): Automation? = automations[id]

    fun all(): List<Automation> = automations.values.toList().sortedBy { it.name }

    fun count(): Int = automations.size

    fun getRunningCount(id: String): Int = runner.getRunningCount(id)

    fun toggle(id: String): Boolean? {
        val a = automations[id] ?: return null
        a.enabled = !a.enabled
        return a.enabled
    }

    fun setMode(id: String, mode: AutomationMode, maxRuns: Int? = null): Boolean {
        val a = automations[id] ?: return false
        a.mode = mode
        if (maxRuns != null && maxRuns > 0) {
            a.maxRuns = maxRuns
        }
        logger.info("Updated execution mode for '${a.name}' to ${mode.title} [${mode.id}] (maxRuns: ${a.maxRuns})")
        return true
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
        return runner.execute(
            automation = automation,
            trigger = trigger,
            triggerEvent = triggerEvent,
            stateStore = stateStore,
            services = services
        )
    }
}
