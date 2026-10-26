package com.monaflow.app.core.theme

import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * 莫奈基础色板（文档第三节，唯一颜色来源，页面禁止硬编码）。
 */
object MonaPalette {
    val CreamWhite = 0xFFF7F5EFL // 奶油白
    val MistBlue = 0xFFDCE8F4L // 雾蓝
    val WaterBlue = 0xFFA9C7D8L // 水色蓝
    val LilyGreen = 0xFFB7CBB3L // 睡莲绿
    val SageGreen = 0xFF8FA995L // 鼠尾草绿
    val SoftPurple = 0xFFD9CBE8L // 淡紫
    val LotusPink = 0xFFEBD1D5L // 莲花粉
    val WarmSand = 0xFFE8DCCBL // 暖沙色
    val DeepText = 0xFF30363BL // 深文字色
}

object ColorUtils {

    /** sRGB 亮度（WCAG 相对亮度），0f..1f。 */
    fun luminance(color: Int): Float {
        val r = (color shr 16 and 0xFF) / 255f
        val g = (color shr 8 and 0xFF) / 255f
        val b = (color and 0xFF) / 255f
        fun channel(c: Float): Float =
            if (c <= 0.03928f) c / 12.92f else ((c + 0.055f) / 1.055f).pow(2.4f)
        return 0.2126f * channel(r) + 0.7152f * channel(g) + 0.0722f * channel(b)
    }

    /** 对比度 1f..21f。 */
    fun contrastRatio(c1: Int, c2: Int): Float {
        val l1 = luminance(c1)
        val l2 = luminance(c2)
        val lighter = max(l1, l2)
        val darker = min(l1, l2)
        return (lighter + 0.05f) / (darker + 0.05f)
    }

    /** 选择与背景对比度更高（>=4.5 优先）的文字色：深墨 或 暖白。 */
    fun readableTextColor(background: Int): Int {
        val dark = 0xFF30363B.toInt()
        val light = 0xFFFBFAF7.toInt()
        return if (contrastRatio(background, dark) >= contrastRatio(background, light)) dark else light
    }

    /** 若文字与背景对比不足，逐步加深/提亮背景，保证正文可读。 */
    fun ensureTextContrast(background: Int, minimum: Float = 4.5f): Int {
        val dark = 0xFF30363B.toInt()
        if (contrastRatio(background, dark) >= minimum) return background
        // 背景过深时往浅调，过浅时往深调，最多迭代 16 次
        var c = background
        val towardLight = luminance(c) < 0.2f
        repeat(16) {
            c = if (towardLight) lighten(c, 0.06f) else darken(c, 0.06f)
            if (contrastRatio(c, dark) >= minimum) return c
        }
        return c
    }

    fun lighten(color: Int, fraction: Float): Int = adjustLightness(color, fraction)

    fun darken(color: Int, fraction: Float): Int = adjustLightness(color, -fraction)

    private fun adjustLightness(color: Int, delta: Float): Int {
        val a = color ushr 24
        val r = (color shr 16 and 0xFF) / 255f
        val g = (color shr 8 and 0xFF) / 255f
        val b = (color and 0xFF) / 255f
        val maxc = max(r, max(g, b))
        val minc = min(r, min(g, b))
        val l = (maxc + minc) / 2f
        val newL = (l + delta).coerceIn(0f, 1f)
        if (newL == l) return color
        val d = maxc - minc
        val s = if (d == 0f) 0f else d / (1f - kotlin.math.abs(2f * l - 1f))
        val h = hueOf(r, g, b, maxc, d)
        val (nr, ng, nb) = hslToRgb(h, s, newL)
        return (a shl 24) or (nr shl 16) or (ng shl 8) or nb
    }

    fun hueOf(r: Float, g: Float, b: Float, maxc: Float, d: Float): Float {
        if (d == 0f) return 0f
        return when (maxc) {
            r -> ((g - b) / d).mod(6f)
            g -> (b - r) / d + 2f
            else -> (r - g) / d + 4f
        } * 60f
    }

    fun hslToRgb(h: Float, s: Float, l: Float): Triple<Int, Int, Int> {
        if (s == 0f) {
            val v = (l * 255f).toInt().coerceIn(0, 255)
            return Triple(v, v, v)
        }
        val c = (1f - kotlin.math.abs(2f * l - 1f)) * s
        val hp = (h.mod(360f)) / 60f
        val x = c * (1f - kotlin.math.abs(hp.mod(2f) - 1f))
        val (r1, g1, b1) = when (hp.toInt()) {
            0 -> Triple(c, x, 0f)
            1 -> Triple(x, c, 0f)
            2 -> Triple(0f, c, x)
            3 -> Triple(0f, x, c)
            4 -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }
        val m = l - c / 2f
        return Triple(
            ((r1 + m) * 255f).toInt().coerceIn(0, 255),
            ((g1 + m) * 255f).toInt().coerceIn(0, 255),
            ((b1 + m) * 255f).toInt().coerceIn(0, 255),
        )
    }

    fun rgbToHsl(color: Int): Triple<Float, Float, Float> {
        val r = (color shr 16 and 0xFF) / 255f
        val g = (color shr 8 and 0xFF) / 255f
        val b = (color and 0xFF) / 255f
        val maxc = max(r, max(g, b))
        val minc = min(r, min(g, b))
        val l = (maxc + minc) / 2f
        val d = maxc - minc
        val s = if (d == 0f) 0f else d / (1f - kotlin.math.abs(2f * l - 1f))
        val h = hueOf(r, g, b, maxc, d)
        return Triple(h, s, l)
    }

    /** 控制饱和度并调整亮度，用于从图片主色生成柔和主题色。 */
    fun soften(color: Int, targetSaturation: Float, targetLightness: Float): Int {
        val (h, _, _) = rgbToHsl(color)
        val (r, g, b) = hslToRgb(h, targetSaturation.coerceIn(0f, 1f), targetLightness.coerceIn(0f, 1f))
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }
}
