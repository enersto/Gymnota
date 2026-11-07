package com.example.myfit

import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.os.Bundle
import androidx.core.view.doOnPreDraw
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import com.example.myfit.ui.MainScreen
import com.example.myfit.ui.theme.MyFitTheme
import com.example.myfit.util.LocaleHelper
import com.example.myfit.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {
    // 使用 viewModels() 委托获取 ViewModel 实例
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Queue initialization after the native first frame, rather than inside manager composition.
        window.decorView.doOnPreDraw {
            window.decorView.post {
                if (!isFinishing && !isDestroyed) {
                    com.example.myfit.flutter.HistoryRepository.start(applicationContext)
                    com.example.myfit.flutter.HistoryEnginePool.warm(this@MainActivity)
                    window.decorView.postDelayed({ if (!isFinishing && !isDestroyed) com.example.myfit.flutter.CatalogEnginePool.warm(this@MainActivity) }, 1500)
                }
            }
        }
        setContent {
            // 监听 ViewModel 中的主题和语言设置
            val currentTheme by viewModel.currentTheme.collectAsState()
            val currentLanguage by viewModel.currentLanguage.collectAsState()
            val systemConfig = LocalConfiguration.current
            val localized = remember(currentLanguage, systemConfig) {
                val resourcesContext = LocaleHelper.setLocale(this, currentLanguage)
                object : ContextWrapper(this) {
                    override fun getResources() = resourcesContext.resources
                    override fun getAssets() = resourcesContext.assets
                }
            }
            val localizedConfig = remember(localized) { Configuration(localized.resources.configuration) }
            LaunchedEffect(currentLanguage) { LocaleHelper.rememberLanguage(this@MainActivity, currentLanguage) }
            // Prepare the read-only catalogue off the UI thread before its tab is opened.
            LaunchedEffect(currentLanguage, "catalogueWarmup") {
                if (currentLanguage.isNotEmpty()) {
                    try { com.example.myfit.data.ExerciseCatalog(applicationContext).load(currentLanguage) }
                    catch (e: kotlinx.coroutines.CancellationException) { throw e }
                    catch (_: Exception) { /* Opening the catalogue can retry normally. */ }
                }
            }


            CompositionLocalProvider(
                LocalContext provides localized,
                LocalConfiguration provides localizedConfig,
                LocalResources provides localized.resources
            ) {
                MyFitTheme(appTheme = currentTheme) { MainScreen(viewModel = viewModel) }
            }
        }
    }

    override fun onDestroy() {
        com.example.myfit.flutter.CatalogEnginePool.releaseWarmup(this)
        com.example.myfit.flutter.HistoryEnginePool.releaseWarmup(this)
        super.onDestroy()
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.setLocale(newBase, LocaleHelper.cachedLanguage(newBase)))
    }
}
