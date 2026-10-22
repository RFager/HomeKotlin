package io.homekotlin.dsl

class ClimateService(private val services: HaServices) {
    suspend fun setTemperature(entityId: CharSequence, temperature: Double): Boolean =
        services.call("climate", "set_temperature", mapOf("entity_id" to entityId.toString(), "temperature" to temperature))

    suspend fun setHvacMode(entityId: CharSequence, hvacMode: String): Boolean =
        services.call("climate", "set_hvac_mode", mapOf("entity_id" to entityId.toString(), "hvac_mode" to hvacMode))
}
