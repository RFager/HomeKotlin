package io.homekotlin

import io.homekotlin.automations.AutomationRegistry
import io.homekotlin.dsl.*
import io.homekotlin.ha.HaClient
import io.homekotlin.ha.HaStateStore
import kotlinx.coroutines.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class AutomationModeTest {

    private fun setupTestEnvironment(): Pair<AutomationRegistry, HaServices> {
        val config = io.homekotlin.config.AppConfig(demoMode = true)
        val stateStore = HaStateStore()
        val client = HaClient(config, stateStore)
        val services = HaServices(client)
        val registry = AutomationRegistry()
        return Pair(registry, services)
    }

    @Test
    fun `test single mode drops concurrent trigger with warning`() = runBlocking {
        val (registry, services) = setupTestEnvironment()
        val stateStore = HaStateStore()

        val runsCompleted = AtomicInteger(0)
        val isFirstRunning = AtomicBoolean(false)

        val automation = Automation(
            id = "test_single",
            name = "Test Single Mode",
            mode = AutomationMode.SINGLE,
            action = {
                isFirstRunning.set(true)
                delay(200)
                runsCompleted.incrementAndGet()
                isFirstRunning.set(false)
            }
        )
        registry.register(automation)

        // Trigger 1 (starts and delays)
        val trigger1 = ManualTrigger("Manual 1")
        val job1 = launch {
            registry.executeAutomation(automation, trigger1, null, stateStore, services)
        }

        // Wait until run 1 is active
        while (!isFirstRunning.get()) {
            delay(10)
        }

        // Trigger 2 arrives while run 1 is still active
        val trigger2 = ManualTrigger("Manual 2")
        val result2 = registry.executeAutomation(automation, trigger2, null, stateStore, services)

        assertFalse(result2.success)
        assertTrue(result2.error?.contains("Enskild") == true, "Expected error to mention Enskild / single")

        job1.join()
        assertEquals(1, runsCompleted.get(), "Only the first run should have completed")
    }

    @Test
    fun `test restart mode cancels previous execution and starts new`() = runBlocking {
        val (registry, services) = setupTestEnvironment()
        val stateStore = HaStateStore()

        val firstRunFinished = AtomicBoolean(false)
        val secondRunFinished = AtomicBoolean(false)
        val firstRunStarted = AtomicBoolean(false)

        var runIndex = 0
        val automation = Automation(
            id = "test_restart",
            name = "Test Restart Mode",
            mode = AutomationMode.RESTART,
            action = {
                val current = ++runIndex
                if (current == 1) {
                    firstRunStarted.set(true)
                    delay(300)
                    firstRunFinished.set(true)
                } else {
                    delay(50)
                    secondRunFinished.set(true)
                }
            }
        )
        registry.register(automation)

        // Trigger 1
        val job1 = launch {
            registry.executeAutomation(automation, ManualTrigger("Run 1"), null, stateStore, services)
        }

        while (!firstRunStarted.get()) {
            delay(10)
        }

        // Trigger 2 (restarts)
        val job2 = launch {
            registry.executeAutomation(automation, ManualTrigger("Run 2"), null, stateStore, services)
        }

        job1.join()
        job2.join()

        assertFalse(firstRunFinished.get(), "First run should have been cancelled before completion")
        assertTrue(secondRunFinished.get(), "Second run should have completed")
    }

    @Test
    fun `test queued mode executes sequential runs in order`() = runBlocking {
        val (registry, services) = setupTestEnvironment()
        val stateStore = HaStateStore()

        val executionOrder = mutableListOf<Int>()
        val firstRunStarted = AtomicBoolean(false)

        var counter = 0
        val automation = Automation(
            id = "test_queued",
            name = "Test Queued Mode",
            mode = AutomationMode.QUEUED,
            maxRuns = 5,
            action = {
                val num = ++counter
                if (num == 1) {
                    firstRunStarted.set(true)
                }
                delay(100)
                synchronized(executionOrder) {
                    executionOrder.add(num)
                }
            }
        )
        registry.register(automation)

        val job1 = launch {
            registry.executeAutomation(automation, ManualTrigger("Run 1"), null, stateStore, services)
        }

        while (!firstRunStarted.get()) {
            delay(10)
        }

        val job2 = launch {
            registry.executeAutomation(automation, ManualTrigger("Run 2"), null, stateStore, services)
        }

        val job3 = launch {
            registry.executeAutomation(automation, ManualTrigger("Run 3"), null, stateStore, services)
        }

        job1.join()
        job2.join()
        job3.join()

        assertEquals(listOf(1, 2, 3), executionOrder, "Executions should run sequentially in FIFO order")
    }

    @Test
    fun `test parallel mode executes concurrently up to max`() = runBlocking {
        val (registry, services) = setupTestEnvironment()
        val stateStore = HaStateStore()

        val activeCount = AtomicInteger(0)
        var maxObservedConcurrency = 0

        val automation = Automation(
            id = "test_parallel",
            name = "Test Parallel Mode",
            mode = AutomationMode.PARALLEL,
            maxRuns = 10,
            action = {
                val current = activeCount.incrementAndGet()
                synchronized(this) {
                    if (current > maxObservedConcurrency) {
                        maxObservedConcurrency = current
                    }
                }
                delay(100)
                activeCount.decrementAndGet()
            }
        )
        registry.register(automation)

        val job1 = launch {
            registry.executeAutomation(automation, ManualTrigger("P1"), null, stateStore, services)
        }
        val job2 = launch {
            registry.executeAutomation(automation, ManualTrigger("P2"), null, stateStore, services)
        }

        job1.join()
        job2.join()

        assertTrue(maxObservedConcurrency >= 2, "Expected concurrent executions in parallel mode (observed: $maxObservedConcurrency)")
    }
}
