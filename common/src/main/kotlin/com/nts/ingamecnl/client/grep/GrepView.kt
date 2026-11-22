package com.nts.ingamecnl.client.grep

import com.nts.ingamecnl.client.log.LogPage
import com.nts.ingamecnl.client.log.LogRecord
import com.nts.ingamecnl.client.log.LogSnapshot
import com.nts.ingamecnl.client.gui.LogEntryLayout
import com.nts.ingamecnl.client.gui.LogLayout
import com.nts.ingamecnl.client.gui.clipToWidth
import com.nts.ingamecnl.config.ConsoleConfig
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.input.KeyEvent
import net.minecraft.network.chat.Component
import org.lwjgl.glfw.GLFW
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

internal class GrepView(private val font: Font) {
    private val layout = LogLayout(font)
    private val timeFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault())

    private var task: GrepTask? = null
    private var result: GrepResult? = null
    private var error: String? = null
    private var query: GrepQuery? = null
    private var top = 0L
    private var horizontal = 0
    private var generation = 0L
    private var visibleRecords = 1

    var isOpen: Boolean = false
        private set

    fun start(query: GrepQuery, snapshot: LogSnapshot) {
        close()
        generation++
        val currentGeneration = generation
        this.query = query
        result = null
        error = null
        top = 0
        horizontal = 0
        isOpen = true
        task = GrepSearch.submit(snapshot, query) { outcome ->
            if (!isOpen || generation != currentGeneration) return@submit
            when (outcome) {
                is GrepOutcome.Completed -> result = outcome.result
                is GrepOutcome.Failed -> error = outcome.message
            }
        }
    }

    fun close() {
        generation++
        task?.cancel()
        task = null
        result = null
        error = null
        query = null
        top = 0
        horizontal = 0
        isOpen = false
    }

    fun statusText(): String = when {
        error != null -> Component.translatable("console.ingamecnl.grep.failed", error ?: "").string
        result == null -> Component.translatable("console.ingamecnl.grep.searching").string
        else -> Component.translatable(
            "console.ingamecnl.grep.matches",
            result?.totalMatches ?: 0,
        ).string
    }

    fun handleKey(event: KeyEvent): Boolean {
        if (!isOpen) return false
        return when (event.key()) {
            GLFW.GLFW_KEY_Q, GLFW.GLFW_KEY_ESCAPE -> {
                close()
                true
            }
            GLFW.GLFW_KEY_UP -> {
                scroll(-1)
                true
            }
            GLFW.GLFW_KEY_DOWN -> {
                scroll(1)
                true
            }
            GLFW.GLFW_KEY_PAGE_UP -> {
                scroll(-visibleRecords.coerceAtLeast(1))
                true
            }
            GLFW.GLFW_KEY_PAGE_DOWN -> {
                scroll(visibleRecords.coerceAtLeast(1))
                true
            }
            GLFW.GLFW_KEY_HOME -> {
                top = 0
                true
            }
            GLFW.GLFW_KEY_END -> {
                top = (result?.totalMatches ?: 0L).let { if (it > 0) it - 1 else 0L }
                true
            }
            GLFW.GLFW_KEY_LEFT -> {
                horizontal = (horizontal - 16).coerceAtLeast(0)
                true
            }
            GLFW.GLFW_KEY_RIGHT -> {
                horizontal += 16
                true
            }
            else -> true
        }
    }

    fun scroll(delta: Int) {
        val total = result?.totalMatches ?: return
        if (total <= 0) return
        top = (top + delta).coerceIn(0, total - 1)
    }

    fun draw(graphics: GuiGraphicsExtractor, left: Int, top: Int, right: Int, bottom: Int) {
        if (!isOpen) return
        val width = (right - left).coerceAtLeast(1)
        val height = (bottom - top).coerceAtLeast(1)
        graphics.enableScissor(left, top, right, bottom)
        graphics.fill(left, top, right, bottom, 0xE60B1016.toInt())
        graphics.fill(left, top, right, top + 1, 0xFF526176.toInt())
        graphics.fill(left, bottom - 1, right, bottom, 0xFF526176.toInt())
        graphics.fill(left, top, left + 1, bottom, 0xFF526176.toInt())
        graphics.fill(right - 1, top, right, bottom, 0xFF526176.toInt())

        val header = query?.let { describe(it) } ?: "grep"
        graphics.text(font, font.clipToWidth(header, width - 12), left + 6, top + 4, 0xFFDCE6F2.toInt())
        val status = when {
            error != null -> Component.translatable("console.ingamecnl.grep.failed", error ?: "").string
            result == null -> Component.translatable("console.ingamecnl.grep.searching").string
            else -> {
                val found = result ?: return
                val range = if (found.minTimeMillis == null || found.maxTimeMillis == null) {
                    Component.translatable("console.ingamecnl.grep.no_range").string
                } else {
                    "${timeFormat.format(Instant.ofEpochMilli(found.minTimeMillis))} ~ " +
                        timeFormat.format(Instant.ofEpochMilli(found.maxTimeMillis))
                }
                Component.translatable(
                    "console.ingamecnl.grep.matches_range",
                    found.totalMatches,
                    range,
                ).string
            }
        }
        graphics.text(font, font.clipToWidth(status, width - 12), left + 6, top + 16, 0xFF95A5B8.toInt())
        graphics.fill(left + 6, top + 27, right - 6, top + 28, 0xFF526176.toInt())
        val bodyTop = top + 31
        val footerTop = bottom - 14
        val bodyBottom = footerTop - 2
        val found = result
        if (found == null) {
            if (error == null) {
                graphics.text(
                    font,
                    Component.translatable("console.ingamecnl.grep.searching").string,
                    left + 6,
                    bodyTop,
                    0xFF95A5B8.toInt(),
                )
            }
            graphics.disableScissor()
            return
        }
        if (found.totalMatches == 0L) {
            graphics.text(
                font,
                Component.translatable("console.ingamecnl.grep.empty").string,
                left + 6,
                bodyTop,
                0xFF95A5B8.toInt(),
            )
        } else {
            val numberWidth = (found.maxSequence + 1).toString().length.coerceAtLeast(2)
            var y = bodyTop
            var index: Long = this.top
            var drawn = 0
            while (index < found.totalMatches && y < bodyBottom) {
                val record = found.recordAt(index) ?: break
                val entry = layout.layout(record, width - 12 + horizontal, numberWidth)
                drawEntry(graphics, entry, left + 6 - horizontal, y)
                y += entry.lineCount * layout.rowHeight
                index++
                drawn++
            }
            visibleRecords = drawn.coerceAtLeast(1)
            val first = if (found.totalMatches == 0L) 0L else this.top + 1
            val last = (this.top + drawn).coerceAtMost(found.totalMatches)
            val position = Component.translatable(
                "console.ingamecnl.grep.position",
                first,
                last,
                found.totalMatches,
            ).string
            graphics.text(
                font,
                font.clipToWidth(position, width - 12),
                left + 6,
                footerTop,
                0xFFB1C0D1.toInt(),
            )
        }
        graphics.disableScissor()
    }

    private fun drawEntry(graphics: GuiGraphicsExtractor, entry: LogEntryLayout, left: Int, y: Int) {
        var cursor = left
        graphics.text(font, entry.number, cursor, y, 0xFF6E7F93.toInt())
        cursor += font.width(entry.number) + 6
        graphics.text(font, entry.time, cursor, y, ConsoleConfig.timeColor())
        cursor += font.width(entry.time) + 4
        graphics.text(font, entry.threadLevel, cursor, y, ConsoleConfig.levelColor(entry.record.level))
        cursor += font.width(entry.threadLevel) + 4
        if (entry.logger.isNotEmpty()) {
            graphics.text(font, entry.logger, cursor, y, ConsoleConfig.loggerColor())
            cursor += font.width(entry.logger) + 4
        }
        val color = ConsoleConfig.messageColor(entry.record.level)
        val continuation = if (entry.messageOnFirstLine) {
            entry.messageLines.firstOrNull()?.let { graphics.text(font, it, cursor, y, color) }
            entry.messageLines.drop(1)
        } else {
            entry.messageLines
        }
        var lineY = y
        for (line in continuation) {
            lineY += layout.rowHeight
            graphics.text(font, line, left, lineY, color)
        }
    }

    private fun describe(query: GrepQuery): String {
        val text = StringBuilder("grep -k \"").append(query.keyword).append("\" -l ").append(query.level.label)
        query.startText?.let { text.append(" -t1 \"").append(it).append('"') }
        query.endText?.let { text.append(" -t2 \"").append(it).append('"') }
        return text.toString()
    }
}
