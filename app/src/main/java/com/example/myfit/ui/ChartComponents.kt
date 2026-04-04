package com.example.myfit.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
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

import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.OutlinedTextField

import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.MenuAnchorType

import com.example.myfit.model.LogType
import com.example.myfit.ui.components.GlassCard
import com.example.myfit.ui.components.GlassChoiceChip
import com.kyant.shapes.Capsule
import androidx.compose.ui.draw.clip

enum class ChartGranularity { DAILY, MONTHLY }
data class ChartDataPoint(val date: LocalDate, val value: Float, val label: String)

@Composable
fun ChartSection(
    title: String,
    defaultChartType: String = "LINE",
    content: @Composable (ChartGranularity) -> Unit
) {
    var granularity by remember { mutableStateOf(ChartGranularity.DAILY) }

    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f).padding(end = 8.dp)
                )

                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    shape = Capsule(),
                    modifier = Modifier.height(32.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
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
            }
            Spacer(modifier = Modifier.height(16.dp))
            Box(modifier = Modifier.height(220.dp).fillMaxWidth()) {
                content(granularity)
            }
        }
    }
}

@Composable
fun GranularityButton(text: String, isSelected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxHeight()
            .clip(Capsule())
            .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent)
            .clickable { onClick() }
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SingleExerciseSection(
    viewModel: MainViewModel,
    category: String,
    title: String,
    defaultMode: Int = 1
) {
    var selectedExercise by remember { mutableStateOf("") }
    var selectedSide by remember { mutableStateOf(0) }

    val exercises by viewModel.getExerciseNamesByCategory(category).collectAsStateWithLifecycle(initialValue = emptyList())
    val history by viewModel.historyRecords.collectAsStateWithLifecycle(initialValue = emptyList())

    val isUnilateral by remember(selectedExercise, history) {
        derivedStateOf {
            if (selectedExercise.isEmpty()) false
            else history.any { it.name == selectedExercise && it.isUnilateral }
        }
    }

    val logType by remember(selectedExercise) {
        viewModel.getLogTypeForExercise(selectedExercise)
    }.collectAsStateWithLifecycle(initialValue = LogType.WEIGHT_REPS.value)

    var expanded by remember { mutableStateOf(false) }
    var searchText by remember { mutableStateOf("") }
    var isUserInput by remember { mutableStateOf(false) }

    LaunchedEffect(exercises) {
        if (selectedExercise.isEmpty() && exercises.isNotEmpty()) {
            selectedExercise = exercises.first()
            searchText = exercises.first()
            isUserInput = false
        }
    }

    val filteredOptions = remember(exercises, searchText, expanded, isUserInput) {
        if (!expanded) exercises
        else {
            if (isUserInput && searchText.isNotEmpty()) {
                exercises.filter { it.contains(searchText, ignoreCase = true) }
            } else {
                exercises
            }
        }
    }

    if (exercises.isNotEmpty()) {
        Column {
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = !expanded }
            ) {
                OutlinedTextField(
                    value = searchText,
                    onValueChange = {
                        searchText = it
                        expanded = true
                        isUserInput = true
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable, true)
                        .onFocusChanged { if (it.isFocused) { isUserInput = false; expanded = true } },
                    label = { Text(stringResource(R.string.title_select_exercise)) },
                    trailingIcon = {
                        if (searchText.isNotEmpty() && expanded) {
                            IconButton(onClick = { searchText = ""; isUserInput = true; expanded = true }) {
                                Icon(Icons.Default.Clear, contentDescription = null)
                            }
                        } else {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                        }
                    },
                    shape = RoundedCornerShape(16.dp),
                    colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                    singleLine = true
                )

                if (filteredOptions.isNotEmpty()) {
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false },
                        modifier = Modifier.heightIn(max = 300.dp)
                    ) {
                        filteredOptions.forEach { selectionOption ->
                            DropdownMenuItem(
                                text = {
                                    val isSelected = selectionOption == selectedExercise
                                    Text(
                                        text = selectionOption,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                    )
                                },
                                onClick = {
                                    selectedExercise = selectionOption
                                    searchText = selectionOption
                                    isUserInput = false
                                    expanded = false
                                    selectedSide = 0
                                },
                                contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (category == "STRENGTH" && isUnilateral) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
                    GlassChoiceChip(
                        text = stringResource(R.string.label_side_left),
                        selected = selectedSide == 0,
                        onClick = { selectedSide = 0 }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    GlassChoiceChip(
                        text = stringResource(R.string.label_side_right),
                        selected = selectedSide == 1,
                        onClick = { selectedSide = 1 }
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            val effectiveMode = when (logType) {
                LogType.DURATION.value -> 0
                LogType.REPS_ONLY.value -> 2
                else -> if (selectedSide == 1) 3 else 1
            }

            ChartSection(title = title) { granularity ->
                val data by viewModel.getSingleExerciseChartData(selectedExercise, effectiveMode, granularity).collectAsStateWithLifecycle(initialValue = emptyList())
                LineChart(data = data)
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
    val maxVal = (yValues.maxOrNull() ?: 100f).let { if (it == 0f) 100f else it } * 1.2f
    val minVal = 0f
    val yRange = maxVal - minVal

    val textPaint = android.graphics.Paint().apply {
        color = android.graphics.Color.GRAY
        textSize = 28f
        textAlign = android.graphics.Paint.Align.CENTER
        alpha = 180
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

        drawLine(Color.Gray.copy(alpha = 0.2f), Offset(0f, height), Offset(width, height), 1f)

        if (data.size > 1) {
            for (i in 0 until data.size - 1) {
                val x1 = i * pointSpacing
                val y1 = height - ((data[i].value - minVal) / yRange) * height
                val x2 = (i + 1) * pointSpacing
                val y2 = height - ((data[i + 1].value - minVal) / yRange) * height

                drawLine(
                    start = Offset(x1, y1),
                    end = Offset(x2, y2),
                    color = lineColor.copy(alpha = 0.8f),
                    strokeWidth = 6f
                )
            }
        }

        for (i in data.indices) {
            val x = i * pointSpacing
            val y = height - ((data[i].value - minVal) / yRange) * height

            drawCircle(color = Color.White, radius = 10f, center = Offset(x, y))
            drawCircle(color = lineColor, radius = 7f, center = Offset(x, y))

            val showValue = data.size < 15 || i == 0 || i == data.lastIndex || data[i].value == (yValues.maxOrNull() ?: 0f)

            if (showValue && data[i].value > 0) {
                drawContext.canvas.nativeCanvas.drawText(
                    String.format("%.1f", data[i].value),
                    x,
                    y - 25f,
                    valuePaint
                )
            }

            if (data.size < 8 || i % (data.indices.last.coerceAtLeast(1) / 4).coerceAtLeast(1) == 0) {
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

    val maxVal = (data.maxOfOrNull { it.value } ?: 100f).let { if (it == 0f) 100f else it } * 1.2f

    val textPaint = android.graphics.Paint().apply {
        color = android.graphics.Color.GRAY
        textSize = 28f
        textAlign = android.graphics.Paint.Align.CENTER
        alpha = 180
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

        drawLine(Color.Gray.copy(alpha = 0.2f), Offset(0f, height), Offset(width, height), 1f)

        for (i in data.indices) {
            val barHeight = (data[i].value / maxVal) * height
            val x = i * spacing + (spacing / 2)
            val y = height - barHeight

            drawRect(
                color = barColor.copy(alpha = 0.7f),
                topLeft = Offset(x - barWidth/2, y),
                size = Size(barWidth, barHeight)
            )

            if (data[i].value > 0) {
                drawContext.canvas.nativeCanvas.drawText(
                    String.format("%.0f", data[i].value),
                    x,
                    y - 15f,
                    valuePaint
                )
            }

            if (data.size < 8 || i % (data.indices.last.coerceAtLeast(1) / 4).coerceAtLeast(1) == 0) {
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
fun PixelBodyHeatmap(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val heatMap by viewModel.muscleHeatMapData.collectAsStateWithLifecycle(initialValue = emptyMap())
    var selectedPartInfo by remember { mutableStateOf<Pair<String, String>?>(null) }

    fun getColorForIntensity(intensity: Float): Color {
        return when {
            intensity <= 0f -> Color.Gray.copy(alpha = 0.15f)
            intensity < 0.5f -> Color(0xFF81C784).copy(alpha = 0.8f)
            intensity < 0.8f -> Color(0xFFFFB74D).copy(alpha = 0.8f)
            else -> Color(0xFFE57373).copy(alpha = 0.8f)
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(R.string.chart_title_heatmap),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(4.dp))
        val infoText = selectedPartInfo?.let { (name, value) -> "$name: $value" } ?: ""
        Text(
            text = infoText,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.secondary,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.height(20.dp)
        )

        Spacer(modifier = Modifier.height(12.dp))

        val blockSize = 24.dp
        val gap = 6.dp

        val gridRows = listOf(
            listOf(null, null, "decoration_head", null, null),
            listOf(null, "part_shoulders", "part_chest", "part_shoulders", null),
            listOf("part_arms", null, "part_back", null, "part_arms"),
            listOf("part_arms", null, "part_abs", null, "part_arms"),
            listOf(null, "part_hips", "part_hips", "part_hips", null),
            listOf(null, "part_thighs", null, "part_thighs", null),
            listOf(null, "part_thighs", null, "part_thighs", null),
            listOf(null, "part_calves", null, "part_calves", null)
        )

        Column(verticalArrangement = Arrangement.spacedBy(gap)) {
            gridRows.forEach { rowParts ->
                Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                    rowParts.forEach { partKey ->
                        if (partKey == null) {
                            Spacer(modifier = Modifier.size(blockSize))
                        } else {
                            val data = heatMap[partKey]
                            val intensity = data?.intensity ?: 0f
                            val rawValue = data?.volume ?: 0f
                            val isDecoration = partKey.startsWith("decoration")
                            
                            val labelRes = getBodyPartResId(partKey)
                            val label = if (labelRes != 0) stringResource(labelRes) else partKey

                            Box(
                                modifier = Modifier
                                    .size(blockSize)
                                    .background(if (isDecoration) Color.Gray.copy(alpha = 0.2f) else getColorForIntensity(intensity), RoundedCornerShape(6.dp))
                                    .clickable(enabled = !isDecoration) {
                                        selectedPartInfo = label to String.format("%,.0f kg", rawValue)
                                    }
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(12.dp).background(Color.Gray.copy(alpha = 0.15f), RoundedCornerShape(3.dp)))
            Text(" 0 ", fontSize = 10.sp, color = Color.Gray)
            Spacer(modifier = Modifier.width(12.dp))
            Box(Modifier.size(12.dp).background(Color(0xFF81C784).copy(alpha = 0.8f), RoundedCornerShape(3.dp)))
            Text(" Low ", fontSize = 10.sp, color = Color.Gray)
            Spacer(modifier = Modifier.width(12.dp))
            Box(Modifier.size(12.dp).background(Color(0xFFE57373).copy(alpha = 0.8f), RoundedCornerShape(3.dp)))
            Text(" Max ", fontSize = 10.sp, color = Color.Gray)
        }
    }
}
