package com.nts.ingamecnl.neoforge

import com.nts.ingamecnl.MOD_ID
import com.nts.ingamecnl.client.ConsoleClient
import com.nts.ingamecnl.client.config.ConfigScreenFactory
import net.neoforged.api.distmarker.Dist
import net.neoforged.fml.ModContainer
import net.neoforged.fml.common.Mod
import net.neoforged.neoforge.client.gui.IConfigScreenFactory

@Mod(MOD_ID, dist = [Dist.CLIENT])
class InGameConsoleNeoForge(container: ModContainer) {
    init {
        ConsoleClient.initialize()
        container.registerExtensionPoint(IConfigScreenFactory::class.java) {
            IConfigScreenFactory { _, parent -> ConfigScreenFactory.create(parent) }
        }
    }
}
