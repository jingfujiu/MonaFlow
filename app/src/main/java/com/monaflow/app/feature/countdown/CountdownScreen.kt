package com.monaflow.app.feature.countdown

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import java.time.LocalDate
import java.time.LocalTime
import com.monaflow.app.core.theme.ColorUtils
import com.monaflow.app.core.theme.LocalMonaColors
import com.monaflow.app.core.ui.ConfirmDialog
import com.monaflow.app.core.ui.EmptyState
import com.monaflow.app.core.ui.MonaCard
import com.monaflow.app.data.local.entity.CountdownEntity

/** 倒计时卡片（今日页紧凑 + 完整两种形态）。width 由调用方给定（LazyRow 中 fillMaxWidth 无效）。 */
@Composable
fun CountdownCard(
    countdown: CountdownEntity,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val mona = LocalMonaColors.current
    val days = MonaDate.countdownDays(
        countdown.targetEpochMillis,
        countdown.timeZoneId,
        countdown.isRecurring,
        countdown.recurrenceRule,
    )
    val accent = Color(countdown.themeColor ?: mona.primary)
    val bigLabel = when {
        days.isToday -> "今天"
        days.isPast -> "已过 ${days.passedDays} 天"
        else -> "${days.daysLeft} 天"
    }
    MonaCard(modifier = modifier, onClick = onClick) {
        Row(
            Modifier.padding(14.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                Modifier
                    .width(4.dp)
                    .height(44.dp)
                    .background(accent, RoundedCornerShape(2.dp)),
            )
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        countdown.title,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(mona.textPrimary),
                    )
                    if (countdown.isRecurring) {
                        Icon(Icons.Outlined.Repeat, contentDescription = "每年重复", tint = Color(mona.textSecondary), modifier = Modifier.height(14.dp))
                    }
                }
                Text(
                    MonaDate.formatFull(days.date) + if (countdown.isRecurring) "（每年）" else "",
                    fontSize = 12.sp,
                    color = Color(mona.textSecondary),
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    bigLabel,
                    fontSize = if (days.isToday) 18.sp else 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (days.isToday) accent else Color(mona.textPrimary),
                )
                if (!days.isToday && !days.isPast) Text("剩余", fontSize = 11.sp, color = Color(mona.textSecondary))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CountdownListScreen(
    viewModel: CountdownListViewModel,
    onBack: () -> Unit,
    onNew: () -> Unit,
    onEdit: (Long) -> Unit,
) {
    val items by viewModel.countdowns.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("倒计时") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "返回") }
                },
            )
        },
        floatingActionButton = {
            val mona = LocalMonaColors.current
            FloatingActionButton(onClick = onNew, containerColor = Color(mona.accent), contentColor = Color(mona.onPrimary)) {
                Icon(Icons.Outlined.Add, contentDescription = "新建")
            }
        },
    ) { padding ->
        if (items.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding)) {
                EmptyState(
                    icon = "⏳",
                    title = "还没有倒计时",
                    hint = "记录生日、纪念日、考试或旅行出发日",
                    actionText = "新建倒计时",
                    onAction = onNew,
                )
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(items, key = { it.id }) { cd ->
                    CountdownCard(countdown = cd, onClick = { onEdit(cd.id) }, modifier = Modifier.fillMaxWidth())
                }
                item { Spacer(Modifier.height(64.dp)) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CountdownEditScreen(
    viewModel: CountdownEditViewModel,
    onBack: () -> Unit,
    pickDate: (current: LocalDate?, onPicked: (LocalDate) -> Unit) -> Unit,
    pickTime: (current: LocalTime?, onPicked: (LocalTime) -> Unit) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val mona = LocalMonaColors.current
    var confirmDelete by remember { mutableStateOf(false) }

    if (!state.loaded) return

    val colorOptions = remember {
        listOf(
            0xFFA9C7D8, 0xFFB7CBB3, 0xFFD9CBE8, 0xFFEBD1D5,
            0xFFE8DCCB, 0xFF8FA995, 0xFF6E9CB5, 0xFFA08258,
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (state.id == 0L) "新建倒计时" else "编辑倒计时") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "返回") }
                },
                actions = {
                    if (state.id > 0) {
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(Icons.Outlined.Delete, "删除", tint = Color(0xFFB3564E))
                        }
                    }
                    TextButton(onClick = { viewModel.save(onBack) }) { Text("保存") }
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
                    label = { Text("标题（如：妈妈的生日）") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
            }
            item {
                MonaCard {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("目标日期", fontSize = 14.sp, color = Color(mona.textPrimary))
                            TextButton(onClick = {
                                pickDate(state.date) { d -> viewModel.update { it.copy(date = d) } }
                            }) {
                                Text(MonaDate.formatFull(state.date), color = Color(mona.primary))
                            }
                        }
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("全天", fontSize = 14.sp, color = Color(mona.textPrimary))
                            Switch(
                                checked = state.isAllDay,
                                onCheckedChange = { v -> viewModel.update { it.copy(isAllDay = v) } },
                            )
                        }
                        if (!state.isAllDay) {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text("时刻", fontSize = 14.sp, color = Color(mona.textPrimary))
                                TextButton(onClick = {
                                    pickTime(state.time) { t -> viewModel.update { it.copy(time = t) } }
                                }) {
                                    Text(MonaDate.formatTime(state.time), color = Color(mona.primary))
                                }
                            }
                        }
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column {
                                Text("每年重复", fontSize = 14.sp, color = Color(mona.textPrimary))
                                Text("适合生日、纪念日", fontSize = 11.sp, color = Color(mona.textSecondary))
                            }
                            Switch(
                                checked = state.isRecurring,
                                onCheckedChange = { v -> viewModel.update { it.copy(isRecurring = v) } },
                            )
                        }
                    }
                }
            }
            item {
                MonaCard {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("主题色", fontSize = 14.sp, color = Color(mona.textPrimary))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            item {
                                Box(
                                    Modifier
                                        .width(48.dp).height(36.dp)
                                        .background(Color(mona.surfaceHigh), RoundedCornerShape(18.dp))
                                        .clickable { viewModel.update { it.copy(themeColor = null) } },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text("默认", fontSize = 10.sp, color = Color(mona.textPrimary))
                                }
                            }
                            items(colorOptions, key = { it }) { c ->
                                Box(
                                    Modifier
                                        .width(36.dp).height(36.dp)
                                        .background(
                                            Color(c),
                                            RoundedCornerShape(if (state.themeColor == c) 8.dp else 18.dp),
                                        )
                                        .clickable { viewModel.update { it.copy(themeColor = c) } },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (state.themeColor == c) {
                                        Text("✓", color = Color(ColorUtils.readableTextColor(c.toInt())))
                                    }
                                }
                            }
                        }
                        Text(
                            "预览：${viewModel.daysLeft(state).let { d -> if (d.isToday) "今天" else if (d.isPast) "已过 ${d.passedDays} 天" else "剩余 ${d.daysLeft} 天" }}",
                            fontSize = 13.sp,
                            color = Color(mona.textSecondary),
                        )
                    }
                }
            }
            item {
                OutlinedTextField(
                    value = state.description,
                    onValueChange = { v -> viewModel.update { it.copy(description = v) } },
                    label = { Text("备注") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                )
            }
            item {
                Text(
                    "时区：${state.timeZoneId}（新建时取设备时区，天数按当地日期计算，跨时区不会算错）",
                    fontSize = 11.sp,
                    color = Color(mona.textSecondary),
                )
            }
            item { Spacer(Modifier.height(48.dp)) }
        }
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = "删除倒计时",
            text = "“${state.title}”将被删除，此操作不可恢复。",
            onConfirm = { confirmDelete = false; viewModel.delete(onBack) },
            onDismiss = { confirmDelete = false },
        )
    }
}
