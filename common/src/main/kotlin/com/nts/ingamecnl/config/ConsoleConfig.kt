package com.nts.ingamecnl.config

import me.shedaniel.autoconfig.AutoConfig
import me.shedaniel.autoconfig.ConfigData
import me.shedaniel.autoconfig.ConfigHolder
import me.shedaniel.autoconfig.annotation.Config
import me.shedaniel.autoconfig.annotation.ConfigEntry
import me.shedaniel.autoconfig.serializer.GsonConfigSerializer
import me.shedaniel.clothconfig2.gui.entries.SelectionListEntry
import com.nts.ingamecnl.log.LogLevel
import com.nts.ingamecnl.log.LogLimits

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
    @field:ConfigEntry.Category("logs")
    @field:ConfigEntry.Gui.Tooltip
    var logRecordLimit: Long = LogLimits.DEFAULT_RECORD_CAPACITY

    @JvmField
    @field:ConfigEntry.Category("logs")
    @field:ConfigEntry.Gui.Tooltip
    @field:ConfigEntry.BoundedDiscrete(min = LogLimits.MIN_TEXT_MIB, max = LogLimits.MAX_TEXT_MIB)
    var logTextLimitMiB: Int = LogLimits.DEFAULT_TEXT_MIB

    @JvmField
    @field:ConfigEntry.Category("autoSplash")
    @field:ConfigEntry.Gui.Tooltip
    var autoSplashEnabled: Boolean = true

    @JvmField
    @field:ConfigEntry.Category("autoSplash")
    @field:ConfigEntry.Gui.Tooltip
    @field:ConfigEntry.Gui.EnumHandler(option = ConfigEntry.Gui.EnumHandler.EnumDisplayOption.BUTTON)
    var autoSplashLevel: FilterLevel = FilterLevel.WARN

    @JvmField
    @field:ConfigEntry.Category("autoSplash")
    @field:ConfigEntry.Gui.Tooltip
    var autoSplashKeyword: String = ""

    @JvmField
    @field:ConfigEntry.Category("autoSplash")
    @field:ConfigEntry.Gui.Tooltip
    @field:ConfigEntry.BoundedDiscrete(min = 10, max = 100)
    var autoSplashHeightPercent: Int = 25

    @JvmField
    @field:ConfigEntry.Category("autoSplash")
    @field:ConfigEntry.Gui.Tooltip
    @field:ConfigEntry.BoundedDiscrete(min = 500, max = 10_000)
    var autoSplashStayMillis: Int = 3_000

    @JvmField
    @field:ConfigEntry.Category("autoSplash")
    @field:ConfigEntry.Gui.Tooltip
    var autoSplashEscapeCloses: Boolean = true

    @JvmField
    @field:ConfigEntry.Category("autoSplash")
    @field:ConfigEntry.Gui.Tooltip
    @field:ConfigEntry.BoundedDiscrete(min = 0, max = 600)
    var autoSplashSilenceSeconds: Int = 30

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

    @JvmField
    @field:ConfigEntry.Category("singleplayer")
    @field:ConfigEntry.Gui.Tooltip
    var allowRemoteCommandMode: Boolean = false

    @JvmField
    @field:ConfigEntry.Category("singleplayer")
    @field:ConfigEntry.Gui.Tooltip
    @field:ConfigEntry.Gui.EnumHandler(option = ConfigEntry.Gui.EnumHandler.EnumDisplayOption.BUTTON)
    var remoteCommandAccessMode: RemoteCommandAccessMode = RemoteCommandAccessMode.WHITELIST

    @JvmField
    @field:ConfigEntry.Category("singleplayer")
    @field:ConfigEntry.Gui.Tooltip
    var remoteCommandNames: MutableList<String> = mutableListOf()

    @JvmField
    @field:ConfigEntry.Category("singleplayer")
    @field:ConfigEntry.Gui.Tooltip
    var remoteCommandIps: MutableList<String> = mutableListOf()

    override fun validatePostLoad() {
        filterKeyword = filterKeyword.trim()
        logRecordLimit = logRecordLimit.coerceIn(1L, LogLimits.MAX_RECORD_CAPACITY)
        logTextLimitMiB = logTextLimitMiB.coerceIn(LogLimits.MIN_TEXT_MIB.toInt(), LogLimits.MAX_TEXT_MIB.toInt())
        autoSplashKeyword = autoSplashKeyword.trim()
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
        autoSplashHeightPercent = autoSplashHeightPercent.coerceIn(10, 100)
        autoSplashStayMillis = autoSplashStayMillis.coerceIn(500, 10_000)
        autoSplashSilenceSeconds = autoSplashSilenceSeconds.coerceIn(0, 600)
        remoteCommandNames = remoteCommandNames
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toMutableList()
        remoteCommandIps = remoteCommandIps
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toMutableList()
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

enum class RemoteCommandAccessMode : SelectionListEntry.Translatable {
    WHITELIST,
    BLACKLIST;

    override fun getKey(): String =
        "text.autoconfig.ingamecnl.option.remoteCommandAccessMode.${name.lowercase()}"
}

object ConsoleConfig {
    private var holder: ConfigHolder<ConsoleConfigData>? = null
    private val fallback = ConsoleConfigData()

    val data: ConsoleConfigData
        get() = holder?.config ?: fallback

    fun initialize() {
        if (holder != null) return
        holder = AutoConfig.register(ConsoleConfigData::class.java) { definition, configClass ->
            GsonConfigSerializer(definition, configClass)
        }
    }

    internal fun configHolder(): ConfigHolder<ConsoleConfigData>? = holder

    fun save() {
        holder?.save()
    }

    fun updateFilter(level: LogLevel?, keyword: String?) {
        val config = data
        config.filterLevel = level?.let { FilterLevel.valueOf(it.name) } ?: FilterLevel.ALL
        config.filterKeyword = keyword ?: ""
    }

    fun logRecordLimit(): Long = logRecordLimit(data)

    fun logRecordLimit(config: ConsoleConfigData): Long =
        config.logRecordLimit.coerceIn(1L, LogLimits.MAX_RECORD_CAPACITY)

    fun logTextLimit(): Long = logTextLimit(data)

    fun logTextLimit(config: ConsoleConfigData): Long =
        config.logTextLimitMiB.coerceIn(LogLimits.MIN_TEXT_MIB.toInt(), LogLimits.MAX_TEXT_MIB.toInt()).toLong() *
            1024L * 1024L

    fun autoSplashEnabled(): Boolean = data.autoSplashEnabled

    fun autoSplashLevel(): LogLevel? = data.autoSplashLevel.toLogLevel()

    fun autoSplashKeyword(): String = data.autoSplashKeyword.trim()

    fun autoSplashHeightPercent(): Int = data.autoSplashHeightPercent.coerceIn(10, 100)

    fun autoSplashStayNanos(): Long =
        (data.autoSplashStayMillis.coerceIn(500, 10_000) * 1_000_000L)

    fun autoSplashEscapeCloses(): Boolean = data.autoSplashEscapeCloses

    fun autoSplashSilenceNanos(): Long =
        (data.autoSplashSilenceSeconds.coerceIn(0, 600) * 1_000_000_000L)

    fun setAutoSplashKeyword(keyword: String) {
        val trimmed = keyword.trim()
        val current = holder
        if (current == null) {
            fallback.autoSplashKeyword = trimmed
            return
        }
        current.config.autoSplashKeyword = trimmed
        current.save()
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

    fun allowRemoteCommandMode(): Boolean = data.allowRemoteCommandMode

    fun remoteCommandAccessMode(): RemoteCommandAccessMode = data.remoteCommandAccessMode

    fun remoteCommandNames(): List<String> = data.remoteCommandNames
        .map { it.trim() }
        .filter { it.isNotEmpty() }

    fun remoteCommandIps(): List<String> = data.remoteCommandIps
        .map { it.trim() }
        .filter { it.isNotEmpty() }

    private fun opaque(color: Int): Int = 0xFF000000.toInt() or (color and 0xFFFFFF)
}
