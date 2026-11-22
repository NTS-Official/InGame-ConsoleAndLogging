package com.nts.ingamecnl.command

import net.minecraft.server.MinecraftServer

internal class LocalCommandSession(
    private val server: MinecraftServer,
    private val executeOnClient: (Runnable) -> Unit,
) : CommandSessionBase() {
    override fun submit(command: String, onResult: (ServerCommandResult) -> Unit): Boolean {
        val id = begin(onResult)
        return try {
            server.executeIfPossible {
                val result = ServerCommandExecutor.execute(server, command)
                executeOnClient { finish(id, result) }
            }
            true
        } catch (_: Exception) {
            cancel(id)
            false
        }
    }
}
