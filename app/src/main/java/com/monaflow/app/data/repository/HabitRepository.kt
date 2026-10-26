package com.monaflow.app.data.repository

import com.monaflow.app.core.common.MonaDate
import com.monaflow.app.core.common.RecurrenceRule
import com.monaflow.app.data.local.MonaFlowDatabase
import com.monaflow.app.data.local.entity.HabitCheckInEntity
import com.monaflow.app.data.local.entity.HabitEntity
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

class HabitRepository(private val db: MonaFlowDatabase) {
    private val dao = db.habitDao()

    fun observeActive(): Flow<List<HabitEntity>> = dao.observeActive()
    fun observeArchived(): Flow<List<HabitEntity>> = dao.observeArchived()
    fun observeById(id: Long): Flow<HabitEntity?> = dao.observeById(id)
    fun observeCheckIns(habitId: Long): Flow<List<HabitCheckInEntity>> = dao.observeCheckIns(habitId)
    fun observeCheckInsOnDate(epochDay: Long): Flow<List<HabitCheckInEntity>> =
        dao.observeCheckInsOnDate(epochDay)

    suspend fun byId(id: Long): HabitEntity? = dao.byId(id)
    suspend fun all(): List<HabitEntity> = dao.all()
    suspend fun allCheckIns(): List<HabitCheckInEntity> = dao.allCheckIns()
    suspend fun checkInsOf(habitId: Long): List<HabitCheckInEntity> = dao.checkInsOf(habitId)

    suspend fun upsert(habit: HabitEntity): Long {
        return if (habit.id == 0L) {
            dao.insert(habit.copy(createdAt = System.currentTimeMillis()))
        } else {
            dao.update(habit)
            habit.id
        }
    }

    suspend fun setPaused(id: Long, paused: Boolean) = dao.setPaused(id, paused)
    suspend fun setArchived(id: Long, archived: Boolean) = dao.setArchived(id, archived)
    suspend fun delete(id: Long) = dao.delete(id)

    /** 打卡（幂等）：唯一索引保证同一习惯同一当地日期只计一次。返回是否真正计入。 */
    suspend fun checkIn(habitId: Long, date: LocalDate, note: String = "", status: Int = STATUS_DONE): Boolean {
        val inserted = dao.insertCheckIn(
            HabitCheckInEntity(
                habitId = habitId,
                localDate = date.toEpochDay(),
                status = status,
                note = note,
                createdAt = System.currentTimeMillis(),
            ),
        )
        return inserted != -1L
    }

    /** 取消某天打卡（仅允许显式操作；不自动改写历史）。 */
    suspend fun removeCheckIn(habitId: Long, date: LocalDate) =
        dao.removeCheckIn(habitId, date.toEpochDay())

    /** 规则是否在某天生效。 */
    fun isDueOn(habit: HabitEntity, date: LocalDate): Boolean {
        if (habit.isPaused || habit.isArchived) return false
        val rule = RecurrenceRule.parse(habit.scheduleRule) ?: RecurrenceRule.daily()
        return rule.isDueOn(date, LocalDate.ofEpochDay(habit.startDate))
    }

    /**
     * 连续天数：从今天向前按“应执行日”回溯连续完成数。
     * 今天应执行但尚未完成时不打断连续（当日还没结束）。
     */
    fun streak(habit: HabitEntity, checkIns: List<HabitCheckInEntity>, today: LocalDate = MonaDate.today()): Int {
        val doneDates = checkIns.filter { it.status == STATUS_DONE }
            .map { LocalDate.ofEpochDay(it.localDate) }
            .toSet()
        var cursor = today
        var streak = 0
        // 今天应执行但未完成：跳过今天，不打断
        if (isDueOn(habit, cursor) && cursor !in doneDates) {
            cursor = cursor.minusDays(1)
        }
        var guard = 0
        while (cursor >= LocalDate.ofEpochDay(habit.startDate) && guard++ < 3650) {
            if (isDueOn(habit, cursor)) {
                if (cursor in doneDates) streak++ else break
            }
            cursor = cursor.minusDays(1)
        }
        return streak
    }

    /** 月历数据：当月每天的 应执行/已完成 状态。 */
    fun monthStatus(
        habit: HabitEntity,
        checkIns: List<HabitCheckInEntity>,
        yearMonth: java.time.YearMonth,
        today: LocalDate = MonaDate.today(),
    ): List<Pair<LocalDate, HabitDayState>> {
        val doneDates = checkIns.filter { it.status == STATUS_DONE }
            .map { LocalDate.ofEpochDay(it.localDate) }
            .toSet()
        val skippedDates = checkIns.filter { it.status == STATUS_SKIPPED }
            .map { LocalDate.ofEpochDay(it.localDate) }
            .toSet()
        return (1..yearMonth.lengthOfMonth()).map { d ->
            val date = yearMonth.atDay(d)
            val state = when {
                date in doneDates -> HabitDayState.DONE
                date in skippedDates -> HabitDayState.SKIPPED
                isDueOn(habit, date) && date.isBefore(today) -> HabitDayState.MISSED
                isDueOn(habit, date) -> HabitDayState.DUE
                else -> HabitDayState.IDLE
            }
            date to state
        }
    }

    /** 累计完成次数。 */
    suspend fun totalCheckIns(habitId: Long): Int = dao.countAllCheckIns(habitId)

    companion object {
        const val STATUS_DONE = 1
        const val STATUS_SKIPPED = 2
    }
}

enum class HabitDayState { DONE, SKIPPED, MISSED, DUE, IDLE }
