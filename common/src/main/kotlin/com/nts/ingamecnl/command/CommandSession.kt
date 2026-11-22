package com.nts.ingamecnl.command

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

internal const val COMMAND_TIMEOUT_NANOS = 10_000_000_000L

internal interface CommandSession {
    fun submit(command: String, onResult: (ServerCommandResult) -> Unit): Boolean

    fun tick(nowNanos: Long)

    fun close()
}

internal abstract class CommandSessionBase : CommandSession {
    private val pending = ConcurrentHashMap<Long, PendingCommand>()
    private val sequence = AtomicLong()

    protected fun begin(onResult: (ServerCommandResult) -> Unit): Long {
        val id = sequence.incrementAndGet()
        pending[id] = PendingCommand(System.nanoTime() + COMMAND_TIMEOUT_NANOS, onResult)
        return id
    }

    protected fun finish(id: Long, result: ServerCommandResult) {
        pending.remove(id)?.onResult?.invoke(result)
    }

    protected fun cancel(id: Long) {
        pending.remove(id)
    }

    override fun tick(nowNanos: Long) {
        for ((id, command) in pending.entries.toList()) {
            if (command.deadlineNanos <= nowNanos && pending.remove(id, command)) {
                command.onResult(ServerCommandResult(CommandResultStatus.UNKNOWN))
            }
        }
    }

    override fun close() {
        pending.clear()
    }

    private data class PendingCommand(
        val deadlineNanos: Long,
        val onResult: (ServerCommandResult) -> Unit,
    )
}
