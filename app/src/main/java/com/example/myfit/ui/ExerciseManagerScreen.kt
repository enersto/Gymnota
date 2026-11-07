package com.example.myfit.ui

import android.Manifest
import android.os.Build
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
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
import coil.compose.AsyncImage
import com.example.myfit.ui.components.ExerciseThumbnail
import com.example.myfit.ui.components.GlassControlSurface
import com.example.myfit.data.CatalogMedia
import com.example.myfit.R
import com.example.myfit.model.ExerciseTemplate
import com.example.myfit.model.LogType
import com.example.myfit.ui.components.GlassCard
import com.example.myfit.ui.components.GlassScaffoldContent
import com.example.myfit.ui.components.LocalBackdrop
import com.example.myfit.ui.components.LocalGlassMode
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
    var personalSelected by rememberSaveable { mutableStateOf(true) }
    var showEditDialog by remember { mutableStateOf(false) }
    var editingTemplate by remember { mutableStateOf<ExerciseTemplate?>(null) }
    var viewingTemplate by remember { mutableStateOf<ExerciseTemplate?>(null) }
    var showResetDialog by remember { mutableStateOf(false) }
    var isSearching by rememberSaveable { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    val backdrop = LocalBackdrop.current
    val useGlassTabs = LocalGlassMode.current && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && backdrop != null
    val personalRows = remember(templates, context, currentLanguage) {
        templates.map { template ->
            val part = getBodyPartResId(template.bodyPart)
            val equipment = getEquipmentResId(template.equipment)
            mapOf("id" to template.id, "name" to template.name, "category" to template.category,
                "bodyLabel" to if (part != 0) context.getString(part) else "",
                "equipment" to if (equipment != 0) context.getString(equipment) else "",
                "hasMedia" to (CatalogMedia.photoUri(template) != null || CatalogMedia.animationUri(context, template) != null),
                "mediaRevision" to listOf(template.name, template.imageUri, template.animationUri).hashCode())
        }
    }
    val catalogHost = com.example.myfit.flutter.rememberEmbeddedCatalogHost(
        currentLanguage, searchQuery,
        onSelect = { selected ->
            editingTemplate = templates.firstOrNull { selected.animationUri != null && CatalogMedia.animationUri(context, it) == selected.animationUri }
                ?: templates.firstOrNull { it.name.equals(selected.name, true) } ?: selected
            showEditDialog = true
        },
        personalRows = personalRows,
        personalSelected = personalSelected,
        onPersonalAction = { action, id ->
            when (action) {
                "showPersonal" -> personalSelected = true
                "showCatalog" -> personalSelected = false
                "add" -> { editingTemplate = null; showEditDialog = true }
                "view" -> viewingTemplate = templates.firstOrNull { it.id == id }
                "delete" -> templates.firstOrNull { it.id == id }?.let { viewModel.deleteTemplate(it.id) }
            }
        },
        personalMedia = { id ->
            templates.firstOrNull { it.id == id }?.let {
                CatalogMedia.photoUri(it) ?: CatalogMedia.animationUri(context, it)
            }
        }
    )
    androidx.activity.compose.BackHandler(enabled = (!personalSelected || isSearching) && !showEditDialog && viewingTemplate == null && !showResetDialog) {
        val leave = { if (isSearching) { isSearching = false; searchQuery = "" } else { navController.popBackStack(); Unit } }
        if (personalSelected) leave() else catalogHost?.backOr(leave) ?: leave()
    }

    GlassScaffoldContent {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                Column(Modifier.statusBarsPadding().padding(top = 24.dp)) {
                    if (isSearching) {
                        ExerciseSearchBar(searchQuery, { searchQuery = it },
                            onBack = { isSearching = false; searchQuery = "" }, onClear = { searchQuery = "" })
                    } else {
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                stringResource(R.string.tab_exercises),
                                style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.onBackground,
                                modifier = Modifier.weight(1f).padding(vertical = 8.dp),
                            )
                            IconButton(onClick = { isSearching = true }) {
                                Icon(Icons.Default.Search, stringResource(R.string.catalog_search))
                            }
                            if (personalSelected) {
                                IconButton(onClick = { showResetDialog = true }) {
                                    Icon(Icons.Default.Refresh, null)
                                }
                            }
                        }
                    }
                }
            }
        ) { padding ->
            com.example.myfit.flutter.EmbeddedCatalogView(
                catalogHost,
                Modifier.fillMaxSize().padding(padding).padding(bottom = if (useGlassTabs) 88.dp else 0.dp)
            )
        }
    }

    if (showResetDialog) AlertDialog(
        onDismissRequest = { showResetDialog = false },
        title = { Text(stringResource(R.string.title_reset_exercises)) },
        text = { Text(stringResource(R.string.msg_reset_exercises_warning)) },
        confirmButton = {
            Button(onClick = { viewModel.reloadStandardExercises(context, currentLanguage); showResetDialog = false }) {
                Text(stringResource(R.string.btn_confirm))
            }
        },
        dismissButton = { TextButton(onClick = { showResetDialog = false }) { Text(stringResource(R.string.btn_cancel)) } }
    )
    viewingTemplate?.let { template ->
        ExerciseDetailDialog(template, onDismiss = { viewingTemplate = null }, onEdit = {
            editingTemplate = template
            viewingTemplate = null
            showEditDialog = true
        })
    }
    if (showEditDialog) ExerciseEditDialog(editingTemplate, { showEditDialog = false }, {
        viewModel.saveTemplate(it)
        showEditDialog = false
    })
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ExerciseEditDialog(template: ExerciseTemplate?, onDismiss: () -> Unit, onSave: (ExerciseTemplate) -> Unit) {
    val context = LocalContext.current
    var name by remember { mutableStateOf(template?.name.orEmpty()) }
    var target by remember { mutableStateOf(template?.defaultTarget.orEmpty()) }
    var category by remember { mutableStateOf(template?.category ?: "STRENGTH") }
    var bodyPart by remember { mutableStateOf(template?.bodyPart ?: "part_chest") }
    var equipment by remember { mutableStateOf(template?.equipment ?: "equip_barbell") }
    var isUnilateral by remember { mutableStateOf(template?.isUnilateral ?: false) }
    var instruction by remember { mutableStateOf(template?.instruction.orEmpty()) }
    var logType by remember { mutableIntStateOf(template?.logType ?: LogType.WEIGHT_REPS.value) }
    var imageUri by remember { mutableStateOf(template?.let { CatalogMedia.photoUri(it) }) }
    var animationUri by remember { mutableStateOf(template?.let { CatalogMedia.animationUri(context, it) }) }
    val photoActionColor = if (imageUri != null) Color.White else MaterialTheme.colorScheme.onSurface
    var showAnimationPicker by remember { mutableStateOf(false) }
    val editLanguage = context.resources.configuration.locales[0].language
    LaunchedEffect(category) { if (template == null) logType = when(category) { "CARDIO", "CORE" -> LogType.DURATION.value; else -> LogType.WEIGHT_REPS.value } }

    fun saveImage(uri: Uri): String? = try { val inputStream = context.contentResolver.openInputStream(uri); val file = File(context.filesDir, "img_${System.currentTimeMillis()}.jpg"); val outputStream = FileOutputStream(file); inputStream?.copyTo(outputStream); inputStream?.close(); outputStream.close(); file.absolutePath } catch(e: Exception) { e.printStackTrace(); null }
    if (showAnimationPicker) ExerciseCatalogDialog(
        language = editLanguage,
        onDismiss = { showAnimationPicker = false },
        onSelect = { selected ->
            animationUri = selected.animationUri
            showAnimationPicker = false
        }
    )
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
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { val p = Manifest.permission.CAMERA; if (ContextCompat.checkSelfPermission(context, p) == PackageManager.PERMISSION_GRANTED) { val uri = try { val f = File.createTempFile("cam_", ".jpg", context.cacheDir).apply { createNewFile(); deleteOnExit() }; FileProvider.getUriForFile(context, "${context.packageName}.provider", f) } catch(e: Exception) { null }; if(uri != null) { tempCameraUri = uri; cameraLauncher.launch(uri) } } else permissionLauncher.launch(p) }) { Icon(Icons.Default.PhotoCamera, null, tint = photoActionColor); Text(stringResource(R.string.source_camera), color = photoActionColor, fontSize = 11.sp) }
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { galleryLauncher.launch("image/*") }) { Icon(Icons.Default.PhotoLibrary, null, tint = photoActionColor); Text(stringResource(R.string.source_gallery), color = photoActionColor, fontSize = 11.sp) }
                    }
                }
                Spacer(Modifier.height(16.dp))
                if (imageUri != null) {
                    GlassControlSurface(onClick = { imageUri = null }) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.remove_exercise_photo))
                    }
                    Spacer(Modifier.height(12.dp))
                }
                animationUri?.let { uri ->
                    AsyncImage(
                        model = uri,
                        contentDescription = stringResource(R.string.catalog_choose_animation),
                        modifier = Modifier.fillMaxWidth().height(160.dp),
                        contentScale = ContentScale.Fit
                    )
                }
                GlassControlSurface(onClick = { showAnimationPicker = true }) {
                    Text(stringResource(R.string.catalog_choose_animation))
                }
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
        }, confirmButton = { Button({ if (name.isNotBlank()) onSave(ExerciseTemplate(id = template?.id ?: 0, name = name, category = category, bodyPart = bodyPart, equipment = equipment, isUnilateral = isUnilateral, logType = logType, instruction = instruction, imageUri = imageUri, animationUri = animationUri, defaultTarget = target)) }) { Text(stringResource(R.string.btn_save)) } }, dismissButton = { TextButton(onDismiss) { Text(stringResource(R.string.btn_cancel)) } }
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
    val mediaUri = CatalogMedia.photoUri(template) ?: CatalogMedia.animationUri(LocalContext.current, template)
    GlassCard(modifier = Modifier.fillMaxWidth().clickable { onClick() }, shape = RoundedCornerShape(16.dp)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (!mediaUri.isNullOrBlank()) { ExerciseThumbnail(if (mediaUri!!.startsWith("/")) File(mediaUri!!) else mediaUri, null, Modifier.size(52.dp).clip(RoundedCornerShape(10.dp)), contentScale = ContentScale.Crop); Spacer(Modifier.width(12.dp)) }
            Column(Modifier.weight(1f)) { Text(template.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold); Row { val p = getBodyPartResId(template.bodyPart); if (p != 0) Text(stringResource(p), style = MaterialTheme.typography.labelSmall, color = Color.Gray); Spacer(Modifier.width(8.dp)); val e = getEquipmentResId(template.equipment); if (e != 0) Text(stringResource(e), style = MaterialTheme.typography.labelSmall, color = Color.Gray) } }
            IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error.copy(0.6f)) }
        }
    }
}
