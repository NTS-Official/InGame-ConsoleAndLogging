package com.nts.ingamecnl.client.mixin

import com.nts.ingamecnl.client.ConsoleClient.consumeMouse
import net.minecraft.client.MouseHandler
import net.minecraft.client.input.MouseButtonInfo
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo

@Mixin(value = [MouseHandler::class], remap = false)
internal abstract class MouseHandlerMixin {
    @Inject(
        method = ["onButton(JLnet/minecraft/client/input/MouseButtonInfo;I)V"],
        at = [At("HEAD")],
        cancellable = true,
        remap = false
    )
    private fun consumeConsoleMouse(window: Long, buttonInfo: MouseButtonInfo, action: Int, callback: CallbackInfo) {
        if (consumeMouse(window, buttonInfo, action)) {
            callback.cancel()
        }
    }
}
