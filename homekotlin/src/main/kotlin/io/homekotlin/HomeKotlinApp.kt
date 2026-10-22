package io.homekotlin

import io.homekotlin.codegen.EntityCodeGenerator
import io.homekotlin.config.AppConfig
import io.homekotlin.dsl.HaServices
import io.homekotlin.ha.HaClient
import io.homekotlin.ha.HaStateStore
import io.homekotlin.scripting.AutomationRegistry
import io.homekotlin.scripting.ScriptEngine
import io.homekotlin.scripting.ScriptWatcher
import io.homekotlin.server.IngressServer
import kotlinx.coroutines.*
import org.slf4j.LoggerFactory
import java.io.File
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    val logger = LoggerFactory.getLogger("HomeKotlin")

    logger.info("==================================================")
    logger.info("   Starting HomeKotlin - Home Assistant Automations")
    logger.info("==================================================")

    if (args.contains("--help") || args.contains("-h")) {
        println(
            """
            HomeKotlin - Type-safe, reactive Home Assistant automations in Kotlin
            
            Options:
              --generate-entities   Fetch entities from Home Assistant and generate Entities.kt
              --help, -h            Show this help message
            
            Configuration via environment variables or /data/options.json:
              HA_URL                Home Assistant WebSocket URL (default: ws://localhost:8123/api/websocket)
              HA_TOKEN              Home Assistant Long-Lived Access Token
              SUPERVISOR_TOKEN      Home Assistant Supervisor Token (automatically set in HA Add-on mode)
              AUTOMATIONS_DIR       Directory where .kts scripts reside (default: automations)
              PORT                  Web server Ingress port (default: 8099)
              DEMO_MODE             Run in simulation mode without connecting to live HA (true/false)
            """.trimIndent()
        )
        exitProcess(0)
    }

    val config = AppConfig.load()
    logger.info("Configuration: HA_URL=${config.haUrl}, Port=${config.port}, AutomationsDir=${config.automationsDir}, DemoMode=${config.demoMode}")

    val stateStore = HaStateStore()
    io.homekotlin.generated.Entity.stateStore = stateStore
    val haClient = HaClient(config, stateStore)
    val services = HaServices(haClient)
    val registry = AutomationRegistry()
    val codeGenerator = EntityCodeGenerator(stateStore)

    val automationsFolder = File(config.automationsDir)
    val scriptEngine = ScriptEngine(automationsFolder, registry)
    val scriptWatcher = ScriptWatcher(automationsFolder, scriptEngine)

    if (args.contains("--generate-entities")) {
        logger.info("Generating Entities.kt from current states...")
        runBlocking {
            haClient.start(this)
            delay(2000)
            val outFile = File("src/main/kotlin/io/homekotlin/generated/Entities.kt")
            codeGenerator.generateToFile(outFile)
            logger.info("Generated: ${outFile.absolutePath}")
            exitProcess(0)
        }
    }

    val appScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    // 1. Start Home Assistant Client
    haClient.start(appScope)

    // 2. Load compiled automations and dynamic scripts from disk
    io.homekotlin.automations.CompiledAutomationLoader.loadAll(registry)
    scriptEngine.loadAll()

    // 3. Start script hot-reload file watcher
    scriptWatcher.start(appScope)

    // 4. Start event and timer dispatchers
    registry.startEventDispatcher(appScope, stateStore, services)
    registry.startTimerDispatcher(appScope, stateStore, services)

    // 5. Start Ingress Web Server
    val server = IngressServer(config, haClient, stateStore, registry, scriptEngine, codeGenerator)
    server.start()

    Runtime.getRuntime().addShutdownHook(Thread {
        logger.info("Shutting down HomeKotlin...")
        scriptWatcher.stop()
        server.stop()
        appScope.cancel()
        logger.info("HomeKotlin stopped gracefully.")
    })

    // Keep the main thread alive
    runBlocking {
        while (isActive) {
            delay(10_000)
        }
    }
}
