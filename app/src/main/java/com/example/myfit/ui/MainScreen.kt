package com.example.myfit.ui

import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.myfit.R
import com.example.myfit.ui.components.GlassScaffoldContent
import com.example.myfit.ui.components.LocalBackdrop
import com.example.myfit.ui.components.LocalGlassMode
import com.example.myfit.ui.components.liquidtabs.LiquidBottomTabs
import com.example.myfit.ui.components.liquidtabs.LocalLiquidBottomTabScale
import com.example.myfit.viewmodel.MainViewModel
import com.kyant.shapes.Capsule

sealed class Screen(val route: String, val titleResId: Int, val icon: ImageVector) {
    object DailyPlan : Screen("daily_plan", R.string.tab_home, Icons.Default.CalendarToday)
    object History : Screen("history", R.string.tab_history, Icons.Default.History)
    object AICoach : Screen("ai_coach", R.string.tab_ai_coach, Icons.Default.SmartToy)
    object Settings : Screen("settings", R.string.tab_settings, Icons.Default.Settings)
}

@Composable
fun MainScreen(viewModel: MainViewModel) {
    val navController = rememberNavController()
    val screens = listOf(Screen.DailyPlan, Screen.History, Screen.AICoach, Screen.Settings)
    val view = LocalView.current
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    
    // Improved selection logic using route patterns
    val selectedIndex = screens.indexOfFirst { screen ->
        currentDestination?.hierarchy?.any { it.route?.startsWith(screen.route) == true } == true
    }.coerceAtLeast(0)

    val glassMode = LocalGlassMode.current
    val useGlassTabs = glassMode && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    Scaffold(
        bottomBar = {
            if (!useGlassTabs) {
                NavigationBar {
                    screens.forEach { screen ->
                        NavigationBarItem(
                            icon = { Icon(screen.icon, contentDescription = null) },
                            label = { Text(stringResource(screen.titleResId)) },
                            selected = currentDestination?.hierarchy?.any {
                                it.route?.startsWith(screen.route) == true
                            } == true,
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
        }
    ) { innerPadding ->
        GlassScaffoldContent {
            Box(modifier = Modifier.fillMaxSize()) {
                NavHost(
                    navController = navController,
                    startDestination = Screen.DailyPlan.route,
                    modifier = Modifier.padding(
                        bottom = if (useGlassTabs) 0.dp else innerPadding.calculateBottomPadding()
                    )
                ) {
                    composable(Screen.DailyPlan.route) {
                        DailyPlanScreen(navController = navController, viewModel = viewModel)
                    }
                    composable(Screen.History.route) {
                        HistoryScreen(viewModel)
                    }
                    composable(Screen.AICoach.route) {
                        AICoachScreen(viewModel = viewModel, navController = navController)
                    }
                    composable(
                        route = Screen.Settings.route + "?scrollToType={scrollToType}&scrollToAi={scrollToAi}",
                        arguments = listOf(
                            navArgument("scrollToType") { defaultValue = "false"; type = NavType.StringType },
                            navArgument("scrollToAi") { defaultValue = "false"; type = NavType.StringType }
                        )
                    ) {
                        ScheduleScreen(navController, viewModel)
                    }
                    composable("exercise_manager") {
                        ExerciseManagerScreen(navController = navController, viewModel = viewModel)
                    }
                    composable("exercise_selector") {
                        ExerciseSelectionScreen(viewModel = viewModel, navController = navController)
                    }
                }

                if (useGlassTabs) {
                    val backdrop = LocalBackdrop.current
                    if (backdrop != null) {
                        LiquidBottomTabs(
                            selectedTabIndex = { selectedIndex },
                            onTabSelected = { index ->
                                val screen = screens[index]
                                if (selectedIndex != index) {
                                    view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                    navController.navigate(screen.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            },
                            backdrop = backdrop,
                            tabsCount = screens.size,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                        ) {
                            screens.forEachIndexed { index, screen ->
                                val scale = LocalLiquidBottomTabScale.current
                                Column(
                                    Modifier
                                        .clip(Capsule())
                                        .fillMaxHeight()
                                        .weight(1f)
                                        .graphicsLayer {
                                            val s = scale()
                                            scaleX = s; scaleY = s
                                        },
                                    verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(
                                        screen.icon,
                                        contentDescription = null,
                                        modifier = Modifier.size(22.dp)
                                    )
                                    Text(
                                        stringResource(screen.titleResId),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontSize = 10.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
