package com.nts.ingamecnl.neoforge.client

import com.nts.ingamecnl.client.ConsoleClient
import com.nts.ingamecnl.client.config.ConfigScreenFactory
import net.neoforged.fml.ModContainer
import net.neoforged.neoforge.client.gui.IConfigScreenFactory

object NeoForgeClientSetup {
    fun initialize(container: ModContainer) {
        ConsoleClient.initialize()
        container.registerExtensionPoint(IConfigScreenFactory::class.java) {
            IConfigScreenFactory { _, parent -> ConfigScreenFactory.create(parent) }
        }
    }
}
