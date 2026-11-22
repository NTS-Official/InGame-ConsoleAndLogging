package com.nts.ingamecnl.client.gui

import com.nts.ingamecnl.command.CommandResultStatus
import com.nts.ingamecnl.command.ServerCommandResult
import net.minecraft.network.chat.Component

internal enum class CommandTranscriptKind {
    INPUT,
    OUTPUT,
    ERROR,
}

internal data class CommandTranscriptEntry(
    val kind: CommandTranscriptKind,
    val text: Component,
)

internal class CommandTranscript {
    private val entries = ArrayDeque<CommandTranscriptEntry>()
    private var characters = 0

    val all: List<CommandTranscriptEntry>
        get() = entries.toList()

    val isEmpty: Boolean
        get() = entries.isEmpty()

    fun addInput(command: String) {
        add(CommandTranscriptEntry(CommandTranscriptKind.INPUT, Component.literal("> $command")))
    }

    fun addResult(result: ServerCommandResult) {
        when (result.status) {
            CommandResultStatus.EXECUTED -> {
                if (result.feedback.isEmpty()) {
                    add(
                        CommandTranscriptEntry(
                            CommandTranscriptKind.OUTPUT,
                            Component.translatable("console.ingamecnl.command.result.success", result.value),
                        ),
                    )
                } else {
                    result.feedback.forEach { add(CommandTranscriptEntry(CommandTranscriptKind.OUTPUT, it)) }
                }
            }
            CommandResultStatus.FAILED -> {
                if (result.feedback.isEmpty()) {
                    add(
                        CommandTranscriptEntry(
                            CommandTranscriptKind.ERROR,
                            Component.translatable("console.ingamecnl.command.result.failed"),
                        ),
                    )
                } else {
                    result.feedback.forEach { add(CommandTranscriptEntry(CommandTranscriptKind.ERROR, it)) }
                }
            }
            CommandResultStatus.DENIED -> add(
                CommandTranscriptEntry(
                    CommandTranscriptKind.ERROR,
                    Component.translatable("console.ingamecnl.command.result.denied"),
                ),
            )
            CommandResultStatus.UNKNOWN -> add(
                CommandTranscriptEntry(
                    CommandTranscriptKind.ERROR,
                    Component.translatable("console.ingamecnl.command.result.unknown"),
                ),
            )
        }
    }

    fun clear() {
        entries.clear()
        characters = 0
    }

    private fun add(entry: CommandTranscriptEntry) {
        entries.addLast(entry)
        characters += entry.text.string.length
        while (entries.size > MAX_ENTRIES) {
            characters -= entries.removeFirst().text.string.length
        }
        while (characters > MAX_CHARACTERS && entries.size > 1) {
            characters -= entries.removeFirst().text.string.length
        }
    }

    private companion object {
        const val MAX_ENTRIES = 200
        const val MAX_CHARACTERS = 20_000
    }
}
