package io.homekotlin

import io.homekotlin.automations.AutomationRegistry
import io.homekotlin.automations.registerAutomations
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class AutomationsRegistrationTest {

    @Test
    fun `test registering automations`() {
        val registry = AutomationRegistry()
        registry.registerAutomations()

        assertTrue(registry.count() >= 2, "Expected at least 2 automations from PresenceLighting")
        val porchAutomation = registry.all().find { it.name == "Porch Welcome & Auto-Off" }
        assertNotNull(porchAutomation)
        assertEquals("PresenceLighting.kt", porchAutomation?.sourceFile)
        assertEquals(io.homekotlin.dsl.AutomationMode.RESTART, porchAutomation?.mode)

        val espressoAutomation = registry.all().find { it.name == "Morning Espresso Machine Prep" }
        assertNotNull(espressoAutomation)
        assertEquals(io.homekotlin.dsl.AutomationMode.SINGLE, espressoAutomation?.mode)
    }
}
