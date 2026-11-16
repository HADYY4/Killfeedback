package com.crashs.killfeedback

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.StringArgumentType
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource
import net.minecraft.client.gui.screen.Screen
import net.minecraft.text.Text

object KillFeedbackCommands {

    fun register(dispatcher: CommandDispatcher<FabricClientCommandSource>) {
        dispatcher.register(
            ClientCommandManager.literal("crashs")
                .then(
                    ClientCommandManager.literal("ui").executes {
                        KillFeedbackMod.requestScreen { parent -> CrashsLegacyScreen(parent) }
                        1
                    },
                )
                .then(
                    ClientCommandManager.literal("imgui").executes {
                        KillFeedbackMod.requestScreen { parent -> CrashsImGuiScreen(parent) }
                        1
                    },
                )
                .then(
                    ClientCommandManager.literal("debugloot")
                        .then(
                            ClientCommandManager.argument("message", StringArgumentType.greedyString())
                                .executes { context ->
                                    val input = StringArgumentType.getString(context, "message")
                                    val component = BroadcastManager.parseDebugComponent(input)
                                    val lines = if (component == null) {
                                        listOf("[DebugLoot] 文本组件解析失败")
                                    } else {
                                        BroadcastManager.debugMessage(component)
                                    }
                                    lines.forEach { line ->
                                        context.source.sendFeedback(Text.literal(line))
                                    }
                                    1
                                },
                        ),
                ),
        )
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
