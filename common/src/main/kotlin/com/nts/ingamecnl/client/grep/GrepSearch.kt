package com.nts.ingamecnl.client.grep

import com.nts.ingamecnl.client.log.LogPage
import com.nts.ingamecnl.client.log.LogRecord
import com.nts.ingamecnl.client.log.LogSnapshot
import net.minecraft.client.Minecraft
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

internal class GrepPageResult(
    val page: LogPage,
    val mask: LongArray,
    val count: Int,
)

internal class GrepResult(
    val query: GrepQuery,
    val pages: List<GrepPageResult>,
    val totalMatches: Long,
    val minTimeMillis: Long?,
    val maxTimeMillis: Long?,
    val maxSequence: Long,
) {
    private val cumulative = LongArray(pages.size)

    init {
        var running = 0L
        for (index in pages.indices) {
            running += pages[index].count
            cumulative[index] = running
        }
    }

    fun recordAt(index: Long): LogRecord? {
        if (index < 0 || index >= totalMatches) return null
        var low = 0
        var high = pages.lastIndex
        while (low < high) {
            val mid = (low + high) ushr 1
            if (index < cumulative[mid]) high = mid else low = mid + 1
        }
        val page = pages[low]
        val pageBase = if (low == 0) 0L else cumulative[low - 1]
        val slot = findSetBit(page.mask, (index - pageBase).toInt()) ?: return null
        return page.page.records.getOrNull(slot)
    }

    private fun findSetBit(mask: LongArray, ordinal: Int): Int? {
        var remaining = ordinal
        for (wordIndex in mask.indices) {
            val word = mask[wordIndex]
            val bits = java.lang.Long.bitCount(word)
            if (remaining >= bits) {
                remaining -= bits
                continue
            }
            var value = word
            while (true) {
                val lowest = value and -value
                if (remaining == 0) {
                    return wordIndex * 64 + java.lang.Long.numberOfTrailingZeros(lowest)
                }
                value = value xor lowest
                remaining--
            }
        }
        return null
    }
}

internal sealed interface GrepOutcome {
    data class Completed(val result: GrepResult) : GrepOutcome
    data class Failed(val message: String) : GrepOutcome
}

internal class GrepTask internal constructor(
    private val snapshot: LogSnapshot,
    private val query: GrepQuery,
) {
    private val cancelled = AtomicBoolean(false)

    @Volatile
    private var delivered = false

    fun cancel() {
        cancelled.set(true)
    }

    internal fun isCancelled(): Boolean = cancelled.get()

    internal fun deliver(outcome: GrepOutcome, callback: (GrepOutcome) -> Unit) {
        if (cancelled.get() || delivered) return
        delivered = true
        callback(outcome)
    }

    internal fun run(): GrepOutcome {
        val keyword = query.keyword
        val asciiKeyword = keyword.all { it.code < 128 }
        return if (GrepNative.available && asciiKeyword) {
            try {
                runNative()
            } catch (error: Throwable) {
                System.err.println("[ingamecnl] native grep failed, using JVM: ${error.message}")
                runJvm()
            }
        } else {
            runJvm()
        }
    }

    private fun runJvm(): GrepOutcome {
        val matches = ArrayList<GrepPageResult>()
        var total = 0L
        var minTime = Long.MAX_VALUE
        var maxTime = Long.MIN_VALUE
        var maxSequence = -1L
        for (page in snapshot.pages) {
            if (cancelled.get()) return GrepOutcome.Failed("cancelled")
            val mask = LongArray((page.count + 63) / 64)
            var pageMatches = 0
            for (slot in 0 until page.count) {
                if ((slot and 63) == 0 && cancelled.get()) return GrepOutcome.Failed("cancelled")
                val record = page.records[slot] ?: continue
                if (record.sequence < snapshot.fromSequence || record.sequence > snapshot.toSequence) continue
                if (record.timeMillis < minTime) minTime = record.timeMillis
                if (record.timeMillis > maxTime) maxTime = record.timeMillis
                if (record.sequence > maxSequence) maxSequence = record.sequence
                if (query.matches(record)) {
                    mask[slot ushr 6] = mask[slot ushr 6] or (1L shl (slot and 63))
                    pageMatches++
                }
            }
            if (pageMatches > 0) {
                matches.add(GrepPageResult(page, mask, pageMatches))
                total += pageMatches
            }
        }
        return GrepOutcome.Completed(
            GrepResult(
                query = query,
                pages = matches,
                totalMatches = total,
                minTimeMillis = if (minTime == Long.MAX_VALUE) null else minTime,
                maxTimeMillis = if (maxTime == Long.MIN_VALUE) null else maxTime,
                maxSequence = maxSequence,
            ),
        )
    }

    private fun runNative(): GrepOutcome {
        val workspace = GrepNative.workspace() ?: return runJvm()
        workspace.use {
            val needle = query.keyword.toByteArray(Charsets.US_ASCII)
            val matches = ArrayList<GrepPageResult>()
            var total = 0L
            var minTime = Long.MAX_VALUE
            var maxTime = Long.MIN_VALUE
            var maxSequence = -1L
            for (page in snapshot.pages) {
                if (cancelled.get()) return GrepOutcome.Failed("cancelled")
                val mask = LongArray((page.count + 63) / 64)
                var pageMatches = 0
                for (groupStart in 0 until page.count step 64) {
                    if (cancelled.get()) return GrepOutcome.Failed("cancelled")
                    val batch = GrepNative.Batch(workspace, groupStart, needle)
                    for (slot in groupStart until minOf(groupStart + 64, page.count)) {
                        if ((slot and 63) == 0 && cancelled.get()) return GrepOutcome.Failed("cancelled")
                        val record = page.records[slot] ?: continue
                        if (record.sequence < snapshot.fromSequence || record.sequence > snapshot.toSequence) continue
                        if (record.timeMillis < minTime) minTime = record.timeMillis
                        if (record.timeMillis > maxTime) maxTime = record.timeMillis
                        if (record.sequence > maxSequence) maxSequence = record.sequence
                        if (record.level != query.level) continue
                        if (query.startMillis != null && record.timeMillis < query.startMillis) continue
                        if (query.endMillis != null && record.timeMillis > query.endMillis) continue

                        val message = record.message
                        var matched = if (message.all { it.code < 128 }) {
                            !batch.add(message.toByteArray(Charsets.US_ASCII), slot)
                        } else {
                            query.matches(record)
                        }
                        val throwable = record.throwableText
                        if (!matched && throwable != null) {
                            matched = if (throwable.all { it.code < 128 }) {
                                !batch.add(throwable.toByteArray(Charsets.US_ASCII), slot)
                            } else {
                                query.matches(record)
                            }
                        }
                        if (matched) {
                            mask[slot ushr 6] = mask[slot ushr 6] or (1L shl (slot and 63))
                            pageMatches++
                        }
                    }
                    val nativeMask = batch.takeMask()
                    for (bit in 0 until 64) {
                        if ((nativeMask ushr bit) and 1L == 0L) continue
                        val slot = groupStart + bit
                        if (slot >= page.count) continue
                        val word = slot ushr 6
                        val flag = 1L shl (slot and 63)
                        if (mask[word] and flag == 0L) {
                            mask[word] = mask[word] or flag
                            pageMatches++
                        }
                    }
                }
                if (pageMatches > 0) {
                    matches.add(GrepPageResult(page, mask, pageMatches))
                    total += pageMatches
                }
            }
            return GrepOutcome.Completed(
                GrepResult(
                    query = query,
                    pages = matches,
                    totalMatches = total,
                    minTimeMillis = if (minTime == Long.MAX_VALUE) null else minTime,
                    maxTimeMillis = if (maxTime == Long.MIN_VALUE) null else maxTime,
                    maxSequence = maxSequence,
                ),
            )
        }
    }
}

internal object GrepSearch {
    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "ingamecnl-grep").apply { isDaemon = true }
    }

    fun submit(snapshot: LogSnapshot, query: GrepQuery, callback: (GrepOutcome) -> Unit): GrepTask {
        val task = GrepTask(snapshot, query)
        executor.execute {
            val outcome = try {
                task.run()
            } catch (error: Throwable) {
                GrepOutcome.Failed(error.message ?: error.javaClass.simpleName)
            }
            Minecraft.getInstance().execute {
                if (!task.isCancelled()) task.deliver(outcome, callback)
            }
        }
        return task
    }
}
