package io.homekotlin.dsl

class AutomationBuilder(val name: String, val id: String = name.toSlug()) {
    var description: String = ""
    var mode: AutomationMode = AutomationMode.SINGLE
    var maxRuns: Int = 10
    val trigger = TriggerBuilder()
    private val conditions = mutableListOf<suspend ActionContext.() -> Boolean>()
    private var actionBlock: (suspend ActionContext.() -> Unit)? = null

    fun description(text: String) {
        description = text
    }

    fun mode(mode: AutomationMode, max: Int = 10) {
        this.mode = mode
        this.maxRuns = max
    }

    fun singleMode() = mode(AutomationMode.SINGLE)
    fun restartMode() = mode(AutomationMode.RESTART)
    fun queuedMode(max: Int = 10) = mode(AutomationMode.QUEUED, max)
    fun parallelMode(max: Int = 10) = mode(AutomationMode.PARALLEL, max)

    fun körläge(läge: AutomationMode, max: Int = 10) = mode(läge, max)

    fun condition(block: suspend ActionContext.() -> Boolean) {
        conditions.add(block)
    }

    fun action(block: suspend ActionContext.() -> Unit) {
        actionBlock = block
    }

    fun build(sourceFile: String? = null): Automation {
        val action = actionBlock ?: { /* no-op action for draft/logging */ }
        return Automation(
            id = id,
            name = name,
            description = description,
            sourceFile = sourceFile,
            mode = mode,
            maxRuns = maxRuns,
            triggers = trigger.triggers.toList(),
            conditions = conditions.toList(),
            action = action
        )
    }
}
