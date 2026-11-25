package com.nts.ingamecnl.command

import com.nts.ingamecnl.config.ServerCommandConfig
import com.nts.ingamecnl.log.LogRecord
import com.nts.ingamecnl.server.log.ServerLogBuffer
import com.nts.ingamecnl.server.log.ServerLogCapture
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

internal object ServerLogStreamer {
    private class Subscription(
        val token: String,
        var lastSequence: Long,
        val pending: ArrayDeque<LogRecord> = ArrayDeque(),
        var resetPending: Boolean = false,
        var droppedPending: Long = 0L,
    )

    private val subscriptions = ConcurrentHashMap<UUID, Subscription>()

    fun subscribe(player: ServerPlayer, token: String, sinceSequence: Long) {
        if (ServerSilence.hides(player)) {
            subscriptions.remove(player.uuid)
            return
        }
        if (!ServerCommandSessions.validate(player, token) || !canStream(player)) {
            CommandNetwork.sendLogBatch(player, ServerLogBatch(true, 0, emptyList()))
            return
        }
        val subscription = Subscription(token, sinceSequence)
        fill(subscription, ServerLogCapture.buffer.snapshot(sinceSequence, ServerCommandConfig.logBacklogLines()))
        subscriptions[player.uuid] = subscription
        flush(player, subscription)
    }

    fun unsubscribe(player: ServerPlayer) {
        subscriptions.remove(player.uuid)
    }

    fun tick(server: MinecraftServer) {
        if (subscriptions.isEmpty()) return
        for (player in server.playerList.players.toList()) {
            val subscription = subscriptions[player.uuid] ?: continue
            if (!ServerCommandSessions.validate(player, subscription.token) || !canStream(player)) {
                subscriptions.remove(player.uuid)
                if (!ServerSilence.hides(player)) {
                    CommandNetwork.sendLogBatch(player, ServerLogBatch(true, 0, emptyList()))
                }
                continue
            }
            if (subscription.pending.isEmpty()) {
                fill(
                    subscription,
                    ServerLogCapture.buffer.snapshot(
                        subscription.lastSequence,
                        ServerCommandConfig.logBacklogLines(),
                    ),
                )
            }
            if (subscription.pending.isEmpty() && !subscription.resetPending) continue
            flush(player, subscription)
        }
    }

    fun forget(player: ServerPlayer) {
        subscriptions.remove(player.uuid)
    }

    fun subscriberCount(): Int = subscriptions.size

    fun clear() {
        subscriptions.clear()
    }

    private fun fill(subscription: Subscription, snapshot: ServerLogBuffer.Snapshot) {
        if (snapshot.reset) {
            subscription.pending.clear()
            subscription.resetPending = true
            subscription.droppedPending = snapshot.dropped
        }
        subscription.pending.addAll(snapshot.records)
    }

    private fun flush(player: ServerPlayer, subscription: Subscription) {
        val records = ArrayList<LogRecord>(minOf(subscription.pending.size, MAX_BATCH_RECORDS))
        var bytes = 0
        while (subscription.pending.isNotEmpty() && records.size < MAX_BATCH_RECORDS) {
            val next = subscription.pending.first()
            if (records.isNotEmpty() && bytes + next.textSize > MAX_BATCH_BYTES) break
            subscription.pending.removeFirst()
            records += next
            bytes += next.textSize
        }
        val reset = subscription.resetPending
        if (records.isEmpty() && !reset) return
        CommandNetwork.sendLogBatch(player, ServerLogBatch(reset, subscription.droppedPending, records))
        subscription.resetPending = false
        subscription.droppedPending = 0L
        records.lastOrNull()?.let { subscription.lastSequence = it.sequence }
    }

    private fun canStream(player: ServerPlayer): Boolean =
        ServerCommandConfig.enabled() &&
            ServerCommandConfig.logStreamingEnabled() &&
            CommandAccess.isAllowed(player)

    private const val MAX_BATCH_RECORDS = 256
    private const val MAX_BATCH_BYTES = 64 * 1024
}
