package com.nts.ingamecnl.client.log

internal class LogHistory(
    private val recordCapacity: Int = 2_000,
    private val characterCapacity: Int = 4 * 1024 * 1024,
) {
    private val records = ArrayDeque<LogRecord>()
    private val visible = ArrayDeque<LogRecord>()
    private var characters = 0
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

    fun accept(pending: PendingRecord) {
        val record = pending.record
        records.addLast(record)
        characters += record.textSize
        while (records.size > 1 && (records.size > recordCapacity || characters > characterCapacity)) {
            val removed = records.removeFirst()
            characters -= removed.textSize
            if (removed.sequence > lastEvictedSequence) lastEvictedSequence = removed.sequence
            evictedCount++
        }
        if (pending.filter.matches(record)) {
            visible.addLast(record)
            visibleCountTotal++
            version++
        }
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
