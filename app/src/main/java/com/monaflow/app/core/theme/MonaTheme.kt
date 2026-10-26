package com.monaflow.app.core.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import kotlinx.serialization.Serializable

private fun hsl(h: Float, s: Float, l: Float): Long {
    val (r, g, b) = ColorUtils.hslToRgb(h, s, l)
    return 0xFF000000L or (r.toLong() shl 16) or (g.toLong() shl 8) or b.toLong()
}

/**
 * 主题语义 Token。所有预设主题与图片取色自定义主题都产出该结构，
 * 再统一映射到 Material3 ColorScheme；页面只允许读取 [LocalMonaColors] / MaterialTheme。
 */
@Serializable
data class MonaColors(
    val background: Long,
    val surface: Long, // 卡片
    val surfaceHigh: Long, // 强调卡片 / 输入框
    val primary: Long,
    val onPrimary: Long,
    val secondary: Long,
    val tertiary: Long,
    val accent: Long, // 主按钮 / FAB
    val textPrimary: Long,
    val textSecondary: Long,
    val outline: Long,
) {
    companion object {
        /** 从种子色生成柔和的莫奈式主题（图片取色入口）。 */
        fun fromSeed(seed: Int, dark: Boolean): MonaColors {
            val (h, s, _) = ColorUtils.rgbToHsl(seed)
            val sat = s.coerceIn(0.16f, 0.38f) // 控制饱和度，保持莫奈式柔和
            return if (dark) {
                val primary = ColorUtils.soften(seed, sat, 0.62f)
                MonaColors(
                    background = hsl(h, sat * 0.5f, 0.13f),
                    surface = hsl(h, sat * 0.55f, 0.17f),
                    surfaceHigh = hsl(h, sat * 0.6f, 0.21f),
                    primary = primary.toLong(),
                    onPrimary = ColorUtils.readableTextColor(primary).toLong(),
                    secondary = hsl(h + 24f, sat * 0.7f, 0.30f),
                    tertiary = hsl(h - 24f, sat * 0.7f, 0.30f),
                    accent = hsl(h, sat, 0.55f),
                    textPrimary = 0xFFEDEAE3L,
                    textSecondary = 0xFFA8ADB3L,
                    outline = hsl(h, sat * 0.4f, 0.30f),
                )
            } else {
                val primary = ColorUtils.ensureTextContrast(ColorUtils.soften(seed, sat, 0.58f))
                MonaColors(
                    background = hsl(h, sat * 0.35f, 0.955f),
                    surface = 0xFFFFFFFFL,
                    surfaceHigh = hsl(h, sat * 0.5f, 0.90f),
                    primary = primary.toLong(),
                    onPrimary = ColorUtils.readableTextColor(primary).toLong(),
                    secondary = hsl(h + 24f, sat * 0.55f, 0.72f),
                    tertiary = hsl(h - 24f, sat * 0.55f, 0.72f),
                    accent = ColorUtils.ensureTextContrast(ColorUtils.soften(seed, sat, 0.46f)).toLong(),
                    textPrimary = MonaPalette.DeepText,
                    textSecondary = hsl(h, 0.08f, 0.42f),
                    outline = hsl(h, sat * 0.35f, 0.80f),
                )
            }
        }
    }
}

val LocalMonaColors = staticCompositionLocalOf { waterLilyLight }

/** 主题预设 ID 与自定义取色主题。 */
object MonaPreset {
    const val WATER_LILY = "water_lily" // 睡莲蓝
    const val GARDEN = "garden" // 花园绿
    const val MIST = "mist" // 晨雾紫
    const val SUNRISE = "sunrise" // 暖阳米
    const val CUSTOM = "custom" // 图片取色

    val ALL = listOf(WATER_LILY, GARDEN, MIST, SUNRISE)
}

fun presetColors(presetId: String, dark: Boolean): MonaColors = when (presetId) {
    MonaPreset.WATER_LILY -> if (dark) waterLilyDark else waterLilyLight
    MonaPreset.GARDEN -> if (dark) gardenDark else gardenLight
    MonaPreset.MIST -> if (dark) mistDark else mistLight
    MonaPreset.SUNRISE -> if (dark) sunriseDark else sunriseLight
    else -> if (dark) waterLilyDark else waterLilyLight
}

private val waterLilyLight = MonaColors(
    background = MonaPalette.CreamWhite,
    surface = 0xFFFFFFFF,
    surfaceHigh = 0xFFEDF3F7,
    primary = 0xFF6E9CB5, // 水色蓝加深保证对比
    onPrimary = 0xFFFBFAF7,
    secondary = MonaPalette.MistBlue,
    tertiary = MonaPalette.LotusPink,
    accent = 0xFF5A8AA5,
    textPrimary = MonaPalette.DeepText,
    textSecondary = 0xFF6B7680,
    outline = 0xFFD3DEE6,
)

private val waterLilyDark = MonaColors(
    background = 0xFF161C22,
    surface = 0xFF1E262E,
    surfaceHigh = 0xFF27313A,
    primary = 0xFF8FB8CE,
    onPrimary = 0xFF16242C,
    secondary = 0xFF3B4A55,
    tertiary = 0xFF5E4A50,
    accent = 0xFF8FB8CE,
    textPrimary = 0xFFEDEAE3,
    textSecondary = 0xFFA8ADB3,
    outline = 0xFF3E4A54,
)

private val gardenLight = MonaColors(
    background = MonaPalette.CreamWhite,
    surface = 0xFFFFFFFF,
    surfaceHigh = 0xFFEEF2EA,
    primary = 0xFF6E8A74, // 鼠尾草绿加深
    onPrimary = 0xFFFBFAF7,
    secondary = MonaPalette.LilyGreen,
    tertiary = MonaPalette.WarmSand,
    accent = 0xFF587A62,
    textPrimary = MonaPalette.DeepText,
    textSecondary = 0xFF6C756D,
    outline = 0xFFD6DDD2,
)

private val gardenDark = MonaColors(
    background = 0xFF171C18,
    surface = 0xFF1F2621,
    surfaceHigh = 0xFF28312B,
    primary = 0xFF9DBBA5,
    onPrimary = 0xFF17211A,
    secondary = 0xFF3C4A3F,
    tertiary = 0xFF524A3B,
    accent = 0xFF9DBBA5,
    textPrimary = 0xFFEDEAE3,
    textSecondary = 0xFFA8ADB3,
    outline = 0xFF414B43,
)

private val mistLight = MonaColors(
    background = 0xFFF8F6FA,
    surface = 0xFFFFFFFF,
    surfaceHigh = 0xFFF0EBF5,
    primary = 0xFF8A78A8, // 淡紫加深
    onPrimary = 0xFFFBFAF7,
    secondary = 0xFFE4DBF0,
    tertiary = MonaPalette.MistBlue,
    accent = 0xFF756394,
    textPrimary = MonaPalette.DeepText,
    textSecondary = 0xFF716D7A,
    outline = 0xFFDDD6E4,
)

private val mistDark = MonaColors(
    background = 0xFF18161E,
    surface = 0xFF211E28,
    surfaceHigh = 0xFF2B2733,
    primary = 0xFFB4A6CE,
    onPrimary = 0xFF1D1826,
    secondary = 0xFF453E52,
    tertiary = 0xFF35424E,
    accent = 0xFFB4A6CE,
    textPrimary = 0xFFEDEAE3,
    textSecondary = 0xFFA8ADB3,
    outline = 0xFF484253,
)

private val sunriseLight = MonaColors(
    background = 0xFFF9F6F0,
    surface = 0xFFFFFFFF,
    surfaceHigh = 0xFFF3ECDF,
    primary = 0xFFA08258, // 暖沙色加深
    onPrimary = 0xFFFBFAF7,
    secondary = MonaPalette.WarmSand,
    tertiary = MonaPalette.LotusPink,
    accent = 0xFF8C6F46,
    textPrimary = MonaPalette.DeepText,
    textSecondary = 0xFF77705F,
    outline = 0xFFE2D8C6,
)

private val sunriseDark = MonaColors(
    background = 0xFF1C1913,
    surface = 0xFF252118,
    surfaceHigh = 0xFF2F2A1F,
    primary = 0xFFC9AE7E,
    onPrimary = 0xFF241D12,
    secondary = 0xFF4A4231,
    tertiary = 0xFF4E3B3E,
    accent = 0xFFC9AE7E,
    textPrimary = 0xFFEDEAE3,
    textSecondary = 0xFFA8ADB3,
    outline = 0xFF4E4636,
)

fun MonaColors.toMaterialScheme(): ColorScheme {
    fun c(v: Long) = Color(v)
    val base = lightColorScheme(
        background = c(background),
        onBackground = c(textPrimary),
        surface = c(surface),
        onSurface = c(textPrimary),
        surfaceVariant = c(surfaceHigh),
        onSurfaceVariant = c(textSecondary),
        primary = c(primary),
        onPrimary = c(onPrimary),
        primaryContainer = c(surfaceHigh),
        onPrimaryContainer = c(textPrimary),
        secondary = c(secondary),
        onSecondary = c(onPrimary),
        secondaryContainer = c(surfaceHigh),
        onSecondaryContainer = c(textPrimary),
        tertiary = c(tertiary),
        onTertiary = c(onPrimary),
        error = c(0xFFB3564E),
        onError = c(0xFFFFFFFF),
        outline = c(outline),
        outlineVariant = c(outline),
        scrim = c(0xFF30363B),
    )
    return if (ColorUtils.luminance(background.toInt()) < 0.3f) {
        // 深色主题：从 darkColorScheme 取正确的容器/层级默认值，再覆盖莫奈 Token
        darkColorScheme(
            background = c(background),
            onBackground = c(textPrimary),
            surface = c(surface),
            onSurface = c(textPrimary),
            surfaceVariant = c(surfaceHigh),
            onSurfaceVariant = c(textSecondary),
            primary = c(primary),
            onPrimary = c(onPrimary),
            primaryContainer = c(surfaceHigh),
            onPrimaryContainer = c(textPrimary),
            secondary = c(secondary),
            onSecondary = c(onPrimary),
            secondaryContainer = c(surfaceHigh),
            onSecondaryContainer = c(textPrimary),
            tertiary = c(tertiary),
            onTertiary = c(onPrimary),
            error = c(0xFFCF7A70),
            onError = c(0xFF2B1512),
            outline = c(outline),
            outlineVariant = c(outline),
            scrim = c(0xFF000000),
        )
    } else {
        base
    }
}

/**
 * MonaFlow 主题入口。dark 参数由调用方根据 ThemeMode 与系统解析后传入。
 */
@Composable
fun MonaFlowTheme(
    mona: MonaColors,
    dark: Boolean,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalMonaColors provides mona) {
        MaterialTheme(
            colorScheme = mona.toMaterialScheme(),
            content = content,
        )
    }
}
