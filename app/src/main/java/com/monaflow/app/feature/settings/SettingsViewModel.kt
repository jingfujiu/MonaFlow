package com.monaflow.app.feature.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.monaflow.app.MonaFlowApp
import com.monaflow.app.data.backup.BackupManager
import com.monaflow.app.data.settings.AppSettings
import com.monaflow.app.data.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsViewModel(
    private val app: MonaFlowApp,
) : ViewModel() {

    private val settingsRepo = app.settingsRepository
    private val backupManager by lazy {
        BackupManager(app, app.database)
    }

    val settings: StateFlow<AppSettings> = settingsRepo.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    fun setThemeMode(mode: String) = viewModelScope.launch { settingsRepo.setThemeMode(mode) }
    fun setThemeId(id: String) = viewModelScope.launch { settingsRepo.setThemeId(id) }

    /** 应用图片取色自定义主题；seed=null 表示恢复默认主题。 */
    fun applyCustomTheme(seed: Int?) {
        viewModelScope.launch {
            if (seed == null) {
                settingsRepo.setCustomSeed(null)
                settingsRepo.setThemeId(com.monaflow.app.core.theme.MonaPreset.WATER_LILY)
            } else {
                settingsRepo.setCustomSeed(seed.toLong())
                settingsRepo.setThemeId(com.monaflow.app.core.theme.MonaPreset.CUSTOM)
            }
        }
    }

    /** 从图片提取种子色（本地处理，失败返回 null 由调用方提示）。 */
    fun extractSeedFromImage(uri: android.net.Uri, onResult: (Int?) -> Unit) {
        viewModelScope.launch {
            val seed = withContext(Dispatchers.IO) {
                com.monaflow.app.core.theme.PaletteExtractor.extractSeed(app, uri)
            }
            onResult(seed)
        }
    }

    /** 色相微调：把种子色 hue 偏移 delta 度。 */
    fun shiftSeedHue(seed: Int, delta: Float): Int {
        val (h, s, l) = com.monaflow.app.core.theme.ColorUtils.rgbToHsl(seed)
        val (r, g, b) = com.monaflow.app.core.theme.ColorUtils.hslToRgb(
            (h + delta).mod(360f), s.coerceIn(0.16f, 0.38f), l,
        )
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }

    fun setReminders(todo: Boolean, habit: Boolean, countdown: Boolean, warranty: Boolean) =
        viewModelScope.launch { settingsRepo.setReminders(todo, habit, countdown, warranty) }

    /** 首页模块顺序/隐藏/紧凑。 */
    fun setHomeModules(order: List<String>, hidden: Set<String>, compact: Boolean) =
        viewModelScope.launch { settingsRepo.setHomeModules(order, hidden, compact) }

    /** HyperOS：跳到系统应用详情页（自启动/省电策略排查入口）。 */
    fun openAppDetailsSettings() {
        val intent = android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            .setData(android.net.Uri.fromParts("package", app.packageName, null))
            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { app.startActivity(intent) }
    }

    /** 精确闹钟权限（Android 12+）：缺失时提醒退化为非精确。 */
    fun canScheduleExactAlarms(): Boolean =
        android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.S ||
            (app.getSystemService(android.app.AlarmManager::class.java)?.canScheduleExactAlarms() ?: false)

    fun requestExactAlarmPermission() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            runCatching {
                app.startActivity(
                    android.content.Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                        .setData(android.net.Uri.fromParts("package", app.packageName, null))
                        .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
        }
    }

    // ---------- 备份 ----------

    sealed class BackupUi {
        data object Idle : BackupUi()
        data object Working : BackupUi()
        data class ExportDone(val fileName: String, val counts: Map<String, Int>) : BackupUi()
        data class ImportPreview(
            val manifest: BackupManager.BackupManifest,
            val stream: java.io.InputStream,
        ) : BackupUi()

        data class Done(val message: String) : BackupUi()
        data class Error(val message: String) : BackupUi()
    }

    val backupUi = MutableStateFlow<BackupUi>(BackupUi.Idle)

    fun exportTo(uri: android.net.Uri, displayName: String) {
        viewModelScope.launch {
            backupUi.value = BackupUi.Working
            val result = withContext(Dispatchers.IO) {
                app.contentResolver.openOutputStream(uri)?.use { out ->
                    backupManager.exportTo(out, appVersion())
                } ?: Result.failure(IllegalStateException("无法写入所选位置"))
            }
            backupUi.value = result.fold(
                onSuccess = { BackupUi.ExportDone(displayName, it.counts) },
                onFailure = { BackupUi.Error("导出失败：${it.message}") },
            )
        }
    }

    fun importPreview(uri: android.net.Uri) {
        viewModelScope.launch {
            backupUi.value = BackupUi.Working
            val stream = withContext(Dispatchers.IO) {
                app.contentResolver.openInputStream(uri)
            }
            if (stream == null) {
                backupUi.value = BackupUi.Error("无法读取所选文件")
                return@launch
            }
            runCatching { backupManager.inspect(stream) }
                .onSuccess { manifest ->
                    // 重新打开流供恢复使用（inspect 只消费了一次）
                    val fresh = withContext(Dispatchers.IO) { app.contentResolver.openInputStream(uri) }
                    if (fresh == null) {
                        backupUi.value = BackupUi.Error("无法再次读取所选文件")
                    } else {
                        backupUi.value = BackupUi.ImportPreview(manifest, fresh)
                    }
                }
                .onFailure {
                    runCatching { stream.close() }
                    backupUi.value = BackupUi.Error("备份无效：${it.message}")
                }
        }
    }

    fun confirmRestore() {
        val ui = backupUi.value
        if (ui !is BackupUi.ImportPreview) return
        viewModelScope.launch {
            backupUi.value = BackupUi.Working
            val result = withContext(Dispatchers.IO) {
                ui.stream.use { stream -> backupManager.restoreFrom(stream, appVersion()) }
            }
            backupUi.value = result.fold(
                onSuccess = { BackupUi.Done("恢复完成：${it.counts.entries.joinToString("，") { "${it.key} ${it.value} 条" }}") },
                onFailure = { BackupUi.Error("恢复失败（现有数据未被修改）：${it.message}") },
            )
        }
    }

    fun dismissBackupUi() {
        (backupUi.value as? BackupUi.ImportPreview)?.let { runCatching { it.stream.close() } }
        backupUi.value = BackupUi.Idle
    }

    private fun appVersion(): String = runCatching {
        val ctx: Context = app
        ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "unknown"
    }.getOrDefault("unknown")
}
