package com.monaflow.app.core.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.lifecycle.createSavedStateHandle
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import com.monaflow.app.core.ui.CapsuleTab
import com.monaflow.app.core.ui.FloatingCapsuleNavigationBar
import com.monaflow.app.core.ui.navBarContentPadding
import kotlinx.coroutines.launch
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Notes
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.EditCalendar
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import com.monaflow.app.MonaFlowApp
import com.monaflow.app.core.theme.LocalMonaColors
import com.monaflow.app.feature.countdown.CountdownEditScreen
import com.monaflow.app.feature.countdown.CountdownListScreen
import com.monaflow.app.feature.countdown.CountdownListViewModel
import com.monaflow.app.feature.countdown.CountdownEditViewModel
import com.monaflow.app.feature.habits.HabitDetailScreen
import com.monaflow.app.feature.habits.HabitDetailViewModel
import com.monaflow.app.feature.habits.HabitEditScreen
import com.monaflow.app.feature.habits.HabitEditViewModel
import com.monaflow.app.feature.habits.HabitListScreen
import com.monaflow.app.feature.habits.HabitListViewModel
import com.monaflow.app.feature.items.ItemEditScreen
import com.monaflow.app.feature.items.ItemEditViewModel
import com.monaflow.app.feature.items.ItemListScreen
import com.monaflow.app.feature.items.ItemListViewModel
import com.monaflow.app.feature.mine.MineScreen
import com.monaflow.app.feature.mine.MineViewModel
import com.monaflow.app.feature.notes.NoteEditScreen
import com.monaflow.app.feature.notes.NoteEditViewModel
import com.monaflow.app.feature.notes.NoteListScreen
import com.monaflow.app.feature.notes.NoteListViewModel
import com.monaflow.app.feature.settings.BackupScreen
import com.monaflow.app.feature.settings.SettingsScreen
import com.monaflow.app.feature.settings.SettingsViewModel
import com.monaflow.app.feature.settings.ThemeScreen
import com.monaflow.app.feature.tasks.TaskEditScreen
import com.monaflow.app.feature.tasks.TaskEditViewModel
import com.monaflow.app.feature.tasks.TaskListScreen
import com.monaflow.app.feature.tasks.TaskListViewModel
import com.monaflow.app.feature.today.TodayScreen
import com.monaflow.app.feature.today.TodayViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

object Routes {
    const val TODAY = "today"
    const val NOTES = "notes"
    const val TASKS = "tasks"
    const val HABITS = "habits"
    const val MINE = "mine"

    const val NOTE_EDIT = "note_edit?noteId={noteId}"
    const val TASK_EDIT = "task_edit?taskId={taskId}"
    const val HABIT_EDIT = "habit_edit?habitId={habitId}"
    const val HABIT_DETAIL = "habit_detail/{habitId}"
    const val ITEMS = "items"
    const val ITEM_EDIT = "item_edit?itemId={itemId}"
    const val COUNTDOWNS = "countdowns"
    const val COUNTDOWN_EDIT = "countdown_edit?cid={cid}"
    const val TRASH = "trash"
    const val SETTINGS = "settings"
    const val THEME = "theme"
    const val BACKUP = "backup"
    const val AI = "ai"
    const val AI_SETTINGS = "ai_settings"
}

private val bottomTabs = listOf(
    CapsuleTab(Routes.TODAY, "今日", Icons.Outlined.Home),
    CapsuleTab(Routes.NOTES, "笔记", Icons.AutoMirrored.Outlined.Notes),
    CapsuleTab(Routes.TASKS, "待办", Icons.Outlined.CheckCircle),
    CapsuleTab(Routes.HABITS, "打卡", Icons.Outlined.EditCalendar),
    CapsuleTab(Routes.MINE, "我的", Icons.Outlined.Person),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonaNavHost(startDestination: String = Routes.TODAY) {
    val navController = rememberNavController()
    val app = LocalContext.current.applicationContext as MonaFlowApp
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val showBottomBar = currentRoute in bottomTabs.map { it.route }

    // 顶部 insets 不在此处处理：外层 Scaffold 默认会先加一次状态栏高度，
    // 内层 TopAppBar 又自带一次，叠加导致标题整体偏低。
    // 现统一由各页面自身的 TopAppBar / statusBarsPadding 单次负责。
    // 底部：内容区整体为悬浮胶囊导航让位（含手势安全区），保证最后一条内容/输入框不被遮挡。
    val bottomReserve = navBarContentPadding()

    Box(Modifier.fillMaxSize()) {
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = bottomReserve),
        ) {
            composable(Routes.TODAY) {
                val vm: TodayViewModel = viewModel {
                    TodayViewModel(
                        app.taskRepository,
                        app.habitRepository,
                        app.noteRepository,
                        app.countdownRepository,
                        app.itemRepository,
                        app.settingsRepository,
                    )
                }
                TodayScreen(
                    viewModel = vm,
                    onOpenNote = { navController.navigate("note_edit?noteId=$it") },
                    onNewNote = { navController.navigate("note_edit?noteId=0") },
                    onNewTask = { navController.navigate("task_edit?taskId=0") },
                    onNewHabit = { navController.navigate("habit_edit?habitId=0") },
                    onNewItem = { navController.navigate("item_edit?itemId=0") },
                    onNewCountdown = { navController.navigate("countdown_edit?cid=0") },
                    onOpenTask = { navController.navigate("task_edit?taskId=$it") },
                    onOpenHabit = { navController.navigate("habit_detail/$it") },
                    onOpenCountdowns = { navController.navigate(Routes.COUNTDOWNS) },
                    onOpenItems = { navController.navigate(Routes.ITEMS) },
                    onOpenAi = { navController.navigate(Routes.AI) },
                    onOpenTasks = { navController.navigate(Routes.TASKS) { launchSingleTop = true } },
                )
            }
            composable(Routes.NOTES) {
                val vm: NoteListViewModel = viewModel { NoteListViewModel(app.noteRepository) }
                NoteListScreen(
                    viewModel = vm,
                    onOpenNote = { navController.navigate("note_edit?noteId=$it") },
                    onNewNote = { navController.navigate("note_edit?noteId=0") },
                    onOpenTrash = { navController.navigate(Routes.TRASH) },
                )
            }
            composable(Routes.TASKS) {
                val vm: TaskListViewModel = viewModel { TaskListViewModel(app.taskRepository) }
                TaskListScreen(
                    viewModel = vm,
                    onOpenTask = { navController.navigate("task_edit?taskId=$it") },
                    onNewTask = { navController.navigate("task_edit?taskId=0") },
                )
            }
            composable(Routes.HABITS) {
                val vm: HabitListViewModel = viewModel { HabitListViewModel(app.habitRepository) }
                HabitListScreen(
                    viewModel = vm,
                    onBack = { navController.popBackStack() },
                    onNew = { navController.navigate("habit_edit?habitId=0") },
                    onOpen = { navController.navigate("habit_detail/$it") },
                )
            }
            composable(Routes.MINE) {
                val vm: MineViewModel = viewModel {
                    MineViewModel(
                        app.noteRepository,
                        app.taskRepository,
                        app.habitRepository,
                        app.itemRepository,
                        app.countdownRepository,
                    )
                }
                MineScreen(
                    viewModel = vm,
                    onOpenItems = { navController.navigate(Routes.ITEMS) },
                    onOpenCountdowns = { navController.navigate(Routes.COUNTDOWNS) },
                    onOpenTrash = { navController.navigate(Routes.TRASH) },
                    onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                    onOpenAi = { navController.navigate(Routes.AI) },
                )
            }

            // ---------- 二级页面 ----------
            composable(
                Routes.NOTE_EDIT,
                arguments = listOf(navArgument("noteId") { type = NavType.LongType; defaultValue = 0L }),
            ) {
                val vm: NoteEditViewModel = viewModel {
                    NoteEditViewModel(app, createSavedStateHandle())
                }
                val scope = androidx.compose.runtime.rememberCoroutineScope()
                NoteEditScreen(
                    viewModel = vm,
                    onBack = { navController.popBackStack() },
                    onConvertToTask = { noteId ->
                        scope.launch {
                            val taskId = app.noteRepository.convertToTask(noteId)
                            navController.popBackStack()
                            navController.navigate("task_edit?taskId=$taskId")
                        }
                    },
                )
            }
            composable(
                Routes.TASK_EDIT,
                arguments = listOf(navArgument("taskId") { type = NavType.LongType; defaultValue = 0L }),
            ) {
                val vm: TaskEditViewModel = viewModel { TaskEditViewModel(app.taskRepository, createSavedStateHandle()) }
                TaskEditScreen(viewModel = vm, onBack = { navController.popBackStack() })
            }
            composable(
                Routes.HABIT_EDIT,
                arguments = listOf(navArgument("habitId") { type = NavType.LongType; defaultValue = 0L }),
            ) {
                val vm: HabitEditViewModel = viewModel { HabitEditViewModel(app.habitRepository, createSavedStateHandle()) }
                HabitEditScreen(viewModel = vm, onBack = { navController.popBackStack() })
            }
            composable(
                Routes.HABIT_DETAIL,
                arguments = listOf(navArgument("habitId") { type = NavType.LongType }),
            ) {
                val vm: HabitDetailViewModel = viewModel { HabitDetailViewModel(app.habitRepository, createSavedStateHandle()) }
                HabitDetailScreen(
                    viewModel = vm,
                    onBack = { navController.popBackStack() },
                    onEdit = { navController.navigate("habit_edit?habitId=$it") },
                )
            }
            composable(Routes.ITEMS) {
                val vm: ItemListViewModel = viewModel { ItemListViewModel(app.itemRepository) }
                ItemListScreen(
                    viewModel = vm,
                    onBack = { navController.popBackStack() },
                    onNew = { navController.navigate("item_edit?itemId=0") },
                    onEdit = { navController.navigate("item_edit?itemId=$it") },
                )
            }
            composable(
                Routes.ITEM_EDIT,
                arguments = listOf(navArgument("itemId") { type = NavType.LongType; defaultValue = 0L }),
            ) {
                val vm: ItemEditViewModel = viewModel { ItemEditViewModel(app, app.itemRepository, createSavedStateHandle()) }
                ItemEditScreen(viewModel = vm, onBack = { navController.popBackStack() })
            }
            composable(Routes.COUNTDOWNS) {
                val vm: CountdownListViewModel = viewModel { CountdownListViewModel(app.countdownRepository) }
                CountdownListScreen(
                    viewModel = vm,
                    onBack = { navController.popBackStack() },
                    onNew = { navController.navigate("countdown_edit?cid=0") },
                    onEdit = { navController.navigate("countdown_edit?cid=$it") },
                )
            }
            composable(
                Routes.COUNTDOWN_EDIT,
                arguments = listOf(navArgument("cid") { type = NavType.LongType; defaultValue = 0L }),
            ) {
                val vm: CountdownEditViewModel = viewModel { CountdownEditViewModel(app.countdownRepository, createSavedStateHandle()) }

                val dateLauncherState = androidx.compose.runtime.remember {
                    androidx.compose.runtime.mutableStateOf<((LocalDate) -> Unit)?>(null)
                }
                val timeLauncherState = androidx.compose.runtime.remember {
                    androidx.compose.runtime.mutableStateOf<((LocalTime) -> Unit)?>(null)
                }

                CountdownEditScreen(
                    viewModel = vm,
                    onBack = { navController.popBackStack() },
                    pickDate = { current, onPicked -> dateLauncherState.value = onPicked },
                    pickTime = { current, onPicked -> timeLauncherState.value = onPicked },
                )

                dateLauncherState.value?.let { onPicked ->
                    val pickerState = rememberDatePickerState(
                        initialSelectedDateMillis = LocalDate.now().atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli(),
                    )
                    DatePickerDialog(
                        onDismissRequest = { dateLauncherState.value = null },
                        confirmButton = {
                            TextButton(onClick = {
                                pickerState.selectedDateMillis?.let {
                                    onPicked(Instant.ofEpochMilli(it).atZone(ZoneId.of("UTC")).toLocalDate())
                                }
                                dateLauncherState.value = null
                            }) { Text("确定") }
                        },
                        dismissButton = { TextButton(onClick = { dateLauncherState.value = null }) { Text("取消") } },
                    ) {
                        DatePicker(state = pickerState)
                    }
                }

                timeLauncherState.value?.let { onPicked ->
                    val timeState = rememberTimePickerState(initialHour = 9, initialMinute = 0)
                    AlertDialog(
                        onDismissRequest = { timeLauncherState.value = null },
                        title = { Text("选择时间") },
                        text = { TimePicker(state = timeState) },
                        confirmButton = {
                            TextButton(onClick = {
                                onPicked(LocalTime.of(timeState.hour, timeState.minute))
                                timeLauncherState.value = null
                            }) { Text("确定") }
                        },
                        dismissButton = { TextButton(onClick = { timeLauncherState.value = null }) { Text("取消") } },
                    )
                }
            }
            composable(Routes.TRASH) {
                val vm: NoteListViewModel = viewModel { NoteListViewModel(app.noteRepository) }
                com.monaflow.app.feature.notes.NoteTrashScreen(
                    viewModel = vm,
                    onBack = { navController.popBackStack() },
                )
            }
            composable(Routes.SETTINGS) {
                val vm: SettingsViewModel = viewModel { SettingsViewModel(app) }
                SettingsScreen(
                    viewModel = vm,
                    onBack = { navController.popBackStack() },
                    onOpenTheme = { navController.navigate(Routes.THEME) },
                    onOpenBackup = { navController.navigate(Routes.BACKUP) },
                    onOpenAi = { navController.navigate(Routes.AI) },
                )
            }
            composable(Routes.THEME) {
                val vm: SettingsViewModel = viewModel { SettingsViewModel(app) }
                ThemeScreen(viewModel = vm, onBack = { navController.popBackStack() })
            }
            composable(Routes.BACKUP) {
                val vm: SettingsViewModel = viewModel { SettingsViewModel(app) }
                BackupScreen(viewModel = vm, onBack = { navController.popBackStack() })
            }
            composable(Routes.AI) {
                val vm: com.monaflow.app.feature.ai.AiAssistantViewModel = viewModel {
                    com.monaflow.app.feature.ai.AiAssistantViewModel(app)
                }
                com.monaflow.app.feature.ai.AiAssistantScreen(
                    viewModel = vm,
                    onBack = { navController.popBackStack() },
                    onOpenSettings = { navController.navigate(Routes.AI_SETTINGS) },
                )
            }
            composable(Routes.AI_SETTINGS) {
                val vm: com.monaflow.app.feature.ai.AiSettingsViewModel = viewModel {
                    com.monaflow.app.feature.ai.AiSettingsViewModel(app)
                }
                com.monaflow.app.feature.ai.AiSettingsScreen(
                    viewModel = vm,
                    onBack = { navController.popBackStack() },
                )
            }
        }

        // 悬浮胶囊导航栏：独立悬浮在内容上方，仅五个主入口页面显示
        if (showBottomBar) {
            FloatingCapsuleNavigationBar(
                tabs = bottomTabs,
                currentRoute = currentRoute,
                onSelect = { route ->
                    if (currentRoute != route) {
                        navController.navigate(route) {
                            popUpTo(Routes.TODAY) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}
