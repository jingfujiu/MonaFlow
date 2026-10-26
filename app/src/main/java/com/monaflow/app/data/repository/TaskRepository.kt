package com.monaflow.app.data.repository

import com.monaflow.app.core.common.MonaDate
import com.monaflow.app.core.common.RecurrenceRule
import com.monaflow.app.data.local.MonaFlowDatabase
import com.monaflow.app.data.local.entity.TaskEntity
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.ZoneId

class TaskRepository(private val db: MonaFlowDatabase) {
    private val dao = db.taskDao()

    fun observeInbox(): Flow<List<TaskEntity>> = dao.observeInbox()
    fun observeToday(): Flow<List<TaskEntity>> = dao.observeToday(MonaDate.endOfTodayMillis())
    fun observeUpcoming(): Flow<List<TaskEntity>> = dao.observeUpcoming(MonaDate.endOfTodayMillis())
    fun observeDone(): Flow<List<TaskEntity>> = dao.observeDone()
    fun observeAll(): Flow<List<TaskEntity>> = dao.observeAll()
    fun observeById(id: Long): Flow<TaskEntity?> = dao.observeById(id)
    fun observeSubTasks(parentId: Long): Flow<List<TaskEntity>> = dao.observeSubTasks(parentId)
    fun observeTodayPendingCount(): Flow<Int> = dao.observeTodayPendingCount(MonaDate.endOfTodayMillis())
    fun observeTodayDoneCount(): Flow<Int> =
        dao.observeTodayDoneCount(MonaDate.startOfTodayMillis(), MonaDate.endOfTodayMillis())

    suspend fun byId(id: Long): TaskEntity? = dao.byId(id)
    suspend fun subTasksOf(parentId: Long): List<TaskEntity> = dao.subTasksOf(parentId)

    suspend fun upsert(task: TaskEntity): Long {
        return if (task.id == 0L) {
            dao.insert(task.copy(createdAt = System.currentTimeMillis()))
        } else {
            dao.update(task)
            task.id
        }
    }

    /**
     * 完成任务。重复任务在同一事务内生成下一次实例，避免重复生成；
     * complete 的 WHERE status=0 保证快速重复点击只生效一次。
     * 同时取消旧提醒并为下一次实例重排提醒。
     */
    suspend fun complete(id: Long) {
        val task = dao.byId(id) ?: return
        if (task.status != 0) return
        val now = System.currentTimeMillis()
        val next = nextInstanceOf(task, now)
        dao.completeWithHistory(task, next, now)
        com.monaflow.app.core.notifications.ReminderScheduler.cancelTask(task.id)
        next?.let { if (it.reminderAt != null) com.monaflow.app.core.notifications.ReminderScheduler.scheduleTask(it) }
    }

    suspend fun uncomplete(id: Long) {
        dao.uncomplete(id)
        // 撤销完成后若提醒时刻仍在未来则恢复提醒
        byId(id)?.let { if (it.reminderAt != null) com.monaflow.app.core.notifications.ReminderScheduler.scheduleTask(it) }
    }

    suspend fun delete(id: Long) {
        com.monaflow.app.core.notifications.ReminderScheduler.cancelTask(id)
        dao.delete(id)
    }

    suspend fun addSubTask(parentId: Long, title: String) {
        dao.insert(
            TaskEntity(
                title = title,
                parentTaskId = parentId,
                createdAt = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun toggleSubTask(sub: TaskEntity) {
        if (sub.status == 0) dao.complete(sub.id, System.currentTimeMillis()) else dao.uncomplete(sub.id)
    }

    /** 判断逾期：dueAt < 当前时刻 且未完成。 */
    fun isOverdue(task: TaskEntity, now: Long = System.currentTimeMillis()): Boolean =
        task.status == 0 && task.dueAt != null && task.dueAt < now

    /**
     * 计算重复任务的下一个实例（基于 dueAt 的当地日期推进；无 dueAt 时基于今天）。
     * 返回 null 表示无重复或规则非法。
     */
    fun nextInstanceOf(task: TaskEntity, now: Long = System.currentTimeMillis()): TaskEntity? {
        if (task.parentTaskId != null) return null // 子任务不重复
        val rule = RecurrenceRule.parse(task.recurrenceRule) ?: return null
        val zone = ZoneId.systemDefault()
        val baseDate = task.dueAt?.let {
            Instant.ofEpochMilli(it).atZone(zone).toLocalDate()
        } ?: MonaDate.today(zone)
        val nextDate = rule.nextAfter(baseDate)
        val nextDue = task.dueAt?.let {
            // 保留时刻，仅推进日期
            val zdt = Instant.ofEpochMilli(it).atZone(zone)
            nextDate.atTime(zdt.toLocalTime()).atZone(zone).toInstant().toEpochMilli()
        } ?: nextDate.atStartOfDay(zone).toInstant().toEpochMilli()
        val nextReminder = task.reminderAt?.let { rem ->
            // 下一次实例的提醒保持同一当地时刻，仅日期推进到下一次发生日
            val zdt = Instant.ofEpochMilli(rem).atZone(zone)
            nextDate.atTime(zdt.toLocalTime()).atZone(zone).toInstant().toEpochMilli()
        }
        return task.copy(
            id = 0,
            status = 0,
            completedAt = null,
            createdAt = now,
            dueAt = nextDue,
            reminderAt = nextReminder,
        )
    }
}
