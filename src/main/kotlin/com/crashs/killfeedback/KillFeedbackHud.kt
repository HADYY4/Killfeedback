package com.crashs.killfeedback

import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.DrawContext
import net.minecraft.text.Text
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Vanilla DrawContext HUD.
 */
object KillFeedbackHud {

    private const val PADDING = 4f
    private const val HEADER_GAP = 3f
    private const val PROGRESS_GAP = 3f
    private const val MIN_WIDTH = 86f
    private const val AMOUNT_SCROLL_MS = 600f
    private const val MOB_REVEAL_MS = 260f

    private var displayedCoins = 0.0
    private var displayedKills = 0f
    private var lastTotalCoins = 0.0
    private var lastTotalKills = 0

    private var widthGeneration = Long.MIN_VALUE
    private var widthLayoutKey = Int.MIN_VALUE
    private var cachedContentWidth = 0f

    fun render(context: DrawContext) {
        val client = MinecraftClient.getInstance()
        val config = KillFeedbackConfigManager.config
        if (!config.enabled || !config.hud) return
        if (client.player == null || client.options.hudHidden || client.currentScreen != null) return

        val now = System.currentTimeMillis()
        KillFeedState.update(
            now = now,
            entryDurationMs = (config.entryDurationSeconds * 1000f).toLong(),
            maxRows = config.maxRows,
            statsWindowMs = (config.statsWindowSeconds * 1000f).toLong(),
            timedStatsWindow = config.statsMode.equals("TIMED", ignoreCase = true),
        )
        val snapshot = KillFeedState.snapshot()
        val headerStyle = config.headerStyle.uppercase()
        if (headerStyle == "HIDDEN" && snapshot.entries.isEmpty()) return

        val font = client.textRenderer
        val scale = config.hudScale.coerceIn(0.5f, 2.5f)
        animateTotals(snapshot)

        val headerVisible = headerStyle != "HIDDEN" && when (headerStyle) {
            "FULL" -> config.showCoins || config.showKills
            "COMPACT" -> config.showCoins || config.showKills
            "COINS_ONLY" -> config.showCoins
            "KILLS_ONLY" -> config.showKills
            else -> true
        }
        val targetContentWidth = max(
            MIN_WIDTH,
            max(
                if (headerVisible) headerWidth(font, headerStyle, config, snapshot) else 0f,
                snapshot.entries.maxOfOrNull { rowWidth(font, it, config) } ?: 0f,
            ) + PADDING * 2,
        )
        val layoutKey = widthLayoutKey(config, headerStyle)
        if (snapshot.generation != widthGeneration || layoutKey != widthLayoutKey) {
            widthGeneration = snapshot.generation
            widthLayoutKey = layoutKey
            cachedContentWidth = targetContentWidth
        } else {
            cachedContentWidth = max(cachedContentWidth, targetContentWidth)
        }
        val contentWidth = cachedContentWidth
        val headerHeight = if (headerVisible) font.fontHeight + HEADER_GAP else 0f
        val progressHeight = if (config.showProgress) config.progressHeight.coerceIn(1f, 8f) + PROGRESS_GAP else 2f
        val rowHeight = max(12f, font.fontHeight + 2f)
        val rowSpacing = config.rowSpacing.coerceIn(0f, 8f)
        val activeCount = snapshot.entries.count { !it.isFading }
        val reserveEvictionRow = activeCount >= config.maxRows.coerceIn(1, 20)
        val layoutRows = if (reserveEvictionRow) {
            max(config.maxRows.coerceIn(1, 20) + 1, snapshot.entries.size)
        } else {
            snapshot.entries.size
        }
        val listHeight = if (layoutRows == 0) 0f else
            layoutRows * rowHeight + (layoutRows - 1) * rowSpacing
        val height = PADDING * 2 + headerHeight + progressHeight + listHeight

        val screenWidth = context.scaledWindowWidth.toFloat()
        val screenHeight = context.scaledWindowHeight.toFloat()
        val percentX = config.hudX.coerceIn(0f, 100f)
        val percentY = config.hudY.coerceIn(0f, 100f)
        val x = if (config.hudRight) {
            screenWidth - contentWidth * scale - screenWidth * percentX / 100f
        } else {
            screenWidth * percentX / 100f
        }
        val y = screenHeight * percentY / 100f

        context.matrices.pushMatrix()
        context.matrices.translate(x, y)
        context.matrices.scale(scale, scale)

        if (config.background) {
            roundedRect(context, 0f, 0f, contentWidth, height, 3f, config.backgroundColor)
        }
        if (config.hudOutline) {
            roundedOutline(context, 0f, 0f, contentWidth, height, config.outlineColor)
        }

        var cursorY = PADDING
        if (headerVisible) {
            drawHeader(context, font, headerStyle, contentWidth, config)
            cursorY += headerHeight
        }

        drawProgress(context, snapshot, now, contentWidth, cursorY, config)
        cursorY += progressHeight

        val rowPitch = rowHeight + rowSpacing
        val pendingId = snapshot.entries
            .filter { !it.isFading }
            .takeIf { it.size >= config.maxRows }
            ?.lastOrNull()
            ?.id

        for (entry in snapshot.entries) {
            val age = now - entry.createdAt
            var alpha = entryAlpha(entry, now)
            if (entry.evicted || entry.id == pendingId) {
                alpha *= config.pendingEvictionAlpha.coerceIn(0.1f, 1f)
            }
            val insertOffset = if (age >= 160) 0f else (1f - easeOutCubic(age / 160f)) * 4f
            val pushOffset = if (!config.entryPushAnimation) {
                0f
            } else {
                entry.shiftedAt?.let { shiftedAt ->
                    val progress = easeOutCubic(((now - shiftedAt) / 180f).coerceIn(0f, 1f))
                    -(1f - progress) * rowPitch
                } ?: 0f
            }
            if (alpha > 0.01f) {
                drawRow(context, font, entry, contentWidth, cursorY + pushOffset + insertOffset, rowHeight, alpha, now, config)
            }
            cursorY += rowPitch
        }

        context.matrices.popMatrix()
    }

    private fun drawHeader(
        context: DrawContext,
        font: net.minecraft.client.font.TextRenderer,
        style: String,
        width: Float,
        config: KillFeedbackConfig,
    ) {
        val coins = "${formatNumber(displayedCoins, config)} Coins"
        val kills = "${formatNumber(displayedKills.roundToInt().toDouble(), config)} Kills"
        val color = config.headerColor

        when (style) {
            "COMPACT" -> {
                val text = when {
                    config.showCoins && config.showKills ->
                        "${formatShort(displayedCoins)}c  ${formatShort(displayedKills.roundToInt().toDouble())}k"
                    config.showCoins -> "${formatShort(displayedCoins)}c"
                    config.showKills -> "${formatShort(displayedKills.roundToInt().toDouble())}k"
                    else -> ""
                }
                if (text.isNotEmpty()) {
                    context.drawText(font, Text.literal(text), PADDING.toInt(), PADDING.toInt(), color, config.hudTextShadow)
                }
            }

            "COINS_ONLY" -> if (config.showCoins) {
                context.drawText(font, Text.literal(coins), PADDING.toInt(), PADDING.toInt(), color, config.hudTextShadow)
            }

            "KILLS_ONLY" -> if (config.showKills) {
                context.drawText(font, Text.literal(kills), PADDING.toInt(), PADDING.toInt(), color, config.hudTextShadow)
            }

            "HIDDEN" -> Unit

            else -> {
                if (config.showCoins) {
                    context.drawText(font, Text.literal(coins), PADDING.toInt(), PADDING.toInt(), color, config.hudTextShadow)
                }
                if (config.showKills) {
                    val killX = width - PADDING - font.getWidth(kills)
                    context.drawText(font, Text.literal(kills), killX.roundToInt(), PADDING.toInt(), color, config.hudTextShadow)
                }
            }
        }
    }

    private fun drawProgress(
        context: DrawContext,
        snapshot: KillFeedSnapshot,
        now: Long,
        width: Float,
        y: Float,
        config: KillFeedbackConfig,
    ) {
        if (!config.showProgress) return
        val x = PADDING
        val barWidth = width - PADDING * 2
        val barHeight = config.progressHeight.coerceIn(1f, 8f)
        val timed = snapshot.windowStartedAt > 0L && snapshot.windowEndsAt > snapshot.windowStartedAt

        if (timed) {
            val fill = ((snapshot.windowEndsAt - now).toFloat() /
                (snapshot.windowEndsAt - snapshot.windowStartedAt).toFloat()).coerceIn(0f, 1f)
            context.fill(
                x.toInt(),
                y.toInt(),
                (x + barWidth).toInt(),
                (y + barHeight).toInt(),
                config.progressBackground,
            )
            if (fill > 0f) {
                context.matrices.pushMatrix()
                context.matrices.translate(x, y)
                context.matrices.scale(fill, 1f)
                context.fill(
                    0,
                    0,
                    barWidth.toInt(),
                    barHeight.toInt(),
                    config.progressColor,
                )
                context.matrices.popMatrix()
            }
        } else {
            context.fill(x.toInt(), y.toInt(), (x + barWidth).toInt(), (y + 1f).toInt(), config.dividerColor)
        }
    }

    private fun drawRow(
        context: DrawContext,
        font: net.minecraft.client.font.TextRenderer,
        entry: KillFeedEntry,
        width: Float,
        y: Float,
        rowHeight: Float,
        alpha: Float,
        now: Long,
        config: KillFeedbackConfig,
    ) {
        val reward = entry.reward
        val age = System.currentTimeMillis() - entry.createdAt
        val source = buildString {
            if (config.showLevel && reward.level != null) {
                append("Lv.")
                append(reward.level)
                append(' ')
            }
            append(reward.mob)
        }
        val coins = if (config.amountScroll && age < AMOUNT_SCROLL_MS) {
            reward.coins * easeOutCubic(age / AMOUNT_SCROLL_MS)
        } else {
            reward.coins
        }
        val amount = "+${KillRewardParser.formatCoins(coins)}"
        val textY = (y + (rowHeight - font.fontHeight) / 2f).roundToInt()

        if (config.showSource) {
            drawRevealedSource(context, font, entry, textY, alpha, now, config)
        }
        if (config.showAmount) {
            val amountX = (width - PADDING - font.getWidth(amount)).roundToInt()
            context.drawText(
                font,
                Text.literal(amount),
                amountX,
                textY,
                withAlpha(config.coinColor, alpha),
                true,
            )
        }
    }

    private fun drawRevealedSource(
        context: DrawContext,
        font: net.minecraft.client.font.TextRenderer,
        entry: KillFeedEntry,
        textY: Int,
        alpha: Float,
        now: Long,
        config: KillFeedbackConfig,
    ) {
        val reward = entry.reward
        val segments = mutableListOf<Pair<String, Int>>()
        if (config.showLevel && reward.level != null) {
            segments += "Lv.${reward.level} " to config.levelColor
        }
        segments += reward.mob to config.sourceColor
        if (segments.isEmpty()) return

        val fullText = segments.joinToString("") { it.first }
        if (fullText.isEmpty()) return
        val fullWidth = font.getWidth(fullText)
        val reveal = if (!config.textRevealAnimation) {
            1f
        } else {
            easeOutCubic(((now - entry.createdAt) / MOB_REVEAL_MS).coerceIn(0f, 1f))
        }
        val visibleCount = (fullText.length * reveal).toInt().coerceIn(0, fullText.length)
        if (visibleCount <= 0) return
        val visibleStart = fullText.length - visibleCount
        val visibleText = fullText.substring(visibleStart)
        val visibleStartX = (PADDING + 2f) + fullWidth - font.getWidth(visibleText)

        var segmentStart = 0
        for ((segmentText, color) in segments) {
            val segmentEnd = segmentStart + segmentText.length
            val from = maxOf(visibleStart, segmentStart)
            val to = segmentEnd
            if (from < to) {
                val part = segmentText.substring(from - segmentStart, to - segmentStart)
                val partX = visibleStartX + font.getWidth(fullText.substring(visibleStart, from))
                context.drawText(
                    font,
                    Text.literal(part),
                    partX.roundToInt(),
                    textY,
                    withAlpha(color, alpha),
                    config.hudTextShadow,
                )
            }
            segmentStart = segmentEnd
        }
    }

    private fun headerWidth(
        font: net.minecraft.client.font.TextRenderer,
        style: String,
        config: KillFeedbackConfig,
        snapshot: KillFeedSnapshot,
    ): Float {
        val coins = "${formatNumber(snapshot.totalCoins, config)} Coins"
        val kills = "${formatNumber(snapshot.totalKills.toDouble(), config)} Kills"
        return when (style) {
            "HIDDEN" -> 0f
            "COMPACT" -> font.getWidth(
                "${formatShort(snapshot.totalCoins)}c  ${formatShort(snapshot.totalKills.toDouble())}k",
            ).toFloat()
            "COINS_ONLY" -> if (config.showCoins) font.getWidth(coins).toFloat() else 0f
            "KILLS_ONLY" -> if (config.showKills) font.getWidth(kills).toFloat() else 0f
            else -> {
                var width = 0f
                if (config.showCoins) width += font.getWidth(coins).toFloat()
                if (config.showKills) width += font.getWidth(kills).toFloat() + 24f
                width
            }
        }
    }

    private fun rowWidth(
        font: net.minecraft.client.font.TextRenderer,
        entry: KillFeedEntry,
        config: KillFeedbackConfig,
    ): Float {
        val reward = entry.reward
        val source = buildString {
            if (config.showLevel && reward.level != null) {
                append("Lv.")
                append(reward.level)
                append(' ')
            }
            append(reward.mob)
        }
        val sourceWidth = if (config.showSource) font.getWidth(source).toFloat() else 0f
        val amountWidth = if (config.showAmount) {
            font.getWidth("+${KillRewardParser.formatCoins(reward.coins)}").toFloat()
        } else {
            0f
        }
        return sourceWidth + amountWidth + 12f
    }

    private fun widthLayoutKey(config: KillFeedbackConfig, headerStyle: String): Int = listOf(
        config.hudScale,
        headerStyle,
        config.showCoins,
        config.showKills,
        config.showSource,
        config.showLevel,
        config.showAmount,
        config.numberFormat,
        config.maxRows,
        config.rowSpacing,
        config.entryDurationSeconds,
        config.statsWindowSeconds,
    ).hashCode()

    private fun animateTotals(snapshot: KillFeedSnapshot) {
        if (snapshot.totalKills < lastTotalKills || snapshot.totalCoins < lastTotalCoins) {
            displayedCoins = snapshot.totalCoins
            displayedKills = snapshot.totalKills.toFloat()
        } else {
            displayedCoins += (snapshot.totalCoins - displayedCoins) * 0.22
            displayedKills += (snapshot.totalKills - displayedKills) * 0.22f
        }
        lastTotalCoins = snapshot.totalCoins
        lastTotalKills = snapshot.totalKills
    }

    private fun entryAlpha(entry: KillFeedEntry, now: Long): Float {
        val insert = easeOutCubic(((now - entry.createdAt).coerceAtLeast(0L) / 160f).coerceIn(0f, 1f))
        val fade = entry.fadeStartedAt?.let {
            (1f - (now - it).toFloat() / KillFeedState.FADE_DURATION_MS).coerceIn(0f, 1f)
        } ?: 1f
        return min(insert, fade)
    }

    private fun easeOutCubic(value: Float): Float {
        val x = value.coerceIn(0f, 1f) - 1f
        return x * x * x + 1f
    }

    private fun formatNumber(value: Double, config: KillFeedbackConfig): String = when (config.numberFormat.uppercase()) {
        "COMMA" -> "%,.0f".format(value)
        "SHORT" -> formatShort(value)
        else -> KillRewardParser.formatCoins(value)
    }

    private fun formatShort(value: Double): String = when {
        value >= 1_000_000 -> "%.1fM".format(value / 1_000_000)
        value >= 1_000 -> "%.1fK".format(value / 1_000)
        else -> value.toInt().toString()
    }

    private fun withAlpha(color: Int, alpha: Float): Int {
        val a = ((color ushr 24 and 0xFF) * alpha.coerceIn(0f, 1f)).toInt()
        return (a shl 24) or (color and 0xFFFFFF)
    }

    private fun roundedRect(
        context: DrawContext,
        x1: Float,
        y1: Float,
        x2: Float,
        y2: Float,
        radius: Float,
        color: Int,
    ) {
        val r = radius.coerceAtMost(min(x2 - x1, y2 - y1) / 2f)
        context.fill((x1 + r).toInt(), y1.toInt(), (x2 - r).toInt(), y2.toInt(), color)
        context.fill(x1.toInt(), (y1 + r).toInt(), x2.toInt(), (y2 - r).toInt(), color)
    }

    private fun roundedOutline(
        context: DrawContext,
        x1: Float,
        y1: Float,
        x2: Float,
        y2: Float,
        color: Int,
    ) {
        context.fill(x1.toInt(), y1.toInt(), x2.toInt(), (y1 + 1f).toInt(), color)
        context.fill(x1.toInt(), (y2 - 1f).toInt(), x2.toInt(), y2.toInt(), color)
        context.fill(x1.toInt(), y1.toInt(), (x1 + 1f).toInt(), y2.toInt(), color)
        context.fill((x2 - 1f).toInt(), y1.toInt(), x2.toInt(), y2.toInt(), color)
    }
}
