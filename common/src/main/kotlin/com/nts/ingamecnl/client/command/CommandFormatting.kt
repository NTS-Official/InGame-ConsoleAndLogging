package com.nts.ingamecnl.client.command

import com.mojang.brigadier.ParseResults
import net.minecraft.ChatFormatting
import net.minecraft.client.multiplayer.ClientSuggestionProvider
import net.minecraft.network.chat.Style
import net.minecraft.util.FormattedCharSequence

/**
 * 复刻原版 `CommandSuggestions.formatText` 的行内着色：字面量灰、参数按序轮换彩色、
 * 尚未解析的剩余部分红。控制台自带的 formatter 先于原版注册，必须自行实现这套规则。
 */
internal object CommandFormatting {
    private val UNPARSED_STYLE = Style.EMPTY.withColor(ChatFormatting.RED)
    private val LITERAL_STYLE = Style.EMPTY.withColor(ChatFormatting.GRAY)
    private val ARGUMENT_STYLES = listOf(
        ChatFormatting.AQUA,
        ChatFormatting.YELLOW,
        ChatFormatting.GREEN,
        ChatFormatting.LIGHT_PURPLE,
        ChatFormatting.GOLD,
    ).map { Style.EMPTY.withColor(it) }

    fun format(parse: ParseResults<ClientSuggestionProvider>?, text: String, offset: Int): FormattedCharSequence {
        if (parse == null) return FormattedCharSequence.forward(text, Style.EMPTY)
        val parts = ArrayList<FormattedCharSequence>()
        var cursor = 0
        var index = -1
        for (argument in parse.context.lastChild.arguments.values) {
            index = if (index + 1 >= ARGUMENT_STYLES.size) 0 else index + 1
            val start = (argument.range.start - offset).coerceAtLeast(0)
            if (start >= text.length) break
            val end = (argument.range.end - offset).coerceAtMost(text.length)
            if (end <= 0) continue
            if (start > cursor) parts.add(FormattedCharSequence.forward(text.substring(cursor, start), LITERAL_STYLE))
            if (end > start) parts.add(FormattedCharSequence.forward(text.substring(start, end), ARGUMENT_STYLES[index]))
            cursor = end
        }
        val reader = parse.reader
        if (reader.canRead()) {
            val start = (reader.cursor - offset).coerceAtLeast(0)
            if (start < text.length) {
                val end = (start + reader.remainingLength).coerceAtMost(text.length)
                if (start > cursor) parts.add(FormattedCharSequence.forward(text.substring(cursor, start), LITERAL_STYLE))
                if (end > start) parts.add(FormattedCharSequence.forward(text.substring(start, end), UNPARSED_STYLE))
                cursor = end
            }
        }
        if (cursor < text.length) parts.add(FormattedCharSequence.forward(text.substring(cursor), LITERAL_STYLE))
        return if (parts.isEmpty()) FormattedCharSequence.forward(text, Style.EMPTY) else FormattedCharSequence.composite(parts)
    }
}
