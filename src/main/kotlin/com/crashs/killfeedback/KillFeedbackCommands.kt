package com.crashs.killfeedback

import com.mojang.brigadier.CommandDispatcher
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource
import net.minecraft.client.gui.screen.Screen

object KillFeedbackCommands {

    fun register(dispatcher: CommandDispatcher<FabricClientCommandSource>) {
        registerUi(dispatcher, "crashs") { parent -> CrashsLegacyScreen(parent) }
        registerUi(dispatcher, "killfeedback") { parent -> KillFeedbackConfigScreen(parent) }
        registerUi(dispatcher, "damagetext") { parent -> ServerDamageConfigScreen(parent) }
    }

    private fun registerUi(
        dispatcher: CommandDispatcher<FabricClientCommandSource>,
        name: String,
        factory: (Screen?) -> Screen,
    ) {
        dispatcher.register(
            ClientCommandManager.literal(name)
                .then(
                    ClientCommandManager.literal("ui").executes {
                        KillFeedbackMod.requestScreen(factory)
                        1
                    },
                ),
        )
    }
}
