package com.lightdesk.app

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lightdesk.app.di.AppContainer
import com.lightdesk.app.ui.LightDeskApp
import com.lightdesk.core.designsystem.theme.LightDeskTheme
import com.lightdesk.core.model.AppSettings
import com.lightdesk.core.model.ThemeMode

/**
 * 单 Activity + Compose 架构：所有页面由导航图管理，便于统一主题、返回栈与转场。
 * v1.1.0 起不再处理悬浮球唤起 Intent（功能下线）。
 */
class MainActivity : ComponentActivity() {

    private val container: AppContainer
        get() = (application as LightDeskApplication).container

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            val settings by container.settingsRepository.observeSettings()
                .collectAsStateWithLifecycle(initialValue = AppSettings())
            val darkTheme = when (settings.themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            LightDeskTheme(darkTheme = darkTheme, palette = settings.themePalette) {
                // 手动切换主题时同步系统状态栏/导航栏图标明暗，避免深色页面配深色图标
                val view = LocalView.current
                if (!view.isInEditMode) {
                    SideEffect {
                        val window = (view.context as Activity).window
                        val controller = WindowCompat.getInsetsController(window, view)
                        controller.isAppearanceLightStatusBars = !darkTheme
                        controller.isAppearanceLightNavigationBars = !darkTheme
                    }
                }
                LightDeskApp(container)
            }
        }
    }
}
