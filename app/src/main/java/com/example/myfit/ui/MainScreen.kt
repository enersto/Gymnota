package com.example.myfit.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartToy // [新增] 修复 SmartToy 报错
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.myfit.R
import com.example.myfit.viewmodel.MainViewModel
import android.view.HapticFeedbackConstants

sealed class Screen(val route: String, val titleResId: Int, val icon: ImageVector) {
    object DailyPlan : Screen("daily_plan", R.string.tab_home, Icons.Default.CalendarToday)
    object History : Screen("history", R.string.tab_history, Icons.Default.History)
    object AICoach : Screen("ai_coach", R.string.tab_ai_coach, Icons.Default.SmartToy) // [新增]
    object Settings : Screen("settings", R.string.tab_settings, Icons.Default.Settings)
}

@Composable
fun MainScreen(viewModel: MainViewModel) {
    val navController = rememberNavController()
    // [修改] 更新底部导航列表
    val screens = listOf(Screen.DailyPlan, Screen.History, Screen.AICoach, Screen.Settings)

    val view = LocalView.current

    Scaffold(
        bottomBar = {
            NavigationBar {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = navBackStackEntry?.destination
                screens.forEach { screen ->
                    NavigationBarItem(
                        icon = { Icon(screen.icon, contentDescription = null) },
                        label = { Text(stringResource(screen.titleResId)) },
                        selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                        onClick = {
                            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            NavHost(
                navController = navController,
                startDestination = Screen.DailyPlan.route
            ) {
                composable(Screen.DailyPlan.route) {
                    DailyPlanScreen(navController = navController, viewModel = viewModel)
                }
                composable(Screen.History.route) {
                    HistoryScreen(viewModel)
                }
                // [新增] AI 教练页面路由
                composable(Screen.AICoach.route) {
                    AICoachScreen(viewModel = viewModel, navController = navController)
                }
                composable(Screen.Settings.route) {
                    ScheduleScreen(navController, viewModel)
                }
                composable("exercise_manager") {
                    ExerciseManagerScreen(navController = navController, viewModel = viewModel)
                }
                composable("exercise_selector") {
                    ExerciseSelectionScreen(viewModel = viewModel, navController = navController)
                }
            }
        }
    }
}