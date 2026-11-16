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
    var comboGroup: String = ComboGroup.NONE.name,
    var postMode: String = PostComboMode.SPECIFIC.name,
    var specificSound: String = PostComboSound.KILL_DF.name,

    var hud: Boolean = true,
    var hudScale: Float = 1f,
    var hudRight: Boolean = false,
    // Percent of the scaled screen: 0 = anchored edge, 100 = opposite edge.
    var hudX: Float = 50f,
    var hudY: Float = 50f,
    var statsWindowSeconds: Float = 8f,
    var maxRows: Int = 5,
    var entryDurationSeconds: Float = 5f,
    var headerStyle: String = "FULL",
    var background: Boolean = false,
    var hudOutline: Boolean = true,
    var hudTextShadow: Boolean = true,
    var entryPushAnimation: Boolean = true,
    var textRevealAnimation: Boolean = true,
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

    var serverDamageEnabled: Boolean = true,
    var serverDamageBackground: Boolean = true,
    var serverDamageRemove: Boolean = false,
    var serverDamageShadow: Boolean = true,
    var serverDamagePlayerHurt: Boolean = true,

    var coinsInfoEnabled: Boolean = false,
    var coinsInfoScale: Float = 1f,
    var coinsInfoRight: Boolean = false,
    var coinsInfoX: Float = 35f,
    var coinsInfoY: Float = 50f,
    var coinsInfoTwoLines: Boolean = true,
    var coinsInfoScroll: Boolean = true,
    var coinsInfoShowTotal: Boolean = true,
    var coinsInfoShowAverage: Boolean = true,
    var coinsInfoAverageUnit: String = "MINUTE",
    var coinsInfoNumberFormat: String = "RAW",
    var coinsInfoTotalColor: Int = 0xFFFFFFFF.toInt(),
    var coinsInfoAverageColor: Int = 0xFFFFFFFF.toInt(),
    var coinsInfoSessionTimeColor: Int = 0xFFFFFFFF.toInt(),
    var coinsInfoKillsColor: Int = 0xFFFFFFFF.toInt(),
    var coinsInfoDividerColor: Int = 0xFFFFD23C.toInt(),
    var coinsInfoDividerBackground: Int = 0x40FFFFFF,
    var coinsInfoBackground: Boolean = false,
    var coinsInfoBackgroundColor: Int = 0xAA08080C.toInt(),
    var coinsInfoOutline: Boolean = true,
    var coinsInfoOutlineColor: Int = 0x40FFFFFF,
    var coinsInfoTextShadow: Boolean = true,
    var coinsInfoShowSessionTime: Boolean = true,
    var coinsInfoShowKills: Boolean = true,

    var broadcastEnabled: Boolean = false,
    var broadcastHideChat: Boolean = false,
    var broadcastBossBar: Boolean = false,
    var broadcastSeconds: Float = 8f,
    var broadcastWriteFile: Boolean = false,
    var broadcastSound: Boolean = false,
    var broadcastSoundMode: String = "ENDER_DRAGON",

    var broadcast: BroadcastSettings = BroadcastSettings(),
    var defaultsVersion: Int = 0,
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
        @Suppress("SENSELESS_COMPARISON")
        if (config.broadcast == null) {
            config.broadcast = BroadcastSettings()
        }
        if (sourcePath == null) {
            config.broadcast.migrated = true
            config.defaultsVersion = 1
        }
        var changed = BroadcastConfigMigrator.migrate(config)
        if (config.defaultsVersion < 1) {
            applyDefaultProfile(config)
            config.defaultsVersion = 1
            changed = true
        }
        if (changed) {
            save()
        }
        if (config.pattern == KillRewardParser.LEGACY_DEFAULT_PATTERN ||
            config.pattern == KillRewardParser.PREVIOUS_DEFAULT_PATTERN
        ) {
            config.pattern = KillRewardParser.DEFAULT_PATTERN
            save()
        } else if (sourcePath != path) {
            save()
        }
    }

    private fun applyDefaultProfile(config: KillFeedbackConfig) {
        config.enabled = true
        config.intercept = true
        config.includeOverlay = false
        config.comboGroup = ComboGroup.NONE.name
        config.postMode = PostComboMode.SPECIFIC.name
        config.specificSound = PostComboSound.KILL_DF.name

        config.coinsInfoTwoLines = true
        config.coinsInfoX = 35f
        config.coinsInfoY = 50f
        config.coinsInfoRight = false

        config.serverDamageEnabled = true
        config.serverDamageBackground = true
        config.serverDamageRemove = false
        config.serverDamageShadow = true
        config.serverDamagePlayerHurt = true

        config.broadcast.enabled = false
        config.broadcast.writeFile = true
        config.broadcast.fileFormat = "CSV"
        config.broadcast.migrated = true
        config.broadcast.own = BroadcastPlayerSettings.default(showChat = true, showBossBar = true)
        config.broadcast.other = BroadcastPlayerSettings.default(showChat = false, showBossBar = true)
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
        config = newConfig.deepCopy()
        config.broadcast.migrated = true
        config.defaultsVersion = 1
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
