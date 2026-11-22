package com.nts.ingamecnl.command

import com.nts.ingamecnl.MOD_ID
import dev.architectury.networking.NetworkManager
import io.netty.buffer.Unpooled
import net.minecraft.core.RegistryAccess
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.chat.ComponentSerialization
import net.minecraft.resources.Identifier
import net.minecraft.server.level.ServerPlayer

internal object CommandNetwork {
    private val CAPABILITY_REQUEST = id("command_capability_request")
    private val CAPABILITY_RESPONSE = id("command_capability")
    private val COMMAND_REQUEST = id("command_request")
    private val COMMAND_RESPONSE = id("command_response")

    internal var onCapabilityResponse: (Boolean) -> Unit = {}
    internal var onCommandResponse: (Long, ServerCommandResult) -> Unit = { _, _ -> }

    fun register() {
        NetworkManager.registerReceiver(
            NetworkManager.Side.C2S,
            CAPABILITY_REQUEST,
            NetworkManager.NetworkReceiver<RegistryFriendlyByteBuf> { _, context ->
                val player = context.player as? ServerPlayer
                if (player != null) context.queue { ServerCommandHandler.sendCapability(player) }
            },
        )
        NetworkManager.registerReceiver(
            NetworkManager.Side.C2S,
            COMMAND_REQUEST,
            NetworkManager.NetworkReceiver<RegistryFriendlyByteBuf> { buffer, context ->
                val player = context.player as? ServerPlayer
                if (player != null) {
                    val requestId = buffer.readLong()
                    val command = buffer.readUtf(MAX_COMMAND_LENGTH)
                    context.queue { ServerCommandHandler.executeRequest(player, requestId, command) }
                }
            },
        )
        NetworkManager.registerReceiver(
            NetworkManager.Side.S2C,
            CAPABILITY_RESPONSE,
            NetworkManager.NetworkReceiver<RegistryFriendlyByteBuf> { buffer, context ->
                val enabled = buffer.readBoolean()
                context.queue { onCapabilityResponse(enabled) }
            },
        )
        NetworkManager.registerReceiver(
            NetworkManager.Side.S2C,
            COMMAND_RESPONSE,
            NetworkManager.NetworkReceiver<RegistryFriendlyByteBuf> { buffer, context ->
                val requestId = buffer.readLong()
                val result = readResult(buffer)
                context.queue { onCommandResponse(requestId, result) }
            },
        )
    }

    fun canSendCapabilityRequest(): Boolean =
        NetworkManager.canServerReceive(CAPABILITY_REQUEST)

    fun sendCapabilityRequest(registryAccess: RegistryAccess) {
        NetworkManager.sendToServer(CAPABILITY_REQUEST, buffer(registryAccess))
    }

    fun sendCommandRequest(registryAccess: RegistryAccess, requestId: Long, command: String): Boolean {
        if (!NetworkManager.canServerReceive(COMMAND_REQUEST)) return false
        val buffer = buffer(registryAccess)
        buffer.writeLong(requestId)
        buffer.writeUtf(command, MAX_COMMAND_LENGTH)
        NetworkManager.sendToServer(COMMAND_REQUEST, buffer)
        return true
    }

    fun sendCapability(player: ServerPlayer, enabled: Boolean) {
        val buffer = buffer(player.level().server.registryAccess())
        buffer.writeBoolean(enabled)
        NetworkManager.sendToPlayer(player, CAPABILITY_RESPONSE, buffer)
    }

    fun sendResult(player: ServerPlayer, requestId: Long, result: ServerCommandResult) {
        val buffer = buffer(player.level().server.registryAccess())
        buffer.writeLong(requestId)
        buffer.writeVarInt(result.status.ordinal)
        buffer.writeVarInt(result.value)
        buffer.writeVarInt(result.feedback.size)
        for (message in result.feedback) {
            ComponentSerialization.STREAM_CODEC.encode(buffer, message)
        }
        NetworkManager.sendToPlayer(player, COMMAND_RESPONSE, buffer)
    }

    private fun readResult(buffer: RegistryFriendlyByteBuf): ServerCommandResult {
        val status = CommandResultStatus.entries[buffer.readVarInt().coerceIn(0, CommandResultStatus.entries.lastIndex)]
        val value = buffer.readVarInt()
        val count = buffer.readVarInt().coerceIn(0, MAX_FEEDBACK)
        val feedback = List(count) { ComponentSerialization.STREAM_CODEC.decode(buffer) }
        return ServerCommandResult(status, value, feedback)
    }

    private fun buffer(registryAccess: RegistryAccess): RegistryFriendlyByteBuf =
        RegistryFriendlyByteBuf(Unpooled.buffer(), registryAccess)

    private fun id(path: String): Identifier = Identifier.fromNamespaceAndPath(MOD_ID, path)

    private const val MAX_COMMAND_LENGTH = 2048
    private const val MAX_FEEDBACK = 64
}
