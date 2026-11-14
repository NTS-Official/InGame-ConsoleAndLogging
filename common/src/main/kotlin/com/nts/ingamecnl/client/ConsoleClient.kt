package com.nts.ingamecnl.client

import com.mojang.blaze3d.platform.InputConstants
import com.nts.ingamecnl.MOD_ID
import dev.architectury.event.events.client.ClientTickEvent
import dev.architectury.registry.client.keymappings.KeyMappingRegistry
import net.minecraft.client.KeyMapping
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.components.Tooltip
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import org.lwjgl.glfw.GLFW

object ConsoleClient {
    fun initialize() {
        val toggleKey = KeyMapping(
            "key.ingamecnl.toggle_console",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_GRAVE_ACCENT,
            KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "console")),
        )
        val state = ConsoleState()
        KeyMappingRegistry.register(toggleKey)
        ClientTickEvent.CLIENT_POST.register { minecraft ->
            var requested = false
            while (toggleKey.consumeClick()) requested = true
            if (requested && minecraft.screen == null && minecraft.level != null) {
                minecraft.setScreen(ConsoleScreen(state, toggleKey))
            }
        }
    }
}

private enum class ConsoleMode(val prefix: String, val statusKey: String) {
    LOG("$", "console.ingamecnl.status.log"),
    COMMAND("#", "console.ingamecnl.status.command"),
}

private enum class ToggleGesture {
    OPENING_HELD,
    READY,
    CLOSING_HELD,
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
    private val toggleKey: KeyMapping,
) : Screen(Component.translatable("console.ingamecnl.title")) {
    private val openedLevel = minecraft.level
    private var toggleGesture = if (toggleKey.isDown) ToggleGesture.OPENING_HELD else ToggleGesture.READY
    private lateinit var input: EditBox
    private lateinit var logButton: Button
    private lateinit var commandButton: Button
    private lateinit var closeButton: Button
    private var feedback: Component? = null
    private var displayedCommandAvailability = false
    private val panelHeight: Int
        get() = (height * 0.65).toInt().coerceAtLeast(144).coerceAtMost(height)
    private val commandAvailable: Boolean
        get() = minecraft.level === openedLevel && minecraft.level != null &&
            minecraft.connection != null && minecraft.singleplayerServer?.let {
                it.isRunning && !it.isStopped && it.isReady
            } == true

    override fun init() {
        if (state.mode == ConsoleMode.COMMAND && !commandAvailable) state.mode = ConsoleMode.LOG
        logButton = addRenderableWidget(
            Button.builder(Component.translatable("console.ingamecnl.mode.log")) {
                selectMode(ConsoleMode.LOG)
            }.build(),
        )
        commandButton = addRenderableWidget(
            Button.builder(Component.translatable("console.ingamecnl.mode.command")) {
                selectMode(ConsoleMode.COMMAND)
            }.build(),
        )
        closeButton = addRenderableWidget(
            Button.builder(Component.literal("×")) { onClose() }
                .tooltip(Tooltip.create(Component.translatable("console.ingamecnl.close")))
                .build(),
        )
        input = addRenderableWidget(EditBox(font, 1, 20, Component.translatable("console.ingamecnl.input")))
        input.setMaxLength(32767)
        input.value = state.draft
        input.setResponder { state.draft = it }
        refreshModeButtons()
        repositionElements()
    }

    override fun setInitialFocus() {
        setInitialFocus(input)
    }

    override fun repositionElements() {
        val padding = 8
        val contentWidth = (width - padding * 2).coerceAtLeast(1)
        val buttonWidth = ((contentWidth - 4) / 2).coerceAtLeast(1)
        logButton.setRectangle(buttonWidth, 20, padding, 30)
        commandButton.setRectangle(buttonWidth, 20, padding + buttonWidth + 4, 30)
        closeButton.setRectangle(20, 20, (width - 28).coerceAtLeast(0), 6)
        input.setRectangle((width - 28).coerceAtLeast(1), 20, 20, panelHeight - 28)
    }

    private fun selectMode(mode: ConsoleMode) {
        if (mode == ConsoleMode.COMMAND && !commandAvailable) return
        state.mode = mode
        input.value = state.draft
        feedback = null
        refreshModeButtons()
        setInitialFocus(input)
    }

    private fun refreshModeButtons() {
        val available = commandAvailable
        displayedCommandAvailability = available
        logButton.active = state.mode != ConsoleMode.LOG
        commandButton.active = available && state.mode != ConsoleMode.COMMAND
        commandButton.setTooltip(
            Tooltip.create(
                Component.translatable(
                    if (available) "console.ingamecnl.command.available"
                    else "console.ingamecnl.command.unavailable",
                ),
            ),
        )
        input.setHint(Component.translatable(state.mode.statusKey))
    }

    override fun tick() {
        if (minecraft.level !== openedLevel) {
            onClose()
            return
        }
        val available = commandAvailable
        if (state.mode == ConsoleMode.COMMAND && !available) {
            selectMode(ConsoleMode.LOG)
        } else if (available != displayedCommandAvailability) {
            refreshModeButtons()
        }
    }

    override fun keyPressed(event: KeyEvent): Boolean {
        if (toggleKey.matches(event)) {
            if (toggleGesture == ToggleGesture.READY) toggleGesture = ToggleGesture.CLOSING_HELD
            return true
        }
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
        if (toggleKey.matches(event)) {
            when (toggleGesture) {
                ToggleGesture.OPENING_HELD -> toggleGesture = ToggleGesture.READY
                ToggleGesture.READY -> Unit
                ToggleGesture.CLOSING_HELD -> onClose()
            }
            return true
        }
        return super.keyReleased(event)
    }

    override fun charTyped(event: CharacterEvent): Boolean =
        toggleGesture != ToggleGesture.READY || super.charTyped(event)

    override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
        if (toggleKey.matchesMouse(event)) {
            onClose()
            return true
        }
        return super.mouseClicked(event, doubleClick)
    }

    override fun mouseReleased(event: MouseButtonEvent): Boolean {
        if (toggleKey.matchesMouse(event)) {
            if (toggleGesture == ToggleGesture.OPENING_HELD) toggleGesture = ToggleGesture.READY
            return true
        }
        return super.mouseReleased(event)
    }

    override fun onClose() {
        if (minecraft.screen === this) minecraft.setScreen(null)
    }

    override fun isPauseScreen() = false

    override fun extractBackground(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) = Unit

    override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        graphics.fill(0, 0, width, panelHeight, 0xDF10151C.toInt())
        graphics.fill(0, panelHeight - 1, width, panelHeight, 0xFF526176.toInt())
        graphics.enableScissor(8, 6, (width - 32).coerceAtLeast(8), 26)
        graphics.text(font, title, 8, 12, 0xFFF0F4FA.toInt())
        graphics.disableScissor()

        val contentBottom = panelHeight - 62
        graphics.enableScissor(8, 56, (width - 8).coerceAtLeast(8), contentBottom.coerceAtLeast(56))
        graphics.textWithWordWrap(
            font,
            Component.translatable("console.ingamecnl.empty"),
            8,
            58,
            (width - 16).coerceAtLeast(1),
            0xFF95A5B8.toInt(),
        )
        graphics.disableScissor()

        graphics.enableScissor(8, panelHeight - 58, (width - 8).coerceAtLeast(8), panelHeight - 32)
        graphics.textWithWordWrap(
            font,
            feedback ?: Component.translatable(state.mode.statusKey),
            8,
            panelHeight - 56,
            (width - 16).coerceAtLeast(1),
            if (feedback == null) 0xFFB1C0D1.toInt() else 0xFFFFD27F.toInt(),
        )
        graphics.disableScissor()
        graphics.text(font, state.mode.prefix, 8, panelHeight - 22, 0xFF8ED5BD.toInt())
        super.extractRenderState(graphics, mouseX, mouseY, delta)
    }
}
