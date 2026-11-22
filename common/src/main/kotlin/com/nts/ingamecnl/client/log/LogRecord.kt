package com.nts.ingamecnl.client.log

import com.nts.ingamecnl.log.LogLevel

data class LogRecord(
    val sequence: Long = 0,
    val timeMillis: Long,
    val level: LogLevel,
    val threadName: String,
    val loggerName: String,
    val message: String,
    val throwableText: String?,
    val truncated: Boolean,
    val textSize: Int = utf8Size(message) + (throwableText?.let(::utf8Size) ?: 0),
)

private fun utf8Size(value: String): Int {
    var size = 0
    var index = 0
    while (index < value.length) {
        val code = value[index].code
        size += when {
            code < 0x80 -> 1
            code < 0x800 -> 2
            Character.isHighSurrogate(value[index]) &&
                index + 1 < value.length &&
                Character.isLowSurrogate(value[index + 1]) -> {
                index++
                4
            }
            else -> 3
        }
        index++
    }
    return size
}
