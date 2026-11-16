package com.crashs.killfeedback

import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.Screen
import net.minecraft.client.gui.widget.ButtonWidget
import net.minecraft.text.Text
import net.minecraft.util.Formatting

class CoinsInfoConfigScreen(private val parent: Screen?) : Screen(Text.literal("CoinsInfo")) {

    private var draft = KillFeedbackConfigManager.config.copy()
    private val labels = mutableListOf<Triple<String, String, Int>>()
    private val rowHeight = 26
    private val contentTop = 30

    override fun init() {
        clearChildren()
        labels.clear()
        val labelX = 16
        val controlWidth = 150
        val controlX = width - controlWidth - 16
        addToggle("启用 CoinsInfo", "独立的会话金币 HUD。", draft.coinsInfoEnabled, contentTop, labelX, controlX, controlWidth) { draft.coinsInfoEnabled = it }
        addToggle("双行显示", "第一行总金币，第二行平均值。", draft.coinsInfoTwoLines, contentTop + rowHeight, labelX, controlX, controlWidth) { draft.coinsInfoTwoLines = it }
        addToggle("显示总金币", "显示会话累计金币。", draft.coinsInfoShowTotal, contentTop + rowHeight * 2, labelX, controlX, controlWidth) { draft.coinsInfoShowTotal = it }
        addToggle("显示平均值", "显示 avg/min 或 avg/h。", draft.coinsInfoShowAverage, contentTop + rowHeight * 3, labelX, controlX, controlWidth) { draft.coinsInfoShowAverage = it }
        addToggle("数字滚动", "数字以缓动方式滚动。", draft.coinsInfoScroll, contentTop + rowHeight * 4, labelX, controlX, controlWidth) { draft.coinsInfoScroll = it }

        addDrawableChild(ButtonWidget.builder(Text.literal("平均单位：${if (draft.coinsInfoAverageUnit.equals("HOUR", true)) "avg/h" else "avg/min"}")) {
            draft.coinsInfoAverageUnit = if (draft.coinsInfoAverageUnit.equals("HOUR", true)) "MINUTE" else "HOUR"
            init()
        }.dimensions(controlX, contentTop + rowHeight * 5, controlWidth, 18).build())
        labels += Triple("平均单位", "avg/min 或 avg/h。", contentTop + rowHeight * 5)

        addDrawableChild(ButtonWidget.builder(Text.literal("数字格式：${draft.coinsInfoNumberFormat}")) {
            draft.coinsInfoNumberFormat = when (draft.coinsInfoNumberFormat.uppercase()) {
                "RAW" -> "COMMA"
                "COMMA" -> "SHORT"
                else -> "RAW"
            }
            init()
        }.dimensions(controlX, contentTop + rowHeight * 6, controlWidth, 18).build())
        labels += Triple("数字格式", "原始 / 千分位 / 缩写。", contentTop + rowHeight * 6)

        val bottomY = height - 24
        addDrawableChild(ButtonWidget.builder(Text.literal("保存")) {
            KillFeedbackConfigManager.apply(draft)
            close()
        }.dimensions(16, bottomY, 70, 18).build())
        addDrawableChild(ButtonWidget.builder(Text.literal("取消")) { close() }.dimensions(width / 2 - 35, bottomY, 70, 18).build())
        addDrawableChild(ButtonWidget.builder(Text.literal("恢复默认")) {
            draft = KillFeedbackConfig().copy(broadcastEnabled = draft.broadcastEnabled)
            init()
        }.dimensions(width - 86, bottomY, 70, 18).build())
    }

    private fun addToggle(title: String, description: String, initial: Boolean, y: Int, labelX: Int, controlX: Int, controlWidth: Int, onChange: (Boolean) -> Unit) {
        lateinit var button: ButtonWidget
        fun stateText(value: Boolean): Text = if (value) Text.literal("开").formatted(Formatting.GREEN) else Text.literal("关").formatted(Formatting.RED)
        button = ButtonWidget.builder(stateText(initial)) {
            val next = button.message.string != "开"
            onChange(next)
            button.setMessage(stateText(next))
        }.dimensions(controlX, y, controlWidth, 18).build()
        addDrawableChild(button)
        labels += Triple(title, description, y)
    }

    override fun render(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 8, 0xFFFFFFFF.toInt())
        labels.forEach { (title, description, y) ->
            context.drawText(textRenderer, Text.literal(title), 16, y + 3, 0xFFFFFFFF.toInt(), false)
            context.drawText(textRenderer, Text.literal(description), 16, y + 14, 0xFFAAAAAA.toInt(), false)
        }
        super.render(context, mouseX, mouseY, delta)
    }

    override fun close() {
        MinecraftClient.getInstance().setScreen(parent)
    }

    override fun shouldPause(): Boolean = false
}
