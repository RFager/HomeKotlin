package io.homekotlin.dsl

data class SunTrigger(
    val event: String, // "sunset" or "sunrise"
    val offsetMinutes: Int = 0
) : Trigger {
    override val description: String = "On $event" + if (offsetMinutes != 0) " (offset: ${offsetMinutes}m)" else ""
}
