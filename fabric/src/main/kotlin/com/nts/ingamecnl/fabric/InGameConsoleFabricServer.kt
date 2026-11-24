package com.nts.ingamecnl.fabric

import com.nts.ingamecnl.server.ServerCommandMode
import net.fabricmc.api.DedicatedServerModInitializer

class InGameConsoleFabricServer : DedicatedServerModInitializer {
    override fun onInitializeServer() {
        ServerCommandMode.initialize()
    }
}
