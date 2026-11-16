package com.crashs.killfeedback

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import net.fabricmc.loader.api.FabricLoader
import java.nio.file.Files
import java.nio.file.Path

/** Persistent window and fold state for the Crashs Legacy ImGui surface. */
object CrashsImGuiUiState {

    private data class State(
        var category: Int = 0,
        var folds: MutableMap<String, Boolean> = mutableMapOf(),
        var windowX: Float = Float.NaN,
        var windowY: Float = Float.NaN,
        var windowWidth: Float = 760f,
        var windowHeight: Float = 480f,
    )

    private val gson = GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()
    private val path: Path get() = FabricLoader.getInstance().configDir.resolve("crashs-legacy-imgui.json")
    private var state = State()
    private var loaded = false

    private fun ensureLoaded() {
        if (loaded) return
        loaded = true
        runCatching {
            if (!Files.exists(path)) return@runCatching
            val root = JsonParser.parseString(Files.readString(path)).asJsonObject
            state.category = root.get("category")?.asInt ?: 0
            state.windowWidth = root.get("windowWidth")?.asFloat ?: 760f
            state.windowHeight = root.get("windowHeight")?.asFloat ?: 480f
            state.windowX = root.get("windowX")?.asFloat ?: Float.NaN
            state.windowY = root.get("windowY")?.asFloat ?: Float.NaN
            root.getAsJsonObject("folds")?.entrySet()?.forEach { (key, value) ->
                state.folds[key] = value.asBoolean
            }
        }
    }

    fun category(fallback: Int): Int {
        ensureLoaded()
        return state.category.takeIf { it >= 0 } ?: fallback
    }

    fun setCategory(value: Int) {
        ensureLoaded()
        state.category = value
    }

    fun isOpen(id: String): Boolean {
        ensureLoaded()
        return state.folds[id] ?: false
    }

    fun setOpen(id: String, value: Boolean) {
        ensureLoaded()
        state.folds[id] = value
    }

    fun windowPosition(): Pair<Float, Float>? {
        ensureLoaded()
        if (state.windowX.isNaN() || state.windowY.isNaN()) return null
        return state.windowX to state.windowY
    }

    fun windowSize(): Pair<Float, Float> {
        ensureLoaded()
        return state.windowWidth to state.windowHeight
    }

    fun updateWindow(x: Float, y: Float, width: Float, height: Float) {
        ensureLoaded()
        state.windowX = x
        state.windowY = y
        state.windowWidth = width
        state.windowHeight = height
    }

    fun save() {
        ensureLoaded()
        runCatching {
            Files.createDirectories(path.parent)
            val root = JsonObject()
            root.addProperty("category", state.category)
            root.addProperty("windowX", state.windowX)
            root.addProperty("windowY", state.windowY)
            root.addProperty("windowWidth", state.windowWidth)
            root.addProperty("windowHeight", state.windowHeight)
            val folds = JsonObject()
            state.folds.forEach { (key, value) -> folds.addProperty(key, value) }
            root.add("folds", folds)
            Files.writeString(path, gson.toJson(root))
        }
    }
}
