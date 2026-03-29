package com.example.myfit.ui

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.example.myfit.R
import com.example.myfit.model.ExerciseTemplate
import com.example.myfit.model.LogType
import com.example.myfit.ui.components.GlassCard
import com.example.myfit.ui.components.GlassScaffoldContent
import com.example.myfit.ui.components.LocalBackdrop
import com.example.myfit.ui.components.liquidtabs.LiquidBottomTabs
import com.example.myfit.ui.components.liquidtabs.LocalLiquidBottomTabScale
import com.example.myfit.viewmodel.MainViewModel
import com.kyant.shapes.Capsule
import java.io.File
import java.io.FileOutputStream

val BODY_PART_OPTIONS = listOf(
    "part_chest", "part_back", "part_shoulders",
    "part_hips", "part_thighs", "part_calves",
    "part_arms", "part_abs", "part_cardio", "part_other"
)

val EQUIPMENT_OPTIONS = listOf(
    "equip_barbell", "equip_dumbbell", "equip_machine", "equip_cable",
    "equip_bodyweight", "equip_cardio_machine", "equip_kettlebell",
    "equip_smith_machine", "equip_resistance_band", "equip_medicine_ball",
    "equip_trx", "equip_bench", "equip_other"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseManagerScreen(viewModel: MainViewModel, navController: NavController) {
    val templates by viewModel.allTemplates.collectAsState(initial = emptyList())
    val currentLanguage by viewModel.currentLanguage.collectAsState(initial = "zh")
    val context = LocalContext.current
    var showEditDialog by remember { mutableStateOf(false) }
    var editingTemplate by remember { mutableStateOf<ExerciseTemplate?>(null) }
    var showDetailDialog by remember { mutableStateOf(false) }
    var viewingTemplate by remember { mutableStateOf<ExerciseTemplate?>(null) }
    var showResetDialog by remember { mutableStateOf(false) }
    var isSearching by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    val categories = listOf("STRENGTH", "CARDIO", "CORE")
    var selectedCategory by remember { mutableStateOf("STRENGTH") }
    val pagerState = rememberPagerState(pageCount = { categories.size })

    LaunchedEffect(selectedCategory) { pagerState.animateScrollToPage(categories.indexOf(selectedCategory)) }
    LaunchedEffect(pagerState.currentPage) { selectedCategory = categories[pagerState.currentPage] }

    val backdrop = LocalBackdrop.current

    GlassScaffoldContent {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                Column(modifier = Modifier.statusBarsPadding().padding(top = 24.dp)) {
                    if (isSearching) {
                        ExerciseSearchBar(
                            query = searchQuery,
                            onQueryChange = { searchQuery = it },
                            onBack = { isSearching = false; searchQuery = "" },
                            onClear = { searchQuery = "" }
                        )
                    } else {
                        CenterAlignedTopAppBar(
                            title = { Text(stringResource(R.string.title_manage_exercises), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) },
                            navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } },
                            actions = {
                                IconButton(onClick = { isSearching = true }) { Icon(Icons.Default.Search, null) }
                                IconButton(onClick = { showResetDialog = true }) { Icon(Icons.Default.Refresh, null, tint = MaterialTheme.colorScheme.primary) }
                            },
                            colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent)
                        )
                        
                        if (backdrop != null) {
                            LiquidBottomTabs(
                                selectedTabIndex = { categories.indexOf(selectedCategory) },
                                onTabSelected = { selectedCategory = categories[it] },
                                backdrop = backdrop,
                                tabsCount = categories.size,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            ) {
                                categories.forEachIndexed { index, category ->
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
                                            color = if (selectedCategory == category) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            floatingActionButton = {
                FloatingActionButton(onClick = { editingTemplate = null; showEditDialog = true }, containerColor = MaterialTheme.colorScheme.primary, shape = RoundedCornerShape(16.dp)) {
                    Icon(Icons.Default.Add, null, tint = Color.White)
                }
            }
        ) { padding ->
            HorizontalPager(state = pagerState, modifier = Modifier.padding(padding)) { page ->
                val category = categories[page]
                val filtered = templates.filter { it.category == category && (searchQuery.isBlank() || it.name.contains(searchQuery, ignoreCase = true)) }
                LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    item { Spacer(Modifier.height(8.dp)) }
                    items(filtered, key = { it.id }) { template ->
                        ExerciseMinimalCard(template = template, onClick = { viewingTemplate = template; showDetailDialog = true }, onDelete = { viewModel.deleteTemplate(template.id) })
                    }
                    item { Spacer(Modifier.height(100.dp)) }
                }
            }
        }
    }

    if (showResetDialog) AlertDialog(onDismissRequest = { showResetDialog = false }, title = { Text(stringResource(R.string.title_reset_exercises)) }, text = { Text(stringResource(R.string.msg_reset_exercises_warning)) }, confirmButton = { Button(onClick = { viewModel.reloadStandardExercises(context, currentLanguage); showResetDialog = false }) { Text(stringResource(R.string.btn_confirm)) } }, dismissButton = { TextButton(onClick = { showResetDialog = false }) { Text(stringResource(R.string.btn_cancel)) } })
    if (showDetailDialog && viewingTemplate != null) ExerciseDetailDialog(template = viewingTemplate!!, onDismiss = { showDetailDialog = false }, onEdit = { editingTemplate = viewingTemplate; viewingTemplate = null; showDetailDialog = false; showEditDialog = true })
    if (showEditDialog) ExerciseEditDialog(template = editingTemplate, onDismiss = { showEditDialog = false }, onSave = { viewModel.saveTemplate(it); showEditDialog = false })
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ExerciseEditDialog(template: ExerciseTemplate?, onDismiss: () -> Unit, onSave: (ExerciseTemplate) -> Unit) {
    var name by remember { mutableStateOf(template?.name ?: "") }; var target by remember { mutableStateOf(template?.defaultTarget ?: "") }; var category by remember { mutableStateOf(template?.category ?: "STRENGTH") }; var bodyPart by remember { mutableStateOf(template?.bodyPart ?: "part_chest") }; var equipment by remember { mutableStateOf(template?.equipment ?: "equip_barbell") }; var isUnilateral by remember { mutableStateOf(template?.isUnilateral ?: false) }; var instruction by remember { mutableStateOf(template?.instruction ?: "") }; var logType by remember { mutableIntStateOf(template?.logType ?: LogType.WEIGHT_REPS.value) }; var imageUri by remember { mutableStateOf(template?.imageUri) }
    val context = LocalContext.current
    LaunchedEffect(category) { if (template == null) logType = when(category) { "CARDIO", "CORE" -> LogType.DURATION.value; else -> LogType.WEIGHT_REPS.value } }

    fun saveImage(uri: Uri): String? = try { val inputStream = context.contentResolver.openInputStream(uri); val file = File(context.filesDir, "img_${System.currentTimeMillis()}.jpg"); val outputStream = FileOutputStream(file); inputStream?.copyTo(outputStream); inputStream?.close(); outputStream.close(); file.absolutePath } catch(e: Exception) { e.printStackTrace(); null }
    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { it?.let { uri -> saveImage(uri)?.let { imageUri = it } } }
    var tempCameraUri by remember { mutableStateOf<Uri?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { if(it && tempCameraUri != null) saveImage(tempCameraUri!!)?.let { imageUri = it } }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { if(it) { val uri = try { val f = File.createTempFile("cam_", ".jpg", context.cacheDir).apply { createNewFile(); deleteOnExit() }; FileProvider.getUriForFile(context, "${context.packageName}.provider", f) } catch(e: Exception) { null }; if(uri != null) { tempCameraUri = uri; cameraLauncher.launch(uri) } } }

    AlertDialog(
        onDismissRequest = onDismiss, title = { Text(if (template == null) stringResource(R.string.title_new_exercise) else stringResource(R.string.title_edit_exercise)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Box(Modifier.fillMaxWidth().height(160.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(0.5f)), Alignment.Center) {
                    if (imageUri != null) { AsyncImage(if (imageUri!!.startsWith("/")) File(imageUri!!) else imageUri, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop); Box(Modifier.fillMaxSize().background(Color.Black.copy(0.3f))) }
                    Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { val p = Manifest.permission.CAMERA; if (ContextCompat.checkSelfPermission(context, p) == PackageManager.PERMISSION_GRANTED) { val uri = try { val f = File.createTempFile("cam_", ".jpg", context.cacheDir).apply { createNewFile(); deleteOnExit() }; FileProvider.getUriForFile(context, "${context.packageName}.provider", f) } catch(e: Exception) { null }; if(uri != null) { tempCameraUri = uri; cameraLauncher.launch(uri) } } else permissionLauncher.launch(p) }) { Icon(Icons.Default.PhotoCamera, null, tint = Color.White); Text(stringResource(R.string.source_camera), color = Color.White, fontSize = 11.sp) }
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { galleryLauncher.launch("image/*") }) { Icon(Icons.Default.PhotoLibrary, null, tint = Color.White); Text(stringResource(R.string.source_gallery), color = Color.White, fontSize = 11.sp) }
                    }
                }
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.label_name)) }, shape = RoundedCornerShape(12.dp))
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(target, { target = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.label_target)) }, shape = RoundedCornerShape(12.dp))
                Spacer(Modifier.height(16.dp)); Text(stringResource(R.string.label_category), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) { listOf("STRENGTH", "CARDIO", "CORE").forEach { cat -> Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { category = cat }) { RadioButton(category == cat, { category = cat }); Text(stringResource(getCategoryResId(cat)), style = MaterialTheme.typography.bodySmall) } } }
                if (category == "STRENGTH") Row(modifier = Modifier.fillMaxWidth().clickable { isUnilateral = !isUnilateral }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) { Checkbox(isUnilateral, { isUnilateral = it }); Text(stringResource(R.string.label_is_unilateral), style = MaterialTheme.typography.bodyMedium) }
                Spacer(Modifier.height(16.dp)); Text(stringResource(R.string.label_body_part), style = MaterialTheme.typography.labelLarge); ResourceDropdown(bodyPart, BODY_PART_OPTIONS) { bodyPart = it }
                Spacer(Modifier.height(12.dp)); Text(stringResource(R.string.label_equipment), style = MaterialTheme.typography.labelLarge); ResourceDropdown(equipment, EQUIPMENT_OPTIONS) { equipment = it }
                Spacer(Modifier.height(16.dp)); Text(stringResource(R.string.label_log_type), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (category == "CARDIO") FilterChip(true, {}, { Text(stringResource(R.string.log_type_duration)) }, leadingIcon = { Icon(Icons.Default.Timer, null, Modifier.size(16.dp)) }, shape = RoundedCornerShape(12.dp))
                    else { FilterChip(logType == LogType.WEIGHT_REPS.value, { logType = LogType.WEIGHT_REPS.value }, { Text(stringResource(R.string.log_type_weight_reps)) }, leadingIcon = { Icon(Icons.Default.FitnessCenter, null, Modifier.size(16.dp)) }, shape = RoundedCornerShape(12.dp)); FilterChip(logType == LogType.REPS_ONLY.value, { logType = LogType.REPS_ONLY.value }, { Text(stringResource(R.string.log_type_reps_only)) }, leadingIcon = { Icon(Icons.Default.AccessibilityNew, null, Modifier.size(16.dp)) }, shape = RoundedCornerShape(12.dp)); if (category == "CORE") FilterChip(logType == LogType.DURATION.value, { logType = LogType.DURATION.value }, { Text(stringResource(R.string.log_type_duration)) }, leadingIcon = { Icon(Icons.Default.Timer, null, Modifier.size(16.dp)) }, shape = RoundedCornerShape(12.dp)) }
                }
                Spacer(Modifier.height(16.dp)); OutlinedTextField(instruction, { instruction = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.label_instruction)) }, minLines = 3, shape = RoundedCornerShape(12.dp))
            }
        }, confirmButton = { Button({ if (name.isNotBlank()) onSave(ExerciseTemplate(id = template?.id ?: 0, name = name, category = category, bodyPart = bodyPart, equipment = equipment, isUnilateral = isUnilateral, logType = logType, instruction = instruction, imageUri = imageUri, defaultTarget = target)) }) { Text(stringResource(R.string.btn_save)) } }, dismissButton = { TextButton(onDismiss) { Text(stringResource(R.string.btn_cancel)) } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResourceDropdown(currentKey: String, options: List<String>, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val resId = getBodyPartResId(currentKey).takeIf { it != 0 } ?: getEquipmentResId(currentKey)
    ExposedDropdownMenuBox(expanded, { expanded = !expanded }) { OutlinedTextField(if (resId != 0) stringResource(resId) else currentKey, {}, readOnly = true, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) }, modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable, true).fillMaxWidth(), shape = RoundedCornerShape(12.dp)); ExposedDropdownMenu(expanded, { expanded = false }) { options.forEach { key -> val id = getBodyPartResId(key).takeIf { it != 0 } ?: getEquipmentResId(key); DropdownMenuItem({ Text(if (id != 0) stringResource(id) else key) }, { onSelect(key); expanded = false }) } } }
}

@Composable
fun ExerciseMinimalCard(template: ExerciseTemplate, onClick: () -> Unit, onDelete: () -> Unit) {
    GlassCard(modifier = Modifier.fillMaxWidth().clickable { onClick() }, shape = RoundedCornerShape(16.dp)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (!template.imageUri.isNullOrBlank()) { AsyncImage(if (template.imageUri!!.startsWith("/")) File(template.imageUri!!) else template.imageUri, null, Modifier.size(52.dp).clip(RoundedCornerShape(10.dp)), contentScale = ContentScale.Crop); Spacer(Modifier.width(12.dp)) }
            Column(Modifier.weight(1f)) { Text(template.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold); Row { val p = getBodyPartResId(template.bodyPart); if (p != 0) Text(stringResource(p), style = MaterialTheme.typography.labelSmall, color = Color.Gray); Spacer(Modifier.width(8.dp)); val e = getEquipmentResId(template.equipment); if (e != 0) Text(stringResource(e), style = MaterialTheme.typography.labelSmall, color = Color.Gray) } }
            IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error.copy(0.6f)) }
        }
    }
}
