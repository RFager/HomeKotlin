package io.homekotlin.dsl

data class ManualTrigger(
    val triggeredBy: String = "User (Web Dashboard)"
) : Trigger {
    override val description: String = "Manual trigger by $triggeredBy"
}
