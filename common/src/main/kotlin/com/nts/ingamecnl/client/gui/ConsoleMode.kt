package com.nts.ingamecnl.client.gui

import com.nts.ingamecnl.MOD_ID

internal enum class ConsoleMode(val prefix: String, val statusKey: String) {
    VIEW_ONLY("", "console.$MOD_ID.status.view_only"),
    LOG("$", "console.$MOD_ID.status.log"),
    COMMAND("#", "console.$MOD_ID.status.command"),
}

internal enum class ModeSwitchGesture {
    READY,
    HELD,
}
