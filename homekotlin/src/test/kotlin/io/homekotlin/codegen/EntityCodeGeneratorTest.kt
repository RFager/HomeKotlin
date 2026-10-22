package io.homekotlin.codegen

import io.homekotlin.generated.BinarySensor
import io.homekotlin.generated.Entities
import io.homekotlin.generated.Entity
import io.homekotlin.generated.Light
import io.homekotlin.ha.HaState
import io.homekotlin.ha.HaStateStore
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Assertions.*
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
                    attributes = buildJsonObject {
                        put("friendly_name", "Front Porch Light")
                        put("brightness", 255)
                    }
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
                ),
                HaState(
                    entityId = "binary_sensor.altan_door",
                    state = "off",
                    attributes = buildJsonObject { put("friendly_name", "Altan dörr") }
                )
            )
        )
        Entity.stateStore = stateStore
        generator = EntityCodeGenerator(stateStore)
    }

    @Test
    fun `test grouped objects style generation`() {
        val code = generator.generateCode(style = "objects")

        assertTrue(code.contains("package io.homekotlin.generated"))
        assertTrue(code.contains("object Light {"))
        assertTrue(code.contains("val PORCH = LightEntity(\"light.porch\")"))
        assertTrue(code.contains("val porch = PORCH"))
        assertTrue(code.contains("val LIVING_ROOM_CEILING = LightEntity(\"light.living_room_ceiling\")"))
        assertTrue(code.contains("object Switch {"))
        assertTrue(code.contains("val COFFEE_MAKER = SwitchEntity(\"switch.coffee_maker\")"))
        assertTrue(code.contains("val coffeeMaker = COFFEE_MAKER"))
        assertTrue(code.contains("object BinarySensor {"))
        assertTrue(code.contains("val FRONT_DOOR = BinarySensorEntity(\"binary_sensor.front_door\")"))
        assertTrue(code.contains("val frontDoor = FRONT_DOOR"))
        assertTrue(code.contains("val ALTAN_DOOR = BinarySensorEntity(\"binary_sensor.altan_door\")"))
        assertTrue(code.contains("val altanDoor = ALTAN_DOOR"))
        assertTrue(code.contains("val BinarySensors = BinarySensor"))
        assertTrue(code.contains("val Lights = Light"))
        assertTrue(code.contains("val Switches = Switch"))
        assertTrue(code.contains("object Entities {"))
    }

    @Test
    fun `test flat constants style generation`() {
        val code = generator.generateCode(style = "constants")

        assertTrue(code.contains("// --- Light ---"))
        assertTrue(code.contains("val LIGHT_PORCH = LightEntity(\"light.porch\")"))
        assertTrue(code.contains("val LIGHT_LIVING_ROOM_CEILING = LightEntity(\"light.living_room_ceiling\")"))
        assertTrue(code.contains("// --- Switch ---"))
        assertTrue(code.contains("val SWITCH_COFFEE_MAKER = SwitchEntity(\"switch.coffee_maker\")"))
    }

    @Test
    fun `test typed DSL style generation`() {
        val code = generator.generateCode(style = "typed")

        assertTrue(code.contains("open class Entity(val entityId: String) : CharSequence by entityId"))
        assertTrue(code.contains("open class EntityState(val entityId: String) : CharSequence"))
        assertTrue(code.contains("open class LightEntity(entityId: String) : Entity(entityId)"))
        assertTrue(code.contains("open class BinarySensorEntity(entityId: String) : Entity(entityId)"))
        assertTrue(code.contains("open class SwitchEntity(entityId: String) : Entity(entityId)"))
        assertTrue(code.contains("object BinarySensor {"))
        assertTrue(code.contains("val ALTAN_DOOR = BinarySensorEntity(\"binary_sensor.altan_door\")"))
        assertTrue(code.contains("object Entities {"))
    }

    @Test
    fun `test domain filtering for lights only`() {
        val code = generator.generateCode(style = "objects", domain = "light")

        assertTrue(code.contains("object Light {"))
        assertTrue(code.contains("val PORCH = LightEntity(\"light.porch\")"))
        assertFalse(code.contains("object Switch {"))
        assertFalse(code.contains("coffee_maker"))
    }

    @Test
    fun `test live state reactivity and available states on generated entities`() {
        val altanDoor = BinarySensor.ALTAN_DOOR

        // 1. Available state constants connected directly to object
        assertEquals("on", altanDoor.OPEN)
        assertEquals("off", altanDoor.CLOSED)
        assertEquals("on", altanDoor.ON)
        assertEquals("off", altanDoor.OFF)
        assertEquals("on", altanDoor.state.OPEN)
        assertEquals("off", altanDoor.state.CLOSED)
        assertTrue(altanDoor.availableStates.contains("on"))
        assertTrue(altanDoor.availableStates.contains("off"))

        // 2. Initial state evaluation (initial state is "off")
        assertFalse(altanDoor.state.isOn)
        assertTrue(altanDoor.state.isOff)
        assertFalse(altanDoor.state.isOpen)
        assertTrue(altanDoor.state.isClosed)
        assertFalse(altanDoor.isOn)
        assertTrue(altanDoor.isClosed)
        assertEquals("off", altanDoor.state.toString())

        // 3. Update state in HaStateStore -> entity dynamically reflects new state
        stateStore.bulkUpdate(
            listOf(
                HaState(
                    entityId = "binary_sensor.altan_door",
                    state = "on",
                    attributes = buildJsonObject { put("friendly_name", "Altan dörr") }
                )
            )
        )

        // Now altanDoor.state.isOn evaluates to true!
        assertTrue(altanDoor.state.isOn)
        assertFalse(altanDoor.state.isOff)
        assertTrue(altanDoor.state.isOpen)
        assertFalse(altanDoor.state.isClosed)
        assertTrue(altanDoor.isOn)
        assertTrue(altanDoor.isOpen)
        assertEquals("on", altanDoor.state.toString())
        assertTrue(altanDoor.state.value == "on")
        assertTrue(altanDoor.state.value == altanDoor.OPEN)
        assertTrue(altanDoor.state.isState("on"))

        // 4. Aliasing works across camelCase, plural object, and root Entities registry
        val camelCaseAltan = BinarySensor.altanDoor
        assertTrue(camelCaseAltan.state.isOn)

        val registryAltan = Entities.BinarySensor.ALTAN_DOOR
        assertTrue(registryAltan.state.isOn)
    }

    @Test
    fun `test dynamic options generated for select entity`() {
        stateStore.bulkUpdate(
            listOf(
                HaState(
                    entityId = "select.waste_bin",
                    state = "Bin 1",
                    attributes = buildJsonObject {
                        put("friendly_name", "Waste Bin Selector")
                        put("options", kotlinx.serialization.json.buildJsonArray {
                            add(kotlinx.serialization.json.JsonPrimitive("Bin 1"))
                            add(kotlinx.serialization.json.JsonPrimitive("Bin 2"))
                            add(kotlinx.serialization.json.JsonPrimitive("Garden Waste"))
                        })
                    }
                )
            )
        )

        val code = generator.generateCode(style = "typed")
        assertTrue(code.contains("object Select {"))
        assertTrue(code.contains("val WASTE_BIN = object : SelectEntity(\"select.waste_bin\") {"))
        assertTrue(code.contains("val BIN_1 = \"Bin 1\""))
        assertTrue(code.contains("val BIN_2 = \"Bin 2\""))
        assertTrue(code.contains("val GARDEN_WASTE = \"Garden Waste\""))
        assertTrue(code.contains("val options = listOf(\"Bin 1\", \"Bin 2\", \"Garden Waste\")"))
        assertTrue(code.contains("val wasteBin = WASTE_BIN"))
    }
}
