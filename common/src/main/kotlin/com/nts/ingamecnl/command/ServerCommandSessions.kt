package com.nts.ingamecnl.command

import com.nts.ingamecnl.config.ServerCommandConfig
import net.minecraft.server.level.ServerPlayer
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

internal object ServerCommandSessions {
    private data class Session(
        val token: String,
        val playerId: UUID,
        val expiresAtNanos: Long,
    )

    private val sessions = ConcurrentHashMap<UUID, Session>()

    fun issue(player: ServerPlayer): String {
        val token = UUID.randomUUID().toString()
        val session = Session(token, player.uuid, System.nanoTime() + ServerCommandConfig.sessionTtlNanos())
        sessions[player.uuid] = session
        return token
    }

    fun validate(player: ServerPlayer, token: String?): Boolean {
        if (token == null) return false
        val session = sessions[player.uuid] ?: return false
        if (session.playerId != player.uuid) return false
        if (session.expiresAtNanos <= System.nanoTime()) {
            sessions.remove(player.uuid, session)
            return false
        }
        return session.token == token
    }

    fun revoke(player: ServerPlayer) {
        sessions.remove(player.uuid)
    }

    fun clear() {
        sessions.clear()
    }
}
