package com.nts.ingamecnl.command

import com.nts.ingamecnl.MOD_ID

import com.nts.ingamecnl.config.ServerCommandConfig
import net.minecraft.server.level.ServerPlayer
import org.apache.logging.log4j.LogManager

internal object ServerCommandHandler {
    private val audit = LogManager.getLogger("${MOD_ID}-audit")

    fun sendCapability(player: ServerPlayer) {
        if (ServerSilence.hides(player)) return
        CommandNetwork.sendCapability(
            player,
            player.level().server.isSingleplayer && CommandAccess.isAllowed(player),
        )
    }

    fun sendCapabilityV2(player: ServerPlayer) {
        if (ServerSilence.hides(player)) return
        val dedicated = !player.level().server.isSingleplayer
        val allowed = CommandAccess.isAllowed(player)
        val token = if (dedicated && allowed) ServerCommandSessions.issue(player) else null
        val reason = when {
            !dedicated -> "integrated"
            !ServerCommandConfig.enabled() -> "disabled"
            !allowed -> "not_allowed"
            else -> "enabled"
        }
        CommandNetwork.sendCapabilityV2(
            player,
            CommandCapability(
                enabled = allowed,
                mixed = dedicated,
                protocolVersion = CommandProtocol.VERSION,
                reason = reason,
                token = token,
                ttlSeconds = ServerCommandConfig.sessionTtlSeconds(),
                logStreaming = dedicated && allowed && ServerCommandConfig.logStreamingEnabled(),
            ),
        )
        if (dedicated && allowed) sendCommandTree(player)
    }

    /** 控制台看到的命令树与玩家自己的游戏权限无关，按服务端最高权限重建后单独下发。 */
    fun sendCommandTree(player: ServerPlayer) {
        val server = player.level().server
        CommandNetwork.sendCommandTree(player, ConsoleCommandTree.packet(server))
    }

    fun executeRequest(player: ServerPlayer, requestId: Long, command: String) {
        if (ServerSilence.hides(player)) return
        if (!player.level().server.isSingleplayer) {
            CommandNetwork.sendResult(player, requestId, ServerCommandResult(CommandResultStatus.DENIED))
            return
        }
        execute(player, requestId, command, v2 = false)
    }

    fun executeRequestV2(player: ServerPlayer, requestId: Long, token: String, command: String) {
        if (ServerSilence.hides(player)) return
        if (!ServerCommandSessions.validate(player, token)) {
            CommandNetwork.sendResultV2(player, requestId, ServerCommandResult(CommandResultStatus.DENIED))
            return
        }
        execute(player, requestId, command, v2 = true)
    }

    private fun execute(player: ServerPlayer, requestId: Long, command: String, v2: Boolean) {
        val server = player.level().server
        if (command.length !in 1..MAX_COMMAND_LENGTH || !CommandAccess.isAllowed(player)) {
            sendResult(player, requestId, ServerCommandResult(CommandResultStatus.DENIED), v2)
            return
        }
        if (v2 && !ServerRequestGuard.allow(player, requestId)) {
            sendResult(player, requestId, ServerCommandResult(CommandResultStatus.DENIED), v2 = true)
            return
        }

        try {
            server.executeIfPossible {
                val result = ServerCommandExecutor.execute(server, command)
                if (v2) logAudit(player, requestId, command, result)
                sendResult(player, requestId, result, v2)
            }
        } catch (_: Exception) {
            sendResult(player, requestId, ServerCommandResult(CommandResultStatus.UNKNOWN), v2)
        }
    }

    private fun sendResult(player: ServerPlayer, requestId: Long, result: ServerCommandResult, v2: Boolean) {
        if (v2) {
            CommandNetwork.sendResultV2(player, requestId, result)
        } else {
            CommandNetwork.sendResult(player, requestId, result)
        }
    }

    private fun logAudit(player: ServerPlayer, requestId: Long, command: String, result: ServerCommandResult) {
        if (!ServerCommandConfig.auditLog()) return
        audit.debug(
            "command player={} uuid={} ip={} id={} status={} command={}",
            player.gameProfile.name,
            player.uuid,
            player.ipAddress,
            requestId,
            result.status,
            command,
        )
    }

    private const val MAX_COMMAND_LENGTH = 2048
}
