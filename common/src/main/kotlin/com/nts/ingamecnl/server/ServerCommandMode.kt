package com.nts.ingamecnl.server

import com.nts.ingamecnl.command.CommandNetwork
import com.nts.ingamecnl.command.CommandProtocol
import com.nts.ingamecnl.command.ServerCommandHandler
import com.nts.ingamecnl.command.ServerCommandSessions
import com.nts.ingamecnl.command.ServerLogStreamer
import com.nts.ingamecnl.command.ServerRequestGuard
import com.nts.ingamecnl.config.ServerCommandConfig
import com.nts.ingamecnl.server.log.ServerLogCapture
import dev.architectury.event.events.common.CommandRegistrationEvent
import dev.architectury.event.events.common.LifecycleEvent
import dev.architectury.event.events.common.PlayerEvent
import dev.architectury.event.events.common.TickEvent
import net.minecraft.commands.Commands
import net.minecraft.network.chat.Component
import net.minecraft.server.MinecraftServer
import net.minecraft.server.permissions.Permissions
import com.nts.ingamecnl.MOD_ID
import com.nts.ingamecnl.MOD_NAME

object ServerCommandMode {
    private var initialized = false

    fun initialize() {
        if (initialized) return
        initialized = true
        ServerCommandConfig.initialize()
        ServerLogCapture.initialize()
        CommandNetwork.register()
        TickEvent.SERVER_POST.register(TickEvent.Server { server -> ServerLogStreamer.tick(server) })
        PlayerEvent.PLAYER_QUIT.register { player ->
            ServerLogStreamer.forget(player)
            ServerCommandSessions.revoke(player)
            ServerRequestGuard.forget(player)
        }
        LifecycleEvent.SERVER_STOPPED.register(LifecycleEvent.ServerState { clear() })
        CommandRegistrationEvent.EVENT.register { dispatcher, _, _ ->
            dispatcher.register(
                Commands.literal(MOD_ID)
                    .then(
                        Commands.literal("commandmode")
                            .requires { source -> source.permissions().hasPermission(Permissions.COMMANDS_ADMIN) }
                            .then(Commands.literal("status").executes { source -> status(source.source) })
                            .then(Commands.literal("on").executes { source -> setEnabled(source.source, true) })
                            .then(Commands.literal("off").executes { source -> setEnabled(source.source, false) })
                            .then(Commands.literal("reload").executes { source -> reload(source.source) }),
                    ),
            )
        }
    }

    private fun status(source: net.minecraft.commands.CommandSourceStack): Int {
        val enabled = if (ServerCommandConfig.enabled()) "on" else "off"
        val mode = ServerCommandConfig.accessMode()
        val streaming = if (ServerCommandConfig.logStreamingEnabled()) "on" else "off"
        source.sendSuccess(
            {
                Component.literal(
                    "$MOD_NAME command mode: $enabled, access=$mode, names=${ServerCommandConfig.names().size}, " +
                        "uuids=${ServerCommandConfig.uuids().size}, ips=${ServerCommandConfig.ips().size}, " +
                        "protocol=${CommandProtocol.VERSION}, logStreaming=$streaming, " +
                        "subscribers=${ServerLogStreamer.subscriberCount()}",
                )
            },
            false,
        )
        return 1
    }

    private fun setEnabled(source: net.minecraft.commands.CommandSourceStack, value: Boolean): Int {
        ServerCommandConfig.setEnabled(value)
        resetSessions()
        resyncPlayers(source.server)
        source.sendSuccess(
            { Component.literal("$MOD_NAME command mode ${if (value) "enabled" else "disabled"}") },
            true,
        )
        return 1
    }

    private fun reload(source: net.minecraft.commands.CommandSourceStack): Int {
        val loaded = ServerCommandConfig.load()
        resetSessions()
        resyncPlayers(source.server)
        source.sendSuccess(
            { Component.literal(if (loaded) "$MOD_NAME command mode reloaded" else "$MOD_NAME command mode reload failed") },
            true,
        )
        return if (loaded) 1 else 0
    }

    private fun resetSessions() {
        ServerCommandSessions.clear()
        ServerLogStreamer.clear()
        ServerRequestGuard.clear()
    }

    private fun resyncPlayers(server: MinecraftServer) {
        server.playerList.players.forEach(ServerCommandHandler::sendCapabilityV2)
    }

    private fun clear() {
        resetSessions()
        ServerLogCapture.buffer.clear()
    }
}
