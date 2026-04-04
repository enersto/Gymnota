package com.example.myfit.ui

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.example.myfit.R
import com.example.myfit.model.*
import com.example.myfit.ui.components.*
import com.example.myfit.viewmodel.MainViewModel
import com.kyant.shapes.Capsule
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.example.myfit.ui.components.liquidtabs.LiquidBottomTabs
import com.example.myfit.ui.components.liquidtabs.LocalLiquidBottomTabScale

import android.os.Build
import androidx.compose.ui.graphics.graphicsLayer

@Composable
fun ScheduleScreen(navController: NavController, viewModel: MainViewModel) {
    val currentTheme by viewModel.currentTheme.collectAsState()
    val currentLanguage by viewModel.currentLanguage.collectAsState()
    val context = LocalContext.current
    val scheduleList by viewModel.allSchedules.collectAsState(initial = emptyList())
    var showImportDialog by remember { mutableStateOf(false) }
    var showManualRoutineDialog by remember { mutableStateOf(false) }
    var showHelpDialog by remember { mutableStateOf(false) }
    var showProfileDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val navBackStackEntry by navController.currentBackStackEntryAsState()

    val exportCsvLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
            uri?.let { viewModel.exportHistoryToCsv(it, context) }
        }
    val createBackupLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/x-sqlite3")) { uri ->
            uri?.let { viewModel.backupDatabase(it, context) }
        }
    val restoreBackupLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let { viewModel.restoreDatabase(it, context) }
        }

    LaunchedEffect(navBackStackEntry?.arguments?.getString("scrollToType")) {
        val trigger = navBackStackEntry?.arguments?.getString("scrollToType")
        if (trigger != null && trigger != "false") listState.animateScrollToItem(8)
    }

    LaunchedEffect(navBackStackEntry?.arguments?.getString("scrollToAi")) {
        val trigger = navBackStackEntry?.arguments?.getString("scrollToAi")
        if (trigger != null && trigger != "false") {
            delay(100); listState.animateScrollToItem(5)
        }
    }

    GlassScaffoldContent {
        Column(modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(top = 24.dp)) {
            Text(
                stringResource(R.string.settings_advanced),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                item {
                    Column {
                        Text(
                            stringResource(R.string.settings_language),
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(Modifier.height(12.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.horizontalScroll(rememberScrollState())
                        ) {
                            listOf("zh", "en", "es", "ja", "de").forEach { code ->
                                GlassLanguageChip(
                                    when (code) {
                                        "zh" -> "中文"; "en" -> "EN"; "es" -> "ES"; "ja" -> "JA"; else -> "DE"
                                    }, code == currentLanguage
                                ) {
                                    scope.launch { viewModel.switchLanguage(code); delay(500); (context as? Activity)?.recreate() }
                                }
                            }
                        }
                    }
                }
                item {
                    Column {
                        Text(
                            stringResource(R.string.settings_data_management),
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            GlassActionButton(
                                Icons.Default.Share,
                                stringResource(R.string.export_csv_btn),
                                Modifier.weight(1f)
                            ) { exportCsvLauncher.launch("history_${LocalDate.now()}.csv") }
                            GlassActionButton(
                                Icons.Default.Upload,
                                stringResource(R.string.import_csv_btn),
                                Modifier.weight(1f)
                            ) { showImportDialog = true }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            GlassActionButton(
                                Icons.Default.HelpOutline,
                                stringResource(R.string.settings_help_reference),
                                Modifier.weight(1f)
                            ) { showHelpDialog = true }
                            GlassActionButton(
                                Icons.Default.Person,
                                stringResource(R.string.settings_profile),
                                Modifier.weight(1f)
                            ) { showProfileDialog = true }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            GlassActionButton(
                                Icons.Default.Backup,
                                stringResource(R.string.btn_backup_db),
                                Modifier.weight(1f)
                            ) { createBackupLauncher.launch("backup.db") }
                            GlassActionButton(
                                Icons.Default.Restore,
                                stringResource(R.string.btn_restore_db),
                                Modifier.weight(1f)
                            ) { restoreBackupLauncher.launch(arrayOf("*/*")) }
                        }
                    }
                }
                item {
                    GlassNavBar(
                        Icons.Default.EditCalendar,
                        stringResource(R.string.weekly_plan)
                    ) { showManualRoutineDialog = true }
                }
                item {
                    GlassNavBar(
                        Icons.AutoMirrored.Filled.List,
                        stringResource(R.string.manage_lib)
                    ) { navController.navigate("exercise_manager") }
                }
                item { AiConfigSection(viewModel) }
                item { TimerSettingsSection(viewModel) }
                item {
                    Column {
                        Text(
                            stringResource(R.string.theme_style),
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                            AppTheme.entries.forEach {
                                GlassThemeCircle(
                                    it,
                                    currentTheme == it
                                ) { viewModel.switchTheme(it) }
                            }
                        }
                    }
                }
                item {
                    Text(
                        stringResource(R.string.schedule_type_title),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                items(scheduleList) { config ->
                    ScheduleItem(config) {
                        viewModel.updateScheduleConfig(
                            config.dayOfWeek,
                            it
                        )
                    }
                }
                item { AboutSection(); Spacer(Modifier.height(140.dp)) }
            }
        }
    }

    if (showImportDialog) ImportDialog(
        stringResource(R.string.import_csv_template),
        { showImportDialog = false }) {
        viewModel.importWeeklyRoutine(
            context,
            it
        ); showImportDialog = false
    }
    if (showHelpDialog) KeyReferenceDialog { showHelpDialog = false }
    if (showManualRoutineDialog) ManualRoutineDialog(viewModel) { showManualRoutineDialog = false }
    if (showProfileDialog) ProfileEditDialog(viewModel) { showProfileDialog = false }
}

@Composable
fun ImportDialog(defaultText: String, onDismiss: () -> Unit, onImport: (String) -> Unit) {
    var text by remember { mutableStateOf(defaultText) }
    Dialog(onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false, // 允许 GlassCard 控制自己的宽度
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        GlassDialogCard(modifier = Modifier.padding(16.dp), shape = RoundedCornerShape(24.dp)) {
            Column(Modifier.padding(24.dp)) {
                Text(
                    stringResource(R.string.import_dialog_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    text,
                    { text = it },
                    Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    textStyle = MaterialTheme.typography.bodySmall,
                    shape = RoundedCornerShape(12.dp)
                )
                Spacer(Modifier.height(24.dp))
                Row(Modifier.fillMaxWidth(), Arrangement.End) {
                    TextButton(onDismiss) { Text(stringResource(R.string.btn_cancel)) }
                    Spacer(Modifier.width(8.dp))
                    GlassButton(stringResource(R.string.import_btn), onClick = { onImport(text) })
                }
            }
        }
    }
}

@Composable
fun ProfileEditDialog(viewModel: MainViewModel, onDismiss: () -> Unit) {
    val profile by viewModel.userProfile.collectAsState()
    var ageInput by remember(profile) { mutableStateOf(if (profile.age > 0) profile.age.toString() else "") }
    var heightInput by remember(profile) { mutableStateOf(if (profile.height > 0) profile.height.toString() else "") }
    var selectedGender by remember(profile) { mutableIntStateOf(profile.gender) }
    Dialog(onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false, // 允许 GlassCard 控制自己的宽度
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )) {
        GlassDialogCard(modifier = Modifier.padding(16.dp), shape = RoundedCornerShape(24.dp)) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    stringResource(R.string.dialog_profile_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                OutlinedTextField(
                    ageInput,
                    { ageInput = it },
                    Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.label_age)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    shape = RoundedCornerShape(12.dp)
                )
                OutlinedTextField(
                    heightInput,
                    { heightInput = it },
                    Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.label_height)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    shape = RoundedCornerShape(12.dp)
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.label_gender) + ": "); Spacer(Modifier.width(8.dp))
                    GlassChoiceChip(
                        stringResource(R.string.gender_male),
                        selectedGender == 0,
                        { selectedGender = 0 })
                    Spacer(Modifier.width(8.dp))
                    GlassChoiceChip(
                        stringResource(R.string.gender_female),
                        selectedGender == 1,
                        { selectedGender = 1 })
                }
                Row(Modifier.fillMaxWidth(), Arrangement.End) {
                    TextButton(onDismiss) { Text(stringResource(R.string.btn_cancel)) }
                    Spacer(Modifier.width(8.dp))
                    GlassButton(stringResource(R.string.btn_save), onClick = {
                        viewModel.updateProfile(
                            ageInput.toIntOrNull() ?: 0,
                            heightInput.toFloatOrNull() ?: 0f,
                            selectedGender
                        )
                        onDismiss()
                    })
                }
            }
        }
    }
}

@Composable
private fun KeyReferenceDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val selectedKeys = remember { mutableStateListOf<String>() }
    val bodyPartKeys = listOf(
        "part_chest",
        "part_back",
        "part_shoulders",
        "part_arms",
        "part_abs",
        "part_cardio",
        "part_hips",
        "part_thighs",
        "part_calves",
        "part_other"
    )
    val equipKeys = listOf(
        "equip_barbell",
        "equip_dumbbell",
        "equip_machine",
        "equip_cable",
        "equip_bodyweight",
        "equip_cardio_machine",
        "equip_kettlebell",
        "equip_smith_machine",
        "equip_resistance_band",
        "equip_medicine_ball",
        "equip_trx",
        "equip_bench",
        "equip_other"
    )
    Dialog(onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false, // 允许 GlassCard 控制自己的宽度
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )) {
        GlassDialogCard(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxHeight(0.85f),
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(Modifier.padding(24.dp)) {
                Text(
                    stringResource(R.string.help_dialog_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { selectedKeys.clear(); selectedKeys.addAll(bodyPartKeys + equipKeys) }) {
                        Text(
                            stringResource(R.string.btn_select_all),
                            fontSize = 12.sp
                        )
                    }
                    TextButton(onClick = { selectedKeys.clear() }) {
                        Text(
                            stringResource(R.string.btn_clear),
                            fontSize = 12.sp
                        )
                    }
                }
                LazyColumn(Modifier.weight(1f)) {
                    item {
                        Text(
                            stringResource(R.string.section_body_part),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                    items(bodyPartKeys) { k ->
                        SelectableRow(
                            stringResource(getBodyPartResId(k)),
                            k,
                            selectedKeys.contains(k)
                        ) {
                            if (selectedKeys.contains(k)) selectedKeys.remove(k) else selectedKeys.add(
                                k
                            )
                        }
                    }
                    item {
                        Text(
                            stringResource(R.string.section_equipment),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                    items(equipKeys) { k ->
                        SelectableRow(
                            stringResource(getEquipmentResId(k)),
                            k,
                            selectedKeys.contains(k)
                        ) {
                            if (selectedKeys.contains(k)) selectedKeys.remove(k) else selectedKeys.add(
                                k
                            )
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                GlassButton(
                    text = if (selectedKeys.isNotEmpty()) stringResource(
                        R.string.btn_copy_selected,
                        selectedKeys.size
                    ) else stringResource(R.string.btn_done), modifier = Modifier.fillMaxWidth()
                ) {
                    if (selectedKeys.isNotEmpty()) {
                        (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(
                            ClipData.newPlainText("Keys", selectedKeys.joinToString(", "))
                        )
                    }
                    onDismiss()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ManualRoutineDialog(viewModel: MainViewModel, onDismiss: () -> Unit) {
    val pagerState = rememberPagerState { 7 }
    val scope = rememberCoroutineScope()
    val selectedDay = pagerState.currentPage + 1
    val routineItems = remember(selectedDay) { mutableStateListOf<WeeklyRoutineItem>() }
    val allTemplates by viewModel.allTemplates.collectAsState(initial = emptyList())

    // ✅ 新增：控制 BottomSheet 的显示
    var showAddSheet by remember { mutableStateOf(false) }
    LaunchedEffect(selectedDay) {
        routineItems.clear(); routineItems.addAll(
        viewModel.getRoutineForDay(
            selectedDay
        )
    )
    }
    Dialog(onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false, // 允许 GlassCard 控制自己的宽度
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )) {
        GlassDialogCard(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(Modifier.padding(16.dp)) {
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.weekly_plan),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onDismiss) { Icon(Icons.Default.Close, null) }
                }
                ScrollableTabRow(
                    selectedTabIndex = pagerState.currentPage,
                    edgePadding = 0.dp,
                    containerColor = Color.Transparent,
                    indicator = {
                        if (pagerState.currentPage < it.size) TabRowDefaults.SecondaryIndicator(
                            Modifier.tabIndicatorOffset(it[pagerState.currentPage])
                        )
                    }) {
                    (1..7).forEach { day ->
                        Tab(
                            pagerState.currentPage == day - 1,
                            { scope.launch { pagerState.animateScrollToPage(day - 1) } },
                            text = {
                                Text(
                                    stringResource(
                                        when (day) {
                                            1 -> R.string.week_1; 2 -> R.string.week_2; 3 -> R.string.week_3; 4 -> R.string.week_4; 5 -> R.string.week_5; 6 -> R.string.week_6; 7 -> R.string.week_7; else -> R.string.week_1
                                        }
                                    )
                                )
                            })
                    }
                }
                HorizontalPager(pagerState, Modifier.weight(1f)) {
                    Box(Modifier.fillMaxSize()) {
                        if (routineItems.isEmpty()) Box(
                            Modifier.fillMaxSize(),
                            Alignment.Center
                        ) { Text(stringResource(R.string.no_plan), color = Color.Gray) }
                        else LazyColumn(Modifier.fillMaxSize()) {
                            items(routineItems) { item ->
                                GlassCard(
                                    Modifier.padding(vertical = 4.dp),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Row(
                                        Modifier.padding(12.dp),
                                        Arrangement.SpaceBetween,
                                        Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text(
                                                item.name,
                                                style = MaterialTheme.typography.bodyLarge
                                            ); Text(
                                            item.target,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color.Gray
                                        )
                                        }
                                        IconButton({
                                            viewModel.removeRoutineItem(item); routineItems.remove(
                                            item
                                        )
                                        }) {
                                            Icon(
                                                Icons.Default.Delete,
                                                null,
                                                tint = Color.Red.copy(0.6f)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                GlassButton(
                    stringResource(R.string.btn_add),
                    Modifier.fillMaxWidth().padding(top = 16.dp),
                    onClick = { showAddSheet = true }  // ✅ 打开 Sheet
                )
            }
        }
        // ✅ ModalBottomSheet 叠加在 Dialog 的 content 层内，层级正确
        if (showAddSheet) {
            AddRoutineItemSheet(
                allTemplates = allTemplates,
                onDismiss = { showAddSheet = false },
                onSelect = { template ->
                    viewModel.addRoutineItem(selectedDay, template)  // ✅ 签名匹配
                    // 乐观更新本地列表，立即反映在 UI 上
                    routineItems.add(
                        WeeklyRoutineItem(
                            dayOfWeek = selectedDay,
                            templateId = template.id,
                            name = template.name,
                            target = template.defaultTarget,
                            category = template.category,
                            bodyPart = template.bodyPart,
                            equipment = template.equipment,
                            isUnilateral = template.isUnilateral,
                            logType = template.logType
                        )
                    )
                    showAddSheet = false
                }
            )
        }
    }
}


@Composable
fun GlassLanguageChip(l: String, s: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.height(44.dp),
        shape = RoundedCornerShape(12.dp),
        color = if (s) MaterialTheme.colorScheme.primary.copy(alpha = 0.85f) else MaterialTheme.colorScheme.surfaceVariant.copy(
            0.35f
        ),
        border = if (s) null else BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))
    ) {
        Box(Modifier.padding(horizontal = 20.dp), Alignment.Center) {
            Text(
                l,
                style = MaterialTheme.typography.labelLarge,
                color = if (s) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = if (s) FontWeight.Bold else FontWeight.Medium
            )
        }
    }
}

@Composable
fun GlassActionButton(i: ImageVector, l: String, m: Modifier = Modifier, onClick: () -> Unit) {
    GlassCard(m
        .height(56.dp)
        .clickable { onClick() }, RoundedCornerShape(14.dp)) {
        Row(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                i,
                null,
                Modifier.size(20.dp),
                MaterialTheme.colorScheme.primary
            ); Spacer(Modifier.width(8.dp)); Text(
            l,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )
        }
    }
}

@Composable
fun GlassNavBar(i: ImageVector, l: String, onClick: () -> Unit) {
    GlassCard(
        Modifier
            .fillMaxWidth()
            .height(64.dp)
            .clickable { onClick() },
        RoundedCornerShape(16.dp)
    ) {
        Row(Modifier
            .fillMaxSize()
            .padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                i,
                null,
                tint = MaterialTheme.colorScheme.primary
            ); Spacer(Modifier.width(16.dp)); Text(
            l,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        ); Spacer(Modifier.weight(1f)); Icon(
            Icons.Default.ChevronRight,
            null,
            tint = Color.Gray.copy(0.6f)
        )
        }
    }
}

@Composable
fun GlassThemeCircle(t: AppTheme, s: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.size(52.dp),
        shape = CircleShape,
        color = Color(t.primary),
        border = if (s) BorderStroke(
            3.dp,
            MaterialTheme.colorScheme.onBackground
        ) else BorderStroke(1.dp, Color.White.copy(0.4f)),
        tonalElevation = if (s) 4.dp else 0.dp
    ) {
        if (s) Box(contentAlignment = Alignment.Center) {
            Icon(
                Icons.Default.Check,
                null,
                tint = Color.White,
                modifier = Modifier.size(28.dp)
            )
        }
    }
}

@Composable
fun AboutSection() {
    val context = LocalContext.current
    val versionName = remember {
        try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0"
        } catch (e: Exception) {
            "1.0"
        }
    }
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Default.Info, null, tint = MaterialTheme.colorScheme.primary.copy(0.5f))
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.about_version_format, versionName),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            stringResource(R.string.about_credit),
            style = MaterialTheme.typography.labelSmall,
            color = Color.Gray
        )
    }
}

@Composable
fun SelectableRow(l: String, k: String, s: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (s) MaterialTheme.colorScheme.primaryContainer.copy(0.4f) else Color.Transparent)
            .clickable { onToggle() }
            .padding(8.dp),
        Arrangement.SpaceBetween,
        Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                s,
                { onToggle() },
                Modifier
                    .size(32.dp)
                    .padding(end = 8.dp)
            ); Text(l, style = MaterialTheme.typography.bodyMedium)
        }
        Surface(
            color = if (s) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(4.dp)
        ) {
            Text(
                k,
                Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                style = MaterialTheme.typography.labelSmall,
                color = if (s) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun ScheduleItem(config: ScheduleConfig, onTypeChange: (DayType) -> Unit) {
    Card(
        Modifier
            .fillMaxWidth()
            .clickable { onTypeChange(DayType.entries[(config.dayType.ordinal + 1) % DayType.entries.size]) },
        colors = CardDefaults.cardColors(containerColor = Color(config.dayType.colorHex).copy(0.8f)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(Modifier.padding(16.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            Text(
                stringResource(
                    when (config.dayOfWeek) {
                        1 -> R.string.week_1; 2 -> R.string.week_2; 3 -> R.string.week_3; 4 -> R.string.week_4; 5 -> R.string.week_5; 6 -> R.string.week_6; else -> R.string.week_7
                    }
                ), color = Color.White, fontWeight = FontWeight.Bold
            )
            Text(stringResource(config.dayType.labelResId), color = Color.White)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiConfigSection(viewModel: MainViewModel) {
    val settings by viewModel.userProfile.collectAsState()
    val connectionState by viewModel.connectionState.collectAsState()
    val scope = rememberCoroutineScope()
    var expanded by remember { mutableStateOf(false) }
    var apiKey by remember(settings) { mutableStateOf(settings.aiApiKey) }
    var model by remember(settings) { mutableStateOf(settings.aiModel) }
    var baseUrl by remember(settings) { mutableStateOf(settings.aiBaseUrl) }
    var selectedProvider by remember(settings) { mutableStateOf(settings.aiProvider) }
    Column(Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.ai_config_title), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        GlassCard(shape = RoundedCornerShape(16.dp)) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ExposedDropdownMenuBox(expanded, { expanded = !expanded }) {
                    OutlinedTextField(
                        selectedProvider,
                        {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.ai_provider_label)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                        modifier = Modifier
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable, true)
                            .fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    ExposedDropdownMenu(expanded, { expanded = false }) {
                        viewModel.availableProviders.map { it.name }.forEach { p ->
                            DropdownMenuItem(
                                { Text(p) },
                                {
                                    if (selectedProvider != p) scope.launch {
                                        val (k, m, u) = viewModel.getSavedProviderConfig(
                                            p
                                        ); apiKey = k; model = m; baseUrl = u
                                    }; selectedProvider = p; expanded = false
                                })
                        }
                    }
                }
                OutlinedTextField(
                    apiKey,
                    { apiKey = it },
                    Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.ai_api_key_label)) },
                    singleLine = true,
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    shape = RoundedCornerShape(12.dp)
                )
                OutlinedTextField(
                    model,
                    { model = it },
                    Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.ai_model_label)) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )
                OutlinedTextField(
                    baseUrl,
                    { baseUrl = it },
                    Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.ai_base_url_label)) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        when (connectionState) {
                            is MainViewModel.ConnectionState.Testing -> CircularProgressIndicator(
                                Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )

                            is MainViewModel.ConnectionState.Success -> Icon(
                                Icons.Default.CheckCircle,
                                null,
                                tint = Color(0xFF4CAF50)
                            )

                            is MainViewModel.ConnectionState.Error -> Icon(
                                Icons.Default.Error,
                                null,
                                tint = MaterialTheme.colorScheme.error
                            )

                            else -> Icon(
                                Icons.Default.Circle,
                                null,
                                tint = Color.Gray,
                                modifier = Modifier.size(12.dp)
                            )
                        }
                        Spacer(Modifier.width(8.dp)); TextButton(
                        {
                            viewModel.testAiConnection(
                                selectedProvider,
                                apiKey,
                                model,
                                baseUrl
                            )
                        },
                        enabled = apiKey.isNotBlank()
                    ) { Text(stringResource(R.string.btn_test_connection)) }
                    }
                    GlassButton(
                        stringResource(R.string.btn_save_config),
                        onClick = {
                            viewModel.saveAiConfig(
                                selectedProvider,
                                apiKey,
                                model,
                                baseUrl
                            )
                        })
                }
            }
        }
    }
}

@Composable
fun TimerSettingsSection(viewModel: MainViewModel) {
    var prepEnabled by remember { mutableStateOf(viewModel.getTimerPrepEnabled()) }
    var prepSecs by remember { mutableIntStateOf(viewModel.getTimerPrepSeconds()) }
    var finalEnabled by remember { mutableStateOf(viewModel.getTimerFinalEnabled()) }
    var finalSecs by remember { mutableIntStateOf(viewModel.getTimerFinalSeconds()) }
    var soundEnabled by remember { mutableStateOf(viewModel.getTimerSoundEnabled()) }
    Column(Modifier.fillMaxWidth()) {
        Text(
            stringResource(R.string.settings_timer_title),
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(Modifier.height(8.dp))
        GlassCard(shape = RoundedCornerShape(16.dp)) {
            Column(Modifier.padding(12.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    Arrangement.SpaceBetween,
                    Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            stringResource(R.string.timer_sound_title),
                            fontWeight = FontWeight.Bold
                        ); Text(
                        stringResource(R.string.timer_sound_desc),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.Gray
                    )
                    }; Switch(
                    soundEnabled,
                    { soundEnabled = it; viewModel.setTimerSoundEnabled(it) })
                }
                HorizontalDivider(
                    Modifier.padding(vertical = 12.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(0.3f)
                )
                Row(
                    Modifier.fillMaxWidth(),
                    Arrangement.SpaceBetween,
                    Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            stringResource(R.string.timer_prep_title),
                            fontWeight = FontWeight.Bold
                        ); Text(
                        stringResource(R.string.timer_prep_desc),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.Gray
                    )
                    }; Switch(prepEnabled, { prepEnabled = it; viewModel.setTimerPrepEnabled(it) })
                }
                AnimatedVisibility(prepEnabled) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 8.dp)
                    ) {
                        Text(stringResource(R.string.label_duration_sec), fontSize = 12.sp); Spacer(
                        Modifier.width(16.dp)
                    ); NumberAdjuster(prepSecs, 3..30) {
                        prepSecs = it; viewModel.setTimerPrepSeconds(it)
                    }
                    }
                }
                HorizontalDivider(
                    Modifier.padding(vertical = 12.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(0.3f)
                )
                Row(
                    Modifier.fillMaxWidth(),
                    Arrangement.SpaceBetween,
                    Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            stringResource(R.string.timer_final_title),
                            fontWeight = FontWeight.Bold
                        ); Text(
                        stringResource(R.string.timer_final_desc),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.Gray
                    )
                    }; Switch(
                    finalEnabled,
                    { finalEnabled = it; viewModel.setTimerFinalEnabled(it) })
                }
                AnimatedVisibility(finalEnabled) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 8.dp)
                    ) {
                        Text(stringResource(R.string.label_duration_sec), fontSize = 12.sp); Spacer(
                        Modifier.width(16.dp)
                    ); NumberAdjuster(finalSecs, 3..15) {
                        finalSecs = it; viewModel.setTimerFinalSeconds(it)
                    }
                    }
                }
            }
        }
    }
}

@Composable
fun NumberAdjuster(value: Int, range: IntRange, onValueChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(
            onClick = { if (value > range.first) onValueChange(value - 1) },
            modifier = Modifier
                .size(36.dp)
                .background(
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    CircleShape
                )
        ) { Text("-", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp) }
        Text(
            "$value",
            Modifier.width(40.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            fontWeight = FontWeight.Bold
        )
        IconButton(
            onClick = { if (value < range.last) onValueChange(value + 1) },
            modifier = Modifier
                .size(36.dp)
                .background(
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    CircleShape
                )
        ) { Text("+", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp) }
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddRoutineItemSheet(
    allTemplates: List<ExerciseTemplate>,
    onDismiss: () -> Unit,
    onSelect: (ExerciseTemplate) -> Unit
) {
    val categories = listOf("STRENGTH", "CARDIO", "CORE")
    var selectedCategory by remember { mutableStateOf("STRENGTH") }

    val glassMode = LocalGlassMode.current
    val useGlass = glassMode && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color.Transparent,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        if (useGlass) {
            // ── 玻璃模式：一个统一的 backdrop 覆盖整个 Sheet 内容区 ──────
            val sheetBackdrop = rememberLayerBackdrop()
            val isDark = isSystemInDarkTheme()

            // Sheet 整体底色：亮色用半透明白补偿 scrim 压暗，深色用极淡白
            val sheetBgColor = remember(isDark) {
                if (isDark) Color.White.copy(alpha = 0.07f)
                else Color.White.copy(alpha = 0.72f)
            }

            Box(modifier = Modifier.fillMaxWidth()) {
                // 第一层：整个 Sheet 的 backdrop 捕获源
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .layerBackdrop(sheetBackdrop)
                        .background(sheetBgColor)
                )

                // 第二层：内容，把 sheetBackdrop 注入 CompositionLocal
                // 供内部 GlassCard 等组件按需使用
                CompositionLocalProvider(LocalBackdrop provides sheetBackdrop) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 32.dp)
                    ) {
                        // ── LiquidBottomTabs：Tab 区域再建一层局部 backdrop ──
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            val tabBackdrop = rememberLayerBackdrop()
                            val tabBrush = remember(isDark) {
                                Brush.linearGradient(
                                    colors = if (isDark)
                                        listOf(Color.White.copy(0.10f), Color.White.copy(0.04f))
                                    else
                                        listOf(Color.White.copy(0.55f), Color.White.copy(0.25f)),
                                    start = Offset.Zero,
                                    end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
                                )
                            }
                            // Tab 区域的捕获源（渐变圆角背景）
                            Box(
                                modifier = Modifier
                                    .matchParentSize()
                                    .layerBackdrop(tabBackdrop)
                                    .background(tabBrush, RoundedCornerShape(50))
                            )
                            LiquidBottomTabs(
                                selectedTabIndex = { categories.indexOf(selectedCategory) },
                                onTabSelected = { selectedCategory = categories[it] },
                                backdrop = tabBackdrop,
                                tabsCount = categories.size,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                categories.forEach { category ->
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
                                            color = if (selectedCategory == category)
                                                MaterialTheme.colorScheme.primary
                                            else
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }

                        // ── 动作列表 ──────────────────────────────────────
                        LazyColumn(modifier = Modifier.fillMaxWidth()) {
                            val filtered = allTemplates.filter { it.category == selectedCategory }
                            if (filtered.isEmpty()) {
                                item {
                                    Box(Modifier.fillMaxWidth().padding(48.dp), Alignment.Center) {
                                        Text(stringResource(R.string.chart_no_data), color = Color.Gray)
                                    }
                                }
                            } else {
                                items(filtered) { t ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { onSelect(t) }
                                            .padding(horizontal = 20.dp, vertical = 16.dp)
                                    ) {
                                        Column {
                                            Text(t.name, style = MaterialTheme.typography.bodyLarge)
                                            if (t.bodyPart.isNotBlank()) {
                                                Text(
                                                    t.bodyPart,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = Color.Gray
                                                )
                                            }
                                        }
                                    }
                                    HorizontalDivider(
                                        modifier = Modifier.padding(horizontal = 20.dp),
                                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        } else {
            // ── Fallback：普通模式 ────────────────────────────────────────
            Column(modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp)) {
                TabRow(
                    selectedTabIndex = categories.indexOf(selectedCategory),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    containerColor = Color.Transparent,
                    contentColor = MaterialTheme.colorScheme.primary
                ) {
                    categories.forEach { category ->
                        Tab(
                            selected = selectedCategory == category,
                            onClick = { selectedCategory = category },
                            text = {
                                Text(
                                    stringResource(getCategoryResId(category)),
                                    fontSize = 13.sp,
                                    fontWeight = if (selectedCategory == category) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        )
                    }
                }
                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    val filtered = allTemplates.filter { it.category == selectedCategory }
                    if (filtered.isEmpty()) {
                        item {
                            Box(Modifier.fillMaxWidth().padding(48.dp), Alignment.Center) {
                                Text(stringResource(R.string.chart_no_data), color = Color.Gray)
                            }
                        }
                    } else {
                        items(filtered) { t ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelect(t) }
                                    .padding(horizontal = 20.dp, vertical = 16.dp)
                            ) {
                                Column {
                                    Text(t.name, style = MaterialTheme.typography.bodyLarge)
                                    if (t.bodyPart.isNotBlank()) {
                                        Text(
                                            t.bodyPart,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color.Gray
                                        )
                                    }
                                }
                            }
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 20.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                            )
                        }
                    }
                }
            }
        }
    }
}
