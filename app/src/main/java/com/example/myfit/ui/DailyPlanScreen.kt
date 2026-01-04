package com.example.myfit.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController // 新增引用
import com.example.myfit.model.*
import com.example.myfit.viewmodel.MainViewModel
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DailyPlanScreen(viewModel: MainViewModel, navController: NavController) { // 修改这里：增加 navController 参数
    val date by viewModel.selectedDate.collectAsState()
    val dayType by viewModel.todayScheduleType.collectAsState()
    val tasks by viewModel.todayTasks.collectAsState(initial = emptyList())
    val showWeightAlert by viewModel.showWeightAlert.collectAsState()

    val progress = if (tasks.isEmpty()) 0f else tasks.count { it.isCompleted } / tasks.size.toFloat()

    // 使用 MaterialTheme 的颜色，而不是硬编码，以支持主题切换
    val themeColor = MaterialTheme.colorScheme.primary

    var showAddSheet by remember { mutableStateOf(false) }
    var showWeightDialog by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background, // 跟随主题
        floatingActionButton = {
            if (dayType != DayType.REST) {
                FloatingActionButton(
                    onClick = { showAddSheet = true },
                    containerColor = themeColor
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Task", tint = Color.White)
                }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).padding(16.dp)) {
            HeaderSection(date, dayType, progress, themeColor, showWeightAlert) { showWeightDialog = true }
            Spacer(modifier = Modifier.height(20.dp))

            if (tasks.isEmpty()) {
                EmptyState(dayType)
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(tasks, key = { it.id }) { task ->
                        BubbleTaskItem(task, themeColor, viewModel)
                    }
                    item { Spacer(modifier = Modifier.height(80.dp)) }
                }
            }
        }
    }

    if (showAddSheet) {
        // 传递 navController 给弹窗
        AddExerciseSheet(viewModel, navController) { showAddSheet = false }
    }

    if (showWeightDialog) {
        WeightDialog(viewModel) { showWeightDialog = false }
    }
}

@Composable
fun HeaderSection(
    date: LocalDate,
    dayType: DayType,
    progress: Float,
    color: Color,
    showAlert: Boolean,
    onWeightClick: () -> Unit
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "${date.monthValue}月${date.dayOfMonth}日",
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.weight(1f))
            if (showAlert) {
                Button(
                    onClick = onWeightClick,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9800)),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Icon(Icons.Default.MonitorWeight, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("记录体重", fontSize = 12.sp)
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = dayType.label,
            color = color,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(16.dp))
        LinearProgressIndicator(
            progress = progress,
            modifier = Modifier.fillMaxWidth().height(12.dp).clip(RoundedCornerShape(6.dp)),
            color = color,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
    }
}

@Composable
fun BubbleTaskItem(task: WorkoutTask, themeColor: Color, viewModel: MainViewModel) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded },
        colors = CardDefaults.cardColors(
            containerColor = if (task.isCompleted) themeColor.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(16.dp),
        border = if (task.isCompleted) BorderStroke(1.dp, themeColor) else null
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(if (task.isCompleted) themeColor else Color.Transparent)
                        .border(2.dp, if (task.isCompleted) themeColor else Color.Gray, CircleShape)
                        .clickable { viewModel.updateTask(task.copy(isCompleted = !task.isCompleted)) },
                    contentAlignment = Alignment.Center
                ) {
                    if (task.isCompleted) {
                        Icon(Icons.Default.Close, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(16.dp))
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = task.name,
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.titleMedium,
                        textDecoration = if (task.isCompleted) androidx.compose.ui.text.style.TextDecoration.LineThrough else null
                    )
                    if (!expanded) {
                        Text(
                            text = if (task.actualWeight.isNotEmpty()) "${task.target} @ ${task.actualWeight}kg" else task.target,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                if (expanded) {
                    IconButton(onClick = { viewModel.removeTask(task) }) {
                        Icon(Icons.Default.Close, contentDescription = "Delete", tint = Color.Gray)
                    }
                }
            }
            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("目标: ", color = Color.Gray, fontSize = 14.sp)
                        BasicTextField(
                            value = task.target,
                            onValueChange = { viewModel.updateTask(task.copy(target = it)) },
                            textStyle = TextStyle(color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp),
                            cursorBrush = SolidColor(themeColor),
                            modifier = Modifier.weight(1f).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp)).padding(8.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    if (task.type == "STRENGTH") {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("实测: ", color = Color.Gray, fontSize = 14.sp)
                            BasicTextField(
                                value = task.actualWeight,
                                onValueChange = { viewModel.updateTask(task.copy(actualWeight = it)) },
                                textStyle = TextStyle(color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp),
                                cursorBrush = SolidColor(themeColor),
                                modifier = Modifier.width(80.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp)).padding(8.dp),
                                decorationBox = { inner ->
                                    if (task.actualWeight.isEmpty()) Text("kg", color = Color.Gray)
                                    inner()
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddExerciseSheet(viewModel: MainViewModel, navController: NavController, onDismiss: () -> Unit) {
    val templates by viewModel.allTemplates.collectAsState(initial = emptyList())

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("添加动作到今日", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface)

            // --- V3.0 新增：快捷跳转到动作管理 ---
            Button(
                onClick = {
                    onDismiss() // 先关弹窗
                    navController.navigate("exercise_manager") // 再跳转
                },
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Default.List, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                Spacer(modifier = Modifier.width(8.dp))
                Text("新建 / 管理动作库", color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            // ------------------------------------

            Divider(color = Color.Gray.copy(alpha = 0.3f), modifier = Modifier.padding(vertical = 8.dp))

            LazyColumn {
                items(templates) { template ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                viewModel.addTaskFromTemplate(template)
                                onDismiss()
                            }
                            .padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(template.name, color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp)
                        Text(template.category, color = Color.Gray, fontSize = 12.sp)
                    }
                    Divider(color = MaterialTheme.colorScheme.surfaceVariant)
                }
            }
            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

@Composable
fun WeightDialog(viewModel: MainViewModel, onDismiss: () -> Unit) {
    var weightInput by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("记录体重") },
        text = {
            OutlinedTextField(
                value = weightInput,
                onValueChange = { weightInput = it },
                label = { Text("KG") },
                singleLine = true
            )
        },
        confirmButton = {
            Button(onClick = {
                weightInput.toFloatOrNull()?.let { viewModel.logWeight(it) }
                onDismiss()
            }) { Text("保存") }
        }
    )
}

@Composable
fun EmptyState(dayType: DayType) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (dayType == DayType.REST) {
            Text("💤 休息日\n好好恢复，无需训练", color = Color.Gray, style = MaterialTheme.typography.bodyLarge, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        } else {
            Text("点击右下角 + 号\n从库中选择今天的训练内容", color = Color.Gray, style = MaterialTheme.typography.bodyLarge, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
    }
}