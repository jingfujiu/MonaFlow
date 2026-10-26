package com.monaflow.app.feature.habits

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.monaflow.app.core.common.MonaDate
import com.monaflow.app.core.common.RecurrenceRule
import com.monaflow.app.data.local.entity.HabitCheckInEntity
import com.monaflow.app.data.local.entity.HabitEntity
import com.monaflow.app.data.repository.HabitDayState
import com.monaflow.app.data.repository.HabitRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth

data class HabitCardState(
    val habit: HabitEntity,
    val doneToday: Boolean,
    val streak: Int,
)

class HabitListViewModel(private val repo: HabitRepository) : ViewModel() {

    val habits: StateFlow<List<HabitCardState>> = combine(
        repo.observeActive(),
        repo.observeCheckInsOnDate(LocalDate.now().toEpochDay()),
    ) { habits, todayCheckIns ->
        habits.map { h ->
            HabitCardState(
                habit = h,
                doneToday = todayCheckIns.any { it.habitId == h.id && it.status == HabitRepository.STATUS_DONE },
                streak = 0, // 列表不重复计算连续天数，详情页计算
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun toggleToday(habit: HabitEntity) {
        viewModelScope.launch {
            val today = LocalDate.now()
            val done = repo.checkInsOf(habit.id).any {
                it.localDate == today.toEpochDay() && it.status == HabitRepository.STATUS_DONE
            }
            if (done) repo.removeCheckIn(habit.id, today) else repo.checkIn(habit.id, today)
        }
    }
}

data class HabitEditState(
    val id: Long = 0,
    val name: String = "",
    val description: String = "",
    val icon: String = "🌿",
    val color: Long = 0xFF8FA995,
    val freq: RecurrenceRule.Freq = RecurrenceRule.Freq.DAILY,
    val interval: Int = 1,
    val byDays: Set<java.time.DayOfWeek> = setOf(java.time.DayOfWeek.MONDAY),
    val reminderTime: LocalTime? = null,
    val targetCount: Int = 1,
    val loaded: Boolean = false,
)

class HabitEditViewModel(
    private val repo: HabitRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val id: Long = savedStateHandle.get<Long>("habitId") ?: 0L

    private val _state = MutableStateFlow(HabitEditState(id = id))
    val state: StateFlow<HabitEditState> = _state

    init {
        if (id > 0) {
            viewModelScope.launch {
                repo.byId(id)?.let { h ->
                    val rule = RecurrenceRule.parse(h.scheduleRule) ?: RecurrenceRule.daily()
                    val reminder = h.reminderMinutes?.let { LocalTime.of(it / 60, it % 60) }
                    _state.value = HabitEditState(
                        id = h.id,
                        name = h.name,
                        description = h.description,
                        icon = h.icon,
                        color = h.color,
                        freq = rule.freq,
                        interval = rule.interval,
                        byDays = rule.byDays.ifEmpty { setOf(java.time.DayOfWeek.MONDAY) },
                        reminderTime = reminder,
                        targetCount = h.targetCount,
                        loaded = true,
                    )
                }
            }
        } else {
            _state.value = _state.value.copy(loaded = true)
        }
    }

    fun update(transform: (HabitEditState) -> HabitEditState) {
        _state.value = transform(_state.value)
    }

    fun save(onDone: () -> Unit) {
        val s = _state.value
        if (s.name.isBlank()) return
        viewModelScope.launch {
            val rule = when (s.freq) {
                RecurrenceRule.Freq.DAILY -> RecurrenceRule.daily(s.interval)
                RecurrenceRule.Freq.WEEKLY -> RecurrenceRule.weekly(s.byDays, s.interval)
                RecurrenceRule.Freq.MONTHLY -> RecurrenceRule.monthly(s.interval)
                RecurrenceRule.Freq.YEARLY -> RecurrenceRule.yearly(s.interval)
            }
            val existing = if (s.id > 0) repo.byId(s.id) else null
            val entity = HabitEntity(
                id = s.id,
                name = s.name.trim(),
                description = s.description.trim(),
                icon = s.icon,
                color = s.color,
                scheduleRule = rule.encode(),
                startDate = existing?.startDate ?: LocalDate.now().toEpochDay(),
                reminderMinutes = s.reminderTime?.let { it.hour * 60 + it.minute },
                targetCount = s.targetCount,
                isPaused = existing?.isPaused ?: false,
                isArchived = existing?.isArchived ?: false,
                createdAt = existing?.createdAt ?: 0,
            )
            val savedId = repo.upsert(entity)
            // 重排每日打卡提醒
            if (entity.reminderMinutes != null && !entity.isPaused && !entity.isArchived) {
                com.monaflow.app.core.notifications.ReminderScheduler.scheduleHabit(
                    savedId, entity.name, entity.reminderMinutes!!,
                )
            } else {
                com.monaflow.app.core.notifications.ReminderScheduler.cancelHabit(savedId)
            }
            onDone()
        }
    }

    fun delete(onDone: () -> Unit) {
        if (id == 0L) return
        viewModelScope.launch {
            com.monaflow.app.core.notifications.ReminderScheduler.cancelHabit(id)
            repo.delete(id)
            onDone()
        }
    }
}

data class HabitDetailState(
    val habit: HabitEntity? = null,
    val checkIns: List<HabitCheckInEntity> = emptyList(),
    val month: YearMonth = YearMonth.now(),
    val monthData: List<Pair<LocalDate, HabitDayState>> = emptyList(),
    val streak: Int = 0,
    val totalCount: Int = 0,
    val monthDoneCount: Int = 0,
    val monthDueCount: Int = 0,
)

class HabitDetailViewModel(
    private val repo: HabitRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    val habitId: Long = savedStateHandle.get<Long>("habitId") ?: 0L

    private val month = MutableStateFlow(YearMonth.now())
    private val refresh = MutableStateFlow(0)

    val state: StateFlow<HabitDetailState> = combine(
        repo.observeById(habitId),
        repo.observeCheckIns(habitId),
        month,
        refresh,
    ) { habit, checkIns, ym, _ ->
        if (habit == null) {
            HabitDetailState()
        } else {
            val monthData = repo.monthStatus(habit, checkIns, ym)
            HabitDetailState(
                habit = habit,
                checkIns = checkIns,
                month = ym,
                monthData = monthData,
                streak = repo.streak(habit, checkIns),
                totalCount = checkIns.count { it.status == HabitRepository.STATUS_DONE },
                monthDoneCount = monthData.count { it.second == HabitDayState.DONE },
                monthDueCount = monthData.count {
                    it.second == HabitDayState.DONE || it.second == HabitDayState.MISSED
                },
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HabitDetailState())

    fun prevMonth() {
        month.value = month.value.minusMonths(1)
    }

    fun nextMonth() {
        month.value = month.value.plusMonths(1)
    }

    /** 打卡今天/补卡：历史记录改动必须由用户在此显式确认后调用。 */
    fun checkInDate(date: LocalDate) {
        viewModelScope.launch {
            repo.checkIn(habitId, date)
            refresh.value++
        }
    }

    /** 跳过某天（用户显式操作）。 */
    fun skipDate(date: LocalDate) {
        viewModelScope.launch {
            repo.checkIn(habitId, date, status = HabitRepository.STATUS_SKIPPED, note = "跳过")
            refresh.value++
        }
    }

    fun uncheckDate(date: LocalDate) {
        viewModelScope.launch {
            repo.removeCheckIn(habitId, date)
            refresh.value++
        }
    }

    fun togglePause() {
        viewModelScope.launch {
            state.value.habit?.let { repo.setPaused(it.id, !it.isPaused) }
        }
    }

    fun archive() {
        viewModelScope.launch {
            repo.setArchived(habitId, true)
        }
    }

    fun delete(onDone: () -> Unit) {
        viewModelScope.launch {
            repo.delete(habitId)
            onDone()
        }
    }
}
