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
    val isDark = appTheme == AppTheme.DARK

    // 根据 AppTheme 枚举生成颜色方案
    val primaryColor = if (isDark) Color(0xFF90CAF9) else Color(appTheme.primary)
    val background = Color(appTheme.background)
    val onBackground = Color(appTheme.onBackground)

    val colorScheme = if (isDark) {
        darkColorScheme(
            primary = primaryColor,
            background = background,
            surface = Color(0xFF1E1E1E),
            surfaceVariant = Color(0xFF2C2C2C),
            onPrimary = Color.Black,
            onBackground = onBackground,
            onSurface = onBackground,
            onSurfaceVariant = Color(0xFFCCCCCC),
            scrim = Color.Black.copy(alpha = 0.5f)
        )
    } else {
        lightColorScheme(
            primary = primaryColor,
            background = background,
            surface = Color.White,
            onPrimary = Color.White,
            onBackground = onBackground,
            onSurface = onBackground,
            scrim = Color.Black.copy(alpha = 0.2f)
        )
    }

    // 设置状态栏颜色
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            
            // 将状态栏设为透明，配合 TopBar 使用
            window.statusBarColor = Color.Transparent.toArgb()

            val insetsController = WindowCompat.getInsetsController(window, view)
            // 暗黑模式下状态栏图标设为浅色，浅色模式下设为深色图标
            insetsController.isAppearanceLightStatusBars = !isDark
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
