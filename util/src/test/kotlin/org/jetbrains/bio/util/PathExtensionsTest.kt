package org.jetbrains.bio.util

import org.jetbrains.bio.ext.*
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals

class PathExtensionsTest {
    var recalcFlag = AtomicInteger()
    var readFlag = AtomicInteger()

    @Before fun setup() {
        recalcFlag.set(0)
        readFlag.set(0)
    }

    @Test fun testName() {
        assertEquals("to", "/path/to/".toPath().name)
        assertEquals("foo", "/path/to/foo".toPath().name)
        assertEquals("foo.bar", "/path/to/foo.bar".toPath().name)
    }

    @Test fun testStem() {
        assertEquals("to", "/path/to/".toPath().stem)
        assertEquals("foo", "/path/to/foo".toPath().stem)
        assertEquals("foo", "/path/to/foo.bar".toPath().stem)
        assertEquals("foo.bar", "/path/to/foo.bar.baz".toPath().stem)
    }

    @Test fun testWithName() {
        assertEquals("/path/to/boo".toPath(),
                     "/path/to/foo.bar".toPath().withName("boo"))
    }

    @Test fun testWithExtension() {
        assertEquals("/path/to/foo.baz".toPath(),
                     "/path/to/foo.bar".toPath().withExtension("baz"))
        assertEquals("/path/to/foo.e.baz".toPath(),
                     "/path/to/foo.e.bar".toPath().withExtension("baz"))
        assertEquals("/path/to/foo.baz".toPath(),
                     "/path/to/foo".toPath().withExtension("baz"))
    }

    @Test fun testWithStem() {
        assertEquals("/path/to/bar.baz".toPath(),
                     "/path/to/foo.baz".toPath().withStem("bar"))
        assertEquals("/path/to/bar.baz".toPath(),
                     "/path/to/foo.baz".toPath().withStem("bar"))
        assertEquals("/path/to/bar".toPath(),
                     "/path/to/foo".toPath().withStem("bar"))
    }

    @Test fun testGlob() {
        withTempDirectory("glob") { path ->
            assertEquals(emptyList(), path.glob("*"))

            val dummy = byteArrayOf()
            (path / "foo.txt").write(dummy)
            (path / "bar.zip").write(dummy)

            assertEquals(path.list().toSet(), path.glob("*").toSet())
            assertEquals(listOf(path / "foo.txt"), path.glob("*.txt"))
        }
    }

    @Test fun testGlobRecursive() {
        withTempDirectory("glob") { path ->
            assertEquals(emptyList(), path.glob("*"))
            val subdir = path / "foo" / "bar"
            subdir.createDirectories()

            val dummy = byteArrayOf()
            (subdir / "boo.txt").write(dummy)
            (path / "baz.zip").write(dummy)

            assertEquals(setOf(subdir / "boo.txt", path / "baz.zip"), path.glob("**").toSet())
            assertEquals(setOf(subdir / "boo.txt"), path.glob("**/*.txt").toSet())
        }
    }

    @Test fun checkOrRecalculate() {
        withTempDirectory("annotations") {
            val lockedPath = it / "locked.txt"
            execConcurrently { id ->
                lockedPath.checkOrRecalculate("thread #$id") { output ->
                    Thread.sleep(10)
                    output.let { it.write(byteArrayOf(0, 1, 2)) }
                    recalcFlag.getAndIncrement()
                    output
                }
            }
            assertEquals(1, recalcFlag.get(), "A single block should've been executed!")
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun checkOrRecalculate_IgnoredTmpPath() {
        withTempDirectory("annotations") {
            (it / "foo.txt").checkOrRecalculate("") {
                // let's ignore path wrapper
                it
            }
        }
    }

    @Test fun checkOrRecalculate_AccessedTmpPath() {
        withTempDirectory("annotations") {
            (it / "foo.txt").checkOrRecalculate("") {
                it.path
                it
            }
        }
    }

    @Test fun readOrRecalculate() {
        withTempDirectory("annotations") {
            val lockedPath = it / "locked.txt"
            execConcurrently { id ->
                lockedPath.readOrRecalculate(
                        { readFlag.getAndIncrement() },
                        {  output ->
                            output.let {
                                Thread.sleep(10)
                                lockedPath.write(byteArrayOf(0, 1, 2))
                                recalcFlag.getAndIncrement()
                            } to Unit
                        },
                        "thread #$id")
            }
            assertEquals(1, recalcFlag.get(), "A single block should've been executed!")
            assertEquals(1, readFlag.get())
        }
    }

    @Test fun checkOrRecalculateLeaveEmptyFile() {
        withTempDirectory("annotations") {
            val lockedPath = it / "locked.txt"
            execConcurrently { id ->
                lockedPath.checkOrRecalculate("thread #$id") { output ->
                    Thread.sleep(10)
                    // No write leave file empty
                    recalcFlag.getAndIncrement()
                    output
                }

            }
            assertEquals(2, recalcFlag.get())
        }
    }

    @Test fun checkOrRecalculateExistingFile() {
        withTempDirectory("annotations") {
            val lockedPath = it / "locked.txt"
            lockedPath.write(byteArrayOf(0, 1, 2))
            execConcurrently { id ->
                lockedPath.checkOrRecalculate("thread #$id") { output ->
                    Thread.sleep(10)
                    // No write leave file empty
                    recalcFlag.getAndIncrement()
                    output
                }
            }
            assertEquals(0, recalcFlag.get())
        }
    }

    @Test fun readOrRecalculateExistingFile() {
        withTempDirectory("annotations") {
            val lockedPath = it / "locked.txt"
            lockedPath.write(byteArrayOf(0, 1, 2))
            execConcurrently { id ->
                lockedPath.readOrRecalculate(
                        { readFlag.getAndIncrement() },
                        { output ->
                            output.let { path ->
                                Thread.sleep(10)
                                // No write leave file empty
                                recalcFlag.getAndIncrement()
                            } to Unit
                        }, "thread #$id")
            }
            assertEquals(0, recalcFlag.get())
            assertEquals(2, readFlag.get())
        }
    }

    fun execConcurrently(task: (Int) -> Unit) {
        if (Runtime.getRuntime().availableProcessors() == 1) {
            return  // cannot test if no parallelism.
        }

        val executor = Executors.newFixedThreadPool(2)
        var latch = CountDownLatch(2)  // ensures concurrency.
        for (i in 1..2) {
            executor.submit {
                latch.countDown()
                task(i)
            }
        }

        executor.shutdown()
        executor.awaitTermination(1, TimeUnit.DAYS)
    }

    @Test fun divStringString() {
        assertEquals( "foo/boo", ("foo" / "boo").toString() )
    }
    @Test fun divStringPath() {
        assertEquals( "foo/boo", ("foo" / "boo".toPath()).toString() )
    }
    @Test fun divPathString() {
        assertEquals( "foo/boo", ("foo".toPath() / "boo").toString() )
    }
    @Test fun divPathPath() {
        assertEquals( "foo/boo", ("foo".toPath() / "boo".toPath()).toString() )
    }

    @Test fun listFiles_Dir() {
        val actual = withTempDirectory("foo") { dir ->
            (dir / "file1.txt").touch()
            (dir / "file2.txt").touch()
            (dir / "boo" / "boo1.txt")
            (dir / "doo")

            dir.list().map { it.name }
        }

        assertEquals(listOf("file1.txt", "file2.txt"), actual.sorted())
    }

    @Test fun gzipInputOutput() {
        withTempFile("test", ".gz") { path ->
            path.bufferedWriter().use { it.write("Hello, world!") }
            path.bufferedReader().use {
                assertEquals("Hello, world!", it.readText())
            }
        }
    }

    @Test fun zipInputOutput() {
        withTempFile("test", ".zip") { path ->
            path.bufferedWriter().use { it.write("Hello, world!") }
            path.bufferedReader().use {
                assertEquals("Hello, world!", it.readText())
            }
        }
    }
}