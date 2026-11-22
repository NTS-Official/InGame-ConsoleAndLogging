package com.nts.ingamecnl.client.log

import com.nts.ingamecnl.log.LogLimits

internal class LogHistory(
    recordCapacity: Long = LogLimits.DEFAULT_RECORD_CAPACITY,
    characterCapacity: Long = LogLimits.DEFAULT_TEXT_CAPACITY,
) {
    private var recordCapacity = recordCapacity
    private var characterCapacity = characterCapacity

    init {
        require(recordCapacity in 1..LogLimits.MAX_RECORD_CAPACITY)
        require(characterCapacity in 1..LogLimits.MAX_TEXT_CAPACITY)
    }

    private val records = ArrayDeque<LogRecord>()
    private val visible = ArrayDeque<LogRecord>()
    private var characters = 0L
    private var evictedCount = 0L
    private var visibleCountTotal = 0L
    private var version = 0L
    private var lastEvictedSequence = -1L
    private var snapshot: List<LogRecord> = emptyList()
    private var snapshotVersion = -1L

    val size: Int
        get() = records.size

    val visibleSize: Int
        get() = visible.size

    val evicted: Long
        get() = evictedCount

    val visibleTotal: Long
        get() = visibleCountTotal

    fun configure(recordCapacity: Long, characterCapacity: Long) {
        require(recordCapacity in 1..LogLimits.MAX_RECORD_CAPACITY)
        require(characterCapacity in 1..LogLimits.MAX_TEXT_CAPACITY)
        this.recordCapacity = recordCapacity
        this.characterCapacity = characterCapacity
        evictOverflow()
        pruneVisible()
    }

    fun accept(pending: PendingRecord): Boolean {
        val record = pending.record
        records.addLast(record)
        characters += record.textSize
        evictOverflow()
        val visibleNow = pending.filter.matches(record)
        if (visibleNow) {
            visible.addLast(record)
            visibleCountTotal++
            version++
        }
        pruneVisible()
        return visibleNow
    }

    private fun evictOverflow() {
        while (records.size > 1 &&
            (records.size.toLong() > recordCapacity || characters > characterCapacity)
        ) {
            val removed = records.removeFirst()
            characters -= removed.textSize
            if (removed.sequence > lastEvictedSequence) lastEvictedSequence = removed.sequence
            evictedCount++
        }
    }

    private fun pruneVisible() {
        while (visible.isNotEmpty() && visible.first().sequence <= lastEvictedSequence) {
            visible.removeFirst()
            version++
        }
    }

    fun visibleRecords(): List<LogRecord> {
        if (snapshotVersion != version) {
            snapshot = visible.toList()
            snapshotVersion = version
        }
        return snapshot
    }
}
