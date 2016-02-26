package org.jetbrains.bio.util

import org.apache.log4j.Logger
import org.jetbrains.bio.ext.*
import java.io.IOException
import java.nio.file.Path

private fun String.toDirectoryOrFail(): Path {
    val value = System.getProperty(this)
    checkNotNull(value) { "missing property $this" }
    val path = value.trim().toPath()
    check(path.isDirectory || path.notExists) { "$this is not a directory" }
    return path
}

/**
 * Project-wide configuration container.
 *
 * @author Sergei Lebedev
 * @since 01/05/15
 */
object Configuration {
    private val LOG = Logger.getLogger(Configuration::class.java)

    @JvmStatic
    fun findSourceRoot(): Path? {
        val classPath = Configuration::class.java.getResource("").path.replaceFirst("file:", "").replaceFirst("!.*", "")
        var rootDir: Path? = classPath.toPath()
        do {
            rootDir = rootDir!!.parent
        } while (rootDir != null && (rootDir / "settings.gradle").notExists)
        return rootDir
    }

    init {
        val projectSourceRoot = findSourceRoot()
        if (projectSourceRoot != null) {
            val configPath = projectSourceRoot / "config.properties"
            if (configPath.isReadable) {
                val properties = System.getProperties()
                try {
                    properties.load(configPath.inputStream())
                } catch (e: IOException) {
                    LOG.error(e)
                }
            }
        }
    }

    val genomesPath: Path by lazy(LazyThreadSafetyMode.NONE) { "genomes.path".toDirectoryOrFail() }
    val rawDataPath: Path by lazy(LazyThreadSafetyMode.NONE) { "raw.data.path".toDirectoryOrFail() }
    val experimentsPath: Path by lazy(LazyThreadSafetyMode.NONE) { "experiments.path".toDirectoryOrFail() }
    val cachePath: Path by lazy(LazyThreadSafetyMode.NONE) { experimentsPath / "cache" }
    val geoSamplesPath: Path get() = rawDataPath / "geo-samples"
}