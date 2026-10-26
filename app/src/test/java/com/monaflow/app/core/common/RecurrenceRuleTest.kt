package com.monaflow.app.core.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class RecurrenceRuleTest {

    // ---------- 解析 ----------

    @Test
    fun `parse daily`() {
        val r = RecurrenceRule.parse("FREQ=DAILY;INTERVAL=2")!!
        assertEquals(RecurrenceRule.Freq.DAILY, r.freq)
        assertEquals(2, r.interval)
        assertTrue(r.byDays.isEmpty())
    }

    @Test
    fun `parse weekly byday`() {
        val r = RecurrenceRule.parse("FREQ=WEEKLY;INTERVAL=1;BYDAY=MO,WE,FR")!!
        assertEquals(RecurrenceRule.Freq.WEEKLY, r.freq)
        assertEquals(
            setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY),
            r.byDays,
        )
        assertEquals("FREQ=WEEKLY;INTERVAL=1;BYDAY=MO,WE,FR", r.encode())
    }

    @Test
    fun `parse invalid returns null`() {
        assertNull(RecurrenceRule.parse(null))
        assertNull(RecurrenceRule.parse(""))
        assertNull(RecurrenceRule.parse("FREQ=WHATEVER"))
        assertNull(RecurrenceRule.parse("FREQ=WEEKLY")) // WEEKLY 必须带 BYDAY
        assertNull(RecurrenceRule.parse("INTERVAL=2")) // 缺 FREQ
    }

    @Test
    fun `parse defaults interval to 1`() {
        assertEquals(1, RecurrenceRule.parse("FREQ=DAILY")!!.interval)
    }

    // ---------- nextAfter ----------

    @Test
    fun `daily next after`() {
        val r = RecurrenceRule.daily()
        assertEquals(LocalDate.of(2026, 1, 2), r.nextAfter(LocalDate.of(2026, 1, 1)))
        val r3 = RecurrenceRule.daily(3)
        assertEquals(LocalDate.of(2026, 1, 4), r3.nextAfter(LocalDate.of(2026, 1, 1)))
    }

    @Test
    fun `weekly next after crosses month and year`() {
        // 周一三五
        val r = RecurrenceRule.weekly(setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY))
        // 2026-01-30 是周五 → 下一个是 2026-02-02 周一（跨月）
        assertEquals(LocalDate.of(2026, 2, 2), r.nextAfter(LocalDate.of(2026, 1, 30)))
        // 2025-12-31 是周三 → 下一个是 2026-01-02 周五（跨年）
        assertEquals(LocalDate.of(2026, 1, 2), r.nextAfter(LocalDate.of(2025, 12, 31)))
    }

    @Test
    fun `monthly next after clamps month end`() {
        val r = RecurrenceRule.monthly()
        // 1月31日 → 2月28日（非闰年）
        assertEquals(LocalDate.of(2026, 2, 28), r.nextAfter(LocalDate.of(2026, 1, 31)))
        // 2024 闰年：1月31 → 2月29
        assertEquals(LocalDate.of(2024, 2, 29), r.nextAfter(LocalDate.of(2024, 1, 31)))
    }

    @Test
    fun `yearly next after handles leap day`() {
        val r = RecurrenceRule.yearly()
        // 2024-02-29（闰年）→ 2025-02-28（钳制）
        assertEquals(LocalDate.of(2025, 2, 28), r.nextAfter(LocalDate.of(2024, 2, 29)))
        // 2025-02-28 → 2026-02-28
        assertEquals(LocalDate.of(2026, 2, 28), r.nextAfter(LocalDate.of(2025, 2, 28)))
        // 下一个闰年 2028 回到 2/29：从 2025-02-28 起逐年+1 → 2026-02-28（ yearly 固定 +interval 年，不跳闰）
        assertEquals(LocalDate.of(2026, 2, 28), r.nextAfter(LocalDate.of(2025, 2, 28)))
    }

    // ---------- isDueOn（习惯） ----------

    @Test
    fun `daily is due`() {
        val r = RecurrenceRule.daily()
        val start = LocalDate.of(2026, 10, 1)
        assertTrue(r.isDueOn(LocalDate.of(2026, 10, 1), start))
        assertTrue(r.isDueOn(LocalDate.of(2026, 10, 31), start))
        assertTrue(r.isDueOn(LocalDate.of(2027, 1, 1), start)) // 跨年
        assertFalse(r.isDueOn(LocalDate.of(2026, 9, 30), start)) // 开始前
    }

    @Test
    fun `weekly interval 2 alignment`() {
        // 从 2026-10-05（周一）起，每两周的周一
        val r = RecurrenceRule.weekly(setOf(DayOfWeek.MONDAY), interval = 2)
        val start = LocalDate.of(2026, 10, 5)
        assertTrue(r.isDueOn(LocalDate.of(2026, 10, 5), start))
        assertFalse(r.isDueOn(LocalDate.of(2026, 10, 12), start)) // 一周后不在
        assertTrue(r.isDueOn(LocalDate.of(2026, 10, 19), start)) // 两周后
        assertTrue(r.isDueOn(LocalDate.of(2026, 11, 2), start)) // 跨月对齐
        assertFalse(r.isDueOn(LocalDate.of(2026, 10, 6), start)) // 非周一
    }

    @Test
    fun `yearly is due on anniversary with clamp`() {
        val r = RecurrenceRule.yearly()
        val start = LocalDate.of(2024, 2, 29)
        assertTrue(r.isDueOn(LocalDate.of(2025, 2, 28), start))
        assertTrue(r.isDueOn(LocalDate.of(2028, 2, 29), start))
        assertFalse(r.isDueOn(LocalDate.of(2025, 3, 1), start))
    }

    // ---------- nextOnOrAfter（倒计时） ----------

    @Test
    fun `countdown next anniversary on or after today`() {
        val r = RecurrenceRule.yearly()
        val base = LocalDate.of(2000, 3, 15)
        // 今天之前 → 下一次 2027-03-15（2026-03-15 已过）
        assertEquals(LocalDate.of(2027, 3, 15), r.nextOnOrAfter(LocalDate.of(2026, 10, 26), base))
        // 当天就是 → 返回今天
        assertEquals(LocalDate.of(2026, 3, 15), r.nextOnOrAfter(LocalDate.of(2026, 3, 15), base))
        // 2/29 生日非闰年 → 2/28
        val leap = LocalDate.of(2000, 2, 29)
        assertEquals(LocalDate.of(2027, 2, 28), r.nextOnOrAfter(LocalDate.of(2027, 1, 1), leap))
        assertEquals(LocalDate.of(2028, 2, 29), r.nextOnOrAfter(LocalDate.of(2028, 1, 1), leap))
    }

    @Test
    fun `nextOnOrAfter with daily rule`() {
        val r = RecurrenceRule.daily(7)
        val base = LocalDate.of(2026, 1, 1)
        assertEquals(LocalDate.of(2026, 1, 29), r.nextOnOrAfter(LocalDate.of(2026, 1, 27), base))
    }

    @Test
    fun `encode roundtrip`() {
        listOf(
            "FREQ=DAILY;INTERVAL=1",
            "FREQ=DAILY;INTERVAL=3",
            "FREQ=WEEKLY;INTERVAL=2;BYDAY=TU,SA",
            "FREQ=MONTHLY;INTERVAL=1",
            "FREQ=YEARLY;INTERVAL=1",
        ).forEach { raw ->
            assertEquals(raw, RecurrenceRule.parse(raw)!!.encode())
        }
    }
}
