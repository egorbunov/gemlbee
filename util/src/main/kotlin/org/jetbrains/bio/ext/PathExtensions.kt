package org.jetbrains.bio.ext

import com.google.common.collect.ImmutableList
import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVParser
import org.apache.commons.csv.CSVPrinter
import org.apache.log4j.Level
import org.apache.log4j.Logger
import org.jetbrains.bio.util.LockManager
import java.io.*
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.file.*
import java.nio.file.StandardOpenOption.CREATE
import java.nio.file.StandardOpenOption.WRITE
import java.nio.file.attribute.BasicFileAttributes
import java.util.*
import java.util.zip.*

private val LOG = Logger.getLogger("org.jetbrains.bio.ext.PathExtensions")

fun Array<String>.toPath(): Path {
    check(isNotEmpty())
    return Paths.get(this[0], *copyOfRange(1, size))
}

fun String.toPath() = Paths.get(this)

operator fun Path.div(other: String) = div(other.toPath())
operator fun Path.div(other: Path) = resolve(other)
operator fun String.div(other: String) = div(other.toPath())
operator fun String.div(other: Path) = toPath() / other

/** Returns `false` if a file exists and `true` otherwise. */
val Path.notExists: Boolean get() = Files.notExists(this)

/** Returns `true` if a file exists and `false` otherwise. */
val Path.exists: Boolean get() = Files.exists(this)

/** Tests whether a file can be read. */
val Path.isReadable: Boolean get() = Files.isReadable(this)

/** Tests whether a path points to a directory. */
val Path.isDirectory: Boolean get() = Files.isDirectory(this)

/** Tests whether a path is a regular file. */
val Path.isRegularFile: Boolean get() = Files.isRegularFile(this)

/** Returns the name of the file or directory denoted by this path. */
val Path.name: String get() = fileName.toString()

/**
 * Returns the name of the file or directory without extension.
 */
val Path.stem: String get() = name.substringBeforeLast(".$extension")

/**
 * Returns the extension of this path (not including the dot), or
 * an empty string if it doesn't have one.
 */
val Path.extension: String get() = toFile().extension

/**
 * Returns file size in bytes.
 */
val Path.size: Long get() = Files.size(this)

/** Returns a new path with the [name] changed. */
fun Path.withName(newName: String): Path {
    check(name.isNotEmpty())
    return parent / newName
}

/** Returns a new path with the [extension] changed. */
fun Path.withExtension(newExtension: String): Path {
    require(newExtension.isNotEmpty())
    return parent / "$stem.$newExtension"
}

/** Returns a new path with the [stem] changed. */
fun Path.withStem(newStem: String): Path {
    return if (extension.isEmpty()) {
        parent / newStem
    } else {
        parent / "$newStem.$extension"
    }
}

fun Path.walkFileTree(walker: FileVisitor<Path>) {
    Files.walkFileTree(this, walker)
}

fun Path.list(): List<Path> {
    val s = Files.list(this)
    try {
        return ImmutableList.copyOf(s.iterator())
    } finally {
        s.close()
    }
}

fun String.toPathMatcher(): PathMatcher {
    // We enforce glob syntax, because it's human-readable.
    return FileSystems.getDefault().getPathMatcher("glob:$this")
}

/**
 * Recursively glob a directory.
 *
 * Note that the current implementation only supports globbing for *files*.
 */
fun Path.glob(pattern: String): List<Path> {
    check(isDirectory) { "$this is not a directory" }

    val matcher = (toAbsolutePath() / pattern).toString().toPathMatcher()
    val matched = ArrayList<Path>()
    walkFileTree(object : SimpleFileVisitor<Path>() {
        override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
            if (matcher.matches(file)) {
                matched.add(file)
            }

            return FileVisitResult.CONTINUE
        }
    })

    return matched
}

fun Path.createDirectories(): Path {
    Files.createDirectories(this)
    return this
}

fun Path.delete() = Files.delete(this)

fun Path.deleteIfExists() = Files.deleteIfExists(this)

fun Path.deleteDirectory() {
    toFile().deleteRecursively()
}

fun InputStream.copy(path: Path, vararg options: StandardCopyOption) {
    Files.copy(this, path, *options)
}

fun Path.copy(outputStream: OutputStream) {
    Files.copy(this, outputStream)
}

fun Path.move(target: Path, vararg options: StandardCopyOption) {
    Files.move(this, target, *options)
}

fun Path.copy(target: Path, vararg options: StandardCopyOption) {
    Files.copy(this, target, *options)
}

fun Path.lines() = Files.lines(this)

fun Path.readAllBytes() = Files.readAllBytes(this)

fun Path.write(buf: String, vararg options: StandardOpenOption): Path {
    return write(buf.toByteArray(), *options)
}

fun Path.write(buf: ByteArray, vararg options: StandardOpenOption): Path {
    parent.createDirectories()
    return Files.write(this, buf, *options)
}

/**
 * Recalculates a file in a reliable way.
 *
 * If file doesn't exist or is empty it will be
 *
 *  - locked,
 *  - recalculated in tmp file
 *  - moved to this path.
 *
 * As a result we won't get a corrupted (partially written) fil if a process
 * was killed or exited with an unhandled exception.
 *
 * @param recalculate recalculates and stores the data in the file provided by path wrapper.
 *                    Deletes file if exception occurs. Delete file provide by wrapper
 *                    if it shouldn't be moved to locked path
 *                    Looks like: { output -> { path -> doSomething(path) } }
 * @param label human-readable short description of the computation.
 * @return True if was recalculated
 */
fun Path.checkOrRecalculate(label: String,
                            recalculate: (PathWrapper) -> PathWrapper) : Boolean
        = readOrRecalculate({ false }, { recalculate(it) to true }, label)

/**
 * This is to make sure the caller of [checkOrRecalculate(id, recalculate)] doesn't
 * ignore the output path, which is easy to do in Kotlin.
 */
class PathWrapper internal constructor(outputPath: Path) {
    private var accessed = false
    val path: Path by lazy {
        accessed = true
        outputPath
    }

    fun let(block: (Path) -> Unit): PathWrapper {
        block(path)
        return this
    }

    internal fun checkAccessed() {
        require(accessed) {
            "Temp path wasn't accessed, seems there is some bug. You are expected to recalculate " +
            "data in it tmp file to guarantee failure-safe recalculation: ${path.toAbsolutePath()}"
        }
    }
}

/**
 * Almost thread/process-safe method of retrieving results from file or
 * recalculating them and storing in the file.
 *
 * While file is empty only one thread or process can recalculate it.
 * If file exists and not empty read task will be launched
 *
 * @param read attempts to read data from file and throws in case of failure.
 * @param recalculate recalculates and stores the data in the file provided by path wrapper.
 *                    Deletes file if exception occurs. Delete file provide by wrapper
 *                    if it shouldn't be moved to locked path
 *                    Looks like: { output -> { path -> doSomething(path) } }
 * @param label human-readable short description of the computation.
 * @return the data.
 */
fun <T: Any> Path.readOrRecalculate(read: () -> T,
                                    recalculate: (PathWrapper) -> Pair<PathWrapper, T>,
                                    label: String): T {

    parent.createDirectories()

    var result: T? = null

    // Main IDEA:
    // * Initial check if file exists and not empty.
    //   - If so => read with no locks
    //   - Assume that "recalculate" for the file is done in this method with atomic move operation
    //
    // * Real file name used for:
    //  - In-app synchronization
    //
    // * Lock file name used for
    //  - Cross-app file lock
    //
    // * Tmp file name (target file name with "tmpNNNNNN_" prefix, where NNNNNN - random number) for:
    //  - Recalculate data in unique tmp file
    //  - Atomic copy form tmp to real file name.

    val thisPathStr = toAbsolutePath().normalize().toString()

    // Optimization: if target already exist => just read, with no file locks
    // " !=0" check is optional here, in general we don't expect 0 size files here, so
    // it is an additional check for smth unexpected
    if (exists && size != 0L) {
        LOG.trace("$label: No lock required for $thisPathStr")

        result = LOG.time(level = Level.TRACE,
                          message = "$label: Reading from $thisPathStr",
                          block = read)
    } else {
        // Need to recalculate file:
        LockManager.synchronized(thisPathStr) { threadsLock ->
            // tmp file must be on the same storage as target to enable atomic move
            val lockPath = parent / "$fileName.lock"
            val lockPathStr = lockPath.toAbsolutePath().normalize().toString()

            // do lock file & recalculate in tmp
            FileChannel.open(lockPath, setOf(WRITE, CREATE)).use { channel ->
                var fsLock: FileLock?

                try {
                    fsLock = channel.tryLock()
                } catch (e: IOException) {
                    // XXX: In case of any problems replace this using spin lock
                    // with *.lock file existing check. File lock is optional here.
                    throw IllegalStateException(
                            "$label: Failed to acquire a file lock $lockPathStr. " +
                            "If you're using NFS, please ensure that NFS " +
                            "lock daemon is running.", e)
                }

                if (fsLock == null) {
                    LOG.debug("$label: Failed to lock file $lockPathStr, waiting ...")
                    fsLock = channel.lock()
                    LOG.debug("$label: Done waiting.")
                }
                LOG.trace("$label: Lock for $lockPathStr acquired.")

                // Lock acquired in 2 case:
                // * we were fist -> recalculate tmp file and move to target path
                // * we were in waiting list -> most likely everything has been already done,
                //          so just check it.
                // * Size cannot == 0 when using readOrRecalculate(), but if you create empty file
                //      first (e.g. tmp file) and pass it to readOrRecalculate() let's do not be
                //      confused and force recalculate
                if (exists && size != 0L) {
                    LOG.debug("$label: File $thisPathStr is already ready. Size = ${size.asFileSize()}.")

                    // release locks before reading
                    lockPath.delete()
                    fsLock?.release()

                    LOG.trace("$label: Released lock $lockPath")
                    threadsLock.unlock();
                    LOG.trace("$label: Released lock key={$thisPathStr}")

                    result = LOG.time(level = Level.TRACE,
                                      message = "$label: Reading from $thisPathStr",
                                      block = read)
                } else {
                    // keep lock while recalculating
                    if (!exists) {
                        LOG.debug("$label: File $thisPathStr is missing.")
                    } else {
                        LOG.debug("$label: Force recalculate empty $thisPathStr file: size = ${size.asFileSize()}.")
                    }
                    result = LOG.time(level = Level.INFO,
                                      message = "$label: Missing $thisPathStr") {

                        // XXX: Guarantees that file will be recalculated in transaction-like
                        // style only for locked path (this). If recalculates writes other
                        // files, nothing will be done with them

                        val rand = Random().nextInt()
                        val tmpPath = parent / "tmp${rand}_$fileName"
                        val tmpPathStr = tmpPath.toAbsolutePath().normalize().toString()
                        try {
                            LOG.trace("$label: Recalculating in tmp file $tmpPathStr")
                            val (wrapper, res) = recalculate(PathWrapper(tmpPath))
                            wrapper.checkAccessed();
                            if (tmpPath.exists) {
                                if (tmpPath.size == 0L) {
                                    check(false) {
                                        "$label: Recalculate function is expected to return not empty file: $tmpPathStr"
                                    }
                                }
                                // both files on same storage (same folder) -> use atomic move
                                // Captain says: first move, than free lock
                                LOG.trace("$label: Moving tmp file (${tmpPath.name}) to $thisPathStr...")
                                tmpPath.move(this,
                                             StandardCopyOption.ATOMIC_MOVE,
                                             StandardCopyOption.REPLACE_EXISTING)
                                LOG.debug("$label: Done, $name is ready to use. Size = ${size.asFileSize()}.")
                            } else {
                                LOG.trace("$label: Tmp file (${tmpPath.name}) wasn't saved, noting to move.")
                            }

                            res
                        } catch (e: Exception) {
                            LOG.error("$label: Error, cannot recalculate $tmpPathStr.", e)
                            throw e
                        } finally {
                            // Captain says: first delete, than free lock. Other Apps will
                            // acquire lock not on "delete" but on "release" operation.
                            tmpPath.deleteIfExists()
                            LOG.trace("$label: Tmp file deleted $tmpPathStr")

                            lockPath.deleteIfExists()
                            fsLock?.release()
                            LOG.trace("$label: Lock released $lockPath")
                        }
                    }
                }
            }
        }
    }
    return checkNotNull(result)
}

inline fun <T> withTempFile(prefix: String, suffix: String,
                            block: (Path) -> T): T {
    val path = Files.createTempFile(prefix, suffix)
    try {
        return block(path)
    } finally {
        if (path.exists) path.delete()
    }
}

inline fun <T> withTempDirectory(prefix: String, block: (Path) -> T): T {
    val tempDir = Files.createTempDirectory(prefix)
    try {
        return block(tempDir)
    } finally {
        if (tempDir.exists) tempDir.deleteDirectory()
    }
}

/**
 * Returns a buffered input stream for this path.
 */
fun Path.inputStream(vararg options: OpenOption): InputStream {
    val inputStream = Files.newInputStream(this, *options).buffered()
    return when (extension.toLowerCase()) {
        "gz"  -> GZIPInputStream(inputStream)
        "zip" ->
            // This only works for single-entry ZIP files.
            ZipInputStream(inputStream).apply { getNextEntry() }
        else  -> inputStream
    }
}

fun Path.bufferedReader(vararg options: OpenOption): BufferedReader {
    return inputStream(*options).bufferedReader()
}

/**
 * Returns a buffered output stream for this path.
 */
fun Path.outputStream(vararg options: OpenOption): OutputStream {
    val outputStream = Files.newOutputStream(this, *options).buffered()
    return when (extension.toLowerCase()) {
        "gz"  -> GZIPOutputStream(outputStream)
        "zip" -> ZipOutputStream(outputStream).apply {
            // Without this line ZIP file will be corrupted.
            putNextEntry(ZipEntry("fake.txt"))
        }
        else  -> outputStream
    }
}

fun Path.bufferedWriter(vararg options: OpenOption): BufferedWriter {
    // Use Path#toAbsolutePath(), otherwise it may have no parent
    toAbsolutePath().parent.createDirectories()
    return outputStream(*options).bufferedWriter()
}

@JvmOverloads fun Path.csvPrinter(format: CSVFormat = CSVFormat.TDF): CSVPrinter {
    return format.print(bufferedWriter())
}

@JvmOverloads fun Path.csvParser(format: CSVFormat = CSVFormat.TDF): CSVParser {
    return format.parse(bufferedReader())
}

fun Path.touch(): Path = when {
    exists -> this
    else   -> write(byteArrayOf())
}
