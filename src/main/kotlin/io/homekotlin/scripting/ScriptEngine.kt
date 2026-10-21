package io.homekotlin.scripting

import io.homekotlin.dsl.HomeScript
import org.slf4j.LoggerFactory
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlin.script.experimental.api.*
import kotlin.script.experimental.host.toScriptSource
import kotlin.script.experimental.jvm.dependenciesFromCurrentContext
import kotlin.script.experimental.jvm.jvm
import kotlin.script.experimental.jvmhost.BasicJvmScriptingHost
import kotlin.script.experimental.jvmhost.createJvmCompilationConfigurationFromTemplate

import kotlinx.serialization.Serializable

@Serializable
data class ScriptDiagnostic(
    val line: Int?,
    val column: Int?,
    val message: String,
    val severity: String
)

@Serializable
data class ScriptInfo(
    val filename: String,
    val path: String,
    val status: ScriptStatus,
    val automationCount: Int = 0,
    val error: String? = null,
    val diagnostics: List<ScriptDiagnostic> = emptyList(),
    val lastLoadedAt: Long = System.currentTimeMillis()
)

@Serializable
enum class ScriptStatus {
    LOADED,
    ERROR,
    EMPTY
}

class ScriptEngine(
    private val automationsDir: File,
    private val registry: AutomationRegistry
) {
    private val logger = LoggerFactory.getLogger(ScriptEngine::class.java)
    private val scriptStatusMap = ConcurrentHashMap<String, ScriptInfo>()
    private val scriptingHost = BasicJvmScriptingHost()

    fun allScripts(): List<ScriptInfo> = scriptStatusMap.values.toList().sortedBy { it.filename }

    fun loadAll() {
        if (!automationsDir.exists()) {
            automationsDir.mkdirs()
        }

        val files = automationsDir.listFiles { f ->
            f.isFile && (f.name.endsWith(".kts") || f.name.endsWith(".kt"))
        } ?: emptyArray()

        logger.info("Discovered ${files.size} automation script(s) in ${automationsDir.absolutePath}")

        if (files.isEmpty()) {
            createDefaultSampleScripts()
        }

        val updatedFiles = automationsDir.listFiles { f ->
            f.isFile && (f.name.endsWith(".kts") || f.name.endsWith(".kt"))
        } ?: emptyArray()

        for (file in updatedFiles) {
            loadFile(file)
        }
    }

    fun loadFile(file: File) {
        val filename = file.name
        registry.unregisterBySource(filename)
        HomeScript.currentScriptCapture.clear()

        try {
            logger.info("Compiling & evaluating script: $filename")
            val source = file.toScriptSource()

            val compilationConfiguration = ScriptCompilationConfiguration {
                jvm {
                    dependenciesFromCurrentContext(wholeClasspath = true)
                }
                defaultImports(
                    "io.homekotlin.dsl.*",
                    "io.homekotlin.ha.*",
                    "kotlin.time.Duration.Companion.seconds",
                    "kotlin.time.Duration.Companion.minutes",
                    "kotlin.time.Duration.Companion.hours"
                )
            }

            val evaluationConfiguration = ScriptEvaluationConfiguration()

            val result = scriptingHost.eval(source, compilationConfiguration, evaluationConfiguration)

            val diagnostics = result.reports.map { report ->
                ScriptDiagnostic(
                    line = report.location?.start?.line,
                    column = report.location?.start?.col,
                    message = report.message,
                    severity = report.severity.name
                )
            }

            when (result) {
                is ResultWithDiagnostics.Success -> {
                    val returnVal = result.value.returnValue
                    val evaluatedObj = (returnVal as? ResultValue.Value)?.value
                    val scriptInstance = returnVal.scriptInstance

                    val scriptTargets = mutableListOf<HomeScript>()
                    if (evaluatedObj is HomeScript) scriptTargets.add(evaluatedObj)
                    if (scriptInstance is HomeScript && !scriptTargets.contains(scriptInstance)) scriptTargets.add(scriptInstance)

                    if (scriptInstance != null) {
                        for (field in scriptInstance.javaClass.declaredFields) {
                            try {
                                field.isAccessible = true
                                val obj = field.get(scriptInstance)
                                if (obj is HomeScript && !scriptTargets.contains(obj)) {
                                    scriptTargets.add(obj)
                                }
                            } catch (_: Exception) {}
                        }
                    }

                    for (target in scriptTargets) {
                        try {
                            target.configure()
                            for (a in target.registeredAutomations) {
                                registry.register(a.copy(sourceFile = filename))
                            }
                        } catch (e: Exception) {
                            logger.warn("configure() call failed on ${target::class.simpleName}: ${e.message}")
                        }
                    }

                    // Also register any automations collected directly during script execution
                    while (true) {
                        val captured = HomeScript.currentScriptCapture.poll() ?: break
                        registry.register(captured.copy(sourceFile = filename))
                    }

                    val count = registry.all().count { it.sourceFile == filename }

                    scriptStatusMap[filename] = ScriptInfo(
                        filename = filename,
                        path = file.absolutePath,
                        status = ScriptStatus.LOADED,
                        automationCount = count,
                        diagnostics = diagnostics
                    )
                    logger.info("Successfully loaded $filename with $count automation(s)")
                }
                is ResultWithDiagnostics.Failure -> {
                    val errors = diagnostics.filter { it.severity == "ERROR" }
                    val errorMsg = errors.joinToString("; ") { "[line ${it.line ?: "?"}] ${it.message}" }
                        .ifEmpty { "Script compilation failed" }

                    scriptStatusMap[filename] = ScriptInfo(
                        filename = filename,
                        path = file.absolutePath,
                        status = ScriptStatus.ERROR,
                        error = errorMsg,
                        diagnostics = diagnostics
                    )
                    logger.error("Failed to load script $filename: $errorMsg")
                }
            }
        } catch (e: Throwable) {
            scriptStatusMap[filename] = ScriptInfo(
                filename = filename,
                path = file.absolutePath,
                status = ScriptStatus.ERROR,
                error = e.message ?: e.toString()
            )
            logger.error("Unexpected error loading $filename: ${e.message}", e)
        }
    }

    fun removeFile(file: File) {
        val filename = file.name
        registry.unregisterBySource(filename)
        scriptStatusMap.remove(filename)
        logger.info("Removed script $filename from registry")
    }

    private fun createDefaultSampleScripts() {
        val welcomeScript = File(automationsDir, "welcome_automations.kts")
        if (!welcomeScript.exists()) {
            welcomeScript.writeText(
                """
                import io.homekotlin.dsl.*
                import kotlin.time.Duration.Companion.seconds
                import kotlin.time.Duration.Companion.minutes

                class WelcomeAutomations : HomeScript() {
                    override fun configure() {
                        automation("Living Room Evening Motion") {
                            description("Turns on living room light when motion is detected after dark")

                            trigger.state("binary_sensor.living_room_motion", to = "on")
                            condition { isSunBelowHorizon() }

                            action {
                                log.info("Motion detected after dark! Turning on living room lights.")
                                services.light.turnOn("light.living_room", brightness = 200)
                            }
                        }

                        automation("Front Door Arrival Alert") {
                            description("Notifies when the front door is opened")

                            trigger.state("binary_sensor.front_door", to = "on")

                            action {
                                log.info("Front door opened!")
                                services.notify.send("Front door was opened", title = "Home Security")
                            }
                        }

                        automation("High Temperature Cooling") {
                            description("Turns on cooling if living room exceeds 24°C")

                            trigger.numeric("sensor.living_room_temperature", above = 24.0)

                            action {
                                log.info("Living room temperature is high, adjusting climate target.")
                                services.climate.setTemperature("climate.living_room", 21.0)
                            }
                        }

                        every(30.minutes) {
                            val temp = states["sensor.living_room_temperature"]?.state
                            log.info("Periodic health check: Living room temp is ${'$'}temp°C")
                        }
                    }
                }

                WelcomeAutomations()
                """.trimIndent()
            )
            logger.info("Created default sample script: ${welcomeScript.absolutePath}")
        }
    }
}
