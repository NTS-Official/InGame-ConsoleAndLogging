package com.nts.ingamecnl.client

import com.mojang.blaze3d.platform.InputConstants
import com.nts.ingamecnl.MOD_ID
import com.nts.ingamecnl.client.config.ConsoleConfig
import com.nts.ingamecnl.client.gui.ConsoleMode
import com.nts.ingamecnl.client.gui.ConsoleScreen
import com.nts.ingamecnl.client.gui.ConsoleState
import com.nts.ingamecnl.client.log.LogCapture
import dev.architectury.event.events.client.ClientGuiEvent
import dev.architectury.event.events.client.ClientTickEvent
import dev.architectury.registry.client.keymappings.KeyMappingRegistry
import me.shedaniel.clothconfig2.api.ConfigScreen
import net.minecraft.client.KeyMapping
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.options.controls.KeyBindsScreen
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.client.input.MouseButtonInfo
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import org.lwjgl.glfw.GLFW

object ConsoleClient {
    private var toggleKeyMapping: KeyMapping? = null
    private var pageUpMapping: KeyMapping? = null
    private var pageDownMapping: KeyMapping? = null
    private var goToBottomMapping: KeyMapping? = null
    private val state = ConsoleState()
    private var heldKey: InputConstants.Key? = null
    private var heldMouseButton: Int? = null
    private var resumingScreen: Screen? = null
    private var viewOnlyOverlay: ConsoleScreen? = null
    private var viewOnlyActive = false
    private var viewOnlyClosing = false

    fun initialize() {
        if (toggleKeyMapping != null) return
        LogCapture.initialize()
        ConsoleConfig.initialize()
        val category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "console"))
        fun register(name: String, key: Int): KeyMapping {
            val mapping = KeyMapping(name, InputConstants.Type.KEYSYM, key, category)
            KeyMappingRegistry.register(mapping)
            return mapping
        }
        toggleKeyMapping = register("key.ingamecnl.toggle_console", GLFW.GLFW_KEY_GRAVE_ACCENT)
        pageUpMapping = register("key.ingamecnl.page_up", GLFW.GLFW_KEY_PAGE_UP)
        pageDownMapping = register("key.ingamecnl.page_down", GLFW.GLFW_KEY_PAGE_DOWN)
        goToBottomMapping = register("key.ingamecnl.go_to_bottom", GLFW.GLFW_KEY_END)
        ClientTickEvent.CLIENT_POST.register(object : ClientTickEvent.Client {
            override fun tick(client: Minecraft) = tickOverlay(client)
        })
        ClientGuiEvent.RENDER_HUD.register { graphics, delta ->
            if (viewOnlyActive && Minecraft.getInstance().screen == null) {
                viewOnlyOverlay?.extractRenderState(graphics, -1, -1, delta.gameTimeDeltaTicks)
            }
        }
        ClientGuiEvent.RENDER_POST.register { screen, graphics, _, _, delta ->
            if (viewOnlyActive && screen !is ConsoleScreen && screen !is ConfigScreen) {
                viewOnlyOverlay?.extractRenderState(graphics, -1, -1, delta)
            }
        }
    }

    fun consumeKey(window: Long, action: Int, event: KeyEvent): Boolean {
        val minecraft = Minecraft.getInstance()
        if (window != minecraft.window.handle()) return false
        val key = InputConstants.getKey(event)
        if (key == heldKey) {
            if (action == GLFW.GLFW_RELEASE) heldKey = null
            return true
        }
        if ((minecraft.screen as? KeyBindsScreen)?.selectedKey != null) return false
        if (minecraft.screen is ConfigScreen) return false
        if (viewOnlyActive) return consumeViewOnlyKey(minecraft, action, event, key)
        val mapping = toggleKeyMapping ?: return false
        if (!mapping.matches(event)) return false
        if (action == GLFW.GLFW_RELEASE) return false
        if (action == GLFW.GLFW_PRESS) {
            heldKey = key
            togglePanel(minecraft)
        }
        return true
    }

    fun consumeCharacter(window: Long): Boolean {
        if (viewOnlyActive) return false
        return toggleKeyMapping != null && window == Minecraft.getInstance().window.handle() && heldKey != null
    }

    fun consumeMouse(window: Long, buttonInfo: MouseButtonInfo, action: Int): Boolean {
        val minecraft = Minecraft.getInstance()
        if (window != minecraft.window.handle()) return false
        if (viewOnlyActive) return false
        val mapping = toggleKeyMapping ?: return false
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

    private fun consumeViewOnlyKey(
        minecraft: Minecraft,
        action: Int,
        event: KeyEvent,
        key: InputConstants.Key,
    ): Boolean {
        if (action == GLFW.GLFW_PRESS && toggleKeyMapping?.matches(event) == true) {
            heldKey = key
            hideViewOnly()
            return true
        }
        if (action == GLFW.GLFW_PRESS && event.hasShiftDown() && event.key() == GLFW.GLFW_KEY_TAB) {
            heldKey = key
            openConsoleScreen(minecraft)
            return true
        }
        if (action == GLFW.GLFW_PRESS || action == GLFW.GLFW_REPEAT) {
            val overlay = viewOnlyOverlay ?: return false
            when {
                pageUpMapping?.matches(event) == true -> overlay.scrollPage(true)
                pageDownMapping?.matches(event) == true -> overlay.scrollPage(false)
                goToBottomMapping?.matches(event) == true -> overlay.returnToBottom()
                event.key() == GLFW.GLFW_KEY_ESCAPE -> hideViewOnly()
                else -> return false
            }
            return true
        }
        return false
    }

    private fun togglePanel(minecraft: Minecraft) {
        val current = minecraft.screen
        if (current is ConsoleScreen) {
            current.togglePanel()
            return
        }
        if (viewOnlyActive) {
            if (viewOnlyClosing) {
                viewOnlyClosing = false
                viewOnlyOverlay?.reveal()
            } else {
                hideViewOnly()
            }
            return
        }
        state.mode = ConsoleMode.VIEW_ONLY
        showViewOnly(minecraft)
    }

    private fun showViewOnly(minecraft: Minecraft) {
        val width = minecraft.window.guiScaledWidth
        val height = minecraft.window.guiScaledHeight
        val overlay = viewOnlyOverlay
            ?.takeIf { it.openedLevel === minecraft.level }
            ?: ConsoleScreen(state, null, overlayOnly = true).also { viewOnlyOverlay = it }
        if (overlay.width != width || overlay.height != height) overlay.init(width, height)
        viewOnlyClosing = false
        viewOnlyActive = true
        overlay.reveal()
    }

    private fun hideViewOnly(immediate: Boolean = false) {
        val overlay = viewOnlyOverlay
        if (immediate || overlay == null) {
            viewOnlyActive = false
            viewOnlyClosing = false
            return
        }
        if (!viewOnlyActive || viewOnlyClosing) return
        viewOnlyClosing = true
        overlay.collapse()
    }

    private fun openConsoleScreen(minecraft: Minecraft) {
        hideViewOnly(immediate = true)
        state.mode = ConsoleMode.LOG
        minecraft.setScreen(ConsoleScreen(state, minecraft.screen))
    }

    internal fun enterViewOnly(previous: Screen?) {
        val minecraft = Minecraft.getInstance()
        state.mode = ConsoleMode.VIEW_ONLY
        minecraft.setScreen(previous)
        showViewOnly(minecraft)
    }

    private fun tickOverlay(client: Minecraft) {
        if (!viewOnlyActive) return
        val overlay = viewOnlyOverlay ?: return
        if (overlay.openedLevel !== client.level) {
            hideViewOnly(immediate = true)
            return
        }
        val width = client.window.guiScaledWidth
        val height = client.window.guiScaledHeight
        if (overlay.width != width || overlay.height != height) overlay.init(width, height)
        if (viewOnlyClosing && overlay.isCollapsed) {
            viewOnlyClosing = false
            viewOnlyActive = false
        }
    }

    internal fun pageUpKey(): KeyMapping? = pageUpMapping
    internal fun pageDownKey(): KeyMapping? = pageDownMapping
    internal fun goToBottomKey(): KeyMapping? = goToBottomMapping

    internal fun keyLabel(mapping: KeyMapping?): String =
        mapping?.translatedKeyMessage?.string ?: "-"

    internal fun switchModeLabel(): String = "Shift+Tab"

    internal fun overlayHint(): String = Component.translatable(
        "console.ingamecnl.overlay.hint",
        keyLabel(toggleKeyMapping),
        switchModeLabel(),
        keyLabel(pageUpMapping),
        keyLabel(pageDownMapping),
        "Esc",
    ).string

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
