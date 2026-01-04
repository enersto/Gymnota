package com.example.myfit.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
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
    // 根据数据库选的主题，动态生成颜色方案
    val colorScheme = if (appTheme == AppTheme.DARK) {
        darkColorScheme(
            primary = Color(appTheme.primary),
            background = Color(appTheme.background),
            surface = Color(0xFF1E1E1E), // 深色模式卡片背景
            onBackground = Color(appTheme.onBackground)
        )
    } else {
        lightColorScheme(
            primary = Color(appTheme.primary),
            background = Color(appTheme.background),
            surface = Color.White, // 浅色模式卡片背景
            onBackground = Color(appTheme.onBackground)
        )
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            // 设置状态栏颜色与背景一致 (解决颜色不一致问题)
            window.statusBarColor = colorScheme.background.toArgb()
            // 如果是深色主题，状态栏文字变白；否则变黑
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = (appTheme != AppTheme.DARK)
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}