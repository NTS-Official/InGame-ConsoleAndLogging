package com.nts.ingamecnl.client.log

import com.nts.ingamecnl.log.LogLevel
import dev.architectury.event.events.client.ClientTickEvent
import net.minecraft.client.Minecraft
import org.apache.logging.log4j.Level
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.core.Appender
import org.apache.logging.log4j.core.LogEvent
import org.apache.logging.log4j.core.LoggerContext
import org.apache.logging.log4j.core.appender.AbstractAppender
import org.apache.logging.log4j.core.config.Property
import java.io.PrintWriter
import java.io.StringWriter

private const val APPENDER_NAME = "ingamecnl-console"
private const val MAX_MESSAGE_CHARS = 8_000
private const val MAX_THROWABLE_CHARS = 16_000
private const val DRAIN_RECORD_BUDGET = 256
private const val DRAIN_NANOS_BUDGET = 2_000_000L

object LogCapture {
    private val inbox = LogInbox()
    internal val history = LogHistory()
    private var installed = false

    internal var onVisibleRecord: (LogRecord) -> Unit = {}

    val filter: DisplayFilter
        get() = inbox.filter()

    val dropped: Long
        get() = inbox.dropped()

    fun initialize() {
        if (installed) return
        installed = true
        installAppender()
        ClientTickEvent.CLIENT_POST.register(object : ClientTickEvent.Client {
            override fun tick(client: Minecraft) = drain()
        })
    }

    fun setFilter(filter: DisplayFilter) {
        inbox.updateFilter(filter)
    }

    fun setHistoryLimits(recordLimit: Long, textLimit: Long) {
        history.configure(recordLimit, textLimit)
    }

    private fun installAppender() {
        val context = LogManager.getContext(false) as? LoggerContext ?: return
        val configuration = context.configuration
        if (configuration.getAppender<Appender>(APPENDER_NAME) != null) return
        val appender = object : AbstractAppender(APPENDER_NAME, null, null, true, emptyArray<Property>()) {
            override fun append(event: LogEvent) {
                inbox.offer(toRecord(event))
            }
        }
        appender.start()
        configuration.addAppender(appender)
        configuration.getLoggerConfig(LogManager.ROOT_LOGGER_NAME).addAppender(appender, Level.ALL, null)
        context.updateLoggers()
    }

    private fun drain() {
        val deadline = System.nanoTime() + DRAIN_NANOS_BUDGET
        var accepted = 0
        while (accepted < DRAIN_RECORD_BUDGET) {
            val pending = inbox.poll() ?: break
            val visible = history.accept(pending)
            if (visible) onVisibleRecord(pending.record)
            accepted++
            if (accepted and 7 == 0 && System.nanoTime() >= deadline) break
        }
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
