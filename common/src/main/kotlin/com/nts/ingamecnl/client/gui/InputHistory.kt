package com.nts.ingamecnl.client.gui

internal class InputHistory(private val limit: Int = 200) {
    private val entries = ArrayList<String>(limit)

    val size: Int
        get() = entries.size

    operator fun get(index: Int): String = entries[index]

    fun add(input: String) {
        if (input.isBlank()) return
        if (entries.lastOrNull() == input) return
        entries += input
        if (entries.size > limit) entries.removeAt(0)
    }
}
