package com.monaflow.app.core.notifications

import java.time.LocalDateTime

/**
 * 提醒时间计算（纯函数，便于单测）。
 */
object ReminderTime {

    /** 下一个“分钟数 of day”时刻：今天已过则顺延到明天（处理跨月跨年）。 */
    fun nextOccurrence(minutesOfDay: Int, now: LocalDateTime): LocalDateTime {
        val minute = minutesOfDay.coerceIn(0, 23 * 60 + 59)
        val todayAt = now.toLocalDate().atTime(minute / 60, minute % 60)
        return if (todayAt.isAfter(now)) todayAt else todayAt.plusDays(1)
    }

    /** 保修到期提醒：到期前 30/7/1 天与到期当天各提醒一次。 */
    fun warrantyShouldNotify(daysLeft: Long, lastNotifiedEpochDay: Long?, todayEpochDay: Long): Boolean {
        val notifyAt = setOf(30L, 7L, 1L, 0L)
        if (daysLeft !in notifyAt) return false
        if (lastNotifiedEpochDay == todayEpochDay) return false
        return true
    }
}
