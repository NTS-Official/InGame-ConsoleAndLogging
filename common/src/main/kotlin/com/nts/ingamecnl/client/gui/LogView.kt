package com.nts.ingamecnl.client.gui

import com.nts.ingamecnl.client.config.ConsoleConfig
import com.nts.ingamecnl.client.log.LogCapture
import com.nts.ingamecnl.client.log.LogRecord
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.network.chat.Component

internal class LogView(private val font: Font) {
    private val layout = LogLayout(font)
    private var anchorSequence: Long? = null
    private var anchorVisibleTotal = 0L
    private var followHint: FollowHint? = null

    val rowHeight: Int
        get() = layout.rowHeight

    val isScrolled: Boolean
        get() = anchorSequence != null

    data class FollowHint(val x: Int, val y: Int, val width: Int, val height: Int)

    fun draw(graphics: GuiGraphicsExtractor, left: Int, top: Int, right: Int, bottom: Int) {
        if (bottom <= top) return
        graphics.enableScissor(left, top, right, bottom)
        val records = LogCapture.history.visibleRecords()
        if (records.isEmpty()) {
            anchorSequence = null
            graphics.textWithWordWrap(
                font,
                Component.translatable(
                    if (LogCapture.filter.isUnfiltered) "console.ingamecnl.empty"
                    else "console.ingamecnl.empty.filtered",
                ),
                left,
                top + 2,
                (right - left).coerceAtLeast(1),
                0xFF95A5B8.toInt(),
            )
            graphics.disableScissor()
            return
        }
        val numberWidth = layout.numberWidth(records)
        val viewWidth = (right - left).coerceAtLeast(1)
        val viewport = (bottom - top).coerceAtLeast(1)
        val autoStart = autoFollowStart(records, viewWidth, numberWidth, viewport)
        var start = autoStart
        var bottomAligned = true
        val anchor = anchorSequence
        if (anchor != null) {
            val index = indexOfSequence(records, anchor)
            if (index < 0) {
                anchorSequence = records.first().sequence
                anchorVisibleTotal = LogCapture.history.visibleTotal
                start = 0
                bottomAligned = false
            } else {
                start = index
                if (start >= autoStart) {
                    anchorSequence = null
                    start = autoStart
                } else {
                    bottomAligned = false
                }
            }
        }
        var totalHeight = 0
        var measured = start
        while (measured < records.size && totalHeight < viewport) {
            totalHeight += layout.heightOf(records[measured], viewWidth, numberWidth)
            measured++
        }
        var y = if (bottomAligned) (bottom - totalHeight).coerceAtLeast(top) else top
        var index = start
        while (index < records.size && y < bottom) {
            val entry = layout.layout(records[index], viewWidth, numberWidth)
            drawEntry(graphics, entry, left, y)
            y += entry.lineCount * layout.rowHeight
            index++
        }
        graphics.disableScissor()
    }

    fun scrollPage(up: Boolean, viewWidth: Int, viewport: Int) {
        val records = LogCapture.history.visibleRecords()
        if (records.isEmpty()) return
        val numberWidth = layout.numberWidth(records)
        val autoStart = autoFollowStart(records, viewWidth, numberWidth, viewport)
        val current = currentStart(records, autoStart)
        var target = current
        var height = 0
        if (up) {
            while (target > 0 && height < viewport) {
                target--
                height += layout.heightOf(records[target], viewWidth, numberWidth)
            }
        } else {
            while (target < records.lastIndex && height < viewport) {
                height += layout.heightOf(records[target], viewWidth, numberWidth)
                target++
            }
        }
        if (target == current) return
        if (target >= autoStart) returnToBottom() else setAnchor(records[target].sequence)
    }

    fun scrollByRecords(delta: Int, viewWidth: Int, viewport: Int) {
        if (delta == 0) return
        val records = LogCapture.history.visibleRecords()
        if (records.isEmpty()) return
        val numberWidth = layout.numberWidth(records)
        val autoStart = autoFollowStart(records, viewWidth, numberWidth, viewport)
        val current = currentStart(records, autoStart)
        val target = (current - delta).coerceIn(0, autoStart)
        if (target >= autoStart) returnToBottom() else setAnchor(records[target].sequence)
    }

    fun returnToBottom() {
        anchorSequence = null
    }

    fun reset() {
        anchorSequence = null
        anchorVisibleTotal = 0L
        followHint = null
    }

    fun newSinceScroll(): Long {
        if (anchorSequence == null) return 0
        return (LogCapture.history.visibleTotal - anchorVisibleTotal).coerceAtLeast(0)
    }

    fun setFollowHint(x: Int, y: Int, width: Int, height: Int) {
        followHint = FollowHint(x, y, width, height)
    }

    fun clearFollowHint() {
        followHint = null
    }

    fun followHintBounds(): FollowHint? = followHint

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
            lineY += rowHeight
            graphics.text(font, line, left, lineY, color)
        }
    }

    private fun autoFollowStart(
        records: List<LogRecord>,
        viewWidth: Int,
        numberWidth: Int,
        viewport: Int,
    ): Int {
        var height = 0
        var included = 0
        var index = records.lastIndex
        while (index >= 0) {
            val next = height + layout.heightOf(records[index], viewWidth, numberWidth)
            if (included > 0 && next > viewport) break
            height = next
            included++
            index--
        }
        return (index + 1).coerceIn(0, records.lastIndex)
    }

    private fun currentStart(records: List<LogRecord>, autoStart: Int): Int {
        val anchor = anchorSequence ?: return autoStart
        val index = indexOfSequence(records, anchor)
        return if (index < 0) 0 else index
    }

    private fun setAnchor(sequence: Long) {
        if (anchorSequence == null) anchorVisibleTotal = LogCapture.history.visibleTotal
        anchorSequence = sequence
    }

    private fun indexOfSequence(records: List<LogRecord>, sequence: Long): Int {
        var low = 0
        var high = records.lastIndex
        while (low <= high) {
            val mid = (low + high) ushr 1
            val value = records[mid].sequence
            when {
                value < sequence -> low = mid + 1
                value > sequence -> high = mid - 1
                else -> return mid
            }
        }
        return -1
    }
}
