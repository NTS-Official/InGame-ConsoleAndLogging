package com.nts.ingamecnl.client.mixin

import com.mojang.brigadier.ParseResults
import net.minecraft.client.gui.components.CommandSuggestions
import net.minecraft.client.multiplayer.ClientSuggestionProvider
import net.minecraft.util.FormattedCharSequence
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.gen.Accessor

/**
 * 原版只在输入文本变化时才清空 `currentParse`，切换命令树后文本不变会沿用旧树的解析结果，
 * 候选因此会错位。这里暴露该字段，供切换命令树时主动失效。
 *
 * 同时暴露解析结果与用法提示行：控制台自带 formatter（先于原版注册），
 * 需要按原版规则自行着色，并在原版位置之外绘制错误/用法提示条。
 */
@Mixin(value = [CommandSuggestions::class], remap = false)
internal interface CommandSuggestionsAccessor {
    @Accessor("currentParse")
    fun setCurrentParse(parse: ParseResults<ClientSuggestionProvider>?)

    @Accessor("currentParse")
    fun getCurrentParse(): ParseResults<ClientSuggestionProvider>?

    @Accessor("commandUsage")
    fun getCommandUsage(): List<FormattedCharSequence>
}
