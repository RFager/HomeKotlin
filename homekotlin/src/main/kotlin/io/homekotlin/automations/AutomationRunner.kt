package io.homekotlin.automations

import io.homekotlin.dsl.ActionContext
import io.homekotlin.dsl.Automation
import io.homekotlin.dsl.AutomationMode
import io.homekotlin.dsl.HaServices
import io.homekotlin.dsl.Trigger
import io.homekotlin.ha.HaStateStore
import io.homekotlin.ha.StateChangeEvent
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.system.measureTimeMillis

/**
 * Manages automation execution concurrency and execution modes (Home Assistant "körläge"):
 * - [AutomationMode.SINGLE] ("Enskild"): Do not start a new run if already running. Issue a warning.
 * - [AutomationMode.RESTART] ("Starta om"): Stop previous running instance and immediately start a new run.
 * - [AutomationMode.QUEUED] ("På kö"): Queue incoming runs sequentially in FIFO order (up to maxRuns).
 * - [AutomationMode.PARALLEL] ("Parallell"): Run independent executions concurrently (up to maxRuns).
 */
class AutomationRunner(
    private val scope: CoroutineScope,
    private val onLog: suspend (ExecutionLogEntry) -> Unit
) {
    private val logger = LoggerFactory.getLogger(AutomationRunner::class.java)

    private val activeJobs = ConcurrentHashMap<String, Job>()
    private val activeCounts = ConcurrentHashMap<String, AtomicInteger>()
    private val queuedCounts = ConcurrentHashMap<String, AtomicInteger>()
    private val queueMutexes = ConcurrentHashMap<String, Mutex>()

    fun getRunningCount(automationId: String): Int {
        return activeCounts[automationId]?.get() ?: 0
    }

    fun getQueuedCount(automationId: String): Int {
        return queuedCounts[automationId]?.get() ?: 0
    }

    fun cancelAll(automationId: String) {
        activeJobs.remove(automationId)?.cancel()
        activeCounts[automationId]?.set(0)
        queuedCounts[automationId]?.set(0)
    }

    fun clear() {
        activeJobs.values.forEach { it.cancel() }
        activeJobs.clear()
        activeCounts.clear()
        queuedCounts.clear()
        queueMutexes.clear()
    }

    suspend fun execute(
        automation: Automation,
        trigger: Trigger,
        triggerEvent: StateChangeEvent?,
        stateStore: HaStateStore,
        services: HaServices
    ): ExecutionLogEntry {
        if (!automation.enabled) {
            val entry = ExecutionLogEntry(
                automationId = automation.id,
                automationName = automation.name,
                triggerSource = trigger.description,
                durationMs = 0,
                success = false,
                error = "Automation is disabled"
            )
            onLog(entry)
            return entry
        }

        return when (automation.mode) {
            AutomationMode.SINGLE -> executeSingle(automation, trigger, triggerEvent, stateStore, services)
            AutomationMode.RESTART -> executeRestart(automation, trigger, triggerEvent, stateStore, services)
            AutomationMode.QUEUED -> executeQueued(automation, trigger, triggerEvent, stateStore, services)
            AutomationMode.PARALLEL -> executeParallel(automation, trigger, triggerEvent, stateStore, services)
        }
    }

    private suspend fun executeSingle(
        automation: Automation,
        trigger: Trigger,
        triggerEvent: StateChangeEvent?,
        stateStore: HaStateStore,
        services: HaServices
    ): ExecutionLogEntry {
        val count = activeCounts.computeIfAbsent(automation.id) { AtomicInteger(0) }
        if (count.get() > 0) {
            logger.warn("Automation '${automation.name}' [${automation.id}] is already running (körläge: Enskild / single). Execution skipped.")
            val entry = ExecutionLogEntry(
                automationId = automation.id,
                automationName = automation.name,
                triggerSource = trigger.description,
                durationMs = 0,
                success = false,
                error = "Already running (körläge: Enskild)"
            )
            onLog(entry)
            return entry
        }

        count.incrementAndGet()
        val deferred = CompletableDeferred<ExecutionLogEntry>()
        val job = scope.launch {
            try {
                val result = runDirect(automation, trigger, triggerEvent, stateStore, services)
                deferred.complete(result)
            } catch (e: CancellationException) {
                deferred.cancel(e)
                throw e
            } catch (e: Throwable) {
                deferred.completeExceptionally(e)
            } finally {
                count.decrementAndGet()
                activeJobs.remove(automation.id)
            }
        }
        activeJobs[automation.id] = job

        return awaitResultOrAsync(deferred, automation, trigger)
    }

    private suspend fun executeRestart(
        automation: Automation,
        trigger: Trigger,
        triggerEvent: StateChangeEvent?,
        stateStore: HaStateStore,
        services: HaServices
    ): ExecutionLogEntry {
        val count = activeCounts.computeIfAbsent(automation.id) { AtomicInteger(0) }
        val oldJob = activeJobs[automation.id]
        if (oldJob != null && oldJob.isActive) {
            logger.info("Restarting automation '${automation.name}' [${automation.id}]: stopping previous run (körläge: Starta om / restart)")
            oldJob.cancel(CancellationException("Restarted by new trigger"))
        }

        val deferred = CompletableDeferred<ExecutionLogEntry>()
        val currentJobRef = scope.launch {
            count.incrementAndGet()
            try {
                yield()
                val result = runDirect(automation, trigger, triggerEvent, stateStore, services)
                deferred.complete(result)
            } catch (e: CancellationException) {
                logger.info("Automation '${automation.name}' [${automation.id}] run cancelled by newer restart.")
                deferred.cancel(e)
                throw e
            } catch (e: Throwable) {
                deferred.completeExceptionally(e)
            } finally {
                count.decrementAndGet()
            }
        }
        activeJobs[automation.id] = currentJobRef

        return awaitResultOrAsync(deferred, automation, trigger)
    }

    private suspend fun executeQueued(
        automation: Automation,
        trigger: Trigger,
        triggerEvent: StateChangeEvent?,
        stateStore: HaStateStore,
        services: HaServices
    ): ExecutionLogEntry {
        val mutex = queueMutexes.computeIfAbsent(automation.id) { Mutex() }
        val queued = queuedCounts.computeIfAbsent(automation.id) { AtomicInteger(0) }
        val active = activeCounts.computeIfAbsent(automation.id) { AtomicInteger(0) }

        val currentQueued = queued.incrementAndGet()
        if (currentQueued > automation.maxRuns) {
            queued.decrementAndGet()
            logger.warn("Automation '${automation.name}' [${automation.id}] queue is full (max: ${automation.maxRuns}). Execution dropped.")
            val entry = ExecutionLogEntry(
                automationId = automation.id,
                automationName = automation.name,
                triggerSource = trigger.description,
                durationMs = 0,
                success = false,
                error = "Queue full (max: ${automation.maxRuns})"
            )
            onLog(entry)
            return entry
        }

        val deferred = CompletableDeferred<ExecutionLogEntry>()
        scope.launch {
            try {
                mutex.withLock {
                    queued.decrementAndGet()
                    active.incrementAndGet()
                    try {
                        val result = runDirect(automation, trigger, triggerEvent, stateStore, services)
                        deferred.complete(result)
                    } finally {
                        active.decrementAndGet()
                    }
                }
            } catch (e: CancellationException) {
                queued.decrementAndGet()
                deferred.cancel(e)
                throw e
            } catch (e: Throwable) {
                deferred.completeExceptionally(e)
            }
        }

        return awaitResultOrAsync(deferred, automation, trigger)
    }

    private suspend fun executeParallel(
        automation: Automation,
        trigger: Trigger,
        triggerEvent: StateChangeEvent?,
        stateStore: HaStateStore,
        services: HaServices
    ): ExecutionLogEntry {
        val active = activeCounts.computeIfAbsent(automation.id) { AtomicInteger(0) }

        val currentActive = active.incrementAndGet()
        if (currentActive > automation.maxRuns) {
            active.decrementAndGet()
            logger.warn("Automation '${automation.name}' [${automation.id}] reached max parallel executions (${automation.maxRuns}). Execution dropped.")
            val entry = ExecutionLogEntry(
                automationId = automation.id,
                automationName = automation.name,
                triggerSource = trigger.description,
                durationMs = 0,
                success = false,
                error = "Max parallel executions reached (${automation.maxRuns})"
            )
            onLog(entry)
            return entry
        }

        val deferred = CompletableDeferred<ExecutionLogEntry>()
        scope.launch {
            try {
                val result = runDirect(automation, trigger, triggerEvent, stateStore, services)
                deferred.complete(result)
            } catch (e: CancellationException) {
                deferred.cancel(e)
                throw e
            } catch (e: Throwable) {
                deferred.completeExceptionally(e)
            } finally {
                active.decrementAndGet()
            }
        }

        return awaitResultOrAsync(deferred, automation, trigger)
    }

    private suspend fun awaitResultOrAsync(
        deferred: CompletableDeferred<ExecutionLogEntry>,
        automation: Automation,
        trigger: Trigger
    ): ExecutionLogEntry {
        return withTimeoutOrNull(1500) {
            try {
                deferred.await()
            } catch (e: CancellationException) {
                ExecutionLogEntry(
                    automationId = automation.id,
                    automationName = automation.name,
                    triggerSource = trigger.description,
                    durationMs = 0,
                    success = false,
                    error = "Cancelled"
                )
            }
        } ?: ExecutionLogEntry(
            automationId = automation.id,
            automationName = automation.name,
            triggerSource = trigger.description,
            durationMs = 0,
            success = true,
            error = null
        )
    }

    private suspend fun runDirect(
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
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.warn("Condition evaluation failed for '${automation.name}': ${e.message}")
                false
            }
        }

        if (!conditionsMet) {
            val entry = ExecutionLogEntry(
                automationId = automation.id,
                automationName = automation.name,
                triggerSource = trigger.description,
                durationMs = 0,
                success = false,
                error = "Conditions not met"
            )
            onLog(entry)
            return entry
        }

        var executionError: String? = null
        val duration = measureTimeMillis {
            try {
                automation.action(context)
            } catch (e: CancellationException) {
                throw e
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

        onLog(entry)
        return entry
    }
}
