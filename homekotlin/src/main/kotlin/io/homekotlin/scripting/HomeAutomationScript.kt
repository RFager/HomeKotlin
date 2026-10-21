package io.homekotlin.scripting

import io.homekotlin.dsl.HomeScript
import kotlin.script.experimental.annotations.KotlinScript
import kotlin.script.experimental.api.*
import kotlin.script.experimental.jvm.dependenciesFromCurrentContext
import kotlin.script.experimental.jvm.jvm

@KotlinScript(
    displayName = "HomeKotlin Automation Script",
    fileExtension = "kts",
    compilationConfiguration = HomeAutomationScriptConfiguration::class
)
abstract class HomeAutomationScript : HomeScript()

object HomeAutomationScriptConfiguration : ScriptCompilationConfiguration({
    defaultImports(
        "io.homekotlin.dsl.*",
        "io.homekotlin.ha.*",
        "kotlin.time.Duration.Companion.seconds",
        "kotlin.time.Duration.Companion.minutes",
        "kotlin.time.Duration.Companion.hours"
    )
    jvm {
        dependenciesFromCurrentContext(wholeClasspath = true)
    }
    ide {
        acceptedLocations(ScriptAcceptedLocation.Everywhere)
    }
})
