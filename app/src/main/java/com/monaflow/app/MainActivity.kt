package com.monaflow.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.monaflow.app.ui.components.FloatingCapsuleBar
import com.monaflow.app.ui.screens.today.TodayScreen
import com.monaflow.app.ui.screens.notes.NotesScreen
import com.monaflow.app.ui.screens.tasks.TasksScreen
import com.monaflow.app.ui.screens.settings.SettingsScreen
import com.monaflow.app.ui.theme.MonetCreamWhite

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val navController = rememberNavController()
            val navBackStackEntry by navController.currentBackStackEntryAsState()
            val currentRoute = navBackStackEntry?.destination?.route ?: "today"
            var showQuickAddDialog by remember { mutableStateOf(false) }

            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MonetCreamWhite
            ) {
                Scaffold(
                    bottomBar = {
                        FloatingCapsuleBar(
                            currentRoute = currentRoute,
                            onNavigate = { route ->
                                navController.navigate(route) {
                                    popUpTo("today") { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            onQuickAdd = { showQuickAddDialog = true }
                        )
                    },
                    containerColor = MonetCreamWhite
                ) { innerPadding ->
                    NavHost(
                        navController = navController,
                        startDestination = "today",
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        composable("today") { TodayScreen() }
                        composable("notes") { NotesScreen() }
                        composable("tasks") { TasksScreen() }
                        composable("settings") { SettingsScreen() }
                    }
                }

                if (showQuickAddDialog) {
                    AlertDialog(
                        onDismissRequest = { showQuickAddDialog = false },
                        confirmButton = {
                            TextButton(onClick = { showQuickAddDialog = false }) {
                                Text("关闭")
                            }
                        },
                        title = { Text("✨ 莫奈快捷创作") },
                        text = { Text("在此可快速新建笔记、记录待办、添加习惯或使用 AI 智能整理。") }
                    )
                }
            }
        }
    }
}
