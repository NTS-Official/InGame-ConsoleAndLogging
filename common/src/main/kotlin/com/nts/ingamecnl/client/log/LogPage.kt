package com.nts.ingamecnl.client.log
import com.nts.ingamecnl.log.LogRecord

internal const val LOG_PAGE_SIZE = 1_024

internal class LogPage(
    val number: Long,
    val records: Array<LogRecord?>,
    val count: Int,
) {
    val firstSequence: Long
        get() = if (count == 0) -1L else records[0]?.sequence ?: -1L

    val lastSequence: Long
        get() = if (count == 0) -1L else records[count - 1]?.sequence ?: -1L
}

internal class LogPageBuilder(private val number: Long) {
    private val records = arrayOfNulls<LogRecord>(LOG_PAGE_SIZE)

    var count: Int = 0
        private set

    val lastSequence: Long
        get() = if (count == 0) -1L else records[count - 1]?.sequence ?: -1L

    fun add(record: LogRecord) {
        records[count++] = record
    }

    fun build(): LogPage = LogPage(number, records, count)
}

internal data class LogSnapshot(
    val pages: List<LogPage>,
    val fromSequence: Long,
    val toSequence: Long,
) {
    val isEmpty: Boolean
        get() = toSequence < fromSequence
}
