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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.monaflow.app.core.common.MonaDate
import com.monaflow.app.core.common.describeRule
import com.monaflow.app.core.theme.LocalMonaColors
import com.monaflow.app.core.ui.EmptyState
import com.monaflow.app.core.ui.MonaCard
import com.monaflow.app.data.repository.HabitDayState
import com.monaflow.app.data.repository.HabitRepository
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HabitListScreen(
    viewModel: HabitListViewModel,
    onBack: () -> Unit,
    onNew: () -> Unit,
    onOpen: (Long) -> Unit,
) {
    val habits by viewModel.habits.collectAsStateWithLifecycle()
    val mona = LocalMonaColors.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("习惯打卡") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "返回") }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onNew,
                containerColor = Color(mona.accent),
                contentColor = Color(mona.onPrimary),
            ) {
                Icon(Icons.Outlined.Add, contentDescription = "新建习惯")
            }
        },
    ) { padding ->
        if (habits.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding)) {
                EmptyState(
                    icon = "🌱",
                    title = "还没有习惯",
                    hint = "每天一点点，像睡莲一样慢慢生长",
                    actionText = "建立第一个习惯",
                    onAction = onNew,
                )
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(habits, key = { it.habit.id }) { card ->
                    val h = card.habit
                    MonaCard(onClick = { onOpen(h.id) }) {
                        Row(
                            Modifier.padding(14.dp).fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Box(
                                Modifier
                                    .size(44.dp)
                                    .background(Color(h.color), CircleShape),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(h.icon, fontSize = 22.sp)
                            }
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(
                                        h.name,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(mona.textPrimary),
                                    )
                                    if (h.isPaused) {
                                        Text("已暂停", fontSize = 10.sp, color = Color(mona.textSecondary))
                                    }
                                }
                                Text(
                                    describeRule(h.scheduleRule),
                                    fontSize = 12.sp,
                                    color = Color(mona.textSecondary),
                                )
                            }
                            if (h.isPaused) {
                                TextButton(onClick = { viewModel.toggleToday(h) }, enabled = false) {
                                    Text("暂停中", fontSize = 12.sp)
                                }
                            } else {
                                Box(
                                    Modifier
                                        .size(44.dp)
                                        .background(
                                            if (card.doneToday) Color(h.color) else Color(mona.surfaceHigh),
                                            CircleShape,
                                        )
                                        .clickable { viewModel.toggleToday(h) },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        Icons.Outlined.Check,
                                        contentDescription = if (card.doneToday) "取消打卡" else "打卡",
                                        tint = if (card.doneToday) ColorUtilsTextColor(h.color) else Color(mona.textSecondary),
                                    )
                                }
                            }
                        }
                    }
                }
                item { Spacer(Modifier.height(64.dp)) }
            }
        }
    }
}

private fun ColorUtilsTextColor(bg: Long): Color = Color(
    com.monaflow.app.core.theme.ColorUtils.readableTextColor(bg.toInt()),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HabitDetailScreen(
    viewModel: HabitDetailViewModel,
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val mona = LocalMonaColors.current
    var pendingDate by remember { mutableStateOf<LocalDate?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    var confirmArchive by remember { mutableStateOf(false) }

    val habit = state.habit ?: return
    val today = LocalDate.now()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(habit.name) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "返回") }
                },
                actions = {
                    TextButton(onClick = { onEdit(habit.id) }) { Text("编辑") }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // 统计
            item {
                MonaCard {
                    Row(
                        Modifier.padding(16.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        StatCell("连续", "${state.streak} 天")
                        StatCell("累计", "${state.totalCount} 次")
                        StatCell(
                            "完成率",
                            if (state.monthDueCount == 0) "—"
                            else "${state.monthDoneCount * 100 / state.monthDueCount}%",
                        )
                        StatCell("开始于", MonaDate.formatShort(LocalDate.ofEpochDay(habit.startDate)))
                    }
                }
            }
            // 月历
            item {
                MonaCard {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            IconButton(onClick = viewModel::prevMonth) { Text("‹", fontSize = 20.sp) }
                            Text(
                                "${state.month.year}年${state.month.monthValue}月",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(mona.textPrimary),
                            )
                            IconButton(onClick = viewModel::nextMonth) { Text("›", fontSize = 20.sp) }
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            listOf("一", "二", "三", "四", "五", "六", "日").forEach {
                                Text(it, fontSize = 11.sp, color = Color(mona.textSecondary), modifier = Modifier.weight(1f))
                            }
                        }
                        // 网格
                        val grid = MonaDate.monthGrid(state.month)
                        grid.chunked(7).forEach { week ->
                            Row(Modifier.fillMaxWidth()) {
                                week.forEach { day ->
                                    Box(Modifier.weight(1f).height(36.dp), contentAlignment = Alignment.Center) {
                                        if (day != null) {
                                            val dayState = state.monthData.firstOrNull { it.first == day }?.second
                                                ?: HabitDayState.IDLE
                                            val bg = when (dayState) {
                                                HabitDayState.DONE -> Color(habit.color)
                                                HabitDayState.SKIPPED -> Color(mona.outline)
                                                HabitDayState.MISSED -> Color(mona.surfaceHigh)
                                                HabitDayState.DUE -> Color(mona.surfaceHigh)
                                                HabitDayState.IDLE -> Color.Transparent
                                            }
                                            val contentColor =
                                                if (dayState == HabitDayState.DONE) {
                                                    Color(com.monaflow.app.core.theme.ColorUtils.readableTextColor(habit.color.toInt()))
                                                } else {
                                                    Color(mona.textPrimary)
                                                }
                                            Box(
                                                Modifier
                                                    .size(30.dp)
                                                    .background(bg, CircleShape)
                                                    .clickable {
                                                        if (dayState != HabitDayState.IDLE) pendingDate = day
                                                    },
                                                contentAlignment = Alignment.Center,
                                            ) {
                                                Text(
                                                    day.dayOfMonth.toString(),
                                                    fontSize = 12.sp,
                                                    color = contentColor,
                                                    fontWeight = if (day == today) FontWeight.Bold else FontWeight.Normal,
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        // 图例
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            LegendDot(Color(habit.color), "完成")
                            LegendDot(Color(mona.outline), "跳过")
                            LegendDot(Color(mona.surfaceHigh), "未完成/待完成")
                        }
                    }
                }
            }
            // 操作
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { viewModel.togglePause() }) {
                        Text(if (habit.isPaused) "恢复" else "暂停")
                    }
                    TextButton(onClick = { confirmArchive = true }) { Text("归档") }
                    TextButton(onClick = { confirmDelete = true }) {
                        Text("删除", color = Color(0xFFB3564E))
                    }
                }
            }
            item { Spacer(Modifier.height(32.dp)) }
        }
    }

    pendingDate?.let { date ->
        val isDone = state.checkIns.any { it.localDate == date.toEpochDay() && it.status == HabitRepository.STATUS_DONE }
        val isSkipped = state.checkIns.any { it.localDate == date.toEpochDay() && it.status == HabitRepository.STATUS_SKIPPED }
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { pendingDate = null },
            title = { Text(MonaDate.formatFull(date)) },
            text = { Text("打卡/跳过都会修改历史记录，请确认。") },
            confirmButton = {
                Row {
                    if (isDone || isSkipped) {
                        TextButton(onClick = {
                            viewModel.uncheckDate(date)
                            pendingDate = null
                        }) { Text("取消记录", color = Color(0xFFB3564E)) }
                    } else {
                        TextButton(onClick = {
                            viewModel.checkInDate(date)
                            pendingDate = null
                        }) { Text("打卡") }
                        TextButton(onClick = {
                            viewModel.skipDate(date)
                            pendingDate = null
                        }) { Text("跳过") }
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDate = null }) { Text("关闭") }
            },
        )
    }

    if (confirmArchive) {
        com.monaflow.app.core.ui.ConfirmDialog(
            title = "归档习惯",
            text = "归档后不再出现在打卡列表，可从归档中查看。",
            confirmText = "归档",
            destructive = false,
            onConfirm = { confirmArchive = false; viewModel.archive(); onBack() },
            onDismiss = { confirmArchive = false },
        )
    }
    if (confirmDelete) {
        com.monaflow.app.core.ui.ConfirmDialog(
            title = "删除习惯",
            text = "“${habit.name}”及全部打卡记录将被删除，无法恢复。",
            onConfirm = { confirmDelete = false; viewModel.delete(onBack) },
            onDismiss = { confirmDelete = false },
        )
    }
}

@Composable
private fun StatCell(label: String, value: String) {
    val mona = LocalMonaColors.current
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(mona.primary))
        Text(label, fontSize = 11.sp, color = Color(mona.textSecondary))
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    val mona = LocalMonaColors.current
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.size(10.dp).background(color, CircleShape))
        Text(label, fontSize = 10.sp, color = Color(mona.textSecondary))
    }
}
