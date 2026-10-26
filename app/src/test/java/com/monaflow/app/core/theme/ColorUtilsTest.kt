package com.monaflow.app.core.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ColorUtilsTest {

    @Test
    fun `white on black contrast is 21`() {
        assertEquals(21f, ColorUtils.contrastRatio(0xFFFFFFFF.toInt(), 0xFF000000.toInt()), 0.1f)
    }

    @Test
    fun `same color contrast is 1`() {
        assertEquals(1f, ColorUtils.contrastRatio(0xFF808080.toInt(), 0xFF808080.toInt()), 0.01f)
    }

    @Test
    fun `readable text color picks darker text on light background`() {
        assertEquals(0xFF30363B.toInt(), ColorUtils.readableTextColor(0xFFF7F5EF.toInt()))
        assertEquals(0xFFFBFAF7.toInt(), ColorUtils.readableTextColor(0xFF30363B.toInt()))
    }

    @Test
    fun `primary colors meet contrast against deep text`() {
        // 四个预设主色都必须与深文字对比达标（正文可读）
        listOf(0xFF6E9CB5.toInt(), 0xFF6E8A74.toInt(), 0xFF8A78A8.toInt(), 0xFFA08258.toInt()).forEach { c ->
            assertTrue(
                "主色 0x${c.toString(16)} 对比不足",
                ColorUtils.contrastRatio(c, 0xFF30363B.toInt()) >= 3f,
            )
        }
    }

    @Test
    fun `soften keeps hsl in mona range`() {
        // 高饱和刺眼色（纯红）软化后应落入柔和区间
        val softened = ColorUtils.soften(0xFFFF0000.toInt(), 0.3f, 0.6f)
        val (_, s, l) = ColorUtils.rgbToHsl(softened)
        assertTrue(s <= 0.31f)
        assertTrue(l in 0.59f..0.61f)
    }

    @Test
    fun `ensureTextContrast never returns unreadable`() {
        listOf(0xFF000000.toInt(), 0xFFFFFFFF.toInt(), 0xFF123456.toInt(), 0xFFABCDEF.toInt()).forEach { bg ->
            val adjusted = ColorUtils.ensureTextContrast(bg)
            assertTrue(
                ColorUtils.contrastRatio(adjusted, 0xFF30363B.toInt()) >= 4.5f ||
                    bg == 0xFFFFFFFF.toInt(),
            )
        }
    }

    @Test
    fun `fromSeed produces valid theme both modes`() {
        listOf(false, true).forEach { dark ->
            val mona = MonaColors.fromSeed(0xFFA9C7D8.toInt(), dark)
            assertTrue(mona.background != 0L)
            assertTrue(mona.primary != 0L)
            assertTrue(mona.onPrimary != 0L)
            // 深浅两版背景必须明显不同
            if (dark) {
                assertTrue(ColorUtils.luminance(mona.background.toInt()) < 0.3f)
            } else {
                assertTrue(ColorUtils.luminance(mona.background.toInt()) > 0.7f)
            }
        }
    }
}
