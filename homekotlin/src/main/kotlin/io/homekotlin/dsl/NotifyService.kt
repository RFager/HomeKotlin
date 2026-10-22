package io.homekotlin.dsl

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
