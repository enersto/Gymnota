package com.example.myfit.ui

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import coil.compose.AsyncImage
import com.example.myfit.R
import com.example.myfit.model.AiChatRecord
import com.example.myfit.ui.components.GlassButton
import com.example.myfit.ui.components.GlassCard
import com.example.myfit.ui.components.GlassChatBubble
import com.example.myfit.ui.components.GlassScaffoldContent
import com.example.myfit.ui.components.LocalGlassMode
import com.example.myfit.viewmodel.MainViewModel
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun AICoachScreen(viewModel: MainViewModel, navController: NavController) {
    val aiResponse by viewModel.aiResponse.collectAsState()
    val isLoading by viewModel.aiIsLoading.collectAsState()
    val settings by viewModel.userProfile.collectAsState()
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    
    var showHistoryDialog by remember { mutableStateOf(false) }
    var isFreeChatMode by remember { mutableStateOf(false) }
    var tempCameraUri by remember { mutableStateOf<Uri?>(null) }
    var showCsvConfirmDialog by remember { mutableStateOf(false) }
    var tempCsvData by remember { mutableStateOf("") }
    var showRefineDialog by remember { mutableStateOf(false) }
    var chatImageUri by remember { mutableStateOf<Uri?>(null) }

    val glassMode = LocalGlassMode.current
    val useGlassTabs = glassMode && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S

    val chatGalleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            chatImageUri = uri
            isFreeChatMode = true
        }
    }

    fun createTempPictureUri(): Uri? {
        return try {
            val tempFile = File.createTempFile("ai_query_", ".jpg", context.cacheDir).apply {
                createNewFile()
                deleteOnExit()
            }
            FileProvider.getUriForFile(context, "${context.packageName}.provider", tempFile)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success && tempCameraUri != null) {
            isFreeChatMode = true
            viewModel.currentTrainingGoal = context.getString(R.string.msg_processing)
            viewModel.analyzeImage(context, tempCameraUri!!)
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted ->
            if (isGranted) {
                val uri = createTempPictureUri()
                if (uri != null) {
                    tempCameraUri = uri
                    cameraLauncher.launch(uri)
                }
            } else {
                Toast.makeText(context, context.getString(R.string.error_img_permission), Toast.LENGTH_SHORT).show()
            }
        }
    )

    GlassScaffoldContent {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(top = 24.dp)
        ) {
            // Fixed Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(R.string.tab_ai_coach),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground
                )
                
                FilledTonalIconButton(
                    onClick = { showHistoryDialog = true },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.History, contentDescription = "History")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Scrollable Content
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp)
                    .verticalScroll(scrollState)
            ) {
                // Status Row
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .clickable {
                                val timestamp = System.currentTimeMillis()
                                navController.navigate("settings?scrollToAi=$timestamp") {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val isConfigured = settings.aiApiKey.isNotBlank()
                        Icon(
                            imageVector = Icons.Default.Circle,
                            contentDescription = null,
                            tint = if (isConfigured) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(10.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isConfigured) stringResource(
                                R.string.ai_status_configured,
                                settings.aiProvider,
                                settings.aiModel
                            )
                            else stringResource(R.string.ai_status_unconfigured),
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        Icon(
                            Icons.Default.Settings,
                            null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Quick Toolbar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            val permission = Manifest.permission.CAMERA
                            if (ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED) {
                                val uri = createTempPictureUri()
                                if (uri != null) {
                                    tempCameraUri = uri
                                    cameraLauncher.launch(uri)
                                }
                            } else {
                                permissionLauncher.launch(permission)
                            }
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(vertical = 10.dp)
                    ) {
                        Icon(Icons.Default.PhotoCamera, null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.btn_snap_photo), fontSize = 13.sp)
                    }

                    Button(
                        onClick = {
                            isFreeChatMode = !isFreeChatMode
                            if (isFreeChatMode) viewModel.currentTrainingGoal = ""
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(vertical = 10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isFreeChatMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                            contentColor = if (isFreeChatMode) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(Icons.Default.Chat, null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.btn_free_chat), fontSize = 13.sp)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Input Area
                if (isFreeChatMode) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        if (chatImageUri != null) {
                            Box(
                                modifier = Modifier
                                    .padding(bottom = 8.dp)
                                    .size(80.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                AsyncImage(
                                    model = chatImageUri,
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                                IconButton(
                                    onClick = { chatImageUri = null },
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .size(24.dp)
                                        .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(0.dp, 0.dp, 0.dp, 8.dp))
                                ) {
                                    Icon(Icons.Default.Close, null, tint = Color.White, modifier = Modifier.size(16.dp))
                                }
                            }
                        }

                        OutlinedTextField(
                            value = viewModel.currentTrainingGoal,
                            onValueChange = { viewModel.currentTrainingGoal = it },
                            label = { Text(stringResource(R.string.btn_free_chat)) },
                            placeholder = { Text(stringResource(R.string.hint_input_chat)) },
                            modifier = Modifier.fillMaxWidth(),
                            maxLines = 3,
                            trailingIcon = {
                                IconButton(onClick = { chatGalleryLauncher.launch("image/*") }) {
                                    Icon(
                                        Icons.Default.Image,
                                        contentDescription = "Add Image",
                                        tint = if (chatImageUri != null) MaterialTheme.colorScheme.primary else Color.Gray
                                    )
                                }
                            },
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                } else {
                    PlanConfigCard(viewModel)
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = viewModel.currentTrainingGoal,
                        onValueChange = { viewModel.currentTrainingGoal = it },
                        label = { Text(stringResource(R.string.label_current_goal)) },
                        placeholder = { Text(stringResource(R.string.hint_input_goal)) },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 3,
                        shape = RoundedCornerShape(12.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // [更新点]：使用 GlassButton
                GlassButton(
                    text = if (isFreeChatMode) stringResource(R.string.btn_send_message) else stringResource(R.string.btn_get_advice),
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isLoading,
                    onClick = {
                        if (isFreeChatMode) {
                            if (viewModel.currentTrainingGoal.isNotBlank() || chatImageUri != null) {
                                viewModel.sendFreeChat(context, viewModel.currentTrainingGoal, chatImageUri)
                                chatImageUri = null
                            } else {
                                Toast.makeText(context, context.getString(R.string.hint_input_chat), Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            viewModel.generateWeeklyPlan(context)
                        }
                    }
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Result Area
                if (!aiResponse.isNullOrBlank()) {
                    GlassChatBubble(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = stringResource(R.string.ai_response_title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                            MarkdownText(markdown = aiResponse!!)
                            
                            if (!isFreeChatMode) {
                                Spacer(modifier = Modifier.height(16.dp))
                                FilledTonalButton(
                                    onClick = { showRefineDialog = true },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(Icons.Default.Download, null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(stringResource(R.string.btn_generate_and_import))
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(if (useGlassTabs) 120.dp else 32.dp))
            }
        }
    }

    // Dialogs
    if (showHistoryDialog) {
        AiHistoryDialog(
            viewModel = viewModel,
            onDismiss = { showHistoryDialog = false },
            onSelect = { record ->
                viewModel.loadAiResponseFromHistory(record.content)
                if (!record.userGoal.isNullOrBlank()) {
                    viewModel.currentTrainingGoal = record.userGoal
                }
                showHistoryDialog = false
            }
        )
    }

    if (showCsvConfirmDialog) {
        ImportDialog(
            defaultText = tempCsvData,
            onDismiss = { showCsvConfirmDialog = false },
            onImport = { csv ->
                viewModel.importWeeklyRoutine(context, csv)
                showCsvConfirmDialog = false
            }
        )
    }

    if (showRefineDialog) {
        RefinePlanDialog(
            viewModel = viewModel,
            adviceContent = aiResponse ?: "",
            onDismiss = { showRefineDialog = false },
            onGenerate = { feedback ->
                viewModel.generateCsvFromAdvice(context, aiResponse ?: "", feedback) { csvData ->
                    tempCsvData = csvData
                    showRefineDialog = false
                    showCsvConfirmDialog = true
                }
            }
        )
    }
}

@Composable
fun RefinePlanDialog(
    viewModel: MainViewModel,
    adviceContent: String,
    onDismiss: () -> Unit,
    onGenerate: (String) -> Unit
) {
    var feedback by remember { mutableStateOf("") }
    val isGenerating by viewModel.isGeneratingCsv.collectAsState()

    AlertDialog(
        onDismissRequest = { if (!isGenerating) onDismiss() },
        title = { Text(stringResource(R.string.weekly_plan)) },
        text = {
            Column {
                Text(stringResource(R.string.msg_refine_dialog_hint), fontSize = 13.sp, color = Color.Gray)
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = feedback,
                    onValueChange = { feedback = it },
                    label = { Text(stringResource(R.string.label_instruction)) },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3,
                    enabled = !isGenerating,
                    shape = RoundedCornerShape(12.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onGenerate(feedback) },
                enabled = !isGenerating,
                shape = RoundedCornerShape(12.dp)
            ) {
                if (isGenerating) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.msg_processing))
                } else {
                    Text(stringResource(R.string.btn_confirm))
                }
            }
        },
        dismissButton = {
            if (!isGenerating) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.btn_cancel)) }
            }
        }
    )
}

@Composable
fun AiHistoryDialog(
    viewModel: MainViewModel,
    onDismiss: () -> Unit,
    onSelect: (AiChatRecord) -> Unit
) {
    val historyList by viewModel.aiChatHistory.collectAsState(initial = emptyList())

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.title_ai_history)) },
        text = {
            if (historyList.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.msg_no_history), color = Color.Gray)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 400.dp).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(historyList) { record ->
                        GlassCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(record) },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    val dateStr = remember(record.timestamp) {
                                        SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(record.timestamp))
                                    }
                                    Text(dateStr, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))

                                    val summary = if (!record.userGoal.isNullOrBlank()) {
                                        "Target: ${record.userGoal}"
                                    } else {
                                        record.content.take(30).replace("\n", " ") + "..."
                                    }
                                    Text(
                                        text = summary,
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 2,
                                        fontWeight = FontWeight.Medium
                                    )
                                }

                                IconButton(onClick = { viewModel.deleteAiChatRecord(record.id) }) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = "Delete",
                                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.6f),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.btn_close)) }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun PlanConfigCard(viewModel: MainViewModel) {
    var showInjuryDialog by remember { mutableStateOf(false) }

    val injuryOptions = mapOf(
        "Knee" to R.string.injury_knee,
        "LowerBack" to R.string.injury_lower_back,
        "Shoulder" to R.string.injury_shoulder,
        "Wrist" to R.string.injury_wrist,
        "Neck" to R.string.injury_neck
    )

    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(stringResource(R.string.label_context_length), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 4.dp)
            ) {
                (1..4).forEach { weeks ->
                    FilterChip(
                        selected = viewModel.historyWeeks == weeks,
                        onClick = { viewModel.historyWeeks = weeks },
                        label = {
                            val labelRes = when (weeks) {
                                1 -> R.string.option_1_week
                                2 -> R.string.option_2_weeks
                                3 -> R.string.option_3_weeks
                                else -> R.string.option_4_weeks
                            }
                            Text(stringResource(labelRes))
                        }
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            Text(stringResource(R.string.label_focus), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 4.dp)
            ) {
                val focusOptions = listOf(
                    "COMPREHENSIVE" to R.string.focus_comprehensive,
                    "STRENGTH" to R.string.category_strength,
                    "CARDIO" to R.string.category_cardio,
                    "CORE" to R.string.category_core
                )

                focusOptions.forEach { (key, resId) ->
                    val isSelected = viewModel.selectedFocus.contains(key)
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            val current = viewModel.selectedFocus.toMutableSet()
                            if (key == "COMPREHENSIVE") {
                                current.clear()
                                current.add("COMPREHENSIVE")
                            } else {
                                current.remove("COMPREHENSIVE")
                                if (current.contains(key)) current.remove(key) else current.add(key)
                                if (current.isEmpty()) current.add("COMPREHENSIVE")
                            }
                            viewModel.selectedFocus = current
                        },
                        label = { Text(stringResource(resId)) }
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            Text(stringResource(R.string.label_scene), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 4.dp)
            ) {
                val sceneOptions = listOf(
                    "GYM" to R.string.scene_gym,
                    "HOME_EQUIP" to R.string.scene_home_equip,
                    "HOME_NONE" to R.string.scene_home_none,
                    "OUTDOOR" to R.string.scene_outdoor,
                    "POOL" to R.string.scene_pool,
                    "YOGA_STUDIO" to R.string.scene_yoga,
                    "LIMITED_GYM" to R.string.scene_limited_gym,
                    "CROSSFIT_BOX" to R.string.scene_crossfit
                )
                sceneOptions.forEach { (key, resId) ->
                    val isSelected = viewModel.selectedScene.contains(key)
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            val current = viewModel.selectedScene.toMutableSet()
                            if (current.contains(key)) current.remove(key) else current.add(key)
                            if (current.isEmpty()) current.add("GYM")
                            viewModel.selectedScene = current
                        },
                        label = { Text(stringResource(resId)) }
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            Row(
                modifier = Modifier.fillMaxWidth().clickable { showInjuryDialog = true },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(stringResource(R.string.label_injuries), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error)
                    val selectedLabels = viewModel.selectedInjuries.mapNotNull { key ->
                        injuryOptions[key]?.let { stringResource(it) }
                    }
                    Text(
                        text = if (selectedLabels.isEmpty()) stringResource(R.string.hint_injuries_none) else selectedLabels.joinToString("、"),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (selectedLabels.isEmpty()) Color.Gray else MaterialTheme.colorScheme.error
                    )
                }
                Icon(Icons.Default.ArrowForwardIos, null, modifier = Modifier.size(12.dp), tint = Color.Gray)
            }
        }
    }

    if (showInjuryDialog) {
        AlertDialog(
            onDismissRequest = { showInjuryDialog = false },
            title = { Text(stringResource(R.string.dialog_injuries_title)) },
            text = {
                Column {
                    injuryOptions.forEach { (key, resId) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val current = viewModel.selectedInjuries.toMutableSet()
                                    if (current.contains(key)) current.remove(key) else current.add(key)
                                    viewModel.selectedInjuries = current
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = viewModel.selectedInjuries.contains(key),
                                onCheckedChange = null
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(resId))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showInjuryDialog = false }) { Text(stringResource(R.string.btn_confirm)) }
            }
        )
    }
}
