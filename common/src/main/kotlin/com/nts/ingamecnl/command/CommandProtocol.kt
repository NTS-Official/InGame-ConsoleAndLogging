package com.nts.ingamecnl.command

import com.nts.ingamecnl.log.LogRecord

internal object CommandProtocol {
    const val VERSION = 2
}

internal data class CommandCapability(
    val enabled: Boolean,
    val mixed: Boolean,
    val protocolVersion: Int,
    val reason: String,
    val token: String?,
    val ttlSeconds: Int,
    val logStreaming: Boolean,
)

internal data class ServerLogBatch(
    val reset: Boolean,
    val dropped: Long,
    val records: List<LogRecord>,
)
