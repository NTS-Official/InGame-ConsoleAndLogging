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
    private val pending = arrayOfNulls<PendingRecord>(recordCapacity.coerceAtLeast(1))
    private var head = 0
    private var count = 0
    private var pendingCharacters = 0
    private var nextSequence = 0L
    private var currentFilter = DisplayFilter()
    private var droppedCount = 0L

    fun offer(record: LogRecord) {
        synchronized(lock) {
            if (count >= pending.size || pendingCharacters + record.textSize > characterCapacity) {
                droppedCount++
                return
            }
            val sequenced = record.copy(sequence = nextSequence++)
            pending[(head + count) % pending.size] = PendingRecord(sequenced, currentFilter)
            count++
            pendingCharacters += sequenced.textSize
        }
    }

    fun poll(): PendingRecord? = synchronized(lock) {
        if (count == 0) return@synchronized null
        val slot = pending[head]
        pending[head] = null
        head = (head + 1) % pending.size
        count--
        pendingCharacters -= slot?.record?.textSize ?: 0
        slot
    }

    fun filter(): DisplayFilter = synchronized(lock) { currentFilter }

    fun updateFilter(filter: DisplayFilter) {
        synchronized(lock) { currentFilter = filter }
    }

    fun dropped(): Long = synchronized(lock) { droppedCount }
}
