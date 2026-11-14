package com.crashs.killfeedback

import com.crashs.killfeedback.serverdamage.ServerDamageDisplayManager
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.screen.Screen
import net.minecraft.client.world.ClientWorld
import net.minecraft.text.Text
import org.slf4j.LoggerFactory

class KillFeedbackMod : ClientModInitializer {

    companion object {
        const val MOD_ID = "crashs-legacy"
        val LOGGER = LoggerFactory.getLogger(MOD_ID)

        private var lastWorld: ClientWorld? = null
        private var pendingScreen: ((Screen?) -> Screen)? = null

        /**
         * Called from the network-handler mixin.
         *
         * @return true when the message should be cancelled.
         */
        @JvmStatic
        fun requestScreen(factory: (Screen?) -> Screen) {
            pendingScreen = factory
        }

        @JvmStatic
        fun handleMessage(message: Text, overlay: Boolean): Boolean {
            val config = KillFeedbackConfigManager.config
            if (!config.enabled) return false
            if (overlay && !config.includeOverlay) return false

            val reward = KillRewardParser.parse(message.string, config.pattern) ?: return false
            val comboCount = KillFeedState.add(reward)

            if (config.sound) {
                val group = ComboGroup.byName(config.comboGroup)
                val postMode = PostComboMode.byName(config.postMode)
                val specific = PostComboSound.byName(config.specificSound)
                MinecraftClient.getInstance().execute {
                    KillFeedbackSoundPlayer.play(
                        comboCount = comboCount,
                        group = group,
                        postMode = postMode,
                        specific = specific,
                        volume = config.soundVolume,
                        pitch = config.soundPitch,
                    )
                }
            }

            if (config.intercept) {
                LOGGER.debug(
                    "Intercepted reward: {} coins from {}",
                    KillRewardParser.formatCoins(reward.coins),
                    reward.source,
                )
                return true
            }
            return false
        }
    }

    override fun onInitializeClient() {
        KillFeedbackConfigManager.load()

        ClientPlayConnectionEvents.JOIN.register { _, _, _ ->
            resetSession()
        }

        ClientPlayConnectionEvents.DISCONNECT.register { _, _ ->
            resetSession()
            lastWorld = null
        }

        ClientTickEvents.END_CLIENT_TICK.register { client ->
            pendingScreen?.let { factory ->
                pendingScreen = null
                client.setScreen(factory(client.currentScreen))
            }

            ServerDamageDisplayManager.syncState()

            val world = client.world
            if (world !== lastWorld) {
                if (lastWorld != null) {
                    resetSession()
                }
                lastWorld = world
            }
        }

        ClientCommandRegistrationCallback.EVENT.register { dispatcher, _ ->
            KillFeedbackCommands.register(dispatcher)
        }

        LOGGER.info("Crashs Legacy loaded for Minecraft 1.21.11")
    }

    private fun resetSession() {
        KillFeedState.reset()
        KillFeedbackSoundPlayer.reset()
        ServerDamageDisplayManager.restoreAll()
        ServerDamageDisplayManager.clearWorld()
    }
}
