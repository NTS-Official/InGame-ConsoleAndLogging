package com.nts.ingamecnl.command

import com.nts.ingamecnl.config.ServerCommandConfig
import net.minecraft.server.level.ServerPlayer

/** 静默模式：对未授权玩家完全不发任何本模组的数据包，使其无法察觉服务端装有本模组。 */
internal object ServerSilence {
    fun hides(player: ServerPlayer): Boolean {
        if (!ServerCommandConfig.silent()) return false
        return !CommandAccess.isAllowed(player)
    }
}
