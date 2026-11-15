package com.nts.ingamecnl.mixin.client;

import com.nts.ingamecnl.client.ConsoleClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = Minecraft.class, remap = false)
abstract class MinecraftScreenMixin {
    @Redirect(
            method = "setScreen(Lnet/minecraft/client/gui/screens/Screen;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/Screen;removed()V", remap = false),
            remap = false
    )
    private void removeConsoleScreen(Screen current, Screen replacement) {
        ConsoleClient.INSTANCE.removeScreen(current, replacement);
    }

    @Redirect(
            method = "setScreen(Lnet/minecraft/client/gui/screens/Screen;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/Screen;added()V", remap = false),
            remap = false
    )
    private void addConsoleScreen(Screen next) {
        ConsoleClient.INSTANCE.addScreen(next);
    }

    @Redirect(
            method = "setScreen(Lnet/minecraft/client/gui/screens/Screen;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/Screen;init(II)V", remap = false),
            remap = false
    )
    private void initializeConsoleScreen(Screen next, int width, int height) {
        ConsoleClient.INSTANCE.initializeScreen(next, width, height);
    }
}
