package io.homekotlin.dsl

class LightService(private val services: HaServices) {
    suspend fun turnOn(
        entityId: CharSequence,
        brightness: Int? = null,
        brightnessPct: Int? = null,
        rgbColor: List<Int>? = null,
        colorTemp: Int? = null,
        transition: Double? = null
    ): Boolean {
        val data = mutableMapOf<String, Any?>("entity_id" to entityId.toString())
        if (brightness != null) data["brightness"] = brightness
        if (brightnessPct != null) data["brightness_pct"] = brightnessPct
        if (rgbColor != null) data["rgb_color"] = rgbColor
        if (colorTemp != null) data["color_temp"] = colorTemp
        if (transition != null) data["transition"] = transition

        return services.call("light", "turn_on", data)
    }

    suspend fun turnOff(entityId: CharSequence, transition: Double? = null): Boolean {
        val data = mutableMapOf<String, Any?>("entity_id" to entityId.toString())
        if (transition != null) data["transition"] = transition
        return services.call("light", "turn_off", data)
    }

    suspend fun toggle(entityId: CharSequence): Boolean {
        return services.call("light", "toggle", mapOf("entity_id" to entityId.toString()))
    }
}
