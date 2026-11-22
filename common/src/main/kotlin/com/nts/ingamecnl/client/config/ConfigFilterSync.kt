package com.nts.ingamecnl.client.config

import com.nts.ingamecnl.client.log.DisplayFilter
import com.nts.ingamecnl.client.log.LogCapture
import com.nts.ingamecnl.config.ConsoleConfig
import com.nts.ingamecnl.config.ConsoleConfigData
import net.minecraft.world.InteractionResult

object ConfigFilterSync {
    fun initialize() {
        val holder = ConsoleConfig.configHolder() ?: return
        holder.registerLoadListener { _, config ->
            apply(config)
            InteractionResult.SUCCESS
        }
        holder.registerSaveListener { _, config ->
            apply(config)
            InteractionResult.SUCCESS
        }
        apply(holder.config)
    }

    fun apply(filter: DisplayFilter, save: Boolean = true) {
        ConsoleConfig.updateFilter(filter.level, filter.keyword)
        LogCapture.setFilter(filter)
        if (save) ConsoleConfig.save()
    }

    private fun apply(config: ConsoleConfigData) {
        val filter = DisplayFilter(
            config.filterLevel.toLogLevel(),
            config.filterKeyword.trim().takeIf { it.isNotEmpty() },
        )
        LogCapture.setFilter(filter)
        LogCapture.setHistoryLimits(ConsoleConfig.logRecordLimit(config), ConsoleConfig.logTextLimit(config))
    }
}
