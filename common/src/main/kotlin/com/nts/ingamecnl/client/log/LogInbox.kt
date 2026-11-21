package com.nts.ingamecnl.client.log

internal data class PendingRecord(
    val record: LogRecord,
    val filter: DisplayFilter,
)

internal class LogInbox(
    private val recordCapacity: Int = 2_048,
    private val characterCapacity: Int = 2 * 1024 * 1024,
) {
    private val lock = Any()
    private val pending = ArrayDeque<PendingRecord>()
    private var pendingCharacters = 0
    private var nextSequence = 0L
    private var currentFilter = DisplayFilter()
    private var droppedCount = 0L

    fun offer(record: LogRecord) {
        synchronized(lock) {
            if (pending.size >= recordCapacity || pendingCharacters + record.textSize > characterCapacity) {
                droppedCount++
                return
            }
            val sequenced = record.copy(sequence = nextSequence++)
            pending.addLast(PendingRecord(sequenced, currentFilter))
            pendingCharacters += sequenced.textSize
        }
    }

    fun poll(): PendingRecord? = synchronized(lock) {
        pending.removeFirstOrNull()?.also { pendingCharacters -= it.record.textSize }
    }

    fun filter(): DisplayFilter = synchronized(lock) { currentFilter }

    fun updateFilter(filter: DisplayFilter) {
        synchronized(lock) { currentFilter = filter }
    }

    fun dropped(): Long = synchronized(lock) { droppedCount }
}
