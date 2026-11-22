package com.nts.ingamecnl.command

import net.minecraft.network.chat.Component

internal enum class CommandResultStatus {
    EXECUTED,
    FAILED,
    DENIED,
    UNKNOWN,
}

internal data class ServerCommandResult(
    val status: CommandResultStatus,
    val value: Int = 0,
    val feedback: List<Component> = emptyList(),
)
