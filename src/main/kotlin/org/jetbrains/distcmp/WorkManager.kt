package org.jetbrains.distcmp

import jline.TerminalFactory
import me.tongfei.progressbar.ProgressBar
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

object WorkManager {
    private var gatherting = AtomicBoolean(true)
    private val cpuExecutor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors() - 1)
    private val progressBar = ProgressBar("", 1, 300)
    private var totalScheduled = AtomicInteger(0)
    private val ioExecutor = Executors.newSingleThreadExecutor()

    fun startGathering() {
        progressBar.maxHint(-1)
        progressBar.start()
    }

    fun submit(workTitle: String, work: () -> Unit) {
        cpuExecutor.submit {
            setProgressMessage(workTitle)
            work()
            progressBar.step()
        }

        totalScheduled.incrementAndGet()
        if (!gatherting.get()) {
            updateProgressMaxHint()
        }
    }

    fun io(work: () -> Unit) {
        ioExecutor.submit(work)
    }

    fun waitDone() {
        gatherting.set(false)

        updateProgressMaxHint()
        cpuExecutor.shutdown()
        cpuExecutor.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS)

        ioExecutor.shutdown()
        ioExecutor.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS)
    }

    private fun updateProgressMaxHint() {
        progressBar.maxHint(totalScheduled.get().toLong())
    }

    fun reportDone() {
        progressBar.stepTo(totalScheduled.get().toLong())
        setProgressMessage("$lastId files, ${itemsByDigest.size} unique, $diffs diffs ($abortedDiffs aborted)")
        progressBar.stop()
    }

    fun setProgressMessage(msg: String) {
        val width = TerminalFactory.get().width - 55
        progressBar.extraMessage = msg.takeLast(width).padEnd(width)
    }
}