package com.example.myfit.ui

import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.myfit.R
import com.example.myfit.ui.components.GlassScaffoldContent
import com.example.myfit.ui.components.LocalBackdrop
import com.example.myfit.ui.components.LocalGlassMode
import com.example.myfit.ui.components.liquidtabs.LiquidBottomTabs
import com.example.myfit.ui.components.liquidtabs.LocalLiquidBottomTabScale
import com.example.myfit.viewmodel.MainViewModel
import com.kyant.shapes.Capsule
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseSelectionScreen(viewModel: MainViewModel, navController: NavController) {
    val templates by viewModel.allTemplates.collectAsState(initial = emptyList())
    val context = LocalContext.current

    // 搜索状态
    var isSearching by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    val categories = listOf("STRENGTH", "CARDIO", "CORE")
    // 初始分类
    var selectedCategory by remember { mutableStateOf("STRENGTH") }
    val pagerState = rememberPagerState(pageCount = { categories.size })
    val scope = rememberCoroutineScope()

    // 联动 Tab 和 Pager
    LaunchedEffect(selectedCategory) {
        pagerState.animateScrollToPage(categories.indexOf(selectedCategory))
    }
    LaunchedEffect(pagerState.currentPage) {
        selectedCategory = categories[pagerState.currentPage]
    }

    val glassMode = LocalGlassMode.current
    val useGlassTabs = glassMode && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val backdrop = LocalBackdrop.current

    GlassScaffoldContent {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                Column(modifier = Modifier.statusBarsPadding().padding(top = 24.dp)) {
                    if (isSearching) {
                        ExerciseSearchBar(
                            query = searchQuery,
                            onQueryChange = { searchQuery = it },
                            onBack = {
                                isSearching = false
                                searchQuery = ""
                            },
                            onClear = { searchQuery = "" }
                        )
                    } else {
                        CenterAlignedTopAppBar(
                            title = { Text(stringResource(R.string.title_select_exercise), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) },
                            navigationIcon = {
                                IconButton(onClick = { navController.popBackStack() }) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "Back"
                                    )
                                }
                            },
                            actions = {
                                IconButton(onClick = { isSearching = true }) {
                                    Icon(Icons.Default.Search, contentDescription = "Search")
                                }
                            },
                            colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                                containerColor = Color.Transparent
                            )
                        )
                        
                        if (backdrop != null) {
                            LiquidBottomTabs(
                                selectedTabIndex = { categories.indexOf(selectedCategory) },
                                onTabSelected = { selectedCategory = categories[it] },
                                backdrop = backdrop,
                                tabsCount = categories.size,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            ) {
                                categories.forEachIndexed { index, category ->
                                    val scale = LocalLiquidBottomTabScale.current
                                    Box(
                                        Modifier
                                            .clip(Capsule())
                                            .fillMaxHeight()
                                            .weight(1f)
                                            .graphicsLayer {
                                                val s = scale()
                                                scaleX = s; scaleY = s
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = stringResource(getCategoryResId(category)),
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = if (selectedCategory == category) FontWeight.Bold else FontWeight.Normal,
                                            color = if (selectedCategory == category) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        ) { padding ->
            ExerciseListContent(
                padding = PaddingValues(
                    top = padding.calculateTopPadding(),
                    bottom = padding.calculateBottomPadding() + 100.dp, // 预留空间给底部全局 Tab
                    start = 0.dp,
                    end = 0.dp
                ),
                searchQuery = searchQuery,
                templates = templates,
                pagerState = pagerState,
                categories = categories,
                onItemClick = { template ->
                    viewModel.addTaskFromTemplate(template)
                    Toast.makeText(
                        context,
                        context.getString(R.string.msg_added_to_plan, template.name),
                        Toast.LENGTH_SHORT
                    ).show()
                    navController.popBackStack()
                },
                onDelete = null
            )
        }
    }
}
