package com.crashs.killfeedback

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import net.fabricmc.loader.api.FabricLoader
import java.nio.file.Files
import java.nio.file.Path

/**
 * Flat JSON config so the mod has no dependency on a config-screen library.
 */
data class KillFeedbackConfig(
    var enabled: Boolean = true,
    var intercept: Boolean = true,
    var includeOverlay: Boolean = false,
    var pattern: String = KillRewardParser.DEFAULT_PATTERN,

    var sound: Boolean = true,
    var soundVolume: Float = 1f,
    var soundPitch: Float = 1f,
    var comboGroup: String = ComboGroup.CF.name,
    var postMode: String = PostComboMode.LAST_OF_GROUP.name,
    var specificSound: String = PostComboSound.KILL_DF.name,

    var hud: Boolean = true,
    var hudScale: Float = 1f,
    var hudRight: Boolean = true,
    // Percent of the scaled screen: 0 = anchored edge, 100 = opposite edge.
    var hudX: Float = 2f,
    var hudY: Float = 10f,
    var statsWindowSeconds: Float = 8f,
    var maxRows: Int = 5,
    var entryDurationSeconds: Float = 5f,
    var headerStyle: String = "FULL",
    var background: Boolean = false,
    var showProgress: Boolean = true,
    var showCoins: Boolean = true,
    var showKills: Boolean = true,
    var showSource: Boolean = true,
    var showLevel: Boolean = true,
    var showAmount: Boolean = true,
    var numberFormat: String = "RAW",
    var statsMode: String = "TIMED",
    var rowSpacing: Float = 1f,
    var pendingEvictionAlpha: Float = 0.55f,
    var progressHeight: Float = 2f,
    var amountScroll: Boolean = true,

    var coinColor: Int = 0xFFFFD23C.toInt(),
    var sourceColor: Int = 0xFFFFFFFF.toInt(),
    var levelColor: Int = 0xFFFFDC50.toInt(),
    var headerColor: Int = 0xFFFFFFFF.toInt(),
    var dividerColor: Int = 0x40FFFFFF,
    var backgroundColor: Int = 0xAA08080C.toInt(),
    var outlineColor: Int = 0x40FFFFFF,
    var progressColor: Int = 0xFFFFD23C.toInt(),
    var progressBackground: Int = 0x40FFFFFF,

    var serverDamageEnabled: Boolean = false,
    var serverDamageBackground: Boolean = false,
    var serverDamageRemove: Boolean = false,
    var serverDamageShadow: Boolean = false,
)

object KillFeedbackConfigManager {

    private const val FILE_NAME = "crashs-legacy.json"
    private const val LEGACY_FILE_NAME = "killfeedback.json"
    private val gson = GsonBuilder()
        .setPrettyPrinting()
        .disableHtmlEscaping()
        .create()

    private val path: Path
        get() = FabricLoader.getInstance().configDir.resolve(FILE_NAME)

    private val legacyPath: Path
        get() = FabricLoader.getInstance().configDir.resolve(LEGACY_FILE_NAME)

    @Volatile
    var config: KillFeedbackConfig = KillFeedbackConfig()
        private set

    fun load() {
        val sourcePath = when {
            Files.exists(path) -> path
            Files.exists(legacyPath) -> legacyPath
            else -> null
        }
        config = runCatching {
            if (sourcePath == null) {
                KillFeedbackConfig()
            } else {
                Files.newBufferedReader(sourcePath).use { reader ->
                    val json = JsonParser.parseReader(reader).asJsonObject
                    val loaded = gson.fromJson(json, KillFeedbackConfig::class.java) ?: KillFeedbackConfig()
                    fillMissingDefaults(loaded, json)
                    loaded
                }
            }
        }.getOrElse {
            KillFeedbackConfig()
        }
        if (config.pattern == KillRewardParser.LEGACY_DEFAULT_PATTERN) {
            config.pattern = KillRewardParser.DEFAULT_PATTERN
            save()
        } else if (sourcePath != path) {
            save()
        }
    }

    private fun fillMissingDefaults(config: KillFeedbackConfig, json: JsonObject) {
        val defaults = KillFeedbackConfig()
        KillFeedbackConfig::class.java.declaredFields
            .filter { !java.lang.reflect.Modifier.isStatic(it.modifiers) && !it.isSynthetic }
            .forEach { field ->
                if (!json.has(field.name)) {
                    runCatching {
                        field.isAccessible = true
                        field.set(config, field.get(defaults))
                    }
                }
            }
    }

    fun apply(newConfig: KillFeedbackConfig) {
        config = newConfig.copy()
        save()
    }

    fun save() {
        runCatching {
            Files.createDirectories(path.parent)
            Files.newBufferedWriter(path).use { writer ->
                gson.toJson(config, writer)
            }
        }
    }

    fun reset() {
        config = KillFeedbackConfig()
        save()
        KillFeedState.reset()
    }
}
