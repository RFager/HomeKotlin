package io.homekotlin.scripting

import kotlin.script.experimental.api.ScriptAcceptedLocation
import kotlin.script.experimental.api.ScriptCompilationConfiguration
import kotlin.script.experimental.api.acceptedLocations
import kotlin.script.experimental.api.defaultImports
import kotlin.script.experimental.api.ide
import kotlin.script.experimental.jvm.dependenciesFromCurrentContext
import kotlin.script.experimental.jvm.jvm

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
