package com.nts.ingamecnl.client

import com.mojang.blaze3d.platform.InputConstants
import com.nts.ingamecnl.MOD_ID
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
import kotlin.math.roundToInt

private const val SLIDE_DURATION_NANOS = 180_000_000L

object ConsoleClient {
    private var toggleKey: KeyMapping? = null
    private val state = ConsoleState()
    private var heldKey: InputConstants.Key? = null
    private var heldMouseButton: Int? = null
    private var resumingScreen: Screen? = null

    fun initialize() {
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
            feedback = Component.translatable(
                when (state.mode) {
                    ConsoleMode.LOG -> "console.ingamecnl.submit.log_unavailable"
                    ConsoleMode.COMMAND -> "console.ingamecnl.submit.command_unavailable"
                },
            )
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
        return super.mouseClicked(event, doubleClick)
    }

    override fun mouseReleased(event: MouseButtonEvent): Boolean {
        if (transition.target == PanelTarget.COLLAPSED) return true
        return super.mouseReleased(event)
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

        val contentBottom = bottom - 62
        graphics.enableScissor(8, top + 32, (width - 8).coerceAtLeast(8), contentBottom.coerceAtLeast(top + 32))
        graphics.textWithWordWrap(
            font,
            Component.translatable("console.ingamecnl.empty"),
            8,
            top + 34,
            (width - 16).coerceAtLeast(1),
            0xFF95A5B8.toInt(),
        )
        graphics.disableScissor()

        graphics.enableScissor(8, bottom - 58, (width - 8).coerceAtLeast(8), bottom - 32)
        graphics.textWithWordWrap(
            font,
            feedback ?: Component.translatable(state.mode.statusKey),
            8,
            bottom - 56,
            (width - 16).coerceAtLeast(1),
            if (feedback == null) 0xFFB1C0D1.toInt() else 0xFFFFD27F.toInt(),
        )
        graphics.disableScissor()
        graphics.text(font, state.mode.prefix, 8, input.y, 0xFF8ED5BD.toInt())
        val hint = modeSwitchHint
        graphics.text(font, hint, width - 8 - font.width(hint), input.y, 0xFF95A5B8.toInt())
        super.extractRenderState(graphics, mouseX, mouseY, delta)
        graphics.disableScissor()
    }
}
