package com.crashs.killfeedback.mixin

import com.crashs.killfeedback.KillFeedbackHud
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.hud.InGameHud
import net.minecraft.client.render.RenderTickCounter
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo

@Mixin(InGameHud::class)
abstract class InGameHudMixin {

    @Inject(method = ["render"], at = [At("TAIL")])
    private fun renderKillFeedback(context: DrawContext, tickCounter: RenderTickCounter, callbackInfo: CallbackInfo) {
        KillFeedbackHud.render(context)
    }
}
