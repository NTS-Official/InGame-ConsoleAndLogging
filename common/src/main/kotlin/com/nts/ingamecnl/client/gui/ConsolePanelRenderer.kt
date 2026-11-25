package com.nts.ingamecnl.client.gui

import com.nts.ingamecnl.MOD_ID

import com.nts.ingamecnl.client.ConsoleClient
import com.nts.ingamecnl.client.log.LogCapture
import com.nts.ingamecnl.client.mixin.CommandSuggestionsAccessor
import com.nts.ingamecnl.config.ConsoleConfig
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.network.chat.Component
import kotlin.math.roundToInt

internal object ConsolePanelRenderer {
    const val TITLE_OFFSET = 6
    const val CONTENT_OFFSET = 21

    /** 与原版 usage 行一致的行高。 */
    private const val USAGE_LINE_HEIGHT = 12

    fun drawPanel(screen: ConsoleScreen, graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int): Boolean {
        val top = screen.repositionPanel(System.nanoTime())
        val bottom = top + screen.panelHeight
        if (bottom <= 0) return false
        graphics.enableScissor(0, 0, screen.width, bottom)
        val alpha = ConsoleConfig.panelAlpha() shl 24
        graphics.fill(0, top, screen.width, bottom, alpha or 0x10151C)
        graphics.fill(0, bottom - 1, screen.width, bottom, alpha or 0x526176)
        graphics.enableScissor(8, top, (screen.width - 32).coerceAtLeast(8), top + CONTENT_OFFSET)
        graphics.text(screen.font, screen.panelTitle(), 8, top + TITLE_OFFSET, screen.titleColor())
        graphics.disableScissor()

        val contentTop = top + CONTENT_OFFSET
        val contentBottom = bottom - screen.contentBottomInset
        screen.drawContent(graphics, 8, contentTop, (screen.width - 8).coerceAtLeast(9), contentBottom)

        val statusY = bottom - 27
        val feedback = screen.feedback
        val status = if (feedback != null) feedback.string else statusText(screen)
        val followHint = if (feedback != null) null else followHint(screen)
        val followWidth = followHint?.let { screen.font.width(it) } ?: 0
        val followX = screen.width - 8 - followWidth
        val statusWidth = (if (followHint != null) followX - 12 else screen.width - 8) - 8
        graphics.text(
            screen.font,
            screen.font.clipToWidth(status, statusWidth.coerceAtLeast(0)),
            8,
            statusY,
            when {
                feedback != null && screen.feedbackError -> 0xFFFF7B72.toInt()
                feedback != null -> 0xFF9AD48B.toInt()
                else -> screen.statusColor()
            },
        )
        if (followHint != null) {
            graphics.text(screen.font, followHint, followX, statusY, 0xFFFFD27F.toInt())
            val hintX = followX - 2
            val hintY = statusY - 2
            val hintWidth = followWidth + 4
            val hintHeight = screen.font.lineHeight + 4
            screen.timelineView.setFollowHint(hintX, hintY, hintWidth, hintHeight)
        } else {
            screen.timelineView.clearFollowHint()
        }

        graphics.text(screen.font, screen.inputPrefix(), 8, screen.input.y, 0xFF8ED5BD.toInt())
        val hint = screen.modeSwitchHint
        graphics.text(
            screen.font,
            hint,
            screen.width - 8 - screen.font.width(hint),
            screen.input.y,
            0xFF95A5B8.toInt(),
        )
        return true
    }

    /**
     * 原版补全区整体下移到输入行下方，面板过高时不展开。
     * 有候选时只画候选列表；没有候选时按原版规则画错误/用法提示行。
     */
    fun drawSuggestions(screen: ConsoleScreen, graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int) {
        val suggestions = screen.suggestions ?: return
        if (!screen.completionControlVisible) return
        val offset = screen.suggestionOffset
        if (screen.completionVisible) {
            graphics.pose().pushMatrix()
            graphics.pose().translate(0f, offset.toFloat())
            suggestions.extractSuggestions(graphics, mouseX, mouseY - offset)
            graphics.pose().popMatrix()
            return
        }
        drawUsage(screen, graphics)
    }

    /** 原版把提示行画在聊天输入行上方，这里同样自下而上堆叠在状态行之上。 */
    private fun drawUsage(screen: ConsoleScreen, graphics: GuiGraphicsExtractor) {
        val lines = (screen.suggestions as? CommandSuggestionsAccessor)?.getCommandUsage() ?: return
        if (lines.isEmpty()) return
        val left = 8
        val width = lines.maxOf { screen.font.width(it) } + 2
        val right = (left + width).coerceAtMost((screen.width - 8).coerceAtLeast(left + 1))
        var y = screen.input.y - 13 - USAGE_LINE_HEIGHT - 2
        for (line in lines.asReversed()) {
            if (y < 0) break
            graphics.fill(left - 1, y, right, y + USAGE_LINE_HEIGHT, 0xB0000000.toInt())
            graphics.text(screen.font, line, left, y + 2, 0xFFFFFFFF.toInt())
            y -= USAGE_LINE_HEIGHT
        }
    }

    fun drawOverlay(screen: ConsoleScreen, graphics: GuiGraphicsExtractor) {
        val top = ((screen.transition.fractionAt(System.nanoTime(), screen.slideDurationNanos) - 1f) *
            screen.panelHeight).roundToInt()
        val bottom = top + screen.panelHeight
        if (bottom <= 0) return
        graphics.enableScissor(0, 0, screen.width, bottom)
        val alpha = ConsoleConfig.panelAlpha() shl 24
        graphics.fill(0, top, screen.width, bottom, alpha or 0x10151C)
        graphics.fill(0, bottom - 1, screen.width, bottom, alpha or 0x526176)
        graphics.text(
            screen.font,
            screen.panelTitle(),
            8,
            top + TITLE_OFFSET,
            screen.titleColor(),
        )
        screen.timelineView.draw(
            graphics,
            LogTimelineSource,
            8,
            top + CONTENT_OFFSET,
            (screen.width - 8).coerceAtLeast(9),
            bottom - screen.contentBottomInset,
        )

        val statusY = bottom - 27
        val status = logStatusText()
        val followHint = followHint(screen)
        val followWidth = followHint?.let { screen.font.width(it) } ?: 0
        val followX = screen.width - 8 - followWidth
        val statusWidth = (if (followHint != null) followX - 12 else screen.width - 8) - 8
        graphics.text(
            screen.font,
            screen.font.clipToWidth(status, statusWidth.coerceAtLeast(0)),
            8,
            statusY,
            0xFFB1C0D1.toInt(),
        )
        if (followHint != null) {
            graphics.text(screen.font, followHint, followX, statusY, 0xFFFFD27F.toInt())
        }
        val hint = screen.font.clipToWidth(ConsoleClient.overlayHint(), (screen.width - 16).coerceAtLeast(1))
        graphics.text(screen.font, hint, 8, bottom - 14, 0xFF95A5B8.toInt())
        graphics.disableScissor()
    }

    fun drawAutoSplash(screen: ConsoleScreen, graphics: GuiGraphicsExtractor) {        val top = ((screen.transition.fractionAt(System.nanoTime(), screen.slideDurationNanos) - 1f) *
            screen.panelHeight).roundToInt()
        val bottom = top + screen.panelHeight
        if (bottom <= 0) return
        graphics.enableScissor(0, 0, screen.width, bottom)
        val alpha = ConsoleConfig.panelAlpha() shl 24
        graphics.fill(0, top, screen.width, bottom, alpha or 0x10151C)
        graphics.fill(0, bottom - 1, screen.width, bottom, alpha or 0x526176)
        screen.timelineView.draw(
            graphics,
            LogTimelineSource,
            8,
            top + 8,
            (screen.width - 8).coerceAtLeast(9),
            bottom - screen.contentBottomInset,
        )
        graphics.disableScissor()
    }

    private fun statusText(screen: ConsoleScreen): String = when {
        screen.grepView.isOpen -> screen.grepView.statusText()
        screen.state.mode == ConsoleMode.LOG -> logStatusText()
        screen.state.mode == ConsoleMode.COMMAND -> screen.commandStatusText()
        else -> Component.translatable(screen.state.mode.statusKey).string
    }

    private fun followHint(screen: ConsoleScreen): Component? {
        if (screen.grepView.isOpen) return null
        val count = screen.timelineView.newSinceScroll()
        if (count <= 0) return null
        return Component.translatable(
            "console.$MOD_ID.follow",
            count,
            ConsoleClient.keyLabel(ConsoleClient.goToBottomKey()),
        )
    }

    private fun logStatusText(): String {
        val filter = LogCapture.filter
        val filterText = when {
            filter.level != null && filter.keyword != null ->
                Component.translatable("console.$MOD_ID.filter.both", filter.level.label, filter.keyword).string
            filter.level != null ->
                Component.translatable("console.$MOD_ID.filter.level", filter.level.label).string
            filter.keyword != null ->
                Component.translatable("console.$MOD_ID.filter.keyword", filter.keyword).string
            else -> Component.translatable("console.$MOD_ID.filter.none").string
        }
        val history = LogCapture.history
        val counters = Component.translatable(
            "console.$MOD_ID.counters",
            history.visibleSize,
            history.evicted,
            LogCapture.dropped,
        ).string
        return "$filterText · $counters"
    }
}
