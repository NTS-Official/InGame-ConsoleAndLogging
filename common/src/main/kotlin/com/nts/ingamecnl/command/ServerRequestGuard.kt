package com.nts.ingamecnl.command

import com.nts.ingamecnl.config.ServerCommandConfig
import net.minecraft.server.level.ServerPlayer
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

internal object ServerRequestGuard {
    private data class Guard(
        var windowStartMillis: Long = 0,
        var count: Int = 0,
        var lastRequestMillis: Long = 0,
        val seen: LinkedHashMap<Long, Long> = LinkedHashMap(),
    )

    private val guards = ConcurrentHashMap<UUID, Guard>()

    fun allow(player: ServerPlayer, requestId: Long): Boolean {
        val now = System.currentTimeMillis()
        val guard = guards.computeIfAbsent(player.uuid) { Guard() }
        synchronized(guard) {
            guard.seen.entries.removeIf { now - it.value > REQUEST_ID_TTL_MILLIS }
            if (guard.seen.containsKey(requestId)) return false
            if (now - guard.windowStartMillis >= 60_000L) {
                guard.windowStartMillis = now
                guard.count = 0
            }
            if (guard.count >= ServerCommandConfig.maxRequestsPerMinute()) return false
            if (now - guard.lastRequestMillis < ServerCommandConfig.minIntervalMillis()) return false
            guard.count++
            guard.lastRequestMillis = now
            guard.seen[requestId] = now
            while (guard.seen.size > MAX_SEEN_REQUESTS) {
                val oldest = guard.seen.entries.firstOrNull() ?: break
                guard.seen.remove(oldest.key)
            }
            return true
        }
    }

    fun forget(player: ServerPlayer) {
        guards.remove(player.uuid)
    }

    fun clear() {
        guards.clear()
    }

    private const val REQUEST_ID_TTL_MILLIS = 60_000L
    private const val MAX_SEEN_REQUESTS = 256
}
