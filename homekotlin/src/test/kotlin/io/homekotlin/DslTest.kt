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
        assertEquals(1, automation.triggers.size)
        assertTrue(automation.triggers[0] is StateTrigger)
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
}
