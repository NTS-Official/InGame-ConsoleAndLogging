package com.nts.ingamecnl.fabric

import com.nts.ingamecnl.client.ConsoleClient
import net.fabricmc.api.ClientModInitializer

class InGameConsoleFabric : ClientModInitializer {
    override fun onInitializeClient() {
        ConsoleClient.initialize()
    }
}
