package com.nts.ingamecnl.neoforge

import com.nts.ingamecnl.MOD_ID
import com.nts.ingamecnl.neoforge.client.NeoForgeClientSetup
import com.nts.ingamecnl.server.ServerCommandMode
import net.neoforged.api.distmarker.Dist
import net.neoforged.fml.ModContainer
import net.neoforged.fml.common.Mod
import net.neoforged.fml.loading.FMLEnvironment

@Mod(MOD_ID)
class InGameConsoleNeoForge(container: ModContainer) {
    init {
        if (FMLEnvironment.getDist() == Dist.CLIENT) {
            NeoForgeClientSetup.initialize(container)
        } else {
            ServerCommandMode.initialize()
        }
    }
}
