package com.nts.ingamecnl.client.command

import com.nts.ingamecnl.command.CommandNetwork
import com.nts.ingamecnl.command.CommandSessionBase
import com.nts.ingamecnl.command.ServerCommandResult
import net.minecraft.core.RegistryAccess

internal class RemoteCommandSession(
    private val executeOnClient: (Runnable) -> Unit,
    private val registryAccess: () -> RegistryAccess,
    private val token: () -> String?,
) : CommandSessionBase() {
    override fun submit(command: String, onResult: (ServerCommandResult) -> Unit): Boolean {
        val id = begin(onResult)
        val sessionToken = token()
        val sent = if (sessionToken != null) {
            CommandNetwork.sendCommandRequestV2(registryAccess(), id, sessionToken, command)
        } else {
            CommandNetwork.sendCommandRequest(registryAccess(), id, command)
        }
        if (!sent) cancel(id)
        return sent
    }

    fun onResponse(requestId: Long, result: ServerCommandResult) {
        executeOnClient { finish(requestId, result) }
    }
}
