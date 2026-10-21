package io.homekotlin.scripting

import kotlinx.coroutines.*
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.file.*
import java.util.concurrent.ConcurrentHashMap

class ScriptWatcher(
    private val automationsDir: File,
    private val scriptEngine: ScriptEngine
) {
    private val logger = LoggerFactory.getLogger(ScriptWatcher::class.java)
    private var watcherJob: Job? = null
    private val pendingReloads = ConcurrentHashMap<String, Job>()

    fun start(scope: CoroutineScope) {
        if (!automationsDir.exists()) {
            automationsDir.mkdirs()
        }

        watcherJob = scope.launch(Dispatchers.IO) {
            try {
                val watchService = FileSystems.getDefault().newWatchService()
                val path = automationsDir.toPath()
                path.register(
                    watchService,
                    StandardWatchEventKinds.ENTRY_CREATE,
                    StandardWatchEventKinds.ENTRY_MODIFY,
                    StandardWatchEventKinds.ENTRY_DELETE
                )

                logger.info("Watching automations directory for hot reload: ${automationsDir.absolutePath}")

                while (isActive) {
                    val key = watchService.take() ?: continue

                    for (event in key.pollEvents()) {
                        val kind = event.kind()
                        if (kind == StandardWatchEventKinds.OVERFLOW) continue

                        @Suppress("UNCHECKED_CAST")
                        val ev = event as WatchEvent<Path>
                        val filename = ev.context().toString()

                        if (!filename.endsWith(".kts") && !filename.endsWith(".kt")) continue
                        if (filename.startsWith(".")) continue

                        val file = File(automationsDir, filename)

                        // Debounce reloads (editors write in bursts)
                        pendingReloads[filename]?.cancel()
                        pendingReloads[filename] = scope.launch {
                            delay(400)
                            if (kind == StandardWatchEventKinds.ENTRY_DELETE || !file.exists()) {
                                logger.info("Detected deletion of script $filename")
                                scriptEngine.removeFile(file)
                            } else {
                                logger.info("Detected changes in script $filename, hot reloading...")
                                scriptEngine.loadFile(file)
                            }
                            pendingReloads.remove(filename)
                        }
                    }

                    val valid = key.reset()
                    if (!valid) break
                }
            } catch (e: Exception) {
                if (e !is CancellationException) {
                    logger.warn("Script watcher terminated: ${e.message}")
                }
            }
        }
    }

    fun stop() {
        watcherJob?.cancel()
        pendingReloads.values.forEach { it.cancel() }
        pendingReloads.clear()
    }
}
