package io.homekotlin

import io.homekotlin.automations.CompiledAutomationLoader
import io.homekotlin.scripting.AutomationRegistry
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class CompiledAutomationLoaderTest {

    @Test
    fun `test loading compiled automations from package`() {
        val registry = AutomationRegistry()
        CompiledAutomationLoader.loadAll(registry, "io.homekotlin.automations")

        assertTrue(registry.count() >= 2, "Expected at least 2 automations from PresenceLighting")
        val porchAutomation = registry.all().find { it.name == "Porch Welcome & Auto-Off" }
        assertNotNull(porchAutomation)
        assertEquals("PresenceLighting.kt", porchAutomation?.sourceFile)

        val espressoAutomation = registry.all().find { it.name == "Morning Espresso Machine Prep" }
        assertNotNull(espressoAutomation)
    }
}
