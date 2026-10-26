package com.monaflow.app.data.local.entity

import kotlinx.serialization.Serializable

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 任务。status: 0=待办 1=已完成；priority: 0=无 1=低 2=中 3=高。
 * 时间戳字段均为 epochMillis；recurrenceRule 为统一简化 RRULE。
 * 子任务通过 parentTaskId 自引用，父任务删除时级联删除。
 */
@Serializable
@Entity(
    tableName = "tasks",
    indices = [
        Index("status"),
        Index("dueAt"),
        Index("parentTaskId"),
        Index("categoryId"),
    ],
    foreignKeys = [
        ForeignKey(
            entity = TaskEntity::class,
            parentColumns = ["id"],
            childColumns = ["parentTaskId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class TaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val description: String = "",
    val status: Int = 0,
    val priority: Int = 0,
    val createdAt: Long,
    val startAt: Long? = null,
    val dueAt: Long? = null,
    val reminderAt: Long? = null,
    val completedAt: Long? = null,
    val parentTaskId: Long? = null,
    val recurrenceRule: String? = null,
    val categoryId: Long? = null,
)
