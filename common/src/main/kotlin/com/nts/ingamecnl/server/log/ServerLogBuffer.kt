package com.nts.ingamecnl.server.log

import com.nts.ingamecnl.log.LogRecord

internal class ServerLogBuffer(
    private val recordCapacity: Int = RECORD_CAPACITY,
    private val characterCapacity: Long = CHARACTER_CAPACITY,
) {
    private val lock = Any()
    private val records = arrayOfNulls<LogRecord>(recordCapacity.coerceAtLeast(1))
    private var head = 0
    private var count = 0
    private var characters = 0L
    private var nextSequence = 1L
    private var dropped = 0L

    fun append(record: LogRecord) {
        synchronized(lock) {
            if (count == records.size) removeOldest()
            val sequence = nextSequence++
            records[(head + count) % records.size] = record.copy(sequence = sequence)
            count++
            characters += record.textSize
            while (characters > characterCapacity && count > 1) removeOldest()
        }
    }

    fun snapshot(sinceSequence: Long, backlog: Int): Snapshot = synchronized(lock) {
        if (count == 0) return Snapshot(false, 0, emptyList())

        val oldest = records[head]!!.sequence
        val newest = records[(head + count - 1) % records.size]!!.sequence
        val reset = sinceSequence < oldest - 1
        val start = when {
            sinceSequence == -1L -> maxOf(oldest, newest - backlog + 1)
            reset -> oldest
            else -> sinceSequence + 1
        }
        if (start > newest) return Snapshot(false, 0, emptyList())

        val skipped = if (reset && sinceSequence >= 0L) {
            (oldest - sinceSequence - 1).coerceAtLeast(0)
        } else {
            0L
        }
        val available = (newest - start + 1).toInt()
        val size = minOf(available, maxOf(backlog, 1))
        val startIndex = ((start - oldest).toInt()).coerceIn(0, count - 1)
        val result = ArrayList<LogRecord>(size)
        for (index in 0 until size) {
            result += records[(head + startIndex + index) % records.size]!!
        }
        Snapshot(reset, dropped + skipped, result)
    }

    fun clear() {
        synchronized(lock) {
            records.fill(null)
            head = 0
            count = 0
            characters = 0
            dropped = 0
        }
    }

    private fun removeOldest() {
        if (count == 0) return
        val removed = records[head]!!
        records[head] = null
        head = (head + 1) % records.size
        count--
        characters -= removed.textSize
        dropped++
    }

    internal data class Snapshot(
        val reset: Boolean,
        val dropped: Long,
        val records: List<LogRecord>,
    )

    companion object {
        const val RECORD_CAPACITY = 10_000
        const val CHARACTER_CAPACITY = 16L * 1024 * 1024
    }
}
