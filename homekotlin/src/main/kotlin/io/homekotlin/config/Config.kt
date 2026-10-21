package io.homekotlin.config

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

data class AppConfig(
    val haUrl: String,
    val haToken: String,
    val isSupervisor: Boolean,
    val automationsDir: String,
    val port: Int,
    val logLevel: String,
    val demoMode: Boolean
) {
    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun load(): AppConfig {
            val supervisorToken = System.getenv("SUPERVISOR_TOKEN")
            val isSupervisor = !supervisorToken.isNullOrBlank()

            // In Home Assistant Supervisor Add-on mode, options are mounted at /data/options.json
            val optionsFile = File("/data/options.json")
            val options = if (optionsFile.exists()) {
                try {
                    json.parseToJsonElement(optionsFile.readText()).jsonObject
                } catch (e: Exception) {
                    null
                }
            } else null

            val defaultHaUrl = if (isSupervisor) "ws://supervisor/core/websocket" else "ws://localhost:8123/api/websocket"
            val haUrl = System.getenv("HA_URL")
                ?: options?.get("ha_url")?.jsonPrimitive?.content
                ?: defaultHaUrl

            val haToken = supervisorToken
                ?: System.getenv("HA_TOKEN")
                ?: options?.get("ha_token")?.jsonPrimitive?.content
                ?: ""

            val automationsDir = System.getenv("AUTOMATIONS_DIR")
                ?: options?.get("automations_dir")?.jsonPrimitive?.content
                ?: if (isSupervisor) "/config/homekotlin/automations" else "automations"

            val port = System.getenv("PORT")?.toIntOrNull()
                ?: options?.get("port")?.jsonPrimitive?.content?.toIntOrNull()
                ?: 8099

            val logLevel = System.getenv("LOG_LEVEL")
                ?: options?.get("log_level")?.jsonPrimitive?.content
                ?: "INFO"

            // Enable demo/mock mode if no token is configured and we're not inside Supervisor
            val demoMode = System.getenv("DEMO_MODE")?.toBooleanStrictOrNull()
                ?: (!isSupervisor && haToken.isBlank())

            return AppConfig(
                haUrl = haUrl,
                haToken = haToken,
                isSupervisor = isSupervisor,
                automationsDir = automationsDir,
                port = port,
                logLevel = logLevel,
                demoMode = demoMode
            )
        }
    }
}
