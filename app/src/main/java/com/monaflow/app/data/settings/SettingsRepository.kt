package com.monaflow.app.data.settings

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.monaflow.app.core.theme.MonaPreset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "monaflow_settings")

data class AppSettings(
    val themeMode: String = THEME_SYSTEM, // system / light / dark
    val themeId: String = MonaPreset.WATER_LILY,
    val customSeedColor: Long? = null, // 图片取色种子色（ARGB）
    val homeModulesOrder: List<String> = DEFAULT_HOME_ORDER,
    val homeModulesHidden: Set<String> = emptySet(),
    val homeCompact: Boolean = false,
    val defaultAiProviderId: Long = -1,
    val reminderTodo: Boolean = true,
    val reminderHabit: Boolean = true,
    val reminderCountdown: Boolean = true,
    val reminderWarranty: Boolean = true,
) {
    companion object {
        const val THEME_SYSTEM = "system"
        const val THEME_LIGHT = "light"
        const val THEME_DARK = "dark"

        const val MODULE_TASKS = "tasks"
        const val MODULE_HABITS = "habits"
        const val MODULE_NOTES = "notes"
        const val MODULE_COUNTDOWNS = "countdowns"
        const val MODULE_ITEMS = "items"

        val DEFAULT_HOME_ORDER = listOf(MODULE_TASKS, MODULE_HABITS, MODULE_COUNTDOWNS, MODULE_NOTES, MODULE_ITEMS)
    }
}

class SettingsRepository(private val context: Context) {

    val settings: Flow<AppSettings> = context.dataStore.data.map { p ->
        AppSettings(
            themeMode = p[KEY_THEME_MODE] ?: AppSettings.THEME_SYSTEM,
            themeId = p[KEY_THEME_ID] ?: MonaPreset.WATER_LILY,
            customSeedColor = p[KEY_CUSTOM_SEED]?.takeIf { it != 0L },
            homeModulesOrder = p[KEY_HOME_ORDER]?.split(",")?.filter { it.isNotBlank() }
                ?: AppSettings.DEFAULT_HOME_ORDER,
            homeModulesHidden = p[KEY_HOME_HIDDEN]?.split(",")?.filter { it.isNotBlank() }?.toSet()
                ?: emptySet(),
            homeCompact = p[KEY_HOME_COMPACT] == "1",
            defaultAiProviderId = p[KEY_AI_DEFAULT] ?: -1L,
            reminderTodo = p[KEY_REM_TODO] != "0",
            reminderHabit = p[KEY_REM_HABIT] != "0",
            reminderCountdown = p[KEY_REM_COUNTDOWN] != "0",
            reminderWarranty = p[KEY_REM_WARRANTY] != "0",
        )
    }

    suspend fun setThemeMode(mode: String) = context.dataStore.edit { it[KEY_THEME_MODE] = mode }
    suspend fun setThemeId(id: String) = context.dataStore.edit { it[KEY_THEME_ID] = id }
    suspend fun setCustomSeed(seed: Long?) = context.dataStore.edit {
        if (seed == null) it.remove(KEY_CUSTOM_SEED) else it[KEY_CUSTOM_SEED] = seed
    }

    suspend fun setHomeModules(order: List<String>, hidden: Set<String>, compact: Boolean) =
        context.dataStore.edit {
            it[KEY_HOME_ORDER] = order.joinToString(",")
            it[KEY_HOME_HIDDEN] = hidden.joinToString(",")
            it[KEY_HOME_COMPACT] = if (compact) "1" else "0"
        }

    suspend fun setDefaultAiProvider(id: Long) = context.dataStore.edit { it[KEY_AI_DEFAULT] = id }

    suspend fun setReminders(todo: Boolean, habit: Boolean, countdown: Boolean, warranty: Boolean) =
        context.dataStore.edit {
            it[KEY_REM_TODO] = if (todo) "1" else "0"
            it[KEY_REM_HABIT] = if (habit) "1" else "0"
            it[KEY_REM_COUNTDOWN] = if (countdown) "1" else "0"
            it[KEY_REM_WARRANTY] = if (warranty) "1" else "0"
        }

    private companion object {
        val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
        val KEY_THEME_ID = stringPreferencesKey("theme_id")
        val KEY_CUSTOM_SEED = longPreferencesKey("custom_seed")
        val KEY_HOME_ORDER = stringPreferencesKey("home_order")
        val KEY_HOME_HIDDEN = stringPreferencesKey("home_hidden")
        val KEY_HOME_COMPACT = stringPreferencesKey("home_compact")
        val KEY_AI_DEFAULT = longPreferencesKey("ai_default")
        val KEY_REM_TODO = stringPreferencesKey("rem_todo")
        val KEY_REM_HABIT = stringPreferencesKey("rem_habit")
        val KEY_REM_COUNTDOWN = stringPreferencesKey("rem_countdown")
        val KEY_REM_WARRANTY = stringPreferencesKey("rem_warranty")
    }
}
