package io.homekotlin

import io.homekotlin.dsl.*
import io.homekotlin.ha.HaState
import io.homekotlin.ha.StateChangeEvent
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.seconds

class DslTest {

    @Test
    fun `test automation builder properties`() {
        val builder = AutomationBuilder("Test Motion Automation", "test_motion")
        builder.description("A test automation")
        builder.trigger.state("binary_sensor.motion", to = "on")
        builder.condition { true }
        builder.action { /* no-op */ }

        val automation = builder.build("test.kts")

        assertEquals("test_motion", automation.id)
        assertEquals("Test Motion Automation", automation.name)
        assertEquals("A test automation", automation.description)
        assertEquals("test.kts", automation.sourceFile)
        assertEquals(AutomationMode.SINGLE, automation.mode)
        assertEquals(1, automation.triggers.size)
        assertTrue(automation.triggers[0] is StateTrigger)
    }

    @Test
    fun `test automation builder with restart mode and Swedish aliases`() {
        val builder = AutomationBuilder("Porch Lighting", "porch_lighting")
        builder.restartMode()
        val a1 = builder.build()
        assertEquals(AutomationMode.RESTART, a1.mode)
        assertEquals("Starta om", a1.mode.title)

        builder.mode(AutomationMode.QUEUED, max = 5)
        val a2 = builder.build()
        assertEquals(AutomationMode.QUEUED, a2.mode)
        assertEquals(5, a2.maxRuns)
        assertEquals("På kö", a2.mode.title)

        builder.körläge(Körläge.PARALLELL, max = 8)
        val a3 = builder.build()
        assertEquals(AutomationMode.PARALLEL, a3.mode)
        assertEquals(8, a3.maxRuns)
        assertEquals("Parallell", a3.mode.title)
    }

    @Test
    fun `test state trigger matching`() {
        val trigger = StateTrigger("binary_sensor.motion", from = "off", to = "on")

        val matchEvent = StateChangeEvent(
            entityId = "binary_sensor.motion",
            oldState = HaState("binary_sensor.motion", "off"),
            newState = HaState("binary_sensor.motion", "on")
        )

        val nonMatchEvent1 = StateChangeEvent(
            entityId = "binary_sensor.motion",
            oldState = HaState("binary_sensor.motion", "on"),
            newState = HaState("binary_sensor.motion", "off")
        )

        val nonMatchEvent2 = StateChangeEvent(
            entityId = "binary_sensor.other",
            oldState = HaState("binary_sensor.other", "off"),
            newState = HaState("binary_sensor.other", "on")
        )

        assertTrue(trigger.matches(matchEvent))
        assertFalse(trigger.matches(nonMatchEvent1))
        assertFalse(trigger.matches(nonMatchEvent2))
    }

    @Test
    fun `test numeric state trigger matching`() {
        val trigger = NumericStateTrigger("sensor.temperature", above = 24.0)

        val riseAboveEvent = StateChangeEvent(
            entityId = "sensor.temperature",
            oldState = HaState("sensor.temperature", "23.5"),
            newState = HaState("sensor.temperature", "25.0")
        )

        val staysAboveEvent = StateChangeEvent(
            entityId = "sensor.temperature",
            oldState = HaState("sensor.temperature", "25.0"),
            newState = HaState("sensor.temperature", "26.0")
        )

        assertTrue(trigger.matches(riseAboveEvent))
        assertFalse(trigger.matches(staysAboveEvent)) // Should not re-trigger if already above
    }

    @Test
    fun `test cron expression matching`() {
        val cron = CronExpression.parse("0 7 * * 1-5") // Weekdays at 07:00

        // Monday 2026-10-26 07:00
        val monday7am = java.time.LocalDateTime.of(2026, 10, 26, 7, 0)
        assertTrue(cron.matches(monday7am))

        // Monday 2026-10-26 07:01 (wrong minute)
        val monday701am = java.time.LocalDateTime.of(2026, 10, 26, 7, 1)
        assertFalse(cron.matches(monday701am))

        // Saturday 2026-10-24 07:00 (weekend)
        val saturday7am = java.time.LocalDateTime.of(2026, 10, 24, 7, 0)
        assertFalse(cron.matches(saturday7am))

        // Sunday 2026-10-25 07:00 (weekend)
        val sunday7am = java.time.LocalDateTime.of(2026, 10, 25, 7, 0)
        assertFalse(cron.matches(sunday7am))
    }

    @Test
    fun `test cron expression steps and lists`() {
        val stepCron = CronExpression.parse("*/15 * * * *")
        assertTrue(stepCron.matches(java.time.LocalDateTime.of(2026, 1, 1, 12, 0)))
        assertTrue(stepCron.matches(java.time.LocalDateTime.of(2026, 1, 1, 12, 15)))
        assertTrue(stepCron.matches(java.time.LocalDateTime.of(2026, 1, 1, 12, 30)))
        assertTrue(stepCron.matches(java.time.LocalDateTime.of(2026, 1, 1, 12, 45)))
        assertFalse(stepCron.matches(java.time.LocalDateTime.of(2026, 1, 1, 12, 10)))

        val listCron = CronExpression.parse("0 9,12,18 * * *")
        assertTrue(listCron.matches(java.time.LocalDateTime.of(2026, 1, 1, 9, 0)))
        assertTrue(listCron.matches(java.time.LocalDateTime.of(2026, 1, 1, 12, 0)))
        assertTrue(listCron.matches(java.time.LocalDateTime.of(2026, 1, 1, 18, 0)))
        assertFalse(listCron.matches(java.time.LocalDateTime.of(2026, 1, 1, 10, 0)))
    }

    @Test
    fun `test time trigger with simple HHmm normalized to cron`() {
        val trigger = TimeTrigger("07:30")
        assertEquals("30 7 * * *", trigger.cronTrigger.expression)
        assertTrue(trigger.matches(java.time.LocalDateTime.of(2026, 5, 10, 7, 30)))
        assertFalse(trigger.matches(java.time.LocalDateTime.of(2026, 5, 10, 7, 31)))
    }

    @Test
    fun `test trigger catalog lists all supported triggers`() {
        val types = TriggerCatalog.supportedTypes()
        assertTrue(types.isNotEmpty())
        val typeNames = types.map { it.type }
        assertTrue(typeNames.contains("cron"))
        assertTrue(typeNames.contains("state"))
        assertTrue(typeNames.contains("numeric_state"))
        assertTrue(typeNames.contains("time"))
        assertTrue(typeNames.contains("interval"))
        assertTrue(typeNames.contains("sun"))
        assertTrue(typeNames.contains("manual"))
    }

    @Test
    fun `test services listServices in demo mode`() = kotlinx.coroutines.runBlocking {
        val config = io.homekotlin.config.AppConfig(demoMode = true)
        val stateStore = io.homekotlin.ha.HaStateStore()
        val client = io.homekotlin.ha.HaClient(config, stateStore)
        val services = io.homekotlin.dsl.HaServices(client)

        val allServices = services.listServices()
        assertTrue(allServices.isNotEmpty())
        assertTrue(allServices.any { it.serviceId == "light.turn_on" })
        assertTrue(allServices.any { it.serviceId == "switch.turn_on" })
        assertTrue(allServices.any { it.serviceId == "climate.set_temperature" })
        assertTrue(allServices.any { it.serviceId == "notify.notify" })

        val lightServices = services.listServices("light")
        assertTrue(lightServices.all { it.domain == "light" })
    }
}
