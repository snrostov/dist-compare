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
    private val progressBar: ProgressBar? = if (showProgress) ProgressBar("", 1, 300) else null
    private val totalScheduled = AtomicInteger(0)
    private val toDoWork = AtomicInteger(0)
    private val toDoIO = AtomicInteger(0)
    private val ioExecutor = Executors.newSingleThreadExecutor()

    fun startGathering() {
        progressBar?.maxHint(-1)
        progressBar?.start()
    }

    fun submit(workTitle: String, work: () -> Unit) {
        cpuExecutor.submit {
            try {
                setProgressMessage(workTitle)
                work()
                progressBar?.step()
                val decrementAndGet = toDoWork.decrementAndGet()
                if (!gatherting.get()) {
                    if (decrementAndGet == 0) {
                        cpuExecutor.shutdown()
                    }
                }
            } catch (t: Throwable) {
                t.printStackTrace()
            }
        }

        totalScheduled.incrementAndGet()
        toDoWork.incrementAndGet()
        if (!gatherting.get()) {
            updateProgressMaxHint()
        }
    }

    fun io(work: () -> Unit) {
        ioExecutor.submit {
            try {
                work()
                val decrementAndGet = toDoIO.decrementAndGet()
                if (!gatherting.get()) {
                    if (decrementAndGet == 0 && cpuExecutor.isShutdown) {
                        ioExecutor.shutdown()
                    }
                }
            } catch (t: Throwable) {
                t.printStackTrace()
            }
        }
        toDoIO.incrementAndGet()
    }

    fun waitDone() {
        gatherting.set(false)

        updateProgressMaxHint()

        cpuExecutor.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS)
        ioExecutor.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS)
    }

    private fun updateProgressMaxHint() {
        progressBar?.maxHint(totalScheduled.get().toLong())
    }

    fun reportDone() {
        progressBar?.stepTo(totalScheduled.get().toLong())
        setProgressMessage("$lastId files, ${itemsByDigest.size} unique, $diffs diffs ($abortedDiffs aborted)")
        progressBar?.stop()
    }

    fun setProgressMessage(msg: String) {
        val width = TerminalFactory.get().width - 55
        progressBar?.extraMessage = msg.takeLast(width).padEnd(width)
    }
}