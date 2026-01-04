package com.example.myfit.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.EditCalendar
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.myfit.viewmodel.MainViewModel

@Composable
fun MainScreen() {
    val navController = rememberNavController()
    val viewModel: MainViewModel = viewModel()

    // 监听主题，确保底部导航栏颜色也跟随
    val currentTheme by viewModel.currentTheme.collectAsState()

    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface, // 跟随主题
                contentColor = MaterialTheme.colorScheme.onSurface
            ) {
                val items = listOf(
                    Triple("打卡", Icons.Default.DateRange, "home"),
                    Triple("历史", Icons.Default.History, "history"),
                    Triple("设置", Icons.Default.EditCalendar, "schedule")
                )
                items.forEach { (label, icon, route) ->
                    val isSelected = navController.currentDestination?.route == route
                    NavigationBarItem(
                        icon = { Icon(icon, contentDescription = null) },
                        label = { Text(label) },
                        selected = isSelected,
                        onClick = { navController.navigate(route) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primary
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(navController, startDestination = "home", modifier = Modifier.padding(innerPadding)) {
            composable("home") { DailyPlanScreen(viewModel, navController) } // 传 nav 用于快捷跳转
            composable("history") { HistoryScreen(viewModel) }
            composable("schedule") { ScheduleScreen(navController, viewModel) }
            composable("exercise_manager") { ExerciseManagerScreen(navController, viewModel) }
        }
    }
}