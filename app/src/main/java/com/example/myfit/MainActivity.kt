package com.example.myfit

import android.app.Activity
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.myfit.data.AppDatabase
import com.example.myfit.ui.MainScreen
import com.example.myfit.ui.theme.MyFitTheme
import com.example.myfit.viewmodel.MainViewModel
import com.example.myfit.util.LocaleHelper
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.util.Locale

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val viewModel: MainViewModel = viewModel()
            val currentTheme by viewModel.currentTheme.collectAsState()

            // 监听数据库中的语言设置
            val currentLanguage by viewModel.currentLanguage.collectAsState()
            val context = LocalContext.current

            // 🌟 核心修复逻辑 🌟
            // 当数据库的语言 (currentLanguage) 发生变化时执行
            LaunchedEffect(currentLanguage) {
                // 1. 获取当前界面实际显示的语言
                val config = context.resources.configuration
                val sysLocale = config.locales[0]
                val currentDisplayLanguage = sysLocale.language

                // 2. 只有当“想要的语言”和“正在显示的语言”不一样时，才重启
                if (currentDisplayLanguage != currentLanguage && currentLanguage.isNotEmpty()) {
                    // 应用新语言配置
                    LocaleHelper.setLocale(context, currentLanguage)
                    // 重启 Activity 以重新加载 strings.xml 资源
                    (context as? Activity)?.recreate()
                }
            }

            MyFitTheme(appTheme = currentTheme) {
                MainScreen()
            }
        }
    }

    // 保持之前的逻辑不变，确保 App 启动瞬间语言就是对的
    override fun attachBaseContext(newBase: Context) {
        val languageCode = try {
            runBlocking {
                val db = AppDatabase.getDatabase(newBase)
                val setting = db.workoutDao().getAppSettings().first()
                setting?.languageCode ?: "zh"
            }
        } catch (e: Exception) {
            "zh"
        }
        val context = LocaleHelper.setLocale(newBase, languageCode)
        super.attachBaseContext(context)
    }
}