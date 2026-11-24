package com.nts.ingamecnl.client.log
import com.nts.ingamecnl.log.LogRecord

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
    private val pages = ArrayDeque<LogPage>()
    private var openPage: LogPageBuilder? = null
    private var nextPageNumber = 0L
    private var characters = 0L
    private var evictedCount = 0L
    private var visibleCountTotal = 0L
    private var version = 0L
    private var structureVersion = 0L
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

    /** 仅当可见记录被淘汰、顺序发生结构性变化时递增；新增记录不递增。 */
    val structureRevision: Long
        get() = structureVersion

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
        appendToPage(record)
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

    private fun appendToPage(record: LogRecord) {
        val page = openPage ?: LogPageBuilder(nextPageNumber++).also { openPage = it }
        page.add(record)
        if (page.count == LOG_PAGE_SIZE) {
            pages.addLast(page.build())
            openPage = null
        }
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
        prunePages()
    }

    private fun prunePages() {
        while (pages.isNotEmpty() && pages.first().lastSequence <= lastEvictedSequence) {
            pages.removeFirst()
        }
        if ((openPage?.lastSequence ?: -1L) <= lastEvictedSequence) {
            openPage = null
        }
    }

    internal fun snapshot(): LogSnapshot {
        openPage?.let {
            if (it.count > 0) pages.addLast(it.build())
            openPage = null
        }
        return LogSnapshot(
            pages = pages.toList(),
            fromSequence = lastEvictedSequence + 1,
            toSequence = records.lastOrNull()?.sequence ?: -1L,
        )
    }

    private fun pruneVisible() {
        while (visible.isNotEmpty() && visible.first().sequence <= lastEvictedSequence) {
            visible.removeFirst()
            version++
            structureVersion++
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
