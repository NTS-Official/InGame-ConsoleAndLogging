package com.nts.ingamecnl.server.log

import com.nts.ingamecnl.MOD_ID

import com.nts.ingamecnl.log.LogLevel
import com.nts.ingamecnl.log.LogRecord
import org.apache.logging.log4j.Level
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.core.Appender
import org.apache.logging.log4j.core.LogEvent
import org.apache.logging.log4j.core.LoggerContext
import org.apache.logging.log4j.core.appender.AbstractAppender
import org.apache.logging.log4j.core.config.Property
import java.io.PrintWriter
import java.io.StringWriter

internal object ServerLogCapture {
    private const val APPENDER_NAME = "${MOD_ID}-server-log"
    private const val MAX_MESSAGE_CHARS = 8_000
    private const val MAX_THROWABLE_CHARS = 16_000

    val buffer = ServerLogBuffer()
    private var installed = false

    fun initialize() {
        if (installed) return
        installed = true
        installAppender()
    }

    private fun installAppender() {
        val context = LogManager.getContext(false) as? LoggerContext ?: return
        val configuration = context.configuration
        if (configuration.getAppender<Appender>(APPENDER_NAME) != null) return
        val appender = object : AbstractAppender(APPENDER_NAME, null, null, true, emptyArray<Property>()) {
            override fun append(event: LogEvent) {
                buffer.append(toRecord(event))
            }
        }
        appender.start()
        configuration.addAppender(appender)
        configuration.getLoggerConfig(LogManager.ROOT_LOGGER_NAME).addAppender(appender, Level.ALL, null)
        context.updateLoggers()
    }

    private fun toRecord(event: LogEvent): LogRecord {
        val message = truncate(event.message?.formattedMessage ?: "", MAX_MESSAGE_CHARS)
        val throwable = event.thrown?.let { thrown ->
            val writer = StringWriter()
            thrown.printStackTrace(PrintWriter(writer))
            truncate(writer.toString(), MAX_THROWABLE_CHARS)
        }
        return LogRecord(
            timeMillis = event.timeMillis,
            level = LogLevel.fromLog4j(event.level),
            threadName = event.threadName ?: "",
            loggerName = event.loggerName ?: "",
            message = message.text,
            throwableText = throwable?.text,
            truncated = message.truncated || throwable?.truncated == true,
        )
    }

    private data class TruncatedText(val text: String, val truncated: Boolean)

    private fun truncate(text: String, maxLength: Int): TruncatedText =
        if (text.length <= maxLength) TruncatedText(text, false)
        else TruncatedText(text.substring(0, maxLength), true)
}
