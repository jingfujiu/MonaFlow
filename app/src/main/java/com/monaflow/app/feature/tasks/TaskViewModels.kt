package com.monaflow.app.feature.tasks

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.monaflow.app.data.local.entity.TaskEntity
import com.monaflow.app.data.repository.TaskRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** 任务视图。 */
enum class TaskView(val label: String) {
    INBOX("收件箱"),
    TODAY("今天"),
    UPCOMING("即将到期"),
    DONE("已完成"),
    ALL("全部"),
}

class TaskListViewModel(private val repo: TaskRepository) : ViewModel() {

    val view = MutableStateFlow(TaskView.INBOX)

    val tasks: StateFlow<List<TaskEntity>> = combine(
        view,
        repo.observeInbox(),
        repo.observeToday(),
        repo.observeUpcoming(),
        repo.observeDone(),
        repo.observeAll(),
    ) { args ->
        val v = args[0] as TaskView
        @Suppress("UNCHECKED_CAST")
        when (v) {
            TaskView.INBOX -> args[1] as List<TaskEntity>
            TaskView.TODAY -> args[2] as List<TaskEntity>
            TaskView.UPCOMING -> args[3] as List<TaskEntity>
            TaskView.DONE -> args[4] as List<TaskEntity>
            TaskView.ALL -> args[5] as List<TaskEntity>
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setView(v: TaskView) {
        view.value = v
    }

    fun complete(task: TaskEntity) = viewModelScope.launch { repo.complete(task.id) }
    fun uncomplete(task: TaskEntity) = viewModelScope.launch { repo.uncomplete(task.id) }
    fun delete(task: TaskEntity) = viewModelScope.launch { repo.delete(task.id) }

    fun isOverdue(task: TaskEntity): Boolean = repo.isOverdue(task)
}

data class TaskEditState(
    val id: Long = 0,
    val title: String = "",
    val description: String = "",
    val priority: Int = 0,
    val hasDue: Boolean = false,
    val dueDate: LocalDate = LocalDate.now(),
    val dueTime: LocalTime = LocalTime.of(18, 0),
    val hasReminder: Boolean = false,
    val reminderDate: LocalDate = LocalDate.now(),
    val reminderTime: LocalTime = LocalTime.of(9, 0),
    val recurrenceRule: String? = null,
    val subTasks: List<TaskEntity> = emptyList(),
    val loaded: Boolean = false,
)

class TaskEditViewModel(
    private val repo: TaskRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val id: Long = savedStateHandle.get<Long>("taskId") ?: 0L

    private val _state = MutableStateFlow(TaskEditState(id = id))
    val state: StateFlow<TaskEditState> = _state

    private val zone: ZoneId = ZoneId.systemDefault()

    init {
        if (id > 0) {
            viewModelScope.launch {
                repo.byId(id)?.let { t ->
                    val dueLdt = t.dueAt?.let {
                        java.time.LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(it), zone)
                    }
                    val remLdt = t.reminderAt?.let {
                        java.time.LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(it), zone)
                    }
                    _state.value = TaskEditState(
                        id = t.id,
                        title = t.title,
                        description = t.description,
                        priority = t.priority,
                        hasDue = t.dueAt != null,
                        dueDate = dueLdt?.toLocalDate() ?: LocalDate.now(),
                        dueTime = dueLdt?.toLocalTime() ?: LocalTime.of(18, 0),
                        hasReminder = t.reminderAt != null,
                        reminderDate = remLdt?.toLocalDate() ?: LocalDate.now(),
                        reminderTime = remLdt?.toLocalTime() ?: LocalTime.of(9, 0),
                        recurrenceRule = t.recurrenceRule,
                        subTasks = repo.subTasksOf(id),
                        loaded = true,
                    )
                }
            }
        } else {
            _state.value = _state.value.copy(loaded = true)
        }
    }

    fun update(transform: (TaskEditState) -> TaskEditState) {
        _state.value = transform(_state.value)
    }

    fun addSubTask(title: String) {
        if (title.isBlank() || id == 0L) return
        viewModelScope.launch {
            repo.addSubTask(id, title.trim())
            _state.value = _state.value.copy(subTasks = repo.subTasksOf(id))
        }
    }

    fun toggleSubTask(sub: TaskEntity) {
        viewModelScope.launch {
            repo.toggleSubTask(sub)
            _state.value = _state.value.copy(subTasks = repo.subTasksOf(id))
        }
    }

    fun removeSubTask(sub: TaskEntity) {
        viewModelScope.launch {
            repo.delete(sub.id)
            _state.value = _state.value.copy(subTasks = repo.subTasksOf(id))
        }
    }

    fun save(onDone: () -> Unit) {
        val s = _state.value
        if (s.title.isBlank()) return
        viewModelScope.launch {
            val dueAt = if (s.hasDue) {
                s.dueDate.atTime(s.dueTime).atZone(zone).toInstant().toEpochMilli()
            } else null
            val reminderAt = if (s.hasReminder) {
                s.reminderDate.atTime(s.reminderTime).atZone(zone).toInstant().toEpochMilli()
            } else null
            if (s.id == 0L) {
                repo.upsert(
                    TaskEntity(
                        title = s.title.trim(),
                        description = s.description.trim(),
                        priority = s.priority,
                        createdAt = System.currentTimeMillis(),
                        dueAt = dueAt,
                        reminderAt = reminderAt,
                        recurrenceRule = s.recurrenceRule,
                    ),
                )
            } else {
                val existing = repo.byId(s.id) ?: return@launch
                val saved = existing.copy(
                    title = s.title.trim(),
                    description = s.description.trim(),
                    priority = s.priority,
                    dueAt = dueAt,
                    reminderAt = reminderAt,
                    recurrenceRule = s.recurrenceRule,
                )
                repo.upsert(saved)
                // 保存后重排提醒（修改日期/取消提醒都要更新旧闹钟）
                if (saved.reminderAt != null && saved.status == 0) {
                    com.monaflow.app.core.notifications.ReminderScheduler.scheduleTask(saved)
                } else {
                    com.monaflow.app.core.notifications.ReminderScheduler.cancelTask(saved.id)
                }
            }
            onDone()
        }
    }

    fun delete(onDone: () -> Unit) {
        if (id == 0L) return
        viewModelScope.launch {
            repo.delete(id)
            onDone()
        }
    }
}
