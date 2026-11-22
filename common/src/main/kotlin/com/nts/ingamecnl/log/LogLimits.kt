package com.nts.ingamecnl.log

object LogLimits {
    const val DEFAULT_RECORD_CAPACITY: Long = Int.MAX_VALUE.toLong()
    const val MAX_RECORD_CAPACITY: Long = Long.MAX_VALUE

    const val MIN_TEXT_MIB: Long = 1
    const val DEFAULT_TEXT_MIB = 128
    const val MAX_TEXT_MIB: Long = 2048

    const val DEFAULT_TEXT_CAPACITY: Long = DEFAULT_TEXT_MIB * 1024L * 1024L
    const val MAX_TEXT_CAPACITY: Long = MAX_TEXT_MIB * 1024L * 1024L
}
