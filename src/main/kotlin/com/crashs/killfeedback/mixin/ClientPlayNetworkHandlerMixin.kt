package com.crashs.killfeedback.mixin

import com.crashs.killfeedback.KillFeedbackMod
import com.crashs.killfeedback.serverdamage.ServerDamageDisplayManager
import net.minecraft.client.network.ClientPlayNetworkHandler
import net.minecraft.network.packet.s2c.play.EntitiesDestroyS2CPacket
import net.minecraft.network.packet.s2c.play.EntityTrackerUpdateS2CPacket
import net.minecraft.network.packet.s2c.play.GameMessageS2CPacket
import net.minecraft.network.packet.s2c.play.OverlayMessageS2CPacket
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo

@Mixin(ClientPlayNetworkHandler::class)
abstract class ClientPlayNetworkHandlerMixin {

    @Inject(method = ["onGameMessage"], at = [At("HEAD")], cancellable = true)
    private fun onGameMessage(packet: GameMessageS2CPacket, callbackInfo: CallbackInfo) {
        if (KillFeedbackMod.handleMessage(packet.content(), packet.overlay())) {
            callbackInfo.cancel()
        }
    }

    @Inject(method = ["onOverlayMessage"], at = [At("HEAD")], cancellable = true)
    private fun onOverlayMessage(packet: OverlayMessageS2CPacket, callbackInfo: CallbackInfo) {
        if (KillFeedbackMod.handleMessage(packet.text(), true)) {
            callbackInfo.cancel()
        }
    }

    @Inject(method = ["onEntityTrackerUpdate"], at = [At("HEAD")], cancellable = true, require = 0)
    private fun cancelServerDamage(packet: EntityTrackerUpdateS2CPacket, callbackInfo: CallbackInfo) {
        if (ServerDamageDisplayManager.shouldCancel(packet)) {
            callbackInfo.cancel()
        }
    }

    @Inject(method = ["onEntityTrackerUpdate"], at = [At("TAIL")], require = 0)
    private fun styleServerDamage(packet: EntityTrackerUpdateS2CPacket, callbackInfo: CallbackInfo) {
        ServerDamageDisplayManager.applyStyle(packet)
    }

    @Inject(method = ["onEntitiesDestroy"], at = [At("HEAD")], require = 0)
    private fun forgetServerDamage(packet: EntitiesDestroyS2CPacket, callbackInfo: CallbackInfo) {
        ServerDamageDisplayManager.forget(packet.entityIds)
    }
}
