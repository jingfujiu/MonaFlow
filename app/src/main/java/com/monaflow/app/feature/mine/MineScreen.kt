package com.monaflow.app.feature.mine

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.monaflow.app.core.theme.LocalMonaColors
import com.monaflow.app.core.ui.MonaCard
import com.monaflow.app.data.repository.CountdownRepository
import com.monaflow.app.data.repository.HabitRepository
import com.monaflow.app.data.repository.ItemRepository
import com.monaflow.app.data.repository.NoteRepository
import com.monaflow.app.data.repository.TaskRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class MineStats(
    val noteCount: Int = 0,
    val taskCount: Int = 0,
    val habitCount: Int = 0,
    val itemCount: Int = 0,
    val countdownCount: Int = 0,
)

class MineViewModel(
    noteRepo: NoteRepository,
    taskRepo: TaskRepository,
    habitRepo: HabitRepository,
    itemRepo: ItemRepository,
    countdownRepo: CountdownRepository,
) : ViewModel() {

    val stats: StateFlow<MineStats> = combine(
        noteRepo.observeActive(),
        taskRepo.observeAll(),
        habitRepo.observeActive(),
        itemRepo.observeActive(),
        countdownRepo.observeAll(),
    ) { notes, tasks, habits, items, countdowns ->
        MineStats(
            noteCount = notes.size,
            taskCount = tasks.size,
            habitCount = habits.size,
            itemCount = items.size,
            countdownCount = countdowns.size,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MineStats())
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MineScreen(
    viewModel: MineViewModel,
    onOpenItems: () -> Unit,
    onOpenCountdowns: () -> Unit,
    onOpenTrash: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAi: () -> Unit,
) {
    val stats by viewModel.stats.collectAsStateWithLifecycle()
    val mona = LocalMonaColors.current

    Scaffold { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Column(Modifier.padding(top = 12.dp)) {
                    Text("我的", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color(mona.textPrimary))
                    Text(
                        "数据只存在这台手机上",
                        fontSize = 12.sp,
                        color = Color(mona.textSecondary),
                    )
                }
            }
            // 数据统计
            item {
                MonaCard {
                    Row(
                        Modifier.padding(16.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        StatCell("笔记", stats.noteCount)
                        StatCell("任务", stats.taskCount)
                        StatCell("习惯", stats.habitCount)
                        StatCell("物品", stats.itemCount)
                        StatCell("倒计时", stats.countdownCount)
                    }
                }
            }
            item {
                MineEntry("🎨", "莫奈主题与外观", "睡莲蓝、花园绿、晨雾紫、暖阳米", onOpenSettings)
            }
            item {
                MineEntry("🤖", "AI 智能整理", "自定义服务商，密钥本机加密", onOpenAi)
            }
            item {
                MineEntry("📦", "物品记录", "存放位置、价格与保修期", onOpenItems)
            }
            item {
                MineEntry("⏳", "倒计时", "生日、纪念日与重要截止", onOpenCountdowns)
            }
            item {
                MineEntry("🗑️", "回收站", "已删除的笔记可在此恢复", onOpenTrash)
            }
            item {
                MineEntry("⚙️", "设置", "提醒、备份、隐私与安全", onOpenSettings)
            }
        }
    }
}

@Composable
private fun StatCell(label: String, value: Int) {
    val mona = LocalMonaColors.current
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("$value", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(mona.primary))
        Text(label, fontSize = 11.sp, color = Color(mona.textSecondary))
    }
}

@Composable
private fun MineEntry(icon: String, title: String, subtitle: String, onClick: () -> Unit) {
    val mona = LocalMonaColors.current
    MonaCard(onClick = onClick) {
        Row(
            Modifier.padding(14.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(0.12f)
                    .padding(vertical = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(icon, fontSize = 20.sp)
            }
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = Color(mona.textPrimary))
                Text(subtitle, fontSize = 12.sp, color = Color(mona.textSecondary))
            }
            Text("›", fontSize = 18.sp, color = Color(mona.textSecondary))
        }
    }
}
