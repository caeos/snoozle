package org.coner.snoozle.db.util

import assertk.all
import assertk.assertions.*
import io.reactivex.observers.TestObserver
import io.reactivex.schedulers.Schedulers
import org.coner.snoozle.util.PathObservables
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.io.TempDir
import java.nio.file.*
import java.util.concurrent.TimeUnit

class PathObservablesTest {

    @TempDir
    lateinit var directory: Path
    lateinit var observer: TestObserver<WatchEvent<*>>

    val subscriptionDelayMs = 100L // needed because the registration of the watcher needs some time

    @BeforeEach
    fun before() {
        observer = TestObserver()
    }

    @AfterEach
    fun after() {
        observer.dispose()
    }

    @Test
    fun shouldWatchDirectoryRecursively() {
        val observable = PathObservables.watchRecursive(directory)
        observable
                .subscribeOn(Schedulers.io())
                .subscribe(observer)
        Thread.sleep(subscriptionDelayMs)

        val newDir = directory.resolve(Paths.get("a")).toFile().apply {
            mkdirs()
        }
        observer.awaitCount(1)
        val file = newDir.resolve("b").writeText("b")
        observer.awaitCount(2)

        assertk.assertThat(observer.values()).all {
            index(0).all {
                prop(WatchEvent<*>::kind).isEqualTo(StandardWatchEventKinds.ENTRY_CREATE)
                prop(WatchEvent<*>::context).toStringFun().isEqualTo("a")
            }
            index(1).all {
                prop(WatchEvent<*>::kind).isEqualTo(StandardWatchEventKinds.ENTRY_CREATE)
                prop(WatchEvent<*>::context).toStringFun().isEqualTo("b")
            }
        }
    }

    @Test
    fun shouldWatchDirectoryNonRecursively() {
        val observable = PathObservables.watchNonRecursive(directory)
        observable
                .subscribeOn(Schedulers.io())
                .subscribe(observer)
        Thread.sleep(subscriptionDelayMs)

        val a = directory.resolve("a").toFile().apply {
            mkdirs()
        }
        a.resolve("b").writeText("b")

        observer.awaitCount(1)
        Thread.sleep(20) // some extra to make sure "a/b" never emits

        assertk.assertThat(observer.values()).all {
            hasSize(1)
            index(0).all {
                prop(WatchEvent<*>::kind).isEqualTo(StandardWatchEventKinds.ENTRY_CREATE)
                prop(WatchEvent<*>::context).toStringFun().isEqualTo("a")
            }
        }
    }

    /**
     * Test the fix for https://github.com/helmbold/rxfilewatcher/issues/8
     *
     * Previously, the PathObservables.ObservableFactory did not call WatchService.close()
     * before completing its subscription. This caused WatchService instances to leak, which in turn
     * could eventually cause the OS to enforce its limits on the leaky process.
     */
    @Test
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    fun shouldCloseWatchService() {
        val empty = ByteArray(0)
        val cycles = 768 // 768 = largest known default watch limit (windows) * 1.5

        for (i in 0 until cycles) {
            print(">>> $i ")
            val observer = TestObserver<WatchEvent<*>>()
            val watch = PathObservables.watchNonRecursive(directory)
            watch
                    .observeOn(Schedulers.io())
                    .subscribeOn(Schedulers.io())
                    .subscribe(observer)
            Thread.sleep(subscriptionDelayMs)

            directory.resolve(i.toString()).let { noise ->
                Files.write(noise, empty)
            }
            observer.awaitCount(1)
            assertk.assertThat(observer.values()).all {
                index(0).isNotNull()
            }

            observer.dispose()
            println("<<<")
        }
    }
}