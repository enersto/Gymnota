package com.example.myfit.ui

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myfit.R
import com.example.myfit.viewmodel.MainViewModel
import java.time.LocalDate
import kotlin.math.max

// ================== 数据模型 ==================

data class ChartDataPoint(
    val date: LocalDate,
    val value: Float,
    val label: String
)

enum class ChartGranularity { DAILY, MONTHLY }

// ================== 基础图表组件 ==================

@Composable
fun GranularitySelector(
    current: ChartGranularity,
    onSelect: (ChartGranularity) -> Unit
) {
    Row(
        modifier = Modifier
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
            .padding(2.dp)
    ) {
        val options = listOf(
            stringResource(R.string.chart_granularity_day) to ChartGranularity.DAILY,
            stringResource(R.string.chart_granularity_month) to ChartGranularity.MONTHLY
        )

        options.forEach { (text, type) ->
            val isSelected = current == type
            val bgColor = if (isSelected) MaterialTheme.colorScheme.background else Color.Transparent
            val textColor = if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray

            Box(
                modifier = Modifier
                    .weight(1f)
                    .background(bgColor, RoundedCornerShape(6.dp))
                    .clickable { onSelect(type) }
                    .padding(vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(text, fontSize = 12.sp, color = textColor, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
fun LineChart(
    data: List<ChartDataPoint>,
    lineColor: Color = MaterialTheme.colorScheme.primary,
    modifier: Modifier = Modifier.height(200.dp).fillMaxWidth()
) {
    if (data.isEmpty()) {
        Box(modifier, contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.chart_no_data), color = Color.Gray, fontSize = 12.sp)
        }
        return
    }

    val textColor = MaterialTheme.colorScheme.onSurface.toArgb()

    Canvas(modifier = modifier.padding(top = 20.dp, bottom = 20.dp, start = 10.dp, end = 10.dp)) {
        val width = size.width
        val height = size.height
        val spacing = width / max(data.size - 1, 1)

        val maxValue = data.maxOf { it.value } * 1.1f
        val minValue = (data.minOf { it.value } * 0.9f).coerceAtLeast(0f)
        val yRange = max(maxValue - minValue, 1f)

        val path = Path()
        val points = data.mapIndexed { index, point ->
            val x = index * spacing
            val y = height - ((point.value - minValue) / yRange * height)
            Offset(x, y)
        }

        points.forEachIndexed { index, point ->
            if (index == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y)
        }

        drawPath(
            path = path,
            color = lineColor,
            style = Stroke(width = 3.dp.toPx())
        )

        points.forEachIndexed { index, point ->
            drawCircle(color = lineColor, radius = 4.dp.toPx(), center = point)

            if (data.size < 10 || index % (data.size / 5) == 0) {
                drawContext.canvas.nativeCanvas.drawText(
                    data[index].label,
                    point.x,
                    height + 20.dp.toPx(),
                    Paint().apply {
                        color = textColor
                        textSize = 10.sp.toPx()
                        textAlign = Paint.Align.CENTER
                    }
                )
            }
        }
    }
}

@Composable
fun BarChart(
    data: List<ChartDataPoint>,
    barColor: Color = MaterialTheme.colorScheme.secondary,
    modifier: Modifier = Modifier.height(200.dp).fillMaxWidth()
) {
    if (data.isEmpty()) {
        Box(modifier, contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.chart_no_data), color = Color.Gray, fontSize = 12.sp)
        }
        return
    }

    val textColor = MaterialTheme.colorScheme.onSurface.toArgb()

    Canvas(modifier = modifier.padding(top = 20.dp, bottom = 20.dp, start = 10.dp, end = 10.dp)) {
        val width = size.width
        val height = size.height
        val barWidth = (width / data.size) * 0.6f
        val spacing = width / data.size

        val maxValue = max(data.maxOf { it.value } * 1.1f, 1f)

        data.forEachIndexed { index, point ->
            val barHeight = (point.value / maxValue) * height
            val x = index * spacing + (spacing - barWidth) / 2
            val y = height - barHeight

            drawRect(
                color = barColor,
                topLeft = Offset(x, y),
                size = Size(barWidth, barHeight)
            )

            if (data.size < 10 || index % (data.size / 5) == 0) {
                drawContext.canvas.nativeCanvas.drawText(
                    point.label,
                    x + barWidth / 2,
                    height + 20.dp.toPx(),
                    Paint().apply {
                        color = textColor
                        textSize = 10.sp.toPx()
                        textAlign = Paint.Align.CENTER
                    }
                )
            }
        }
    }
}

// ================== 业务容器组件 (补全部分) ==================

@Composable
fun ChartSection(
    title: String,
    defaultChartType: String = "LINE",
    content: @Composable (ChartGranularity) -> Unit
) {
    var granularity by remember { mutableStateOf(ChartGranularity.DAILY) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                GranularitySelector(current = granularity, onSelect = { granularity = it })
            }
            Spacer(modifier = Modifier.height(16.dp))
            content(granularity)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SingleExerciseSection(
    viewModel: MainViewModel,
    category: String,
    title: String,
    mode: Int
) {
    val exercises by viewModel.getExerciseNamesByCategory(category).collectAsState(initial = emptyList())
    var selectedExercise by remember { mutableStateOf("") }

    // 默认选中第一个
    LaunchedEffect(exercises) {
        if (selectedExercise.isEmpty() && exercises.isNotEmpty()) {
            selectedExercise = exercises.first()
        }
    }

    if (exercises.isEmpty()) return

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))

            // 动作选择下拉框
            var expanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = !expanded }
            ) {
                OutlinedTextField(
                    value = selectedExercise,
                    onValueChange = {},
                    readOnly = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    modifier = Modifier.menuAnchor().fillMaxWidth(),
                    textStyle = MaterialTheme.typography.bodyMedium
                )
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    exercises.forEach { name ->
                        DropdownMenuItem(
                            text = { Text(name) },
                            onClick = {
                                selectedExercise = name
                                expanded = false
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 图表部分
            if (selectedExercise.isNotEmpty()) {
                var granularity by remember { mutableStateOf(ChartGranularity.DAILY) }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    GranularitySelector(current = granularity, onSelect = { granularity = it })
                }
                Spacer(modifier = Modifier.height(8.dp))

                val data by viewModel.getSingleExerciseChartData(selectedExercise, mode, granularity).collectAsState(initial = emptyList())
                LineChart(data = data)
            }
        }
    }
}