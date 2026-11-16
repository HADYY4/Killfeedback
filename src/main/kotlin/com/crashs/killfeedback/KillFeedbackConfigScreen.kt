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
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Dependency-free config screen.
 *
 * Left half: setting rows. Right half: description of the last focused row.
 */
class KillFeedbackConfigScreen(
    private val parent: Screen?,
    initialTab: String? = null,
) : Screen(Text.literal("击杀反馈配置")) {

    private enum class Tab(val label: String) {
        GENERAL("常规"),
        HUD_STATS("HUD 与统计"),
        STYLE("样式"),
        COLOR("颜色"),
        COINS("金币统计"),
    }

    private data class RowInfo(
        val title: String,
        val description: String,
        val label: String,
    )

    private data class ChoiceSpec(
        val row: Int,
        val options: List<Pair<String, String>>,
        val apply: (String) -> Unit,
    )

    private var tab = initialTab?.let { runCatching { Tab.valueOf(it) }.getOrNull() } ?: Tab.GENERAL
    private var draft = KillFeedbackConfigManager.config.copy()
    private var selectedRow = if (tab == Tab.COINS) -1 else 0
    private var openChoice: ChoiceSpec? = null
    private var error: String? = null
    private val rows = mutableListOf<RowInfo>()

    private val rowHeight = 18
    private val controlHeight = 16
    private val contentTop = 44
    private val leftWidth get() = width / 2
    private val rightX get() = leftWidth + 6
    private val rightWidth get() = width - rightX - 8
    private val labelWidth get() = (leftWidth * 0.43f).toInt().coerceIn(54, 92)
    private val controlX get() = 8 + labelWidth
    private val controlWidth get() = (leftWidth - controlX - 8).coerceAtLeast(36)

    override fun init() {
        clearChildren()
        rows.clear()
        error = null

        val tabWidth = (width - 20) / Tab.entries.size
        Tab.entries.forEachIndexed { index, value ->
            addDrawableChild(
                ButtonWidget.builder(Text.literal(if (value == tab) "● ${value.label}" else value.label)) {
                    tab = value
                    selectedRow = if (value == Tab.COINS) -1 else 0
                    openChoice = null
                    init()
                }.dimensions(10 + index * tabWidth, 17, tabWidth - 4, 18).build(),
            )
        }

        when (tab) {
            Tab.GENERAL -> initGeneral()
            Tab.HUD_STATS -> initHudStats()
            Tab.STYLE -> initStyle()
            Tab.COLOR -> initColors()
            Tab.COINS -> initCoinsInfo()
        }

        val bottomY = height - 24
        addDrawableChild(
            ButtonWidget.builder(Text.literal("保存")) { save() }
                .dimensions(10, bottomY, 70, 18).build(),
        )
        addDrawableChild(
            ButtonWidget.builder(Text.literal("取消")) { close() }
                .dimensions(width / 2 - 35, bottomY, 70, 18).build(),
        )
        addDrawableChild(
            ButtonWidget.builder(Text.literal("恢复默认")) {
                draft = KillFeedbackConfig()
                selectedRow = 0
                openChoice = null
                init()
            }.dimensions(width - 80, bottomY, 70, 18).build(),
        )

        openChoice?.let { buildChoicePopup(it) }
        selectedRow = if (tab == Tab.COINS) {
            selectedRow.coerceIn(-1, rows.lastIndex.coerceAtLeast(0))
        } else {
            selectedRow.coerceIn(0, rows.lastIndex.coerceAtLeast(0))
        }
    }

    private fun initGeneral() {
        addToggleRow(
            "启用模块",
            "控制 KillFeedback 是否处理奖励消息、HUD 和音效。关闭后不会拦截消息。",
            "启用",
            draft.enabled,
        ) { draft.enabled = it }

        addToggleRow(
            "拦截原始消息",
            "成功解析奖励后取消服务器原始系统消息，避免聊天栏重复显示。",
            "拦截",
            draft.intercept,
        ) { draft.intercept = it }

        addToggleRow(
            "包含 ActionBar",
            "同时处理 ActionBar 中的奖励文本。默认只处理系统聊天。",
            "ActionBar",
            draft.includeOverlay,
        ) { draft.includeOverlay = it }


        val patternRow = addRow(
            "奖励正则",
            "用于识别奖励消息。默认识别“! 你获得了 2 金币，来自于 Lv.5 荒林羊”，并支持金币后的 VIP/加成括号说明。保存时会校验正则。",
            "正则",
        )
        val field = TextFieldWidget(
            textRenderer,
            controlX,
            y(patternRow),
            controlWidth,
            controlHeight,
            Text.literal("奖励正则"),
        )
        field.setMaxLength(512)
        field.setText(draft.pattern)
        field.setChangedListener {
            draft.pattern = it
            selectedRow = patternRow
        }
        addDrawableChild(field)

        addToggleRow(
            "音效",
            "解析成功后播放连杀音效。关闭后不影响统计和 HUD。",
            "音效",
            draft.sound,
        ) { draft.sound = it }

        addSliderRow(
            "音效音量",
            "连杀音效的本地音量，最终仍受 Minecraft 主音量影响。",
            "音量",
            draft.soundVolume,
            0f,
            1f,
            0.05f,
            display = { "${(it * 100).roundToInt()}%" },
        ) { draft.soundVolume = it }

        addSliderRow(
            "音效音调",
            "连杀音效的本地音调。",
            "音调",
            draft.soundPitch,
            0.5f,
            2f,
            0.05f,
            display = { "%.2f".format(it) },
        ) { draft.soundPitch = it }

        addChoiceRow(
            "连杀音效组",
            "根据连续击杀次数选择音效组。超过组长度后由收尾模式决定播放内容。",
            "音效组",
            ComboGroup.entries.map { it.name to comboLabel(it) },
            draft.comboGroup,
        ) { draft.comboGroup = it }

        addChoiceRow(
            "收尾模式",
            "连杀次数超过音效组长度后的处理方式。",
            "收尾",
            PostComboMode.entries.map { it.name to postModeLabel(it) },
            draft.postMode,
        ) { draft.postMode = it }

        addChoiceRow(
            "指定音效",
            "当收尾模式为“指定音效”时播放的音效。",
            "指定",
            PostComboSound.entries.map { it.name to soundLabel(it) },
            draft.specificSound,
        ) { draft.specificSound = it }
    }

    private fun initStyle() {
        addToggleRow(
            "显示金币",
            "是否在 HUD 头部显示金币总数。",
            "金币",
            draft.showCoins,
        ) { draft.showCoins = it }

        addToggleRow(
            "显示击杀",
            "是否在 HUD 头部显示击杀数。",
            "击杀",
            draft.showKills,
        ) { draft.showKills = it }

        addToggleRow(
            "显示来源",
            "是否显示每条奖励的怪物来源。",
            "来源",
            draft.showSource,
        ) { draft.showSource = it }

        addToggleRow(
            "显示等级",
            "是否在来源前显示 Lv. 等级。",
            "等级",
            draft.showLevel,
        ) { draft.showLevel = it }

        addToggleRow(
            "显示金额",
            "是否显示每条奖励的金币金额。",
            "金额",
            draft.showAmount,
        ) { draft.showAmount = it }

        addChoiceRow(
            "数字格式",
            "金币和击杀数的显示格式。",
            "格式",
            listOf(
                "RAW" to "原始",
                "COMMA" to "千分位",
                "SHORT" to "缩写",
            ),
            draft.numberFormat,
        ) { draft.numberFormat = it }

        addChoiceRow(
            "统计模式",
            "TIMED 会按统计窗口清空，SESSION 会保留本次会话累计。",
            "模式",
            listOf(
                "TIMED" to "计时窗口",
                "SESSION" to "本次会话",
            ),
            draft.statsMode,
        ) { draft.statsMode = it }

        addSliderRow(
            "行间距",
            "奖励行之间的垂直间距。",
            "行距",
            draft.rowSpacing,
            0f,
            8f,
            1f,
            display = { it.roundToInt().toString() },
        ) { draft.rowSpacing = it }

        addSliderRow(
            "待淘汰透明度",
            "超过最大行数时，最后一行在淡出前的透明度。",
            "淘汰",
            draft.pendingEvictionAlpha,
            0.1f,
            1f,
            0.05f,
            display = { "${(it * 100).roundToInt()}%" },
        ) { draft.pendingEvictionAlpha = it }

        addSliderRow(
            "进度条高度",
            "统计窗口进度条的高度。",
            "条高",
            draft.progressHeight,
            1f,
            8f,
            1f,
            display = { it.roundToInt().toString() },
        ) { draft.progressHeight = it }

        addToggleRow(
            "数字滚动动画",
            "新条目出现时让金额从 0 滚动到实际值。",
            "数字滚动动画",
            draft.amountScroll,
        ) { draft.amountScroll = it }

        addToggleRow(
            "下移动画",
            "新条目加入时旧条目平滑下移。关闭后位置立即更新。",
            "下移",
            draft.entryPushAnimation,
        ) { draft.entryPushAnimation = it }

        addToggleRow(
            "逐字显示",
            "怪物等级和名称从右往左逐字出现。",
            "逐字",
            draft.textRevealAnimation,
        ) { draft.textRevealAnimation = it }
    }

    private fun initCoinsInfo() {
        addToggleRow(
            "启用金币统计",
            "显示独立于 KillFeedback 统计窗口的会话金币 HUD。",
            "启用",
            draft.coinsInfoEnabled,
        ) { draft.coinsInfoEnabled = it }

        addToggleRow(
            "双行显示",
            "第一行显示总金币，第二行显示平均值。",
            "双行",
            draft.coinsInfoTwoLines,
        ) { draft.coinsInfoTwoLines = it }

        addToggleRow(
            "显示总金币",
            "显示会话累计金币。",
            "总金币",
            draft.coinsInfoShowTotal,
        ) { draft.coinsInfoShowTotal = it }

        addToggleRow(
            "显示平均值",
            "显示 avg/min 或 avg/h。",
            "平均值",
            draft.coinsInfoShowAverage,
        ) { draft.coinsInfoShowAverage = it }

        addToggleRow(
            "显示会话时间",
            "在金币统计上方显示本次会话时长。",
            "时长",
            draft.coinsInfoShowSessionTime,
        ) { draft.coinsInfoShowSessionTime = it }

        addToggleRow(
            "显示总击杀",
            "在金币统计上方显示本次会话总击杀数。",
            "击杀",
            draft.coinsInfoShowKills,
        ) { draft.coinsInfoShowKills = it }

        addToggleRow(
            "数字滚动动画",
            "总金币和平均值使用缓动滚动，并用会话级宽度缓存稳定布局。",
            "数字滚动动画",
            draft.coinsInfoScroll,
        ) { draft.coinsInfoScroll = it }

        addToggleRow(
            "文字阴影",
            "为金币统计的文字绘制阴影。",
            "阴影",
            draft.coinsInfoTextShadow,
        ) { draft.coinsInfoTextShadow = it }

        addChoiceRow(
            "平均单位",
            "平均值按分钟或小时计算。",
            "单位",
            listOf("MINUTE" to "avg/min", "HOUR" to "avg/h"),
            draft.coinsInfoAverageUnit,
        ) { draft.coinsInfoAverageUnit = it }

        addChoiceRow(
            "数字格式",
            "金币和平均值的显示格式。",
            "格式",
            listOf("RAW" to "原始", "COMMA" to "千分位", "SHORT" to "缩写"),
            draft.coinsInfoNumberFormat,
        ) { draft.coinsInfoNumberFormat = it }

        addSliderRow(
            "CoinsInfo 缩放",
            "独立 HUD 的整体缩放。",
            "缩放",
            draft.coinsInfoScale,
            0.5f,
            2.5f,
            0.05f,
            display = { "%.2f".format(it) },
        ) { draft.coinsInfoScale = it }

        addSliderRow(
            "水平位置",
            "CoinsInfo 在屏幕上的水平百分比。",
            "X",
            draft.coinsInfoX,
            0f,
            100f,
            1f,
            display = { "${it.roundToInt()}%" },
        ) { draft.coinsInfoX = it }

        addSliderRow(
            "垂直位置",
            "CoinsInfo 在屏幕上的垂直百分比。",
            "Y",
            draft.coinsInfoY,
            0f,
            100f,
            1f,
            display = { "${it.roundToInt()}%" },
        ) { draft.coinsInfoY = it }

        addToggleRow(
            "右上角对齐",
            "开启后按屏幕右侧计算水平位置。",
            "右上",
            draft.coinsInfoRight,
        ) { draft.coinsInfoRight = it }

        addColorRow(
            "总金币颜色",
            "默认与 KillFeedback 统计文本一致。",
            "总金币",
            draft.coinsInfoTotalColor,
        ) { draft.coinsInfoTotalColor = it }

        addColorRow(
            "平均值颜色",
            "默认与总金币颜色相同。",
            "平均值",
            draft.coinsInfoAverageColor,
        ) { draft.coinsInfoAverageColor = it }

        addColorRow(
            "会话时间颜色",
            "金币统计顶部会话时间的颜色。",
            "时长",
            draft.coinsInfoSessionTimeColor,
        ) { draft.coinsInfoSessionTimeColor = it }

        addColorRow(
            "总击杀颜色",
            "金币统计顶部总击杀数的颜色。",
            "击杀",
            draft.coinsInfoKillsColor,
        ) { draft.coinsInfoKillsColor = it }

        addColorRow(
            "分隔条颜色",
            "金币统计顶部横条的颜色。",
            "分隔条",
            draft.coinsInfoDividerColor,
        ) { draft.coinsInfoDividerColor = it }

        addColorRow(
            "分隔条背景色",
            "金币统计顶部横条的背景颜色。",
            "分隔条背景",
            draft.coinsInfoDividerBackground,
        ) { draft.coinsInfoDividerBackground = it }

        addToggleRow(
            "显示背景",
            "在金币统计后方绘制背景。",
            "背景",
            draft.coinsInfoBackground,
        ) { draft.coinsInfoBackground = it }

        addColorRow(
            "背景颜色",
            "金币统计背景颜色，包含 alpha。",
            "背景",
            draft.coinsInfoBackgroundColor,
        ) { draft.coinsInfoBackgroundColor = it }

        addToggleRow(
            "显示边框",
            "绘制金币统计外边框。",
            "边框",
            draft.coinsInfoOutline,
        ) { draft.coinsInfoOutline = it }

        addColorRow(
            "边框颜色",
            "金币统计外边框颜色。",
            "边框",
            draft.coinsInfoOutlineColor,
        ) { draft.coinsInfoOutlineColor = it }

        addDrawableChild(
            ButtonWidget.builder(Text.literal("重置金币统计")) {
                CoinsSession.reset()
                selectedRow = -1
                init()
            }.dimensions(rightX + rightWidth - 107, height - 59, 100, 18).build(),
        )
    }

    private fun initColors() {
        addColorRow(
            "金币颜色",
            "奖励金额的颜色。",
            "金币",
            draft.coinColor,
        ) { draft.coinColor = it }

        addColorRow(
            "来源颜色",
            "怪物名称的颜色。",
            "来源",
            draft.sourceColor,
        ) { draft.sourceColor = it }

        addColorRow(
            "等级颜色",
            "Lv. 等级文字的颜色。",
            "等级",
            draft.levelColor,
        ) { draft.levelColor = it }

        addColorRow(
            "头部颜色",
            "金币/击杀头部文字颜色。",
            "头部",
            draft.headerColor,
        ) { draft.headerColor = it }

        addColorRow(
            "进度条颜色",
            "统计窗口进度条填充颜色。",
            "进度",
            draft.progressColor,
        ) { draft.progressColor = it }

        addColorRow(
            "进度背景",
            "进度条背景颜色。",
            "进度背景",
            draft.progressBackground,
        ) { draft.progressBackground = it }

        addColorRow(
            "背景颜色",
            "HUD 背景颜色，包含 alpha。",
            "背景",
            draft.backgroundColor,
        ) { draft.backgroundColor = it }

        addColorRow(
            "边框颜色",
            "HUD 外边框颜色。",
            "边框",
            draft.outlineColor,
        ) { draft.outlineColor = it }

        addColorRow(
            "分隔线颜色",
            "无进度条时的分隔线颜色。",
            "分隔线",
            draft.dividerColor,
        ) { draft.dividerColor = it }
    }

    private fun initHudStats() {
        addToggleRow(
            "HUD",
            "显示金币统计和最近奖励列表。关闭后仍会解析和播放音效。",
            "HUD",
            draft.hud,
        ) { draft.hud = it }

        addSliderRow(
            "HUD 缩放",
            "整体缩放 HUD，包括文字、间距和进度条。",
            "缩放",
            draft.hudScale,
            0.5f,
            2.5f,
            0.05f,
            display = { "%.2f".format(it) },
        ) { draft.hudScale = it }

        addToggleRow(
            "右上角对齐",
            "开启后 HUD 以屏幕右上角为基准；关闭后使用左上角。",
            "右上角",
            draft.hudRight,
        ) { draft.hudRight = it }

        draft.hudX = draft.hudX.coerceIn(0f, 100f)
        draft.hudY = draft.hudY.coerceIn(0f, 100f)

        addSliderRow(
            "X 百分比",
            "HUD 的水平位置百分比。0% 为左/右边缘，100% 为另一侧边缘。",
            "X 百分比",
            draft.hudX,
            0f,
            100f,
            1f,
            display = { "${it.roundToInt()}%" },
        ) { draft.hudX = it }

        addSliderRow(
            "Y 百分比",
            "HUD 距离屏幕顶部的百分比。0% 为顶部，100% 为底部。",
            "Y 百分比",
            draft.hudY,
            0f,
            100f,
            1f,
            display = { "${it.roundToInt()}%" },
        ) { draft.hudY = it }

        addChoiceRow(
            "头部样式",
            "金币和击杀数的显示方式。隐藏后只显示奖励列表。",
            "头部",
            listOf(
                "FULL" to "完整",
                "COMPACT" to "紧凑",
                "COINS_ONLY" to "仅金币",
                "KILLS_ONLY" to "仅击杀",
                "HIDDEN" to "隐藏",
            ),
            draft.headerStyle,
        ) { draft.headerStyle = it }

        addToggleRow(
            "HUD 背景",
            "在 HUD 后方绘制半透明背景。",
            "背景",
            draft.background,
        ) { draft.background = it }

        addToggleRow(
            "HUD 边框",
            "绘制 HUD 外边框。",
            "边框",
            draft.hudOutline,
        ) { draft.hudOutline = it }

        addToggleRow(
            "文字阴影",
            "为 HUD 文字绘制阴影。",
            "阴影",
            draft.hudTextShadow,
        ) { draft.hudTextShadow = it }

        addToggleRow(
            "显示进度条",
            "在头部和奖励列表之间显示统计窗口进度。",
            "进度条",
            draft.showProgress,
        ) { draft.showProgress = it }

        addSliderRow(
            "统计窗口",
            "统计窗口的持续时间。新击杀会重新开始计时，超时后清空统计。",
            "窗口",
            draft.statsWindowSeconds,
            1f,
            600f,
            1f,
            display = { "${it.roundToInt()} s" },
        ) { draft.statsWindowSeconds = it }

        addSliderRow(
            "最大行数",
            "HUD 中最多同时显示的奖励行数。",
            "行数",
            draft.maxRows.toFloat(),
            1f,
            20f,
            1f,
            display = { it.roundToInt().toString() },
        ) { draft.maxRows = it.roundToInt() }

        addSliderRow(
            "条目持续时间",
            "单条奖励在 HUD 中停留的秒数，之后开始淡出。",
            "持续",
            draft.entryDurationSeconds,
            1f,
            60f,
            1f,
            display = { "${it.roundToInt()} s" },
        ) { draft.entryDurationSeconds = it }
    }

    private fun addColorRow(
        title: String,
        description: String,
        label: String,
        initial: Int,
        onChange: (Int) -> Unit,
    ) {
        val row = addRow(title, description, label)
        val field = ColorField(
            textRenderer,
            controlX,
            y(row),
            controlWidth,
            controlHeight,
            label,
            initial,
        ) { value ->
            onChange(value)
            selectedRow = row
        }
        addDrawableChild(field)
    }

    private fun addRow(title: String, description: String, label: String): Int {
        rows += RowInfo(title, description, label)
        return rows.lastIndex
    }

    private fun y(index: Int): Int = contentTop + index * rowHeight

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
        val sliderWidth = (controlWidth * 0.62f).toInt().coerceAtLeast(32)
        val numberWidth = (controlWidth - sliderWidth - 3).coerceAtLeast(24)
        lateinit var slider: FloatSlider

        val field = NumericField(
            textRenderer,
            controlX + sliderWidth + 3,
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
        val x = rightX + 5
        val y = contentTop + 4
        return PopupBounds(x, y, popupWidth, popupHeight)
    }

    private fun save() {
        val pattern = draft.pattern.ifBlank { KillRewardParser.DEFAULT_PATTERN }
        val compiled = runCatching { Regex(pattern) }
        if (compiled.isFailure) {
            error = "正则无效：${compiled.exceptionOrNull()?.message ?: "未知错误"}"
            return
        }
        draft.pattern = pattern
        KillFeedbackConfigManager.apply(draft)
        close()
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
        if (index !in rows.indices) return -1
        return index
    }

    override fun close() {
        MinecraftClient.getInstance().setScreen(parent)
    }

    override fun shouldPause(): Boolean = false

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

        if (tab == Tab.COINS && selectedRow < 0) {
            drawCoinsDetails(context, rightTop)
        } else rows.getOrNull(selectedRow)?.let { selected ->
            context.drawText(
                textRenderer,
                Text.literal(selected.title),
                rightX + 7,
                rightTop + 6,
                0xFFFFD23C.toInt(),
                false,
            )
            val lines = textRenderer.wrapLines(Text.literal(selected.description), rightWidth - 14)
            var textY = rightTop + 22
            lines.forEach { line ->
                context.drawText(textRenderer, line, rightX + 7, textY, 0xFFDDDDDD.toInt(), false)
                textY += 11
            }
        }

        super.render(context, mouseX, mouseY, delta)

        rows.forEachIndexed { index, row ->
            context.drawText(textRenderer, Text.literal(row.label), 8, y(index) + 3, 0xFFFFFFFF.toInt(), false)
        }

        error?.let {
            context.drawCenteredTextWithShadow(textRenderer, Text.literal(it), width / 2, height - 35, 0xFFFF5555.toInt())
        }
    }

    private fun drawCoinsDetails(context: DrawContext, top: Int) {
        val snapshot = CoinsSession.snapshot()
        val total = when (draft.coinsInfoNumberFormat.uppercase()) {
            "RAW" -> KillRewardParser.formatCoins(snapshot.totalCoins)
            "SHORT" -> {
                val value = snapshot.totalCoins
                when {
                    value >= 1_000_000 -> "%.1fM".format(java.util.Locale.ROOT, value / 1_000_000)
                    value >= 1_000 -> "%.1fK".format(java.util.Locale.ROOT, value / 1_000)
                    else -> value.toInt().toString()
                }
            }
            else -> String.format(java.util.Locale.ROOT, "%,.2f", snapshot.totalCoins).trimEnd('0').trimEnd('.')
        }
        val average = when (draft.coinsInfoAverageUnit.uppercase()) {
            "HOUR" -> snapshot.averagePerHour()
            else -> snapshot.averagePerMinute()
        }
        val unit = if (draft.coinsInfoAverageUnit.equals("HOUR", true)) "avg/h" else "avg/min"
        val duration = formatSessionDuration(snapshot.elapsedMs)

        context.drawText(textRenderer, Text.literal("详情"), rightX + 7, top + 6, 0xFFFFD23C.toInt(), false)
        val lines = listOf(
            "总金币：$total",
            "平均值：${KillRewardParser.formatCoins(average)} $unit",
            "会话时间：$duration",
            "总击杀：${snapshot.totalKills}",
        )
        var y = top + 22
        lines.forEach { line ->
            context.drawText(textRenderer, Text.literal(line), rightX + 7, y, 0xFFDDDDDD.toInt(), false)
            y += 11
        }
    }

    private fun formatSessionDuration(ms: Long): String {
        val seconds = (ms / 1000L).coerceAtLeast(0L)
        val hours = seconds / 3600L
        val minutes = (seconds % 3600L) / 60L
        val secs = seconds % 60L
        return if (hours > 0L) "%d:%02d:%02d".format(java.util.Locale.ROOT, hours, minutes, secs)
        else "%02d:%02d".format(java.util.Locale.ROOT, minutes, secs)
    }

    private fun comboLabel(group: ComboGroup): String = when (group) {
        ComboGroup.NONE -> "无"
        ComboGroup.CF -> "CF 连杀"
        ComboGroup.VALORANT_BUBBLEGUM -> "泡泡糖"
        ComboGroup.VALORANT_CHAMPIONS -> "2021 冠军"
        ComboGroup.VALORANT_GAIA -> "盖亚"
        ComboGroup.VALORANT_GAIA_VENGEANCE -> "盖亚复仇"
        ComboGroup.VALORANT_GLITCHPOP -> "故障艺术"
        ComboGroup.VALORANT_PRELUDE -> "混沌序曲"
        ComboGroup.VALORANT_PRIME -> "Prime"
        ComboGroup.VALORANT_PRIMORDIUM -> "原初"
        ComboGroup.VALORANT_RADIANT_CRISIS -> "辐射危机"
        ComboGroup.VALORANT_RGX -> "RGX 11z"
        ComboGroup.VALORANT_SINGULARITY -> "奇点"
    }

    private fun postModeLabel(mode: PostComboMode): String = when (mode) {
        PostComboMode.LOOP_GROUP -> "循环整组"
        PostComboMode.LAST_OF_GROUP -> "组内最后一个"
        PostComboMode.NONE -> "不播放"
        PostComboMode.SPECIFIC -> "指定音效"
    }

    private fun soundLabel(sound: PostComboSound): String = when (sound) {
        PostComboSound.CARD_CRIT -> "卡牌暴击"
        PostComboSound.CARD_EXPLOSION -> "卡牌爆炸"
        PostComboSound.CARD_HEADSHOT -> "卡牌爆头"
        PostComboSound.COLLISION -> "碰撞击杀"
        PostComboSound.CRIT -> "暴击击杀"
        PostComboSound.EXPLOSION -> "爆炸击杀"
        PostComboSound.HEADSHOT_BF1 -> "爆头 BF1"
        PostComboSound.HEADSHOT_BF5 -> "爆头 BF5"
        PostComboSound.HEADSHOT_BF6 -> "爆头 BF6"
        PostComboSound.HEADSHOT_DF -> "爆头 DF"
        PostComboSound.HIT -> "命中"
        PostComboSound.HONOR_HIGH -> "高级荣誉"
        PostComboSound.HONOR_NORMAL -> "普通荣誉"
        PostComboSound.KILL_BF1 -> "击杀 BF1"
        PostComboSound.KILL_BF5 -> "击杀 BF5"
        PostComboSound.KILL_BF6 -> "击杀 BF6"
        PostComboSound.KILL_DF -> "击杀 DF"
        PostComboSound.VEHICLE_BF5 -> "载具击杀 BF5"
    }

    private class ColorField(
        textRenderer: net.minecraft.client.font.TextRenderer,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        label: String,
        initial: Int,
        private val onValue: (Int) -> Unit,
    ) : TextFieldWidget(textRenderer, x, y, width, height, Text.literal(label)) {

        private var syncing = false
        private var lastValid = initial

        init {
            setMaxLength(9)
            syncing = true
            setText(format(initial))
            syncing = false
            setEditableColor(TextFieldWidget.DEFAULT_EDITABLE_COLOR)
            setChangedListener { text ->
                if (syncing) return@setChangedListener
                val value = parse(text)
                if (value == null) {
                    setEditableColor(INVALID_COLOR)
                } else {
                    lastValid = value
                    setEditableColor(TextFieldWidget.DEFAULT_EDITABLE_COLOR)
                    onValue(value)
                }
            }
        }

        override fun setFocused(focused: Boolean) {
            super.setFocused(focused)
            if (!focused) {
                syncing = true
                setText(format(lastValid))
                syncing = false
                setEditableColor(TextFieldWidget.DEFAULT_EDITABLE_COLOR)
            }
        }

        companion object {
            private const val INVALID_COLOR = 0xFFFF5555.toInt()

            private fun format(value: Int): String = "#%08X".format(value)

            private fun parse(text: String): Int? {
                val raw = text.trim().removePrefix("#")
                if (raw.length != 6 && raw.length != 8) return null
                val parsed = raw.toLongOrNull(16) ?: return null
                return if (raw.length == 6) (0xFF000000L or parsed).toInt() else parsed.toInt()
            }
        }
    }

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
            onChange(actualValue())
        }

        private fun actualValue(): Float {
            val raw = min + value.toFloat() * (max - min)
            val stepped = if (step > 0f) (raw / step).roundToInt() * step else raw
            return stepped.coerceIn(min, max)
        }
    }
}
