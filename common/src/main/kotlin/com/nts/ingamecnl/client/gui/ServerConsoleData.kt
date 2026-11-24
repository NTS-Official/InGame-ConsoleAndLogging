package com.nts.ingamecnl.client.gui

import com.nts.ingamecnl.MOD_ID

import com.nts.ingamecnl.client.log.DisplayFilter
import com.nts.ingamecnl.client.log.LOG_PAGE_SIZE
import com.nts.ingamecnl.client.log.LogPage
import com.nts.ingamecnl.client.log.LogSnapshot
import com.nts.ingamecnl.command.CommandResultStatus
import com.nts.ingamecnl.command.ServerCommandResult
import com.nts.ingamecnl.log.LogRecord
import net.minecraft.network.chat.Component

internal class ServerConsoleData : TimelineSource {
    private val raw = ArrayDeque<LogRecord>()
    private val entries = ArrayDeque<TimelineEntry>()
    private var rawCharacters = 0L
    private var commandCharacters = 0
    private var visibleLogs = 0

    var filter = DisplayFilter()
        private set

    var lastSequence = -1L
        private set

    var dropped = 0L
        private set

    var hidden = 0L
        private set

    var version = 0L
        private set

    override var revision = 0L
        private set

    var needsResync = false
        private set

    override val size: Int
        get() = entries.size

    override val emptyTextKey: String
        get() = "console.$MOD_ID.mixed.empty"

    override val numberWidth: Int
        get() = entries.lastOrNull { it is TimelineEntry.Record }
            ?.let { ((it as TimelineEntry.Record).record.sequence + 1L).toString().length.coerceAtLeast(2) }
            ?: 2

    override fun entryAt(index: Int): TimelineEntry = entries[index]

    val rawRecords: List<LogRecord>
        get() = raw.toList()

    fun onBatch(reset: Boolean, droppedCount: Long, records: List<LogRecord>) {
        if (reset) clearLogs()
        dropped = maxOf(dropped, droppedCount.coerceAtLeast(0L))
        for (record in records) {
            if (lastSequence >= 0L && record.sequence > lastSequence + 1L) {
                clearLogs()
                needsResync = true
            }
            appendRaw(record)
            if (filter.matches(record)) {
                entries.addLast(TimelineEntry.Record(record))
                visibleLogs++
                version++
            } else {
                hidden++
            }
            lastSequence = maxOf(lastSequence, record.sequence)
        }
    }

    fun updateFilter(filter: DisplayFilter) {
        this.filter = filter
    }

    fun clearResync() {
        needsResync = false
    }

    fun resetFilter() {
        if (filter.isUnfiltered) return
        filter = DisplayFilter()
        version++
    }

    fun addInput(command: String) {
        addText(Component.literal("> $command"), TimelinePalette.INPUT)
    }

    fun addNote(text: Component, error: Boolean = false) {
        addText(text, if (error) TimelinePalette.ERROR else TimelinePalette.NOTE)
    }

    fun addResult(result: ServerCommandResult) {
        when (result.status) {
            CommandResultStatus.EXECUTED -> {
                if (result.feedback.isEmpty()) {
                    addText(
                        Component.translatable("console.$MOD_ID.command.result.success", result.value),
                        TimelinePalette.NOTE,
                    )
                } else {
                    result.feedback.forEach { addText(indent(it), TimelinePalette.NOTE) }
                }
            }
            CommandResultStatus.FAILED -> {
                if (result.feedback.isEmpty()) {
                    addText(Component.translatable("console.$MOD_ID.command.result.failed"), TimelinePalette.ERROR)
                } else {
                    result.feedback.forEach { addText(indent(it), TimelinePalette.ERROR) }
                }
            }
            CommandResultStatus.DENIED -> addText(
                Component.translatable("console.$MOD_ID.command.result.denied"),
                TimelinePalette.ERROR,
            )
            CommandResultStatus.UNKNOWN -> addText(
                Component.translatable("console.$MOD_ID.command.result.unknown"),
                TimelinePalette.ERROR,
            )
        }
    }

    fun clearLogs() {
        raw.clear()
        rawCharacters = 0L
        entries.removeAll { it is TimelineEntry.Record }
        visibleLogs = 0
        hidden = 0L
        lastSequence = -1L
        version++
        revision++
    }

    fun clearAll() {
        raw.clear()
        entries.clear()
        rawCharacters = 0L
        commandCharacters = 0
        visibleLogs = 0
        dropped = 0L
        hidden = 0L
        lastSequence = -1L
        filter = DisplayFilter()
        version++
        revision++
    }

    fun snapshot(): LogSnapshot {
        val records = raw.toList()
        if (records.isEmpty()) return LogSnapshot(emptyList(), -1L, -1L)
        val pages = ArrayList<LogPage>((records.size + LOG_PAGE_SIZE - 1) / LOG_PAGE_SIZE)
        var index = 0
        var pageNumber = 0L
        while (index < records.size) {
            val count = minOf(LOG_PAGE_SIZE, records.size - index)
            val slots = arrayOfNulls<LogRecord>(LOG_PAGE_SIZE)
            for (offset in 0 until count) slots[offset] = records[index + offset]
            pages += LogPage(pageNumber++, slots, count)
            index += count
        }
        return LogSnapshot(pages, records.first().sequence, records.last().sequence)
    }

    fun filterText(): String {
        val current = filter
        val level = current.level
        val keyword = current.keyword
        val rule = when {
            level != null && keyword != null ->
                Component.translatable("console.$MOD_ID.filter.both", level.label, keyword).string
            level != null ->
                Component.translatable("console.$MOD_ID.filter.level", level.label).string
            keyword != null ->
                Component.translatable("console.$MOD_ID.filter.keyword", keyword).string
            else -> Component.translatable("console.$MOD_ID.filter.none").string
        }
        val counters = Component.translatable(
            "console.$MOD_ID.mixed.counters",
            visibleLogCount(),
            hidden,
        ).string
        return "$rule · $counters"
    }

    fun visibleLogCount(): Int = visibleLogs

    private fun appendRaw(record: LogRecord) {
        raw.addLast(record)
        rawCharacters += record.textSize
        var evictedSequence = -1L
        while (raw.size > MAX_RAW_RECORDS || rawCharacters > MAX_RAW_CHARACTERS) {
            val removed = raw.removeFirst()
            rawCharacters -= removed.textSize
            evictedSequence = maxOf(evictedSequence, removed.sequence)
        }
        if (evictedSequence < 0L) return
        val iterator = entries.iterator()
        var pruned = false
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (entry !is TimelineEntry.Record) continue
            if (entry.record.sequence > evictedSequence) break
            iterator.remove()
            visibleLogs--
            pruned = true
        }
        if (pruned) {
            version++
            revision++
        }
    }

    private fun addText(text: Component, color: Int) {
        entries.addLast(TimelineEntry.Text(text, color))
        commandCharacters += text.string.length
        var trimmed = false
        while (entries.count { it is TimelineEntry.Text } > MAX_COMMAND_ENTRIES) {
            val removed = entries.firstOrNull { it is TimelineEntry.Text } ?: break
            entries.remove(removed)
            commandCharacters -= (removed as TimelineEntry.Text).text.string.length
            trimmed = true
        }
        while (commandCharacters > MAX_COMMAND_CHARACTERS && entries.any { it is TimelineEntry.Text }) {
            val removed = entries.firstOrNull { it is TimelineEntry.Text } ?: break
            entries.remove(removed)
            commandCharacters -= (removed as TimelineEntry.Text).text.string.length
            trimmed = true
        }
        if (trimmed) revision++
        version++
    }

    private fun indent(text: Component): Component =
        if (text.string.isEmpty()) text else Component.literal("  ").append(text)

    private companion object {
        const val MAX_RAW_RECORDS = 10_000
        const val MAX_RAW_CHARACTERS = 16L * 1024L * 1024L
        const val MAX_COMMAND_ENTRIES = 200
        const val MAX_COMMAND_CHARACTERS = 20_000
    }
}
