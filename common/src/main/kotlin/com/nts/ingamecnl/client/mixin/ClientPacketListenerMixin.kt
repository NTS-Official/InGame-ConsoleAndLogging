package com.nts.ingamecnl.client.mixin

import com.mojang.brigadier.CommandDispatcher
import com.llamalad7.mixinextras.injector.ModifyReturnValue
import com.nts.ingamecnl.client.ConsoleClient
import com.nts.ingamecnl.client.command.CommandTrees
import com.nts.ingamecnl.client.gui.ConsoleScreen
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientPacketListener
import net.minecraft.client.multiplayer.ClientSuggestionProvider
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At

/**
 * 原版候选列表固定从 `ClientPacketListener.getCommands()` 取命令树。
 * 控制台在日志模式下用自己的本地命令树、混合模式下按前缀在本地与服务端命令树之间切换，
 * 因此这里按当前模式返回应当使用的命令树，其余场景保持原版行为。
 */
@Mixin(value = [ClientPacketListener::class], remap = false)
internal abstract class ClientPacketListenerMixin {
    @ModifyReturnValue(
        method = ["getCommands()Lcom/mojang/brigadier/CommandDispatcher;"],
        at = [At(value = "RETURN", remap = false)],
        remap = false,
    )
    private fun selectCommandTree(
        original: CommandDispatcher<ClientSuggestionProvider>,
    ): CommandDispatcher<ClientSuggestionProvider> {
        val minecraft = Minecraft.getInstance()
        val screen = minecraft.screen as? ConsoleScreen ?: return original
        if (!screen.acceptsTextInput) return original
        return CommandTrees.select(
            screen.currentMode,
            ConsoleClient.isMixedMode(),
            minecraft.isSingleplayer,
            screen.currentInput,
            original,
        )
    }
}
