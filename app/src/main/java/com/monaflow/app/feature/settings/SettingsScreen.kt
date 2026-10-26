package com.monaflow.app.feature.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.monaflow.app.core.theme.LocalMonaColors
import com.monaflow.app.core.theme.MonaColors
import com.monaflow.app.core.theme.MonaPreset
import com.monaflow.app.core.ui.MonaCard
import com.monaflow.app.data.settings.AppSettings
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    onOpenTheme: () -> Unit,
    onOpenBackup: () -> Unit,
    onOpenAi: () -> Unit,
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val mona = LocalMonaColors.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "返回") }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // 外观
            item {
                MonaCard {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("外观", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color(mona.textPrimary))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(
                                AppSettings.THEME_SYSTEM to "跟随系统",
                                AppSettings.THEME_LIGHT to "浅色",
                                AppSettings.THEME_DARK to "深色",
                            ).forEach { (mode, label) ->
                                FilterChip(
                                    selected = settings.themeMode == mode,
                                    onClick = { viewModel.setThemeMode(mode) },
                                    label = { Text(label, fontSize = 12.sp) },
                                )
                            }
                        }
                        Row(
                            Modifier.fillMaxWidth().clickable(onClick = onOpenTheme),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column {
                                Text("莫奈主题", fontSize = 14.sp, color = Color(mona.textPrimary))
                                Text(
                                    when (settings.themeId) {
                                        MonaPreset.WATER_LILY -> "睡莲蓝"
                                        MonaPreset.GARDEN -> "花园绿"
                                        MonaPreset.MIST -> "晨雾紫"
                                        MonaPreset.SUNRISE -> "暖阳米"
                                        else -> "图片取色"
                                    },
                                    fontSize = 12.sp,
                                    color = Color(mona.textSecondary),
                                )
                            }
                            Text("›", fontSize = 18.sp, color = Color(mona.textSecondary))
                        }
                    }
                }
            }
            // AI
            item {
                MonaCard(onClick = onOpenAi) {
                    Row(
                        Modifier.padding(14.dp).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column {
                            Text("AI 服务", fontSize = 14.sp, color = Color(mona.textPrimary))
                            Text("自定义服务商 · 密钥本机加密存储", fontSize = 12.sp, color = Color(mona.textSecondary))
                        }
                        Text("›", fontSize = 18.sp, color = Color(mona.textSecondary))
                    }
                }
            }
            // 通知
            item {
                MonaCard {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("提醒", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color(mona.textPrimary))
                        ReminderToggle("待办提醒", settings.reminderTodo) { v ->
                            viewModel.setReminders(v, settings.reminderHabit, settings.reminderCountdown, settings.reminderWarranty)
                        }
                        ReminderToggle("习惯打卡提醒", settings.reminderHabit) { v ->
                            viewModel.setReminders(settings.reminderTodo, v, settings.reminderCountdown, settings.reminderWarranty)
                        }
                        ReminderToggle("倒计时提醒", settings.reminderCountdown) { v ->
                            viewModel.setReminders(settings.reminderTodo, settings.reminderHabit, v, settings.reminderWarranty)
                        }
                        ReminderToggle("保修到期提醒", settings.reminderWarranty) { v ->
                            viewModel.setReminders(settings.reminderTodo, settings.reminderHabit, settings.reminderCountdown, v)
                        }
                    }
                }
            }
            // 数据
            item {
                MonaCard(onClick = onOpenBackup) {
                    Row(
                        Modifier.padding(14.dp).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column {
                            Text("数据备份与恢复", fontSize = 14.sp, color = Color(mona.textPrimary))
                            Text("导出/导入 ZIP 备份，含附件与校验", fontSize = 12.sp, color = Color(mona.textSecondary))
                        }
                        Text("›", fontSize = 18.sp, color = Color(mona.textSecondary))
                    }
                }
            }
            // 首页自定义
            item {
                MonaCard {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("首页模块", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color(mona.textPrimary))
                        val order = settings.homeModulesOrder
                        val hidden = settings.homeModulesHidden
                        order.forEach { moduleId ->
                            val label = when (moduleId) {
                                AppSettings.MODULE_TASKS -> "今日任务"
                                AppSettings.MODULE_HABITS -> "习惯打卡"
                                AppSettings.MODULE_COUNTDOWNS -> "重要日子"
                                AppSettings.MODULE_NOTES -> "最近笔记"
                                AppSettings.MODULE_ITEMS -> "最近物品"
                                else -> moduleId
                            }
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(label, fontSize = 14.sp, color = Color(mona.textPrimary))
                                Switch(
                                    checked = moduleId !in hidden,
                                    onCheckedChange = { visible ->
                                        val newHidden = if (visible) hidden - moduleId else hidden + moduleId
                                        viewModel.setHomeModules(order, newHidden, settings.homeCompact)
                                    },
                                )
                            }
                        }
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("紧凑布局", fontSize = 14.sp, color = Color(mona.textPrimary))
                            Switch(
                                checked = settings.homeCompact,
                                onCheckedChange = { v ->
                                    viewModel.setHomeModules(settings.homeModulesOrder, settings.homeModulesHidden, v)
                                },
                            )
                        }
                    }
                }
            }
            // HyperOS 适配说明
            item {
                MonaCard {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("小米 HyperOS 使用说明", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color(mona.textPrimary))
                        Text(
                            "若提醒偶发不送达，多为系统后台限制所致，可按需排查：\n" +
                                "1. 系统设置 → 应用设置 → MonaFlow → 自启动\n" +
                                "2. 省电策略改为“无限制”\n" +
                                "3. Android 14+ 需授予“闹钟和提醒”权限（应用内触发时会引导）\n\n" +
                                "MonaFlow 不强制常驻后台、不轮询，提醒依赖系统闹钟，以上设置不改变也不会静默失败告警。",
                            fontSize = 12.sp,
                            color = Color(mona.textSecondary),
                            lineHeight = 17.sp,
                        )
                        TextButton(onClick = { viewModel.openAppDetailsSettings() }) {
                            Text("打开系统应用信息页")
                        }
                        if (!viewModel.canScheduleExactAlarms()) {
                            Text(
                                "未授予精确闹钟权限，提醒可能偏差几分钟",
                                fontSize = 12.sp,
                                color = Color(0xFFD9A441),
                            )
                            TextButton(onClick = { viewModel.requestExactAlarmPermission() }) {
                                Text("授权精确闹钟")
                            }
                        }
                    }
                }
            }
            // 隐私说明
            item {
                MonaCard {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("隐私与安全", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color(mona.textPrimary))
                        Text(
                            "· 全部数据仅存储在本机，无账号、无云端\n" +
                                "· API Key 使用 Android Keystore 加密后保存，明文不落库\n" +
                                "· AI 请求仅在你确认后发送相应内容\n" +
                                "· 备份文件不含 API Key",
                            fontSize = 12.sp,
                            color = Color(mona.textSecondary),
                            lineHeight = 18.sp,
                        )
                    }
                }
            }
            item {
                Text(
                    "MonaFlow v1.1.0 · 莫奈印象派个人效率中心",
                    fontSize = 11.sp,
                    color = Color(mona.textSecondary),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                )
            }
        }
    }
}

@Composable
private fun ReminderToggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val mona = LocalMonaColors.current
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, fontSize = 14.sp, color = Color(mona.textPrimary))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val mona = LocalMonaColors.current
    var previewSeed by remember { mutableStateOf<Int?>(null) }
    var extractFailed by remember { mutableStateOf(false) }

    val photoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) {
            viewModel.extractSeedFromImage(uri) { seed ->
                if (seed != null) {
                    previewSeed = seed
                    extractFailed = false
                } else {
                    extractFailed = true
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("莫奈主题") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "返回") }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val presets = listOf(
                Triple(MonaPreset.WATER_LILY, "睡莲蓝", listOf(0xFFA9C7D8, 0xFFDCE8F4, 0xFF6E9CB5)),
                Triple(MonaPreset.GARDEN, "花园绿", listOf(0xFF8FA995, 0xFFB7CBB3, 0xFF6E8A74)),
                Triple(MonaPreset.MIST, "晨雾紫", listOf(0xFFD9CBE8, 0xFF8A78A8, 0xFFF0EBF5)),
                Triple(MonaPreset.SUNRISE, "暖阳米", listOf(0xFFE8DCCB, 0xFFA08258, 0xFFF3ECDF)),
            )
            items(presets.size) { i ->
                val (id, name, colors) = presets[i]
                MonaCard(
                    background = if (settings.themeId == id) Color(mona.surfaceHigh) else Color(mona.surface),
                    onClick = { viewModel.setThemeId(id) },
                ) {
                    Row(
                        Modifier.padding(14.dp).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy((-8).dp)) {
                            colors.forEach { c ->
                                Box(
                                    Modifier
                                        .size(28.dp)
                                        .background(Color(c), CircleShape),
                                )
                            }
                        }
                        Text(name, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = Color(mona.textPrimary), modifier = Modifier.weight(1f))
                        if (settings.themeId == id) {
                            Text("使用中", fontSize = 12.sp, color = Color(mona.primary))
                        }
                    }
                }
            }
            item {
                MonaCard {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("图片取色主题", fontSize = 14.sp, color = Color(mona.textPrimary))
                        Text(
                            "从照片提取主色生成专属莫奈主题。图片仅在本机处理，不会上传。",
                            fontSize = 12.sp,
                            color = Color(mona.textSecondary),
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = {
                                photoPicker.launch(
                                    androidx.activity.result.PickVisualMediaRequest(
                                        ActivityResultContracts.PickVisualMedia.ImageOnly,
                                    ),
                                )
                            }) { Text("选择图片取色") }
                            if (settings.themeId == MonaPreset.CUSTOM) {
                                TextButton(onClick = { viewModel.applyCustomTheme(null) }) {
                                    Text("恢复默认主题")
                                }
                            }
                        }
                        if (settings.themeId == MonaPreset.CUSTOM) {
                            Text("当前使用图片取色主题", fontSize = 11.sp, color = Color(mona.primary))
                        }
                        if (extractFailed) {
                            Text("取色失败：图片无法读取，请换一张试试", fontSize = 12.sp, color = Color(0xFFB3564E))
                        }
                    }
                }
            }
        }
    }

    // 取色预览：实时预览 → 应用 / 取消 / 色相微调
    previewSeed?.let { seed ->
        var adjusted by remember(seed) { mutableStateOf(seed) }
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { previewSeed = null },
            title = { Text("预览取色主题") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    ThemePreviewStrip(MonaColors.fromSeed(adjusted, dark = false), label = "浅色")
                    ThemePreviewStrip(MonaColors.fromSeed(adjusted, dark = true), label = "深色")
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(0f to "原色", 30f to "偏暖", -30f to "偏冷", 120f to "偏绿", -120f to "偏紫").forEach { (d, label) ->
                            FilterChip(
                                selected = adjusted == viewModel.shiftSeedHue(seed, d) && d != 0f,
                                onClick = { adjusted = viewModel.shiftSeedHue(seed, d) },
                                label = { Text(label, fontSize = 11.sp) },
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.applyCustomTheme(adjusted)
                    previewSeed = null
                }) { Text("应用") }
            },
            dismissButton = {
                TextButton(onClick = { previewSeed = null }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun ThemePreviewStrip(colors: MonaColors, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, fontSize = 11.sp, color = Color(colors.textSecondary))
        listOf(
            colors.background to "背景",
            colors.surface to "卡片",
            colors.primary to "主色",
            colors.accent to "强调",
            colors.textPrimary to "文字",
        ).forEach { (c, _) ->
            Box(
                Modifier
                    .size(24.dp)
                    .background(Color(c), RoundedCornerShape(6.dp)),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
) {
    val backupUi by viewModel.backupUi.collectAsStateWithLifecycle()
    val mona = LocalMonaColors.current
    val snackbar = remember { SnackbarHostState() }
    val timeLabel = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.CHINA).format(Date())

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri ->
        if (uri != null) viewModel.exportTo(uri, "monaflow-backup-$timeLabel.zip")
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) viewModel.importPreview(uri)
    }

    LaunchedEffect(backupUi) {
        when (val ui = backupUi) {
            is SettingsViewModel.BackupUi.ExportDone ->
                snackbar.showSnackbar("导出完成：$ui.fileName（${ui.counts["notes"] ?: 0} 篇笔记等）")
            is SettingsViewModel.BackupUi.Done -> snackbar.showSnackbar(ui.message)
            is SettingsViewModel.BackupUi.Error -> snackbar.showSnackbar(ui.message)
            else -> {}
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("备份与恢复") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "返回") }
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
                MonaCard {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("导出备份", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color(mona.textPrimary))
                        Text(
                            "生成带校验的 ZIP 备份，包含全部笔记、标签、习惯、打卡、任务、物品、倒计时与附件。\n不含 API Key。",
                            fontSize = 12.sp,
                            color = Color(mona.textSecondary),
                            lineHeight = 17.sp,
                        )
                        TextButton(
                            onClick = { exportLauncher.launch("monaflow-backup-$timeLabel.zip") },
                            enabled = backupUi !is SettingsViewModel.BackupUi.Working,
                        ) { Text("选择位置并导出") }
                    }
                }
            }
            item {
                MonaCard {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("从备份恢复", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color(mona.textPrimary))
                        Text(
                            "恢复为覆盖模式：导入成功后，现有数据将被备份内容整体替换。\n" +
                                "导入前会先校验备份完整性；恢复过程在单个事务中完成，失败不会破坏现有数据。",
                            fontSize = 12.sp,
                            color = Color(mona.textSecondary),
                            lineHeight = 17.sp,
                        )
                        TextButton(
                            onClick = { importLauncher.launch(arrayOf("application/zip", "application/octet-stream")) },
                            enabled = backupUi !is SettingsViewModel.BackupUi.Working,
                        ) { Text("选择备份文件") }
                    }
                }
            }
            item {
                Text(
                    "提示：恢复前建议先导出一份当前数据作为快照。",
                    fontSize = 11.sp,
                    color = Color(mona.textSecondary),
                )
            }
        }
    }

    // 恢复预览确认
    (backupUi as? SettingsViewModel.BackupUi.ImportPreview)?.let { preview ->
        val m = preview.manifest
        val created = SimpleDateFormat("yyyy年M月d日 HH:mm", Locale.CHINA).format(Date(m.createdAt))
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { viewModel.dismissBackupUi() },
            title = { Text("确认覆盖恢复") },
            text = {
                Text(
                    "备份创建于：$created\n来源版本：${m.appVersion}（数据库 v${m.dbVersion}）\n" +
                        "包含：笔记 ${m.counts["notes"] ?: 0} · 标签 ${m.counts["tags"] ?: 0} · 习惯 ${m.counts["habits"] ?: 0} · " +
                        "打卡 ${m.counts["habitCheckIns"] ?: 0} · 任务 ${m.counts["tasks"] ?: 0} · 物品 ${m.counts["items"] ?: 0} · " +
                        "倒计时 ${m.counts["countdowns"] ?: 0}\n\n" +
                        "⚠️ 恢复将覆盖现有全部数据（合并恢复暂不支持）。",
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                )
            },
            confirmButton = {
                TextButton(onClick = { viewModel.confirmRestore() }) {
                    Text("覆盖恢复", color = Color(0xFFB3564E))
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissBackupUi() }) { Text("取消") }
            },
        )
    }
}
