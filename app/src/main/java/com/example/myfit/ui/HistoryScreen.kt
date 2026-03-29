package com.example.myfit.ui

import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myfit.R
import com.example.myfit.model.*
import com.example.myfit.ui.components.GlassCard
import com.example.myfit.ui.components.GlassScaffoldContent
import com.example.myfit.ui.components.LocalBackdrop
import com.example.myfit.ui.components.LocalGlassMode
import com.example.myfit.viewmodel.MainViewModel
import com.kyant.shapes.Capsule

@Composable
fun HistoryScreen(viewModel: MainViewModel) {
    var selectedTabIndex by remember { mutableIntStateOf(1) }
    val tabTitles = listOf(
        stringResource(R.string.tab_list),
        stringResource(R.string.tab_chart)
    )

    val view = LocalView.current
    val glassMode = LocalGlassMode.current
    val useGlassTabs = glassMode && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    GlassScaffoldContent {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(top = 24.dp) // Added more top padding
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(R.string.history_title),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground
                )

                // Sub-tabs moved to the top right for better layout in glass mode
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    shape = Capsule(),
                    modifier = Modifier.height(36.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        tabTitles.forEachIndexed { index, title ->
                            val isSelected = selectedTabIndex == index
                            Box(
                                modifier = Modifier
                                    .clip(Capsule())
                                    .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent)
                                    .clickable {
                                        if (selectedTabIndex != index) {
                                            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                            selectedTabIndex = index
                                        }
                                    }
                                    .padding(horizontal = 16.dp, vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = title,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Box(modifier = Modifier.weight(1f)) {
                when (selectedTabIndex) {
                    0 -> HistoryList(viewModel)
                    1 -> HistoryCharts(viewModel)
                }
            }
        }
    }
}

@Composable
fun HistoryList(viewModel: MainViewModel) {
    val tasks by viewModel.historyRecords.collectAsState(initial = emptyList())
    val weights by viewModel.weightHistory.collectAsState(initial = emptyList())

    val historyData = remember(tasks, weights) {
        val allDates = (tasks.map { it.date } + weights.map { it.date }).distinct().sortedDescending()
        allDates.map { date ->
            Triple(date, weights.find { it.date == date }, tasks.filter { it.date == date })
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        if (historyData.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.no_history), color = Color.Gray)
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(24.dp)) {
                historyData.forEach { (date, weightRecord, dayTasks) ->
                    item {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = date,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                                    fontWeight = FontWeight.Bold
                                )
                                if (weightRecord != null) {
                                    Surface(
                                        color = Color(0xFFFF9800).copy(alpha = 0.1f),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                Icons.Default.MonitorWeight,
                                                null,
                                                modifier = Modifier.size(14.dp),
                                                tint = Color(0xFFFF9800)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = "${weightRecord.weight} KG",
                                                color = Color(0xFFFF9800),
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }

                    if (dayTasks.isEmpty()) {
                        item {
                            Text(
                                stringResource(R.string.history_no_train),
                                fontSize = 12.sp,
                                color = Color.Gray,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                        }
                    } else {
                        items(dayTasks) { task ->
                            HistoryTaskCard(task)
                        }
                    }
                }
                item { Spacer(modifier = Modifier.height(120.dp)) }
            }
        }
    }
}

@Composable
fun HistoryCharts(viewModel: MainViewModel) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        item {
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                PixelBodyHeatmap(viewModel = viewModel, modifier = Modifier.padding(16.dp))
            }
        }

        item {
            Text(
                stringResource(R.string.header_strength_train),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
        item {
            SingleExerciseSection(
                viewModel = viewModel,
                category = "STRENGTH",
                title = stringResource(R.string.chart_title_strength_single),
                defaultMode = 1
            )
        }

        item {
            Text(
                stringResource(R.string.header_core_train),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
        item {
            SingleExerciseSection(
                viewModel = viewModel,
                category = "CORE",
                title = stringResource(R.string.chart_title_core_single),
                defaultMode = 2
            )
        }

        item {
            Text(
                stringResource(R.string.header_cardio_train),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
        item {
            ChartSection(
                title = stringResource(R.string.chart_title_cardio_total),
                defaultChartType = "BAR"
            ) { granularity ->
                val data by viewModel.getCardioTotalChartData(granularity).collectAsState(initial = emptyList())
                BarChart(data = data)
            }
        }
        item {
            SingleExerciseSection(
                viewModel = viewModel,
                category = "CARDIO",
                title = stringResource(R.string.chart_title_cardio_single),
                defaultMode = 0
            )
        }

        item {
            Text(
                stringResource(R.string.chart_title_body_status),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }

        item {
            ChartSection(title = stringResource(R.string.chart_title_weight)) { granularity ->
                val data by viewModel.getWeightChartData(granularity).collectAsState(initial = emptyList())
                LineChart(data = data)
            }
        }

        item {
            ChartSection(title = stringResource(R.string.chart_title_bmi)) { granularity ->
                val data by viewModel.getBMIChartData(granularity).collectAsState(initial = emptyList())
                LineChart(data = data, lineColor = Color(0xFFE91E63))
            }
        }

        item {
            ChartSection(title = stringResource(R.string.chart_title_bmr)) { granularity ->
                val data by viewModel.getBMRChartData(granularity).collectAsState(initial = emptyList())
                LineChart(data = data, lineColor = Color(0xFF9C27B0))
            }
        }

        item {
            ChartSection(title = stringResource(R.string.chart_title_body_fat)) { granularity ->
                val data: List<ChartDataPoint> by viewModel.getBodyFatChartData(granularity).collectAsState(initial = emptyList())
                LineChart(data = data)
            }
        }

        item {
            ChartSection(title = stringResource(R.string.chart_title_skeletal_muscle)) { granularity ->
                val data: List<ChartDataPoint> by viewModel.getSkeletalMuscleChartData(granularity).collectAsState(initial = emptyList())
                LineChart(data = data)
            }
        }

        item {
            ChartSection(title = stringResource(R.string.chart_title_body_water)) { granularity ->
                val data: List<ChartDataPoint> by viewModel.getBodyWaterChartData(granularity).collectAsState(initial = emptyList())
                LineChart(data = data)
            }
        }

        item {
            ChartSection(title = stringResource(R.string.chart_title_whr)) { granularity ->
                val data: List<ChartDataPoint> by viewModel.getWHRChartData(granularity).collectAsState(initial = emptyList())
                LineChart(data = data)
            }
        }

        item { Spacer(modifier = Modifier.height(120.dp)) }
    }
}

@Composable
fun HistoryTaskCard(task: WorkoutTask) {
    GlassCard(
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (task.isUnilateral) {
                        Surface(
                            color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.7f),
                            shape = RoundedCornerShape(4.dp),
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            Text(
                                stringResource(R.string.tag_uni),
                                fontSize = 10.sp,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                    }
                    Text(
                        text = task.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    stringResource(R.string.btn_done),
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
            Spacer(modifier = Modifier.height(8.dp))

            if (task.sets.isNotEmpty()) {
                if (task.isUnilateral) {
                    task.sets.forEachIndexed { index, set ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("#${index + 1}", color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f), fontWeight = FontWeight.Bold, modifier = Modifier.width(30.dp), fontSize = 12.sp)
                            Row(modifier = Modifier.weight(1f)) {
                                Text(stringResource(R.string.label_side_l), fontSize = 12.sp, color = Color.Gray)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("${set.weightOrDuration} x ${set.reps}", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            }
                            Row(modifier = Modifier.weight(1f)) {
                                Text(stringResource(R.string.label_side_r), fontSize = 12.sp, color = Color.Gray)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("${set.rightWeight ?: "-"} x ${set.rightReps ?: "-"}", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            }
                        }
                        if (index < task.sets.size - 1) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f), modifier = Modifier.padding(vertical = 2.dp))
                        }
                    }
                } else {
                    val isStrength = task.category == "STRENGTH"
                    if (isStrength) {
                        val setsStr = task.sets.joinToString("  |  ") { set -> "${set.weightOrDuration} x ${set.reps}" }
                        Text(text = setsStr, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        task.sets.forEach { set -> Text("✅ ${set.weightOrDuration}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
            } else {
                Text(if (task.actualWeight.isNotEmpty()) "${task.target} @ ${task.actualWeight}" else task.target, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            }
        }
    }
}
