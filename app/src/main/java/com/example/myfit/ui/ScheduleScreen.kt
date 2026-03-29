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
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.example.myfit.R
import com.example.myfit.model.*
import com.example.myfit.ui.components.GlassButton
import com.example.myfit.ui.components.GlassCard
import com.example.myfit.ui.components.GlassScaffoldContent
import com.example.myfit.ui.components.LocalBackdrop
import com.example.myfit.ui.components.LocalGlassMode
import com.example.myfit.viewmodel.MainViewModel
import com.kyant.shapes.Capsule
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate

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

    val exportCsvLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        uri?.let { viewModel.exportHistoryToCsv(it, context) }
    }
    val createBackupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/x-sqlite3")) { uri ->
        uri?.let { viewModel.backupDatabase(it, context) }
    }
    val restoreBackupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(top = 24.dp)
        ) {
            // Fixed Header
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
                // Language Section
                item {
                    Column {
                        Text(
                            stringResource(R.string.settings_language),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.horizontalScroll(rememberScrollState())
                        ) {
                            listOf("zh", "en", "es", "ja", "de").forEach { code ->
                                val label = when (code) {
                                    "zh" -> "中文"; "en" -> "EN"; "es" -> "ES"; "ja" -> "JA"; "de" -> "DE"; else -> ""
                                }
                                GlassLanguageChip(label, code == currentLanguage) {
                                    scope.launch {
                                        viewModel.switchLanguage(code)
                                        delay(500)
                                        (context as? Activity)?.recreate()
                                    }
                                }
                            }
                        }
                    }
                }

                // Data Management Section
                item {
                    Column {
                        Text(
                            stringResource(R.string.settings_data_management),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            GlassActionButton(Icons.Default.Share, stringResource(R.string.export_csv_btn), Modifier.weight(1f)) {
                                exportCsvLauncher.launch("myfit_history_${LocalDate.now()}.csv")
                            }
                            GlassActionButton(Icons.Default.Upload, stringResource(R.string.import_csv_btn), Modifier.weight(1f)) {
                                showImportDialog = true
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            GlassActionButton(Icons.Default.HelpOutline, stringResource(R.string.settings_help_reference), Modifier.weight(1f)) {
                                showHelpDialog = true
                            }
                            GlassActionButton(Icons.Default.Person, stringResource(R.string.settings_profile), Modifier.weight(1f)) {
                                showProfileDialog = true
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            GlassActionButton(Icons.Default.Backup, stringResource(R.string.btn_backup_db), Modifier.weight(1f)) {
                                createBackupLauncher.launch("myfit_backup_${LocalDate.now()}.db")
                            }
                            GlassActionButton(Icons.Default.Restore, stringResource(R.string.btn_restore_db), Modifier.weight(1f)) {
                                restoreBackupLauncher.launch(arrayOf("*/*"))
                            }
                        }
                    }
                }

                // Weekly Plan Bar
                item {
                    GlassNavBar(Icons.Default.EditCalendar, stringResource(R.string.weekly_plan)) {
                        showManualRoutineDialog = true
                    }
                }

                // Library Bar
                item {
                    GlassNavBar(Icons.AutoMirrored.Filled.List, stringResource(R.string.manage_lib)) {
                        navController.navigate("exercise_manager")
                    }
                }

                // AI Configuration Section
                item { AiConfigSection(viewModel) }

                // Timer Settings Section
                item { TimerSettingsSection(viewModel) }

                // Theme Style Section
                item {
                    Column {
                        Text(
                            stringResource(R.string.theme_style),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                            AppTheme.entries.forEach { theme ->
                                GlassThemeCircle(theme, currentTheme == theme) {
                                    viewModel.switchTheme(theme)
                                }
                            }
                        }
                    }
                }

                // Schedule Type Title
                item {
                    Text(
                        stringResource(R.string.schedule_type_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                // Schedule List Items
                items(scheduleList) { config ->
                    ScheduleItem(config) {
                        viewModel.updateScheduleConfig(config.dayOfWeek, it)
                    }
                }

                // Spacer for Floating Bottom Bar
                item {
                    AboutSection()
                    Spacer(modifier = Modifier.height(140.dp))
                }
            }
        }
    }

    // Dialogs
    if (showImportDialog) ImportDialog(stringResource(R.string.import_csv_template), { showImportDialog = false }) {
        viewModel.importWeeklyRoutine(context, it)
        showImportDialog = false
    }
    if (showHelpDialog) KeyReferenceDialog { showHelpDialog = false }
    if (showManualRoutineDialog) ManualRoutineDialog(viewModel) { showManualRoutineDialog = false }
    if (showProfileDialog) ProfileEditDialog(viewModel) { showProfileDialog = false }
}

@Composable
fun GlassLanguageChip(label: String, isSelected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.height(44.dp),
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.85f) 
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        border = if (isSelected) null else BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))
    ) {
        Box(modifier = Modifier.padding(horizontal = 20.dp), contentAlignment = Alignment.Center) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
            )
        }
    }
}

@Composable
fun GlassActionButton(icon: ImageVector, label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    GlassCard(
        modifier = modifier.height(56.dp).clickable { onClick() },
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(icon, null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = label, 
                fontSize = 12.sp, 
                color = MaterialTheme.colorScheme.onSurface, 
                maxLines = 1, 
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
fun GlassNavBar(icon: ImageVector, label: String, onClick: () -> Unit) {
    GlassCard(
        modifier = Modifier.fillMaxWidth().height(64.dp).clickable { onClick() },
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.width(16.dp))
            Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.weight(1f))
            Icon(Icons.Default.ChevronRight, null, tint = Color.Gray.copy(alpha = 0.6f))
        }
    }
}

@Composable
fun GlassThemeCircle(theme: AppTheme, isSelected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.size(52.dp),
        shape = CircleShape,
        color = Color(theme.primary),
        border = if (isSelected) BorderStroke(3.dp, MaterialTheme.colorScheme.onBackground) 
                 else BorderStroke(1.dp, Color.White.copy(alpha = 0.4f)),
        tonalElevation = if (isSelected) 4.dp else 0.dp
    ) {
        if (isSelected) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(28.dp))
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
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_profile_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(ageInput, { ageInput = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.label_age)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), shape = RoundedCornerShape(12.dp))
                OutlinedTextField(heightInput, { heightInput = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.label_height)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), shape = RoundedCornerShape(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.label_gender) + ": ")
                    Spacer(modifier = Modifier.width(8.dp))
                    FilterChip(selectedGender == 0, { selectedGender = 0 }, label = { Text(stringResource(R.string.gender_male)) })
                    Spacer(modifier = Modifier.width(8.dp))
                    FilterChip(selectedGender == 1, { selectedGender = 1 }, label = { Text(stringResource(R.string.gender_female)) })
                }
            }
        },
        confirmButton = {
            GlassButton(text = stringResource(R.string.btn_save)) {
                viewModel.updateProfile(ageInput.toIntOrNull() ?: 0, heightInput.toFloatOrNull() ?: 0f, selectedGender)
                onDismiss()
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.btn_cancel)) } }
    )
}

@Composable
fun TimerSettingsSection(viewModel: MainViewModel) {
    var prepEnabled by remember { mutableStateOf(viewModel.getTimerPrepEnabled()) }
    var prepSecs by remember { mutableIntStateOf(viewModel.getTimerPrepSeconds()) }
    var finalEnabled by remember { mutableStateOf(viewModel.getTimerFinalEnabled()) }
    var finalSecs by remember { mutableIntStateOf(viewModel.getTimerFinalSeconds()) }
    var soundEnabled by remember { mutableStateOf(viewModel.getTimerSoundEnabled()) }
    
    Column(Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.settings_timer_title), style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))
        GlassCard(shape = RoundedCornerShape(16.dp)) {
            Column(Modifier.padding(12.dp)) {
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                    Column {
                        Text(stringResource(R.string.timer_sound_title), fontWeight = FontWeight.Bold)
                        Text(stringResource(R.string.timer_sound_desc), style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                    }
                    Switch(soundEnabled, { soundEnabled = it; viewModel.setTimerSoundEnabled(it) })
                }
                HorizontalDivider(Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(0.3f))
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                    Column {
                        Text(stringResource(R.string.timer_prep_title), fontWeight = FontWeight.Bold)
                        Text(stringResource(R.string.timer_prep_desc), style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                    }
                    Switch(prepEnabled, { prepEnabled = it; viewModel.setTimerPrepEnabled(it) })
                }
                AnimatedVisibility(prepEnabled) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                        Text(stringResource(R.string.label_duration_sec), fontSize = 12.sp)
                        Spacer(Modifier.width(16.dp))
                        NumberAdjuster(prepSecs, 3..30) { prepSecs = it; viewModel.setTimerPrepSeconds(it) }
                    }
                }
                HorizontalDivider(Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(0.3f))
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                    Column {
                        Text(stringResource(R.string.timer_final_title), fontWeight = FontWeight.Bold)
                        Text(stringResource(R.string.timer_final_desc), style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                    }
                    Switch(finalEnabled, { finalEnabled = it; viewModel.setTimerFinalEnabled(it) })
                }
                AnimatedVisibility(finalEnabled) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                        Text(stringResource(R.string.label_duration_sec), fontSize = 12.sp)
                        Spacer(Modifier.width(16.dp))
                        NumberAdjuster(finalSecs, 3..15) { finalSecs = it; viewModel.setTimerFinalSeconds(it) }
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
            modifier = Modifier.size(36.dp).background(MaterialTheme.colorScheme.surfaceVariant.copy(0.5f), CircleShape)
        ) { Text("-", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp) }
        Text("$value", Modifier.width(40.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center, fontWeight = FontWeight.Bold)
        IconButton(
            onClick = { if (value < range.last) onValueChange(value + 1) },
            modifier = Modifier.size(36.dp).background(MaterialTheme.colorScheme.surfaceVariant.copy(0.5f), CircleShape)
        ) { Text("+", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp) }
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
                    OutlinedTextField(selectedProvider, {}, readOnly = true, label = { Text(stringResource(R.string.ai_provider_label)) }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) }, modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable, true).fillMaxWidth(), shape = RoundedCornerShape(12.dp))
                    ExposedDropdownMenu(expanded, { expanded = false }) {
                        viewModel.availableProviders.map { it.name }.forEach { p ->
                            DropdownMenuItem({ Text(p) }, {
                                if (selectedProvider != p) scope.launch {
                                    val (k, m, u) = viewModel.getSavedProviderConfig(p)
                                    apiKey = k; model = m; baseUrl = u
                                }
                                selectedProvider = p; expanded = false
                            })
                        }
                    }
                }
                OutlinedTextField(apiKey, { apiKey = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.ai_api_key_label)) }, singleLine = true, visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(), shape = RoundedCornerShape(12.dp))
                OutlinedTextField(model, { model = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.ai_model_label)) }, singleLine = true, shape = RoundedCornerShape(12.dp))
                OutlinedTextField(baseUrl, { baseUrl = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.ai_base_url_label)) }, singleLine = true, shape = RoundedCornerShape(12.dp))
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        when (connectionState) {
                            is MainViewModel.ConnectionState.Testing -> CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                            is MainViewModel.ConnectionState.Success -> Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF4CAF50))
                            is MainViewModel.ConnectionState.Error -> Icon(Icons.Default.Error, null, tint = MaterialTheme.colorScheme.error)
                            else -> Icon(Icons.Default.Circle, null, tint = Color.Gray, modifier = Modifier.size(12.dp))
                        }
                        Spacer(Modifier.width(8.dp))
                        TextButton({ viewModel.testAiConnection(selectedProvider, apiKey, model, baseUrl) }, enabled = apiKey.isNotBlank()) {
                            Text(stringResource(R.string.btn_test_connection))
                        }
                    }
                    // [修复点]：使用 GlassButton
                    GlassButton(
                        text = stringResource(R.string.btn_save_config),
                        onClick = { viewModel.saveAiConfig(selectedProvider, apiKey, model, baseUrl) }
                    )
                }
                if (connectionState is MainViewModel.ConnectionState.Error) Text((connectionState as MainViewModel.ConnectionState.Error).message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
fun ScheduleItem(config: ScheduleConfig, onTypeChange: (DayType) -> Unit) {
    val weekRes = when (config.dayOfWeek) {
        1 -> R.string.week_1; 2 -> R.string.week_2; 3 -> R.string.week_3; 4 -> R.string.week_4; 5 -> R.string.week_5; 6 -> R.string.week_6; 7 -> R.string.week_7; else -> R.string.week_1
    }
    Card(
        Modifier.fillMaxWidth().clickable { onTypeChange(DayType.entries[(config.dayType.ordinal + 1) % DayType.entries.size]) },
        colors = CardDefaults.cardColors(containerColor = Color(config.dayType.colorHex).copy(0.8f)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(Modifier.padding(16.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            Text(stringResource(weekRes), color = Color.White, fontWeight = FontWeight.Bold)
            Text(stringResource(config.dayType.labelResId), color = Color.White)
        }
    }
}

@Composable
fun AboutSection() {
    val context = LocalContext.current
    val versionName = remember { try { context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0" } catch (e: Exception) { "1.0" } }
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Default.Info, null, tint = MaterialTheme.colorScheme.primary.copy(0.5f))
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.about_version_format, versionName), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
        Text(stringResource(R.string.about_credit), style = MaterialTheme.typography.labelSmall, color = Color.Gray)
    }
}

@Composable
fun ImportDialog(defaultText: String, onDismiss: () -> Unit, onImport: (String) -> Unit) {
    var text by remember { mutableStateOf(defaultText) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(R.string.import_dialog_title)) }, 
        text = { Column { Text(stringResource(R.string.import_dialog_hint), fontSize = 12.sp, color = Color.Gray); OutlinedTextField(text, { text = it }, Modifier.fillMaxWidth().height(200.dp), textStyle = MaterialTheme.typography.bodySmall, shape = RoundedCornerShape(12.dp)) } }, 
        confirmButton = { 
            GlassButton(text = stringResource(R.string.import_btn)) { onImport(text) }
        }, 
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.btn_cancel)) } })
}

@Composable
private fun KeyReferenceDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val selectedKeys = remember { mutableStateListOf<String>() }
    val bodyPartKeys = listOf("part_chest", "part_back", "part_shoulders", "part_arms", "part_abs", "part_cardio", "part_hips", "part_thighs", "part_calves", "part_other")
    val equipKeys = listOf("equip_barbell", "equip_dumbbell", "equip_machine", "equip_cable", "equip_bodyweight", "equip_cardio_machine", "equip_kettlebell", "equip_smith_machine", "equip_resistance_band", "equip_medicine_ball", "equip_trx", "equip_bench", "equip_other")
    val allKeys = bodyPartKeys + equipKeys

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Column { Text(stringResource(R.string.help_dialog_title)); Row(Modifier.fillMaxWidth(), Arrangement.End) { if(selectedKeys.size < allKeys.size) TextButton(onClick = { selectedKeys.clear(); selectedKeys.addAll(allKeys) }) { Text(stringResource(R.string.btn_select_all), fontSize = 12.sp) }; if(selectedKeys.isNotEmpty()) TextButton(onClick = { selectedKeys.clear() }) { Text(stringResource(R.string.btn_clear), fontSize = 12.sp) } } } },
        text = {
            Column(Modifier.heightIn(max = 400.dp)) {
                Text(stringResource(R.string.help_dialog_multiselect_hint), fontSize = 12.sp, color = Color.Gray); Spacer(Modifier.height(8.dp)); HorizontalDivider()
                LazyColumn(Modifier.weight(1f)) {
                    item { Text(stringResource(R.string.section_body_part), Modifier.padding(top = 16.dp, bottom = 8.dp), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge) }
                    items(bodyPartKeys) { key -> SelectableRow(stringResource(getBodyPartResId(key)), key, selectedKeys.contains(key)) { if(selectedKeys.contains(key)) selectedKeys.remove(key) else selectedKeys.add(key) } }
                    item { Text(stringResource(R.string.section_equipment), Modifier.padding(top = 16.dp, bottom = 8.dp), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge) }
                    items(equipKeys) { key -> SelectableRow(stringResource(getEquipmentResId(key)), key, selectedKeys.contains(key)) { if(selectedKeys.contains(key)) selectedKeys.remove(key) else selectedKeys.add(key) } }
                }
            }
        },
        confirmButton = { 
            GlassButton(
                text = if(selectedKeys.isNotEmpty()) stringResource(R.string.btn_copy_selected, selectedKeys.size) else stringResource(R.string.btn_done),
                onClick = { 
                    if(selectedKeys.isNotEmpty()) { 
                        (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Keys", selectedKeys.joinToString(", ")))
                        Toast.makeText(context, context.getString(R.string.msg_copied_count, selectedKeys.size), Toast.LENGTH_SHORT).show()
                        selectedKeys.clear() 
                    } else onDismiss()
                }
            )
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.btn_cancel)) } }
    )
}

@Composable
private fun SelectableRow(label: String, key: String, isSelected: Boolean, onToggle: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(if(isSelected) MaterialTheme.colorScheme.primaryContainer.copy(0.4f) else Color.Transparent).clickable { onToggle() }.padding(8.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
        Row(verticalAlignment =Alignment.CenterVertically) { Checkbox(isSelected, { onToggle() }, Modifier.size(32.dp).padding(end = 8.dp)); Text(label, style = MaterialTheme.typography.bodyMedium) }
        Surface(color = if(isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(4.dp)) { Text(key, Modifier.padding(horizontal = 6.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall, color = if(isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant) }
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
    var showTemplateSelector by remember { mutableStateOf(false) }

    LaunchedEffect(selectedDay) { routineItems.clear(); routineItems.addAll(viewModel.getRoutineForDay(selectedDay)) }

    AlertDialog(
        onDismissRequest = onDismiss, modifier = Modifier.fillMaxSize().padding(16.dp), properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
        title = { Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) { Text(stringResource(R.string.weekly_plan)); IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, null) } } },
        text = {
            Column(Modifier.fillMaxSize()) {
                ScrollableTabRow(selectedTabIndex = pagerState.currentPage, edgePadding = 0.dp, containerColor = Color.Transparent, indicator = { if (pagerState.currentPage < it.size) TabRowDefaults.SecondaryIndicator(Modifier.tabIndicatorOffset(it[pagerState.currentPage])) }) {
                    (1..7).forEach { day -> Tab(pagerState.currentPage == day - 1, { scope.launch { pagerState.animateScrollToPage(day - 1) } }, text = { Text(stringResource(when(day){1->R.string.week_1;2->R.string.week_2;3->R.string.week_3;4->R.string.week_4;5->R.string.week_5;6->R.string.week_6;7->R.string.week_7;else->R.string.week_1})) }) }
                }
                HorizontalPager(state = pagerState, modifier = Modifier.weight(1f)) {
                    Box(Modifier.fillMaxSize(), Alignment.TopStart) {
                        if (routineItems.isEmpty()) Box(Modifier.fillMaxSize(), Alignment.Center) { Text(stringResource(R.string.no_plan), color = Color.Gray) }
                        else LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) { 
                            items(routineItems) { item -> 
                                GlassCard(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), shape = RoundedCornerShape(12.dp)) {
                                    Row(Modifier.padding(12.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) { 
                                        Column { Text(item.name, style = MaterialTheme.typography.titleMedium); Text(item.target, style = MaterialTheme.typography.bodySmall, color = Color.Gray) }
                                        IconButton(onClick = { viewModel.removeRoutineItem(item); routineItems.remove(item) }) { Icon(Icons.Default.Delete, null, tint = Color.Red.copy(0.5f)) } 
                                    }
                                }
                            } 
                        }
                    }
                }
                GlassButton(text = stringResource(R.string.btn_add), modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { showTemplateSelector = true }
            }
        }, confirmButton = {}
    )

    if (showTemplateSelector) {
        val categories = listOf("STRENGTH", "CARDIO", "CORE")
        var selectedCategory by remember { mutableStateOf("STRENGTH") }
        ModalBottomSheet(onDismissRequest = { showTemplateSelector = false }) {
            Column(Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.manage_lib), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(16.dp))
                TabRow(categories.indexOf(selectedCategory), containerColor = Color.Transparent) { categories.forEach { c -> Tab(selectedCategory == c, { selectedCategory = c }, text = { Text(stringResource(when(c){"STRENGTH"->R.string.category_strength;"CARDIO"->R.string.category_cardio;else->R.string.category_core})) }) } }
                LazyColumn(Modifier.weight(1f, false)) {
                    val filtered = allTemplates.filter { it.category == selectedCategory }
                    if (filtered.isEmpty()) item { Box(Modifier.fillMaxWidth().padding(32.dp), Alignment.Center) { Text(stringResource(R.string.chart_no_data), color = Color.Gray) } }
                    else items(filtered) { t -> Row(Modifier.fillMaxWidth().clickable { viewModel.addRoutineItem(selectedDay, t); scope.launch { routineItems.clear(); routineItems.addAll(viewModel.getRoutineForDay(selectedDay)) }; showTemplateSelector = false }.padding(16.dp)) { Text(t.name) }; HorizontalDivider(Modifier.padding(horizontal = 16.dp)) }
                }
                Spacer(Modifier.height(40.dp))
            }
        }
    }
}
