package com.crashs.killfeedback

import cn.enaium.fabric.imgui.ImGuiRenderable
import imgui.ImGui
import imgui.ImGuiIO
import imgui.flag.ImGuiCond
import imgui.flag.ImGuiWindowFlags
import imgui.type.ImBoolean
import imgui.type.ImFloat
import imgui.type.ImInt
import imgui.type.ImString
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.screen.Screen
import net.minecraft.text.Text

/**
 * Crashs Legacy ImGui shell.
 *
 * Settings are written live; only text fields have an explicit per-field Save button.
 */
class CrashsImGuiScreen(
    private val parent: Screen?,
    initialCategory: Int? = null,
) : Screen(Text.literal("Crashs Legacy")), ImGuiRenderable {

    private var binder = CrashsImGuiConfigBinder()
    private var category = (initialCategory ?: CrashsImGuiUiState.category(0))
        .coerceIn(0, 5)
    private var firstFrame = true
    private var resetRequestedAt = 0L
    private var resetPopupOpen = false
    private val testBool = ImBoolean(true)
    private val testRadio = ImInt(0)
    private val testCombo = ImInt(0)
    private val testText = ImString("Crashs Legacy", 128)
    private val testInt = ImInt(42)
    private val testIntArray = intArrayOf(42)
    private val testFloat = ImFloat(0.65f)
    private val testSlider = floatArrayOf(0.5f)
    private val testColor = floatArrayOf(1f, 0.38f, 0.35f, 1f)
    private val testList = ImInt(0)
    private val comboOptions = arrayOf("选项 A", "选项 B", "选项 C")
    private val listOptions = arrayOf("列表项 1", "列表项 2", "列表项 3")
    private val testMultiline = ImString("多行文本\n第二行", 256)
    private val testInt3 = intArrayOf(1, 2, 3)
    private val testFloat2 = floatArrayOf(0.25f, 0.75f)
    private val testRangeMin = floatArrayOf(0.2f)
    private val testRangeMax = floatArrayOf(0.8f)
    private val testPlot = floatArrayOf(0.1f, 0.4f, 0.2f, 0.8f, 0.6f, 0.9f, 0.3f)
    private val categoryNames = arrayOf(
        "击杀反馈",
        "金币统计",
        "服务器伤害",
        "播报管理",
        "播报查询",
        "控件测试",
    )

    init {
        CrashsImGuiTheme.initialize()
        CrashsImGuiUiState.setCategory(category)
    }

    override fun render(io: ImGuiIO) {
        CrashsImGuiConfigSync.tick()
        val width = io.displaySizeX
        val height = io.displaySizeY
        if (firstFrame) {
            firstFrame = false
            CrashsImGuiUiState.windowSize().let { (w, h) ->
                ImGui.setNextWindowSize(w, h, ImGuiCond.FirstUseEver)
            }
            val saved = CrashsImGuiUiState.windowPosition()
            if (saved != null) {
                ImGui.setNextWindowPos(saved.first, saved.second, ImGuiCond.FirstUseEver)
            } else {
                ImGui.setNextWindowPos((width - 760f) / 2f, (height - 480f) / 2f, ImGuiCond.FirstUseEver)
            }
        }

        if (ImGui.begin("Crashs Legacy", ImGuiWindowFlags.NoCollapse)) {
            if (ImGui.beginChild("##sidebar", 150f, 0f, true)) {
                ImGui.text("分类")
                ImGui.separator()
                for ((index, name) in categoryNames.withIndex()) {
                    if (ImGui.selectable(name, category == index)) {
                        category = index
                        CrashsImGuiUiState.setCategory(index)
                    }
                }
                ImGui.spacing()
                ImGui.separator()
                if (ImGui.button("恢复默认")) {
                    resetRequestedAt = System.currentTimeMillis()
                    resetPopupOpen = true
                    ImGui.openPopup("确认恢复默认")
                }
                if (ImGui.button("关闭")) {
                    close()
                }
                ImGui.endChild()
            }

            ImGui.sameLine()
            if (ImGui.beginChild("##content", 0f, 0f, false)) {
                ImGui.text(categoryNames[category])
                ImGui.separator()
                ImGui.spacing()
                when (category) {
                    0 -> binder.drawKillFeedback()
                    1 -> binder.drawCoinsInfo()
                    2 -> binder.drawServerDamage()
                    3 -> binder.drawBroadcast()
                    4 -> drawQueryEntry()
                    else -> drawWidgetTest()
                }
                ImGui.endChild()
            }

            drawResetPopup()
            val pos = ImGui.getWindowPos()
            val size = ImGui.getWindowSize()
            CrashsImGuiUiState.updateWindow(pos.x, pos.y, size.x, size.y)
        }
        ImGui.end()
    }

    private fun drawResetPopup() {
        if (!resetPopupOpen) return
        if (ImGui.beginPopupModal("确认恢复默认")) {
            val elapsed = System.currentTimeMillis() - resetRequestedAt
            val remain = ((10_000L - elapsed) / 1000L).coerceAtLeast(0L)
            ImGui.text("确定恢复所有 Crashs Legacy 配置的默认值吗？")
            ImGui.text("窗口将在 ${remain} 秒后自动取消。")
            ImGui.spacing()
            if (ImGui.button("确认恢复")) {
                KillFeedbackConfigManager.apply(KillFeedbackConfig())
                CrashsImGuiConfigSync.flush()
                binder = CrashsImGuiConfigBinder()
                resetPopupOpen = false
                ImGui.closeCurrentPopup()
                category = 0
                CrashsImGuiUiState.setCategory(0)
            }
            ImGui.sameLine()
            if (ImGui.button("取消")) {
                resetPopupOpen = false
                ImGui.closeCurrentPopup()
            }
            if (elapsed >= 10_000L) {
                resetPopupOpen = false
                ImGui.closeCurrentPopup()
            }
            ImGui.endPopup()
        }
    }

    private fun drawQueryEntry() {
        ImGui.text("播报查询")
        ImGui.textWrapped("历史记录查询继续使用现有 CSV / SQLite 查询界面。")
        ImGui.spacing()
        if (ImGui.button("打开播报查询")) {
            CrashsImGuiConfigSync.flush()
            CrashsImGuiUiState.save()
            MinecraftClient.getInstance().setScreen(BroadcastQueryScreen(this))
        }
    }

    override fun close() {
        CrashsImGuiConfigSync.flush()
        CrashsImGuiUiState.save()
        MinecraftClient.getInstance().setScreen(parent)
    }

    override fun shouldPause(): Boolean = false

    private fun drawWidgetTest() {
        ImGui.text("控件测试页")
        ImGui.textWrapped("用于验证 ImGui 默认控件的渲染、输入、弹出层和中文显示。")
        ImGui.separator()

        ImGui.collapsingHeader("文本与按钮")
        ImGui.text("普通文本")
        ImGui.textDisabled("禁用文本")
        ImGui.bulletText("项目符号文本")
        if (ImGui.button("普通按钮")) {
            // Test-only button; no persistent action.
        }
        ImGui.sameLine()
        ImGui.smallButton("小按钮")
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("这是小按钮的 tooltip")
        }

        ImGui.collapsingHeader("选择控件")
        ImGui.checkbox("复选框", testBool)
        ImGui.radioButton("单选 A", testRadio, 0)
        ImGui.sameLine()
        ImGui.radioButton("单选 B", testRadio, 1)
        ImGui.combo("下拉框", testCombo, comboOptions)
        if (ImGui.beginCombo("手动下拉框", comboOptions[testCombo.get()])) {
            for ((index, option) in comboOptions.withIndex()) {
                if (ImGui.selectable(option, testCombo.get() == index)) {
                    testCombo.set(index)
                }
            }
            ImGui.endCombo()
        }

        ImGui.collapsingHeader("输入控件")
        ImGui.inputText("文本输入", testText)
        ImGui.inputInt("整数输入", testInt)
        ImGui.inputFloat("浮点输入", testFloat)
        ImGui.sliderFloat("浮点滑块", testSlider, 0f, 1f, "%.2f")
        ImGui.sliderInt("整数滑块", testIntArray, 0, 100)
        ImGui.dragFloat("浮点拖拽", testSlider, 0.01f, 0f, 1f)
        ImGui.dragInt("整数拖拽", testIntArray, 1f, 0, 100)

        ImGui.collapsingHeader("颜色与进度")
        ImGui.colorEdit4("颜色编辑", testColor)
        ImGui.colorPicker4("颜色选择器", testColor)
        ImGui.progressBar(testSlider[0], 160f, 18f, "进度 50%")

        ImGui.collapsingHeader("列表、树与折叠")
        if (ImGui.beginListBox("列表", 180f, 72f)) {
            for ((index, option) in listOptions.withIndex()) {
                if (ImGui.selectable(option, testList.get() == index)) {
                    testList.set(index)
                }
            }
            ImGui.endListBox()
        }
        if (ImGui.treeNode("树节点")) {
            ImGui.text("树节点内容")
            ImGui.treePop()
        }
        ImGui.collapsingHeader("折叠标题")

        ImGui.collapsingHeader("扩展控件")
        ImGui.inputTextMultiline("多行输入", testMultiline, 260f, 54f)
        ImGui.inputInt3("整数三元组", testInt3)
        ImGui.sliderFloat2("浮点二维", testFloat2, 0f, 1f, "%.2f")
        ImGui.dragFloatRange2("范围拖拽", testRangeMin, testRangeMax, 0.01f, 0f, 1f)
        ImGui.vSliderFloat("垂直滑块", 18f, 72f, testSlider, 0f, 1f)
        ImGui.colorButton("颜色按钮", testColor)
        ImGui.plotLines("曲线", testPlot, testPlot.size)
        if (ImGui.beginTabBar("标签栏")) {
            if (ImGui.beginTabItem("标签一")) {
                ImGui.text("标签一内容")
                ImGui.endTabItem()
            }
            if (ImGui.beginTabItem("标签二")) {
                ImGui.text("标签二内容")
                ImGui.endTabItem()
            }
            ImGui.endTabBar()
        }
        if (ImGui.beginTable("表格", 2)) {
            ImGui.tableSetupColumn("项目")
            ImGui.tableSetupColumn("值")
            ImGui.tableHeadersRow()
            ImGui.tableNextRow()
            ImGui.tableNextColumn()
            ImGui.text("示例")
            ImGui.tableNextColumn()
            ImGui.text("123")
            ImGui.endTable()
        }

        ImGui.collapsingHeader("弹出窗口")
        if (ImGui.button("打开弹窗")) {
            ImGui.openPopup("控件测试弹窗")
        }
        if (ImGui.beginPopupModal("控件测试弹窗")) {
            ImGui.text("这是一个 ImGui 模态弹窗。")
            if (ImGui.button("关闭")) {
                ImGui.closeCurrentPopup()
            }
            ImGui.endPopup()
        }
    }

}
