package com.nts.ingamecnl.command

import net.minecraft.server.level.ServerPlayer

internal object ServerCommandHandler {
    fun sendCapability(player: ServerPlayer) {
        CommandNetwork.sendCapability(player, CommandAccess.isAllowed(player))
    }

    fun executeRequest(player: ServerPlayer, requestId: Long, command: String) {
        val server = player.level().server
        if (command.length !in 1..MAX_COMMAND_LENGTH || !CommandAccess.isAllowed(player)) {
            CommandNetwork.sendResult(player, requestId, ServerCommandResult(CommandResultStatus.DENIED))
            return
        }

        try {
            server.executeIfPossible {
                val result = ServerCommandExecutor.execute(server, command)
                CommandNetwork.sendResult(player, requestId, result)
            }
        } catch (_: Exception) {
            CommandNetwork.sendResult(player, requestId, ServerCommandResult(CommandResultStatus.UNKNOWN))
        }
    }

    private const val MAX_COMMAND_LENGTH = 2048
}
