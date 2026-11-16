package com.crashs.killfeedback

import com.crashs.killfeedback.serverdamage.ServerDamageDisplayManager
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.minecraft.client.util.InputUtil
import net.minecraft.util.Identifier
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.screen.Screen
import net.minecraft.client.option.KeyBinding
import net.minecraft.client.world.ClientWorld
import net.minecraft.text.Text
import org.lwjgl.glfw.GLFW
import org.slf4j.LoggerFactory

class KillFeedbackMod : ClientModInitializer {

    companion object {
        const val MOD_ID = "crashs-legacy"
        val LOGGER = LoggerFactory.getLogger(MOD_ID)

        private var lastWorld: ClientWorld? = null
        private var pendingScreen: ((Screen?) -> Screen)? = null
        private lateinit var openUiKey: KeyBinding
        private data class PacketResult(val at: Long, val cancel: Boolean)
        private val processedPackets = LinkedHashMap<Long, PacketResult>()
        private const val PACKET_DEDUPE_MS = 2_000L

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
        fun handleMessage(
            message: Text,
            overlay: Boolean,
            packetId: Int,
            handlerId: Int,
        ): Boolean {
            val config = KillFeedbackConfigManager.config
            if (!config.enabled) return false
            if (overlay && !config.includeOverlay) return false

            val duplicate = duplicateResult(packetId, handlerId)
            if (duplicate != null) {
                return duplicate
            }

            if (BroadcastManager.handleMessage(message)) {
                rememberResult(packetId, handlerId, true)
                return true
            }

            val reward = KillRewardParser.parse(message.string, config.pattern)
            if (reward == null) {
                rememberResult(packetId, handlerId, false)
                return false
            }
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
                rememberResult(packetId, handlerId, true)
                return true
            }
            rememberResult(packetId, handlerId, false)
            return false
        }

        /**
         * Returns the first pass's cancellation result for a duplicate packet.
         *
         * The packet is delivered twice by some client paths. Returning the first result keeps
         * cancellation tied to the message handling, instead of cancelling every duplicate
         * merely because the global intercept option is enabled.
         */
        private fun duplicateResult(packetId: Int, handlerId: Int): Boolean? {
            val now = System.currentTimeMillis()
            val key = packetKey(packetId, handlerId)
            synchronized(processedPackets) {
                processedPackets.entries.removeIf { now - it.value.at > PACKET_DEDUPE_MS }
                val previous = processedPackets[key]
                if (previous != null && now - previous.at <= PACKET_DEDUPE_MS) {
                    return previous.cancel
                }
                processedPackets[key] = PacketResult(now, false)
                return null
            }
        }

        private fun rememberResult(packetId: Int, handlerId: Int, cancel: Boolean) {
            val key = packetKey(packetId, handlerId)
            synchronized(processedPackets) {
                processedPackets[key] = PacketResult(System.currentTimeMillis(), cancel)
            }
        }

        private fun packetKey(packetId: Int, handlerId: Int): Long =
            (handlerId.toLong() shl 32) xor (packetId.toLong() and 0xFFFFFFFFL)
    }

    override fun onInitializeClient() {
        KillFeedbackConfigManager.load()

        ClientPlayConnectionEvents.JOIN.register { _, _, _ ->
            resetConnectionSession()
        }

        ClientPlayConnectionEvents.DISCONNECT.register { _, _ ->
            resetConnectionSession()
            lastWorld = null
        }

        openUiKey = KeyBindingHelper.registerKeyBinding(
            KeyBinding(
                "key.crashs-legacy.ui",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_F7,
                KeyBinding.Category.create(Identifier.of("crashs-legacy", "main")),
            ),
        )

        ClientTickEvents.END_CLIENT_TICK.register { client ->
            while (openUiKey.wasPressed()) {
                requestScreen { parent -> CrashsImGuiScreen(parent) }
            }

            pendingScreen?.let { factory ->
                pendingScreen = null
                client.setScreen(factory(client.currentScreen))
            }

            ServerDamageDisplayManager.syncState()
            BroadcastManager.tick()

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

    private fun resetConnectionSession() {
        resetSession()
        CoinsSession.reset()
        BroadcastManager.clear()
    }
}
