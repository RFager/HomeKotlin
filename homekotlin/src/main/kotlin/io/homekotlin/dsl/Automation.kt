package io.homekotlin.dsl

data class Automation(
    val id: String,
    val name: String,
    val description: String = "",
    val sourceFile: String? = null,
    var mode: AutomationMode = AutomationMode.SINGLE,
    var maxRuns: Int = 10,
    val triggers: List<Trigger> = emptyList(),
    val conditions: List<suspend ActionContext.() -> Boolean> = emptyList(),
    val action: suspend ActionContext.() -> Unit,
    var enabled: Boolean = true,
    var lastTriggered: Long? = null,
    var lastTriggerSource: String? = null,
    var executionCount: Long = 0,
    var lastDurationMs: Long? = null,
    var lastError: String? = null
)
