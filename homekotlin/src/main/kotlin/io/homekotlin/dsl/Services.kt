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

    suspend fun listServices(domain: String? = null): List<io.homekotlin.ha.HaServiceInfo> {
        val all = client.getServices()
        return if (domain != null) all.filter { it.domain.equals(domain, ignoreCase = true) } else all
    }

    fun listTriggerTypes(): List<io.homekotlin.ha.TriggerTypeInfo> {
        return TriggerCatalog.supportedTypes()
    }
}

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

class SwitchService(private val services: HaServices) {
    suspend fun turnOn(entityId: CharSequence): Boolean =
        services.call("switch", "turn_on", mapOf("entity_id" to entityId.toString()))

    suspend fun turnOff(entityId: CharSequence): Boolean =
        services.call("switch", "turn_off", mapOf("entity_id" to entityId.toString()))

    suspend fun toggle(entityId: CharSequence): Boolean =
        services.call("switch", "toggle", mapOf("entity_id" to entityId.toString()))
}

class ClimateService(private val services: HaServices) {
    suspend fun setTemperature(entityId: CharSequence, temperature: Double): Boolean =
        services.call("climate", "set_temperature", mapOf("entity_id" to entityId.toString(), "temperature" to temperature))

    suspend fun setHvacMode(entityId: CharSequence, hvacMode: String): Boolean =
        services.call("climate", "set_hvac_mode", mapOf("entity_id" to entityId.toString(), "hvac_mode" to hvacMode))
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
    suspend fun open(entityId: CharSequence): Boolean =
        services.call("cover", "open_cover", mapOf("entity_id" to entityId.toString()))

    suspend fun close(entityId: CharSequence): Boolean =
        services.call("cover", "close_cover", mapOf("entity_id" to entityId.toString()))

    suspend fun stop(entityId: CharSequence): Boolean =
        services.call("cover", "stop_cover", mapOf("entity_id" to entityId.toString()))

    suspend fun setPosition(entityId: CharSequence, position: Int): Boolean =
        services.call("cover", "set_cover_position", mapOf("entity_id" to entityId.toString(), "position" to position))
}

class SceneService(private val services: HaServices) {
    suspend fun turnOn(entityId: CharSequence): Boolean =
        services.call("scene", "turn_on", mapOf("entity_id" to entityId.toString()))
}
