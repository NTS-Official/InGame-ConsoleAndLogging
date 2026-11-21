package com.nts.ingamecnl.client

import com.mojang.blaze3d.platform.InputConstants
import com.nts.ingamecnl.MOD_ID
import com.nts.ingamecnl.client.log.FilterCommand
import com.nts.ingamecnl.client.log.FilterCommandError
import com.nts.ingamecnl.client.log.FilterCommandParse
import com.nts.ingamecnl.client.log.LogCapture
import com.nts.ingamecnl.client.log.LogLevel
import com.nts.ingamecnl.client.log.LogRecord
import com.nts.ingamecnl.client.log.apply
import com.nts.ingamecnl.client.log.parseFilterCommand
import dev.architectury.registry.client.keymappings.KeyMappingRegistry
import net.minecraft.client.KeyMapping
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.components.PlainTextButton
import net.minecraft.client.gui.components.Tooltip
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.options.controls.KeyBindsScreen
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.client.input.MouseButtonInfo
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import org.lwjgl.glfw.GLFW
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

private const val SLIDE_DURATION_NANOS = 180_000_000L
private const val MAX_LOG_LINES = 8
private val LOOM_BLUE = 0xFF5555FF.toInt()
private val LOOM_CYAN = 0xFF55FFFF.toInt()
private val LOOM_GREEN = 0xFF55FF55.toInt()
private val LOOM_YELLOW = 0xFFFFFF55.toInt()
private val LOOM_RED = 0xFFFF5555.toInt()
private val LOOM_WHITE = 0xFFFFFFFF.toInt()

object ConsoleClient {
    private var toggleKey: KeyMapping? = null
    private val state = ConsoleState()
    private var heldKey: InputConstants.Key? = null
    private var heldMouseButton: Int? = null
    private var resumingScreen: Screen? = null

    fun initialize() {
        LogCapture.initialize()
        val mapping = KeyMapping(
            "key.ingamecnl.toggle_console",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_GRAVE_ACCENT,
            KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "console")),
        )
        toggleKey = mapping
        KeyMappingRegistry.register(mapping)
    }

    fun consumeKey(window: Long, action: Int, event: KeyEvent): Boolean {
        val mapping = toggleKey ?: return false
        val minecraft = Minecraft.getInstance()
        if (window != minecraft.window.handle()) return false
        val key = InputConstants.getKey(event)
        if (key == heldKey) {
            if (action == GLFW.GLFW_RELEASE) heldKey = null
            return true
        }
        if (minecraft.screen is ConsoleScreen && event.key() == GLFW.GLFW_KEY_TAB &&
            (event.hasShiftDown() || action == GLFW.GLFW_RELEASE)
        ) {
            return false
        }
        if ((minecraft.screen as? KeyBindsScreen)?.selectedKey != null || !mapping.matches(event)) return false
        if (action == GLFW.GLFW_RELEASE) return false
        if (action == GLFW.GLFW_PRESS) {
            heldKey = key
            togglePanel(minecraft)
        }
        return true
    }

    fun consumeCharacter(window: Long): Boolean =
        toggleKey != null && window == Minecraft.getInstance().window.handle() && heldKey != null

    fun consumeMouse(window: Long, buttonInfo: MouseButtonInfo, action: Int): Boolean {
        val mapping = toggleKey ?: return false
        val minecraft = Minecraft.getInstance()
        if (window != minecraft.window.handle()) return false
        if (buttonInfo.button() == heldMouseButton) {
            if (action == GLFW.GLFW_RELEASE) heldMouseButton = null
            return true
        }
        if ((minecraft.screen as? KeyBindsScreen)?.selectedKey != null ||
            !mapping.matchesMouse(MouseButtonEvent(0.0, 0.0, buttonInfo))
        ) return false
        if (action == GLFW.GLFW_RELEASE) return false
        if (action == GLFW.GLFW_PRESS) {
            heldMouseButton = buttonInfo.button()
            togglePanel(minecraft)
        }
        return true
    }

    private fun togglePanel(minecraft: Minecraft) {
        val current = minecraft.screen
        if (current is ConsoleScreen) current.togglePanel()
        else minecraft.setScreen(ConsoleScreen(state, current))
    }

    fun removeScreen(current: Screen, replacement: Screen?) {
        resumingScreen = null
        if (replacement is ConsoleScreen && replacement.previousScreen === current) return
        if (current is ConsoleScreen) {
            if (current.previousScreen === replacement) resumingScreen = replacement
            else current.previousScreen?.removed()
        }
        current.removed()
    }

    fun addScreen(next: Screen) {
        if (next !== resumingScreen) next.added()
    }

    fun initializeScreen(next: Screen, width: Int, height: Int) {
        if (next !== resumingScreen) next.init(width, height)
        else {
            if (next.width != width || next.height != height) next.resize(width, height)
            next.currentFocusPath?.applyFocus(true)
        }
        resumingScreen = null
    }
}

private enum class ConsoleMode(val prefix: String, val statusKey: String) {
    LOG("$", "console.ingamecnl.status.log"),
    COMMAND("#", "console.ingamecnl.status.command"),
}

private enum class ModeSwitchGesture {
    READY,
    HELD,
}

private enum class PanelTarget {
    EXPANDED,
    COLLAPSED,
}

private data class PanelTransition(
    val origin: Float,
    val target: PanelTarget,
    val startedAtNanos: Long,
) {
    fun fractionAt(nowNanos: Long): Float {
        val distance = (nowNanos - startedAtNanos).coerceAtLeast(0).toFloat() / SLIDE_DURATION_NANOS
        return when (target) {
            PanelTarget.EXPANDED -> (origin + distance).coerceAtMost(1f)
            PanelTarget.COLLAPSED -> (origin - distance).coerceAtLeast(0f)
        }
    }
}

private class ConsoleState {
    var mode = ConsoleMode.LOG
    private var logDraft = ""
    private var commandDraft = ""

    var draft: String
        get() = when (mode) {
            ConsoleMode.LOG -> logDraft
            ConsoleMode.COMMAND -> commandDraft
        }
        set(value) {
            when (mode) {
                ConsoleMode.LOG -> logDraft = value
                ConsoleMode.COMMAND -> commandDraft = value
            }
        }
}

private class ConsoleScreen(
    private val state: ConsoleState,
    val previousScreen: Screen?,
) : Screen(Component.translatable("console.ingamecnl.title")) {
    private val openedLevel = minecraft.level
    private var modeSwitchGesture = ModeSwitchGesture.READY
    private var transition = PanelTransition(0f, PanelTarget.EXPANDED, System.nanoTime())
    private lateinit var input: EditBox
    private lateinit var closeButton: Button
    private var feedback: Component? = null
    private var feedbackError = false
    private val timeFormat = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault())
    private var scrollAnchorSequence: Long? = null
    private var anchorVisibleTotal = 0L
    private var followHintX = 0
    private var followHintY = 0
    private var followHintWidth = 0
    private val logRowHeight: Int
        get() = font.lineHeight + 1
    private val panelHeight: Int
        get() = (height * 0.65).toInt().coerceAtLeast(144).coerceAtMost(height)
    private val commandAvailable: Boolean
        get() = minecraft.level === openedLevel && minecraft.level != null &&
            minecraft.connection != null && minecraft.singleplayerServer?.let {
                it.isRunning && !it.isStopped && it.isReady
            } == true
    private val modeSwitchHint: String
        get() {
            val fullHint = Component.translatable("console.ingamecnl.switch_mode").string
            val maxWidth = ((width - 48) / 2).coerceAtLeast(0)
            val hint = if (font.width(fullHint) <= maxWidth) fullHint
            else Component.translatable("console.ingamecnl.switch_mode.compact").string
            return font.plainSubstrByWidth(hint, maxWidth)
        }

    override fun init() {
        if (state.mode == ConsoleMode.COMMAND && !commandAvailable) state.mode = ConsoleMode.LOG
        closeButton = addRenderableWidget(
            PlainTextButton(0, 0, 20, 20, Component.literal("×"), { onClose() }, font),
        )
        closeButton.setTooltip(Tooltip.create(Component.translatable("console.ingamecnl.close")))
        input = addRenderableWidget(EditBox(font, 1, 20, Component.translatable("console.ingamecnl.input")))
        input.setBordered(false)
        input.setMaxLength(32767)
        input.value = state.draft
        input.setResponder { state.draft = it }
        input.setHint(Component.translatable(state.mode.statusKey))
        repositionElements()
    }

    override fun setInitialFocus() {
        setInitialFocus(input)
    }

    override fun repositionElements() {
        repositionPanel(System.nanoTime())
    }

    private fun repositionPanel(nowNanos: Long): Int {
        val top = ((transition.fractionAt(nowNanos) - 1f) * panelHeight).roundToInt()
        val hintX = width - 8 - font.width(modeSwitchHint)
        closeButton.setRectangle(20, 20, (width - 28).coerceAtLeast(0), top + 6)
        input.setRectangle((hintX - 28).coerceAtLeast(1), font.lineHeight + 2, 20, top + panelHeight - 22)
        return top
    }

    private fun selectMode(mode: ConsoleMode) {
        if (mode == ConsoleMode.COMMAND && !commandAvailable) return
        state.mode = mode
        input.value = state.draft
        feedback = null
        feedbackError = false
        input.setHint(Component.translatable(state.mode.statusKey))
        setInitialFocus(input)
    }

    fun togglePanel() {
        if (transition.target == PanelTarget.EXPANDED) {
            onClose()
            return
        }
        val nowNanos = System.nanoTime()
        transition = PanelTransition(transition.fractionAt(nowNanos), PanelTarget.EXPANDED, nowNanos)
        input.setEditable(true)
        closeButton.active = true
        closeButton.setTooltip(Tooltip.create(Component.translatable("console.ingamecnl.close")))
    }

    override fun tick() {
        if (minecraft.level !== openedLevel) {
            if (minecraft.screen === this) minecraft.setScreen(null)
            return
        }
        if (transition.target == PanelTarget.COLLAPSED && transition.fractionAt(System.nanoTime()) == 0f) {
            if (minecraft.screen === this) minecraft.setScreen(previousScreen)
            return
        }
        if (state.mode == ConsoleMode.COMMAND && !commandAvailable) {
            selectMode(ConsoleMode.LOG)
        }
        previousScreen?.tick()
    }

    override fun resize(width: Int, height: Int) {
        super.resize(width, height)
        previousScreen?.resize(width, height)
    }

    override fun keyPressed(event: KeyEvent): Boolean {
        if (event.key() == GLFW.GLFW_KEY_TAB &&
            (event.hasShiftDown() || modeSwitchGesture == ModeSwitchGesture.HELD)
        ) {
            if (transition.target == PanelTarget.COLLAPSED || modeSwitchGesture == ModeSwitchGesture.HELD) return true
            modeSwitchGesture = ModeSwitchGesture.HELD
            if (state.mode == ConsoleMode.COMMAND) selectMode(ConsoleMode.LOG)
            else if (commandAvailable) selectMode(ConsoleMode.COMMAND)
            else feedback = Component.translatable("console.ingamecnl.command.unavailable")
            return true
        }
        if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
            onClose()
            return true
        }
        if (transition.target == PanelTarget.COLLAPSED) return true
        if (input.isFocused && (event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_KP_ENTER)) {
            when (state.mode) {
                ConsoleMode.LOG -> submitLogCommand()
                ConsoleMode.COMMAND -> {
                    feedback = Component.translatable("console.ingamecnl.submit.command_unavailable")
                    feedbackError = false
                }
            }
            return true
        }
        if (event.key() == GLFW.GLFW_KEY_PAGE_UP) {
            scrollPage(true)
            return true
        }
        if (event.key() == GLFW.GLFW_KEY_PAGE_DOWN) {
            scrollPage(false)
            return true
        }
        if (event.key() == GLFW.GLFW_KEY_END && scrollAnchorSequence != null) {
            returnToBottom()
            return true
        }
        return super.keyPressed(event)
    }

    override fun keyReleased(event: KeyEvent): Boolean {
        if (event.key() == GLFW.GLFW_KEY_TAB && modeSwitchGesture == ModeSwitchGesture.HELD) {
            modeSwitchGesture = ModeSwitchGesture.READY
            return true
        }
        if (transition.target == PanelTarget.COLLAPSED) return true
        return super.keyReleased(event)
    }

    override fun charTyped(event: CharacterEvent): Boolean =
        transition.target == PanelTarget.COLLAPSED || super.charTyped(event)

    override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
        if (transition.target == PanelTarget.COLLAPSED) return true
        val top = repositionPanel(System.nanoTime())
        if (event.y() < 0 || event.y() >= top + panelHeight) return true
        if (followHintWidth > 0 &&
            event.x() >= followHintX && event.x() <= followHintX + followHintWidth &&
            event.y() >= followHintY && event.y() <= followHintY + font.lineHeight + 4
        ) {
            returnToBottom()
            return true
        }
        return super.mouseClicked(event, doubleClick)
    }

    override fun mouseReleased(event: MouseButtonEvent): Boolean {
        if (transition.target == PanelTarget.COLLAPSED) return true
        return super.mouseReleased(event)
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
        if (transition.target == PanelTarget.COLLAPSED) return true
        val top = repositionPanel(System.nanoTime())
        val bottom = top + panelHeight
        if (mouseY < top + 30 || mouseY > bottom - 64) return true
        if (scrollY == 0.0) return true
        val delta = if (scrollY > 0) scrollY.toInt().coerceAtLeast(1) else scrollY.toInt().coerceAtMost(-1)
        scrollByRecords(delta)
        return true
    }

    override fun onClose() {
        if (transition.target == PanelTarget.COLLAPSED) return
        val nowNanos = System.nanoTime()
        transition = PanelTransition(transition.fractionAt(nowNanos), PanelTarget.COLLAPSED, nowNanos)
        input.setEditable(false)
        closeButton.active = false
        closeButton.setTooltip(null)
    }

    override fun isPauseScreen() = false

    override fun extractBackground(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        previousScreen?.extractRenderStateWithTooltipAndSubtitles(graphics, -1, -1, delta)
    }

    override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        val top = repositionPanel(System.nanoTime())
        val bottom = top + panelHeight
        if (bottom <= 0) return
        graphics.enableScissor(0, 0, width, bottom)
        graphics.fill(0, top, width, bottom, 0x1A10151C)
        graphics.fill(0, bottom - 1, width, bottom, 0x1A526176)
        graphics.enableScissor(8, top + 6, (width - 32).coerceAtLeast(8), top + 26)
        graphics.text(font, title, 8, top + 12, 0xFFF0F4FA.toInt())
        graphics.disableScissor()

        val contentTop = top + 30
        val contentBottom = bottom - 64
        drawLog(graphics, 8, contentTop, (width - 8).coerceAtLeast(9), contentBottom)

        val statusY = bottom - 57
        val status = if (state.mode == ConsoleMode.LOG) logStatusText()
        else Component.translatable(state.mode.statusKey).string
        val newCount = newSinceScroll()
        val followHint = if (newCount > 0) {
            Component.translatable("console.ingamecnl.follow", newCount).string
        } else {
            null
        }
        val followWidth = followHint?.let(font::width) ?: 0
        val followX = width - 8 - followWidth
        val statusWidth = (if (followHint != null) followX - 12 else width - 8) - 8
        graphics.text(font, clip(status, statusWidth.coerceAtLeast(0)), 8, statusY, 0xFFB1C0D1.toInt())
        if (followHint != null) {
            graphics.text(font, followHint, followX, statusY, 0xFFFFD27F.toInt())
            followHintX = followX - 2
            followHintY = statusY - 2
            followHintWidth = followWidth + 4
        } else {
            followHintWidth = 0
        }

        feedback?.let {
            graphics.text(
                font,
                clip(it.string, (width - 16).coerceAtLeast(1)),
                8,
                bottom - 44,
                if (feedbackError) 0xFFFF7B72.toInt() else 0xFF9AD48B.toInt(),
            )
        }
        graphics.text(font, state.mode.prefix, 8, input.y, 0xFF8ED5BD.toInt())
        val hint = modeSwitchHint
        graphics.text(font, hint, width - 8 - font.width(hint), input.y, 0xFF95A5B8.toInt())
        super.extractRenderState(graphics, mouseX, mouseY, delta)
        graphics.disableScissor()
    }

    private data class LogEntryLayout(
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

    private fun drawLog(graphics: GuiGraphicsExtractor, left: Int, top: Int, right: Int, bottom: Int) {
        if (bottom <= top) return
        graphics.enableScissor(left, top, right, bottom)
        val records = LogCapture.history.visibleRecords()
        if (records.isEmpty()) {
            scrollAnchorSequence = null
            graphics.textWithWordWrap(
                font,
                Component.translatable(
                    if (LogCapture.filter.isUnfiltered) "console.ingamecnl.empty"
                    else "console.ingamecnl.empty.filtered",
                ),
                left,
                top + 2,
                (width - 16).coerceAtLeast(1),
                0xFF95A5B8.toInt(),
            )
            graphics.disableScissor()
            return
        }
        val numberWidth = numberWidth(records)
        val viewWidth = (right - left).coerceAtLeast(1)
        val viewport = (bottom - top).coerceAtLeast(1)
        val autoStart = autoFollowStart(records, viewWidth, numberWidth, viewport)
        var start = autoStart
        var bottomAligned = true
        val anchor = scrollAnchorSequence
        if (anchor != null) {
            val index = indexOfSequence(records, anchor)
            if (index < 0) {
                scrollAnchorSequence = records.first().sequence
                anchorVisibleTotal = LogCapture.history.visibleTotal
                start = 0
                bottomAligned = false
            } else {
                start = index
                if (start >= autoStart) {
                    scrollAnchorSequence = null
                    start = autoStart
                } else {
                    bottomAligned = false
                }
            }
        }
        var totalHeight = 0
        var measured = start
        while (measured < records.size && totalHeight < viewport) {
            totalHeight += heightOf(records[measured], viewWidth, numberWidth)
            measured++
        }
        var y = if (bottomAligned) (bottom - totalHeight).coerceAtLeast(top) else top
        var index = start
        while (index < records.size && y < bottom) {
            val entry = layoutEntry(records[index], viewWidth, numberWidth)
            drawEntry(graphics, entry, left, y)
            y += entry.lineCount * logRowHeight
            index++
        }
        graphics.disableScissor()
    }

    private fun drawEntry(graphics: GuiGraphicsExtractor, entry: LogEntryLayout, left: Int, y: Int) {
        var cursor = left
        graphics.text(font, entry.number, cursor, y, 0xFF6E7F93.toInt())
        cursor += font.width(entry.number) + 6
        graphics.text(font, entry.time, cursor, y, LOOM_BLUE)
        cursor += font.width(entry.time) + 4
        graphics.text(font, entry.threadLevel, cursor, y, loomLevelColor(entry.record.level))
        cursor += font.width(entry.threadLevel) + 4
        if (entry.logger.isNotEmpty()) {
            graphics.text(font, entry.logger, cursor, y, LOOM_CYAN)
            cursor += font.width(entry.logger) + 4
        }
        val color = loomMessageColor(entry.record.level)
        val continuation = if (entry.messageOnFirstLine) {
            entry.messageLines.firstOrNull()?.let { graphics.text(font, it, cursor, y, color) }
            entry.messageLines.drop(1)
        } else {
            entry.messageLines
        }
        var lineY = y
        for (line in continuation) {
            lineY += logRowHeight
            graphics.text(font, line, left, lineY, color)
        }
    }

    private fun layoutEntry(record: LogRecord, viewWidth: Int, numberWidth: Int): LogEntryLayout {
        val number = (record.sequence + 1).toString().padStart(numberWidth)
        val time = "[" + timeFormat.format(Instant.ofEpochMilli(record.timeMillis)) + "]"
        val fixedWidth = numberWidth + 6 + font.width(time) + 4
        val flexibleWidth = (viewWidth - fixedWidth).coerceAtLeast(0)
        val threadBudget = (flexibleWidth / 3).coerceAtLeast(0)
        val loggerBudget = (flexibleWidth / 3).coerceAtLeast(0)
        val thread = clip(record.threadName.ifBlank { "-" }, threadBudget)
        val threadLevel = "[$thread/${record.level.label}]"
        val simpleLogger = record.loggerName.substringAfterLast('.')
        var logger = if (simpleLogger.isBlank()) "" else "(" + clip(simpleLogger, loggerBudget) + ")"
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
            lines[lines.lastIndex] = clip(if (last.isEmpty()) "…" else "$last …", fullWidth)
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
            val next = height + heightOf(records[index], viewWidth, numberWidth)
            if (included > 0 && next > viewport) break
            height = next
            included++
            index--
        }
        return (index + 1).coerceIn(0, records.lastIndex)
    }

    private fun heightOf(record: LogRecord, viewWidth: Int, numberWidth: Int): Int =
        layoutEntry(record, viewWidth, numberWidth).lineCount * logRowHeight

    private fun numberWidth(records: List<LogRecord>): Int =
        (records.last().sequence + 1).toString().length.coerceAtLeast(2)

    private fun loomLevelColor(level: LogLevel): Int = when (level) {
        LogLevel.TRACE -> LOOM_BLUE
        LogLevel.DEBUG -> LOOM_GREEN
        LogLevel.INFO -> LOOM_GREEN
        LogLevel.WARN -> LOOM_YELLOW
        LogLevel.ERROR -> LOOM_RED
        LogLevel.FATAL -> LOOM_RED
    }

    private fun loomMessageColor(level: LogLevel): Int = when (level) {
        LogLevel.ERROR, LogLevel.FATAL -> LOOM_RED
        else -> LOOM_WHITE
    }

    private fun clip(text: String, maxWidth: Int): String {
        if (maxWidth <= 0) return ""
        if (font.width(text) <= maxWidth) return text
        val ellipsis = "..."
        val ellipsisWidth = font.width(ellipsis)
        if (maxWidth <= ellipsisWidth) return font.plainSubstrByWidth(text, maxWidth)
        return font.plainSubstrByWidth(text, maxWidth - ellipsisWidth) + ellipsis
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

    private fun submitLogCommand() {
        val text = input.value.trim()
        if (text.isEmpty()) return
        when (val parsed = parseFilterCommand(text)) {
            is FilterCommandParse.Error -> {
                feedback = commandError(parsed)
                feedbackError = true
            }
            is FilterCommandParse.Parsed -> {
                val command = parsed.command
                if (command == FilterCommand.Show) {
                    feedback = Component.literal(logStatusText())
                } else {
                    val filter = LogCapture.filter.apply(command) ?: return
                    LogCapture.setFilter(filter)
                    feedback = confirmation(command)
                }
                feedbackError = false
                input.value = ""
            }
        }
    }

    private fun commandError(error: FilterCommandParse.Error): Component = when (error.error) {
        FilterCommandError.UNKNOWN_COMMAND -> Component.translatable("console.ingamecnl.error.unknown_command")
        FilterCommandError.MISSING_SUBCOMMAND -> Component.translatable("console.ingamecnl.error.missing_subcommand")
        FilterCommandError.UNKNOWN_SUBCOMMAND -> Component.translatable("console.ingamecnl.error.unknown_subcommand")
        FilterCommandError.MISSING_LEVEL -> Component.translatable("console.ingamecnl.error.missing_level")
        FilterCommandError.INVALID_LEVEL ->
            Component.translatable("console.ingamecnl.error.invalid_level", error.detail ?: "")
        FilterCommandError.MISSING_KEYWORD -> Component.translatable("console.ingamecnl.error.missing_keyword")
        FilterCommandError.TOO_MANY_ARGUMENTS ->
            Component.translatable("console.ingamecnl.error.too_many_arguments")
    }

    private fun confirmation(command: FilterCommand): Component = when (command) {
        is FilterCommand.SetLevel ->
            Component.translatable("console.ingamecnl.filter.set.level", command.level.label)
        is FilterCommand.SetKeyword ->
            Component.translatable("console.ingamecnl.filter.set.keyword", command.keyword)
        FilterCommand.ResetAll -> Component.translatable("console.ingamecnl.filter.reset.all")
        FilterCommand.ResetLevel -> Component.translatable("console.ingamecnl.filter.reset.level")
        FilterCommand.ResetKeyword -> Component.translatable("console.ingamecnl.filter.reset.keyword")
        FilterCommand.Show -> Component.literal(logStatusText())
    }

    private fun scrollByRecords(delta: Int) {
        if (delta == 0) return
        val records = LogCapture.history.visibleRecords()
        if (records.isEmpty()) return
        val autoStart = autoFollowStart(records, logViewWidth(), numberWidth(records), logViewportHeight())
        val current = currentStart(records, autoStart)
        val target = (current - delta).coerceIn(0, autoStart)
        if (target >= autoStart) returnToBottom() else setAnchor(records[target].sequence)
    }

    private fun scrollPage(up: Boolean) {
        val records = LogCapture.history.visibleRecords()
        if (records.isEmpty()) return
        val viewWidth = logViewWidth()
        val numberWidth = numberWidth(records)
        val viewport = logViewportHeight()
        val autoStart = autoFollowStart(records, viewWidth, numberWidth, viewport)
        val current = currentStart(records, autoStart)
        var target = current
        var height = 0
        if (up) {
            while (target > 0 && height < viewport) {
                target--
                height += heightOf(records[target], viewWidth, numberWidth)
            }
        } else {
            while (target < records.lastIndex && height < viewport) {
                height += heightOf(records[target], viewWidth, numberWidth)
                target++
            }
        }
        if (target == current) return
        if (target >= autoStart) returnToBottom() else setAnchor(records[target].sequence)
    }

    private fun currentStart(records: List<LogRecord>, autoStart: Int): Int {
        val anchor = scrollAnchorSequence ?: return autoStart
        val index = indexOfSequence(records, anchor)
        return if (index < 0) 0 else index
    }

    private fun setAnchor(sequence: Long) {
        if (scrollAnchorSequence == null) anchorVisibleTotal = LogCapture.history.visibleTotal
        scrollAnchorSequence = sequence
    }

    private fun logViewWidth(): Int = (width - 16).coerceAtLeast(1)

    private fun logViewportHeight(): Int = (panelHeight - 94).coerceAtLeast(1)

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

    private fun returnToBottom() {
        scrollAnchorSequence = null
    }

    private fun newSinceScroll(): Long {
        if (scrollAnchorSequence == null) return 0
        return (LogCapture.history.visibleTotal - anchorVisibleTotal).coerceAtLeast(0)
    }
}
