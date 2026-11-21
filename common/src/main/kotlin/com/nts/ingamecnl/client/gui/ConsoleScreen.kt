package com.nts.ingamecnl.client.gui

import com.nts.ingamecnl.client.ConsoleClient
import com.nts.ingamecnl.client.config.ConsoleConfig
import com.nts.ingamecnl.client.log.FilterCommand
import com.nts.ingamecnl.client.log.FilterCommandError
import com.nts.ingamecnl.client.log.FilterCommandParse
import com.nts.ingamecnl.client.log.LogCapture
import com.nts.ingamecnl.client.log.apply
import com.nts.ingamecnl.client.log.parseFilterCommand
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.components.PlainTextButton
import net.minecraft.client.gui.components.Tooltip
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import org.lwjgl.glfw.GLFW
import kotlin.math.roundToInt

internal class ConsoleScreen(
    internal val state: ConsoleState,
    val previousScreen: Screen?,
    val overlayOnly: Boolean = false,
) : Screen(Component.translatable("console.ingamecnl.title")) {
    val openedLevel = minecraft.level
    private var modeSwitchGesture = ModeSwitchGesture.READY
    internal var transition = PanelTransition(0f, PanelTarget.EXPANDED, System.nanoTime())
    internal lateinit var input: EditBox
    private lateinit var closeButton: Button
    internal var feedback: Component? = null
    internal var feedbackError = false
    internal val logView: LogView by lazy { LogView(font) }

    internal val panelHeight: Int
        get() = (height * (if (overlayOnly) ConsoleConfig.overlayHeightPercent() / 100f else 0.65f))
            .toInt().coerceAtLeast(144).coerceAtMost(height)
    internal val slideDurationNanos: Long
        get() = if (overlayOnly) ConsoleConfig.overlayAnimationNanos() else SLIDE_DURATION_NANOS
    internal val contentBottomInset: Int
        get() = if (overlayOnly) 34 else 64
    internal val commandAvailable: Boolean
        get() = minecraft.level === openedLevel && minecraft.level != null &&
            minecraft.connection != null && minecraft.singleplayerServer?.let {
                it.isRunning && !it.isStopped && it.isReady
            } == true
    internal val modeSwitchHint: String
        get() {
            val switchKey = ConsoleClient.switchModeLabel()
            val fullHint = Component.translatable("console.ingamecnl.switch_mode", switchKey).string
            val maxWidth = ((width - 48) / 2).coerceAtLeast(0)
            val hint = if (font.width(fullHint) <= maxWidth) fullHint
            else Component.translatable("console.ingamecnl.switch_mode.compact", switchKey).string
            return font.plainSubstrByWidth(hint, maxWidth)
        }

    override fun init() {
        if (overlayOnly) return
        if (state.mode == ConsoleMode.VIEW_ONLY) {
            state.mode = ConsoleMode.LOG
        }
        closeButton = addRenderableWidget(
            PlainTextButton(0, 0, 20, 20, Component.literal("×"), { onClose() }, font),
        )
        closeButton.setTooltip(
            Tooltip.create(
                Component.translatable("console.ingamecnl.close", ConsoleClient.keyLabel(ConsoleClient.closeKey())),
            ),
        )
        input = addRenderableWidget(EditBox(font, 1, 20, Component.translatable("console.ingamecnl.input")))
        input.setBordered(false)
        input.setMaxLength(32767)
        input.value = state.draft
        input.setResponder { state.draft = it }
        input.setHint(Component.translatable(state.mode.statusKey))
        repositionElements()
    }

    override fun setInitialFocus() {
        if (overlayOnly) return
        setInitialFocus(input)
    }

    override fun repositionElements() {
        repositionPanel(System.nanoTime())
    }

    internal fun repositionPanel(nowNanos: Long): Int {
        val top = ((transition.fractionAt(nowNanos, slideDurationNanos) - 1f) * panelHeight).roundToInt()
        if (overlayOnly) return top
        val hintX = width - 8 - font.width(modeSwitchHint)
        closeButton.setRectangle(20, 20, (width - 28).coerceAtLeast(0), top + 6)
        input.setRectangle((hintX - 28).coerceAtLeast(1), font.lineHeight + 2, 20, top + panelHeight - 22)
        return top
    }

    private fun selectMode(mode: ConsoleMode) {
        if (mode == ConsoleMode.VIEW_ONLY) {
            ConsoleClient.enterViewOnly(previousScreen)
            return
        }
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
        transition = PanelTransition(transition.fractionAt(nowNanos, slideDurationNanos), PanelTarget.EXPANDED, nowNanos)
        input.setEditable(true)
        closeButton.active = true
        closeButton.setTooltip(
            Tooltip.create(
                Component.translatable("console.ingamecnl.close", ConsoleClient.keyLabel(ConsoleClient.closeKey())),
            ),
        )
    }

    fun reveal() {
        val nowNanos = System.nanoTime()
        transition = PanelTransition(transition.fractionAt(nowNanos, slideDurationNanos), PanelTarget.EXPANDED, nowNanos)
    }

    fun collapse() {
        val nowNanos = System.nanoTime()
        transition = PanelTransition(transition.fractionAt(nowNanos, slideDurationNanos), PanelTarget.COLLAPSED, nowNanos)
    }

    val isCollapsed: Boolean
        get() = transition.target == PanelTarget.COLLAPSED &&
            transition.fractionAt(System.nanoTime(), slideDurationNanos) == 0f

    override fun tick() {
        if (minecraft.level !== openedLevel) {
            if (minecraft.screen === this) minecraft.setScreen(null)
            state.mode = ConsoleMode.VIEW_ONLY
            return
        }
        if (transition.target == PanelTarget.COLLAPSED &&
            transition.fractionAt(System.nanoTime(), slideDurationNanos) == 0f
        ) {
            if (minecraft.screen === this) minecraft.setScreen(previousScreen)
            state.mode = ConsoleMode.VIEW_ONLY
            return
        }
        previousScreen?.tick()
    }

    override fun resize(width: Int, height: Int) {
        super.resize(width, height)
        previousScreen?.resize(width, height)
    }

    override fun keyPressed(event: KeyEvent): Boolean {
        val switchKey = ConsoleClient.switchModeKey()
        if (switchKey?.matches(event) == true && event.hasShiftDown() &&
            (modeSwitchGesture == ModeSwitchGesture.READY || modeSwitchGesture == ModeSwitchGesture.HELD)
        ) {
            if (transition.target == PanelTarget.COLLAPSED || modeSwitchGesture == ModeSwitchGesture.HELD) return true
            modeSwitchGesture = ModeSwitchGesture.HELD
            when (state.mode) {
                ConsoleMode.VIEW_ONLY -> selectMode(ConsoleMode.LOG)
                ConsoleMode.LOG -> selectMode(ConsoleMode.COMMAND)
                ConsoleMode.COMMAND -> selectMode(ConsoleMode.VIEW_ONLY)
            }
            return true
        }
        if (ConsoleClient.closeKey()?.matches(event) == true) {
            onClose()
            return true
        }
        if (transition.target == PanelTarget.COLLAPSED) return true
        if (input.isFocused && (event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_KP_ENTER)) {
            when (state.mode) {
                ConsoleMode.VIEW_ONLY -> Unit
                ConsoleMode.LOG -> submitLogCommand()
                ConsoleMode.COMMAND -> {
                    feedback = Component.translatable("console.ingamecnl.submit.command_unavailable")
                    feedbackError = false
                }
            }
            return true
        }
        if (ConsoleClient.pageUpKey()?.matches(event) == true) {
            scrollPage(true)
            return true
        }
        if (ConsoleClient.pageDownKey()?.matches(event) == true) {
            scrollPage(false)
            return true
        }
        if (ConsoleClient.goToBottomKey()?.matches(event) == true && logView.isScrolled) {
            returnToBottom()
            return true
        }
        return super.keyPressed(event)
    }

    override fun keyReleased(event: KeyEvent): Boolean {
        if (ConsoleClient.switchModeKey()?.matches(event) == true && modeSwitchGesture == ModeSwitchGesture.HELD) {
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
        val hint = logView.followHintBounds()
        if (hint != null &&
            event.x() >= hint.x && event.x() <= hint.x + hint.width &&
            event.y() >= hint.y && event.y() <= hint.y + hint.height
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
        if (mouseY < top + 30 || mouseY > bottom - contentBottomInset) return true
        if (scrollY == 0.0) return true
        val delta = if (scrollY > 0) scrollY.toInt().coerceAtLeast(1) else scrollY.toInt().coerceAtMost(-1)
        scrollByRecords(delta)
        return true
    }

    override fun onClose() {
        if (overlayOnly || transition.target == PanelTarget.COLLAPSED) return
        val nowNanos = System.nanoTime()
        transition = PanelTransition(transition.fractionAt(nowNanos, slideDurationNanos), PanelTarget.COLLAPSED, nowNanos)
        input.setEditable(false)
        closeButton.active = false
        closeButton.setTooltip(null)
    }

    override fun isPauseScreen() = false

    override fun extractBackground(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        previousScreen?.extractRenderStateWithTooltipAndSubtitles(graphics, -1, -1, delta)
    }

    override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        if (overlayOnly) {
            ConsolePanelRenderer.drawOverlay(this, graphics)
            return
        }
        if (!ConsolePanelRenderer.drawPanel(this, graphics)) return
        super.extractRenderState(graphics, mouseX, mouseY, delta)
        graphics.disableScissor()
    }

    internal fun scrollPage(up: Boolean) {
        logView.scrollPage(up, logViewWidth(), logViewportHeight())
    }

    internal fun returnToBottom() {
        logView.returnToBottom()
    }

    private fun scrollByRecords(delta: Int) {
        logView.scrollByRecords(delta, logViewWidth(), logViewportHeight())
    }

    private fun logViewWidth(): Int = (width - 16).coerceAtLeast(1)

    private fun logViewportHeight(): Int = (panelHeight - 30 - contentBottomInset).coerceAtLeast(1)

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
                    ConsoleConfig.applyFilter(filter)
                    feedback = confirmation(command)
                }
                feedbackError = false
                input.value = ""
            }
        }
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
}
