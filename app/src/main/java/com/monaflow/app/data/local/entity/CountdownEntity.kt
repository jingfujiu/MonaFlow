package com.monaflow.app.data.local.entity

import kotlinx.serialization.Serializable

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 倒计时。targetEpochMillis 存目标时刻；isAllDay=true 时该时刻为目标时区的当日 00:00，
 * 天数计算基于目标时区的当地日期，不得用固定 24 小时除法。
 * recurrenceRule 通常为 FREQ=YEARLY;INTERVAL=1（生日/纪念日取下一次发生）。
 */
@Serializable
@Entity(tableName = "countdowns", indices = [Index("targetEpochMillis")])
data class CountdownEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val description: String = "",
    val targetEpochMillis: Long,
    val isAllDay: Boolean = true,
    val timeZoneId: String,
    val isRecurring: Boolean = false,
    val recurrenceRule: String? = null,
    val coverUri: String? = null,
    val themeColor: Long? = null,
    val createdAt: Long,
)
