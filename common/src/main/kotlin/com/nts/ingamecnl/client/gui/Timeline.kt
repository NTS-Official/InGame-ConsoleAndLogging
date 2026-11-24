package com.nts.ingamecnl.client.gui

import com.nts.ingamecnl.log.LogRecord
import net.minecraft.network.chat.Component

/** 所有模式共用的时间线条目：日志记录或纯文本行。 */
internal sealed interface TimelineEntry {
    data class Record(val record: LogRecord) : TimelineEntry

    data class Text(val text: Component, val color: Int) : TimelineEntry
}

/** 时间线数据源：只提供顺序与内容，排版与滚动统一由 [TimelineView] 负责。 */
internal interface TimelineSource {
    val size: Int

    val emptyTextKey: String

    /** 序号列宽度，没有日志记录的数据源返回 0。 */
    val numberWidth: Int

    /** 条目被替换或整体清空时递增，用于让视图失效重建。 */
    val revision: Long
        get() = 0L

    fun entryAt(index: Int): TimelineEntry
}

internal object TimelinePalette {
    const val EMPTY = 0xFF95A5B8.toInt()
    const val NUMBER = 0xFF6E7F93.toInt()
    const val INPUT = 0xFF8ED5BD.toInt()
    const val NOTE = 0xFFDCE6F2.toInt()
    const val ERROR = 0xFFFF7B72.toInt()
    const val MIXED = 0xFFFFC857.toInt()
}
