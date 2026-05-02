package com.example.myfit.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.example.myfit.model.AppTheme

@Composable
fun MyFitTheme(
    appTheme: AppTheme,
    content: @Composable () -> Unit
) {
    // 根据 AppTheme 枚举生成颜色方案
    val primaryColor = Color(appTheme.primary)
    val background = Color(appTheme.background)
    val onBackground = Color(appTheme.onBackground)

    // 彻底移除深色模式逻辑，统一使用浅色方案
    val colorScheme = lightColorScheme(
        primary = primaryColor,
        background = background,
        surface = Color.White,
        onPrimary = Color.White,
        onBackground = onBackground,
        onSurface = onBackground,
        scrim = Color.Black.copy(alpha = 0.2f)
    )

    // 设置状态栏颜色
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            
            // 将状态栏设为透明，配合 TopBar 使用
            window.statusBarColor = Color.Transparent.toArgb()

            val insetsController = WindowCompat.getInsetsController(window, view)
            // 由于强制浅色背景，状态栏图标统一设为黑色/深色
            insetsController.isAppearanceLightStatusBars = true
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
