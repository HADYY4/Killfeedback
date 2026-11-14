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

    private var displayedCoins = 0.0
    private var displayedKills = 0f
    private var lastTotalCoins = 0.0
    private var lastTotalKills = 0

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
        val contentWidth = max(
            MIN_WIDTH,
            max(
                if (headerVisible) headerWidth(font, headerStyle, config) else 0f,
                snapshot.entries.maxOfOrNull { rowWidth(font, it, config) } ?: 0f,
            ) + PADDING * 2,
        )
        val headerHeight = if (headerVisible) font.fontHeight + HEADER_GAP else 0f
        val progressHeight = if (config.showProgress) config.progressHeight.coerceIn(1f, 8f) + PROGRESS_GAP else 2f
        val rowHeight = max(12f, font.fontHeight + 2f)
        val rowSpacing = config.rowSpacing.coerceIn(0f, 8f)
        val listHeight = if (snapshot.entries.isEmpty()) 0f else
            snapshot.entries.size * rowHeight + (snapshot.entries.size - 1) * rowSpacing
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
        roundedOutline(context, 0f, 0f, contentWidth, height, config.outlineColor)

        var cursorY = PADDING
        if (headerVisible) {
            drawHeader(context, font, headerStyle, contentWidth, config)
            cursorY += headerHeight
        }

        drawProgress(context, snapshot, now, contentWidth, cursorY, config)
        cursorY += progressHeight

        val active = snapshot.entries.filter { !it.isFading }
        val pendingId = if (active.size >= config.maxRows) active.lastOrNull()?.id else null

        for (entry in snapshot.entries) {
            val age = now - entry.createdAt
            var alpha = entryAlpha(entry, now)
            if (entry.id == pendingId) {
                alpha *= config.pendingEvictionAlpha.coerceIn(0.1f, 1f)
            }
            val insertOffset = if (age >= 160) 0f else (1f - easeOutCubic(age / 160f)) * 4f
            if (alpha > 0.01f) {
                drawRow(context, font, entry, contentWidth, cursorY + insertOffset, rowHeight, alpha, config)
            }
            cursorY += rowHeight + rowSpacing
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
                    context.drawText(font, Text.literal(text), PADDING.toInt(), PADDING.toInt(), color, true)
                }
            }

            "COINS_ONLY" -> if (config.showCoins) {
                context.drawText(font, Text.literal(coins), PADDING.toInt(), PADDING.toInt(), color, true)
            }

            "KILLS_ONLY" -> if (config.showKills) {
                context.drawText(font, Text.literal(kills), PADDING.toInt(), PADDING.toInt(), color, true)
            }

            "HIDDEN" -> Unit

            else -> {
                if (config.showCoins) {
                    context.drawText(font, Text.literal(coins), PADDING.toInt(), PADDING.toInt(), color, true)
                }
                if (config.showKills) {
                    val killX = width - PADDING - font.getWidth(kills)
                    context.drawText(font, Text.literal(kills), killX.roundToInt(), PADDING.toInt(), color, true)
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
                context.fill(
                    x.toInt(),
                    y.toInt(),
                    (x + barWidth * fill).toInt(),
                    (y + barHeight).toInt(),
                    config.progressColor,
                )
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
            var sourceX = (PADDING + 2f).roundToInt()
            if (config.showLevel && reward.level != null) {
                val levelText = "Lv.${reward.level} "
                context.drawText(
                    font,
                    Text.literal(levelText),
                    sourceX,
                    textY,
                    withAlpha(config.levelColor, alpha),
                    true,
                )
                sourceX += font.getWidth(levelText)
            }
            context.drawText(
                font,
                Text.literal(reward.mob),
                sourceX,
                textY,
                withAlpha(config.sourceColor, alpha),
                true,
            )
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

    private fun headerWidth(
        font: net.minecraft.client.font.TextRenderer,
        style: String,
        config: KillFeedbackConfig,
    ): Float {
        val coins = "${formatNumber(displayedCoins, config)} Coins"
        val kills = "${formatNumber(displayedKills.roundToInt().toDouble(), config)} Kills"
        return when (style) {
            "HIDDEN" -> 0f
            "COMPACT" -> font.getWidth(
                "${formatShort(displayedCoins)}c  ${formatShort(displayedKills.roundToInt().toDouble())}k",
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
