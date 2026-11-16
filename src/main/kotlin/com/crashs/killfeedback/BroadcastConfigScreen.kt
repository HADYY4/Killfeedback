package com.crashs.killfeedback

import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.Click
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.Screen
import net.minecraft.client.gui.widget.ButtonWidget
import net.minecraft.client.gui.widget.SliderWidget
import net.minecraft.client.gui.widget.TextFieldWidget
import net.minecraft.text.Text
import net.minecraft.util.Formatting
import kotlin.math.roundToInt

class BroadcastConfigScreen(private val parent: Screen?) : Screen(Text.literal("播报管理")) {

    private enum class Tab(val label: String) {
        ENABLED("启用"),
        OWN("本玩家"),
        OTHER("其他玩家"),
        STORAGE("存储"),
    }

    private data class RowInfo(val title: String, val description: String, val label: String)

    private data class ChoiceSpec(
        val row: Int,
        val options: List<Pair<String, String>>,
        val apply: (String) -> Unit,
    )

    private var tab = Tab.ENABLED
    private var category = "KEYWORD"
    private var draft = KillFeedbackConfigManager.config.deepCopy()
    private var selectedRow = 0
    private var openChoice: ChoiceSpec? = null
    private val rows = mutableListOf<RowInfo>()

    private val rowHeight = 18
    private val controlHeight = 16
    private val contentTop get() = if (tab == Tab.STORAGE || tab == Tab.ENABLED) 44 else 62
    private val leftWidth get() = width / 2
    private val rightX get() = leftWidth + 6
    private val rightWidth get() = width - rightX - 8
    private val labelWidth get() = (leftWidth * 0.43f).toInt().coerceIn(54, 104)
    private val controlX get() = 8 + labelWidth
    private val controlWidth get() = (leftWidth - controlX - 8).coerceAtLeast(36)

    override fun init() {
        clearChildren()
        rows.clear()

        val tabWidth = (width - 20) / Tab.entries.size
        Tab.entries.forEachIndexed { index, value ->
            addDrawableChild(
                ButtonWidget.builder(Text.literal(if (value == tab) "● ${value.label}" else value.label)) {
                    tab = value
                    selectedRow = 0
                    openChoice = null
                    init()
                }.dimensions(10 + index * tabWidth, 17, tabWidth - 4, 18).build(),
            )
        }

        if (tab == Tab.OWN || tab == Tab.OTHER) {
            val subWidth = (width - 20) / 3
            listOf("KEYWORD" to "关键词", "RED" to "红色", "OTHER" to "其他").forEachIndexed { index, (code, label) ->
                addDrawableChild(
                    ButtonWidget.builder(Text.literal(if (category == code) "● $label" else label)) {
                        category = code
                        selectedRow = 0
                        openChoice = null
                        init()
                    }.dimensions(10 + index * subWidth, 39, subWidth - 4, 17).build(),
                )
            }
        }

        when (tab) {
            Tab.ENABLED -> initEnabled()
            Tab.OWN -> initPlayer(draft.broadcast.own)
            Tab.OTHER -> initPlayer(draft.broadcast.other)
            Tab.STORAGE -> initStorage()
        }

        val bottomY = height - 24
        addDrawableChild(
            ButtonWidget.builder(Text.literal("保存")) {
                KillFeedbackConfigManager.apply(draft)
                close()
            }.dimensions(10, bottomY, 70, 18).build(),
        )
        addDrawableChild(
            ButtonWidget.builder(Text.literal("取消")) { close() }
                .dimensions(width / 2 - 35, bottomY, 70, 18).build(),
        )
        addDrawableChild(
            ButtonWidget.builder(Text.literal("恢复默认")) {
                draft = KillFeedbackConfig().deepCopy()
                selectedRow = 0
                init()
            }.dimensions(width - 80, bottomY, 70, 18).build(),
        )

        openChoice?.let { buildChoicePopup(it) }
        selectedRow = selectedRow.coerceIn(0, rows.lastIndex.coerceAtLeast(0))
    }

    private fun initEnabled() {
        addToggleRow(
            "启用播报管理",
            "总开关；关闭后不解析任何播报。",
            "启用",
            draft.broadcast.enabled,
        ) { draft.broadcast.enabled = it }
        addInfoRow("记录格式", "当前：${draft.broadcast.fileFormat}", "格式")
        addInfoRow("记录文件", if (draft.broadcast.writeFile) "已开启" else "未开启", "记录")
    }

    private fun initPlayer(group: BroadcastPlayerSettings) {
        if (category == "KEYWORD") {
            addTextFieldRow(
                "关键词",
                "装备名称包含该关键词时使用“关键词装备”分类。",
                "关键词",
                group.keyword,
            ) { group.keyword = it }
        }

        val settings = group.category(category)

        addToggleRow(
            "显示聊天",
            "关闭后该分类的服务器聊天会被取消。",
            "聊天",
            settings.showChat,
        ) { settings.showChat = it }

        addToggleRow(
            "显示 BossBar",
            "为该分类创建独立 BossBar。",
            "BossBar",
            settings.showBossBar,
        ) { settings.showBossBar = it }

        addSliderRow(
            "BossBar 时间",
            "该分类 BossBar 的持续时间。",
            "时间",
            settings.bossBarSeconds.toFloat(),
            3f,
            60f,
            1f,
            display = { "${it.roundToInt()}s" },
        ) { settings.bossBarSeconds = it.roundToInt() }

        addChoiceRow(
            "音效",
            "该分类识别成功后的音效。",
            "音效",
            listOf(
                "OFF" to "不播放",
                "ENDER_DRAGON" to "末影龙死亡",
                "WITHER" to "凋零死亡",
                "EXPERIENCE" to "经验获取",
            ),
            settings.sound,
        ) { settings.sound = it }

    }

    private fun initStorage() {
        addToggleRow(
            "记录到文件",
            "把识别结果写入文件。",
            "记录",
            draft.broadcast.writeFile,
        ) { draft.broadcast.writeFile = it }

        addChoiceRow(
            "文件格式",
            "SQLite 使用独立数据库；CSV 使用兼容文本格式。",
            "格式",
            listOf("CSV" to "CSV", "SQLITE" to "SQLite"),
            draft.broadcast.fileFormat,
        ) { draft.broadcast.fileFormat = it }

        addInfoRow("SQLite 路径", "config/crashs-legacy-announcements.db", "路径")
        addInfoRow("CSV 路径", "config/crashs-legacy-announcements.csv", "路径")
    }

    private fun addTextFieldRow(
        title: String,
        description: String,
        label: String,
        initial: String,
        onChange: (String) -> Unit,
    ) {
        val row = addRow(title, description, label)
        val field = TextFieldWidget(textRenderer, controlX, y(row), controlWidth, controlHeight, Text.literal(label))
        field.setMaxLength(64)
        field.setText(initial)
        field.setChangedListener {
            onChange(it)
            selectedRow = row
        }
        addDrawableChild(field)
    }

    private fun addToggleRow(
        title: String,
        description: String,
        label: String,
        initial: Boolean,
        onChange: (Boolean) -> Unit,
    ) {
        val row = addRow(title, description, label)
        lateinit var button: ButtonWidget
        fun stateText(value: Boolean): Text =
            if (value) Text.literal("开").formatted(Formatting.GREEN)
            else Text.literal("关").formatted(Formatting.RED)

        button = ButtonWidget.builder(stateText(initial)) {
            val next = button.message.string != "开"
            onChange(next)
            button.setMessage(stateText(next))
            selectedRow = row
        }.dimensions(controlX, y(row), controlWidth, controlHeight).build()
        addDrawableChild(button)
    }

    private fun addChoiceRow(
        title: String,
        description: String,
        label: String,
        options: List<Pair<String, String>>,
        current: String,
        apply: (String) -> Unit,
    ) {
        val row = addRow(title, description, label)
        val currentLabel = options.firstOrNull { it.first == current }?.second ?: current
        val spec = ChoiceSpec(row, options, apply)
        addDrawableChild(
            ButtonWidget.builder(Text.literal(currentLabel)) {
                openChoice = spec
                selectedRow = row
                init()
            }.dimensions(controlX, y(row), controlWidth, controlHeight).build(),
        )
    }

    private fun addSliderRow(
        title: String,
        description: String,
        label: String,
        initial: Float,
        minValue: Float,
        maxValue: Float,
        step: Float,
        display: (Float) -> String,
        onChange: (Float) -> Unit,
    ) {
        val row = addRow(title, description, label)
        val sliderWidth = (controlWidth * 0.62f).toInt().coerceAtLeast(40)
        val numberWidth = (controlWidth - sliderWidth - 4).coerceAtLeast(30)
        lateinit var slider: FloatSlider
        val field = NumericField(
            textRenderer,
            controlX + sliderWidth + 4,
            y(row),
            numberWidth,
            controlHeight,
            label,
            initial,
            minValue,
            maxValue,
            display,
        ) { value ->
            onChange(value)
            slider.setExternalValue(value)
            selectedRow = row
        }
        slider = FloatSlider(
            controlX,
            y(row),
            sliderWidth,
            controlHeight,
            initial,
            minValue,
            maxValue,
            step,
        ) { value ->
            onChange(value)
            field.updateFromSlider(value)
            selectedRow = row
        }
        addDrawableChild(slider)
        addDrawableChild(field)
    }

    private fun addStepperRow(
        title: String,
        description: String,
        label: String,
        value: String,
        onClick: () -> Unit,
    ) {
        val row = addRow(title, description, label)
        addDrawableChild(
            ButtonWidget.builder(Text.literal(value)) {
                selectedRow = row
                onClick()
            }.dimensions(controlX, y(row), controlWidth, controlHeight).build(),
        )
    }

    private fun addInfoRow(title: String, description: String, label: String) {
        addRow(title, description, label)
    }

    private fun addRow(title: String, description: String, label: String): Int {
        rows += RowInfo(title, description, label)
        return rows.lastIndex
    }

    private fun y(index: Int): Int = contentTop + index * rowHeight

    private fun buildChoicePopup(spec: ChoiceSpec) {
        val bounds = popupBounds(spec)
        val optionWidth = (bounds.width - 6) / 2
        spec.options.forEachIndexed { index, (code, label) ->
            val col = index % 2
            val line = index / 2
            val x = bounds.x + 2 + col * (optionWidth + 2)
            val y = bounds.y + 2 + line * 13
            addDrawableChild(
                ButtonWidget.builder(Text.literal(label)) {
                    spec.apply(code)
                    openChoice = null
                    init()
                }.dimensions(x, y, optionWidth, 13).build(),
            )
        }
    }

    private data class PopupBounds(val x: Int, val y: Int, val width: Int, val height: Int)

    private fun popupBounds(spec: ChoiceSpec): PopupBounds {
        val lines = (spec.options.size + 1) / 2
        val popupWidth = (rightWidth - 10).coerceIn(120, 240)
        val popupHeight = lines * 13 + 4
        return PopupBounds(rightX + 5, contentTop + 4, popupWidth, popupHeight)
    }

    override fun mouseClicked(click: Click, doubled: Boolean): Boolean {
        val row = rowAt(click.x(), click.y())
        if (row >= 0) selectedRow = row

        val choice = openChoice
        if (choice != null) {
            val bounds = popupBounds(choice)
            val inside = click.x() >= bounds.x && click.x() < bounds.x + bounds.width &&
                click.y() >= bounds.y && click.y() < bounds.y + bounds.height
            if (!inside) openChoice = null
        }
        return super.mouseClicked(click, doubled)
    }

    private fun rowAt(mouseX: Double, mouseY: Double): Int {
        if (mouseX < 4.0 || mouseX > leftWidth - 4) return -1
        val index = ((mouseY - contentTop) / rowHeight).toInt()
        return if (index in rows.indices) index else -1
    }

    override fun render(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 5, 0xFFFFFFFF.toInt())

        val rightTop = contentTop
        context.fill(rightX, rightTop, rightX + rightWidth, height - 34, 0x70101010)
        context.fill(rightX, rightTop, rightX + 1, height - 34, 0x50FFFFFF)
        context.fill(rightX + rightWidth - 1, rightTop, rightX + rightWidth, height - 34, 0x50FFFFFF)

        rows.forEachIndexed { index, _ ->
            if (index == selectedRow) {
                val rowY = y(index)
                context.fill(4, rowY - 1, leftWidth - 4, rowY + rowHeight - 1, 0x30FFFFFF)
            }
        }

        openChoice?.let { choice ->
            val bounds = popupBounds(choice)
            context.fill(bounds.x, bounds.y, bounds.x + bounds.width, bounds.y + bounds.height, 0xF0101010.toInt())
            context.fill(bounds.x, bounds.y, bounds.x + bounds.width, bounds.y + 1, 0x80FFFFFF.toInt())
        }

        rows.getOrNull(selectedRow)?.let { selected ->
            context.drawText(textRenderer, Text.literal(selected.title), rightX + 7, rightTop + 6, 0xFFFFD23C.toInt(), false)
            var textY = rightTop + 22
            textRenderer.wrapLines(Text.literal(selected.description), rightWidth - 14).forEach { line ->
                context.drawText(textRenderer, line, rightX + 7, textY, 0xFFDDDDDD.toInt(), false)
                textY += 11
            }
        }

        super.render(context, mouseX, mouseY, delta)

        rows.forEachIndexed { index, row ->
            context.drawText(textRenderer, Text.literal(row.label), 8, y(index) + 3, 0xFFFFFFFF.toInt(), false)
        }
    }

    override fun close() {
        MinecraftClient.getInstance().setScreen(parent)
    }

    override fun shouldPause(): Boolean = false
    private class NumericField(
        textRenderer: net.minecraft.client.font.TextRenderer,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        label: String,
        initial: Float,
        private val min: Float,
        private val max: Float,
        private val display: (Float) -> String,
        private val onValue: (Float) -> Unit,
    ) : TextFieldWidget(textRenderer, x, y, width, height, Text.literal(label)) {

        private var syncing = false
        private var lastValid = initial.coerceIn(min, max)

        init {
            setMaxLength(10)
            syncing = true
            setText(display(lastValid))
            syncing = false
            setEditableColor(TextFieldWidget.DEFAULT_EDITABLE_COLOR)
            setChangedListener { text ->
                if (syncing) return@setChangedListener
                val parsed = text.toFloatOrNull()
                if (parsed == null || text.isBlank()) {
                    setEditableColor(INVALID_COLOR)
                    return@setChangedListener
                }
                val value = parsed.coerceIn(min, max)
                lastValid = value
                setEditableColor(TextFieldWidget.DEFAULT_EDITABLE_COLOR)
                onValue(value)
            }
        }

        fun updateFromSlider(value: Float) {
            lastValid = value.coerceIn(min, max)
            syncing = true
            setText(display(lastValid))
            syncing = false
            setEditableColor(TextFieldWidget.DEFAULT_EDITABLE_COLOR)
        }

        override fun setFocused(focused: Boolean) {
            super.setFocused(focused)
            if (!focused) {
                val parsed = text.toFloatOrNull()
                if (parsed == null || text.isBlank()) {
                    updateFromSlider(lastValid)
                } else {
                    updateFromSlider(parsed.coerceIn(min, max))
                }
            }
        }

        companion object {
            private const val INVALID_COLOR = 0xFFFF5555.toInt()
        }
    }

    private class FloatSlider(
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        initial: Float,
        private val min: Float,
        private val max: Float,
        private val step: Float,
        private val onChange: (Float) -> Unit,
    ) : SliderWidget(
        x,
        y,
        width,
        height,
        Text.literal(""),
        ((initial.coerceIn(min, max) - min) / (max - min)).toDouble(),
    ) {

        init {
            updateMessage()
        }

        fun setExternalValue(value: Float) {
            this.value = ((value.coerceIn(min, max) - min) / (max - min)).toDouble()
            updateMessage()
        }

        override fun updateMessage() {
            setMessage(Text.literal(""))
        }

        override fun applyValue() {
            val raw = min + value.toFloat() * (max - min)
            val stepped = if (step > 0f) (raw / step).roundToInt() * step else raw
            onChange(stepped.coerceIn(min, max))
        }
    }

}
