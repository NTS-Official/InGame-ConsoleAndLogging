package com.nts.ingamecnl.client

import com.mojang.blaze3d.platform.InputConstants
import com.nts.ingamecnl.MOD_ID
import com.nts.ingamecnl.client.command.CommandAvailability
import com.nts.ingamecnl.client.command.CommandTrees
import com.nts.ingamecnl.client.command.RemoteCommandAccess
import com.nts.ingamecnl.client.command.RemoteCommandCapability
import com.nts.ingamecnl.client.command.RemoteCommandSession
import com.nts.ingamecnl.client.config.ConfigFilterSync
import com.nts.ingamecnl.client.gui.ConsoleMode
import com.nts.ingamecnl.client.gui.ConsoleScreen
import com.nts.ingamecnl.client.gui.ConsoleState
import com.nts.ingamecnl.client.log.LogCapture
import com.nts.ingamecnl.log.LogRecord
import com.nts.ingamecnl.command.CommandNetwork
import com.nts.ingamecnl.command.CommandResultStatus
import com.nts.ingamecnl.command.CommandSession
import com.nts.ingamecnl.command.LocalCommandSession
import com.nts.ingamecnl.config.ConsoleConfig
import dev.architectury.event.events.client.ClientGuiEvent
import dev.architectury.event.events.client.ClientPlayerEvent
import dev.architectury.event.events.client.ClientTickEvent
import dev.architectury.event.events.common.LifecycleEvent
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
import net.minecraft.server.MinecraftServer
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
    private var autoSplashOverlay: ConsoleScreen? = null
    private var autoSplashActive = false
    private var autoSplashClosing = false
    private var autoSplashDeadlineNanos = 0L
    private var autoSplashSilencedUntilNanos = 0L
    private var commandSession: CommandSession? = null
    private var commandServer: MinecraftServer? = null
    private var serverLogSubscribed = false

    fun initialize() {
        if (toggleKeyMapping != null) return
        LogCapture.initialize()
        ConsoleConfig.initialize()
        ConfigFilterSync.initialize()
        LogCapture.onVisibleRecord = ::onVisibleRecord
        CommandNetwork.register()
        CommandNetwork.onCapabilityResponse = { enabled -> RemoteCommandCapability.onResponse(enabled) }
        CommandNetwork.onCommandResponse = { requestId, result ->
            (commandSession as? RemoteCommandSession)?.onResponse(requestId, result)
        }
        CommandNetwork.onCapabilityV2 = { capability ->
            serverLogSubscribed = false
            RemoteCommandCapability.onResponse(capability)
            syncServerLogSubscription(Minecraft.getInstance())
        }
        CommandNetwork.onCommandResponseV2 = { requestId, result ->
            (commandSession as? RemoteCommandSession)?.onResponse(requestId, result)
        }
        CommandNetwork.onServerLogReset = {
            state.serverConsole.clearLogs()
        }
        CommandNetwork.onServerLogBatch = { batch ->
            state.serverConsole.onBatch(batch.reset, batch.dropped, batch.records)
        }
        CommandNetwork.onCommandTree = { packet ->
            val connection = Minecraft.getInstance().connection
            if (connection != null) {
                CommandTrees.onServerTree(
                    packet,
                    connection.registryAccess(),
                    connection.enabledFeatures(),
                )
            }
        }
        RemoteCommandCapability.onChanged = {
            syncServerLogSubscription(Minecraft.getInstance())
        }
        ClientPlayerEvent.CLIENT_PLAYER_JOIN.register { _ -> connectCommandSession() }
        ClientPlayerEvent.CLIENT_PLAYER_QUIT.register { _ -> resetCommandSession() }
        LifecycleEvent.SERVER_STOPPED.register { _ -> resetCommandSession() }
        val category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "console"))
        fun register(name: String, key: Int): KeyMapping {
            val mapping = KeyMapping(name, InputConstants.Type.KEYSYM, key, category)
            KeyMappingRegistry.register(mapping)
            return mapping
        }
        toggleKeyMapping = register("key.$MOD_ID.toggle_console", GLFW.GLFW_KEY_GRAVE_ACCENT)
        pageUpMapping = register("key.$MOD_ID.page_up", GLFW.GLFW_KEY_PAGE_UP)
        pageDownMapping = register("key.$MOD_ID.page_down", GLFW.GLFW_KEY_PAGE_DOWN)
        goToBottomMapping = register("key.$MOD_ID.go_to_bottom", GLFW.GLFW_KEY_END)
        ClientTickEvent.CLIENT_POST.register(object : ClientTickEvent.Client {
            override fun tick(client: Minecraft) = tickOverlay(client)
        })
        ClientGuiEvent.RENDER_HUD.register { graphics, delta ->
            val minecraft = Minecraft.getInstance()
            if (autoSplashActive && minecraft.screen == null) {
                autoSplashOverlay?.extractRenderState(graphics, -1, -1, delta.gameTimeDeltaTicks)
            } else if (viewOnlyActive && minecraft.screen == null) {
                viewOnlyOverlay?.extractRenderState(graphics, -1, -1, delta.gameTimeDeltaTicks)
            }
        }
        ClientGuiEvent.RENDER_POST.register { screen, graphics, _, _, delta ->
            when {
                autoSplashActive && screen !is ConsoleScreen && screen !is ConfigScreen ->
                    autoSplashOverlay?.extractRenderState(graphics, -1, -1, delta)
                viewOnlyActive && screen !is ConsoleScreen && screen !is ConfigScreen ->
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
        if (autoSplashActive) {
            if (ConsoleConfig.autoSplashEscapeCloses() &&
                action == GLFW.GLFW_PRESS && event.key() == GLFW.GLFW_KEY_ESCAPE
            ) {
                silenceAutoSplash()
                hideAutoSplash()
                return true
            }
            return false
        }
        val current = minecraft.screen
        if (current is ConsoleScreen && current.acceptsTextInput &&
            toggleKeyMapping?.matches(event) == true
        ) return false
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
        if (viewOnlyActive || autoSplashActive) return false
        return toggleKeyMapping != null && window == Minecraft.getInstance().window.handle() && heldKey != null
    }

    fun consumeMouse(window: Long, buttonInfo: MouseButtonInfo, action: Int): Boolean {
        val minecraft = Minecraft.getInstance()
        if (window != minecraft.window.handle()) return false
        if (viewOnlyActive || autoSplashActive) return false
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
        hideAutoSplash(immediate = true)
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
        if (state.mode == ConsoleMode.VIEW_ONLY) showViewOnly(minecraft)
        else openConsoleScreen(minecraft, state.mode)
    }

    private fun showViewOnly(minecraft: Minecraft) {
        hideAutoSplash(immediate = true)
        val width = minecraft.window.guiScaledWidth
        val height = minecraft.window.guiScaledHeight
        val created = viewOnlyOverlay == null
        val overlay = viewOnlyOverlay
            ?: ConsoleScreen(state, null, overlayOnly = true).also { viewOnlyOverlay = it }
        if (created) {
            overlay.init(width, height)
            overlay.resetTransition()
        } else if (overlay.width != width || overlay.height != height) {
            overlay.init(width, height)
        }
        viewOnlyClosing = false
        viewOnlyActive = true
        overlay.reveal()
    }

    private fun hideViewOnly(immediate: Boolean = false) {
        val overlay = viewOnlyOverlay
        if (immediate || overlay == null) {
            viewOnlyActive = false
            viewOnlyClosing = false
            overlay?.resetTransition()
            return
        }
        if (!viewOnlyActive || viewOnlyClosing) return
        viewOnlyClosing = true
        overlay.collapse()
    }

    private fun openConsoleScreen(minecraft: Minecraft, mode: ConsoleMode = ConsoleMode.LOG) {
        hideAutoSplash(immediate = true)
        val fromViewOnly = viewOnlyActive
        hideViewOnly(immediate = true)
        state.mode = mode
        minecraft.setScreen(ConsoleScreen(state, minecraft.screen, initiallyExpanded = fromViewOnly))
    }

    internal fun enterViewOnly(previous: Screen?) {
        val minecraft = Minecraft.getInstance()
        hideAutoSplash(immediate = true)
        state.mode = ConsoleMode.VIEW_ONLY
        minecraft.setScreen(previous)
        showViewOnly(minecraft)
    }

    private fun tickOverlay(client: Minecraft) {
        if (client.level != null && client.singleplayerServer == null &&
            RemoteCommandCapability.access == RemoteCommandAccess.UNKNOWN
        ) {
            RemoteCommandCapability.request(client)
        }
        val now = System.nanoTime()
        RemoteCommandCapability.tick(now)
        RemoteCommandCapability.refreshIfExpired(client)
        commandSession?.tick(now)
        syncServerLogSubscription(client)
        tickAutoSplash(client)
        if (!viewOnlyActive) return
        val overlay = viewOnlyOverlay ?: return
        val width = client.window.guiScaledWidth
        val height = client.window.guiScaledHeight
        if (overlay.width != width || overlay.height != height) overlay.init(width, height)
        if (viewOnlyClosing && overlay.isCollapsed) {
            viewOnlyClosing = false
            viewOnlyActive = false
        }
    }

    private fun connectCommandSession() {
        resetCommandSession()
        val minecraft = Minecraft.getInstance()
        val server = minecraft.singleplayerServer
        if (server != null && server.isRunning && !server.isStopped && server.isReady) {
            commandServer = server
            commandSession = LocalCommandSession(server) { minecraft.execute(it) }
            return
        }
        RemoteCommandCapability.request(minecraft)
        commandSession = RemoteCommandSession(
            executeOnClient = { minecraft.execute(it) },
            registryAccess = { RemoteCommandCapability.registryAccess(minecraft) },
            token = { RemoteCommandCapability.token },
        )
    }

    private fun resetCommandSession() {
        unsubscribeServerLog()
        commandSession?.close()
        commandSession = null
        commandServer = null
        RemoteCommandCapability.reset()
        CommandTrees.clearServer()
        state.commandTranscript.clear()
        state.serverConsole.clearAll()
    }

    internal fun commandAvailability(): CommandAvailability {
        val minecraft = Minecraft.getInstance()
        if (minecraft.singleplayerServer != null) {
            val server = commandServer
            return if (server != null && server.isRunning && !server.isStopped && server.isReady) {
                CommandAvailability.LOCAL
            } else {
                CommandAvailability.UNAVAILABLE
            }
        }
        return when (RemoteCommandCapability.access) {
            RemoteCommandAccess.ENABLED -> CommandAvailability.REMOTE
            RemoteCommandAccess.PENDING -> CommandAvailability.PENDING
            RemoteCommandAccess.UNKNOWN, RemoteCommandAccess.DISABLED -> CommandAvailability.UNAVAILABLE
        }
    }

    internal fun submitCommand(command: String): Boolean {
        if (commandAvailability() == CommandAvailability.UNAVAILABLE) return false
        val session = commandSession ?: run {
            connectCommandSession()
            commandSession
        } ?: return false
        return session.submit(command) { result ->
            Minecraft.getInstance().execute {
                if (result.status == CommandResultStatus.DENIED) {
                    RemoteCommandCapability.refresh(Minecraft.getInstance())
                }
                if (RemoteCommandCapability.mixedMode) {
                    state.serverConsole.addResult(result)
                } else {
                    state.commandTranscript.addResult(result)
                }
            }
        }
    }

    internal fun isMixedMode(): Boolean = RemoteCommandCapability.mixedMode

    internal fun mixedModePending(): Boolean =
        RemoteCommandCapability.access == RemoteCommandAccess.PENDING

    internal fun serverLogStreaming(): Boolean =
        RemoteCommandCapability.logStreaming && RemoteCommandCapability.token != null

    internal fun mixedReason(): String = RemoteCommandCapability.reason

    private fun syncServerLogSubscription(minecraft: Minecraft) {
        if (!RemoteCommandCapability.mixedMode || !RemoteCommandCapability.logStreaming) {
            unsubscribeServerLog()
            return
        }
        val screen = minecraft.screen
        if (screen !is ConsoleScreen || state.mode != ConsoleMode.COMMAND) {
            unsubscribeServerLog()
            return
        }
        if (serverLogSubscribed) {
            if (!state.serverConsole.needsResync) return
            val token = RemoteCommandCapability.token ?: return
            val registryAccess = minecraft.level?.registryAccess() ?: return
            state.serverConsole.clearResync()
            CommandNetwork.sendLogSubscribe(registryAccess, token, -1L)
            return
        }
        val token = RemoteCommandCapability.token ?: return
        val registryAccess = minecraft.level?.registryAccess() ?: return
        state.serverConsole.clearResync()
        if (CommandNetwork.sendLogSubscribe(registryAccess, token, state.serverConsole.lastSequence)) {
            serverLogSubscribed = true
        }
    }

    private fun unsubscribeServerLog() {
        if (!serverLogSubscribed) return
        serverLogSubscribed = false
        val minecraft = Minecraft.getInstance()
        val registryAccess = minecraft.level?.registryAccess() ?: return
        CommandNetwork.sendLogUnsubscribe(registryAccess)
    }

    private fun onVisibleRecord(record: LogRecord) {
        val minecraft = Minecraft.getInstance()
        if (!ConsoleConfig.autoSplashEnabled() || minecraft.level == null) return
        if (isManualPanelOpen(minecraft)) {
            hideAutoSplash(immediate = true)
            return
        }
        if (ConsoleConfig.autoSplashSilenceNanos() > 0L &&
            System.nanoTime() < autoSplashSilencedUntilNanos
        ) return
        val triggerLevel = ConsoleConfig.autoSplashLevel()
        if (triggerLevel != null && record.level.ordinal < triggerLevel.ordinal) return
        val keyword = ConsoleConfig.autoSplashKeyword()
        if (keyword.isNotEmpty() && !record.contains(keyword)) return
        showAutoSplash(minecraft)
    }

    private fun isManualPanelOpen(minecraft: Minecraft): Boolean =
        viewOnlyActive || minecraft.screen is ConsoleScreen || minecraft.screen is ConfigScreen

    private fun showAutoSplash(minecraft: Minecraft) {
        val width = minecraft.window.guiScaledWidth
        val height = minecraft.window.guiScaledHeight
        val overlay = autoSplashOverlay
            ?: ConsoleScreen(state, null, overlayOnly = true, autoSplash = true).also {
                autoSplashOverlay = it
            }
        if (overlay.width != width || overlay.height != height) overlay.init(width, height)
        autoSplashClosing = false
        autoSplashActive = true
        autoSplashDeadlineNanos = System.nanoTime() + ConsoleConfig.autoSplashStayNanos()
        overlay.timelineView.returnToBottom()
        overlay.reveal()
    }

    private fun hideAutoSplash(immediate: Boolean = false) {
        val overlay = autoSplashOverlay
        if (immediate || overlay == null) {
            autoSplashActive = false
            autoSplashClosing = false
            overlay?.resetTransition()
            return
        }
        if (!autoSplashActive || autoSplashClosing) return
        autoSplashClosing = true
        overlay.collapse()
    }

    private fun silenceAutoSplash() {
        val nanos = ConsoleConfig.autoSplashSilenceNanos()
        autoSplashSilencedUntilNanos = if (nanos > 0L) System.nanoTime() + nanos else 0L
    }

    private fun tickAutoSplash(client: Minecraft) {
        if (!autoSplashActive) return
        val overlay = autoSplashOverlay ?: return
        if (isManualPanelOpen(client)) {
            hideAutoSplash(immediate = true)
            return
        }
        val width = client.window.guiScaledWidth
        val height = client.window.guiScaledHeight
        if (overlay.width != width || overlay.height != height) overlay.init(width, height)
        if (!autoSplashClosing && System.nanoTime() >= autoSplashDeadlineNanos) {
            hideAutoSplash()
        }
        if (autoSplashClosing && overlay.isCollapsed) {
            autoSplashClosing = false
            autoSplashActive = false
        }
    }

    private fun LogRecord.contains(keyword: String): Boolean =
        message.contains(keyword, ignoreCase = true) ||
            throwableText?.contains(keyword, ignoreCase = true) == true

    internal fun pageUpKey(): KeyMapping? = pageUpMapping
    internal fun pageDownKey(): KeyMapping? = pageDownMapping
    internal fun goToBottomKey(): KeyMapping? = goToBottomMapping

    internal fun keyLabel(mapping: KeyMapping?): String =
        mapping?.translatedKeyMessage?.string ?: "-"

    internal fun switchModeLabel(): String = "Shift+Tab"

    internal fun overlayHint(): String = Component.translatable(
        "console.$MOD_ID.overlay.hint",
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
