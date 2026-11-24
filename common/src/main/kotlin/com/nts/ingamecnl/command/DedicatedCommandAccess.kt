package com.nts.ingamecnl.command

import com.nts.ingamecnl.config.ServerCommandAccessMode
import com.nts.ingamecnl.config.ServerCommandConfig
import net.minecraft.server.level.ServerPlayer
import java.util.UUID

internal object DedicatedCommandAccess {
    fun isAllowed(player: ServerPlayer): Boolean {
        if (!ServerCommandConfig.enabled()) return false

        val name = player.gameProfile.name
        val uuid = player.uuid.toString()
        val address = player.ipAddress
        val nameMatch = ServerCommandConfig.names().any { it.equals(name, ignoreCase = true) }
        val uuidMatch = ServerCommandConfig.uuids().any { normalizeUuid(it) == normalizeUuid(uuid) }
        val addressMatch = ServerCommandConfig.ips().any { it == address }

        return when (ServerCommandConfig.accessMode()) {
            ServerCommandAccessMode.WHITELIST -> nameMatch || uuidMatch || addressMatch
            ServerCommandAccessMode.BLACKLIST -> {
                val hasEntries = ServerCommandConfig.names().isNotEmpty() ||
                    ServerCommandConfig.uuids().isNotEmpty() ||
                    ServerCommandConfig.ips().isNotEmpty()
                hasEntries && !(nameMatch || uuidMatch || addressMatch)
            }
        }
    }

    private fun normalizeUuid(value: String): String? =
        runCatching { UUID.fromString(value.trim()).toString() }.getOrNull()
}
