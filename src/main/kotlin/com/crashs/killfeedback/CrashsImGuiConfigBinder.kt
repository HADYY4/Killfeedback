package com.crashs.killfeedback

import imgui.ImGui
import imgui.flag.ImGuiCond
import imgui.type.ImBoolean
import imgui.type.ImInt
import imgui.type.ImString

/**
 * Direct live binding for the Flat KillFeedbackConfig.
 *
 * Non-text widgets update the live config immediately. Text widgets keep a local buffer and
 * require the adjacent Save button; closing the screen therefore drops uncommitted text edits.
 */
class CrashsImGuiConfigBinder {

    private val config: KillFeedbackConfig
        get() = KillFeedbackConfigManager.config

    private val textBuffers = HashMap<String, ImString>()
    private val numericBuffers = HashMap<String, ImString>()
    private val numericErrors = HashSet<String>()
    private val colorBuffers = HashMap<String, FloatArray>()
    private var idPrefix = "root"

    private fun key(label: String): String = "$idPrefix::$label"

    fun drawKillFeedback() {
        section("基础", "killfeedback.base") {
            bool("启用击杀反馈", { config.enabled }) { config.enabled = it }
            bool("拦截聊天", { config.intercept }) { config.intercept = it }
            bool("包含 ActionBar", { config.includeOverlay }) { config.includeOverlay = it }
            text("奖励正则", { config.pattern }, { config.pattern = it }, { value ->
                if (value.isBlank()) null else runCatching { Regex(value) }.exceptionOrNull()?.let { "正则无效" }
            })
        }
        section("声音", "killfeedback.sound") {
            bool("启用声音", { config.sound }) { config.sound = it }
            float("音量", { config.soundVolume }, 0f, 2f, "%.2f") { config.soundVolume = it }
            float("音高", { config.soundPitch }, 0.5f, 2f, "%.2f") { config.soundPitch = it }
            choice("连杀音效组", ComboGroup.entries.map { it.name }, config.comboGroup) { config.comboGroup = it }
            choice("收尾模式", PostComboMode.entries.map { it.name }, config.postMode) { config.postMode = it }
            choice("指定音效", PostComboSound.entries.map { it.name }, config.specificSound) { config.specificSound = it }
        }
        section("HUD", "killfeedback.hud") {
            bool("显示 HUD", { config.hud }) { config.hud = it }
            float("整体缩放", { config.hudScale }, 0.3f, 2f, "%.2f") { config.hudScale = it }
            bool("右侧锚点", { config.hudRight }) { config.hudRight = it }
            float("X 位置", { config.hudX }, 0f, 100f, "%.1f") { config.hudX = it }
            float("Y 位置", { config.hudY }, 0f, 100f, "%.1f") { config.hudY = it }
            float("统计窗口", { config.statsWindowSeconds }, 1f, 600f, "%.1f") { config.statsWindowSeconds = it }
            intSlider("最大行数", { config.maxRows }, 1, 20) { config.maxRows = it }
            float("条目持续时间", { config.entryDurationSeconds }, 1f, 60f, "%.1f") { config.entryDurationSeconds = it }
            float("行间距", { config.rowSpacing }, 0f, 8f, "%.1f") { config.rowSpacing = it }
            float("待淘汰透明度", { config.pendingEvictionAlpha }, 0.1f, 1f, "%.2f") { config.pendingEvictionAlpha = it }
            float("进度条高度", { config.progressHeight }, 1f, 8f, "%.1f") { config.progressHeight = it }
        }
        section("样式", "killfeedback.style") {
            choice("头部样式", listOf("FULL", "COMPACT", "COINS_ONLY", "KILLS_ONLY", "HIDDEN"), config.headerStyle) { config.headerStyle = it }
            choice("数字格式", listOf("RAW", "COMMA", "SHORT"), config.numberFormat) { config.numberFormat = it }
            choice("统计模式", listOf("TIMED", "SESSION"), config.statsMode) { config.statsMode = it }
            bool("显示金币", { config.showCoins }) { config.showCoins = it }
            bool("显示击杀", { config.showKills }) { config.showKills = it }
            bool("显示来源", { config.showSource }) { config.showSource = it }
            bool("显示等级", { config.showLevel }) { config.showLevel = it }
            bool("显示金额", { config.showAmount }) { config.showAmount = it }
            bool("显示进度条", { config.showProgress }) { config.showProgress = it }
            bool("背景", { config.background }) { config.background = it }
            bool("边框", { config.hudOutline }) { config.hudOutline = it }
            bool("文字阴影", { config.hudTextShadow }) { config.hudTextShadow = it }
            bool("下移动画", { config.entryPushAnimation }) { config.entryPushAnimation = it }
            bool("逐字显示", { config.textRevealAnimation }) { config.textRevealAnimation = it }
            bool("金额滚动", { config.amountScroll }) { config.amountScroll = it }
        }
        section("颜色", "killfeedback.colors") {
            color("金币颜色", { config.coinColor }) { config.coinColor = it }
            color("来源颜色", { config.sourceColor }) { config.sourceColor = it }
            color("等级颜色", { config.levelColor }) { config.levelColor = it }
            color("头部颜色", { config.headerColor }) { config.headerColor = it }
            color("分隔线颜色", { config.dividerColor }) { config.dividerColor = it }
            color("背景颜色", { config.backgroundColor }) { config.backgroundColor = it }
            color("边框颜色", { config.outlineColor }) { config.outlineColor = it }
            color("进度颜色", { config.progressColor }) { config.progressColor = it }
            color("进度背景", { config.progressBackground }) { config.progressBackground = it }
        }
    }

    fun drawCoinsInfo() {
        section("基础", "coins.base") {
            bool("启用金币统计", { config.coinsInfoEnabled }) { config.coinsInfoEnabled = it }
            float("缩放", { config.coinsInfoScale }, 0.5f, 2f, "%.2f") { config.coinsInfoScale = it }
            bool("右侧锚点", { config.coinsInfoRight }) { config.coinsInfoRight = it }
            float("X 位置", { config.coinsInfoX }, 0f, 100f, "%.1f") { config.coinsInfoX = it }
            float("Y 位置", { config.coinsInfoY }, 0f, 100f, "%.1f") { config.coinsInfoY = it }
            bool("双行显示", { config.coinsInfoTwoLines }) { config.coinsInfoTwoLines = it }
            bool("数字滚动", { config.coinsInfoScroll }) { config.coinsInfoScroll = it }
            bool("显示总金币", { config.coinsInfoShowTotal }) { config.coinsInfoShowTotal = it }
            bool("显示平均值", { config.coinsInfoShowAverage }) { config.coinsInfoShowAverage = it }
            bool("显示会话时间", { config.coinsInfoShowSessionTime }) { config.coinsInfoShowSessionTime = it }
            bool("显示总击杀", { config.coinsInfoShowKills }) { config.coinsInfoShowKills = it }
        }
        section("数据", "coins.data") {
            choice("平均单位", listOf("MINUTE", "HOUR"), config.coinsInfoAverageUnit) { config.coinsInfoAverageUnit = it }
            choice("数字格式", listOf("RAW", "COMMA", "SHORT"), config.coinsInfoNumberFormat) { config.coinsInfoNumberFormat = it }
        }
        section("颜色", "coins.colors") {
            color("总金币颜色", { config.coinsInfoTotalColor }) { config.coinsInfoTotalColor = it }
            color("平均值颜色", { config.coinsInfoAverageColor }) { config.coinsInfoAverageColor = it }
            color("会话时间颜色", { config.coinsInfoSessionTimeColor }) { config.coinsInfoSessionTimeColor = it }
            color("总击杀颜色", { config.coinsInfoKillsColor }) { config.coinsInfoKillsColor = it }
            color("分隔线颜色", { config.coinsInfoDividerColor }) { config.coinsInfoDividerColor = it }
            color("分隔线背景", { config.coinsInfoDividerBackground }) { config.coinsInfoDividerBackground = it }
        }
        section("显示", "coins.display") {
            bool("背景", { config.coinsInfoBackground }) { config.coinsInfoBackground = it }
            color("背景颜色", { config.coinsInfoBackgroundColor }) { config.coinsInfoBackgroundColor = it }
            bool("边框", { config.coinsInfoOutline }) { config.coinsInfoOutline = it }
            color("边框颜色", { config.coinsInfoOutlineColor }) { config.coinsInfoOutlineColor = it }
            bool("文字阴影", { config.coinsInfoTextShadow }) { config.coinsInfoTextShadow = it }
        }
    }

    fun drawServerDamage() {
        section("服务器伤害", "serverdamage.main") {
            bool("启用伤害显示管理", { config.serverDamageEnabled }) { config.serverDamageEnabled = it }
            bool("显示背景", { config.serverDamageBackground }) { config.serverDamageBackground = it }
            bool("移除伤害显示", { config.serverDamageRemove }) { config.serverDamageRemove = it }
            bool("文字阴影", { config.serverDamageShadow }) { config.serverDamageShadow = it }
            bool("玩家受伤文本（-nn.nn）", { config.serverDamagePlayerHurt }) { config.serverDamagePlayerHurt = it }
        }
    }

    fun drawBroadcast() {
        val settings = config.broadcast
        section("全局", "broadcast.global") {
            bool("启用播报管理", { settings.enabled }) { settings.enabled = it }
            bool("记录到文件", { settings.writeFile }) { settings.writeFile = it }
            choice("文件格式", listOf("CSV", "SQLITE"), settings.fileFormat) { settings.fileFormat = it }
        }
        if (ImGui.beginTabBar("##broadcastPlayers")) {
            if (ImGui.beginTabItem("本玩家")) {
                drawBroadcastPlayer("本玩家", "broadcast.own", settings.own)
                ImGui.endTabItem()
            }
            if (ImGui.beginTabItem("其他玩家")) {
                drawBroadcastPlayer("其他玩家", "broadcast.other", settings.other)
                ImGui.endTabItem()
            }
            ImGui.endTabBar()
        }
    }

    private fun drawBroadcastPlayer(title: String, id: String, group: BroadcastPlayerSettings) {
        section(title, id) {
            section("关键词", "$id.keyword") {
                text("关键词", { group.keyword }, { group.keyword = it }, { value ->
                    if (value.isBlank()) "关键词不能为空" else null
                })
            }
            section("关键词装备", "$id.keywordCategory") {
                broadcastCategory(group.keywordCategory)
            }
            section("红色装备", "$id.redCategory") {
                broadcastCategory(group.redCategory)
            }
            section("其他装备", "$id.otherCategory") {
                broadcastCategory(group.otherCategory)
            }
        }
    }

    private fun broadcastCategory(category: BroadcastCategorySettings) {
        bool("显示聊天", { category.showChat }) { category.showChat = it }
        bool("显示 BossBar", { category.showBossBar }) { category.showBossBar = it }
        intSlider("BossBar 秒数", { category.bossBarSeconds }, 3, 60) { category.bossBarSeconds = it }
        choice("音效", listOf("ENDER_DRAGON", "WITHER", "EXPERIENCE", "OFF"), category.sound) { category.sound = it }
    }

    private fun section(title: String, id: String, block: () -> Unit) {
        val open = CrashsImGuiUiState.isOpen(id)
        ImGui.setNextItemOpen(open, ImGuiCond.Always)
        val expanded = ImGui.collapsingHeader("$title##$id")
        if (expanded != open) {
            CrashsImGuiUiState.setOpen(id, expanded)
        }
        if (expanded) {
            val previousPrefix = idPrefix
            idPrefix = id
            ImGui.pushID(id)
            ImGui.indent(12f)
            block()
            ImGui.unindent(12f)
            ImGui.popID()
            idPrefix = previousPrefix
        }
    }

    private fun bool(label: String, get: () -> Boolean, set: (Boolean) -> Unit) {
        val value = ImBoolean(get())
        if (ImGui.checkbox(label, value)) {
            set(value.get())
            CrashsImGuiConfigSync.touch()
        }
    }

    private fun float(
        label: String,
        get: () -> Float,
        min: Float,
        max: Float,
        format: String,
        set: (Float) -> Unit,
    ) {
        val values = floatArrayOf(get())
        ImGui.text(label)
        ImGui.setNextItemWidth(190f)
        val key = key(label)
        if (ImGui.sliderFloat("##slider$key", values, min, max, format)) {
            set(values[0])
            CrashsImGuiConfigSync.touch()
            numericBuffers.getOrPut(key) { ImString() }.set(formatValue(values[0], format))
        }
        ImGui.sameLine()
        val buffer = numericBuffers.getOrPut(key) { ImString(formatValue(get(), format), 24) }
        ImGui.setNextItemWidth(64f)
        val entered = ImGui.inputText("##input$key", buffer, 24)
        if (entered || ImGui.isItemDeactivatedAfterEdit()) {
            val parsed = buffer.get().replace(',', '.').toFloatOrNull()
            if (parsed == null) {
                numericErrors += key
            } else {
                numericErrors -= key
                val clamped = parsed.coerceIn(min, max)
                set(clamped)
                CrashsImGuiConfigSync.touch()
                buffer.set(formatValue(clamped, format))
            }
        }
        if (key in numericErrors) {
            ImGui.sameLine()
            ImGui.textColored(1f, 0.3f, 0.3f, 1f, "!")
        }
    }

    private fun intSlider(label: String, get: () -> Int, min: Int, max: Int, set: (Int) -> Unit) {
        val values = intArrayOf(get())
        ImGui.text(label)
        ImGui.setNextItemWidth(190f)
        val key = key(label)
        if (ImGui.sliderInt("##slider$key", values, min, max)) {
            set(values[0])
            CrashsImGuiConfigSync.touch()
            numericBuffers.getOrPut(key) { ImString(values[0].toString(), 16) }.set(values[0].toString())
        }
        ImGui.sameLine()
        val buffer = numericBuffers.getOrPut(key) { ImString(get().toString(), 16) }
        ImGui.setNextItemWidth(64f)
        val entered = ImGui.inputText("##input$key", buffer, 16)
        if (entered || ImGui.isItemDeactivatedAfterEdit()) {
            val parsed = buffer.get().toIntOrNull()
            if (parsed == null) {
                numericErrors += key
            } else {
                numericErrors -= key
                val clamped = parsed.coerceIn(min, max)
                set(clamped)
                CrashsImGuiConfigSync.touch()
                buffer.set(clamped.toString())
            }
        }
        if (key in numericErrors) {
            ImGui.sameLine()
            ImGui.textColored(1f, 0.3f, 0.3f, 1f, "!")
        }
    }

    private fun choice(label: String, options: List<String>, current: String, set: (String) -> Unit) {
        val index = options.indexOf(current).coerceAtLeast(0)
        val selected = ImInt(index)
        if (ImGui.combo(label, selected, options.toTypedArray())) {
            set(options[selected.get().coerceIn(options.indices)])
            CrashsImGuiConfigSync.touch()
        }
    }

    private fun text(
        label: String,
        get: () -> String,
        set: (String) -> Unit,
        validate: (String) -> String? = { null },
    ) {
        val key = key(label)
        val buffer = textBuffers.getOrPut(key) { ImString(get(), 2048) }
        ImGui.setNextItemWidth(-70f)
        ImGui.inputText("##text$key", buffer, 2048)
        ImGui.sameLine()
        val error = validate(buffer.get())
        if (ImGui.button("保存##$key")) {
            if (error == null) {
                set(buffer.get())
                CrashsImGuiConfigSync.touch()
            }
        }
        if (error != null) {
            ImGui.textColored(1f, 0.35f, 0.35f, 1f, error)
        }
    }

    private fun color(label: String, get: () -> Int, set: (Int) -> Unit) {
        val key = key(label)
        val rgba = colorBuffers.getOrPut(key) { argbToRgba(get()) }
        if (ImGui.colorEdit4(label, rgba)) {
            set(rgbaToArgb(rgba))
            CrashsImGuiConfigSync.touch()
        }
    }

    private fun formatValue(value: Float, format: String): String =
        if (format.contains("f")) String.format(java.util.Locale.ROOT, format, value) else value.toString()

    private fun argbToRgba(argb: Int): FloatArray = floatArrayOf(
        ((argb ushr 16) and 0xFF) / 255f,
        ((argb ushr 8) and 0xFF) / 255f,
        (argb and 0xFF) / 255f,
        ((argb ushr 24) and 0xFF) / 255f,
    )

    private fun rgbaToArgb(rgba: FloatArray): Int {
        val a = (rgba[3].coerceIn(0f, 1f) * 255f).toInt()
        val r = (rgba[0].coerceIn(0f, 1f) * 255f).toInt()
        val g = (rgba[1].coerceIn(0f, 1f) * 255f).toInt()
        val b = (rgba[2].coerceIn(0f, 1f) * 255f).toInt()
        return (a shl 24) or (r shl 16) or (g shl 8) or b
    }
}
