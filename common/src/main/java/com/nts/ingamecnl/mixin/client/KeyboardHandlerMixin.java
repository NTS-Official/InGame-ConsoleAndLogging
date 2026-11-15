package com.nts.ingamecnl.mixin.client;

import com.nts.ingamecnl.client.ConsoleClient;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = KeyboardHandler.class, remap = false)
abstract class KeyboardHandlerMixin {
    @Inject(method = "keyPress(JILnet/minecraft/client/input/KeyEvent;)V", at = @At("HEAD"), cancellable = true, remap = false)
    private void consumeConsoleKey(long window, int action, KeyEvent event, CallbackInfo callback) {
        if (ConsoleClient.INSTANCE.consumeKey(window, action, event)) {
            callback.cancel();
        }
    }

    @Inject(method = "charTyped(JLnet/minecraft/client/input/CharacterEvent;)V", at = @At("HEAD"), cancellable = true, remap = false)
    private void consumeConsoleCharacter(long window, CharacterEvent event, CallbackInfo callback) {
        if (ConsoleClient.INSTANCE.consumeCharacter(window)) {
            callback.cancel();
        }
    }
}
