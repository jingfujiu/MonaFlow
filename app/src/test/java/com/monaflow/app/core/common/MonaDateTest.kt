package com.monaflow.app.core.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

class MonaDateTest {

    private val shanghai = ZoneId.of("Asia/Shanghai")
    private val utc = ZoneId.of("UTC")

    private fun millisAt(date: LocalDateTime, zone: ZoneId): Long =
        date.atZone(zone).toInstant().toEpochMilli()

    // ---------- 倒计时：天数必须基于当地日期差 ----------

    @Test
    fun `countdown same instant different zone differs by date`() {
        // 2026-12-25 00:00 上海 = 2026-12-24 16:00 UTC
        val millis = millisAt(LocalDateTime.of(2026, 12, 25, 0, 0), shanghai)
        val today = LocalDate.of(2026, 10, 26)
        // 以上海时区看，目标日期是 12/25 → 差 60 天
        val sh = MonaDate.countdownDays(millis, "Asia/Shanghai", false, null, today)
        assertEquals(LocalDate.of(2026, 12, 25), sh.date)
        assertEquals(60L, sh.daysLeft)
        // 以 UTC 看，同一时刻是 12/24 → 差 59 天（时区变化不应用 24h 除法抹平）
        val ut = MonaDate.countdownDays(millis, "UTC", false, null, today)
        assertEquals(LocalDate.of(2026, 12, 24), ut.date)
        assertEquals(59L, ut.daysLeft)
    }

    @Test
    fun `countdown across year boundary`() {
        val millis = millisAt(LocalDateTime.of(2027, 1, 2, 0, 0), shanghai)
        val today = LocalDate.of(2026, 10, 26)
        val result = MonaDate.countdownDays(millis, "Asia/Shanghai", false, null, today)
        // 10/26 → 1/2：10月剩5天(27-31) + 11月30 + 12月31 + 2 = 68
        assertEquals(68L, result.daysLeft)
    }

    @Test
    fun `countdown past date reports passed days`() {
        val millis = millisAt(LocalDateTime.of(2026, 1, 1, 0, 0), shanghai)
        val today = LocalDate.of(2026, 10, 26)
        val result = MonaDate.countdownDays(millis, "Asia/Shanghai", false, null, today)
        assertTrue(result.isPast)
        assertFalse(result.isToday)
        assertEquals(-298L, result.daysLeft)
        assertEquals(298L, result.passedDays)
    }

    @Test
    fun `countdown today`() {
        val millis = millisAt(LocalDateTime.of(2026, 10, 26, 0, 0), shanghai)
        val result = MonaDate.countdownDays(millis, "Asia/Shanghai", false, null, LocalDate.of(2026, 10, 26))
        assertTrue(result.isToday)
        assertEquals(0L, result.daysLeft)
    }

    @Test
    fun `recurring countdown uses next anniversary`() {
        // 生日 1995-06-10，每年重复；今天 2026-10-26 → 下一次 2027-06-10
        val millis = millisAt(LocalDateTime.of(1995, 6, 10, 0, 0), shanghai)
        val result = MonaDate.countdownDays(millis, "Asia/Shanghai", true, "FREQ=YEARLY;INTERVAL=1", LocalDate.of(2026, 10, 26))
        assertEquals(LocalDate.of(2027, 6, 10), result.date)
        assertEquals(LocalDate.of(2026, 10, 26), LocalDate.of(2026, 10, 26))
        assertEquals(227L, result.daysLeft)
    }

    @Test
    fun `recurring countdown on the day itself`() {
        val millis = millisAt(LocalDateTime.of(1995, 10, 26, 0, 0), shanghai)
        val result = MonaDate.countdownDays(millis, "Asia/Shanghai", true, "FREQ=YEARLY;INTERVAL=1", LocalDate.of(2026, 10, 26))
        assertTrue(result.isToday)
        assertEquals(0L, result.daysLeft)
    }

    @Test
    fun `leap day birthday in non-leap year`() {
        val millis = millisAt(LocalDateTime.of(2000, 2, 29, 0, 0), shanghai)
        val result = MonaDate.countdownDays(millis, "Asia/Shanghai", true, "FREQ=YEARLY;INTERVAL=1", LocalDate.of(2027, 1, 1))
        assertEquals(LocalDate.of(2027, 2, 28), result.date)
    }

    @Test
    fun `invalid timezone falls back to system zone without crashing`() {
        val millis = millisAt(LocalDateTime.of(2026, 12, 25, 0, 0), shanghai)
        val result = MonaDate.countdownDays(millis, "Not/AZone", false, null, LocalDate.of(2026, 10, 26))
        assertEquals(60L, result.daysLeft) // 系统时区为上海时正确
    }

    // ---------- 月历 ----------

    @Test
    fun `month grid pads correctly`() {
        val grid = MonaDate.monthGrid(java.time.YearMonth.of(2026, 10))
        // 2026-10-01 是周四 → 前面 3 个空位（一、二、三）
        assertEquals(null, grid[0])
        assertEquals(null, grid[2])
        assertEquals(LocalDate.of(2026, 10, 1), grid[3])
        assertEquals(35, grid.size) // 3 空 + 31 天 → 35 格
    }

    // ---------- 相对日期 ----------

    @Test
    fun `relative labels`() {
        val today = LocalDate.of(2026, 10, 26)
        assertEquals("今天", MonaDate.relativeLabel(today, today))
        assertEquals("明天", MonaDate.relativeLabel(today.plusDays(1), today))
        assertEquals("昨天", MonaDate.relativeLabel(today.minusDays(1), today))
        assertEquals("周三", MonaDate.relativeLabel(today.plusDays(2), today)) // 10/26/2026 是周一
        assertEquals("2025年10月26日", MonaDate.relativeLabel(today.minusDays(365), today))
    }
}
