package com.nts.ingamecnl.client.gui

internal enum class ConsoleMode(val prefix: String, val statusKey: String) {
    VIEW_ONLY("", "console.ingamecnl.status.view_only"),
    LOG("$", "console.ingamecnl.status.log"),
    COMMAND("#", "console.ingamecnl.status.command"),
}

internal enum class ModeSwitchGesture {
    READY,
    HELD,
}
