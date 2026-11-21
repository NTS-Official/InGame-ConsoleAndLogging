package com.nts.ingamecnl.client.mixin

import com.llamalad7.mixinextras.injector.wrapoperation.Operation
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation
import com.llamalad7.mixinextras.sugar.Local
import com.nts.ingamecnl.client.ConsoleClient.addScreen
import com.nts.ingamecnl.client.ConsoleClient.initializeScreen
import com.nts.ingamecnl.client.ConsoleClient.removeScreen
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At

@Mixin(value = [Minecraft::class], remap = false)
internal abstract class MinecraftScreenMixin {
    @WrapOperation(
        method = ["setScreen(Lnet/minecraft/client/gui/screens/Screen;)V"],
        at = [At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/Screen;removed()V", remap = false)],
        remap = false
    )
    private fun removeConsoleScreen(
        current: Screen,
        original: Operation<Void?>?,
        @Local(index = 1) replacement: Screen?
    ) {
        removeScreen(current, replacement)
    }

    @WrapOperation(
        method = ["setScreen(Lnet/minecraft/client/gui/screens/Screen;)V"],
        at = [At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/Screen;added()V", remap = false)],
        remap = false
    )
    private fun addConsoleScreen(next: Screen, original: Operation<Void?>?) {
        addScreen(next)
    }

    @WrapOperation(
        method = ["setScreen(Lnet/minecraft/client/gui/screens/Screen;)V"],
        at = [At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/Screen;init(II)V", remap = false)],
        remap = false
    )
    private fun initializeConsoleScreen(next: Screen, width: Int, height: Int, original: Operation<Void?>?) {
        initializeScreen(next, width, height)
    }
}
