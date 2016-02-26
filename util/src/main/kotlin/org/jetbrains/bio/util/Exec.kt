package org.jetbrains.bio.util

import org.apache.log4j.Logger
import org.jetbrains.bio.ext.div
import org.jetbrains.bio.ext.outputStream
import org.jetbrains.bio.ext.withTempDirectory
import java.nio.file.Path

/**
 * @author Evgeny Kurbatsky
 * @since 11/06/15
 */
class Exec(vararg args: String) {

    private val processBuilder: ProcessBuilder = ProcessBuilder(*args)

    fun directory(path: Path): Exec {
        processBuilder.directory(path.toFile())
        return this
    }

    fun runAndRedirectOutput(path : Path) {
        processBuilder.redirectOutput(path.toFile())
        run(false, false)
    }

    fun runWithLog() = run(true, false)

    fun runWithStdout() = run(false, true)

    private fun run(writeToLog: Boolean, inheritIO : Boolean) {
        val command = processBuilder.command().joinToString(" ")
        LOG.info("Process: $command")

        if (writeToLog) {
            processBuilder.redirectErrorStream(true)
        }
        if (inheritIO) {
            processBuilder.inheritIO()
        }
        val process = processBuilder.start()
        if (writeToLog) {
            process.inputStream.bufferedReader().use {
                for (line in it.lineSequence()) {
                    LOG.info(line)
                }
            }
        }

        val exitCode = process.waitFor()
        if (exitCode != 0) {
            throw RuntimeException("Process stopped with exit code $exitCode")
        }
    }

    companion object {
        private val LOG = Logger.getLogger(Exec::class.java)

        @JvmStatic fun make(vararg args: String): Exec {
            return Exec(*args)
        }

        @JvmStatic fun make(path: Path, vararg args: String): Exec {
            val list = arrayListOf(path.toString())
            list.addAll(args)
            return Exec(*list.toTypedArray())
        }
    }
}

/**
 * A sweeter extended version of the [Exec] API.
 */
fun Path.run(vararg args: Any, log: Boolean = false,
             init: Exec.() -> Unit = {}) {
    val exe = Exec(toString(), *args.map { it.toString() }.toTypedArray())
    exe.init()
    if (log) {
        exe.runWithLog()
    } else {
        exe.runWithStdout()
    }
}

/**
 * A helper for fetching package resources in a safe manner.
 *
 * Example:
 *
 *     private class Proxy
 *
 *     withResource(Proxy::class.java, ...) { ... }
 *
 * @param proxy class used for fetching resources
 * @param name path to resource without the starting '/', e.g. "run_sleuth.R".
 * @param block DWYW.
 */
inline fun withResource(proxy: Class<*>, name: String, block: (Path) -> Unit) {
    val resource = proxy.getResource("/$name")
    withTempDirectory(proxy.simpleName) { dir ->
        val path = dir / name
        path.outputStream().use {
            resource.openStream().copyTo(it)
        }

        block(path)
    }
}
