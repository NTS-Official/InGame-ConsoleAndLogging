package com.nts.ingamecnl.client.gui

internal class ConsoleState {
    var mode = ConsoleMode.VIEW_ONLY
    private var logDraft = ""
    private var commandDraft = ""

    var draft: String
        get() = when (mode) {
            ConsoleMode.LOG -> logDraft
            ConsoleMode.COMMAND -> commandDraft
            ConsoleMode.VIEW_ONLY -> ""
        }
        set(value) {
            when (mode) {
                ConsoleMode.LOG -> logDraft = value
                ConsoleMode.COMMAND -> commandDraft = value
                ConsoleMode.VIEW_ONLY -> Unit
            }
        }
}
