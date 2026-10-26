package com.monaflow.app.data.local.entity

import kotlinx.serialization.Serializable

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 习惯。scheduleRule 为统一简化 RRULE：
 * FREQ=DAILY;INTERVAL=n 或 FREQ=WEEKLY;INTERVAL=n;BYDAY=MO,TU,WE,TH,FR,SA,SU
 * reminderMinutes 为一天内提醒分钟数（0..1439），null 表示不提醒。
 */
@Serializable
@Entity(tableName = "habits", indices = [Index("isArchived"), Index("isPaused")])
data class HabitEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val description: String = "",
    val icon: String = "🌿",
    val color: Long = 0xFF8FA995,
    val scheduleRule: String = "FREQ=DAILY;INTERVAL=1",
    val startDate: Long, // epochDay
    val reminderMinutes: Int? = null,
    val targetCount: Int = 1,
    val isPaused: Boolean = false,
    val isArchived: Boolean = false,
    val createdAt: Long,
)

/** 打卡记录。habitId + localDate 唯一：同一习惯同一当地日期只计一次。 */
@Serializable
@Entity(
    tableName = "habit_checkins",
    indices = [
        Index(value = ["habitId", "localDate"], unique = true),
        Index("localDate"),
    ],
    foreignKeys = [
        ForeignKey(
            entity = HabitEntity::class,
            parentColumns = ["id"],
            childColumns = ["habitId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class HabitCheckInEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val habitId: Long,
    val localDate: Long, // epochDay（当地日期）
    val status: Int, // 1=完成 2=跳过
    val note: String = "",
    val createdAt: Long,
)
