package com.nts.ingamecnl.client.mixin

import com.nts.ingamecnl.client.ConsoleClient.consumeCharacter
import com.nts.ingamecnl.client.ConsoleClient.consumeKey
import net.minecraft.client.KeyboardHandler
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo

@Mixin(value = [KeyboardHandler::class], remap = false)
internal abstract class KeyboardHandlerMixin {
    @Inject(
        method = ["keyPress(JILnet/minecraft/client/input/KeyEvent;)V"],
        at = [At("HEAD")],
        cancellable = true,
        remap = false
    )
    private fun consumeConsoleKey(window: Long, action: Int, event: KeyEvent, callback: CallbackInfo) {
        if (consumeKey(window, action, event)) {
            callback.cancel()
        }
    }

    @Inject(
        method = ["charTyped(JLnet/minecraft/client/input/CharacterEvent;)V"],
        at = [At("HEAD")],
        cancellable = true,
        remap = false
    )
    private fun consumeConsoleCharacter(window: Long, event: CharacterEvent?, callback: CallbackInfo) {
        if (consumeCharacter(window)) {
            callback.cancel()
        }
    }
}
