package com.nts.ingamecnl.command

import com.nts.ingamecnl.config.ConsoleConfig
import com.nts.ingamecnl.config.RemoteCommandAccessMode
import net.minecraft.server.level.ServerPlayer
import net.minecraft.server.players.NameAndId

internal object CommandAccess {
    fun isAllowed(player: ServerPlayer): Boolean {
        val server = player.level().server
        if (!server.isSingleplayer) return false
        if (server.isSingleplayerOwner(NameAndId(player.gameProfile))) return true
        if (!ConsoleConfig.allowRemoteCommandMode()) return false

        val name = player.gameProfile.name
        val address = player.ipAddress
        val nameMatch = ConsoleConfig.remoteCommandNames().any { it.equals(name, ignoreCase = true) }
        val addressMatch = ConsoleConfig.remoteCommandIps().any { it == address }
        return when (ConsoleConfig.remoteCommandAccessMode()) {
            RemoteCommandAccessMode.WHITELIST -> nameMatch || addressMatch
            RemoteCommandAccessMode.BLACKLIST -> !nameMatch && !addressMatch
        }
    }
}
