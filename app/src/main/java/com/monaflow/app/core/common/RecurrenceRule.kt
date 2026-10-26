package com.monaflow.app.core.common

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters

/**
 * 统一的简化重复规则（习惯、重复任务、重复倒计时共用，避免各自实现导致行为不一致）。
 * 格式：FREQ=DAILY;INTERVAL=n | FREQ=WEEKLY;INTERVAL=n;BYDAY=MO,TU,...
 *      FREQ=MONTHLY;INTERVAL=n | FREQ=YEARLY;INTERVAL=n
 * 闰年 2/29 的年度重复在非闰年落到 2/28；月末日期（如 31 日）在短月落到当月最后一天。
 */
data class RecurrenceRule(
    val freq: Freq,
    val interval: Int,
    val byDays: Set<DayOfWeek> = emptySet(),
) {
    enum class Freq { DAILY, WEEKLY, MONTHLY, YEARLY }

    companion object {
        fun daily(interval: Int = 1) = RecurrenceRule(Freq.DAILY, interval.coerceAtLeast(1))
        fun weekly(byDays: Set<DayOfWeek>, interval: Int = 1) =
            RecurrenceRule(Freq.WEEKLY, interval.coerceAtLeast(1), byDays)
        fun monthly(interval: Int = 1) = RecurrenceRule(Freq.MONTHLY, interval.coerceAtLeast(1))
        fun yearly(interval: Int = 1) = RecurrenceRule(Freq.YEARLY, interval.coerceAtLeast(1))

        /** 解析失败返回 null，调用方必须回退为不重复，不得抛异常。 */
        fun parse(raw: String?): RecurrenceRule? {
            if (raw.isNullOrBlank()) return null
            val parts = raw.split(";").mapNotNull {
                val i = it.indexOf('=')
                if (i <= 0) null else it.substring(0, i).trim().uppercase() to
                    it.substring(i + 1).trim().uppercase()
            }.toMap()
            val freq = when (parts["FREQ"]) {
                "DAILY" -> Freq.DAILY
                "WEEKLY" -> Freq.WEEKLY
                "MONTHLY" -> Freq.MONTHLY
                "YEARLY" -> Freq.YEARLY
                else -> return null
            }
            val interval = parts["INTERVAL"]?.toIntOrNull()?.takeIf { it >= 1 } ?: 1
            val byDays = parts["BYDAY"]
                ?.split(",")
                ?.mapNotNull { dayCodeToDay(it.trim()) }
                ?.toSet()
                .orEmpty()
            if (freq == Freq.WEEKLY && byDays.isEmpty()) {
                return null // WEEKLY 必须显式指定星期
            }
            return RecurrenceRule(freq, interval, byDays)
        }

        fun dayCodeToDay(code: String): DayOfWeek? = when (code.uppercase()) {
            "MO" -> DayOfWeek.MONDAY
            "TU" -> DayOfWeek.TUESDAY
            "WE" -> DayOfWeek.WEDNESDAY
            "TH" -> DayOfWeek.THURSDAY
            "FR" -> DayOfWeek.FRIDAY
            "SA" -> DayOfWeek.SATURDAY
            "SU" -> DayOfWeek.SUNDAY
            else -> null
        }

        fun dayToCode(d: DayOfWeek): String = when (d) {
            DayOfWeek.MONDAY -> "MO"
            DayOfWeek.TUESDAY -> "TU"
            DayOfWeek.WEDNESDAY -> "WE"
            DayOfWeek.THURSDAY -> "TH"
            DayOfWeek.FRIDAY -> "FR"
            DayOfWeek.SATURDAY -> "SA"
            DayOfWeek.SUNDAY -> "SU"
        }

        /** 闰年/月末钳制：以 base 的月-日为模板落到 year 年（2/29 非闰年→2/28）。 */
        fun clamped(base: LocalDate, year: Int): LocalDate {
            val lastDay = YearMonth.of(year, base.month).lengthOfMonth()
            return LocalDate.of(year, base.month, base.dayOfMonth.coerceAtMost(lastDay))
        }
    }

    fun encode(): String = buildString {
        append("FREQ=").append(freq.name)
        append(";INTERVAL=").append(interval)
        if (byDays.isNotEmpty()) {
            append(";BYDAY=").append(
                byDays.sortedBy { it.value }.joinToString(",") { dayToCode(it) },
            )
        }
    }

    /** 严格晚于 date 的下一次发生（date 本身应为一个发生日；WEEKLY 以 date 所在 ISO 周为锚）。 */
    fun nextAfter(date: LocalDate): LocalDate {
        return when (freq) {
            Freq.DAILY -> date.plusDays(interval.toLong())
            Freq.WEEKLY -> {
                val weekStart = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                val sorted = byDays.sortedBy { it.value }
                for (dow in sorted) {
                    val d = weekStart.plusDays((dow.value - DayOfWeek.MONDAY.value + 7).toLong() % 7)
                    if (d.isAfter(date)) return d
                }
                weekStart.plusWeeks(interval.toLong())
                    .plusDays((sorted.first().value - DayOfWeek.MONDAY.value + 7).toLong() % 7)
            }
            Freq.MONTHLY -> {
                val nextMonth = YearMonth.from(date).plusMonths(interval.toLong())
                nextMonth.atDay(date.dayOfMonth.coerceAtMost(nextMonth.lengthOfMonth()))
            }
            Freq.YEARLY -> clamped(date, date.year + interval)
        }
    }

    /** >= date 的最近一次发生（以 base 为规则起点，用于倒计时取“下一次发生日期”）。 */
    fun nextOnOrAfter(date: LocalDate, base: LocalDate): LocalDate {
        return when (freq) {
        Freq.DAILY -> {
            val diff = java.time.temporal.ChronoUnit.DAYS.between(base, date)
            val k = if (diff <= 0) 0L else (diff + interval - 1) / interval
            base.plusDays(k * interval)
        }
        Freq.WEEKLY -> {
            var cur = if (date.isBefore(base)) base else date
            var guard = 0
            val maxScan = 7L * interval + 7
            while (cur.dayOfWeek !in byDays ||
                (cur.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toEpochDay() / 7 -
                    base.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toEpochDay() / 7) % interval != 0L
            ) {
                cur = cur.plusDays(1)
                if (++guard > maxScan) return date // 防御
            }
            cur
        }
        Freq.MONTHLY -> {
            val monthDiff = java.time.temporal.ChronoUnit.MONTHS.between(
                YearMonth.from(base),
                YearMonth.from(date),
            )
            val k = if (monthDiff <= 0) 0L else (monthDiff + interval - 1) / interval
            val ym = YearMonth.from(base).plusMonths(k * interval)
            ym.atDay(base.dayOfMonth.coerceAtMost(ym.lengthOfMonth()))
        }
        Freq.YEARLY -> {
            // 以 base 的月-日为模板逐年匹配；2/29 在非闰年钳到 2/28，闰年恢复 2/29
            var k = 0L
            var candidate = base
            while (candidate.isBefore(date)) {
                k += 1
                candidate = clamped(base, base.year + (k * interval).toInt())
                if (k > 5000) return date
            }
            candidate
        }
        }
    }

    /** date 是否为应执行日（从 startDate 起算）。 */
    fun isDueOn(date: LocalDate, startDate: LocalDate): Boolean {
        if (date.isBefore(startDate)) return false
        return when (freq) {
            Freq.DAILY -> (date.toEpochDay() - startDate.toEpochDay()) % interval == 0L
            Freq.WEEKLY -> {
                if (date.dayOfWeek !in byDays) return false
                val w1 = startDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toEpochDay() / 7
                val w2 = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toEpochDay() / 7
                (w2 - w1) % interval == 0L
            }
            Freq.MONTHLY -> {
                val m1 = (startDate.year * 12L + startDate.monthValue)
                val m2 = (date.year * 12L + date.monthValue)
                if ((m2 - m1) % interval != 0L) return false
                val ym = YearMonth.from(date)
                date.dayOfMonth == startDate.dayOfMonth.coerceAtMost(ym.lengthOfMonth())
            }
            Freq.YEARLY -> {
                if ((date.year - startDate.year).toLong() % interval != 0L) return false
                date == clamped(startDate, date.year)
            }
        }
    }
}

/** 规则的人类可读描述（UI 共用）。 */
fun describeRule(rule: String?): String {
    val r = RecurrenceRule.parse(rule) ?: return "不重复"
    return when (r.freq) {
        RecurrenceRule.Freq.DAILY -> if (r.interval == 1) "每天" else "每 ${r.interval} 天"
        RecurrenceRule.Freq.WEEKLY -> {
            val days = r.byDays.sortedBy { it.value }.joinToString("、") { "周" + cnWd(it) }
            if (r.interval == 1) "每周（$days）" else "每 ${r.interval} 周（$days）"
        }
        RecurrenceRule.Freq.MONTHLY -> if (r.interval == 1) "每月" else "每 ${r.interval} 月"
        RecurrenceRule.Freq.YEARLY -> if (r.interval == 1) "每年" else "每 ${r.interval} 年"
    }
}

private fun cnWd(d: DayOfWeek): String = when (d) {
    DayOfWeek.MONDAY -> "一"
    DayOfWeek.TUESDAY -> "二"
    DayOfWeek.WEDNESDAY -> "三"
    DayOfWeek.THURSDAY -> "四"
    DayOfWeek.FRIDAY -> "五"
    DayOfWeek.SATURDAY -> "六"
    DayOfWeek.SUNDAY -> "日"
}
