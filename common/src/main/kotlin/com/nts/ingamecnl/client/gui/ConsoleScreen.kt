package com.nts.ingamecnl.client.gui

import com.nts.ingamecnl.client.ConsoleClient
import com.nts.ingamecnl.client.command.CommandAvailability
import com.nts.ingamecnl.client.config.ConfigFilterSync
import com.nts.ingamecnl.config.ConsoleConfig
import com.nts.ingamecnl.client.log.AutoSplashCommand
import com.nts.ingamecnl.client.log.AutoSplashCommandError
import com.nts.ingamecnl.client.log.AutoSplashCommandParse
import com.nts.ingamecnl.client.log.FilterCommand
import com.nts.ingamecnl.client.log.FilterCommandError
import com.nts.ingamecnl.client.log.FilterCommandParse
import com.nts.ingamecnl.client.log.LogCapture
import com.nts.ingamecnl.client.log.apply
import com.nts.ingamecnl.client.log.parseAutoSplashCommand
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
    val autoSplash: Boolean = false,
    initiallyExpanded: Boolean = false,
) : Screen(Component.translatable("console.ingamecnl.title")) {
    private var modeSwitchGesture = ModeSwitchGesture.READY
    internal var transition = PanelTransition(
        if (initiallyExpanded) 1f else 0f,
        PanelTarget.EXPANDED,
        System.nanoTime(),
    )
    internal lateinit var input: EditBox
    private lateinit var closeButton: Button
    internal var feedback: Component? = null
    internal var feedbackError = false
    private var historyIndex = -1
    private var historyDraft = ""
    internal val logView: LogView by lazy { LogView(font) }
    private val commandView: CommandView by lazy { CommandView(font, state.commandTranscript) }

    internal val panelHeight: Int
        get() {
            val fraction = when {
                autoSplash -> ConsoleConfig.autoSplashHeightPercent() / 100f
                overlayOnly -> ConsoleConfig.overlayHeightPercent() / 100f
                else -> 0.65f
            }
            return (height * fraction).toInt().coerceAtLeast(144).coerceAtMost(height)
        }
    internal val slideDurationNanos: Long
        get() = if (overlayOnly || autoSplash) ConsoleConfig.overlayAnimationNanos() else SLIDE_DURATION_NANOS
    internal val contentBottomInset: Int
        get() = when {
            autoSplash -> 12
            overlayOnly -> 34
            else -> 64
        }
    internal val acceptsTextInput: Boolean
        get() = !overlayOnly && state.mode != ConsoleMode.VIEW_ONLY
    internal val isScrolled: Boolean
        get() = if (state.mode == ConsoleMode.COMMAND) commandView.isScrolled else logView.isScrolled
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
            Tooltip.create(Component.translatable("console.ingamecnl.close")),
        )
        input = addRenderableWidget(EditBox(font, 1, 20, Component.translatable("console.ingamecnl.input")))
        input.setBordered(false)
        input.setMaxLength(32767)
        input.value = state.draft
        input.setResponder { state.draft = it }
        input.setHint(inputHint())
        resetHistoryNavigation()
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
        input.setHint(inputHint())
        resetHistoryNavigation()
        if (mode == ConsoleMode.COMMAND) commandView.returnToBottom()
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
            Tooltip.create(Component.translatable("console.ingamecnl.close")),
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

    fun resetTransition() {
        transition = PanelTransition(0f, PanelTarget.EXPANDED, System.nanoTime())
    }

    val isCollapsed: Boolean
        get() = transition.target == PanelTarget.COLLAPSED &&
            transition.fractionAt(System.nanoTime(), slideDurationNanos) == 0f

    override fun tick() {
        if (transition.target == PanelTarget.COLLAPSED &&
            transition.fractionAt(System.nanoTime(), slideDurationNanos) == 0f
        ) {
            if (minecraft.screen === this) minecraft.setScreen(previousScreen)
            return
        }
        previousScreen?.tick()
    }

    override fun resize(width: Int, height: Int) {
        super.resize(width, height)
        previousScreen?.resize(width, height)
    }

    override fun keyPressed(event: KeyEvent): Boolean {
        if (event.key() == GLFW.GLFW_KEY_TAB && event.hasShiftDown() &&
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
        if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
            onClose()
            return true
        }
        if (transition.target == PanelTarget.COLLAPSED) return true
        if (input.isFocused && event.key() == GLFW.GLFW_KEY_UP && navigateHistory(-1)) return true
        if (input.isFocused && event.key() == GLFW.GLFW_KEY_DOWN && navigateHistory(1)) return true
        if (input.isFocused && (event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_KP_ENTER)) {
            when (state.mode) {
                ConsoleMode.VIEW_ONLY -> Unit
                ConsoleMode.LOG -> submitLogCommand()
                ConsoleMode.COMMAND -> submitServerCommand()
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
        if (ConsoleClient.goToBottomKey()?.matches(event) == true && isScrolled) {
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
        if (autoSplash) {
            ConsolePanelRenderer.drawAutoSplash(this, graphics)
            return
        }
        if (overlayOnly) {
            ConsolePanelRenderer.drawOverlay(this, graphics)
            return
        }
        if (!ConsolePanelRenderer.drawPanel(this, graphics)) return
        super.extractRenderState(graphics, mouseX, mouseY, delta)
        graphics.disableScissor()
    }

    internal fun scrollPage(up: Boolean) {
        if (state.mode == ConsoleMode.COMMAND) {
            commandView.scrollPage(up, logViewWidth(), logViewportHeight())
        } else {
            logView.scrollPage(up, logViewWidth(), logViewportHeight())
        }
    }

    internal fun returnToBottom() {
        if (state.mode == ConsoleMode.COMMAND) commandView.returnToBottom() else logView.returnToBottom()
    }

    private fun scrollByRecords(delta: Int) {
        if (state.mode == ConsoleMode.COMMAND) {
            commandView.scrollByLines(delta, logViewWidth(), logViewportHeight())
        } else {
            logView.scrollByRecords(delta, logViewWidth(), logViewportHeight())
        }
    }

    internal fun drawContent(graphics: GuiGraphicsExtractor, left: Int, top: Int, right: Int, bottom: Int) {
        if (state.mode == ConsoleMode.COMMAND) {
            commandView.draw(graphics, left, top, right, bottom)
        } else {
            logView.draw(graphics, left, top, right, bottom)
        }
    }

    internal fun commandStatusText(): String = when (ConsoleClient.commandAvailability()) {
        CommandAvailability.LOCAL ->
            Component.translatable("console.ingamecnl.command.status.local").string
        CommandAvailability.REMOTE ->
            Component.translatable("console.ingamecnl.command.status.remote").string
        CommandAvailability.PENDING ->
            Component.translatable("console.ingamecnl.command.status.pending").string
        CommandAvailability.UNAVAILABLE ->
            Component.translatable("console.ingamecnl.command.unavailable").string
    }

    private fun inputHint(): Component = Component.translatable(
        if (state.mode == ConsoleMode.COMMAND) "console.ingamecnl.input.command"
        else state.mode.statusKey,
    )

    private fun logViewWidth(): Int = (width - 16).coerceAtLeast(1)

    private fun logViewportHeight(): Int = (panelHeight - 30 - contentBottomInset).coerceAtLeast(1)

    private fun navigateHistory(direction: Int): Boolean {
        val history = when (state.mode) {
            ConsoleMode.LOG -> state.logHistory
            ConsoleMode.COMMAND -> state.commandHistory
            ConsoleMode.VIEW_ONLY -> return false
        }
        if (history.size == 0) return false
        if (direction < 0) {
            if (historyIndex == -1) {
                historyDraft = input.value
                historyIndex = history.size - 1
            } else if (historyIndex > 0) {
                historyIndex--
            } else {
                return true
            }
        } else {
            if (historyIndex == -1) return false
            if (historyIndex < history.size - 1) {
                historyIndex++
            } else {
                historyIndex = -1
                applyHistoryValue(historyDraft)
                return true
            }
        }
        applyHistoryValue(history[historyIndex])
        return true
    }

    private fun applyHistoryValue(value: String) {
        input.value = value
        state.draft = value
        input.setCursorPosition(value.length)
    }

    private fun resetHistoryNavigation() {
        historyIndex = -1
        historyDraft = ""
    }

    private fun submitServerCommand() {
        val text = input.value.trim()
        if (text.isEmpty()) return
        state.commandHistory.add(text)
        resetHistoryNavigation()
        if (!ConsoleClient.submitCommand(text)) {
            feedback = Component.translatable("console.ingamecnl.command.unavailable")
            feedbackError = true
            return
        }
        state.commandTranscript.addInput(text)
        commandView.returnToBottom()
        input.value = ""
        state.draft = ""
        feedback = null
        feedbackError = false
    }

    private fun submitLogCommand() {
        val text = input.value.trim()
        if (text.isEmpty()) return
        state.logHistory.add(text)
        resetHistoryNavigation()
        if (text.equals("autosplash", ignoreCase = true) || text.startsWith("autosplash ", ignoreCase = true)) {
            submitAutoSplashCommand(text)
            return
        }
        when (val parsed = parseFilterCommand(text)) {
            is FilterCommandParse.Error -> {
                feedback = commandError(parsed.error, parsed.detail)
                feedbackError = true
            }
            is FilterCommandParse.Parsed -> {
                val command = parsed.command
                if (command == FilterCommand.Show) {
                    feedback = Component.literal(logStatusText())
                } else {
                    val filter = LogCapture.filter.apply(command) ?: return
                    ConfigFilterSync.apply(filter)
                    feedback = confirmation(command)
                }
                feedbackError = false
                input.value = ""
            }
        }
    }

    private fun submitAutoSplashCommand(text: String) {
        when (val parsed = parseAutoSplashCommand(text)) {
            is AutoSplashCommandParse.Error -> {
                feedback = autoSplashCommandError(parsed.error)
                feedbackError = true
            }
            is AutoSplashCommandParse.Parsed -> {
                when (val command = parsed.command) {
                    is AutoSplashCommand.SetKeyword -> {
                        ConsoleConfig.setAutoSplashKeyword(command.keyword)
                        feedback = Component.translatable(
                            "console.ingamecnl.autosplash.set.keyword",
                            command.keyword,
                        )
                        feedbackError = false
                        input.value = ""
                    }
                }
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

    private fun commandError(error: FilterCommandError, detail: String? = null): Component = when (error) {
        FilterCommandError.UNKNOWN_COMMAND -> Component.translatable("console.ingamecnl.error.unknown_command")
        FilterCommandError.MISSING_SUBCOMMAND -> Component.translatable("console.ingamecnl.error.missing_subcommand")
        FilterCommandError.UNKNOWN_SUBCOMMAND -> Component.translatable("console.ingamecnl.error.unknown_subcommand")
        FilterCommandError.MISSING_LEVEL -> Component.translatable("console.ingamecnl.error.missing_level")
        FilterCommandError.INVALID_LEVEL ->
            Component.translatable("console.ingamecnl.error.invalid_level", detail ?: "")
        FilterCommandError.MISSING_KEYWORD -> Component.translatable("console.ingamecnl.error.missing_keyword")
        FilterCommandError.TOO_MANY_ARGUMENTS ->
            Component.translatable("console.ingamecnl.error.too_many_arguments")
    }

    private fun autoSplashCommandError(error: AutoSplashCommandError): Component = when (error) {
        AutoSplashCommandError.MISSING_SUBCOMMAND ->
            Component.translatable("console.ingamecnl.error.autosplash_missing_subcommand")
        AutoSplashCommandError.UNKNOWN_SUBCOMMAND ->
            Component.translatable("console.ingamecnl.error.autosplash_unknown_subcommand")
        AutoSplashCommandError.MISSING_KEYWORD ->
            Component.translatable("console.ingamecnl.error.autosplash_missing_keyword")
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
