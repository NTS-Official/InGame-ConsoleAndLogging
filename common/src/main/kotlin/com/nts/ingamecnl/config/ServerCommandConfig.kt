package com.nts.ingamecnl.config

import com.nts.ingamecnl.MOD_ID

import me.shedaniel.autoconfig.AutoConfig
import me.shedaniel.autoconfig.ConfigData
import me.shedaniel.autoconfig.ConfigHolder
import me.shedaniel.autoconfig.annotation.Config
import me.shedaniel.autoconfig.annotation.ConfigEntry
import me.shedaniel.autoconfig.serializer.GsonConfigSerializer
import java.util.UUID

enum class ServerCommandAccessMode {
    WHITELIST,
    BLACKLIST,
}

@Config(name = MOD_ID + "-server")
class ServerCommandConfigData : ConfigData {
    @JvmField
    @field:ConfigEntry.Gui.Tooltip
    var enabled: Boolean = false

    @JvmField
    @field:ConfigEntry.Gui.Tooltip
    var silent: Boolean = false

    @JvmField
    @field:ConfigEntry.Gui.Tooltip
    var accessMode: ServerCommandAccessMode = ServerCommandAccessMode.WHITELIST

    @JvmField
    @field:ConfigEntry.Gui.Tooltip
    var names: MutableList<String> = mutableListOf()

    @JvmField
    @field:ConfigEntry.Gui.Tooltip
    var uuids: MutableList<String> = mutableListOf()

    @JvmField
    @field:ConfigEntry.Gui.Tooltip
    var ips: MutableList<String> = mutableListOf()

    @JvmField
    @field:ConfigEntry.Gui.Tooltip
    var maxRequestsPerMinute: Int = 20

    @JvmField
    @field:ConfigEntry.Gui.Tooltip
    var minIntervalMillis: Int = 200

    @JvmField
    @field:ConfigEntry.Gui.Tooltip
    var sessionTtlSeconds: Int = 300

    @JvmField
    @field:ConfigEntry.Gui.Tooltip
    var auditLog: Boolean = true

    @JvmField
    @field:ConfigEntry.Gui.Tooltip
    var logStreamingEnabled: Boolean = true

    @JvmField
    @field:ConfigEntry.Gui.Tooltip
    var logBacklogLines: Int = 10_000

    override fun validatePostLoad() {
        names = names.map { it.trim() }.filter { it.isNotEmpty() }.distinct().toMutableList()
        uuids = uuids.mapNotNull { value ->
            val trimmed = value.trim()
            if (trimmed.isEmpty()) null else runCatching { UUID.fromString(trimmed).toString() }.getOrNull()
        }.distinct().toMutableList()
        ips = ips.map { it.trim() }.filter { it.isNotEmpty() }.distinct().toMutableList()
        maxRequestsPerMinute = maxRequestsPerMinute.coerceIn(1, 600)
        minIntervalMillis = minIntervalMillis.coerceIn(0, 10_000)
        sessionTtlSeconds = sessionTtlSeconds.coerceIn(60, 3_600)
        logBacklogLines = logBacklogLines.coerceIn(100, 10_000)
    }
}

object ServerCommandConfig {
    private var holder: ConfigHolder<ServerCommandConfigData>? = null
    private val fallback = ServerCommandConfigData()

    val data: ServerCommandConfigData
        get() = holder?.config ?: fallback

    fun initialize() {
        if (holder != null) return
        holder = AutoConfig.register(ServerCommandConfigData::class.java) { definition, configClass ->
            GsonConfigSerializer(definition, configClass)
        }
    }

    fun load(): Boolean = holder?.load() ?: false

    fun save() {
        holder?.save()
    }

    fun enabled(): Boolean = data.enabled

    fun setEnabled(value: Boolean) {
        data.enabled = value
        save()
    }

    fun silent(): Boolean = data.silent

    fun setSilent(value: Boolean) {
        data.silent = value
        save()
    }

    fun accessMode(): ServerCommandAccessMode = data.accessMode

    fun names(): List<String> = data.names

    fun uuids(): List<String> = data.uuids

    fun ips(): List<String> = data.ips

    fun maxRequestsPerMinute(): Int = data.maxRequestsPerMinute.coerceIn(1, 600)

    fun minIntervalMillis(): Int = data.minIntervalMillis.coerceIn(0, 10_000)

    fun sessionTtlSeconds(): Int = data.sessionTtlSeconds.coerceIn(60, 3_600)

    fun sessionTtlNanos(): Long = sessionTtlSeconds() * 1_000_000_000L

    fun auditLog(): Boolean = data.auditLog

    fun logStreamingEnabled(): Boolean = data.logStreamingEnabled

    fun logBacklogLines(): Int = data.logBacklogLines.coerceIn(100, 10_000)
}
