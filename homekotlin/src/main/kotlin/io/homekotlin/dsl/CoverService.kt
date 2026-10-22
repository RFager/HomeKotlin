package io.homekotlin.dsl

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
