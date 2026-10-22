package io.homekotlin.ha

data class ConnectionStatus(
    val state: ConnectionState = ConnectionState.DISCONNECTED,
    val url: String = "",
    val connectedAt: Long? = null,
    val reconnectAttempts: Int = 0,
    val lastError: String? = null,
    val isDemoMode: Boolean = false
)
