package com.nts.ingamecnl.client.config

import com.nts.ingamecnl.config.ConsoleConfigData
import me.shedaniel.autoconfig.AutoConfigClient
import net.minecraft.client.gui.screens.Screen

object ConfigScreenFactory {
    fun create(parent: Screen?): Screen =
        AutoConfigClient.getConfigScreen(ConsoleConfigData::class.java, parent).get()
}
