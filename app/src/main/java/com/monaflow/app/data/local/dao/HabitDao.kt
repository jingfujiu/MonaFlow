package com.monaflow.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.monaflow.app.data.local.entity.HabitCheckInEntity
import com.monaflow.app.data.local.entity.HabitEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HabitDao {

    @Query("SELECT * FROM habits WHERE isArchived = 0 ORDER BY createdAt ASC")
    fun observeActive(): Flow<List<HabitEntity>>

    @Query("SELECT * FROM habits WHERE isArchived = 1 ORDER BY createdAt ASC")
    fun observeArchived(): Flow<List<HabitEntity>>

    @Query("SELECT * FROM habits WHERE id = :id")
    suspend fun byId(id: Long): HabitEntity?

    @Query("SELECT * FROM habits WHERE id = :id")
    fun observeById(id: Long): Flow<HabitEntity?>

    @Query("SELECT * FROM habits")
    suspend fun all(): List<HabitEntity>

    @Insert
    suspend fun insert(habit: HabitEntity): Long

    @Update
    suspend fun update(habit: HabitEntity)

    @Query("UPDATE habits SET isPaused = :paused WHERE id = :id")
    suspend fun setPaused(id: Long, paused: Boolean)

    @Query("UPDATE habits SET isArchived = :archived WHERE id = :id")
    suspend fun setArchived(id: Long, archived: Boolean)

    @Query("DELETE FROM habits WHERE id = :id")
    suspend fun delete(id: Long)

    // ---------- 打卡 ----------

    /** IGNORE + 唯一索引保证同一习惯同一当地日期只计一次；返回 -1 表示重复打卡被忽略。 */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCheckIn(checkIn: HabitCheckInEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCheckIns(checkIns: List<HabitCheckInEntity>): List<Long>

    @Query("DELETE FROM habit_checkins WHERE habitId = :habitId AND localDate = :localDate")
    suspend fun removeCheckIn(habitId: Long, localDate: Long)

    @Query("SELECT * FROM habit_checkins WHERE habitId = :habitId ORDER BY localDate DESC")
    fun observeCheckIns(habitId: Long): Flow<List<HabitCheckInEntity>>

    @Query("SELECT * FROM habit_checkins WHERE habitId = :habitId ORDER BY localDate DESC")
    suspend fun checkInsOf(habitId: Long): List<HabitCheckInEntity>

    @Query("SELECT * FROM habit_checkins")
    suspend fun allCheckIns(): List<HabitCheckInEntity>

    @Query("SELECT * FROM habit_checkins WHERE localDate = :localDate")
    fun observeCheckInsOnDate(localDate: Long): Flow<List<HabitCheckInEntity>>

    @Query(
        "SELECT COUNT(*) FROM habit_checkins WHERE habitId = :habitId AND localDate BETWEEN :fromEpochDay AND :toEpochDay"
    )
    suspend fun countCheckInsBetween(habitId: Long, fromEpochDay: Long, toEpochDay: Long): Int

    @Query("SELECT COUNT(*) FROM habit_checkins WHERE habitId = :habitId")
    suspend fun countAllCheckIns(habitId: Long): Int

    @Query(
        """
        UPDATE habit_checkins SET note = :note, status = :status
        WHERE habitId = :habitId AND localDate = :localDate
        """
    )
    suspend fun updateCheckIn(habitId: Long, localDate: Long, status: Int, note: String)
}
