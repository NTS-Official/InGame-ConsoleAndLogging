package com.nts.ingamecnl.client.gui

import com.nts.ingamecnl.MOD_ID

import com.nts.ingamecnl.command.CommandResultStatus
import com.nts.ingamecnl.command.ServerCommandResult
import net.minecraft.network.chat.Component

internal class CommandTranscript : TimelineSource {
    private val entries = ArrayDeque<TimelineEntry.Text>()
    private var characters = 0

    override var revision = 0L
        private set

    override val size: Int
        get() = entries.size

    override val emptyTextKey: String
        get() = "console.$MOD_ID.command.empty"

    override val numberWidth: Int
        get() = 0

    override fun entryAt(index: Int): TimelineEntry = entries[index]

    fun addInput(command: String) {
        add(Component.literal("> $command"), TimelinePalette.INPUT)
    }

    fun addNote(text: Component, error: Boolean = false) {
        add(text, if (error) TimelinePalette.ERROR else TimelinePalette.NOTE)
    }

    fun addResult(result: ServerCommandResult) {
        when (result.status) {
            CommandResultStatus.EXECUTED -> {
                if (result.feedback.isEmpty()) {
                    add(
                        Component.translatable("console.$MOD_ID.command.result.success", result.value),
                        TimelinePalette.NOTE,
                    )
                } else {
                    result.feedback.forEach { add(indent(it), TimelinePalette.NOTE) }
                }
            }
            CommandResultStatus.FAILED -> {
                if (result.feedback.isEmpty()) {
                    add(Component.translatable("console.$MOD_ID.command.result.failed"), TimelinePalette.ERROR)
                } else {
                    result.feedback.forEach { add(indent(it), TimelinePalette.ERROR) }
                }
            }
            CommandResultStatus.DENIED -> add(
                Component.translatable("console.$MOD_ID.command.result.denied"),
                TimelinePalette.ERROR,
            )
            CommandResultStatus.UNKNOWN -> add(
                Component.translatable("console.$MOD_ID.command.result.unknown"),
                TimelinePalette.ERROR,
            )
        }
    }

    fun clear() {
        entries.clear()
        characters = 0
        revision++
    }

    private fun add(text: Component, color: Int) {
        entries.addLast(TimelineEntry.Text(text, color))
        characters += text.string.length
        while (entries.size > MAX_ENTRIES) {
            characters -= entries.removeFirst().text.string.length
        }
        while (characters > MAX_CHARACTERS && entries.size > 1) {
            characters -= entries.removeFirst().text.string.length
        }
        revision++
    }

    private fun indent(text: Component): Component =
        if (text.string.isEmpty()) text else Component.literal("  ").append(text)

    private companion object {
        const val MAX_ENTRIES = 200
        const val MAX_CHARACTERS = 20_000
    }
}
