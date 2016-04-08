package org.jetbrains.bio.histones

import org.jetbrains.bio.ext.*
import org.jetbrains.bio.util.Configuration
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*

/**
 * @author Roman.Chernyatchik
 *
 * Used for #719 related cleanup
 */

object CleanupCachesExperiment {
    @JvmStatic fun main(args: Array<String>) {
        val cachesRoot = Configuration.cachePath

        val problemFiles = ArrayList<Path>();

        scanEmptyFiles(cachesRoot, problemFiles)

        println("========================")
        println("========================")
        val cachesRootStr = cachesRoot.toAbsolutePath().toString()

        println("Problem files ${problemFiles.size}")
        val itemsToShow = problemFiles.take(20)
        itemsToShow.forEachIndexed { i, path ->
            println("  %3d: ${makeRelativeTo(path, cachesRootStr)}".format(i))
        }
        if (problemFiles.size > 20) {
            println("  ...")
        }

        if (problemFiles.isNotEmpty()) {
            val currentDir = Paths.get("").toAbsolutePath()

            // Log
            val logPath = currentDir / "bad_caches.log"
            logPath.bufferedWriter().use { bf ->
                problemFiles.forEachIndexed { i, path ->
                    bf.write("  %3d: ${makeRelativeTo(path, cachesRootStr)}\n".format(i))
                    bf.write("      size:     ${path.size}\n")
                    bf.write("      modified: ${path.lastModifiedTime}\n")
                    bf.newLine()
                }
            }
            println("Log file saved to: $logPath")

            // Cleanup script
            val cleanupPath = currentDir / "bad_caches_cleanup.sh"
            cleanupPath.bufferedWriter().use { bf ->
                problemFiles.forEach { bf.write("rm -rf ${it.toAbsolutePath()}\n") }
            }
            println("Cleanup script saved to: $cleanupPath")
        }
    }

    private fun scanEmptyFiles(root: Path, problemFiles: ArrayList<Path>) {
        val files = root.glob("**/*").filter { path -> path.extension != "lock" }

        println("=== Check for Empty files ===")
        val size = files.size
        files.forEachIndexed { i, path ->
            println(">>> $i of $size: ${path.toAbsolutePath()}")

            if (path.size.toBytes() != 0L) {
                println("     [OK] ${path.fileName}")
            } else {
                problemFiles.add(path)
                println("     [FAIL] Empty file: ${path.fileName}")
            }
        }
    }

    private fun makeRelativeTo(path: Path, cachesRootStr: String): String {
        val relativePath = path.toAbsolutePath().toString().replace(cachesRootStr, ".")
        return relativePath
    }

}

