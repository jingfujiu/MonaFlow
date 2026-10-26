package com.monaflow.app.feature.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.monaflow.app.data.local.entity.CountdownEntity
import com.monaflow.app.data.local.entity.HabitCheckInEntity
import com.monaflow.app.data.local.entity.HabitEntity
import com.monaflow.app.data.local.entity.ItemEntity
import com.monaflow.app.data.local.entity.NoteEntity
import com.monaflow.app.data.local.entity.TaskEntity
import com.monaflow.app.data.repository.CountdownRepository
import com.monaflow.app.data.repository.HabitRepository
import com.monaflow.app.data.repository.ItemRepository
import com.monaflow.app.data.repository.NoteRepository
import com.monaflow.app.data.repository.TaskRepository
import com.monaflow.app.data.settings.AppSettings
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

data class TodayUiState(
    val today: LocalDate = LocalDate.now(),
    val todayTasks: List<TaskEntity> = emptyList(), // 今日到期未完成（含逾期）
    val doneTodayCount: Int = 0,
    val habits: List<HabitEntity> = emptyList(),
    val habitCheckIns: List<HabitCheckInEntity> = emptyList(),
    val recentNotes: List<NoteEntity> = emptyList(),
    val countdowns: List<CountdownEntity> = emptyList(),
    val recentItems: List<ItemEntity> = emptyList(),
    val homeOrder: List<String> = AppSettings.DEFAULT_HOME_ORDER,
    val homeHidden: Set<String> = emptySet(),
    val homeCompact: Boolean = false,
)

class TodayViewModel(
    private val taskRepo: TaskRepository,
    private val habitRepo: HabitRepository,
    private val noteRepo: NoteRepository,
    private val countdownRepo: CountdownRepository,
    private val itemRepo: ItemRepository,
    settingsRepo: com.monaflow.app.data.settings.SettingsRepository? = null,
) : ViewModel() {

    private val settingsFlow = settingsRepo?.settings
        ?: kotlinx.coroutines.flow.flowOf(com.monaflow.app.data.settings.AppSettings())

    val uiState: StateFlow<TodayUiState> = combine(
        taskRepo.observeToday(),
        taskRepo.observeTodayDoneCount(),
        habitRepo.observeActive(),
        habitRepo.observeCheckInsOnDate(LocalDate.now().toEpochDay()),
        noteRepo.observeActive(),
        countdownRepo.observeAll(),
        itemRepo.observeActive(),
        settingsFlow,
    ) { args ->
        val settings = args[7] as com.monaflow.app.data.settings.AppSettings
        TodayUiState(
            today = LocalDate.now(),
            todayTasks = args[0] as List<TaskEntity>,
            doneTodayCount = args[1] as Int,
            habits = args[2] as List<HabitEntity>,
            habitCheckIns = args[3] as List<HabitCheckInEntity>,
            recentNotes = (args[4] as List<NoteEntity>).take(3),
            countdowns = args[5] as List<CountdownEntity>,
            recentItems = (args[6] as List<ItemEntity>).take(3),
            homeOrder = settings.homeModulesOrder,
            homeHidden = settings.homeModulesHidden,
            homeCompact = settings.homeCompact,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TodayUiState())

    fun isHabitDoneToday(habit: HabitEntity, state: TodayUiState): Boolean =
        state.habitCheckIns.any {
            it.habitId == habit.id && it.localDate == state.today.toEpochDay() &&
                it.status == HabitRepository.STATUS_DONE
        }

    fun toggleHabit(habit: HabitEntity) {
        viewModelScope.launch {
            val done = uiState.value.let { s -> isHabitDoneToday(habit, s) }
            if (done) {
                habitRepo.removeCheckIn(habit.id, LocalDate.now())
            } else {
                habitRepo.checkIn(habit.id, LocalDate.now())
            }
        }
    }

    fun completeTask(task: TaskEntity) {
        viewModelScope.launch { taskRepo.complete(task.id) }
    }

    fun uncompleteTask(task: TaskEntity) {
        viewModelScope.launch { taskRepo.uncomplete(task.id) }
    }
}
