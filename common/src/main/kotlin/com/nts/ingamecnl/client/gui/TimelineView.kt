package com.nts.ingamecnl.client.gui

import com.nts.ingamecnl.config.ConsoleConfig
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.network.chat.Component
import net.minecraft.util.FormattedCharSequence

/**
 * 所有模式共用的内容视图：日志记录与纯文本行都按同一套排版、换行、着色与滚动规则绘制。
 * 条目序列由 [TimelineSource] 提供，视图自身不区分模式。
 */
internal class TimelineView(private val font: Font) {
    private val layout = LogLayout(font)
    private val rows = ArrayList<Row>()
    private val layouts = HashMap<Long, LogEntryLayout>()

    private var scrollOffset = 0
    private var newCount = 0
    private var builtSource: TimelineSource? = null
    private var builtSize = 0
    private var builtWidth = -1
    private var builtNumberWidth = -1
    private var builtRevision = 0L
    private var builtHeight = 0
    private var rebuilt = false
    private var maxOffset = 0
    private var contentWidth = 1
    private var contentHeight = 1
    private var followHint: FollowHint? = null

    val rowHeight: Int
        get() = layout.rowHeight

    val isScrolled: Boolean
        get() = scrollOffset > 0

    fun draw(
        graphics: GuiGraphicsExtractor,
        source: TimelineSource,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
    ) {
        if (bottom <= top) return
        contentWidth = (right - left).coerceAtLeast(1)
        contentHeight = (bottom - top).coerceAtLeast(1)
        if (source !== builtSource) {
            scrollOffset = 0
            newCount = 0
        } else if (scrollOffset > 0 && source.size > builtSize) {
            newCount += source.size - builtSize
        }
        val view = buildRows(source)

        graphics.enableScissor(left, top, right, bottom)
        if (view.isEmpty()) {
            scrollOffset = 0
            newCount = 0
            maxOffset = 0
            graphics.textWithWordWrap(
                font,
                Component.translatable(source.emptyTextKey),
                left,
                top + 2,
                contentWidth,
                TimelinePalette.EMPTY,
            )
            graphics.disableScissor()
            return
        }

        val totalHeight = view.size * rowHeight
        maxOffset = (totalHeight - contentHeight).coerceAtLeast(0)
        if (!rebuilt && scrollOffset > 0 && totalHeight > builtHeight) {
            scrollOffset += totalHeight - builtHeight
        }
        rebuilt = false
        builtHeight = totalHeight
        scrollOffset = scrollOffset.coerceIn(0, maxOffset)
        if (scrollOffset == 0) newCount = 0
        var y = bottom - totalHeight + scrollOffset
        for (row in view) {
            if (y + rowHeight > top && y < bottom) row.draw(graphics, font, left, y)
            y += rowHeight
        }
        graphics.disableScissor()
    }

    fun scrollPage(up: Boolean) {
        val lines = (contentHeight / rowHeight).coerceAtLeast(1)
        scrollByLines(if (up) lines else -lines)
    }

    fun scrollByLines(lines: Int) {
        if (lines == 0) return
        scrollOffset = (scrollOffset + lines * rowHeight).coerceAtLeast(0).coerceAtMost(maxOffset.coerceAtLeast(0))
        if (scrollOffset == 0) newCount = 0
    }

    fun scrollToTop() {
        if (maxOffset <= 0) return
        scrollOffset = maxOffset
    }

    fun returnToBottom() {
        scrollOffset = 0
        newCount = 0
    }

    fun reset() {
        scrollOffset = 0
        newCount = 0
        builtSource = null
        builtSize = 0
        builtHeight = 0
        builtRevision = -1L
        maxOffset = 0
        rows.clear()
        layouts.clear()
    }

    fun newSinceScroll(): Long = if (scrollOffset == 0) 0L else newCount.toLong()

    fun setFollowHint(x: Int, y: Int, width: Int, height: Int) {
        followHint = FollowHint(x, y, width, height)
    }

    fun clearFollowHint() {
        followHint = null
    }

    fun followHintBounds(): FollowHint? = followHint

    private fun buildRows(source: TimelineSource): List<Row> {
        val count = source.size
        val numberWidth = source.numberWidth
        if (count == 0) {
            rows.clear()
            layouts.clear()
            builtSize = 0
            builtWidth = contentWidth
            builtNumberWidth = numberWidth
            builtRevision = source.revision
            builtHeight = 0
            rebuilt = true
            return rows
        }
        val rebuild = contentWidth != builtWidth ||
            numberWidth != builtNumberWidth ||
            source !== builtSource ||
            source.revision != builtRevision ||
            count < builtSize
        if (rebuild) {
            rows.clear()
            layouts.clear()
            builtSize = 0
            builtHeight = 0
            rebuilt = true
        }
        builtSource = source
        builtWidth = contentWidth
        builtNumberWidth = numberWidth
        builtRevision = source.revision
        while (builtSize < count) {
            appendRows(source.entryAt(builtSize), numberWidth)
            builtSize++
        }
        return rows
    }

    private fun appendRows(entry: TimelineEntry, numberWidth: Int) {
        when (entry) {
            is TimelineEntry.Record -> {
                val entryLayout = layouts.getOrPut(entry.record.sequence) {
                    layout.layout(entry.record, contentWidth, numberWidth)
                }
                rows += RecordRow(entryLayout)
                val color = ConsoleConfig.messageColor(entry.record.level)
                val continuation = if (entryLayout.messageOnFirstLine) {
                    entryLayout.messageLines.drop(1)
                } else {
                    entryLayout.messageLines
                }
                continuation.forEach { rows += LineRow(it, color) }
            }
            is TimelineEntry.Text -> {
                font.split(entry.text, contentWidth).forEach { rows += TextRow(it, entry.color) }
            }
        }
    }

    private sealed interface Row {
        fun draw(graphics: GuiGraphicsExtractor, font: Font, left: Int, y: Int)
    }

    private class RecordRow(private val entry: LogEntryLayout) : Row {
        override fun draw(graphics: GuiGraphicsExtractor, font: Font, left: Int, y: Int) {
            var cursor = left
            graphics.text(font, entry.number, cursor, y, TimelinePalette.NUMBER)
            cursor += font.width(entry.number) + 6
            graphics.text(font, entry.time, cursor, y, ConsoleConfig.timeColor())
            cursor += font.width(entry.time) + 4
            graphics.text(font, entry.threadLevel, cursor, y, ConsoleConfig.levelColor(entry.record.level))
            cursor += font.width(entry.threadLevel) + 4
            if (entry.logger.isNotEmpty()) {
                graphics.text(font, entry.logger, cursor, y, ConsoleConfig.loggerColor())
                cursor += font.width(entry.logger) + 4
            }
            if (entry.messageOnFirstLine) {
                entry.messageLines.firstOrNull()?.let {
                    graphics.text(font, it, cursor, y, ConsoleConfig.messageColor(entry.record.level))
                }
            }
        }
    }

    private class LineRow(
        private val text: String,
        private val color: Int,
    ) : Row {
        override fun draw(graphics: GuiGraphicsExtractor, font: Font, left: Int, y: Int) {
            graphics.text(font, text, left, y, color)
        }
    }

    private class TextRow(
        private val text: FormattedCharSequence,
        private val color: Int,
    ) : Row {
        override fun draw(graphics: GuiGraphicsExtractor, font: Font, left: Int, y: Int) {
            graphics.text(font, text, left, y, color)
        }
    }
}
