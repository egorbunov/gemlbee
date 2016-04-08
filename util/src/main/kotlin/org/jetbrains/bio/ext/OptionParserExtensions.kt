package org.jetbrains.bio.ext

import joptsimple.*
import org.jetbrains.bio.util.Logs
import java.io.StringWriter
import java.nio.file.Path
import java.util.*

/**
 * NOTE:
 * Do not implement get[], because is has ambiguous meaning: [OptionSet#valueOf] or [OptionSet#valuesOf]
 */
operator fun OptionSet.contains(option: String): Boolean = has(option)

fun OptionParser.parse(args: Array<String>, block: (OptionSet) -> Unit) {
    try {
        // TODO fix me, once https://youtrack.jetbrains.com/issue/KT-11398 is fixed!
        // acceptsAll(listOf("h", "?"), "show help").forHelp()

        val options = parse(*args)
        if (options.nonOptionArguments().isNotEmpty()) {
            fail("Unrecognized options: ${options.nonOptionArguments()}")
        }

        if ("help" in options) {
            System.err.print("Arguments: ")
            System.err.println(Arrays.toString(args))
            printHelpOn(System.err)
            System.exit(0)
        }

        block(options)
    } catch (e: OptionException) {
        fail(e)
    }
}

private fun OptionParser.fail(e: OptionException) {
    return fail(e.cause?.message ?: e.message!!)
}

fun OptionParser.fail(message: String) {
    val help = StringWriter()
    printHelpOn(help)
    Logs.checkOrFail(false, "$message\n${help.toString()}")
}

/**
 * A converter for files or directories.
 *
 * @author Sergei Lebedev
 * @since 27/08/14
 */
abstract class PathConverter : ValueConverter<Path> {
    @Throws(ValueConversionException::class)
    abstract fun check(path: Path)

    override fun convert(value: String): Path {
        val path = value.toPath().toAbsolutePath()
        check(path)
        return path
    }

    override fun valueType() = Path::class.java

    override fun valuePattern(): String? = null

    companion object {
        fun exists(): PathConverter = object : PathConverter() {
            override fun check(path: Path) {
                if (path.notExists) {
                    throw ValueConversionException("Path $path does not exist")
                }
            }
        }

        fun noCheck(): PathConverter = object : PathConverter() {
            @Throws(ValueConversionException::class)
            override fun check(path: Path) {}
        }
    }
}
