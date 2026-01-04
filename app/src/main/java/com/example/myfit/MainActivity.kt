package com.example.myfit

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.myfit.ui.MainScreen
import com.example.myfit.ui.theme.MyFitTheme
import com.example.myfit.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            // 获取 VM 以监听主题变化
            val viewModel: MainViewModel = viewModel()
            val theme by viewModel.currentTheme.collectAsState()

            MyFitTheme(appTheme = theme) {
                MainScreen()
            }
        }
    }
}