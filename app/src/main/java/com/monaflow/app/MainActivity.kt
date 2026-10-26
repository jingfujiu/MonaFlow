package com.monaflow.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.monaflow.app.core.navigation.MonaNavHost
import com.monaflow.app.core.notifications.NotificationHelper
import com.monaflow.app.core.theme.MonaColors
import com.monaflow.app.core.theme.MonaFlowTheme
import com.monaflow.app.core.theme.MonaPreset
import com.monaflow.app.core.theme.presetColors
import com.monaflow.app.data.settings.AppSettings

class MainActivity : ComponentActivity() {

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestNotificationPermissionIfNeeded()

        val app = application as MonaFlowApp
        // 通知点击跳转：把路由作为起始目的地
        val startRoute = intent?.getStringExtra(NotificationHelper.EXTRA_ROUTE)
        setContent {
            val settings by app.settingsRepository.settings
                .collectAsStateWithLifecycle(initialValue = AppSettings())
            val dark = when (settings.themeMode) {
                AppSettings.THEME_LIGHT -> false
                AppSettings.THEME_DARK -> true
                else -> isSystemInDarkTheme()
            }
            val mona: MonaColors = if (
                settings.themeId == MonaPreset.CUSTOM && settings.customSeedColor != null
            ) {
                MonaColors.fromSeed(settings.customSeedColor!!.toInt(), dark)
            } else {
                presetColors(settings.themeId, dark)
            }
            MonaFlowTheme(mona = mona, dark = dark) {
                MonaNavHost(startDestination = startRoute ?: com.monaflow.app.core.navigation.Routes.TODAY)
            }
        }
    }

    /** Android 13+ 通知运行时权限；拒绝不影响功能，仅提醒不可用。 */
    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}
