package io.homekotlin.codegen

import io.homekotlin.ha.HaState
import io.homekotlin.ha.HaStateStore
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File

class EntityCodeGeneratorTest {

    private lateinit var stateStore: HaStateStore
    private lateinit var generator: EntityCodeGenerator

    @BeforeEach
    fun setup() {
        stateStore = HaStateStore()
        stateStore.bulkUpdate(
            listOf(
                HaState(
                    entityId = "light.porch",
                    state = "on",
                    attributes = buildJsonObject { put("friendly_name", "Front Porch Light") }
                ),
                HaState(
                    entityId = "light.living_room_ceiling",
                    state = "off",
                    attributes = buildJsonObject { put("friendly_name", "Living Room Ceiling Light") }
                ),
                HaState(
                    entityId = "switch.coffee_maker",
                    state = "off",
                    attributes = buildJsonObject { put("friendly_name", "Kitchen Coffee Maker") }
                ),
                HaState(
                    entityId = "sensor.living_room_temperature",
                    state = "21.5",
                    attributes = buildJsonObject { put("friendly_name", "Living Room Temperature") }
                ),
                HaState(
                    entityId = "binary_sensor.front_door",
                    state = "off",
                    attributes = buildJsonObject { put("friendly_name", "Front Door Contact") }
                )
            )
        )
        generator = EntityCodeGenerator(stateStore)
    }

    @Test
    fun `test grouped objects style generation`() {
        val code = generator.generateCode(style = "objects")

        assertTrue(code.contains("package io.homekotlin.generated"))
        assertTrue(code.contains("object Lights {"))
        assertTrue(code.contains("const val PORCH = \"light.porch\""))
        assertTrue(code.contains("const val LIVING_ROOM_CEILING = \"light.living_room_ceiling\""))
        assertTrue(code.contains("object Switches {"))
        assertTrue(code.contains("const val COFFEE_MAKER = \"switch.coffee_maker\""))
        assertTrue(code.contains("object Sensors {"))
        assertTrue(code.contains("object BinarySensors {"))
    }

    @Test
    fun `test flat constants style generation`() {
        val code = generator.generateCode(style = "constants")

        assertTrue(code.contains("// --- Light ---"))
        assertTrue(code.contains("const val LIGHT_PORCH = \"light.porch\""))
        assertTrue(code.contains("const val LIGHT_LIVING_ROOM_CEILING = \"light.living_room_ceiling\""))
        assertTrue(code.contains("// --- Switch ---"))
        assertTrue(code.contains("const val SWITCH_COFFEE_MAKER = \"switch.coffee_maker\""))
    }

    @Test
    fun `test typed DSL style generation`() {
        val code = generator.generateCode(style = "typed")

        assertTrue(code.contains("open class Entity(val entityId: String) : CharSequence by entityId"))
        assertTrue(code.contains("class LightEntity(entityId: String) : Entity(entityId)"))
        assertTrue(code.contains("class SwitchEntity(entityId: String) : Entity(entityId)"))
        assertTrue(code.contains("object Entities {"))
        assertTrue(code.contains("object Light {"))
        assertTrue(code.contains("val porch = LightEntity(\"light.porch\")"))
        assertTrue(code.contains("val livingRoomCeiling = LightEntity(\"light.living_room_ceiling\")"))
        assertTrue(code.contains("object Switch {"))
        assertTrue(code.contains("val coffeeMaker = SwitchEntity(\"switch.coffee_maker\")"))
    }

    @Test
    fun `test domain filtering for lights only`() {
        val code = generator.generateCode(style = "objects", domain = "light")

        assertTrue(code.contains("object Lights {"))
        assertTrue(code.contains("const val PORCH = \"light.porch\""))
        assertFalse(code.contains("object Switches {"))
        assertFalse(code.contains("coffee_maker"))
    }

    @Test
    fun `test domain filtering for switches only in constants style`() {
        val code = generator.generateCode(style = "constants", domain = "switch")

        assertTrue(code.contains("const val SWITCH_COFFEE_MAKER = \"switch.coffee_maker\""))
        assertFalse(code.contains("LIGHT_PORCH"))
    }

    @Test
    fun `test generateToFile writes valid file`() {
        val tempFile = File.createTempFile("GeneratedEntitiesTest", ".kt")
        try {
            val file = generator.generateToFile(tempFile, style = "objects", domain = "light")
            assertTrue(file.exists())
            val content = file.readText()
            assertTrue(content.contains("object Lights {"))
            assertTrue(content.contains("PORCH"))
        } finally {
            tempFile.delete()
        }
    }
}
