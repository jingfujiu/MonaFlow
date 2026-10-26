package com.monaflow.app.data.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.monaflow.app.core.theme.MonaPreset
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SettingsRepositoryTest {

    @Test
    fun `theme and home modules persist`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = SettingsRepository(context)

        // 默认值
        val defaults = repo.settings.first()
        assertEquals(AppSettings.THEME_SYSTEM, defaults.themeMode)
        assertEquals(MonaPreset.WATER_LILY, defaults.themeId)
        assertEquals(AppSettings.DEFAULT_HOME_ORDER, defaults.homeModulesOrder)
        assertTrue(defaults.homeModulesHidden.isEmpty())

        // 修改后读取
        repo.setThemeMode(AppSettings.THEME_DARK)
        repo.setThemeId(MonaPreset.GARDEN)
        repo.setHomeModules(
            listOf(AppSettings.MODULE_COUNTDOWNS, AppSettings.MODULE_TASKS, AppSettings.MODULE_HABITS, AppSettings.MODULE_NOTES, AppSettings.MODULE_ITEMS),
            setOf(AppSettings.MODULE_ITEMS),
            compact = true,
        )
        val updated = repo.settings.first()
        assertEquals(AppSettings.THEME_DARK, updated.themeMode)
        assertEquals(MonaPreset.GARDEN, updated.themeId)
        assertEquals(AppSettings.MODULE_COUNTDOWNS, updated.homeModulesOrder.first())
        assertTrue(AppSettings.MODULE_ITEMS in updated.homeModulesHidden)
        assertTrue(updated.homeCompact)

        // 恢复默认主题
        repo.applyDefaults()
        val restored = repo.settings.first()
        assertEquals(MonaPreset.WATER_LILY, restored.themeId)
    }

    private suspend fun SettingsRepository.applyDefaults() {
        setThemeMode(AppSettings.THEME_SYSTEM)
        setThemeId(MonaPreset.WATER_LILY)
        setCustomSeed(null)
    }
}
