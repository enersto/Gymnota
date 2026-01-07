package com.example.myfit.ui

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
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.myfit.R
import com.example.myfit.viewmodel.MainViewModel
import java.time.LocalDate

enum class ChartGranularity { DAILY, MONTHLY }
data class ChartDataPoint(val date: LocalDate, val value: Float, val label: String)

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
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Row(
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                        .padding(4.dp)
                ) {
                    GranularityButton(
                        stringResource(R.string.chart_granularity_day),
                        granularity == ChartGranularity.DAILY
                    ) { granularity = ChartGranularity.DAILY }

                    GranularityButton(
                        stringResource(R.string.chart_granularity_month),
                        granularity == ChartGranularity.MONTHLY
                    ) { granularity = ChartGranularity.MONTHLY }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Box(modifier = Modifier.height(220.dp).fillMaxWidth()) { // 稍微增加高度以容纳数值标签
                content(granularity)
            }
        }
    }
}

@Composable
fun GranularityButton(text: String, isSelected: Boolean, onClick: () -> Unit) {
    Text(
        text = text,
        modifier = Modifier
            .clickable { onClick() }
            .background(
                if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                RoundedCornerShape(4.dp)
            )
            .padding(horizontal = 12.dp, vertical = 6.dp),
        color = if (isSelected) Color.White else Color.Gray,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold
    )
}

@Composable
fun SingleExerciseSection(
    viewModel: MainViewModel,
    category: String,
    title: String,
    mode: Int
) {
    var selectedExercise by remember { mutableStateOf("") }
    val exercises by viewModel.getExerciseNamesByCategory(category).collectAsStateWithLifecycle(initialValue = emptyList())

    if (exercises.isNotEmpty()) {
        if (selectedExercise.isEmpty()) selectedExercise = exercises.first()

        Column {
            ChartSection(title = title) { granularity ->
                val data by viewModel.getSingleExerciseChartData(selectedExercise, mode, granularity).collectAsStateWithLifecycle(initialValue = emptyList())
                LineChart(data = data)
            }
            Spacer(modifier = Modifier.height(8.dp))
            androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(exercises.size) { index ->
                    val name = exercises[index]
                    FilterChip(
                        selected = name == selectedExercise,
                        onClick = { selectedExercise = name },
                        label = { Text(name) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    )
                }
            }
        }
    }
}

@Composable
fun LineChart(
    data: List<ChartDataPoint>,
    lineColor: Color = MaterialTheme.colorScheme.primary,
    modifier: Modifier = Modifier
) {
    if (data.isEmpty()) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.chart_no_data), color = Color.Gray, fontSize = 12.sp)
        }
        return
    }

    val yValues = data.map { it.value }
    val maxVal = (yValues.maxOrNull() ?: 100f) * 1.2f // 增加顶部留白
    val minVal = (yValues.minOrNull() ?: 0f) * 0.8f
    val yRange = if (maxVal - minVal == 0f) 1f else maxVal - minVal

    // 文字画笔
    val textPaint = android.graphics.Paint().apply {
        color = android.graphics.Color.GRAY
        textSize = 28f
        textAlign = android.graphics.Paint.Align.CENTER
    }
    val valuePaint = android.graphics.Paint().apply {
        color = lineColor.toArgb()
        textSize = 32f
        textAlign = android.graphics.Paint.Align.CENTER
        isFakeBoldText = true
    }

    Canvas(modifier = modifier.fillMaxSize().padding(start = 10.dp, end = 10.dp, top = 20.dp, bottom = 20.dp)) {
        val width = size.width
        val height = size.height
        val pointSpacing = if (data.size > 1) width / (data.size - 1) else 0f

        // 绘制坐标轴
        drawLine(Color.LightGray.copy(alpha = 0.5f), Offset(0f, height), Offset(width, height), 2f)

        // 绘制折线
        if (data.size > 1) {
            for (i in 0 until data.size - 1) {
                val x1 = i * pointSpacing
                val y1 = height - ((data[i].value - minVal) / yRange) * height
                val x2 = (i + 1) * pointSpacing
                val y2 = height - ((data[i + 1].value - minVal) / yRange) * height

                drawLine(
                    start = Offset(x1, y1),
                    end = Offset(x2, y2),
                    color = lineColor,
                    strokeWidth = 5f
                )
            }
        }

        // 绘制点和数值
        for (i in data.indices) {
            val x = i * pointSpacing
            val y = height - ((data[i].value - minVal) / yRange) * height

            // 绘制点
            drawCircle(color = Color.White, radius = 10f, center = Offset(x, y))
            drawCircle(color = lineColor, radius = 7f, center = Offset(x, y))

            // 绘制数值 (如果点不多，或者只绘制极值)
            if (data.size < 15 || i == 0 || i == data.lastIndex || data[i].value == yValues.maxOrNull()) {
                drawContext.canvas.nativeCanvas.drawText(
                    String.format("%.1f", data[i].value),
                    x,
                    y - 20f,
                    valuePaint
                )
            }

            // 绘制日期标签 (间隔绘制，避免拥挤)
            if (data.size < 8 || i % (data.size / 5) == 0) {
                drawContext.canvas.nativeCanvas.drawText(
                    data[i].label,
                    x,
                    height + 40f,
                    textPaint
                )
            }
        }
    }
}

@Composable
fun BarChart(
    data: List<ChartDataPoint>,
    barColor: Color = MaterialTheme.colorScheme.secondary,
    modifier: Modifier = Modifier
) {
    if (data.isEmpty()) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.chart_no_data), color = Color.Gray, fontSize = 12.sp)
        }
        return
    }

    val maxVal = (data.maxOfOrNull { it.value } ?: 100f) * 1.2f

    val textPaint = android.graphics.Paint().apply {
        color = android.graphics.Color.GRAY
        textSize = 28f
        textAlign = android.graphics.Paint.Align.CENTER
    }
    val valuePaint = android.graphics.Paint().apply {
        color = barColor.toArgb()
        textSize = 28f
        textAlign = android.graphics.Paint.Align.CENTER
    }

    Canvas(modifier = modifier.fillMaxSize().padding(start = 10.dp, end = 10.dp, top = 20.dp, bottom = 20.dp)) {
        val width = size.width
        val height = size.height
        val barWidth = (width / data.size) * 0.5f
        val spacing = width / data.size

        drawLine(Color.LightGray.copy(alpha = 0.5f), Offset(0f, height), Offset(width, height), 2f)

        for (i in data.indices) {
            val barHeight = (data[i].value / maxVal) * height
            val x = i * spacing + (spacing / 2) // 居中
            val y = height - barHeight

            // 绘制柱子
            drawRect(
                color = barColor,
                topLeft = Offset(x - barWidth/2, y),
                size = Size(barWidth, barHeight)
            )

            // 绘制数值
            if (data[i].value > 0) {
                drawContext.canvas.nativeCanvas.drawText(
                    String.format("%.0f", data[i].value),
                    x,
                    y - 10f,
                    valuePaint
                )
            }

            // 绘制日期
            if (data.size < 8 || i % (data.size / 5) == 0) {
                drawContext.canvas.nativeCanvas.drawText(
                    data[i].label,
                    x,
                    height + 40f,
                    textPaint
                )
            }
        }
    }
}