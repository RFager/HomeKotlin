package io.homekotlin.scripting

import io.homekotlin.dsl.HomeScript
import kotlin.script.experimental.annotations.KotlinScript

@KotlinScript(
    displayName = "HomeKotlin Automation Script",
    fileExtension = "kts",
    compilationConfiguration = HomeAutomationScriptConfiguration::class
)
abstract class HomeAutomationScript : HomeScript()
