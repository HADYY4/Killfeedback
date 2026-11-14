package com.crashs.killfeedback

import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.Screen
import net.minecraft.client.gui.widget.ButtonWidget
import net.minecraft.text.Text

/**
 * Top-level feature selector for Crashs Legacy.
 */
class CrashsLegacyScreen(
    private val parent: Screen?,
) : Screen(Text.literal("Crashs Legacy")) {

    override fun init() {
        val centerX = width / 2
        addDrawableChild(
            ButtonWidget.builder(Text.literal("KillFeedback 击杀反馈")) {
                MinecraftClient.getInstance().setScreen(KillFeedbackConfigScreen(this))
            }.dimensions(centerX - 100, height / 2 - 48, 200, 24).build(),
        )
        addDrawableChild(
            ButtonWidget.builder(Text.literal("服务器伤害显示管理")) {
                MinecraftClient.getInstance().setScreen(ServerDamageConfigScreen(this))
            }.dimensions(centerX - 100, height / 2 - 10, 200, 24).build(),
        )
        addDrawableChild(
            ButtonWidget.builder(Text.literal("关闭")) {
                close()
            }.dimensions(centerX - 50, height / 2 + 42, 100, 20).build(),
        )
    }

    override fun render(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, height / 2 - 80, 0xFFFFFFFF.toInt())
        context.drawCenteredTextWithShadow(
            textRenderer,
            Text.literal("KillFeedback 与服务器伤害显示"),
            width / 2,
            height / 2 - 66,
            0xFFAAAAAA.toInt(),
        )
        super.render(context, mouseX, mouseY, delta)
    }

    override fun close() {
        MinecraftClient.getInstance().setScreen(parent)
    }

    override fun shouldPause(): Boolean = false
}
