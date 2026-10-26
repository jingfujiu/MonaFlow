package com.monaflow.app.feature.tasks

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text as M3Text
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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.monaflow.app.core.common.MonaDate
import com.monaflow.app.core.theme.LocalMonaColors
import com.monaflow.app.core.ui.EmptyState
import com.monaflow.app.core.ui.MonaCard
import com.monaflow.app.data.local.entity.TaskEntity

internal val PRIORITY_LABELS = listOf("无", "低", "中", "高")
private val PRIORITY_COLORS = listOf(0xFF9AA3AA, 0xFF8FA995, 0xFFD9A441, 0xFFB3564E)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskListScreen(
    viewModel: TaskListViewModel,
    onOpenTask: (Long) -> Unit,
    onNewTask: () -> Unit,
) {
    val tasks by viewModel.tasks.collectAsStateWithLifecycle()
    val view by viewModel.view.collectAsStateWithLifecycle()
    val mona = LocalMonaColors.current

    Scaffold(
        topBar = { TopAppBar(title = { Text("待办") }) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onNewTask,
                containerColor = Color(mona.accent),
                contentColor = Color(mona.onPrimary),
            ) {
                Icon(Icons.Outlined.Add, contentDescription = "新建任务")
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = view.ordinal) {
                TaskView.entries.forEach { v ->
                    Tab(
                        selected = view == v,
                        onClick = { viewModel.setView(v) },
                        text = { M3Text(v.label, fontSize = 13.sp) },
                    )
                }
            }
            if (tasks.isEmpty()) {
                Box(Modifier.fillMaxSize()) {
                    EmptyState(
                        icon = "✅",
                        title = emptyTitle(view),
                        hint = emptyHint(view),
                        actionText = "新建任务",
                        onAction = onNewTask,
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(tasks, key = { it.id }) { task ->
                        TaskCard(
                            task = task,
                            isOverdue = viewModel.isOverdue(task),
                            onToggle = {
                                if (task.status == 0) viewModel.complete(task) else viewModel.uncomplete(task)
                            },
                            onOpen = { onOpenTask(task.id) },
                            onDelete = { viewModel.delete(task) },
                        )
                    }
                    item { Spacer(Modifier.height(72.dp)) }
                }
            }
        }
    }
}

private fun emptyTitle(v: TaskView) = when (v) {
    TaskView.INBOX -> "收件箱是空的"
    TaskView.TODAY -> "今天没有到期任务"
    TaskView.UPCOMING -> "没有即将到期的任务"
    TaskView.DONE -> "还没有完成的任务"
    TaskView.ALL -> "还没有任务"
}

private fun emptyHint(v: TaskView) = when (v) {
    TaskView.INBOX -> "没有日期的任务都在这里"
    TaskView.TODAY -> "今天想完成什么？"
    TaskView.UPCOMING -> "未来几天的安排会出现在这里"
    TaskView.DONE -> "完成任务后会记录在这里"
    TaskView.ALL -> "记录待办，让生活井井有条"
}

@Composable
private fun TaskCard(
    task: TaskEntity,
    isOverdue: Boolean,
    onToggle: () -> Unit,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
) {
    val mona = LocalMonaColors.current
    var confirmDelete by remember { mutableStateOf(false) }
    val done = task.status == 1
    MonaCard(onClick = onOpen) {
        Row(
            Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(
                checked = done,
                onCheckedChange = { onToggle() },
                colors = CheckboxDefaults.colors(checkedColor = Color(mona.primary)),
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (task.priority > 0) {
                        Box(
                            Modifier
                                .height(8.dp)
                                .width(3.dp)
                                .background(
                                    Color(PRIORITY_COLORS[task.priority.coerceIn(0, 3)]),
                                    androidx.compose.foundation.shape.RoundedCornerShape(2.dp),
                                ),
                        )
                    }
                    Text(
                        task.title,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(if (done) mona.textSecondary else mona.textPrimary),
                        textDecoration = if (done) TextDecoration.LineThrough else null,
                        maxLines = 2,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    task.dueAt?.let { due ->
                        val label = MonaDate.relativeLabel(
                            java.time.Instant.ofEpochMilli(due).atZone(java.time.ZoneId.systemDefault()).toLocalDate(),
                        )
                        Text(
                            label + " " + MonaDate.formatMillisTime(due),
                            fontSize = 11.sp,
                            color = Color(if (isOverdue) 0xFFB3564E else mona.textSecondary),
                        )
                    }
                    if (task.recurrenceRule != null) {
                        Icon(Icons.Outlined.Repeat, contentDescription = "重复", tint = Color(mona.textSecondary), modifier = Modifier.height(12.dp))
                    }
                    if (task.priority > 0) {
                        Text(
                            PRIORITY_LABELS[task.priority.coerceIn(0, 3)] + "优先",
                            fontSize = 11.sp,
                            color = Color(PRIORITY_COLORS[task.priority.coerceIn(0, 3)]),
                        )
                    }
                }
            }
            IconButton(onClick = { confirmDelete = true }) {
                Icon(Icons.Outlined.Delete, "删除", tint = Color(mona.textSecondary), modifier = Modifier.height(18.dp))
            }
        }
    }
    if (confirmDelete) {
        com.monaflow.app.core.ui.ConfirmDialog(
            title = "删除任务",
            text = "“${task.title}”将被删除（含子任务），无法恢复。",
            onConfirm = { confirmDelete = false; onDelete() },
            onDismiss = { confirmDelete = false },
        )
    }
}
