package com.example.myfit.ui

// [新增] 基础 UI 组件
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn // 修复 LazyColumn 报错
import androidx.compose.foundation.lazy.items // 修复 items 报错
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape

// [新增] Material 图标与组件
import androidx.compose.material.icons.Icons // 修复 Icons 报错
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.*

// [新增] 运行时与图形
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color // 修复 Color 报错
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight // 修复 FontWeight 报错
import androidx.compose.ui.unit.dp

// [新增] 业务逻辑引用
import com.example.myfit.R
import com.example.myfit.viewmodel.MainViewModel
import com.example.myfit.ui.MarkdownText
import com.example.myfit.model.AiChatRecord // 修复 record.id, content 等引用报错

// [新增] 日期格式化
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

import androidx.compose.material.icons.filled.Check

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.navigation.NavController // [新增]
import androidx.compose.material.icons.filled.PhotoCamera // [新增]
import androidx.compose.material.icons.filled.Chat // [新增]
import androidx.compose.material.icons.filled.Settings // [新增]
import androidx.compose.material.icons.filled.Circle // [新增]
import java.io.File // [新增]

import androidx.compose.ui.unit.sp // ✅ [修复] 解决 sp 报错

import androidx.compose.foundation.layout.FlowRow

import androidx.compose.foundation.BorderStroke // 修复 BorderStroke
import androidx.compose.material.icons.filled.ArrowForwardIos

import androidx.compose.material.icons.filled.Download // [新增] 下载/导入图标
import android.content.Context
import androidx.compose.material.icons.filled.Close //以此类推，确保有 Close 图标
import androidx.compose.material.icons.filled.Image // 新增相册图标

// ... 其他 imports ...
import androidx.compose.ui.draw.clip // 确保这行存在
import androidx.compose.ui.layout.ContentScale // [新增]
import coil.compose.AsyncImage // [新增] 必须添加这个才能显示图片

import androidx.navigation.NavGraph.Companion.findStartDestination

private fun createTempPictureUri(context: android.content.Context): Uri? {
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
@Composable
fun AICoachScreen(viewModel: MainViewModel,navController: NavController) {
    val aiResponse by viewModel.aiResponse.collectAsState()
    val isLoading by viewModel.aiIsLoading.collectAsState()
    // [新增] 监听用户配置，用于状态灯
    val settings by viewModel.userProfile.collectAsState()
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    // [新增] 控制历史记录弹窗显示
    var showHistoryDialog by remember { mutableStateOf(false) }

    // [新增] 自由对话模式状态
    var isFreeChatMode by remember { mutableStateOf(false) }

    // ================== [新增] 复用相机逻辑开始 ==================
    var tempCameraUri by remember { mutableStateOf<Uri?>(null) }

    // [新增] CSV 确认弹窗状态
    var showCsvConfirmDialog by remember { mutableStateOf(false) }
    var tempCsvData by remember { mutableStateOf("") }
    // [新增] 控制“优化并生成”对话框的显示
    var showRefineDialog by remember { mutableStateOf(false) }

    // [新增] 聊天图片状态
    var chatImageUri by remember { mutableStateOf<Uri?>(null) }

    // [新增] 相册选择器 (用于聊天)
    val chatGalleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            chatImageUri = uri
            // 如果不在自由对话模式，自动切换过去
            isFreeChatMode = true
        }
    }

    // 辅助函数：创建临时文件 URI
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

    // 拍照回调
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success && tempCameraUri != null) {
            // [修改] 1. 自动切换到自由对话模式 (隐藏历史周数等无关UI)
            isFreeChatMode = true
            // 2. 设置输入框提示语 (可选)
            viewModel.currentTrainingGoal = context.getString(R.string.msg_processing)
            // 3. 调用识别逻辑
            viewModel.analyzeImage(context, tempCameraUri!!)
        }
    }

    // 权限请求
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(scrollState)
    ) {
        // 1. [新增] 页面大标题 (与 Settings/History 保持一致)
        Text(
            text = stringResource(R.string.tab_ai_coach),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground
        )

        // 2. [原有] 顶部状态栏 (作为卡片存在)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // 左侧：模型状态卡片
            Card(
                onClick = {
                    // [修改] 带上 scrollToAi 参数，值为时间戳以强制刷新
                    val timestamp = System.currentTimeMillis()
                    navController.navigate("settings?scrollToAi=$timestamp") {
                        // 保持底部导航的平滑切换体验
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(
                        alpha = 0.5f
                    )
                ),
                shape = RoundedCornerShape(50), // 胶囊形状
                modifier = Modifier.weight(1f).padding(end = 8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val isConfigured = settings.aiApiKey.isNotBlank()
                    // 状态灯
                    Icon(
                        imageVector = Icons.Default.Circle,
                        contentDescription = null,
                        tint = if (isConfigured) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(10.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    // 模型名称
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
                        tint = Color.Gray
                    )
                }
            }

            // 右侧：历史记录按钮
            FilledTonalIconButton(
                onClick = { showHistoryDialog = true }
            ) {
                Icon(Icons.Default.History, contentDescription = "History")
            }
        }

        // [新增] 2. 快捷工具栏 (拍一拍 / 自由对话)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 📸 拍一拍按钮
            OutlinedButton(
                onClick = {
                    val permission = Manifest.permission.CAMERA
                    if (ContextCompat.checkSelfPermission(
                            context,
                            permission
                        ) == PackageManager.PERMISSION_GRANTED
                    ) {
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

            // 💬 自由对话按钮
            Button(
                onClick = {
                    isFreeChatMode = !isFreeChatMode // 切换模式
                    if (isFreeChatMode) viewModel.currentTrainingGoal = "" // 清空输入框
                },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(vertical = 10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isFreeChatMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = if (isFreeChatMode) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(Icons.Default.Chat, null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.btn_free_chat), fontSize = 13.sp)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // [修复] 根据模式显示不同的输入区域
        if (isFreeChatMode) {
                // ==================== 自由对话模式 ====================
                Column(modifier = Modifier.fillMaxWidth()) {
                    // A. 图片预览气泡 (仅在已选图时显示)
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
                            // 删除图片的小叉号
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

                    // B. 输入框 (带相册按钮)
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
                        }
                    )
                }


        Spacer(modifier = Modifier.height(16.dp))

// ------------------------------------------------------
// 3. 发送/确认按钮 (逻辑也需要微调)
// ------------------------------------------------------
        Button(
            onClick = {
                if (isFreeChatMode) {
                    // 自由对话逻辑：支持文字 OR 图片
                    if (viewModel.currentTrainingGoal.isNotBlank() || chatImageUri != null) {
                        viewModel.sendFreeChat(context, viewModel.currentTrainingGoal, chatImageUri)
                        chatImageUri = null // 发送后清空图片
                    } else {
                        Toast.makeText(context, context.getString(R.string.hint_input_chat), Toast.LENGTH_SHORT).show()
                    }
                } else {
                    // 周计划逻辑
                    viewModel.generateWeeklyPlan(context)
                }
            },
            enabled = !isLoading,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            contentPadding = PaddingValues(vertical = 12.dp)
        ){
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.msg_processing))
            } else {
                Text(stringResource(R.string.btn_send_message))
            }
        }
        } else {
            // ==================== 周计划模式 ====================

        Spacer(modifier = Modifier.height(16.dp))

            // 显示详细配置卡片
            PlanConfigCard(viewModel)

            Spacer(modifier = Modifier.height(16.dp))

            // 目标输入框
            OutlinedTextField(
                value = viewModel.currentTrainingGoal,
                onValueChange = { viewModel.currentTrainingGoal = it },
                label = { Text(if (isFreeChatMode) stringResource(R.string.btn_free_chat) else stringResource(R.string.label_current_goal))},
                placeholder = {
                    Text(
                        if (isFreeChatMode) stringResource(R.string.hint_input_chat) else stringResource(
                            R.string.hint_input_goal
                        )
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 3
            )

            Spacer(modifier = Modifier.height(16.dp))

                // 生成周计划按钮
                Button(
                    onClick = {
                        viewModel.generateWeeklyPlan(context)
                    },
                    enabled = !isLoading,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(vertical = 12.dp)
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.msg_processing))
                    } else {
                        Text(stringResource(R.string.btn_get_advice))
                    }
                }

            }

        Spacer(modifier = Modifier.height(24.dp))

        // 结果展示区域
        if (!aiResponse.isNullOrBlank()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.ai_response_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    // [修改] 使用新的 MarkdownText 组件替换原来的 Text
                    MarkdownText(
                        markdown = aiResponse!!
                    )
                    // [新增] 意图 B 触发按钮 (仅在非自由对话模式且有结果时显示)
                    if (!isFreeChatMode) {
                        Spacer(modifier = Modifier.height(16.dp))
                        FilledTonalButton(
                            onClick = {
                                // [修复] 点击只打开弹窗，不直接生成
                                showRefineDialog = true
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Download, null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.btn_generate_and_import))
                        }
                    }
                }
            }
        }

        // [新增] 历史记录弹窗
        if (showHistoryDialog) {
            AiHistoryDialog(
                viewModel = viewModel,
                onDismiss = { showHistoryDialog = false },
                onSelect = { record ->
                    viewModel.loadAiResponseFromHistory(record.content)
                    // 可选：回填当时的目标
                    if (!record.userGoal.isNullOrBlank()) {
                        viewModel.currentTrainingGoal = record.userGoal
                    }
                    showHistoryDialog = false
                }
            )
        }

        // [新增] CSV 确认导入弹窗
        if (showCsvConfirmDialog) {
            // 复用 ImportDialog (已在 ScheduleScreen.kt 中定义，如果是同一个包可以直接用)
            // 如果 ImportDialog 是 private 的，你需要去 ScheduleScreen.kt 把它的 private 去掉
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
                    // 调用生成，并在回调中打开确认弹窗
                    viewModel.generateCsvFromAdvice(
                        context,
                        aiResponse ?: "",
                        feedback
                    ) { csvData ->
                        tempCsvData = csvData
                        showRefineDialog = false // 关闭当前的输入框
                        showCsvConfirmDialog = true // 打开最终确认框
                    }
                }
            )
        }
    }
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
        title = { Text(stringResource(R.string.title_ai_history)) }, // 需添加资源
        text = {
            if (historyList.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.msg_no_history), color = Color.Gray) // 需添加资源
                }
            } else {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 400.dp).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(historyList) { record ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(record) },
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    // 显示日期
                                    val dateStr = remember(record.timestamp) {
                                        SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(record.timestamp))
                                    }
                                    Text(dateStr, style = MaterialTheme.typography.labelSmall, color = Color.Gray)

                                    // 显示摘要 (目标或内容前30字)
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

                                // 删除按钮
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

    // 伤病列表 (Key -> ResId)
    val injuryOptions = mapOf(
        "Knee" to R.string.injury_knee,
        "LowerBack" to R.string.injury_lower_back,
        "Shoulder" to R.string.injury_shoulder,
        "Wrist" to R.string.injury_wrist,
        "Neck" to R.string.injury_neck
    )

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {

            // 1. 参考时长
            Text(stringResource(R.string.label_context_length), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp), // 换行后的垂直间距
                modifier = Modifier.padding(top = 4.dp)
            ) {
                (1..4).forEach { weeks ->
                    FilterChip(
                        selected = viewModel.historyWeeks == weeks,
                        onClick = { viewModel.historyWeeks = weeks },
                        label = {
                            // [修改] 根据周数动态映射资源 ID
                            val labelRes = when (weeks) {
                                1 -> R.string.option_1_week
                                2 -> R.string.option_2_weeks
                                3 -> R.string.option_3_weeks
                                else -> R.string.option_4_weeks
                            }
                            Text(stringResource(labelRes))
                        },
                        modifier = Modifier.height(32.dp)
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            // 2. 训练重心 (多选)
            Text(stringResource(R.string.label_focus), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp), // [新增] 增加换行后的垂直间距
                modifier = Modifier.padding(top = 4.dp) // [新增] 标题和内容的微小间距
            ) {
                // 选项定义
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
                                // 选综合则清空其他
                                current.clear()
                                current.add("COMPREHENSIVE")
                            } else {
                                // 选其他则移除综合
                                current.remove("COMPREHENSIVE")
                                if (current.contains(key)) current.remove(key) else current.add(key)
                                // 如果全空了，默认回综合
                                if (current.isEmpty()) current.add("COMPREHENSIVE")
                            }
                            viewModel.selectedFocus = current
                        },
                        label = { Text(stringResource(resId)) },
                        modifier = Modifier.height(32.dp)
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            // 3. 器械场景 (单选)
            Text(stringResource(R.string.label_scene), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp), // [新增] 增加换行后的垂直间距
                modifier = Modifier.padding(top = 4.dp)
                ) {
                // [修改] 3. 器械场景 (单选) - 增加新选项
                val sceneOptions = listOf(
                    "GYM" to R.string.scene_gym,
                    "HOME_EQUIP" to R.string.scene_home_equip,
                    "HOME_NONE" to R.string.scene_home_none,
                    "OUTDOOR" to R.string.scene_outdoor,
                    // [新增]
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
                            // [修改] 多选切换逻辑
                            val current = viewModel.selectedScene.toMutableSet()
                            if (current.contains(key)) {
                                current.remove(key)
                            } else {
                                current.add(key)
                            }

                            // 兜底：如果全部取消了，默认回退到 GYM (防止空选)
                            if (current.isEmpty()) {
                                current.add("GYM")
                            }

                            viewModel.selectedScene = current
                        },
                        label = { Text(stringResource(resId)) },
                        modifier = Modifier.height(32.dp)
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            // 4. 伤病避开 (弹窗交互)
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

    // 伤病选择弹窗
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
                                onCheckedChange = null // 处理逻辑在 Row clickable
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

// [新增组件]
@Composable
fun RefinePlanDialog(
    viewModel: MainViewModel,
    adviceContent: String,
    onDismiss: () -> Unit,
    onGenerate: (String) -> Unit
) {
    var feedback by remember { mutableStateOf("") }
    // 监听 ViewModel 中的 CSV 生成状态
    val isGenerating by viewModel.isGeneratingCsv.collectAsState()

    AlertDialog(
        onDismissRequest = { if (!isGenerating) onDismiss() }, // 生成中不可关闭
        title = { Text(stringResource(R.string.weekly_plan)) }, // 建议放入 strings.xml
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
                    enabled = !isGenerating
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onGenerate(feedback) },
                enabled = !isGenerating
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