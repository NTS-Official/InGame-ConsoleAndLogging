package com.nts.ingamecnl.command

import com.nts.ingamecnl.MOD_ID
import com.nts.ingamecnl.log.LogLevel
import com.nts.ingamecnl.log.LogRecord
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

    private val CAPABILITY_V2_REQUEST = id("command_capability_v2_request")
    private val CAPABILITY_V2_RESPONSE = id("command_capability_v2")
    private val COMMAND_V2_REQUEST = id("command_request_v2")
    private val COMMAND_V2_RESPONSE = id("command_response_v2")
    private val LOG_SUBSCRIBE = id("server_log_subscribe_v2")
    private val LOG_UNSUBSCRIBE = id("server_log_unsubscribe_v2")
    private val LOG_BATCH = id("server_log_batch_v2")

    internal var onCapabilityResponse: (Boolean) -> Unit = {}
    internal var onCommandResponse: (Long, ServerCommandResult) -> Unit = { _, _ -> }
    internal var onCapabilityV2: (CommandCapability) -> Unit = {}
    internal var onCommandResponseV2: (Long, ServerCommandResult) -> Unit = { _, _ -> }
    internal var onServerLogBatch: (ServerLogBatch) -> Unit = {}
    internal var onServerLogReset: () -> Unit = {}

    private var registered = false

    fun register() {
        if (registered) return
        registered = true
        registerV1()
        registerV2()
    }

    private fun registerV1() {
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

    private fun registerV2() {
        NetworkManager.registerReceiver(
            NetworkManager.Side.C2S,
            CAPABILITY_V2_REQUEST,
            NetworkManager.NetworkReceiver<RegistryFriendlyByteBuf> { _, context ->
                val player = context.player as? ServerPlayer
                if (player != null) context.queue { ServerCommandHandler.sendCapabilityV2(player) }
            },
        )
        NetworkManager.registerReceiver(
            NetworkManager.Side.C2S,
            COMMAND_V2_REQUEST,
            NetworkManager.NetworkReceiver<RegistryFriendlyByteBuf> { buffer, context ->
                val player = context.player as? ServerPlayer
                if (player != null) {
                    val requestId = buffer.readLong()
                    val token = buffer.readUtf(MAX_TOKEN_LENGTH)
                    val command = buffer.readUtf(MAX_COMMAND_LENGTH)
                    context.queue { ServerCommandHandler.executeRequestV2(player, requestId, token, command) }
                }
            },
        )
        NetworkManager.registerReceiver(
            NetworkManager.Side.C2S,
            LOG_SUBSCRIBE,
            NetworkManager.NetworkReceiver<RegistryFriendlyByteBuf> { buffer, context ->
                val player = context.player as? ServerPlayer
                if (player != null) {
                    val token = buffer.readUtf(MAX_TOKEN_LENGTH)
                    val sinceSequence = buffer.readLong()
                    context.queue { ServerLogStreamer.subscribe(player, token, sinceSequence) }
                }
            },
        )
        NetworkManager.registerReceiver(
            NetworkManager.Side.C2S,
            LOG_UNSUBSCRIBE,
            NetworkManager.NetworkReceiver<RegistryFriendlyByteBuf> { _, context ->
                val player = context.player as? ServerPlayer
                if (player != null) context.queue { ServerLogStreamer.unsubscribe(player) }
            },
        )
        NetworkManager.registerReceiver(
            NetworkManager.Side.S2C,
            CAPABILITY_V2_RESPONSE,
            NetworkManager.NetworkReceiver<RegistryFriendlyByteBuf> { buffer, context ->
                val capability = readCapability(buffer)
                context.queue { onCapabilityV2(capability) }
            },
        )
        NetworkManager.registerReceiver(
            NetworkManager.Side.S2C,
            COMMAND_V2_RESPONSE,
            NetworkManager.NetworkReceiver<RegistryFriendlyByteBuf> { buffer, context ->
                val requestId = buffer.readLong()
                val result = readResult(buffer)
                context.queue { onCommandResponseV2(requestId, result) }
            },
        )
        NetworkManager.registerReceiver(
            NetworkManager.Side.S2C,
            LOG_BATCH,
            NetworkManager.NetworkReceiver<RegistryFriendlyByteBuf> { buffer, context ->
                val batch = readBatch(buffer)
                context.queue {
                    if (batch.reset) onServerLogReset()
                    if (batch.records.isNotEmpty()) onServerLogBatch(batch)
                }
            },
        )
    }

    fun canSendCapabilityRequest(): Boolean =
        NetworkManager.canServerReceive(CAPABILITY_V2_REQUEST) ||
            NetworkManager.canServerReceive(CAPABILITY_REQUEST)

    fun canUseV2(): Boolean = NetworkManager.canServerReceive(CAPABILITY_V2_REQUEST)

    fun sendCapabilityRequest(registryAccess: RegistryAccess) {
        NetworkManager.sendToServer(CAPABILITY_REQUEST, buffer(registryAccess))
    }

    fun sendCapabilityRequestV2(registryAccess: RegistryAccess) {
        NetworkManager.sendToServer(CAPABILITY_V2_REQUEST, buffer(registryAccess))
    }

    fun sendCommandRequest(registryAccess: RegistryAccess, requestId: Long, command: String): Boolean {
        if (!NetworkManager.canServerReceive(COMMAND_REQUEST)) return false
        val buffer = buffer(registryAccess)
        buffer.writeLong(requestId)
        buffer.writeUtf(command, MAX_COMMAND_LENGTH)
        NetworkManager.sendToServer(COMMAND_REQUEST, buffer)
        return true
    }

    fun sendCommandRequestV2(registryAccess: RegistryAccess, requestId: Long, token: String, command: String): Boolean {
        if (!NetworkManager.canServerReceive(COMMAND_V2_REQUEST)) return false
        val buffer = buffer(registryAccess)
        buffer.writeLong(requestId)
        buffer.writeUtf(token, MAX_TOKEN_LENGTH)
        buffer.writeUtf(command, MAX_COMMAND_LENGTH)
        NetworkManager.sendToServer(COMMAND_V2_REQUEST, buffer)
        return true
    }

    fun sendLogSubscribe(registryAccess: RegistryAccess, token: String, sinceSequence: Long): Boolean {
        if (!NetworkManager.canServerReceive(LOG_SUBSCRIBE)) return false
        val buffer = buffer(registryAccess)
        buffer.writeUtf(token, MAX_TOKEN_LENGTH)
        buffer.writeLong(sinceSequence)
        NetworkManager.sendToServer(LOG_SUBSCRIBE, buffer)
        return true
    }

    fun sendLogUnsubscribe(registryAccess: RegistryAccess) {
        if (!NetworkManager.canServerReceive(LOG_UNSUBSCRIBE)) return
        NetworkManager.sendToServer(LOG_UNSUBSCRIBE, buffer(registryAccess))
    }

    fun sendCapability(player: ServerPlayer, enabled: Boolean) {
        val buffer = buffer(player.level().server.registryAccess())
        buffer.writeBoolean(enabled)
        NetworkManager.sendToPlayer(player, CAPABILITY_RESPONSE, buffer)
    }

    fun sendCapabilityV2(player: ServerPlayer, capability: CommandCapability) {
        val buffer = buffer(player.level().server.registryAccess())
        buffer.writeVarInt(capability.protocolVersion)
        buffer.writeBoolean(capability.enabled)
        buffer.writeBoolean(capability.mixed)
        buffer.writeUtf(capability.reason, MAX_REASON_LENGTH)
        buffer.writeBoolean(capability.token != null)
        capability.token?.let { buffer.writeUtf(it, MAX_TOKEN_LENGTH) }
        buffer.writeVarInt(capability.ttlSeconds)
        buffer.writeBoolean(capability.logStreaming)
        NetworkManager.sendToPlayer(player, CAPABILITY_V2_RESPONSE, buffer)
    }

    fun sendResult(player: ServerPlayer, requestId: Long, result: ServerCommandResult) {
        val buffer = buffer(player.level().server.registryAccess())
        buffer.writeLong(requestId)
        writeResult(buffer, result)
        NetworkManager.sendToPlayer(player, COMMAND_RESPONSE, buffer)
    }

    fun sendResultV2(player: ServerPlayer, requestId: Long, result: ServerCommandResult) {
        val buffer = buffer(player.level().server.registryAccess())
        buffer.writeLong(requestId)
        writeResult(buffer, result)
        NetworkManager.sendToPlayer(player, COMMAND_V2_RESPONSE, buffer)
    }

    fun sendLogBatch(player: ServerPlayer, batch: ServerLogBatch) {
        val buffer = buffer(player.level().server.registryAccess())
        buffer.writeBoolean(batch.reset)
        buffer.writeLong(batch.dropped)
        buffer.writeVarInt(batch.records.size)
        batch.records.forEach { writeRecord(buffer, it) }
        NetworkManager.sendToPlayer(player, LOG_BATCH, buffer)
    }

    private fun writeResult(buffer: RegistryFriendlyByteBuf, result: ServerCommandResult) {
        buffer.writeVarInt(result.status.ordinal)
        buffer.writeVarInt(result.value)
        buffer.writeVarInt(result.feedback.size)
        for (message in result.feedback) {
            ComponentSerialization.STREAM_CODEC.encode(buffer, message)
        }
    }

    private fun readResult(buffer: RegistryFriendlyByteBuf): ServerCommandResult {
        val status = CommandResultStatus.entries[buffer.readVarInt().coerceIn(0, CommandResultStatus.entries.lastIndex)]
        val value = buffer.readVarInt()
        val count = buffer.readVarInt().coerceIn(0, MAX_FEEDBACK)
        val feedback = List(count) { ComponentSerialization.STREAM_CODEC.decode(buffer) }
        return ServerCommandResult(status, value, feedback)
    }

    private fun readCapability(buffer: RegistryFriendlyByteBuf): CommandCapability {
        val protocolVersion = buffer.readVarInt()
        val enabled = buffer.readBoolean()
        val mixed = buffer.readBoolean()
        val reason = buffer.readUtf(MAX_REASON_LENGTH)
        val token = if (buffer.readBoolean()) buffer.readUtf(MAX_TOKEN_LENGTH) else null
        val ttl = buffer.readVarInt()
        val logStreaming = buffer.readBoolean()
        return CommandCapability(enabled, mixed, protocolVersion, reason, token, ttl, logStreaming)
    }

    private fun writeRecord(buffer: RegistryFriendlyByteBuf, record: LogRecord) {
        buffer.writeLong(record.sequence)
        buffer.writeLong(record.timeMillis)
        buffer.writeVarInt(record.level.ordinal)
        buffer.writeUtf(record.threadName, MAX_THREAD_LENGTH)
        buffer.writeUtf(record.loggerName, MAX_LOGGER_LENGTH)
        buffer.writeUtf(record.message, MAX_MESSAGE_LENGTH)
        buffer.writeBoolean(record.throwableText != null)
        record.throwableText?.let { buffer.writeUtf(it, MAX_THROWABLE_LENGTH) }
        buffer.writeBoolean(record.truncated)
    }

    private fun readBatch(buffer: RegistryFriendlyByteBuf): ServerLogBatch {
        val reset = buffer.readBoolean()
        val dropped = buffer.readLong()
        val count = buffer.readVarInt().coerceIn(0, MAX_BATCH_RECORDS)
        val records = ArrayList<LogRecord>(count)
        repeat(count) { records += readRecord(buffer) }
        return ServerLogBatch(reset, dropped, records)
    }

    private fun readRecord(buffer: RegistryFriendlyByteBuf): LogRecord {
        val sequence = buffer.readLong()
        val timeMillis = buffer.readLong()
        val level = LogLevel.entries[buffer.readVarInt().coerceIn(0, LogLevel.entries.lastIndex)]
        val threadName = buffer.readUtf(MAX_THREAD_LENGTH)
        val loggerName = buffer.readUtf(MAX_LOGGER_LENGTH)
        val message = buffer.readUtf(MAX_MESSAGE_LENGTH)
        val throwable = if (buffer.readBoolean()) buffer.readUtf(MAX_THROWABLE_LENGTH) else null
        val truncated = buffer.readBoolean()
        return LogRecord(
            sequence = sequence,
            timeMillis = timeMillis,
            level = level,
            threadName = threadName,
            loggerName = loggerName,
            message = message,
            throwableText = throwable,
            truncated = truncated,
        )
    }

    private fun buffer(registryAccess: RegistryAccess): RegistryFriendlyByteBuf =
        RegistryFriendlyByteBuf(Unpooled.buffer(), registryAccess)

    private fun id(path: String): Identifier = Identifier.fromNamespaceAndPath(MOD_ID, path)

    private const val MAX_COMMAND_LENGTH = 2048
    private const val MAX_FEEDBACK = 64
    private const val MAX_TOKEN_LENGTH = 64
    private const val MAX_REASON_LENGTH = 64
    private const val MAX_THREAD_LENGTH = 128
    private const val MAX_LOGGER_LENGTH = 256
    private const val MAX_MESSAGE_LENGTH = 8_000
    private const val MAX_THROWABLE_LENGTH = 16_000
    private const val MAX_BATCH_RECORDS = 512
}
