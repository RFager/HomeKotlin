package io.homekotlin.dsl

import io.homekotlin.ha.HaClient
import io.homekotlin.ha.HaServiceInfo
import io.homekotlin.ha.TriggerTypeInfo
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

    suspend fun listServices(domain: String? = null): List<HaServiceInfo> {
        val all = client.getServices()
        return if (domain != null) all.filter { it.domain.equals(domain, ignoreCase = true) } else all
    }

    fun listTriggerTypes(): List<TriggerTypeInfo> {
        return TriggerCatalog.supportedTypes()
    }
}
