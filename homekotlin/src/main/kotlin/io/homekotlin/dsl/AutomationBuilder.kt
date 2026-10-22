package io.homekotlin.dsl

class AutomationBuilder(val name: String, val id: String = name.toSlug()) {
    var description: String = ""
    val trigger = TriggerBuilder()
    private val conditions = mutableListOf<suspend ActionContext.() -> Boolean>()
    private var actionBlock: (suspend ActionContext.() -> Unit)? = null

    fun description(text: String) {
        description = text
    }

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
            triggers = trigger.triggers.toList(),
            conditions = conditions.toList(),
            action = action
        )
    }
}
