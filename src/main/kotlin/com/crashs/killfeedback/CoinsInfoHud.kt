package com.crashs.killfeedback

import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.DrawContext
import net.minecraft.text.Text
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

object CoinsInfoHud {

    private data class Row(val left: Text, val right: Text?, val leftColor: Int, val rightColor: Int)

    private var displayedTotal = 0.0
    private var displayedAverage = 0.0
    private var displayGeneration = Long.MIN_VALUE
    private var displayUnit: String? = null

    private var widthGeneration = Long.MIN_VALUE
    private var widthLayoutKey = Int.MIN_VALUE
    private var cachedWidth = 0f

    fun render(context: DrawContext) {
        val client = MinecraftClient.getInstance()
        val config = KillFeedbackConfigManager.config
        if (!config.enabled || !config.coinsInfoEnabled) return
        if (client.player == null || client.options.hudHidden || client.currentScreen != null) return

        val snapshot = CoinsSession.snapshot()
        animate(snapshot, config)
        val rows = rows(snapshot, config, animated = true)
        if (rows.isEmpty()) return

        val font = client.textRenderer
        val scale = config.coinsInfoScale.coerceIn(0.5f, 2.5f)
        val width = contentWidth(font, snapshot, config)
        val lineHeight = font.fontHeight.toFloat()
        val gap = if (rows.size > 1) 2f else 0f
        val headerVisible = config.coinsInfoShowSessionTime || config.coinsInfoShowKills
        val headerHeight = if (headerVisible) font.fontHeight + 2f else 0f
        val dividerHeight = if (headerVisible) config.progressHeight.coerceIn(1f, 8f) + 3f else 0f
        val height = rows.size * lineHeight + gap * (rows.size - 1) +
            headerHeight + dividerHeight + PADDING * 2

        val screenWidth = context.scaledWindowWidth.toFloat()
        val screenHeight = context.scaledWindowHeight.toFloat()
        val percentX = config.coinsInfoX.coerceIn(0f, 100f)
        val percentY = config.coinsInfoY.coerceIn(0f, 100f)
        val x = if (config.coinsInfoRight) {
            screenWidth - width * scale - screenWidth * percentX / 100f
        } else {
            screenWidth * percentX / 100f
        }
        val y = screenHeight * percentY / 100f

        context.matrices.pushMatrix()
        context.matrices.translate(x, y)
        context.matrices.scale(scale, scale)

        if (config.coinsInfoBackground) {
            context.fill(0, 0, width.toInt(), height.toInt(), config.coinsInfoBackgroundColor)
        }
        if (config.coinsInfoOutline) {
            outline(context, 0f, 0f, width, height, config.coinsInfoOutlineColor)
        }

        var cursorY = PADDING
        if (headerVisible) {
            if (config.coinsInfoShowSessionTime) {
                context.drawText(
                    font,
                    Text.literal(formatDuration(snapshot.elapsedMs)),
                    PADDING.toInt(),
                    cursorY.toInt(),
                    config.coinsInfoSessionTimeColor,
                    config.coinsInfoTextShadow,
                )
            }
            if (config.coinsInfoShowKills) {
                val kills = Text.literal("${snapshot.totalKills} Kills")
                val killsWidth = font.getWidth(kills).toFloat()
                context.drawText(
                    font,
                    kills,
                    (width - PADDING - killsWidth).toInt(),
                    cursorY.toInt(),
                    config.coinsInfoKillsColor,
                    config.coinsInfoTextShadow,
                )
            }
            cursorY += headerHeight
            val barHeight = config.progressHeight.coerceIn(1f, 8f).toInt()
            context.fill(PADDING.toInt(), cursorY.toInt(), (width - PADDING).toInt(), cursorY.toInt() + barHeight, config.coinsInfoDividerBackground)
            context.fill(PADDING.toInt(), cursorY.toInt(), (width - PADDING).toInt(), cursorY.toInt() + 1, config.coinsInfoDividerColor)
            cursorY += dividerHeight
        }

        for ((index, row) in rows.withIndex()) {
            context.drawText(font, row.left, PADDING.toInt(), cursorY.toInt(), row.leftColor, config.coinsInfoTextShadow)
            row.right?.let { right ->
                val rightWidth = font.getWidth(right).toFloat()
                context.drawText(
                    font,
                    right,
                    (width - PADDING - rightWidth).toInt(),
                    cursorY.toInt(),
                    row.rightColor,
                    config.coinsInfoTextShadow,
                )
            }
            cursorY += lineHeight
            if (index < rows.size - 1) cursorY += gap
        }

        context.matrices.popMatrix()
    }

    private fun animate(snapshot: CoinsSessionSnapshot, config: KillFeedbackConfig) {
        val targetTotal = snapshot.totalCoins
        val targetAverage = average(snapshot, config)
        if (!config.coinsInfoScroll ||
            snapshot.generation != displayGeneration ||
            config.coinsInfoAverageUnit != displayUnit
        ) {
            displayedTotal = targetTotal
            displayedAverage = targetAverage
            displayGeneration = snapshot.generation
            displayUnit = config.coinsInfoAverageUnit
            return
        }
        displayedTotal += (targetTotal - displayedTotal) * 0.22
        displayedAverage += (targetAverage - displayedAverage) * 0.22
    }

    private fun contentWidth(
        font: net.minecraft.client.font.TextRenderer,
        snapshot: CoinsSessionSnapshot,
        config: KillFeedbackConfig,
    ): Float {
        val rows = rows(snapshot, config, animated = false)
        val headerWidth = if (config.coinsInfoShowSessionTime || config.coinsInfoShowKills) {
            val time = if (config.coinsInfoShowSessionTime) {
                font.getWidth(Text.literal(formatDuration(snapshot.elapsedMs))).toFloat()
            } else 0f
            val kills = if (config.coinsInfoShowKills) {
                font.getWidth(Text.literal("${snapshot.totalKills} Kills")).toFloat()
            } else 0f
            time + (if (time > 0f && kills > 0f) 8f else 0f) + kills
        } else 0f
        val target = max(
            MIN_WIDTH,
            max(headerWidth, rows.maxOfOrNull { rowWidth(font, it) } ?: 0f) + PADDING * 2,
        )
        val key = layoutKey(config)
        if (snapshot.generation != widthGeneration || key != widthLayoutKey) {
            widthGeneration = snapshot.generation
            widthLayoutKey = key
            cachedWidth = target
        } else {
            cachedWidth = max(cachedWidth, target)
        }
        return cachedWidth
    }

    private fun rows(
        snapshot: CoinsSessionSnapshot,
        config: KillFeedbackConfig,
        animated: Boolean,
    ): List<Row> {
        val totalValue = if (animated) displayedTotal else snapshot.totalCoins
        val avgValue = if (animated) displayedAverage else average(snapshot, config)
        val total = formatNumber(totalValue, config)
        val avg = formatNumber(avgValue, config)
        val unit = if (config.coinsInfoAverageUnit.equals("HOUR", true)) "avg/h" else "avg/min"

        if (config.coinsInfoTwoLines) {
            return buildList {
                if (config.coinsInfoShowTotal) {
                    add(Row(Text.literal(total), Text.literal("Coins"), config.coinsInfoTotalColor, config.coinsInfoTotalColor))
                }
                if (config.coinsInfoShowAverage) {
                    add(Row(Text.literal(avg), Text.literal(unit), config.coinsInfoAverageColor, config.coinsInfoAverageColor))
                }
            }
        }

        return when {
            config.coinsInfoShowTotal && config.coinsInfoShowAverage -> listOf(
                Row(
                    Text.literal("$total Coins"),
                    Text.literal("$avg $unit"),
                    config.coinsInfoTotalColor,
                    config.coinsInfoAverageColor,
                ),
            )
            config.coinsInfoShowTotal -> listOf(
                Row(Text.literal("$total Coins"), null, config.coinsInfoTotalColor, config.coinsInfoTotalColor),
            )
            config.coinsInfoShowAverage -> listOf(
                Row(Text.literal("$avg $unit"), null, config.coinsInfoAverageColor, config.coinsInfoAverageColor),
            )
            else -> emptyList()
        }
    }

    private fun rowWidth(font: net.minecraft.client.font.TextRenderer, row: Row): Float {
        val left = font.getWidth(row.left).toFloat()
        val right = row.right?.let { font.getWidth(it).toFloat() } ?: 0f
        return left + if (row.right != null) 8f + right else 0f
    }

    private fun average(snapshot: CoinsSessionSnapshot, config: KillFeedbackConfig): Double =
        if (config.coinsInfoAverageUnit.equals("HOUR", true)) snapshot.averagePerHour() else snapshot.averagePerMinute()

    private fun layoutKey(config: KillFeedbackConfig): Int = listOf(
        config.coinsInfoScale,
        config.coinsInfoTwoLines,
        config.coinsInfoShowTotal,
        config.coinsInfoShowAverage,
        config.coinsInfoShowSessionTime,
        config.coinsInfoShowKills,
        config.coinsInfoAverageUnit,
        config.coinsInfoNumberFormat,
    ).hashCode()

    private fun formatNumber(value: Double, config: KillFeedbackConfig): String = when (config.coinsInfoNumberFormat.uppercase()) {
        "RAW" -> KillRewardParser.formatCoins(value)
        "SHORT" -> formatShort(value)
        else -> String.format(Locale.ROOT, "%,.2f", value).trimEnd('0').trimEnd('.')
    }

    private fun formatDuration(ms: Long): String {
        val totalSeconds = (ms / 1000L).coerceAtLeast(0L)
        val hours = totalSeconds / 3600L
        val minutes = (totalSeconds % 3600L) / 60L
        val seconds = totalSeconds % 60L
        return if (hours > 0L) {
            "%d:%02d:%02d".format(Locale.ROOT, hours, minutes, seconds)
        } else {
            "%02d:%02d".format(Locale.ROOT, minutes, seconds)
        }
    }

    private fun formatShort(value: Double): String = when {
        value >= 1_000_000 -> "%.1fM".format(Locale.ROOT, value / 1_000_000)
        value >= 1_000 -> "%.1fK".format(Locale.ROOT, value / 1_000)
        else -> value.toInt().toString()
    }

    private fun outline(context: DrawContext, x1: Float, y1: Float, x2: Float, y2: Float, color: Int) {
        context.fill(x1.toInt(), y1.toInt(), x2.toInt(), (y1 + 1f).toInt(), color)
        context.fill(x1.toInt(), (y2 - 1f).toInt(), x2.toInt(), y2.toInt(), color)
        context.fill(x1.toInt(), y1.toInt(), (x1 + 1f).toInt(), y2.toInt(), color)
        context.fill((x2 - 1f).toInt(), y1.toInt(), x2.toInt(), y2.toInt(), color)
    }

    private const val PADDING = 3f
    private const val MIN_WIDTH = 100f
}
