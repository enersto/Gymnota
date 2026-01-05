package com.example.myfit.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource // 关键引用
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.myfit.R // 关键引用
import com.example.myfit.model.*
import com.example.myfit.viewmodel.MainViewModel
import kotlinx.coroutines.delay
import java.time.LocalDate
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.PI
import kotlin.random.Random

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DailyPlanScreen(viewModel: MainViewModel, navController: NavController) {
    val date by viewModel.selectedDate.collectAsState()
    val dayType by viewModel.todayScheduleType.collectAsState()
    val tasks by viewModel.todayTasks.collectAsState(initial = emptyList())
    val showWeightAlert by viewModel.showWeightAlert.collectAsState()
    val progress = if (tasks.isEmpty()) 0f else tasks.count { it.isCompleted } / tasks.size.toFloat()
    val themeColor = MaterialTheme.colorScheme.primary

    var showAddSheet by remember { mutableStateOf(false) }
    var showWeightDialog by remember { mutableStateOf(false) }
    var showExplosion by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            floatingActionButton = {
                if (dayType != DayType.REST) {
                    FloatingActionButton(onClick = { showAddSheet = true }, containerColor = themeColor) {
                        Icon(Icons.Default.Add, contentDescription = "Add", tint = Color.White)
                    }
                }
            }
        ) { padding ->
            Column(modifier = Modifier.padding(padding).padding(16.dp)) {
                HeaderSection(date, dayType, progress, themeColor, showWeightAlert) { showWeightDialog = true }
                Spacer(modifier = Modifier.height(20.dp))

                if (tasks.isEmpty()) {
                    EmptyState(dayType) { viewModel.applyWeeklyRoutineToToday() }
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(tasks, key = { it.id }) { task ->
                            SwipeToDeleteContainer(item = task, onDelete = { viewModel.removeTask(task) }) {
                                BubbleTaskItem(task, themeColor, viewModel) { showExplosion = true }
                            }
                        }
                        item { Spacer(modifier = Modifier.height(80.dp)) }
                    }
                }
            }
        }

        if (showExplosion) ExplosionEffect { showExplosion = false }
        if (showAddSheet) AddExerciseSheet(viewModel, navController) { showAddSheet = false }
        if (showWeightDialog) WeightDialog(viewModel) { showWeightDialog = false }
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
                text = "${date.monthValue} / ${date.dayOfMonth}",
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
                    // V4.3: 使用资源引用
                    Text(stringResource(R.string.log_weight), fontSize = 12.sp)
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        // ▼▼▼ 修复点：这里原来是 dayType.label，现在改为 stringResource(dayType.labelResId) ▼▼▼
        Text(
            text = stringResource(dayType.labelResId),
            color = color,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        // ▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲
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
fun EmptyState(dayType: DayType, onApplyRoutine: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (dayType == DayType.REST) {
                Text(stringResource(R.string.type_rest), color = Color.Gray, style = MaterialTheme.typography.bodyLarge)
            } else {
                Text(stringResource(R.string.no_plan), color = Color.Gray, style = MaterialTheme.typography.bodyLarge)
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = onApplyRoutine) { Text(stringResource(R.string.apply_routine)) }
                Spacer(modifier = Modifier.height(8.dp))
                Text(stringResource(R.string.click_add), color = Color.Gray, style = MaterialTheme.typography.bodySmall)
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
            Text(stringResource(R.string.add_to_today), style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface)

            Button(
                onClick = {
                    onDismiss()
                    navController.navigate("exercise_manager")
                },
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Default.List, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.new_manage_lib), color = MaterialTheme.colorScheme.onPrimaryContainer)
            }

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
        title = { Text(stringResource(R.string.dialog_weight_title)) },
        text = {
            OutlinedTextField(value = weightInput, onValueChange = { weightInput = it }, label = { Text("KG") }, singleLine = true)
        },
        confirmButton = {
            Button(onClick = { weightInput.toFloatOrNull()?.let { viewModel.logWeight(it) }; onDismiss() }) { Text(stringResource(R.string.btn_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.btn_cancel)) } }
    )
}

// 保持 BubbleTaskItem, PillCheckButton, SwipeToDeleteContainer, ExplosionEffect 不变
// (但建议将 BubbleTaskItem 里的 "已完成" "打卡" 也替换为 stringResource)
@Composable
fun BubbleTaskItem(task: WorkoutTask, themeColor: Color, viewModel: MainViewModel, onComplete: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val isCompleted = task.isCompleted
    val cardBgColor = if (isCompleted) Color(0xFFF0F0F0) else MaterialTheme.colorScheme.surface
    val contentAlpha = if (isCompleted) 0.5f else 1f

    Card(
        modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded },
        colors = CardDefaults.cardColors(containerColor = cardBgColor),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isCompleted) 0.dp else 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = task.name, color = MaterialTheme.colorScheme.onSurface.copy(alpha = contentAlpha), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, textDecoration = if (isCompleted) androidx.compose.ui.text.style.TextDecoration.LineThrough else null)
                    if (!expanded) {
                        Text(text = if (task.actualWeight.isNotEmpty()) "${task.target} @ ${task.actualWeight}" else task.target, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f * contentAlpha), style = MaterialTheme.typography.bodySmall)
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                PillCheckButton(isCompleted = isCompleted, color = themeColor, onClick = {
                    val newState = !task.isCompleted
                    viewModel.updateTask(task.copy(isCompleted = newState))
                    if (newState) onComplete()
                })
            }
            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Target", color = Color.Gray, fontSize = 12.sp, modifier = Modifier.width(40.dp))
                        BasicTextField(value = task.target, onValueChange = { viewModel.updateTask(task.copy(target = it)) }, textStyle = TextStyle(color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp), cursorBrush = SolidColor(themeColor), modifier = Modifier.weight(1f).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp)).padding(8.dp))
                    }
                    if (task.type == "STRENGTH") {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Actual", color = Color.Gray, fontSize = 12.sp, modifier = Modifier.width(40.dp))
                            BasicTextField(value = task.actualWeight, onValueChange = { viewModel.updateTask(task.copy(actualWeight = it)) }, textStyle = TextStyle(color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp), cursorBrush = SolidColor(themeColor), modifier = Modifier.weight(1f).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp)).padding(8.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PillCheckButton(isCompleted: Boolean, color: Color, onClick: () -> Unit) {
    val scale by animateFloatAsState(if (isCompleted) 0.95f else 1f)
    Surface(onClick = onClick, modifier = Modifier.height(36.dp).scale(scale), shape = RoundedCornerShape(50), color = if (isCompleted) Color.LightGray else color, contentColor = Color.White) {
        Box(modifier = Modifier.padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
            // V4.3: 使用资源引用
            if (isCompleted) Text(stringResource(R.string.btn_done), fontSize = 12.sp, fontWeight = FontWeight.Bold)
            else Text(stringResource(R.string.btn_check), fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}

// SwipeToDeleteContainer, ExplosionEffect 保持不变，直接复制即可
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> SwipeToDeleteContainer(
    item: T,
    onDelete: (T) -> Unit,
    content: @Composable () -> Unit
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = {
            if (it == SwipeToDismissBoxValue.EndToStart) {
                onDelete(item)
                true
            } else false
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            val color = Color.Red
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(color, RoundedCornerShape(16.dp))
                    .padding(end = 24.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.White)
            }
        },
        content = { content() }
    )
}

@Composable
fun ExplosionEffect(onDismiss: () -> Unit) {
    val particles = remember { List(20) { Particle() } }
    var visible by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        delay(1000)
        visible = false
        onDismiss()
    }
    if (visible) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("🎉", fontSize = 100.sp)
            Canvas(modifier = Modifier.fillMaxSize()) {
                val center = this.center
                particles.forEach { p ->
                    val x = center.x + p.radius * cos(p.angle)
                    val y = center.y + p.radius * sin(p.angle)
                    drawCircle(color = p.color, radius = 8f, center = Offset(x.toFloat(), y.toFloat()))
                    p.update()
                }
            }
        }
    }
}
class Particle {
    var angle = Random.nextDouble(0.0, 2 * PI)
    var radius = 0.0
    var speed = Random.nextDouble(10.0, 30.0)
    val color = listOf(Color.Red, Color.Yellow, Color.Blue, Color.Green).random()
    fun update() { radius += speed }
}