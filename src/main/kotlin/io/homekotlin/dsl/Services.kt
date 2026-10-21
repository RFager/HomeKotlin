package io.homekotlin.dsl

import io.homekotlin.ha.HaClient
import org.slf4j.LoggerFactory

class HaServices(private val client: HaClient) {
    private val logger = LoggerFactory.getLogger(HaServices::class.java)

    val light = LightService(this)
    val switch = SwitchService(this)
    val climate = ClimateService(this)
    val notify = NotifyService(this)
    val cover = CoverService(this)
    val scene = SceneService(this)

    suspend fun call(
        domain: String,
        service: String,
        serviceData: Map<String, Any?> = emptyMap(),
        target: Map<String, Any?>? = null
    ): Boolean {
        return client.callService(domain, service, serviceData, target)
    }
}

class LightService(private val services: HaServices) {
    suspend fun turnOn(
        entityId: String,
        brightness: Int? = null,
        brightnessPct: Int? = null,
        rgbColor: List<Int>? = null,
        colorTemp: Int? = null,
        transition: Double? = null
    ): Boolean {
        val data = mutableMapOf<String, Any?>("entity_id" to entityId)
        if (brightness != null) data["brightness"] = brightness
        if (brightnessPct != null) data["brightness_pct"] = brightnessPct
        if (rgbColor != null) data["rgb_color"] = rgbColor
        if (colorTemp != null) data["color_temp"] = colorTemp
        if (transition != null) data["transition"] = transition

        return services.call("light", "turn_on", data)
    }

    suspend fun turnOff(entityId: String, transition: Double? = null): Boolean {
        val data = mutableMapOf<String, Any?>("entity_id" to entityId)
        if (transition != null) data["transition"] = transition
        return services.call("light", "turn_off", data)
    }

    suspend fun toggle(entityId: String): Boolean {
        return services.call("light", "toggle", mapOf("entity_id" to entityId))
    }
}

class SwitchService(private val services: HaServices) {
    suspend fun turnOn(entityId: String): Boolean =
        services.call("switch", "turn_on", mapOf("entity_id" to entityId))

    suspend fun turnOff(entityId: String): Boolean =
        services.call("switch", "turn_off", mapOf("entity_id" to entityId))

    suspend fun toggle(entityId: String): Boolean =
        services.call("switch", "toggle", mapOf("entity_id" to entityId))
}

class ClimateService(private val services: HaServices) {
    suspend fun setTemperature(entityId: String, temperature: Double): Boolean =
        services.call("climate", "set_temperature", mapOf("entity_id" to entityId, "temperature" to temperature))

    suspend fun setHvacMode(entityId: String, hvacMode: String): Boolean =
        services.call("climate", "set_hvac_mode", mapOf("entity_id" to entityId, "hvac_mode" to hvacMode))
}

class NotifyService(private val services: HaServices) {
    suspend fun send(message: String, title: String? = null, targetService: String = "notify"): Boolean {
        val data = mutableMapOf<String, Any?>("message" to message)
        if (title != null) data["title"] = title
        return services.call("notify", targetService, data)
    }

    suspend fun persistentNotification(message: String, title: String? = null, notificationId: String? = null): Boolean {
        val data = mutableMapOf<String, Any?>("message" to message)
        if (title != null) data["title"] = title
        if (notificationId != null) data["notification_id"] = notificationId
        return services.call("persistent_notification", "create", data)
    }
}

class CoverService(private val services: HaServices) {
    suspend fun open(entityId: String): Boolean =
        services.call("cover", "open_cover", mapOf("entity_id" to entityId))

    suspend fun close(entityId: String): Boolean =
        services.call("cover", "close_cover", mapOf("entity_id" to entityId))

    suspend fun stop(entityId: String): Boolean =
        services.call("cover", "stop_cover", mapOf("entity_id" to entityId))

    suspend fun setPosition(entityId: String, position: Int): Boolean =
        services.call("cover", "set_cover_position", mapOf("entity_id" to entityId, "position" to position))
}

class SceneService(private val services: HaServices) {
    suspend fun turnOn(entityId: String): Boolean =
        services.call("scene", "turn_on", mapOf("entity_id" to entityId))
}
