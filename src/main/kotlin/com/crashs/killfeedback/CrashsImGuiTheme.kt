package com.crashs.killfeedback

import imgui.ImFontConfig
import imgui.ImGui
import imgui.flag.ImGuiCol

/** Fonts and colors for the Crashs Legacy ImGui surface. */
object CrashsImGuiTheme {

    private var initialized = false
    private var fontData: ByteArray? = null

    fun initialize() {
        if (initialized) return
        initialized = true

        val bytes = CrashsImGuiScreen::class.java
            .getResourceAsStream("/assets/crashs-legacy/fonts/misans.ttf")
            ?.use { it.readBytes() }
        if (bytes != null) {
            fontData = bytes
            val io = ImGui.getIO()
            val config = ImFontConfig()
            config.setMergeMode(true)
            config.setFontDataOwnedByAtlas(false)
            config.setOversampleH(1)
            config.setOversampleV(1)
            config.setPixelSnapH(false)
            io.fonts.addFontFromMemoryTTF(
                bytes,
                18f,
                config,
                io.fonts.glyphRangesChineseFull,
            )
            io.fonts.build()
        }

        val style = ImGui.getStyle()
        style.setWindowRounding(0f)
        style.setChildRounding(0f)
        style.setFrameRounding(2f)
        style.setGrabRounding(2f)
        style.setWindowPadding(10f, 10f)
        style.setFramePadding(6f, 4f)
        style.setItemSpacing(6f, 6f)
        style.setColor(ImGuiCol.WindowBg, 0.055f, 0.055f, 0.055f, 1f)
        style.setColor(ImGuiCol.ChildBg, 0.075f, 0.075f, 0.075f, 1f)
        style.setColor(ImGuiCol.PopupBg, 0.08f, 0.08f, 0.08f, 1f)
        style.setColor(ImGuiCol.Border, 0.16f, 0.16f, 0.16f, 1f)
        style.setColor(ImGuiCol.FrameBg, 0.11f, 0.11f, 0.11f, 1f)
        style.setColor(ImGuiCol.FrameBgHovered, 0.16f, 0.16f, 0.16f, 1f)
        style.setColor(ImGuiCol.FrameBgActive, 0.2f, 0.2f, 0.2f, 1f)
        style.setColor(ImGuiCol.TitleBg, 0.08f, 0.08f, 0.08f, 1f)
        style.setColor(ImGuiCol.TitleBgActive, 0.1f, 0.1f, 0.1f, 1f)
        style.setColor(ImGuiCol.Header, 0.18f, 0.18f, 0.18f, 1f)
        style.setColor(ImGuiCol.HeaderHovered, 0.24f, 0.24f, 0.24f, 1f)
        style.setColor(ImGuiCol.HeaderActive, 0.3f, 0.3f, 0.3f, 1f)
        style.setColor(ImGuiCol.Button, 0.14f, 0.14f, 0.14f, 1f)
        style.setColor(ImGuiCol.ButtonHovered, 0.22f, 0.22f, 0.22f, 1f)
        style.setColor(ImGuiCol.ButtonActive, 0.3f, 0.3f, 0.3f, 1f)
        style.setColor(ImGuiCol.Text, 0.92f, 0.92f, 0.92f, 1f)
        style.setColor(ImGuiCol.TextDisabled, 0.45f, 0.45f, 0.45f, 1f)
        style.setColor(ImGuiCol.Separator, 0.2f, 0.2f, 0.2f, 1f)
    }
}
