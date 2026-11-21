package com.nts.ingamecnl.client.config

import com.nts.ingamecnl.client.log.DisplayFilter
import com.nts.ingamecnl.client.log.LogCapture
import com.nts.ingamecnl.client.log.LogLevel
import me.shedaniel.autoconfig.AutoConfig
import me.shedaniel.autoconfig.AutoConfigClient
import me.shedaniel.autoconfig.ConfigData
import me.shedaniel.autoconfig.ConfigHolder
import me.shedaniel.autoconfig.annotation.Config
import me.shedaniel.autoconfig.annotation.ConfigEntry
import me.shedaniel.autoconfig.serializer.GsonConfigSerializer
import me.shedaniel.clothconfig2.gui.entries.SelectionListEntry
import net.minecraft.client.gui.screens.Screen
import net.minecraft.world.InteractionResult

@Config(name = "ingamecnl")
class ConsoleConfigData : ConfigData {
    @JvmField
    @field:ConfigEntry.Category("filter")
    @field:ConfigEntry.Gui.Tooltip
    @field:ConfigEntry.Gui.EnumHandler(option = ConfigEntry.Gui.EnumHandler.EnumDisplayOption.BUTTON)
    var filterLevel: FilterLevel = FilterLevel.ALL

    @JvmField
    @field:ConfigEntry.Category("filter")
    @field:ConfigEntry.Gui.Tooltip
    var filterKeyword: String = ""

    @JvmField
    @field:ConfigEntry.Category("colors")
    @field:ConfigEntry.Gui.Tooltip
    @field:ConfigEntry.ColorPicker(allowAlpha = false)
    var timeColor: Int = 0x5555FF

    @JvmField
    @field:ConfigEntry.Category("colors")
    @field:ConfigEntry.Gui.Tooltip
    @field:ConfigEntry.ColorPicker(allowAlpha = false)
    var levelTraceColor: Int = 0x5555FF

    @JvmField
    @field:ConfigEntry.Category("colors")
    @field:ConfigEntry.Gui.Tooltip
    @field:ConfigEntry.ColorPicker(allowAlpha = false)
    var levelDebugColor: Int = 0x55FF55

    @JvmField
    @field:ConfigEntry.Category("colors")
    @field:ConfigEntry.Gui.Tooltip
    @field:ConfigEntry.ColorPicker(allowAlpha = false)
    var levelInfoColor: Int = 0x55FF55

    @JvmField
    @field:ConfigEntry.Category("colors")
    @field:ConfigEntry.Gui.Tooltip
    @field:ConfigEntry.ColorPicker(allowAlpha = false)
    var levelWarnColor: Int = 0xFFFF55

    @JvmField
    @field:ConfigEntry.Category("colors")
    @field:ConfigEntry.Gui.Tooltip
    @field:ConfigEntry.ColorPicker(allowAlpha = false)
    var levelErrorColor: Int = 0xFF5555

    @JvmField
    @field:ConfigEntry.Category("colors")
    @field:ConfigEntry.Gui.Tooltip
    @field:ConfigEntry.ColorPicker(allowAlpha = false)
    var levelFatalColor: Int = 0xFF5555

    @JvmField
    @field:ConfigEntry.Category("colors")
    @field:ConfigEntry.Gui.Tooltip
    @field:ConfigEntry.ColorPicker(allowAlpha = false)
    var loggerColor: Int = 0x55FFFF

    @JvmField
    @field:ConfigEntry.Category("colors")
    @field:ConfigEntry.Gui.Tooltip
    @field:ConfigEntry.ColorPicker(allowAlpha = false)
    var messageColor: Int = 0xFFFFFF

    @JvmField
    @field:ConfigEntry.Category("colors")
    @field:ConfigEntry.Gui.Tooltip
    @field:ConfigEntry.ColorPicker(allowAlpha = false)
    var errorMessageColor: Int = 0xFF5555

    @JvmField
    @field:ConfigEntry.Category("overlay")
    @field:ConfigEntry.Gui.Tooltip
    @field:ConfigEntry.BoundedDiscrete(min = 30, max = 100)
    var overlayHeightPercent: Int = 65

    @JvmField
    @field:ConfigEntry.Category("overlay")
    @field:ConfigEntry.Gui.Tooltip
    @field:ConfigEntry.BoundedDiscrete(min = 0, max = 1000)
    var overlayAnimationMillis: Int = 180

    @JvmField
    @field:ConfigEntry.Category("overlay")
    @field:ConfigEntry.Gui.Tooltip
    @field:ConfigEntry.BoundedDiscrete(min = 0, max = 100)
    var overlayOpacityPercent: Int = 10

    override fun validatePostLoad() {
        filterKeyword = filterKeyword.trim()
        timeColor = rgb(timeColor)
        levelTraceColor = rgb(levelTraceColor)
        levelDebugColor = rgb(levelDebugColor)
        levelInfoColor = rgb(levelInfoColor)
        levelWarnColor = rgb(levelWarnColor)
        levelErrorColor = rgb(levelErrorColor)
        levelFatalColor = rgb(levelFatalColor)
        loggerColor = rgb(loggerColor)
        messageColor = rgb(messageColor)
        errorMessageColor = rgb(errorMessageColor)
        overlayHeightPercent = overlayHeightPercent.coerceIn(30, 100)
        overlayAnimationMillis = overlayAnimationMillis.coerceIn(0, 1000)
        overlayOpacityPercent = overlayOpacityPercent.coerceIn(0, 100)
    }

    private fun rgb(color: Int): Int = color and 0xFFFFFF
}

enum class FilterLevel : SelectionListEntry.Translatable {
    ALL,
    TRACE,
    DEBUG,
    INFO,
    WARN,
    ERROR,
    FATAL;

    override fun getKey(): String = "text.autoconfig.ingamecnl.option.filterLevel.${name.lowercase()}"

    fun toLogLevel(): LogLevel? = when (this) {
        ALL -> null
        else -> LogLevel.valueOf(name)
    }
}

object ConsoleConfig {
    private var holder: ConfigHolder<ConsoleConfigData>? = null
    private val fallback = ConsoleConfigData()

    val data: ConsoleConfigData
        get() = holder?.config ?: fallback

    fun initialize() {
        if (holder != null) return
        val registered = AutoConfig.register(ConsoleConfigData::class.java) { definition, configClass ->
            GsonConfigSerializer(definition, configClass)
        }
        registered.registerLoadListener { _, config ->
            applyFilter(config)
            InteractionResult.SUCCESS
        }
        registered.registerSaveListener { _, config ->
            applyFilter(config)
            InteractionResult.SUCCESS
        }
        holder = registered
        applyFilter(registered.config)
    }

    fun createScreen(parent: Screen?): Screen =
        AutoConfigClient.getConfigScreen(ConsoleConfigData::class.java, parent).get()

    fun applyFilter(filter: DisplayFilter, save: Boolean = true) {
        val current = holder
        if (current == null) {
            LogCapture.setFilter(filter)
            return
        }
        val config = current.config
        config.filterLevel = filter.level?.let { FilterLevel.valueOf(it.name) } ?: FilterLevel.ALL
        config.filterKeyword = filter.keyword ?: ""
        applyFilter(config)
        if (save) current.save()
    }

    fun overlayHeightPercent(): Int = data.overlayHeightPercent.coerceIn(30, 100)

    fun overlayAnimationNanos(): Long =
        (data.overlayAnimationMillis.coerceIn(0, 1000) * 1_000_000L).coerceAtLeast(1L)

    fun overlayAlpha(): Int = data.overlayOpacityPercent.coerceIn(0, 100) * 255 / 100

    fun timeColor(): Int = opaque(data.timeColor)

    fun loggerColor(): Int = opaque(data.loggerColor)

    fun levelColor(level: LogLevel): Int = opaque(
        when (level) {
            LogLevel.TRACE -> data.levelTraceColor
            LogLevel.DEBUG -> data.levelDebugColor
            LogLevel.INFO -> data.levelInfoColor
            LogLevel.WARN -> data.levelWarnColor
            LogLevel.ERROR -> data.levelErrorColor
            LogLevel.FATAL -> data.levelFatalColor
        },
    )

    fun messageColor(level: LogLevel): Int = opaque(
        when (level) {
            LogLevel.ERROR, LogLevel.FATAL -> data.errorMessageColor
            else -> data.messageColor
        },
    )

    private fun applyFilter(config: ConsoleConfigData) {
        LogCapture.setFilter(
            DisplayFilter(
                config.filterLevel.toLogLevel(),
                config.filterKeyword.trim().takeIf { it.isNotEmpty() },
            ),
        )
    }

    private fun opaque(color: Int): Int = 0xFF000000.toInt() or (color and 0xFFFFFF)
}


