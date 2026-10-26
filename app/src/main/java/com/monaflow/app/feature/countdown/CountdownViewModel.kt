package com.monaflow.app.feature.countdown

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.monaflow.app.core.common.MonaDate
import com.monaflow.app.data.local.entity.CountdownEntity
import com.monaflow.app.data.repository.CountdownRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

class CountdownListViewModel(repo: CountdownRepository) : ViewModel() {
    val countdowns: StateFlow<List<CountdownEntity>> =
        repo.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}

data class CountdownEditState(
    val id: Long = 0,
    val title: String = "",
    val description: String = "",
    val date: LocalDate = LocalDate.now().plusDays(7),
    val time: LocalTime = LocalTime.of(9, 0),
    val isAllDay: Boolean = true,
    val timeZoneId: String = ZoneId.systemDefault().id,
    val isRecurring: Boolean = false,
    val themeColor: Long? = null,
    val loaded: Boolean = false,
)

class CountdownEditViewModel(
    private val repo: CountdownRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val id: Long = savedStateHandle.get<Long>("cid") ?: 0L

    private val _state = kotlinx.coroutines.flow.MutableStateFlow(CountdownEditState(id = id))
    val state: StateFlow<CountdownEditState> = _state

    init {
        if (id > 0) {
            viewModelScope.launch {
                repo.byId(id)?.let { c ->
                    val zone = runCatching { ZoneId.of(c.timeZoneId) }.getOrElse { ZoneId.systemDefault() }
                    val ldt = LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(c.targetEpochMillis), zone)
                    _state.value = CountdownEditState(
                        id = c.id,
                        title = c.title,
                        description = c.description,
                        date = ldt.toLocalDate(),
                        time = ldt.toLocalTime(),
                        isAllDay = c.isAllDay,
                        timeZoneId = c.timeZoneId,
                        isRecurring = c.isRecurring,
                        themeColor = c.themeColor,
                        loaded = true,
                    )
                }
            }
        } else {
            _state.value = _state.value.copy(loaded = true)
        }
    }

    fun update(transform: (CountdownEditState) -> CountdownEditState) {
        _state.value = transform(_state.value)
    }

    fun save(onDone: () -> Unit) {
        val s = _state.value
        if (s.title.isBlank()) return
        viewModelScope.launch {
            val zone = runCatching { ZoneId.of(s.timeZoneId) }.getOrElse { ZoneId.systemDefault() }
            val ldt = if (s.isAllDay) s.date.atStartOfDay() else s.date.atTime(s.time)
            val millis = ldt.atZone(zone).toInstant().toEpochMilli()
            val entity = CountdownEntity(
                id = s.id,
                title = s.title.trim(),
                description = s.description.trim(),
                targetEpochMillis = millis,
                isAllDay = s.isAllDay,
                timeZoneId = s.timeZoneId,
                isRecurring = s.isRecurring,
                recurrenceRule = if (s.isRecurring) "FREQ=YEARLY;INTERVAL=1" else null,
                themeColor = s.themeColor,
                createdAt = 0,
            )
            if (s.id == 0L) repo.upsert(entity) else {
                val old = repo.byId(s.id)
                repo.upsert(entity.copy(createdAt = old?.createdAt ?: System.currentTimeMillis()))
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

    fun daysLeft(s: CountdownEditState): MonaDate.CountdownDays {
        val zone = runCatching { ZoneId.of(s.timeZoneId) }.getOrElse { ZoneId.systemDefault() }
        val ldt = if (s.isAllDay) s.date.atStartOfDay() else s.date.atTime(s.time)
        val millis = ldt.atZone(zone).toInstant().toEpochMilli()
        return MonaDate.countdownDays(
            millis,
            s.timeZoneId,
            s.isRecurring,
            if (s.isRecurring) "FREQ=YEARLY;INTERVAL=1" else null,
        )
    }
}
