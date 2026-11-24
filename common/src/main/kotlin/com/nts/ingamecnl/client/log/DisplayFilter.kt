package com.nts.ingamecnl.client.log
import com.nts.ingamecnl.log.LogRecord

import com.nts.ingamecnl.log.LogLevel

data class DisplayFilter(
    val level: LogLevel? = null,
    val keyword: String? = null,
) {
    val isUnfiltered: Boolean
        get() = level == null && keyword == null

    fun matches(record: LogRecord): Boolean {
        if (level != null && record.level != level) return false
        val keyword = keyword ?: return true
        return record.message.contains(keyword, ignoreCase = true) ||
            record.throwableText?.contains(keyword, ignoreCase = true) == true
    }
}
