package com.crashs.killfeedback.serverdamage

import com.crashs.killfeedback.KillFeedbackConfigManager
import net.minecraft.client.MinecraftClient
import net.minecraft.entity.decoration.DisplayEntity
import net.minecraft.network.packet.s2c.play.EntityTrackerUpdateS2CPacket
import net.minecraft.text.Text

/**
 * Applies the optional server TextDisplay damage-number controls.
 *
 * The packet carries serialized tracker entries; text is identified by value type, so this
 * implementation does not depend on private TEXT/BACKGROUND/FLAGS field names.
 */
object ServerDamageDisplayManager {

    private enum class DamageTextKind {
        ELEMENTAL,
        PLAYER_HURT,
    }

    private val hurtPattern = Regex("""^-\d+(?:\.\d+)?$""")
    private val trackedIds = LinkedHashSet<Int>()
    private val trackedKinds = HashMap<Int, DamageTextKind>()
    private val originalBackgrounds = HashMap<Int, Int>()
    private val originalFlags = HashMap<Int, Byte>()
    private val lock = Any()

    fun shouldCancel(packet: EntityTrackerUpdateS2CPacket): Boolean {
        val config = KillFeedbackConfigManager.config
        if (!config.serverDamageEnabled || !config.serverDamageRemove) return false

        val text = textOf(packet) ?: return false
        val kind = classify(text) ?: return false

        val id = packet.id()
        synchronized(lock) {
            trackedIds += id
            trackedKinds[id] = kind
        }
        entityOf(id)?.discard()
        return true
    }

    fun applyStyle(packet: EntityTrackerUpdateS2CPacket) {
        val config = KillFeedbackConfigManager.config
        if (!config.serverDamageEnabled || config.serverDamageRemove) return
        if (!config.serverDamageBackground && !config.serverDamageShadow) return

        val entity = entityOf(packet.id()) as? DisplayEntity.TextDisplayEntity ?: return
        val kind = classify(entity.text) ?: return

        val id = packet.id()
        synchronized(lock) {
            trackedIds += id
            trackedKinds[id] = kind
            originalBackgrounds.putIfAbsent(id, entity.background)
            originalFlags.putIfAbsent(id, entity.displayFlags)
        }

        if (config.serverDamageBackground) {
            entity.background = 0
        }

        var flags = entity.displayFlags.toInt()
        if (config.serverDamageShadow) {
            flags = flags or DisplayEntity.TextDisplayEntity.SHADOW_FLAG.toInt()
        }
        if (config.serverDamageBackground) {
            flags = flags and DisplayEntity.TextDisplayEntity.DEFAULT_BACKGROUND_FLAG.toInt().inv()
        }
        entity.displayFlags = flags.toByte()
    }

    fun forget(ids: Iterable<Int>) {
        synchronized(lock) {
            for (id in ids) {
                trackedIds -= id
                trackedKinds -= id
                originalBackgrounds -= id
                originalFlags -= id
            }
        }
    }

    fun clearWorld() {
        synchronized(lock) {
            trackedIds.clear()
            trackedKinds.clear()
            originalBackgrounds.clear()
            originalFlags.clear()
        }
    }

    /**
     * Restore entities when the feature is disabled, or when only part of it is turned off.
     */
    fun syncState() {
        val config = KillFeedbackConfigManager.config
        if (!config.serverDamageEnabled) {
            restoreAll()
            return
        }
        if (!config.serverDamagePlayerHurt) {
            restoreKind(DamageTextKind.PLAYER_HURT)
        }
        if (!config.serverDamageBackground) {
            restoreBackgrounds()
        }
        if (!config.serverDamageShadow) {
            restoreFlags()
        }
    }

    fun restoreAll() {
        val level = MinecraftClient.getInstance().world
        synchronized(lock) {
            if (level != null) {
                for (id in trackedIds) {
                    val entity = level.getEntityById(id) as? DisplayEntity.TextDisplayEntity ?: continue
                    originalBackgrounds[id]?.let { entity.background = it }
                    originalFlags[id]?.let { entity.displayFlags = it }
                }
            }
            trackedIds.clear()
            trackedKinds.clear()
            originalBackgrounds.clear()
            originalFlags.clear()
        }
    }

    private fun restoreKind(kind: DamageTextKind) {
        val level = MinecraftClient.getInstance().world
        synchronized(lock) {
            val ids = trackedKinds.filterValues { it == kind }.keys.toList()
            if (level != null) {
                for (id in ids) {
                    val entity = level.getEntityById(id) as? DisplayEntity.TextDisplayEntity ?: continue
                    originalBackgrounds[id]?.let { entity.background = it }
                    originalFlags[id]?.let { entity.displayFlags = it }
                }
            }
            for (id in ids) {
                trackedIds -= id
                trackedKinds -= id
                originalBackgrounds -= id
                originalFlags -= id
            }
        }
    }

    private fun classify(text: Text): DamageTextKind? {
        if (DamageElement.parse(text) != null) return DamageTextKind.ELEMENTAL
        if (KillFeedbackConfigManager.config.serverDamagePlayerHurt && hurtPattern.matches(text.string.trim())) {
            return DamageTextKind.PLAYER_HURT
        }
        return null
    }

    private fun restoreBackgrounds() {
        val level = MinecraftClient.getInstance().world
        synchronized(lock) {
            if (level != null) {
                for ((id, color) in originalBackgrounds) {
                    val entity = level.getEntityById(id) as? DisplayEntity.TextDisplayEntity ?: continue
                    entity.background = color
                }
            }
            originalBackgrounds.clear()
        }
    }

    private fun restoreFlags() {
        val level = MinecraftClient.getInstance().world
        synchronized(lock) {
            if (level != null) {
                for ((id, flags) in originalFlags) {
                    val entity = level.getEntityById(id) as? DisplayEntity.TextDisplayEntity ?: continue
                    entity.displayFlags = flags
                }
            }
            originalFlags.clear()
        }
    }

    private fun textOf(packet: EntityTrackerUpdateS2CPacket): Text? =
        packet.trackedValues().firstNotNullOfOrNull { it.value() as? Text }

    private fun entityOf(id: Int): DisplayEntity.TextDisplayEntity? =
        MinecraftClient.getInstance().world?.getEntityById(id) as? DisplayEntity.TextDisplayEntity
}

