package org.jetbrains.bio.util

import org.apache.log4j.Logger
import org.jetbrains.bio.ext.*
import java.net.URLClassLoader
import java.net.URLDecoder
import java.nio.file.Path
import java.util.function.Consumer
import java.util.jar.JarFile

class ClassPathEntry(private val entry: String) {

    fun process(consumer: Consumer<String>) {
        val path = entry.toPath()
        if (path.notExists) {
            return
        }
        if (path.isDirectory) {
            processFolder("", path, consumer)
        } else {
            processJar(entry, consumer)
        }
    }

    companion object {
        private val CLASS_FILE_SUFFIX = ".class"

        private fun processFolder(curPath: String, parent: Path, consumer: Consumer<String>) {
            val prefix = if (curPath.isEmpty()) "" else curPath + '.'
                for (f in parent.list()) {
                    if (f.name.endsWith(CLASS_FILE_SUFFIX)) {
                        consumer.accept(prefix + f.name.substringBefore(CLASS_FILE_SUFFIX))
                    } else if (f.isDirectory) {
                        processFolder(prefix + f.name, f, consumer)
                    }
            }
        }

        private fun processJar(entry: String, consumer: Consumer<String>) {
            JarFile(entry).use { jarFile ->
                val entries = jarFile.entries()
                while (entries.hasMoreElements()) {
                    val jarEntry = entries.nextElement()
                    val name = jarEntry.name
                    if (name.endsWith(CLASS_FILE_SUFFIX)) {
                        consumer.accept(name.substringBefore(CLASS_FILE_SUFFIX).replace("/|\\\\".toRegex(), "."))
                    }
                }
            }
        }
    }
}

object ClassProcessor {
    private val LOG = Logger.getLogger(ClassProcessor::class.java)
    private val classLoaders = hashSetOf<ClassLoader>(ClassLoader.getSystemClassLoader())

    /**
     * This method tries to initialize given class with constructor(), getInstance() or INSTANCE or ourInstance fields and methods
     * @return Object instance if succeeded, null otherwise
     */
    @JvmStatic
    fun tryToInstantiate(clazzz: Class<*>): Any? {
        try {
            return clazzz.newInstance()
        } catch (e: Exception) {
            // Constructor is private or exception in it
            LOG.debug(e)
        }
        try {
            val field = clazzz.getField("INSTANCE")
            if (field != null) {
                return field.get(clazzz)
            }
        } catch (e: Exception) {
            // Cannot call getInstance
            LOG.error(e)
        }

        // Give up, cannot initialize
        LOG.debug("Failed to create instance for class: " + clazzz.name)
        return null
    }

    @JvmStatic
    fun processClasses(consumer: Consumer<String>) {
        classLoaders.flatMap {
            (it as URLClassLoader).urLs
                    .filter { it.protocol == "file" }
                    .map { ClassPathEntry(URLDecoder.decode(it.path, "UTF-8")) }
        }.forEach { it.process(consumer) }
    }
}

