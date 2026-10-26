package com.monaflow.app.feature.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Assignment
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.monaflow.app.core.common.MonaDate
import com.monaflow.app.core.theme.LocalMonaColors
import com.monaflow.app.core.ui.MonaCard
import com.monaflow.app.core.ui.SectionHeader
import com.monaflow.app.data.local.entity.TaskEntity
import com.monaflow.app.data.settings.AppSettings
import com.monaflow.app.feature.countdown.CountdownCard
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun TodayScreen(
    viewModel: TodayViewModel,
    onOpenNote: (Long) -> Unit,
    onNewNote: () -> Unit,
    onNewTask: () -> Unit,
    onNewHabit: () -> Unit,
    onNewItem: () -> Unit,
    onNewCountdown: () -> Unit,
    onOpenTask: (Long) -> Unit,
    onOpenHabit: (Long) -> Unit,
    onOpenCountdowns: () -> Unit,
    onOpenItems: () -> Unit,
    onOpenAi: () -> Unit,
    onOpenTasks: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val mona = LocalMonaColors.current
    var fabMenu by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().statusBarsPadding(), // 今日页无 TopAppBar，状态栏在此单次处理
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // 日期头（始终显示）
            item(key = "header") {
                Column {
                    Text(
                        text = "${state.today.monthValue}月${state.today.dayOfMonth}日 · " +
                            state.today.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.CHINESE),
                        fontSize = 13.sp,
                        color = Color(mona.textSecondary),
                    )
                    Text(
                        text = greeting() + "，今天也要慢慢来",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(mona.textPrimary),
                    )
                }
            }

            // 按用户自定义顺序渲染模块（隐藏的跳过）
            state.homeOrder.filter { it !in state.homeHidden }.forEach { moduleId ->
                when (moduleId) {
                    AppSettings.MODULE_TASKS -> {
                        item(key = "tasks_progress") {
                            TasksProgressCard(
                                done = state.doneTodayCount,
                                pending = state.todayTasks.size,
                                onOpenTasks = onOpenTasks,
                            )
                        }
                        if (state.todayTasks.isNotEmpty()) {
                            item(key = "tasks_list_header") { SectionHeader("今日待办") }
                            items(
                                state.todayTasks,
                                key = { "task_${it.id}" },
                            ) { task ->
                                TaskRow(
                                    task = task,
                                    compact = state.homeCompact,
                                    onToggle = {
                                        if (task.status == 1) viewModel.uncompleteTask(task) else viewModel.completeTask(task)
                                    },
                                )
                            }
                        }
                    }

                    AppSettings.MODULE_HABITS -> if (state.habits.isNotEmpty()) {
                        item(key = "habits_header") { SectionHeader("习惯打卡") }
                        item(key = "habits_row") {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                items(state.habits, key = { it.id }) { habit ->
                                    val done = viewModel.isHabitDoneToday(habit, state)
                                    MonaCard(
                                        modifier = Modifier.width(120.dp),
                                        background = if (done) Color(mona.primary) else Color(mona.surface),
                                        onClick = { viewModel.toggleHabit(habit) },
                                    ) {
                                        Column(
                                            Modifier.padding(12.dp).fillMaxWidth(),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.spacedBy(4.dp),
                                        ) {
                                            Text(habit.icon, fontSize = 24.sp)
                                            Text(
                                                habit.name,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = if (done) Color(mona.onPrimary) else Color(mona.textPrimary),
                                                maxLines = 1,
                                            )
                                            Text(
                                                if (done) "已完成" else "待打卡",
                                                fontSize = 11.sp,
                                                color = if (done) Color(mona.onPrimary).copy(alpha = 0.8f) else Color(mona.textSecondary),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    AppSettings.MODULE_COUNTDOWNS -> if (state.countdowns.isNotEmpty()) {
                        item(key = "cd_header") {
                            SectionHeader(
                                "重要日子",
                                trailing = { TextButton(onClick = onOpenCountdowns) { Text("全部", fontSize = 12.sp) } },
                            )
                        }
                        item(key = "cd_row") {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                items(state.countdowns.take(5), key = { "cd_${it.id}" }) { cd ->
                                    CountdownCard(countdown = cd, onClick = onOpenCountdowns, modifier = Modifier.width(260.dp), compact = true)
                                }
                            }
                        }
                    }

                    AppSettings.MODULE_NOTES -> if (state.recentNotes.isNotEmpty()) {
                        item(key = "notes_header") { SectionHeader("最近笔记") }
                        items(state.recentNotes, key = { "note_${it.id}" }) { note ->
                            MonaCard(onClick = { onOpenNote(note.id) }) {
                                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(
                                        note.title.ifBlank { "(无标题)" },
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = Color(mona.textPrimary),
                                        maxLines = 1,
                                    )
                                    if (!state.homeCompact) {
                                        Text(
                                            note.content.take(60),
                                            fontSize = 12.sp,
                                            color = Color(mona.textSecondary),
                                            maxLines = 2,
                                        )
                                    }
                                }
                            }
                        }
                    }

                    AppSettings.MODULE_ITEMS -> if (state.recentItems.isNotEmpty()) {
                        item(key = "items_header") {
                            SectionHeader(
                                "最近物品",
                                trailing = { TextButton(onClick = onOpenItems) { Text("全部", fontSize = 12.sp) } },
                            )
                        }
                        item(key = "items_row") {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                items(state.recentItems, key = { "item_${it.id}" }) { item ->
                                    MonaCard(Modifier.width(140.dp), onClick = onOpenItems) {
                                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Text(
                                                item.name,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = Color(mona.textPrimary),
                                                maxLines = 1,
                                            )
                                            if (!state.homeCompact && item.storageLocation.isNotBlank()) {
                                                Text(
                                                    "📍 ${item.storageLocation}",
                                                    fontSize = 11.sp,
                                                    color = Color(mona.textSecondary),
                                                    maxLines = 1,
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item(key = "bottom_space") { Spacer(Modifier.height(72.dp)) }
        }

        // AI 快捷入口
        ExtendedFloatingActionButton(
            onClick = onOpenAi,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 16.dp, bottom = 16.dp),
            containerColor = Color(mona.surfaceHigh),
            contentColor = Color(mona.textPrimary),
        ) {
            Icon(Icons.Outlined.Psychology, contentDescription = "AI 助手", modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("AI 整理", fontSize = 13.sp)
        }

        // 快速新建
        Box(Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 16.dp)) {
            FloatingActionButton(
                onClick = { fabMenu = true },
                containerColor = Color(mona.accent),
                contentColor = Color(mona.onPrimary),
            ) {
                Icon(Icons.Outlined.Add, contentDescription = "快速新建")
            }
            DropdownMenu(expanded = fabMenu, onDismissRequest = { fabMenu = false }) {
                DropdownMenuItem(
                    text = { Text("新建待办") },
                    leadingIcon = { Icon(Icons.Outlined.Assignment, null) },
                    onClick = { fabMenu = false; onNewTask() },
                )
                DropdownMenuItem(
                    text = { Text("新建笔记") },
                    leadingIcon = { Icon(Icons.Outlined.Event, null) },
                    onClick = { fabMenu = false; onNewNote() },
                )
                DropdownMenuItem(
                    text = { Text("新建倒计时") },
                    leadingIcon = { Icon(Icons.Outlined.Timer, null) },
                    onClick = { fabMenu = false; onNewCountdown() },
                )
                DropdownMenuItem(
                    text = { Text("新建物品") },
                    leadingIcon = { Icon(Icons.Outlined.Inventory2, null) },
                    onClick = { fabMenu = false; onNewItem() },
                )
                DropdownMenuItem(
                    text = { Text("新建习惯") },
                    leadingIcon = { Text("🌿") },
                    onClick = { fabMenu = false; onNewHabit() },
                )
            }
        }
    }
}

@Composable
private fun TasksProgressCard(done: Int, pending: Int, onOpenTasks: () -> Unit) {
    val mona = LocalMonaColors.current
    val total = done + pending
    MonaCard(Modifier.padding(top = 4.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("今日任务", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color(mona.textPrimary))
                TextButton(onClick = onOpenTasks) { Text("查看全部", fontSize = 12.sp) }
            }
            if (total == 0) {
                Text("今天没有安排任务，享受生活吧", fontSize = 13.sp, color = Color(mona.textSecondary))
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text("$done / $total", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color(mona.primary))
                    Text("已完成", fontSize = 12.sp, color = Color(mona.textSecondary))
                }
                LinearProgressIndicator(
                    progress = { if (total == 0) 0f else done.toFloat() / total },
                    modifier = Modifier.fillMaxWidth().height(6.dp),
                    color = Color(mona.primary),
                    trackColor = Color(mona.surfaceHigh),
                )
            }
        }
    }
}

@Composable
private fun TaskRow(task: TaskEntity, compact: Boolean, onToggle: () -> Unit) {
    val mona = LocalMonaColors.current
    val overdue = task.status == 0 && task.dueAt != null && task.dueAt < System.currentTimeMillis()
    MonaCard {
        Row(
            Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(
                checked = task.status == 1,
                onCheckedChange = { onToggle() },
                colors = CheckboxDefaults.colors(checkedColor = Color(mona.primary)),
            )
            Column(Modifier.weight(1f)) {
                Text(
                    task.title,
                    fontSize = 14.sp,
                    color = Color(mona.textPrimary),
                    textDecoration = if (task.status == 1) TextDecoration.LineThrough else null,
                    maxLines = 2,
                )
                if (!compact) {
                    task.dueAt?.let {
                        Text(
                            "截止 " + MonaDate.formatDateTime(it),
                            fontSize = 11.sp,
                            color = Color(if (overdue) 0xFFB3564E else mona.textSecondary),
                        )
                    }
                }
            }
        }
    }
}

private fun greeting(): String {
    val h = java.time.LocalTime.now().hour
    return when (h) {
        in 5..10 -> "早上好"
        in 11..13 -> "午安"
        in 14..17 -> "下午好"
        in 18..22 -> "晚上好"
        else -> "夜深了"
    }
}
