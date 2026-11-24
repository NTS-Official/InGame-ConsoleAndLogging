package com.nts.ingamecnl.client.gui

import com.nts.ingamecnl.MOD_ID

import com.nts.ingamecnl.client.log.LogCapture

/** 把客户端日志历史适配为时间线数据源。 */
internal object LogTimelineSource : TimelineSource {
    override val size: Int
        get() = records().size

    override val emptyTextKey: String
        get() = if (LogCapture.filter.isUnfiltered) {
            "console.$MOD_ID.empty"
        } else {
            "console.$MOD_ID.empty.filtered"
        }

    override val numberWidth: Int
        get() = records().lastOrNull()
            ?.let { (it.sequence + 1).toString().length.coerceAtLeast(2) }
            ?: 2

    override val revision: Long
        get() = LogCapture.history.structureRevision

    override fun entryAt(index: Int): TimelineEntry = TimelineEntry.Record(records()[index])

    private fun records() = LogCapture.history.visibleRecords()
}
