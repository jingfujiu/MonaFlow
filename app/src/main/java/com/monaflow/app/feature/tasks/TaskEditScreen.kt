package com.monaflow.app.feature.tasks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.monaflow.app.core.common.MonaDate
import com.monaflow.app.core.common.RecurrenceRule
import com.monaflow.app.core.theme.LocalMonaColors
import com.monaflow.app.core.ui.ConfirmDialog
import com.monaflow.app.core.ui.MonaCard
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

private val WEEKDAY_LABELS = listOf("一", "二", "三", "四", "五", "六", "日")
private val WEEKDAYS = listOf(
    java.time.DayOfWeek.MONDAY, java.time.DayOfWeek.TUESDAY, java.time.DayOfWeek.WEDNESDAY,
    java.time.DayOfWeek.THURSDAY, java.time.DayOfWeek.FRIDAY, java.time.DayOfWeek.SATURDAY,
    java.time.DayOfWeek.SUNDAY,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskEditScreen(
    viewModel: TaskEditViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val mona = LocalMonaColors.current
    var confirmDelete by remember { mutableStateOf(false) }
    var showRecurrence by remember { mutableStateOf(false) }

    if (!state.loaded) return

    // 日期/时间选择对话框状态
    var dateTarget by remember { mutableStateOf<String?>(null) } // "due" / "reminder"
    var timeTarget by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (state.id == 0L) "新建任务" else "编辑任务") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "返回") }
                },
                actions = {
                    if (state.id > 0) {
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(Icons.Outlined.Delete, "删除", tint = Color(0xFFB3564E))
                        }
                    }
                    IconButton(onClick = { viewModel.save(onBack) }) {
                        Icon(Icons.Outlined.Check, "保存", tint = Color(mona.primary))
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                OutlinedTextField(
                    value = state.title,
                    onValueChange = { v -> viewModel.update { it.copy(title = v) } },
                    label = { Text("任务标题") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
            }
            item {
                OutlinedTextField(
                    value = state.description,
                    onValueChange = { v -> viewModel.update { it.copy(description = v) } },
                    label = { Text("说明") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                )
            }
            // 优先级
            item {
                MonaCard {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("优先级", fontSize = 14.sp, color = Color(mona.textPrimary))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(0, 1, 2, 3).forEach { p ->
                                FilterChip(
                                    selected = state.priority == p,
                                    onClick = { viewModel.update { it.copy(priority = p) } },
                                    label = {
                                        Text(
                                            if (p == 0) "无" else "${PRIORITY_LABELS[p]}优先",
                                            fontSize = 12.sp,
                                        )
                                    },
                                )
                            }
                        }
                    }
                }
            }
            // 截止时间
            item {
                MonaCard {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("截止时间", fontSize = 14.sp, color = Color(mona.textPrimary))
                            Switch(
                                checked = state.hasDue,
                                onCheckedChange = { v -> viewModel.update { it.copy(hasDue = v) } },
                            )
                        }
                        if (state.hasDue) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(onClick = { dateTarget = "due" }) {
                                    Text(MonaDate.formatFull(state.dueDate), color = Color(mona.primary))
                                }
                                TextButton(onClick = { timeTarget = "due" }) {
                                    Text(MonaDate.formatTime(state.dueTime), color = Color(mona.primary))
                                }
                            }
                        }
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("提醒", fontSize = 14.sp, color = Color(mona.textPrimary))
                            Switch(
                                checked = state.hasReminder,
                                onCheckedChange = { v -> viewModel.update { it.copy(hasReminder = v) } },
                            )
                        }
                        if (state.hasReminder) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(onClick = { dateTarget = "reminder" }) {
                                    Text(MonaDate.formatFull(state.reminderDate), color = Color(mona.primary))
                                }
                                TextButton(onClick = { timeTarget = "reminder" }) {
                                    Text(MonaDate.formatTime(state.reminderTime), color = Color(mona.primary))
                                }
                            }
                        }
                    }
                }
            }
            // 重复规则
            item {
                MonaCard(onClick = { showRecurrence = true }) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("重复", fontSize = 14.sp, color = Color(mona.textPrimary))
                        Text(
                            state.recurrenceRule?.let { describeRule(it) } ?: "不重复",
                            fontSize = 12.sp,
                            color = Color(mona.textSecondary),
                        )
                    }
                }
            }
            // 子任务（仅已有任务支持）
            if (state.id > 0) {
                item {
                    MonaCard {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            val doneCount = state.subTasks.count { it.status == 1 }
                            Text(
                                "子任务 ${doneCount}/${state.subTasks.size}",
                                fontSize = 14.sp,
                                color = Color(mona.textPrimary),
                            )
                            state.subTasks.forEach { sub ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(
                                        checked = sub.status == 1,
                                        onCheckedChange = { viewModel.toggleSubTask(sub) },
                                    )
                                    Text(
                                        sub.title,
                                        Modifier.weight(1f),
                                        fontSize = 13.sp,
                                        color = Color(mona.textPrimary),
                                    )
                                    IconButton(onClick = { viewModel.removeSubTask(sub) }) {
                                        Icon(Icons.Outlined.Close, "移除", modifier = Modifier.height(14.dp))
                                    }
                                }
                            }
                            SubTaskInput(onAdd = { viewModel.addSubTask(it) })
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(40.dp)) }
        }
    }

    // 日期选择
    dateTarget?.let { target ->
        val currentDate = if (target == "due") state.dueDate else state.reminderDate
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = currentDate.atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { dateTarget = null },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { millis ->
                        val picked = Instant.ofEpochMilli(millis).atZone(ZoneId.of("UTC")).toLocalDate()
                        viewModel.update {
                            if (target == "due") it.copy(dueDate = picked) else it.copy(reminderDate = picked)
                        }
                    }
                    dateTarget = null
                }) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = { dateTarget = null }) { Text("取消") } },
        ) {
            DatePicker(state = pickerState)
        }
    }

    // 时间选择
    timeTarget?.let { target ->
        val currentTime = if (target == "due") state.dueTime else state.reminderTime
        val timeState = rememberTimePickerState(initialHour = currentTime.hour, initialMinute = currentTime.minute)
        AlertDialog(
            onDismissRequest = { timeTarget = null },
            title = { Text("选择时间") },
            text = { TimePicker(state = timeState) },
            confirmButton = {
                TextButton(onClick = {
                    val picked = LocalTime.of(timeState.hour, timeState.minute)
                    viewModel.update {
                        if (target == "due") it.copy(dueTime = picked) else it.copy(reminderTime = picked)
                    }
                    timeTarget = null
                }) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = { timeTarget = null }) { Text("取消") } },
        )
    }

    if (showRecurrence) {
        RecurrenceDialog(
            initial = state.recurrenceRule,
            onConfirm = { rule ->
                viewModel.update { it.copy(recurrenceRule = rule) }
                showRecurrence = false
            },
            onDismiss = { showRecurrence = false },
        )
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = "删除任务",
            text = "任务及其子任务将被删除，无法恢复。",
            onConfirm = { confirmDelete = false; viewModel.delete(onBack) },
            onDismiss = { confirmDelete = false },
        )
    }
}

@Composable
private fun SubTaskInput(onAdd: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            placeholder = { Text("添加子任务", fontSize = 13.sp) },
            modifier = Modifier.weight(1f),
            singleLine = true,
        )
        TextButton(
            onClick = {
                if (text.isNotBlank()) {
                    onAdd(text)
                    text = ""
                }
            },
        ) { Text("添加") }
    }
}

fun describeRule(rule: String?): String = com.monaflow.app.core.common.describeRule(rule)

/** 重复规则选择：不重复 / 每天 / 每周指定星期 / 每月 / 每年。 */
@Composable
private fun RecurrenceDialog(
    initial: String?,
    onConfirm: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var mode by remember { mutableStateOf(initial?.let { RecurrenceRule.parse(it)?.freq }) }
    var interval by remember {
        mutableStateOf(initial?.let { RecurrenceRule.parse(it)?.interval } ?: 1)
    }
    var selectedDays by remember {
        mutableStateOf(
            initial?.let { RecurrenceRule.parse(it)?.byDays } ?: setOf(java.time.DayOfWeek.MONDAY),
        )
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("重复") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(
                        null to "不重复",
                        RecurrenceRule.Freq.DAILY to "每天",
                        RecurrenceRule.Freq.WEEKLY to "每周",
                        RecurrenceRule.Freq.MONTHLY to "每月",
                        RecurrenceRule.Freq.YEARLY to "每年",
                    ).forEach { (f, label) ->
                        FilterChip(
                            selected = mode == f,
                            onClick = { mode = f },
                            label = { Text(label, fontSize = 12.sp) },
                        )
                    }
                }
                if (mode == RecurrenceRule.Freq.WEEKLY) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        WEEKDAYS.forEachIndexed { i, d ->
                            FilterChip(
                                selected = d in selectedDays,
                                onClick = {
                                    selectedDays =
                                        if (d in selectedDays) selectedDays - d else selectedDays + d
                                },
                                label = { Text(WEEKDAY_LABELS[i], fontSize = 12.sp) },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val rule = when (mode) {
                    null -> null
                    RecurrenceRule.Freq.DAILY -> RecurrenceRule.daily(interval).encode()
                    RecurrenceRule.Freq.WEEKLY ->
                        if (selectedDays.isEmpty()) null
                        else RecurrenceRule.weekly(selectedDays, interval).encode()
                    RecurrenceRule.Freq.MONTHLY -> RecurrenceRule.monthly(interval).encode()
                    RecurrenceRule.Freq.YEARLY -> RecurrenceRule.yearly(interval).encode()
                }
                onConfirm(rule)
            }) { Text("确定") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
