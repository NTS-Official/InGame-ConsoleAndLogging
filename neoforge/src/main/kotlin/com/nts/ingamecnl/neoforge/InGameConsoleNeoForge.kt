package com.nts.ingamecnl.neoforge

import com.nts.ingamecnl.MOD_ID
import com.nts.ingamecnl.client.ConsoleClient
import net.neoforged.api.distmarker.Dist
import net.neoforged.fml.common.Mod

@Mod(MOD_ID, dist = [Dist.CLIENT])
class InGameConsoleNeoForge {
    init {
        ConsoleClient.initialize()
    }
}
