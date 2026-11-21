package com.nts.ingamecnl.client.log

import org.apache.logging.log4j.Level

enum class LogLevel(val label: String) {
    TRACE("TRACE"),
    DEBUG("DEBUG"),
    INFO("INFO"),
    WARN("WARN"),
    ERROR("ERROR"),
    FATAL("FATAL"),
    ;

    companion object {
        fun parse(value: String): LogLevel? =
            entries.firstOrNull { it.label.equals(value, ignoreCase = true) }

        fun fromLog4j(level: Level): LogLevel = when {
            level.intLevel() >= Level.TRACE.intLevel() -> TRACE
            level.intLevel() >= Level.DEBUG.intLevel() -> DEBUG
            level.intLevel() >= Level.INFO.intLevel() -> INFO
            level.intLevel() >= Level.WARN.intLevel() -> WARN
            level.intLevel() >= Level.ERROR.intLevel() -> ERROR
            else -> FATAL
        }
    }
}
