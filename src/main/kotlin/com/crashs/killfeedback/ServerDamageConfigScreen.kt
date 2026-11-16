package com.crashs.killfeedback

import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.Screen
import net.minecraft.client.gui.widget.ButtonWidget
import net.minecraft.text.Text
import net.minecraft.util.Formatting

/**
 * Standalone configuration panel for server-side damage TextDisplay control.
 */
class ServerDamageConfigScreen(
    private val parent: Screen?,
) : Screen(Text.literal("服务器伤害显示管理")) {

    private var draft = KillFeedbackConfigManager.config.copy()
    private val rowHeight = 30
    private val contentTop = 40

    override fun init() {
        clearChildren()
        labels.clear()
        val labelX = 16
        val controlWidth = 140
        val controlX = width - controlWidth - 16
        val bottomY = height - 24

        addToggle(
            "启用服务器伤害管理",
            "识别服务器生成的十种元素伤害 TextDisplay。",
            draft.serverDamageEnabled,
            contentTop,
            labelX,
            controlX,
            controlWidth,
        ) { draft.serverDamageEnabled = it }

        addToggle(
            "背景透明",
            "移除识别到的服务器伤害 TextDisplay 背景。",
            draft.serverDamageBackground,
            contentTop + rowHeight,
            labelX,
            controlX,
            controlWidth,
        ) { draft.serverDamageBackground = it }

        addToggle(
            "移除伤害显示",
            "拦截并丢弃识别到的服务器伤害 TextDisplay。",
            draft.serverDamageRemove,
            contentTop + rowHeight * 2,
            labelX,
            controlX,
            controlWidth,
        ) { draft.serverDamageRemove = it }

        addToggle(
            "字体阴影",
            "给服务器伤害文本添加阴影。",
            draft.serverDamageShadow,
            contentTop + rowHeight * 3,
            labelX,
            controlX,
            controlWidth,
        ) { draft.serverDamageShadow = it }

        addToggle(
            "玩家受伤文本（-n）",
            "将服务器生成的负数玩家受伤 TextDisplay 纳入本次服务端伤害显示控制。",
            draft.serverDamagePlayerHurt,
            contentTop + rowHeight * 4,
            labelX,
            controlX,
            controlWidth,
        ) { draft.serverDamagePlayerHurt = it }


        addDrawableChild(
            ButtonWidget.builder(Text.literal("保存")) {
                KillFeedbackConfigManager.apply(draft)
                close()
            }.dimensions(16, bottomY, 70, 18).build(),
        )
        addDrawableChild(
            ButtonWidget.builder(Text.literal("取消")) { close() }
                .dimensions(width / 2 - 35, bottomY, 70, 18).build(),
        )
        addDrawableChild(
            ButtonWidget.builder(Text.literal("恢复默认")) {
                draft.serverDamageEnabled = false
                draft.serverDamageBackground = false
                draft.serverDamageRemove = false
                draft.serverDamageShadow = false
                draft.serverDamagePlayerHurt = false
                init()
            }.dimensions(width - 86, bottomY, 70, 18).build(),
        )
    }

    private fun addToggle(
        title: String,
        description: String,
        initial: Boolean,
        y: Int,
        labelX: Int,
        controlX: Int,
        controlWidth: Int,
        onChange: (Boolean) -> Unit,
    ) {
        lateinit var button: ButtonWidget
        fun stateText(value: Boolean): Text =
            if (value) Text.literal("开").formatted(Formatting.GREEN)
            else Text.literal("关").formatted(Formatting.RED)

        button = ButtonWidget.builder(stateText(initial)) {
            val next = button.message.string != "开"
            onChange(next)
            button.setMessage(stateText(next))
        }.dimensions(controlX, y, controlWidth, 18).build()
        addDrawableChild(button)

        // Labels are drawn in render, because widgets are rendered below custom text.
        labels += Label(title, description, y, labelX)
    }

    private data class Label(val title: String, val description: String, val y: Int, val x: Int)
    private val labels = mutableListOf<Label>()

    override fun render(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 8, 0xFFFFFFFF.toInt())
        labels.forEach { label ->
            context.drawText(textRenderer, Text.literal(label.title), 16, label.y + 3, 0xFFFFFFFF.toInt(), false)
            context.drawText(textRenderer, Text.literal(label.description), 16, label.y + 14, 0xFFAAAAAA.toInt(), false)
        }
        super.render(context, mouseX, mouseY, delta)
    }

    override fun close() {
        MinecraftClient.getInstance().setScreen(parent)
    }

    override fun shouldPause(): Boolean = false
}
