package com.nts.ingamecnl.mixin.client;

import com.nts.ingamecnl.client.ConsoleClient;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = MouseHandler.class, remap = false)
abstract class MouseHandlerMixin {
    @Inject(method = "onButton(JLnet/minecraft/client/input/MouseButtonInfo;I)V", at = @At("HEAD"), cancellable = true, remap = false)
    private void consumeConsoleMouse(long window, MouseButtonInfo buttonInfo, int action, CallbackInfo callback) {
        if (ConsoleClient.INSTANCE.consumeMouse(window, buttonInfo, action)) {
            callback.cancel();
        }
    }
}
