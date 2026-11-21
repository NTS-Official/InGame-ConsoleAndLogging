package com.nts.ingamecnl.client.log

data class LogRecord(
    val sequence: Long = 0,
    val timeMillis: Long,
    val level: LogLevel,
    val threadName: String,
    val loggerName: String,
    val message: String,
    val throwableText: String?,
    val truncated: Boolean,
) {
    val textSize: Int
        get() = message.length + (throwableText?.length ?: 0)
}
