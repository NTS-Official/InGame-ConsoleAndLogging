package com.nts.ingamecnl.client.gui

import com.nts.ingamecnl.log.LogRecord
import net.minecraft.client.gui.Font
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

internal const val MAX_LOG_LINES = 8

internal data class LogEntryLayout(
    val record: LogRecord,
    val number: String,
    val time: String,
    val threadLevel: String,
    val logger: String,
    val messageLines: List<String>,
    val messageOnFirstLine: Boolean,
) {
    val lineCount: Int
        get() = if (messageOnFirstLine) maxOf(1, messageLines.size) else 1 + messageLines.size
}

internal class LogLayout(private val font: Font) {
    private val timeFormat = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault())

    val rowHeight: Int
        get() = font.lineHeight + 1

    fun numberWidth(records: List<LogRecord>): Int =
        (records.last().sequence + 1).toString().length.coerceAtLeast(2)

    fun heightOf(record: LogRecord, viewWidth: Int, numberWidth: Int): Int =
        layout(record, viewWidth, numberWidth).lineCount * rowHeight

    fun layout(record: LogRecord, viewWidth: Int, numberWidth: Int): LogEntryLayout {
        val number = (record.sequence + 1).toString().padStart(numberWidth)
        val time = "[" + timeFormat.format(Instant.ofEpochMilli(record.timeMillis)) + "]"
        val fixedWidth = font.width(number) + 6 + font.width(time) + 4
        val flexibleWidth = (viewWidth - fixedWidth).coerceAtLeast(0)
        val threadBudget = (flexibleWidth / 3).coerceAtLeast(0)
        val loggerBudget = (flexibleWidth / 3).coerceAtLeast(0)
        val thread = font.clipToWidth(record.threadName.ifBlank { "-" }, threadBudget)
        val threadLevel = "[$thread/${record.level.label}]"
        val simpleLogger = record.loggerName.substringAfterLast('.')
        var logger = if (simpleLogger.isBlank()) "" else "(" + font.clipToWidth(simpleLogger, loggerBudget) + ")"
        var cursor = fixedWidth + font.width(threadLevel) + 4
        if (logger.isNotEmpty()) {
            val loggerWidth = font.width(logger) + 4
            if (cursor + loggerWidth <= viewWidth - 12) cursor += loggerWidth else logger = ""
        }
        val firstWidth = (viewWidth - cursor - 2).coerceAtLeast(0)
        val fullWidth = (viewWidth - 2).coerceAtLeast(1)
        val messageOnFirstLine = firstWidth >= font.width("MW") * 2
        val messageLineLimit = if (messageOnFirstLine) MAX_LOG_LINES else MAX_LOG_LINES - 1
        val messageLines = wrapMessage(
            record,
            if (messageOnFirstLine) firstWidth else fullWidth,
            fullWidth,
            messageLineLimit,
        )
        return LogEntryLayout(record, number, time, threadLevel, logger, messageLines, messageOnFirstLine)
    }

    private fun wrapMessage(record: LogRecord, firstWidth: Int, fullWidth: Int, maxLines: Int): List<String> {
        val message = record.message.replace("\r\n", "\n").replace('\r', '\n')
        val lines = ArrayList<String>(4)
        var width = firstWidth
        var remaining = message
        var truncated = record.throwableText != null || record.truncated
        while (remaining.isNotEmpty() && lines.size < maxLines) {
            val newline = remaining.indexOf('\n')
            val paragraph = if (newline >= 0) remaining.substring(0, newline) else remaining
            remaining = if (newline >= 0) remaining.substring(newline + 1) else ""
            if (paragraph.isEmpty()) {
                lines.add("")
                width = fullWidth
                continue
            }
            var text = paragraph
            while (text.isNotEmpty() && lines.size < maxLines) {
                val chunk = takeFitting(text, width)
                if (chunk.isEmpty()) break
                lines.add(chunk)
                text = text.substring(chunk.length)
                width = fullWidth
            }
            if (text.isNotEmpty()) truncated = true
            width = fullWidth
        }
        if (remaining.isNotEmpty()) truncated = true
        if (lines.isEmpty() && truncated) lines.add("…")
        if (truncated && lines.isNotEmpty()) {
            val last = lines.last()
            lines[lines.lastIndex] = font.clipToWidth(if (last.isEmpty()) "…" else "$last …", fullWidth)
        }
        return lines
    }

    private fun takeFitting(text: String, maxWidth: Int): String {
        if (maxWidth <= 0) return ""
        if (font.width(text) <= maxWidth) return text
        val chunk = font.plainSubstrByWidth(text, maxWidth)
        if (chunk.isNotEmpty()) return chunk
        return text.substring(0, text.offsetByCodePoints(0, 1))
    }
}

internal fun Font.clipToWidth(text: String, maxWidth: Int): String {
    if (maxWidth <= 0) return ""
    if (width(text) <= maxWidth) return text
    val ellipsis = "..."
    val ellipsisWidth = width(ellipsis)
    if (maxWidth <= ellipsisWidth) return plainSubstrByWidth(text, maxWidth)
    return plainSubstrByWidth(text, maxWidth - ellipsisWidth) + ellipsis
}
