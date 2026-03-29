package com.example.myfit.ui

import android.Manifest
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import coil.compose.AsyncImage
import com.example.myfit.R
import com.example.myfit.model.*
import com.example.myfit.ui.components.GlassButton
import com.example.myfit.ui.components.GlassCard
import com.example.myfit.ui.components.GlassScaffoldContent
import com.example.myfit.ui.components.LocalGlassMode
import com.example.myfit.util.NotificationHelper
import com.example.myfit.viewmodel.MainViewModel
import com.example.myfit.viewmodel.TimerPhase
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.math.*
import kotlin.random.Random

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DailyPlanScreen(viewModel: MainViewModel, navController: NavController) {
    val date by viewModel.selectedDate.collectAsState()
    val dayType by viewModel.todayScheduleType.collectAsState()
    val tasks by viewModel.todayTasks.collectAsState(initial = emptyList<WorkoutTask>())
    val allTemplates by viewModel.allTemplates.collectAsState(initial = emptyList())
    val showWeightAlert by viewModel.showWeightAlert.collectAsState()
    val timerState by viewModel.timerState.collectAsStateWithLifecycle()
    val hasShownGuide by viewModel.hasShownLockScreenGuide.collectAsState()
    val progress = if (tasks.isEmpty()) 0f else tasks.count { it.isCompleted } / tasks.size.toFloat()
    val themeColor = MaterialTheme.colorScheme.primary

    var showAddSheet by remember { mutableStateOf(false) }
    var showWeightDialog by remember { mutableStateOf(false) }
    var showExplosion by remember { mutableStateOf(false) }
    var showLockScreenSetupDialog by remember { mutableStateOf(false) }

    val glassMode = LocalGlassMode.current
    val useGlassTabs = glassMode && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted ->
            if (isGranted && !hasShownGuide) showLockScreenSetupDialog = true
            else if (!isGranted) Toast.makeText(context, "Notification permission required", Toast.LENGTH_SHORT).show()
        }
    )

    GlassScaffoldContent {
        Scaffold(
            containerColor = Color.Transparent,
            floatingActionButton = {
                if (dayType != DayType.REST) {
                    FloatingActionButton(
                        onClick = { navController.navigate("exercise_selector") },
                        containerColor = themeColor,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.padding(bottom = if (useGlassTabs) 100.dp else 0.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Add", tint = Color.White)
                    }
                }
            }
        ) { padding ->
            Column(modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .statusBarsPadding()) {

                HeaderSection(
                    date = date,
                    dayType = dayType,
                    progress = progress,
                    color = themeColor,
                    showWeightAlert = showWeightAlert,
                    onWeightClick = { showWeightDialog = true },
                    onTypeClick = {
                        navController.navigate("settings?scrollToType=true") {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )

                Spacer(modifier = Modifier.height(24.dp))

                if (tasks.isEmpty()) {
                    EmptyState(dayType) { viewModel.applyWeeklyRoutineToToday() }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(tasks, key = { it.id }) { task ->
                            SwipeToDeleteContainer(item = task, onDelete = { viewModel.removeTask(task) }) {
                                AdvancedTaskItem(
                                    task = task,
                                    allTemplates = allTemplates,
                                    themeColor = themeColor,
                                    viewModel = viewModel,
                                    timerState = timerState,
                                    onComplete = { showExplosion = true },
                                    onRequestPermission = {
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                        }
                                    }
                                )
                            }
                        }
                        item { Spacer(modifier = Modifier.height(if (useGlassTabs) 120.dp else 24.dp)) }
                    }
                }
            }
        }

        if (timerState.isRunning && timerState.showBigAlert) CountdownOverlay(timerState)
        if (showExplosion) ExplosionEffect { showExplosion = false }
        if (showAddSheet) AddExerciseSheet(viewModel, navController) { showAddSheet = false }
        if (showWeightDialog) WeightDialog(viewModel) { showWeightDialog = false }

        if (showLockScreenSetupDialog) {
            AlertDialog(
                onDismissRequest = { viewModel.markLockScreenGuideShown(); showLockScreenSetupDialog = false },
                title = { Text(stringResource(R.string.dialog_lock_screen_title)) },
                text = { Text(stringResource(R.string.dialog_lock_screen_content)) },
                confirmButton = {
                    Button(onClick = {
                        viewModel.markLockScreenGuideShown()
                        NotificationHelper.openNotificationSettings(context)
                        showLockScreenSetupDialog = false
                    }) { Text(stringResource(R.string.btn_go_to_settings)) }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.markLockScreenGuideShown(); showLockScreenSetupDialog = false }) {
                        Text(stringResource(R.string.btn_later))
                    }
                }
            )
        }
    }
}

@Composable
fun AdvancedTaskItem(
    task: WorkoutTask,
    allTemplates: List<ExerciseTemplate>,
    themeColor: Color,
    viewModel: MainViewModel,
    timerState: MainViewModel.TimerState,
    onComplete: () -> Unit,
    onRequestPermission: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var showDetailInfo by remember { mutableStateOf(false) }
    val isCompleted = task.isCompleted

    val bodyPartRes = getBodyPartResId(task.bodyPart)
    val bodyPartLabel = if (bodyPartRes != 0) stringResource(bodyPartRes) else task.bodyPart
    val equipRes = getEquipmentResId(task.equipment)
    val equipLabel = if (equipRes != 0) stringResource(equipRes) else task.equipment
    val context = LocalContext.current

    if (showDetailInfo) {
        val liveTemplate = allTemplates.find { it.id == task.templateId }
        val displayTemplate = liveTemplate ?: ExerciseTemplate(
            id = task.templateId, name = task.name, category = task.category,
            bodyPart = task.bodyPart, equipment = task.equipment,
            isUnilateral = task.isUnilateral, logType = task.logType,
            instruction = "", imageUri = task.imageUri, defaultTarget = task.target
        )
        ExerciseDetailDialog(template = displayTemplate, onDismiss = { showDetailInfo = false }, onEdit = null)
    }

    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded },
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val liveImageUri = allTemplates.find { it.id == task.templateId }?.imageUri ?: task.imageUri
                if (!liveImageUri.isNullOrBlank()) {
                    AsyncImage(
                        model = if (liveImageUri.startsWith("/")) File(liveImageUri) else liveImageUri,
                        contentDescription = null,
                        modifier = Modifier
                            .size(52.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color.LightGray.copy(alpha = 0.2f))
                            .clickable(enabled = expanded) { showDetailInfo = true },
                        contentScale = ContentScale.Crop
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = task.name,
                        color = if (isCompleted) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        textDecoration = if (isCompleted) androidx.compose.ui.text.style.TextDecoration.LineThrough else null,
                        modifier = Modifier.clickable(enabled = expanded) { showDetailInfo = true }
                    )
                    Row(modifier = Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (task.isUnilateral) {
                            Surface(color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.7f), shape = RoundedCornerShape(4.dp)) {
                                Text(stringResource(R.string.label_unilateral_mode), fontSize = 9.sp, modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp), color = MaterialTheme.colorScheme.onTertiaryContainer)
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                        }
                        Text(text = "$bodyPartLabel | $equipLabel", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                    }
                }
                
                PillCheckButton(isCompleted = isCompleted, color = themeColor, onClick = {
                    val newState = !task.isCompleted
                    var updatedTask = task.copy(isCompleted = newState)
                    val isAutoFill = task.logType == LogType.DURATION.value || task.logType == LogType.REPS_ONLY.value || task.category.contains("有氧") || task.category.contains("核心")
                    if (newState && isAutoFill) {
                        updatedTask = updatedTask.copy(sets = task.sets.map { it.copy(weightOrDuration = task.target.replace(" ", ""), reps = "Done") })
                    }
                    viewModel.updateTask(updatedTask)
                    if (newState) onComplete()
                })
            }

            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(top = 16.dp)) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    Spacer(modifier = Modifier.height(12.dp))

                    val isTimer = task.logType == LogType.DURATION.value
                    if (!isTimer) {
                        val isRepsOnly = task.logType == LogType.REPS_ONLY.value
                        Row(Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.header_set_no), Modifier.weight(0.5f), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                            if (!isRepsOnly) Text(stringResource(R.string.header_weight_time), Modifier.weight(1f), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                            Text(stringResource(R.string.header_reps), Modifier.weight(1f), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        task.sets.forEachIndexed { index, set ->
                            SetRow(index = index, set = set, color = themeColor, isUnilateral = task.isUnilateral, isRepsOnly = isRepsOnly) { updatedSet ->
                                viewModel.updateTask(task.copy(sets = task.sets.toMutableList().apply { set(index, updatedSet) }))
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    } else {
                        val showSeconds = task.category.equals("CORE", ignoreCase = true) || task.target.contains("s", ignoreCase = true) || task.target.contains("秒")
                        task.sets.forEachIndexed { index, set ->
                            TimerSetRow(index = index, set = set, defaultDuration = parseDefaultDuration(task.target), taskId = task.id, timerState = timerState, themeColor = themeColor, showSeconds = showSeconds, onStart = { onRequestPermission(); viewModel.startTimer(context, task.id, index, it) }, onPause = { viewModel.pauseTimer(context) }, onStop = { viewModel.stopTimer(context) }) {
                                viewModel.updateTask(task.copy(sets = task.sets.toMutableList().apply { removeAt(index) }))
                            }
                            if (index < task.sets.size - 1) HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                        }
                    }

                    TextButton(onClick = {
                        val lastSet = task.sets.lastOrNull()
                        val newSet = WorkoutSet(setNumber = task.sets.size + 1, weightOrDuration = lastSet?.weightOrDuration ?: "", reps = lastSet?.reps ?: "", rightWeight = lastSet?.rightWeight, rightReps = lastSet?.rightReps)
                        viewModel.updateTask(task.copy(sets = task.sets + newSet))
                    }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.btn_add_set), style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}

private fun parseDefaultDuration(target: String): String = Regex("\\d+").find(target)?.value ?: "30"

@Composable
fun SetRow(index: Int, set: WorkoutSet, color: Color, isUnilateral: Boolean = false, isRepsOnly: Boolean = false, onUpdate: (WorkoutSet) -> Unit) {
    var weightInput by remember(set.weightOrDuration) { mutableStateOf(set.weightOrDuration) }
    var repsInput by remember(set.reps) { mutableStateOf(set.reps) }
    var rightWeightInput by remember(set.rightWeight) { mutableStateOf(set.rightWeight ?: "") }
    var rightRepsInput by remember(set.rightReps) { mutableStateOf(set.rightReps ?: "") }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("${set.setNumber}", modifier = Modifier.weight(0.5f), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f))
        if (isUnilateral) {
            Column(modifier = Modifier.weight(2f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("L", fontSize = 10.sp, color = color, modifier = Modifier.width(16.dp), fontWeight = FontWeight.Bold)
                    if (!isRepsOnly) { InputBox(weightInput, color, Modifier.weight(1f)) { weightInput = it; onUpdate(set.copy(weightOrDuration = it)) }; Spacer(modifier = Modifier.width(4.dp)) }
                    InputBox(repsInput, color, Modifier.weight(1f)) { repsInput = it; onUpdate(set.copy(reps = it)) }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("R", fontSize = 10.sp, color = color, modifier = Modifier.width(16.dp), fontWeight = FontWeight.Bold)
                    if (!isRepsOnly) { InputBox(rightWeightInput, color, Modifier.weight(1f)) { rightWeightInput = it; onUpdate(set.copy(rightWeight = it)) }; Spacer(modifier = Modifier.width(4.dp)) }
                    InputBox(rightRepsInput, color, Modifier.weight(1f)) { rightRepsInput = it; onUpdate(set.copy(rightReps = it)) }
                }
            }
        } else {
            if (!isRepsOnly) { InputBox(weightInput, color, Modifier.weight(1f).padding(end = 8.dp)) { weightInput = it; onUpdate(set.copy(weightOrDuration = it)) } }
            InputBox(repsInput, color, Modifier.weight(1f)) { repsInput = it; onUpdate(set.copy(reps = it)) }
        }
    }
}

@Composable
fun InputBox(value: String, color: Color, modifier: Modifier = Modifier, onValueChange: (String) -> Unit) {
    Surface(modifier = modifier, color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), shape = RoundedCornerShape(8.dp)) {
        BasicTextField(
            value = value, onValueChange = onValueChange,
            textStyle = TextStyle(color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp, fontWeight = FontWeight.Medium),
            singleLine = true, cursorBrush = SolidColor(color),
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
        )
    }
}

@Composable
fun TimerSetRow(index: Int, set: WorkoutSet, defaultDuration: String, taskId: Long, timerState: MainViewModel.TimerState, themeColor: Color, showSeconds: Boolean = false, onStart: (Float) -> Unit, onPause: () -> Unit, onStop: () -> Unit, onRemove: () -> Unit) {
    val isActive = timerState.taskId == taskId && timerState.setIndex == index
    var inputMinutes by remember { mutableStateOf(defaultDuration) }
    var inputSeconds by remember { mutableStateOf(if (showSeconds) "30" else "0") }

    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text = "${index + 1}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.width(30.dp), color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f), fontWeight = FontWeight.Bold)
        Box(modifier = Modifier.weight(1f).padding(horizontal = 8.dp), contentAlignment = Alignment.CenterStart) {
            if (isActive) {
                val s = timerState.remainingSeconds
                val isPrep = timerState.phase == TimerPhase.PREP
                val displayColor = if (isPrep) Color(0xFFFF9800) else themeColor
                Column {
                    if (isPrep) Text("PREP", style = MaterialTheme.typography.labelSmall, color = displayColor, fontWeight = FontWeight.Bold)
                    Text(String.format("%02d:%02d", s / 60, s % 60), style = MaterialTheme.typography.headlineMedium, color = displayColor, fontWeight = FontWeight.Bold)
                }
            } else if (set.weightOrDuration.isNotBlank() && (set.weightOrDuration.contains("min") || set.reps == "Done")) {
                Text(text = "✅ ${set.weightOrDuration}", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.tertiary)
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), shape = RoundedCornerShape(8.dp), modifier = Modifier.width(54.dp)) {
                        BasicTextField(value = inputMinutes, onValueChange = { if (it.all { c -> c.isDigit() }) inputMinutes = it }, textStyle = TextStyle(color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp, fontWeight = FontWeight.Medium), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.padding(8.dp), cursorBrush = SolidColor(themeColor))
                    }
                    Text("m", modifier = Modifier.padding(start = 4.dp), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (showSeconds) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), shape = RoundedCornerShape(8.dp), modifier = Modifier.width(54.dp)) {
                            BasicTextField(value = inputSeconds, onValueChange = { if (it.length <= 2 && it.all { c -> c.isDigit() }) inputSeconds = it }, textStyle = TextStyle(color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp, fontWeight = FontWeight.Medium), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.padding(8.dp), cursorBrush = SolidColor(themeColor))
                        }
                        Text("s", modifier = Modifier.padding(start = 4.dp), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        Row {
            if (isActive) {
                IconButton(onClick = if (timerState.isRunning) onPause else { { onStart(timerState.totalSeconds / 60f) } }) { Icon(if (timerState.isRunning) Icons.Default.Pause else Icons.Default.PlayArrow, null, tint = themeColor) }
                IconButton(onClick = onStop) { Icon(Icons.Default.Stop, null, tint = Color.Red) }
            } else if (set.weightOrDuration.isBlank()) {
                IconButton(onClick = { onStart((inputMinutes.toIntOrNull() ?: 0) + (inputSeconds.toIntOrNull() ?: 0) / 60f) }) { Icon(Icons.Default.PlayCircle, null, tint = themeColor, modifier = Modifier.size(32.dp)) }
                IconButton(onClick = onRemove) { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)) }
            } else IconButton(onClick = onRemove) { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)) }
        }
    }
}

@Composable
fun HeaderSection(date: LocalDate, dayType: DayType, progress: Float, color: Color, showWeightAlert: Boolean, onWeightClick: () -> Unit, onTypeClick: () -> Unit) {
    Column {
        Text(text = date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f))
        Spacer(modifier = Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = stringResource(dayType.labelResId), color = color, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.ExtraBold, modifier = Modifier.clickable { onTypeClick() })
            if (showWeightAlert) {
                Spacer(modifier = Modifier.width(12.dp))
                Button(onClick = onWeightClick, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9800)), shape = RoundedCornerShape(8.dp), contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp), modifier = Modifier.height(28.dp)) {
                    Text(stringResource(R.string.log_weight), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)), color = color, trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    }
}

@Composable
fun EmptyState(dayType: DayType, onApplyRoutine: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize().padding(bottom = 100.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.FitnessCenter, null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
            Spacer(modifier = Modifier.height(16.dp))
            if (dayType == DayType.REST) Text(stringResource(R.string.type_rest), color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f), style = MaterialTheme.typography.bodyLarge)
            else {
                Text(stringResource(R.string.no_plan), color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f), style = MaterialTheme.typography.bodyLarge)
                Spacer(modifier = Modifier.height(12.dp))
                Button(onClick = onApplyRoutine, shape = RoundedCornerShape(12.dp)) { Text(stringResource(R.string.apply_routine)) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddExerciseSheet(viewModel: MainViewModel, navController: NavController, onDismiss: () -> Unit) {
    val templates by viewModel.allTemplates.collectAsState(initial = emptyList())
    val categories = listOf("STRENGTH", "CARDIO", "CORE")
    var selectedCategory by remember { mutableStateOf("STRENGTH") }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp)) {
            Button(onClick = { onDismiss(); navController.navigate("exercise_manager") }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), shape = RoundedCornerShape(12.dp)) { Text(stringResource(R.string.new_manage_lib)) }
            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
            TabRow(selectedTabIndex = categories.indexOf(selectedCategory), containerColor = Color.Transparent, contentColor = MaterialTheme.colorScheme.primary) {
                categories.forEach { category ->
                    val labelRes = when(category) { "STRENGTH" -> R.string.category_strength; "CARDIO" -> R.string.category_cardio; "CORE" -> R.string.category_core; else -> R.string.category_strength }
                    Tab(selected = selectedCategory == category, onClick = { selectedCategory = category }, text = { Text(stringResource(labelRes), fontSize = 13.sp, fontWeight = if(selectedCategory == category) FontWeight.Bold else FontWeight.Normal) })
                }
            }
            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                val filtered = templates.filter { it.category == selectedCategory }
                if (filtered.isEmpty()) item { Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) { Text(stringResource(R.string.chart_no_data), color = Color.Gray) } }
                else items(filtered) { t ->
                    Row(modifier = Modifier.fillMaxWidth().clickable { viewModel.addTaskFromTemplate(t); onDismiss() }.padding(20.dp, 16.dp)) { Text(t.name, style = MaterialTheme.typography.bodyLarge) }
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                }
            }
        }
    }
}

@Composable
fun WeightDialog(viewModel: MainViewModel, onDismiss: () -> Unit) {
    val profile by viewModel.userProfile.collectAsState()
    val needFullInfo = remember(profile) { profile.height == 0f || profile.age == 0 || profile.age < 22 }
    var weightInput by remember { mutableStateOf("") }
    var ageInput by remember { mutableStateOf(if (profile.age > 0) profile.age.toString() else "") }
    var heightInput by remember { mutableStateOf(if (profile.height > 0) profile.height.toString() else "") }
    var selectedGender by remember { mutableStateOf(profile.gender) }
    var bodyFatInput by remember { mutableStateOf("") }; var muscleInput by remember { mutableStateOf("") }; var waterInput by remember { mutableStateOf("") }; var waistInput by remember { mutableStateOf("") }; var hipInput by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (needFullInfo) R.string.dialog_profile_title else R.string.dialog_weight_title)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(value = weightInput, onValueChange = { weightInput = it }, label = { Text(stringResource(R.string.label_weight_kg)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, shape = RoundedCornerShape(12.dp))
                if (needFullInfo) {
                    HorizontalDivider()
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = ageInput, onValueChange = { ageInput = it }, label = { Text(stringResource(R.string.hint_input_age)) }, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, shape = RoundedCornerShape(12.dp))
                        OutlinedTextField(value = heightInput, onValueChange = { heightInput = it }, label = { Text(stringResource(R.string.hint_input_height)) }, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, shape = RoundedCornerShape(12.dp))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.label_gender) + ": ")
                        FilterChip(selected = selectedGender == 0, onClick = { selectedGender = 0 }, label = { Text(stringResource(R.string.gender_male)) })
                        Spacer(modifier = Modifier.width(8.dp))
                        FilterChip(selected = selectedGender == 1, onClick = { selectedGender = 1 }, label = { Text(stringResource(R.string.gender_female)) })
                    }
                }
                HorizontalDivider()
                Text(stringResource(R.string.weekly_metrics_subtitle), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = bodyFatInput, onValueChange = { bodyFatInput = it }, label = { Text(stringResource(R.string.label_body_fat_kg)) }, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, shape = RoundedCornerShape(12.dp))
                    OutlinedTextField(value = muscleInput, onValueChange = { muscleInput = it }, label = { Text(stringResource(R.string.label_skeletal_muscle_kg)) }, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, shape = RoundedCornerShape(12.dp))
                }
                OutlinedTextField(value = waterInput, onValueChange = { waterInput = it }, label = { Text(stringResource(R.string.label_body_water)) }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, shape = RoundedCornerShape(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = waistInput, onValueChange = { waistInput = it }, label = { Text(stringResource(R.string.label_waist_circumference)) }, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, shape = RoundedCornerShape(12.dp))
                    OutlinedTextField(value = hipInput, onValueChange = { hipInput = it }, label = { Text(stringResource(R.string.label_hip_circumference)) }, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, shape = RoundedCornerShape(12.dp))
                }
            }
        },
        confirmButton = { Button(onClick = { weightInput.toFloatOrNull()?.let { viewModel.logWeightAndProfile(it, ageInput.toIntOrNull(), heightInput.toFloatOrNull(), selectedGender, bodyFatInput.toFloatOrNull(), muscleInput.toFloatOrNull(), waterInput.toFloatOrNull(), waistInput.toFloatOrNull(), hipInput.toFloatOrNull()); onDismiss() } }) { Text(stringResource(R.string.btn_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.btn_cancel)) } }
    )
}

@Composable
fun PillCheckButton(isCompleted: Boolean, color: Color, onClick: () -> Unit) {
    val scale by animateFloatAsState(if (isCompleted) 0.95f else 1f)
    val text = if (isCompleted) stringResource(R.string.btn_done) else stringResource(R.string.btn_check)
    
    GlassButton(
        text = text,
        modifier = Modifier.width(100.dp).height(38.dp).scale(scale),
        containerColor = if (isCompleted) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f) else color,
        contentColor = if (isCompleted) MaterialTheme.colorScheme.onSecondaryContainer else Color.White,
        onClick = onClick
    )
}

@Composable
fun <T> SwipeToDeleteContainer(item: T, onDelete: (T) -> Unit, content: @Composable () -> Unit) {
    val haptic = LocalHapticFeedback.current; val density = LocalDensity.current; val scope = rememberCoroutineScope()
    val configuration = LocalConfiguration.current; val screenWidthPx = with(density) { configuration.screenWidthDp.dp.toPx() }
    val offsetX = remember { Animatable(0f) }
    val revealThresholdPx = with(density) { 80.dp.toPx() }; val deleteThresholdPx = screenWidthPx * 0.5f
    Box(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        if (offsetX.value < 0) Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.error.copy(alpha = 0.8f), RoundedCornerShape(20.dp)).clickable { onDelete(item) }.padding(end = 24.dp), contentAlignment = Alignment.CenterEnd) { Icon(Icons.Default.Delete, null, tint = Color.White) }
        Box(modifier = Modifier.offset { IntOffset(offsetX.value.roundToInt(), 0) }.draggable(state = rememberDraggableState { delta -> scope.launch { offsetX.snapTo((offsetX.value + delta).coerceAtMost(0f)) } }, orientation = Orientation.Horizontal, onDragStopped = { _ -> val currentX = abs(offsetX.value); when { currentX >= deleteThresholdPx -> { haptic.performHapticFeedback(HapticFeedbackType.LongPress); scope.launch { offsetX.animateTo(-screenWidthPx, tween(300)); onDelete(item); offsetX.snapTo(0f) } } currentX >= revealThresholdPx / 2 -> scope.launch { offsetX.animateTo(-revealThresholdPx, tween(300)) } else -> scope.launch { offsetX.animateTo(0f, tween(300)) } } })) { content() }
    }
}

@Composable
fun ExplosionEffect(onDismiss: () -> Unit) {
    val particles = remember { List(20) { Particle() } }; var visible by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) { delay(1000); visible = false; onDismiss() }
    if (visible) Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("🎉", fontSize = 100.sp)
        Canvas(modifier = Modifier.fillMaxSize()) { val center = this.center; particles.forEach { p -> drawCircle(color = p.color, radius = 8f, center = Offset((center.x + p.radius * cos(p.angle)).toFloat(), (center.y + p.radius * sin(p.angle)).toFloat())); p.update() } }
    }
}
class Particle { var angle = Random.nextDouble(0.0, 2 * PI); var radius = 0.0; var speed = Random.nextDouble(10.0, 30.0); val color = listOf(Color.Red, Color.Yellow, Color.Blue, Color.Green).random(); fun update() { radius += speed } }

@Composable
fun CountdownOverlay(timerState: MainViewModel.TimerState) {
    val isPrep = timerState.phase == TimerPhase.PREP; val color = if (isPrep) Color(0xFFFF9800) else Color(0xFFF44336)
    Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.9f)).clickable(enabled = false) {}, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = if (isPrep) stringResource(R.string.timer_overlay_prep) else stringResource(R.string.timer_overlay_finish), style = MaterialTheme.typography.displayMedium, color = Color.White.copy(alpha = 0.7f), fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(32.dp))
            Text(text = "${timerState.remainingSeconds}", style = MaterialTheme.typography.displayLarge.copy(fontSize = 140.sp), color = color, fontWeight = FontWeight.ExtraBold)
        }
    }
}
