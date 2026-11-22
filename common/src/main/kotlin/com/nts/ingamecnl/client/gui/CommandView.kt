package com.nts.ingamecnl.client.gui

import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.network.chat.Component
import net.minecraft.util.FormattedCharSequence

internal class CommandView(
    private val font: Font,
    private val transcript: CommandTranscript,
) {
    private data class Line(val text: FormattedCharSequence, val color: Int)

    private var scrollOffset = 0

    val isScrolled: Boolean
        get() = scrollOffset > 0

    fun draw(graphics: GuiGraphicsExtractor, left: Int, top: Int, right: Int, bottom: Int) {
        if (bottom <= top) return
        val width = (right - left).coerceAtLeast(1)
        val viewport = (bottom - top).coerceAtLeast(1)
        val lines = buildLines(width)
        graphics.enableScissor(left, top, right, bottom)
        if (lines.isEmpty()) {
            scrollOffset = 0
            graphics.textWithWordWrap(
                font,
                Component.translatable("console.ingamecnl.command.empty"),
                left,
                top + 2,
                width,
                0xFF95A5B8.toInt(),
            )
            graphics.disableScissor()
            return
        }

        val rowHeight = font.lineHeight + 1
        val totalHeight = lines.size * rowHeight
        val maxOffset = (totalHeight - viewport).coerceAtLeast(0)
        scrollOffset = scrollOffset.coerceIn(0, maxOffset)
        var y = bottom - totalHeight + scrollOffset
        for (line in lines) {
            if (y + rowHeight > top && y < bottom) {
                graphics.text(font, line.text, left, y, line.color)
            }
            y += rowHeight
        }
        graphics.disableScissor()
    }

    fun scrollPage(up: Boolean, width: Int, viewport: Int) {
        scrollBy(if (up) viewport else -viewport, width, viewport)
    }

    fun scrollByLines(lines: Int, width: Int, viewport: Int) {
        scrollBy(lines * (font.lineHeight + 1), width, viewport)
    }

    fun returnToBottom() {
        scrollOffset = 0
    }

    private fun scrollBy(delta: Int, width: Int, viewport: Int) {
        val lines = buildLines(width.coerceAtLeast(1))
        val totalHeight = lines.size * (font.lineHeight + 1)
        val maxOffset = (totalHeight - viewport).coerceAtLeast(0)
        scrollOffset = (scrollOffset + delta).coerceIn(0, maxOffset)
    }

    private fun buildLines(width: Int): List<Line> {
        if (transcript.isEmpty) return emptyList()
        val lines = mutableListOf<Line>()
        for (entry in transcript.all) {
            val color = when (entry.kind) {
                CommandTranscriptKind.INPUT -> 0xFF8ED5BD.toInt()
                CommandTranscriptKind.OUTPUT -> 0xFFDCE6F2.toInt()
                CommandTranscriptKind.ERROR -> 0xFFFF7B72.toInt()
            }
            font.split(entry.text, width).forEach { lines += Line(it, color) }
        }
        return lines
    }
}
