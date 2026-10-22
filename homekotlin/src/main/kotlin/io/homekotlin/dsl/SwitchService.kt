package io.homekotlin.dsl

class SwitchService(private val services: HaServices) {
    suspend fun turnOn(entityId: CharSequence): Boolean =
        services.call("switch", "turn_on", mapOf("entity_id" to entityId.toString()))

    suspend fun turnOff(entityId: CharSequence): Boolean =
        services.call("switch", "turn_off", mapOf("entity_id" to entityId.toString()))

    suspend fun toggle(entityId: CharSequence): Boolean =
        services.call("switch", "toggle", mapOf("entity_id" to entityId.toString()))
}
