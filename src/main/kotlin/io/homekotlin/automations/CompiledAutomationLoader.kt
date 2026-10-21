package io.homekotlin.automations

import io.homekotlin.dsl.HomeScript
import io.homekotlin.scripting.AutomationRegistry
import org.slf4j.LoggerFactory
import java.io.File
import java.net.JarURLConnection

object CompiledAutomationLoader {
    private val logger = LoggerFactory.getLogger(CompiledAutomationLoader::class.java)

    fun loadAll(registry: AutomationRegistry, packageName: String = "io.homekotlin.automations") {
        val classes = findHomeScriptClasses(packageName)
        logger.info("Discovered ${classes.size} compiled automation class(es) in $packageName")
        for (clazz in classes) {
            try {
                HomeScript.currentScriptCapture.clear()
                val instance = clazz.getDeclaredConstructor().newInstance()
                instance.configure()

                val sourceName = clazz.simpleName + ".kt"
                for (a in instance.registeredAutomations) {
                    registry.register(a.copy(sourceFile = sourceName))
                }

                while (true) {
                    val captured = HomeScript.currentScriptCapture.poll() ?: break
                    registry.register(captured.copy(sourceFile = sourceName))
                }

                logger.info("Loaded compiled automation class: ${clazz.name}")
            } catch (e: Throwable) {
                logger.error("Failed to initialize automation class ${clazz.name}: ${e.message}", e)
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun findHomeScriptClasses(packageName: String): List<Class<out HomeScript>> {
        val classes = mutableListOf<Class<out HomeScript>>()
        val classLoader = Thread.currentThread().contextClassLoader ?: CompiledAutomationLoader::class.java.classLoader
        val path = packageName.replace('.', '/')

        try {
            val resources = classLoader.getResources(path)
            while (resources.hasMoreElements()) {
                val resource = resources.nextElement()
                when (resource.protocol) {
                    "file" -> {
                        val directory = File(resource.toURI())
                        if (directory.exists() && directory.isDirectory) {
                            directory.walkTopDown()
                                .filter { it.isFile && it.name.endsWith(".class") && !it.name.contains("$") }
                                .forEach { file ->
                                    val relativePath = file.relativeTo(directory).path
                                    val className = packageName + "." + relativePath.removeSuffix(".class").replace(File.separatorChar, '.')
                                    tryLoadClass(className, classLoader)?.let { classes.add(it) }
                                }
                        }
                    }
                    "jar" -> {
                        val jarConnection = resource.openConnection() as? JarURLConnection
                        val jarFile = jarConnection?.jarFile
                        jarFile?.entries()?.iterator()?.forEach { entry ->
                            if (entry.name.startsWith(path) && entry.name.endsWith(".class") && !entry.name.contains("$")) {
                                val className = entry.name.removeSuffix(".class").replace('/', '.')
                                tryLoadClass(className, classLoader)?.let { classes.add(it) }
                            }
                        }
                    }
                }
            }
        } catch (e: Throwable) {
            logger.warn("Error scanning for compiled automations in $packageName: ${e.message}")
        }

        return classes
    }

    @Suppress("UNCHECKED_CAST")
    private fun tryLoadClass(className: String, classLoader: ClassLoader): Class<out HomeScript>? {
        return try {
            val clazz = classLoader.loadClass(className)
            if (HomeScript::class.java.isAssignableFrom(clazz) && !java.lang.reflect.Modifier.isAbstract(clazz.modifiers)) {
                clazz as Class<out HomeScript>
            } else null
        } catch (_: Throwable) {
            null
        }
    }
}
