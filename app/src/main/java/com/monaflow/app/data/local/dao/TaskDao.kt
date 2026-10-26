package com.monaflow.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.monaflow.app.data.local.entity.TaskEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {

    @Query("SELECT * FROM tasks WHERE status = 0 AND parentTaskId IS NULL ORDER BY priority DESC, dueAt IS NULL, dueAt ASC, createdAt DESC")
    fun observeInbox(): Flow<List<TaskEntity>>

    /** 今天：到期时间在今天结束之前（含逾期）。 */
    @Query(
        """
        SELECT * FROM tasks
        WHERE status = 0 AND parentTaskId IS NULL AND dueAt IS NOT NULL AND dueAt <= :endOfToday
        ORDER BY dueAt ASC, priority DESC
        """
    )
    fun observeToday(endOfToday: Long): Flow<List<TaskEntity>>

    /** 即将到期：今天之后的任务。 */
    @Query(
        """
        SELECT * FROM tasks
        WHERE status = 0 AND parentTaskId IS NULL AND dueAt IS NOT NULL AND dueAt > :endOfToday
        ORDER BY dueAt ASC, priority DESC
        """
    )
    fun observeUpcoming(endOfToday: Long): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE status = 1 AND parentTaskId IS NULL ORDER BY completedAt DESC")
    fun observeDone(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE parentTaskId IS NULL ORDER BY status ASC, priority DESC, dueAt IS NULL, dueAt ASC, createdAt DESC")
    fun observeAll(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun byId(id: Long): TaskEntity?

    @Query("SELECT * FROM tasks WHERE id = :id")
    fun observeById(id: Long): Flow<TaskEntity?>

    @Query("SELECT * FROM tasks WHERE parentTaskId = :parentId ORDER BY createdAt ASC")
    fun observeSubTasks(parentId: Long): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE parentTaskId = :parentId ORDER BY createdAt ASC")
    suspend fun subTasksOf(parentId: Long): List<TaskEntity>

    @Query("SELECT * FROM tasks")
    suspend fun all(): List<TaskEntity>

    @Insert
    suspend fun insert(task: TaskEntity): Long

    @Insert
    suspend fun insertAll(tasks: List<TaskEntity>): List<Long>

    @Update
    suspend fun update(task: TaskEntity)

    @Query("UPDATE tasks SET status = 1, completedAt = :now WHERE id = :id AND status = 0")
    suspend fun complete(id: Long, now: Long): Int

    @Query("UPDATE tasks SET status = 0, completedAt = NULL WHERE id = :id AND status = 1")
    suspend fun uncomplete(id: Long): Int

    @Query("DELETE FROM tasks WHERE id = :id")
    suspend fun delete(id: Long)

    /** 首页统计：今日到期未完成数。 */
    @Query("SELECT COUNT(*) FROM tasks WHERE status = 0 AND parentTaskId IS NULL AND dueAt IS NOT NULL AND dueAt <= :endOfToday")
    fun observeTodayPendingCount(endOfToday: Long): Flow<Int>

    /** 首页统计：今日完成数（completedAt 在今天内）。 */
    @Query("SELECT COUNT(*) FROM tasks WHERE status = 1 AND completedAt IS NOT NULL AND completedAt BETWEEN :startOfToday AND :endOfToday")
    fun observeTodayDoneCount(startOfToday: Long, endOfToday: Long): Flow<Int>

    @Transaction
    suspend fun completeWithHistory(task: TaskEntity, nextInstance: TaskEntity?, now: Long) {
        val updated = complete(task.id, now)
        if (updated == 1 && nextInstance != null) {
            insert(nextInstance)
        }
    }
}
