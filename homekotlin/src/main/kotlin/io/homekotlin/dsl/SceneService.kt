package io.homekotlin.dsl

class SceneService(private val services: HaServices) {
    suspend fun turnOn(entityId: CharSequence): Boolean =
        services.call("scene", "turn_on", mapOf("entity_id" to entityId.toString()))
}
