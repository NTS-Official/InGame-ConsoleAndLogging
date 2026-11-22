package com.nts.ingamecnl.client.gui

import com.nts.ingamecnl.client.ConsoleClient
import com.nts.ingamecnl.client.log.LogCapture
import com.nts.ingamecnl.config.ConsoleConfig
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.network.chat.Component
import kotlin.math.roundToInt

internal object ConsolePanelRenderer {
    fun drawPanel(screen: ConsoleScreen, graphics: GuiGraphicsExtractor): Boolean {
        val top = screen.repositionPanel(System.nanoTime())
        val bottom = top + screen.panelHeight
        if (bottom <= 0) return false
        graphics.enableScissor(0, 0, screen.width, bottom)
        graphics.fill(0, top, screen.width, bottom, 0x1A10151C)
        graphics.fill(0, bottom - 1, screen.width, bottom, 0x1A526176)
        graphics.enableScissor(8, top + 6, (screen.width - 32).coerceAtLeast(8), top + 26)
        graphics.text(screen.font, screen.title, 8, top + 12, 0xFFF0F4FA.toInt())
        graphics.disableScissor()

        val contentTop = top + 30
        val contentBottom = bottom - screen.contentBottomInset
        screen.drawContent(graphics, 8, contentTop, (screen.width - 8).coerceAtLeast(9), contentBottom)

        val statusY = bottom - 57
        val status = statusText(screen)
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
            screen.logView.setFollowHint(followX - 2, statusY - 2, followWidth + 4, screen.font.lineHeight + 4)
        } else {
            screen.logView.clearFollowHint()
        }

        screen.feedback?.let {
            graphics.text(
                screen.font,
                screen.font.clipToWidth(it.string, (screen.width - 16).coerceAtLeast(1)),
                8,
                bottom - 44,
                if (screen.feedbackError) 0xFFFF7B72.toInt() else 0xFF9AD48B.toInt(),
            )
        }
        graphics.text(screen.font, screen.state.mode.prefix, 8, screen.input.y, 0xFF8ED5BD.toInt())
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

    fun drawOverlay(screen: ConsoleScreen, graphics: GuiGraphicsExtractor) {
        val top = ((screen.transition.fractionAt(System.nanoTime(), screen.slideDurationNanos) - 1f) *
            screen.panelHeight).roundToInt()
        val bottom = top + screen.panelHeight
        if (bottom <= 0) return
        graphics.enableScissor(0, 0, screen.width, bottom)
        val alpha = ConsoleConfig.overlayAlpha() shl 24
        graphics.fill(0, top, screen.width, bottom, alpha or 0x10151C)
        graphics.fill(0, bottom - 1, screen.width, bottom, alpha or 0x526176)
        graphics.text(
            screen.font,
            Component.translatable("console.ingamecnl.overlay.title"),
            8,
            top + 12,
            0xFFF0F4FA.toInt(),
        )
        screen.logView.draw(
            graphics,
            8,
            top + 30,
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

    fun drawAutoSplash(screen: ConsoleScreen, graphics: GuiGraphicsExtractor) {
        val top = ((screen.transition.fractionAt(System.nanoTime(), screen.slideDurationNanos) - 1f) *
            screen.panelHeight).roundToInt()
        val bottom = top + screen.panelHeight
        if (bottom <= 0) return
        graphics.enableScissor(0, 0, screen.width, bottom)
        val alpha = ConsoleConfig.overlayAlpha() shl 24
        graphics.fill(0, top, screen.width, bottom, alpha or 0x10151C)
        graphics.fill(0, bottom - 1, screen.width, bottom, alpha or 0x526176)
        screen.logView.draw(
            graphics,
            8,
            top + 8,
            (screen.width - 8).coerceAtLeast(9),
            bottom - screen.contentBottomInset,
        )
        graphics.disableScissor()
    }

    private fun statusText(screen: ConsoleScreen): String = when (screen.state.mode) {
        ConsoleMode.LOG -> logStatusText()
        ConsoleMode.COMMAND -> screen.commandStatusText()
        ConsoleMode.VIEW_ONLY -> Component.translatable(screen.state.mode.statusKey).string
    }

    private fun followHint(screen: ConsoleScreen): Component? {
        if (screen.state.mode != ConsoleMode.LOG) return null
        val count = screen.logView.newSinceScroll()
        if (count <= 0) return null
        return Component.translatable(
            "console.ingamecnl.follow",
            count,
            ConsoleClient.keyLabel(ConsoleClient.goToBottomKey()),
        )
    }

    private fun logStatusText(): String {
        val filter = LogCapture.filter
        val filterText = when {
            filter.level != null && filter.keyword != null ->
                Component.translatable("console.ingamecnl.filter.both", filter.level.label, filter.keyword).string
            filter.level != null ->
                Component.translatable("console.ingamecnl.filter.level", filter.level.label).string
            filter.keyword != null ->
                Component.translatable("console.ingamecnl.filter.keyword", filter.keyword).string
            else -> Component.translatable("console.ingamecnl.filter.none").string
        }
        val history = LogCapture.history
        val counters = Component.translatable(
            "console.ingamecnl.counters",
            history.visibleSize,
            history.evicted,
            LogCapture.dropped,
        ).string
        return "$filterText · $counters"
    }
}
