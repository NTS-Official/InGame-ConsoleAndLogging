package com.nts.ingamecnl.fabric

import com.nts.ingamecnl.client.config.ConsoleConfig
import com.terraformersmc.modmenu.api.ConfigScreenFactory
import com.terraformersmc.modmenu.api.ModMenuApi
import net.minecraft.client.gui.screens.Screen

class ModMenuIntegration : ModMenuApi {
    override fun getModConfigScreenFactory(): ConfigScreenFactory<Screen> =
        ConfigScreenFactory { parent -> ConsoleConfig.createScreen(parent) }
}
