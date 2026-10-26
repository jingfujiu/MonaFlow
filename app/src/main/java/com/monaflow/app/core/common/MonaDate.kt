package com.monaflow.app.core.common

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * 时间语义统一约定：
 * - 时间戳（createdAt 等）用 epochMillis（UTC 瞬时）；
 * - “当地日期”（打卡、购买日期、保修日期）用 epochDay；
 * - 倒计时天数必须基于目标时区的 LocalDate 差值，禁止 24h 除法。
 */
object MonaDate {

    private val dfFull = DateTimeFormatter.ofPattern("yyyy年M月d日")
    private val dfShort = DateTimeFormatter.ofPattern("M月d日")
    private val dfTime = DateTimeFormatter.ofPattern("HH:mm")
    private val dfDateTime = DateTimeFormatter.ofPattern("yyyy年M月d日 HH:mm")

    fun today(zone: ZoneId = ZoneId.systemDefault()): LocalDate = LocalDate.now(zone)

    fun todayEpochDay(zone: ZoneId = ZoneId.systemDefault()): Long = today(zone).toEpochDay()

    fun startOfTodayMillis(zone: ZoneId = ZoneId.systemDefault()): Long =
        today(zone).atStartOfDay(zone).toInstant().toEpochMilli()

    fun endOfTodayMillis(zone: ZoneId = ZoneId.systemDefault()): Long =
        today(zone).plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1

    fun formatFull(d: LocalDate): String = d.format(dfFull)
    fun formatShort(d: LocalDate): String = d.format(dfShort)
    fun formatTime(t: LocalTime): String = t.format(dfTime)
    fun formatDateTime(millis: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), zone).format(dfDateTime)

    fun formatMillisTime(millis: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), zone).format(dfTime)

    fun formatEpochDay(d: Long): String = formatFull(LocalDate.ofEpochDay(d))

    /** 相对日期描述：今天/明天/昨天/星期X/M月d日。 */
    fun relativeLabel(d: LocalDate, today: LocalDate = LocalDate.now()): String = when (d) {
        today -> "今天"
        today.plusDays(1) -> "明天"
        today.minusDays(1) -> "昨天"
        else -> {
            val yearDiff = d.year - today.year
            if (yearDiff == 0 && d.isAfter(today) && d.isBefore(today.plusDays(7))) {
                "周" + cnWeekday(d.dayOfWeek)
            } else if (yearDiff != 0) {
                formatFull(d)
            } else {
                formatShort(d)
            }
        }
    }

    fun cnWeekday(d: DayOfWeek): String = when (d) {
        DayOfWeek.MONDAY -> "一"
        DayOfWeek.TUESDAY -> "二"
        DayOfWeek.WEDNESDAY -> "三"
        DayOfWeek.THURSDAY -> "四"
        DayOfWeek.FRIDAY -> "五"
        DayOfWeek.SATURDAY -> "六"
        DayOfWeek.SUNDAY -> "日"
    }

    // ---------- 倒计时 ----------

    /**
     * 倒计时天数（纯日期差，非 24h 除法）。
     * 重复倒计时返回下一次发生日期相对 today 的天数；今天为 0。
     */
    fun countdownDays(
        targetEpochMillis: Long,
        timeZoneId: String,
        isRecurring: Boolean,
        recurrenceRule: String?,
        today: LocalDate = LocalDate.now(),
    ): CountdownDays {
        val zone = runCatching { ZoneId.of(timeZoneId) }.getOrElse { ZoneId.systemDefault() }
        val targetDate = Instant.ofEpochMilli(targetEpochMillis).atZone(zone).toLocalDate()
        val effective = if (isRecurring) {
            val rule = RecurrenceRule.parse(recurrenceRule) ?: RecurrenceRule.yearly()
            rule.nextOnOrAfter(today, targetDate)
        } else {
            targetDate
        }
        val days = ChronoUnit.DAYS.between(today, effective)
        return CountdownDays(
            date = effective,
            daysLeft = days,
            isPast = !isRecurring && days < 0,
            isToday = days == 0L,
            passedDays = if (!isRecurring && days < 0) -days else 0,
        )
    }

    data class CountdownDays(
        val date: LocalDate,
        val daysLeft: Long,
        val isPast: Boolean,
        val isToday: Boolean,
        val passedDays: Long,
    )

    /** 月历网格（周一起始），返回该月日历格（含首尾补空）。 */
    fun monthGrid(yearMonth: YearMonth): List<LocalDate?> {
        val first = yearMonth.atDay(1)
        val leadingEmpty = first.dayOfWeek.value - DayOfWeek.MONDAY.value
        val cells = mutableListOf<LocalDate?>()
        repeat(leadingEmpty) { cells.add(null) }
        for (d in 1..yearMonth.lengthOfMonth()) cells.add(yearMonth.atDay(d))
        while (cells.size % 7 != 0) cells.add(null)
        return cells
    }
}
