package com.monaflow.app.feature.habits

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
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
import java.time.LocalTime

private val ICON_CHOICES = listOf("🌿", "💧", "🏃", "📖", "🧘", "💪", "🛏️", "🥗", "✍️", "🎹", "🧹", "🌞")
private val COLOR_CHOICES = listOf(
    0xFF8FA995L, 0xFFA9C7D8L, 0xFFB7CBB3L, 0xFFD9CBE8L, 0xFFEBD1D5L, 0xFFE8DCCBL, 0xFF6E9CB5L, 0xFFA08258L,
)
private val WEEKDAY_LABELS = listOf("一", "二", "三", "四", "五", "六", "日")
private val WEEKDAYS = listOf(
    java.time.DayOfWeek.MONDAY, java.time.DayOfWeek.TUESDAY, java.time.DayOfWeek.WEDNESDAY,
    java.time.DayOfWeek.THURSDAY, java.time.DayOfWeek.FRIDAY, java.time.DayOfWeek.SATURDAY,
    java.time.DayOfWeek.SUNDAY,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HabitEditScreen(
    viewModel: HabitEditViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val mona = LocalMonaColors.current
    var confirmDelete by remember { mutableStateOf(false) }
    var showReminderPicker by remember { mutableStateOf(false) }

    if (!state.loaded) return

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (state.id == 0L) "新建习惯" else "编辑习惯") },
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
                    value = state.name,
                    onValueChange = { v -> viewModel.update { it.copy(name = v) } },
                    label = { Text("习惯名称（如：每天读书）") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
            }
            // 图标
            item {
                MonaCard {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("图标", fontSize = 14.sp, color = Color(mona.textPrimary))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(ICON_CHOICES) { icon ->
                                Box(
                                    Modifier
                                        .size(40.dp)
                                        .background(
                                            if (state.icon == icon) Color(state.color) else Color(mona.surfaceHigh),
                                            CircleShape,
                                        )
                                        .clickable { viewModel.update { it.copy(icon = icon) } },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(icon, fontSize = 18.sp)
                                }
                            }
                        }
                        Text("颜色", fontSize = 14.sp, color = Color(mona.textPrimary))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(COLOR_CHOICES, key = { it }) { c ->
                                Box(
                                    Modifier
                                        .size(32.dp)
                                        .background(
                                            Color(c),
                                            CircleShape,
                                        )
                                        .clickable { viewModel.update { it.copy(color = c) } },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (state.color == c) {
                                        Text("✓", color = Color(com.monaflow.app.core.theme.ColorUtils.readableTextColor(c.toInt())))
                                    }
                                }
                            }
                        }
                    }
                }
            }
            // 重复规则
            item {
                MonaCard {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("重复", fontSize = 14.sp, color = Color(mona.textPrimary))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(
                                RecurrenceRule.Freq.DAILY to "每天",
                                RecurrenceRule.Freq.WEEKLY to "每周指定",
                                RecurrenceRule.Freq.DAILY to "隔天",
                            ).forEach { (f, label) ->
                                val selected = when (label) {
                                    "隔天" -> state.freq == RecurrenceRule.Freq.DAILY && state.interval >= 2
                                    else -> state.freq == f && state.interval == 1
                                }
                                FilterChip(
                                    selected = selected,
                                    onClick = {
                                        viewModel.update {
                                            when (label) {
                                                "隔天" -> it.copy(freq = RecurrenceRule.Freq.DAILY, interval = 2)
                                                else -> it.copy(freq = f, interval = 1)
                                            }
                                        }
                                    },
                                    label = { Text(label, fontSize = 12.sp) },
                                )
                            }
                        }
                        if (state.freq == RecurrenceRule.Freq.WEEKLY) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                WEEKDAYS.forEachIndexed { i, d ->
                                    FilterChip(
                                        selected = d in state.byDays,
                                        onClick = {
                                            viewModel.update {
                                                val newDays =
                                                    if (d in it.byDays) it.byDays - d else it.byDays + d
                                                it.copy(byDays = newDays)
                                            }
                                        },
                                        label = { Text(WEEKDAY_LABELS[i], fontSize = 12.sp) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
            // 提醒
            item {
                MonaCard {
                    Row(
                        Modifier.padding(14.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            Text("提醒时间", fontSize = 14.sp, color = Color(mona.textPrimary))
                            Text(
                                if (state.reminderTime == null) "不提醒" else "每天 ${MonaDate.formatTime(state.reminderTime!!)} 提醒",
                                fontSize = 12.sp,
                                color = Color(mona.textSecondary),
                            )
                        }
                        Row {
                            TextButton(onClick = { showReminderPicker = true }) {
                                Text(if (state.reminderTime == null) "设置" else "修改", color = Color(mona.primary))
                            }
                            if (state.reminderTime != null) {
                                TextButton(onClick = { viewModel.update { it.copy(reminderTime = null) } }) {
                                    Text("清除")
                                }
                            }
                        }
                    }
                }
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
            item { Spacer(Modifier.height(40.dp)) }
        }
    }

    if (showReminderPicker) {
        val initial = state.reminderTime ?: LocalTime.of(9, 0)
        val timeState = rememberTimePickerState(initialHour = initial.hour, initialMinute = initial.minute)
        AlertDialog(
            onDismissRequest = { showReminderPicker = false },
            title = { Text("提醒时间") },
            text = { TimePicker(state = timeState) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.update { it.copy(reminderTime = LocalTime.of(timeState.hour, timeState.minute)) }
                    showReminderPicker = false
                }) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = { showReminderPicker = false }) { Text("取消") } },
        )
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = "删除习惯",
            text = "“${state.name}”及全部打卡记录将被删除，无法恢复。",
            onConfirm = { confirmDelete = false; viewModel.delete(onBack) },
            onDismiss = { confirmDelete = false },
        )
    }
}
