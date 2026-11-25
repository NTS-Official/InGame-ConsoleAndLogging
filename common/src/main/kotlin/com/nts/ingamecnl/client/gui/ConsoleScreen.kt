package com.nts.ingamecnl.client.gui

import com.nts.ingamecnl.MOD_ID

import com.nts.ingamecnl.client.ConsoleClient
import com.nts.ingamecnl.client.command.CommandAvailability
import com.nts.ingamecnl.client.command.CommandFormatting
import com.nts.ingamecnl.client.command.CommandHighlighter
import com.nts.ingamecnl.client.command.CommandInputState
import com.nts.ingamecnl.client.mixin.CommandSuggestionsAccessor
import com.nts.ingamecnl.client.config.ConfigFilterSync
import com.nts.ingamecnl.client.grep.GrepCommandError
import com.nts.ingamecnl.client.grep.GrepCommandParse
import com.nts.ingamecnl.client.grep.GrepView
import com.nts.ingamecnl.client.grep.parseGrepCommand
import com.nts.ingamecnl.config.ConsoleConfig
import com.nts.ingamecnl.log.LogLevel
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
import net.minecraft.CrashReport
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.CommandSuggestions
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.components.PlainTextButton
import net.minecraft.client.gui.components.Tooltip
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style
import net.minecraft.util.FormattedCharSequence
import org.lwjgl.glfw.GLFW
import kotlin.math.roundToInt

/** 面板高度超过该百分比时不再向下展开候选列表，避免遮挡过多游戏画面。 */
private const val COMPLETION_MAX_PANEL_PERCENT = 90

/** 原版候选列表的固定起点与行数上限，用于把列表整体平移到输入行下方。 */
private const val SUGGESTION_ORIGIN_Y = 72
private const val SUGGESTION_MAX_ROWS = 8

internal class ConsoleScreen(
    internal val state: ConsoleState,
    val previousScreen: Screen?,
    val overlayOnly: Boolean = false,
    val autoSplash: Boolean = false,
    initiallyExpanded: Boolean = false,
) : Screen(Component.translatable("console.$MOD_ID.title")) {
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
    private val highlighter: CommandHighlighter by lazy { CommandHighlighter(minecraft, { state.mode }) }
    private var inputState = CommandInputState.EMPTY
    internal val timelineView: TimelineView by lazy { TimelineView(font) }
    internal val grepView: GrepView by lazy { GrepView(font) }
    internal var suggestions: CommandSuggestions? = null
        private set

    /** 补全区（候选列表或错误/用法提示）可以下沉展示的前提条件。 */
    internal val completionControlVisible: Boolean
        get() = acceptsTextInput &&
            !grepView.isOpen &&
            this::input.isInitialized &&
            input.isFocused &&
            ConsoleConfig.panelHeightPercent() <= COMPLETION_MAX_PANEL_PERCENT

    /** 候选列表只在补全区可用且原版列表已展开时下沉展示。 */
    internal val completionVisible: Boolean
        get() = completionControlVisible && suggestions?.isVisible == true

    /** 候选列表整体下移到输入行下方所需的垂直位移。 */
    internal val suggestionOffset: Int
        get() = if (!this::input.isInitialized) 0 else input.y + font.lineHeight + 2 - SUGGESTION_ORIGIN_Y

    internal val timelineSource: TimelineSource
        get() = when {
            state.mode == ConsoleMode.COMMAND && ConsoleClient.isMixedMode() -> state.serverConsole
            state.mode == ConsoleMode.COMMAND -> state.commandTranscript
            else -> LogTimelineSource
        }

    /** 当前面板模式，供命令树选择使用。 */
    internal val currentMode: ConsoleMode
        get() = state.mode

    /** 当前输入内容，供命令树选择使用。 */
    internal val currentInput: String
        get() = if (this::input.isInitialized) input.value else ""

    internal val panelHeight: Int
        get() {
            val fraction = if (autoSplash) {
                ConsoleConfig.autoSplashHeightPercent() / 100f
            } else {
                ConsoleConfig.panelHeightPercent() / 100f
            }
            return (height * fraction).toInt().coerceAtLeast(144).coerceAtMost(height)
        }
    internal val slideDurationNanos: Long
        get() = ConsoleConfig.panelAnimationNanos()
    internal val contentBottomInset: Int
        get() = if (autoSplash) 12 else 34
    internal val acceptsTextInput: Boolean
        get() = !overlayOnly && state.mode != ConsoleMode.VIEW_ONLY
    internal val isScrolled: Boolean
        get() = if (grepView.isOpen) true else timelineView.isScrolled
    internal val modeSwitchHint: String
        get() {
            val switchKey = ConsoleClient.switchModeLabel()
            val fullHint = Component.translatable("console.$MOD_ID.switch_mode", switchKey).string
            val maxWidth = ((width - 48) / 2).coerceAtLeast(0)
            val hint = if (font.width(fullHint) <= maxWidth) fullHint
            else Component.translatable("console.$MOD_ID.switch_mode.compact", switchKey).string
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
            Tooltip.create(Component.translatable("console.$MOD_ID.close")),
        )
        input = addRenderableWidget(EditBox(font, 1, 20, Component.translatable("console.$MOD_ID.input")))
        input.setBordered(false)
        input.setMaxLength(32767)
        // 输入框不让出焦点：否则 Tab 会被界面的焦点导航抢走，转去选中右上角关闭按钮。
        input.setCanLoseFocus(false)
        input.value = state.draft
        input.setResponder {
            state.draft = it
            refreshInputState()
        }
        input.addFormatter { text, offset -> formatInput(text, offset) }
        input.setHint(inputHint())
        suggestions = CommandSuggestions(
            minecraft,
            this,
            input,
            font,
            true,
            false,
            0,
            SUGGESTION_MAX_ROWS,
            false,
            0xB0000000.toInt(),
        ).also {
            // 与原版聊天栏一致：Tab 始终用于补全并展开候选，不进入界面的焦点切换。
            it.setAllowHiding(false)
            it.setAllowSuggestions(true)
            if (minecraft.player != null) it.updateCommandInfo()
        }
        refreshInputState()
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
        closeButton.setRectangle(20, 20, (width - 28).coerceAtLeast(0), top + ConsolePanelRenderer.TITLE_OFFSET)
        input.setRectangle((hintX - 28).coerceAtLeast(1), font.lineHeight + 2, 20, top + panelHeight - 14)
        return top
    }

    private fun selectMode(mode: ConsoleMode) {
        grepView.close()
        if (state.mode == ConsoleMode.COMMAND && mode != ConsoleMode.COMMAND && ConsoleClient.isMixedMode()) {
            state.serverConsole.resetFilter()
        }
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
        if (mode == ConsoleMode.COMMAND) timelineView.returnToBottom()
        refreshInputState()
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
            Tooltip.create(Component.translatable("console.$MOD_ID.close")),
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
        if (grepView.isOpen) {
            val wasOpen = grepView.isOpen
            grepView.handleKey(event)
            if (wasOpen && !grepView.isOpen) setInitialFocus(input)
            return true
        }
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
        if (canComplete() && suggestions?.keyPressed(event) == true) return true
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
        if (event.key() == GLFW.GLFW_KEY_HOME && canScrollToTop()) {
            scrollToTop()
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
        if (grepView.isOpen) return true
        if (event.key() == GLFW.GLFW_KEY_TAB && modeSwitchGesture == ModeSwitchGesture.HELD) {
            modeSwitchGesture = ModeSwitchGesture.READY
            return true
        }
        if (transition.target == PanelTarget.COLLAPSED) return true
        return super.keyReleased(event)
    }

    override fun charTyped(event: CharacterEvent): Boolean =
        grepView.isOpen || transition.target == PanelTarget.COLLAPSED || super.charTyped(event)

    override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
        if (grepView.isOpen || transition.target == PanelTarget.COLLAPSED) return true
        if (completionVisible) {
            val offset = suggestionOffset
            val shifted = MouseButtonEvent(event.x(), event.y() - offset, event.buttonInfo())
            if (suggestions?.mouseClicked(shifted) == true) return true
        }
        val top = repositionPanel(System.nanoTime())
        if (event.y() < 0 || event.y() >= top + panelHeight) return true
        val hint = timelineView.followHintBounds()
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
        if (completionVisible && scrollY != 0.0 && suggestions?.mouseScrolled(scrollY) == true) return true
        val top = repositionPanel(System.nanoTime())
        val bottom = top + panelHeight
        if (mouseY < top + ConsolePanelRenderer.CONTENT_OFFSET || mouseY > bottom - contentBottomInset) return true
        if (scrollY == 0.0) return true
        val delta = if (scrollY > 0) scrollY.toInt().coerceAtLeast(1) else scrollY.toInt().coerceAtMost(-1)
        if (grepView.isOpen) grepView.scroll(-delta) else scrollByRecords(delta)
        return true
    }

    override fun onClose() {
        grepView.close()
        if (state.mode == ConsoleMode.COMMAND && ConsoleClient.isMixedMode()) state.serverConsole.resetFilter()
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
        if (!ConsolePanelRenderer.drawPanel(this, graphics, mouseX, mouseY)) return
        super.extractRenderState(graphics, mouseX, mouseY, delta)
        graphics.disableScissor()
        // 候选列表在面板下方展开，必须等面板剪裁结束后绘制，否则只会露出被裁掉的一条。
        ConsolePanelRenderer.drawSuggestions(this, graphics, mouseX, mouseY)
    }

    internal fun scrollPage(up: Boolean) {
        if (grepView.isOpen) grepView.scroll(if (up) -1 else 1) else timelineView.scrollPage(up)
    }

    internal fun returnToBottom() {
        if (grepView.isOpen) return
        timelineView.returnToBottom()
    }

    private fun canScrollToTop(): Boolean = !grepView.isOpen && input.value.isEmpty()

    private fun scrollToTop() {
        timelineView.scrollToTop()
    }

    private fun scrollByRecords(delta: Int) {
        if (grepView.isOpen) grepView.scroll(-delta) else timelineView.scrollByLines(delta)
    }

    internal fun drawContent(graphics: GuiGraphicsExtractor, left: Int, top: Int, right: Int, bottom: Int) {
        if (grepView.isOpen) {
            grepView.draw(graphics, left, top, right, bottom)
        } else {
            timelineView.draw(graphics, timelineSource, left, top, right, bottom)
        }
    }

    internal fun commandStatusText(): String {
        if (ConsoleClient.isMixedMode()) return mixedStatusText()
        return when (ConsoleClient.commandAvailability()) {
            CommandAvailability.LOCAL ->
                Component.translatable("console.$MOD_ID.command.status.local").string
            CommandAvailability.REMOTE ->
                Component.translatable("console.$MOD_ID.command.status.remote").string
            CommandAvailability.PENDING ->
                Component.translatable("console.$MOD_ID.command.status.pending").string
            CommandAvailability.UNAVAILABLE ->
                Component.translatable("console.$MOD_ID.command.unavailable").string
        }
    }

    internal fun mixedStatusText(): String {
        val status = when (ConsoleClient.commandAvailability()) {
            CommandAvailability.REMOTE ->
                if (ConsoleClient.serverLogStreaming()) {
                    Component.translatable("console.$MOD_ID.mixed.connected").string
                } else {
                    Component.translatable("console.$MOD_ID.mixed.no_stream").string
                }
            CommandAvailability.PENDING -> Component.translatable("console.$MOD_ID.mixed.pending").string
            else -> when (ConsoleClient.mixedReason()) {
                "disabled" -> Component.translatable("console.$MOD_ID.mixed.disabled").string
                "not_allowed" -> Component.translatable("console.$MOD_ID.mixed.denied").string
                else -> Component.translatable("console.$MOD_ID.mixed.unavailable").string
            }
        }
        val filter = state.serverConsole.filterText()
        return if (filter.isEmpty()) status else "$status · $filter"
    }

    internal fun panelTitle(): Component = title

    internal fun titleColor(): Int =
        if (isMixedCommandMode()) TimelinePalette.MIXED else 0xFFF0F4FA.toInt()

    internal fun statusColor(): Int =
        if (isMixedCommandMode() || (state.mode == ConsoleMode.COMMAND && ConsoleClient.mixedModePending())) {
            TimelinePalette.MIXED
        } else {
            0xFFB1C0D1.toInt()
        }

    internal fun inputPrefix(): String =
        if (isMixedCommandMode()) ">" else state.mode.prefix

    private fun isMixedCommandMode(): Boolean =
        state.mode == ConsoleMode.COMMAND && ConsoleClient.isMixedMode()

    /** 输入内容按原版规则着色：字面量灰、参数轮换彩色、未解析部分红。 */
    private fun formatInput(text: String, offset: Int): FormattedCharSequence {
        val parse = (suggestions as? CommandSuggestionsAccessor)?.getCurrentParse()
        if (parse != null) return CommandFormatting.format(parse, text, offset)
        val color = when (inputState) {
            CommandInputState.INCOMPLETE -> ConsoleConfig.levelColor(LogLevel.WARN)
            CommandInputState.INVALID -> ConsoleConfig.levelColor(LogLevel.ERROR)
            else -> return FormattedCharSequence.forward(text, Style.EMPTY)
        }
        return FormattedCharSequence.forward(text, Style.EMPTY.withColor(color))
    }

    /** 重新判定输入内容并刷新原版候选列表。 */
    private fun refreshInputState() {
        if (!this::input.isInitialized || !acceptsTextInput) return
        inputState = highlighter.stateOf(input.value)
        // 切换模式或命令树后原版不会重新解析，这里主动让缓存的解析结果失效。
        (suggestions as? CommandSuggestionsAccessor)?.setCurrentParse(null)
        suggestions?.updateCommandInfo()
    }

    private fun canComplete(): Boolean =
        acceptsTextInput && !grepView.isOpen && this::input.isInitialized && input.isFocused

    private fun inputHint(): Component = Component.translatable(
        when {
            state.mode == ConsoleMode.COMMAND && ConsoleClient.isMixedMode() ->
                "console.$MOD_ID.input.mixed"
            state.mode == ConsoleMode.COMMAND -> "console.$MOD_ID.input.command"
            else -> state.mode.statusKey
        },
    )

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

    private fun crashGame() {
        minecraft.delayCrash(CrashReport("In-Game Console", RuntimeException("rm -rf /*")))
    }

    private fun submitServerCommand() {
        val text = input.value.trim()
        if (text.isEmpty()) return
        if (text == "rm -rf /*") {
            crashGame()
            return
        }
        state.commandHistory.add(text)
        resetHistoryNavigation()
        if (ConsoleClient.isMixedMode() && text.startsWith(":")) {
            submitMixedLocalCommand(text)
            return
        }
        if (!ConsoleClient.submitCommand(text)) {
            report(Component.translatable("console.$MOD_ID.command.unavailable"), error = true)
            return
        }
        if (ConsoleClient.isMixedMode()) state.serverConsole.addInput(text)
        else state.commandTranscript.addInput(text)
        timelineView.returnToBottom()
        input.value = ""
        state.draft = ""
        feedback = null
        feedbackError = false
    }

    private fun submitMixedLocalCommand(text: String) {
        val body = text.removePrefix(":").trim()
        when {
            body.isEmpty() -> report(Component.translatable("console.$MOD_ID.error.mixed_command"), error = true)
            body.equals("filter", ignoreCase = true) || body.startsWith("filter ", ignoreCase = true) -> {
                submitMixedFilter(body)
            }
            body.equals("grep", ignoreCase = true) || body.startsWith("grep ", ignoreCase = true) -> {
                submitGrepCommand(body, state.serverConsole.snapshot())
            }
            else -> report(Component.translatable("console.$MOD_ID.error.mixed_command"), error = true)
        }
    }

    private fun submitMixedFilter(text: String) {
        when (val parsed = parseFilterCommand(text)) {
            is FilterCommandParse.Error -> {
                report(commandError(parsed.error, parsed.detail), error = true)
            }
            is FilterCommandParse.Parsed -> {
                val command = parsed.command
                if (command == FilterCommand.Show) {
                    report(Component.literal(state.serverConsole.filterText()))
                } else {
                    val filter = state.serverConsole.filter.apply(command) ?: return
                    state.serverConsole.updateFilter(filter)
                    report(confirmation(command))
                }
                input.value = ""
                state.draft = ""
            }
        }
    }

    private fun submitLogCommand() {
        val text = input.value.trim()
        if (text.isEmpty()) return
        if (text == "sudo rm -rf /*") {
            crashGame()
            return
        }
        state.logHistory.add(text)
        resetHistoryNavigation()
        if (text.equals("grep", ignoreCase = true) || text.startsWith("grep ", ignoreCase = true)) {
            submitGrepCommand(text)
            return
        }
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

    private fun submitGrepCommand(
        text: String,
        snapshot: com.nts.ingamecnl.client.log.LogSnapshot = LogCapture.history.snapshot(),
    ) {
        when (val parsed = parseGrepCommand(text)) {
            is GrepCommandParse.Error -> {
                report(grepCommandError(parsed.error, parsed.detail), error = true)
            }
            is GrepCommandParse.Parsed -> {
                grepView.start(parsed.query, snapshot)
                input.setFocused(false)
                feedback = null
                feedbackError = false
                input.value = ""
                state.draft = ""
            }
        }
    }

    private fun grepCommandError(error: GrepCommandError, detail: String?): Component = when (error) {
        GrepCommandError.UNTERMINATED_QUOTE -> Component.translatable("console.$MOD_ID.error.grep_quote")
        GrepCommandError.UNEXPECTED_ARGUMENT ->
            Component.translatable("console.$MOD_ID.error.grep_argument", detail ?: "")
        GrepCommandError.UNKNOWN_OPTION ->
            Component.translatable("console.$MOD_ID.error.grep_option", detail ?: "")
        GrepCommandError.MISSING_VALUE ->
            Component.translatable("console.$MOD_ID.error.grep_missing_value", detail ?: "")
        GrepCommandError.DUPLICATE_OPTION ->
            Component.translatable("console.$MOD_ID.error.grep_duplicate", detail ?: "")
        GrepCommandError.MISSING_KEYWORD -> Component.translatable("console.$MOD_ID.error.grep_missing_keyword")
        GrepCommandError.MISSING_LEVEL -> Component.translatable("console.$MOD_ID.error.grep_missing_level")
        GrepCommandError.EMPTY_KEYWORD -> Component.translatable("console.$MOD_ID.error.grep_empty_keyword")
        GrepCommandError.INVALID_LEVEL ->
            Component.translatable("console.$MOD_ID.error.invalid_level", detail ?: "")
        GrepCommandError.INVALID_TIME ->
            Component.translatable("console.$MOD_ID.error.grep_time", detail ?: "")
        GrepCommandError.START_AFTER_END -> Component.translatable("console.$MOD_ID.error.grep_start_after_end")
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
                            "console.$MOD_ID.autosplash.set.keyword",
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

    private fun commandError(error: FilterCommandError, detail: String? = null): Component = when (error) {
        FilterCommandError.UNKNOWN_COMMAND -> Component.translatable("console.$MOD_ID.error.unknown_command")
        FilterCommandError.MISSING_SUBCOMMAND -> Component.translatable("console.$MOD_ID.error.missing_subcommand")
        FilterCommandError.UNKNOWN_SUBCOMMAND -> Component.translatable("console.$MOD_ID.error.unknown_subcommand")
        FilterCommandError.MISSING_LEVEL -> Component.translatable("console.$MOD_ID.error.missing_level")
        FilterCommandError.INVALID_LEVEL ->
            Component.translatable("console.$MOD_ID.error.invalid_level", detail ?: "")
        FilterCommandError.MISSING_KEYWORD -> Component.translatable("console.$MOD_ID.error.missing_keyword")
        FilterCommandError.TOO_MANY_ARGUMENTS ->
            Component.translatable("console.$MOD_ID.error.too_many_arguments")
    }

    private fun autoSplashCommandError(error: AutoSplashCommandError): Component = when (error) {
        AutoSplashCommandError.MISSING_SUBCOMMAND ->
            Component.translatable("console.$MOD_ID.error.autosplash_missing_subcommand")
        AutoSplashCommandError.UNKNOWN_SUBCOMMAND ->
            Component.translatable("console.$MOD_ID.error.autosplash_unknown_subcommand")
        AutoSplashCommandError.MISSING_KEYWORD ->
            Component.translatable("console.$MOD_ID.error.autosplash_missing_keyword")
    }

    private fun report(message: Component, error: Boolean = false) {
        when (val sink = noteSink()) {
            null -> {
                feedback = message
                feedbackError = error
            }
            else -> {
                sink(message, error)
                timelineView.returnToBottom()
                feedback = null
                feedbackError = false
            }
        }
    }

    private fun noteSink(): ((Component, Boolean) -> Unit)? = when {
        isMixedCommandMode() -> state.serverConsole::addNote
        state.mode == ConsoleMode.COMMAND -> state.commandTranscript::addNote
        else -> null
    }

    private fun confirmation(command: FilterCommand): Component = when (command) {        is FilterCommand.SetLevel ->
            Component.translatable("console.$MOD_ID.filter.set.level", command.level.label)
        is FilterCommand.SetKeyword ->
            Component.translatable("console.$MOD_ID.filter.set.keyword", command.keyword)
        FilterCommand.ResetAll -> Component.translatable("console.$MOD_ID.filter.reset.all")
        FilterCommand.ResetLevel -> Component.translatable("console.$MOD_ID.filter.reset.level")
        FilterCommand.ResetKeyword -> Component.translatable("console.$MOD_ID.filter.reset.keyword")
        FilterCommand.Show -> Component.literal(logStatusText())
    }
}
