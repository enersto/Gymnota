package com.example.myfit.flutter

import android.app.Activity
import android.content.Intent
import android.content.res.Configuration
import android.database.sqlite.SQLiteDatabase
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.room.withTransaction
import com.example.myfit.R
import com.example.myfit.data.AppDatabase
import com.example.myfit.data.ExerciseCatalog
import com.example.myfit.data.RustCatalog
import androidx.core.graphics.drawable.toBitmap
import coil.decode.BitmapFactoryDecoder
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.example.myfit.model.AppTheme
import com.example.myfit.ui.getBodyPartResId
import com.example.myfit.ui.getEquipmentResId
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Locale

/** Shared platform bridge for the embedded catalogue and the existing history activity. */
internal class LibraryBridge(
    private val activity: Activity,
    private val embedded: Boolean = false,
    initialState: Map<String, Any?> = emptyMap(),
    private val onSelect: ((String, String) -> Unit)? = null,
    private val onPersonal: (() -> Unit)? = null,
    private val onOverlayOpen: (((Boolean) -> Unit) -> Unit)? = null,
    private val onOverlayClose: ((() -> Unit) -> Unit)? = null,
    private val onOverlayBackdrop: (suspend () -> ByteArray?)? = null,
    private val onOverlayReady: (() -> Unit)? = null,
    private val onInlineReady: (() -> Unit)? = null,
    private val onPersonalAction: ((String, Long?) -> Unit)? = null,
    private val personalMedia: ((Long) -> String?)? = null,
    private val onSettingsAction: ((String, Any?) -> Unit)? = null,
    private val onSettingsModel: (suspend (String, Map<String, String>) -> Map<String, Any?>)? = null
) {
    private var channel: MethodChannel? = null
    private var state: Map<String, Any?> = initialState
    private val applicationContext get() = activity.applicationContext
    private val resources get() = activity.resources
    private val packageName get() = activity.packageName
    private val intent get() = activity.intent
    private val assets get() = activity.assets
    private val noBackupFilesDir get() = activity.noBackupFilesDir
    private val contentResolver get() = activity.contentResolver
    private fun createConfigurationContext(config: Configuration) = activity.createConfigurationContext(config)
    private fun startActivityForResult(intent: Intent, code: Int) = activity.startActivityForResult(intent, code)

    fun update(next: Map<String, Any?>) {
        if (next == state) return
        state = next
        val language = next["language"] as? String ?: "zh"
        channel?.invokeMethod("embeddedState", next + mapOf("labels" to labels(language)))
    }

    fun park() {
        channel?.invokeMethod("embeddedState", mapOf(
            "embedded" to true, "page" to "/embedded-idle", "language" to "zh", "labels" to emptyMap<String, String>()
        ))
    }

    fun updateBackdrop(session: Any?, bytes: ByteArray, width: Float, height: Float) {
        channel?.invokeMethod("catalogBackdropCached", mapOf("session" to session, "bytes" to bytes, "width" to width, "height" to height))
    }

    fun popOrClose(close: () -> Unit) {
        channel?.invokeMethod("embeddedBack", null, object : MethodChannel.Result {
            override fun success(value: Any?) { if (value != true) close() }
            override fun error(code: String, message: String?, details: Any?) = close()
            override fun notImplemented() = close()
        }) ?: close()
    }

    private suspend fun catalog(language: String, query: String, part: String?): Map<String, Any> = withContext(Dispatchers.Default) {
        val entries = ExerciseCatalog(applicationContext).search(language, query, part)
        if (query.isBlank() && part == null) catalogues[language] = entries
        val localized = createConfigurationContext(Configuration(resources.configuration).apply { setLocale(Locale.forLanguageTag(language)) })
        mapOf("labels" to labels(language), "entries" to entries.map { e ->
            mapOf("id" to e.sourceId, "name" to e.name, "canonical" to e.canonicalName,
                "bodyPart" to e.bodyPart, "bodyLabel" to localized.getString(getBodyPartResId(e.bodyPart).takeIf { it != 0 } ?: R.string.part_other),
                "equipment" to localized.getString(getEquipmentResId(e.equipment).takeIf { it != 0 } ?: R.string.equip_other),
                "instruction" to e.instruction, "target" to e.defaultTarget.orEmpty(),
                "categoryLabel" to localized.getString(com.example.myfit.ui.getCategoryResId(e.category ?: "STRENGTH")),
                "logTypeLabel" to localized.getString(com.example.myfit.ui.getLogTypeResId(e.logType ?: 0)),
                "unilateral" to (e.isUnilateral ?: false))
        })
    }

    private fun chartSnapshot(snapshot: Map<String, Any?>): Map<String, Any?> =
        mapOf("revision" to snapshot["revision"], "charts" to snapshot["charts"])

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var pendingBackup: MethodChannel.Result? = null
    private var backupCopy: File? = null
    private val catalogues = java.util.concurrent.ConcurrentHashMap<String, List<com.example.myfit.data.CatalogExercise>>()

    fun configure(flutterEngine: FlutterEngine) {
        channel = MethodChannel(flutterEngine.dartExecutor.binaryMessenger, "gymnota/library")
        if (state["page"] == "/embedded-history" || (!embedded && intent.getStringExtra("page") == "/history")) {
            scope.launch { HistoryRepository.observe(applicationContext).collect { snapshot ->
                snapshot.onSuccess { channel?.invokeMethod("historySnapshot", if (embedded) chartSnapshot(it) else it) }
                    .onFailure { channel?.invokeMethod("historySnapshotError", null) }
            } }
        }
        channel!!.setMethodCallHandler { call, result ->
            if (call.method == "primaryHaptic") {
                result.success(activity.window.decorView.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY))
                return@setMethodCallHandler
            }
            if (call.method == "openCatalogOverlay") {
                if (onOverlayOpen == null) result.success(false)
                else onOverlayOpen.invoke { result.success(it) }
                return@setMethodCallHandler
            }
            if (call.method == "catalogOverlayReady") {
                onOverlayReady?.invoke(); result.success(true)
                return@setMethodCallHandler
            }
            if (call.method == "catalogInlineReady") {
                onInlineReady?.invoke(); result.success(true)
                return@setMethodCallHandler
            }
            if (call.method == "closeCatalogOverlay") {
                if (onOverlayClose == null) result.success(true)
                else onOverlayClose.invoke { result.success(true) }
                return@setMethodCallHandler
            }

            if (call.method == "pickBackup") {
                if (pendingBackup != null) result.error("busy", "", null)
                else {
                    pendingBackup = result
                    try {
                        startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                            addCategory(Intent.CATEGORY_OPENABLE); type = "*/*"
                        }, 6102)
                    } catch (e: Exception) { pendingBackup = null; result.error("picker", "", null) }
                }
                return@setMethodCallHandler
            }
            scope.launch {
                try {
                    val language = call.argument<String>("language") ?: state["language"] as? String ?: intent.getStringExtra("language") ?: "zh"
                    val value: Any? = when (call.method) {
                        "bootstrap" -> if (embedded && state["page"] == "/embedded-history") {
                            state + mapOf("labels" to labels(language), "initialHistory" to HistoryRepository.currentOrNull()?.let(::chartSnapshot))
                        } else if (embedded && (state["page"] == "/embedded-idle" || state["page"] == "/embedded-coach" || state["page"] == "/embedded-settings" || state["exerciseManager"] == true)) {
                            state + mapOf("labels" to labels(language))
                        } else if (embedded) {
                            state + mapOf("labels" to labels(language), "catalogLanguage" to language, "initialCatalog" to catalog(language, "", null))
                        } else withContext(Dispatchers.IO) {
                            val settings = AppDatabase.getDatabase(applicationContext).workoutDao().getAppSettings().first()
                            val theme = AppTheme.fromId(settings?.themeId ?: 1)
                            mapOf("page" to (intent.getStringExtra("page") ?: "/catalog"), "language" to (settings?.languageCode ?: language), "dark" to (theme == AppTheme.DARK),
                                "primary" to (if (theme == AppTheme.DARK) 0xFF90CAF9L else theme.primary), "background" to theme.background, "onBackground" to theme.onBackground, "secondary" to (if (theme == AppTheme.DARK) 0xFF4F378BL else 0xFFEADDFFL))
                        }
                        "settingsModel" -> {
                            check(state["page"] == "/embedded-settings")
                            val fields = listOf("provider", "model", "apiKey", "baseUrl").associateWith {
                                call.argument<String>(it).orEmpty()
                            }
                            checkNotNull(onSettingsModel).invoke(call.argument<String>("operation").orEmpty(), fields)
                        }
                        "settingsAction" -> {
                            check(state["page"] == "/embedded-settings")
                            checkNotNull(onSettingsAction).invoke(call.argument<String>("action").orEmpty(), call.argument<Any>("value"))
                            true
                        }
                        "exerciseManagerAction" -> {
                            check(state["exerciseManager"] == true)
                            onPersonalAction?.invoke(call.argument<String>("action").orEmpty(), (call.argument<Number>("id"))?.toLong())
                            true
                        }
                        "personalSearch" -> {
                            check(state["exerciseManager"] == true)
                            val query = call.argument<String>("query").orEmpty().trim()
                            val names = (state["personalRows"] as? List<*>).orEmpty().mapNotNull { row ->
                                val item = row as? Map<*, *> ?: return@mapNotNull null
                                val id = item["id"] as? Long ?: return@mapNotNull null
                                id to (item["name"] as? String).orEmpty()
                            }
                            withContext(Dispatchers.Default) {
                                RustCatalog.searchPersonal(names, query)
                                    ?: names.filter { (_, name) -> name.contains(query, ignoreCase = true) }.map { it.first }
                            }
                        }
                        "personalThumbnail" -> {
                            check(state["exerciseManager"] == true)
                            val id = call.argument<Number>("id")?.toLong() ?: error("Missing ID")
                            val uri = personalMedia?.invoke(id)
                            if (uri == null) null else withContext(Dispatchers.IO) {
                                val model: Any = if (uri.startsWith("/")) File(uri) else uri
                                val request = ImageRequest.Builder(applicationContext)
                                    .data(model)
                                    .size(104)
                                    .allowHardware(false)
                                    .memoryCacheKey("personal-exercise-thumbnail:$uri")
                                    .decoderFactory { source, options, _ -> BitmapFactoryDecoder(source.source, options) }
                                    .build()
                                val image = applicationContext.imageLoader.execute(request) as? SuccessResult
                                image?.drawable?.toBitmap()?.let { bitmap ->
                                    ByteArrayOutputStream().use { stream ->
                                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
                                        stream.toByteArray()
                                    }
                                }
                            }
                        }
                        "catalogOverlayBackdrop" -> withContext(Dispatchers.IO) { onOverlayBackdrop?.invoke() }
                        "catalog" -> catalog(language, call.argument<String>("query").orEmpty(), call.argument<String>("part"))
                        "labels" -> labels(language)
                        "media" -> withContext(Dispatchers.IO) {
                            val id = call.argument<String>("id") ?: error("Missing ID")
                            // Resolve through catalogue rather than accepting a file path from Dart.
                            val entries = catalogues.values.firstOrNull() ?: ExerciseCatalog(applicationContext).load("en").also { catalogues["en"] = it }
                            val entry = entries.first { it.sourceId == id }
                            val bytes = assets.open(entry.gifUri.removePrefix("file:///android_asset/")).use { it.readBytes() }
                            if (call.argument<Boolean>("thumbnail") == true) {
                                val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: error("Invalid image")
                                try { ByteArrayOutputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it); it.toByteArray() } }
                                finally { bitmap.recycle() }
                            } else bytes
                        }
                        "history" -> withContext(Dispatchers.IO) {
                            val rawSnapshot = if (call.argument<Boolean>("backup") == true) {
                                val file = backupCopy ?: error("No backup selected")
                                SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
                                    HistorySnapshot.read(encodedSets = true, chartFields = true) { sql -> db.rawQuery(sql, null) }
                                }
                            } else {
                                val snapshot = if (call.argument<Boolean>("refresh") == true) HistoryRepository.reload(applicationContext)
                                    else HistoryRepository.current(applicationContext)
                                return@withContext when {
                                    !embedded -> snapshot
                                    call.argument<Boolean>("records") == true -> mapOf("revision" to snapshot["revision"],
                                        "tasks" to snapshot["tasks"], "weights" to snapshot["weights"])
                                    else -> chartSnapshot(snapshot)
                                }
                            }
                            RustHistoryCore.process(rawSnapshot)
                        }
                        "useExercise" -> {
                            val id = call.argument<String>("id") ?: error("Missing ID")
                            check(ExerciseCatalog(applicationContext).load(language).any { it.sourceId == id })
                            if (embedded) onSelect?.invoke(id, language)
                            else { activity.setResult(Activity.RESULT_OK, Intent().putExtra("sourceId", id).putExtra("language", language)); activity.finish() }
                            true
                        }
                        "showPersonal" -> { onPersonal?.invoke(); true }
                        "close" -> { if (!embedded) activity.finish(); true }
                        else -> { result.notImplemented(); return@launch }
                    }
                    result.success(value)
                } catch (e: CancellationException) { throw e }
                catch (e: Exception) { result.error("library", "Unable to load library data", null) }
            }
        }
    }

    private val localizedLabels = java.util.concurrent.ConcurrentHashMap<String, Map<String, String>>()
    private fun labels(language: String): Map<String, String> =
        localizedLabels.getOrPut(language) { loadLabels(language) }

    private fun loadLabels(language: String): Map<String, String> {
        val c = createConfigurationContext(Configuration(resources.configuration).apply { setLocale(Locale.forLanguageTag(language)) })
        val names = listOf("catalog_personal_title", "btn_add", "btn_delete", "btn_test_connection", "msg_connection_success", "source_gallery", "action_delete_photo", "btn_snap_photo", "label_injuries", "injury_knee", "injury_lower_back", "injury_shoulder", "injury_wrist", "injury_neck", "title_ai_history", "msg_no_history", "ai_status_unconfigured", "coach_stream_stop", "coach_stream_failed", "coach_stream_stopped", "coach_stream_incomplete", "coach_stream_connecting", "coach_stream_reasoning_summary", "ai_response_title", "btn_generate_and_import", "msg_refine_dialog_hint", "btn_cancel", "btn_confirm", "label_focus", "label_scene", "focus_comprehensive", "category_strength", "category_cardio", "category_core", "scene_gym", "scene_home_equip", "scene_home_none", "scene_outdoor", "scene_pool", "scene_yoga", "scene_limited_gym", "scene_crossfit", "option_1_week", "option_2_weeks", "option_3_weeks", "option_4_weeks", "tab_ai_coach", "btn_close", "weekly_plan", "btn_free_chat", "label_context_length", "label_current_goal", "hint_input_chat", "hint_input_goal", "coach_stream_enabled", "btn_send_message", "btn_get_advice", "tag_uni", "label_log_type", "tab_list","tab_chart","chart_granularity_day","chart_granularity_month","header_strength_train","header_core_train","header_cardio_train","chart_title_strength_single","chart_title_core_single","chart_title_cardio_single","chart_title_cardio_total","chart_title_body_status","chart_title_weight","chart_title_bmi","chart_title_bmr","chart_title_body_fat","chart_title_skeletal_muscle","chart_title_body_water","chart_title_whr","chart_title_heatmap","hint_heatmap_volume","chart_no_data","title_select_exercise","charts_unavailable","charts_data","charts_low","charts_high","part_chest","part_back","part_shoulders","part_arms","part_abs","part_hips","part_thighs","part_calves","part_cardio","part_other","settings_language", "catalog_title", "catalog_setup_hint", "catalog_search", "catalog_all_parts", "catalog_empty", "catalog_use_exercise",
            "history_title", "no_history", "history_no_train", "header_set_no", "label_weight", "label_reps", "label_duration_sec", "label_side_left", "label_side_right",
            "label_instruction", "label_instruction_empty", "label_target", "btn_cancel", "btn_confirm", "btn_done",
            "history_stats_partial", "history_stats", "history_stats_days", "history_stats_tasks", "history_stats_sets", "history_stats_volume", "history_stats_duration", "history_stats_max_weight", "history_stats_fallback", "flutter_history_search", "flutter_history", "flutter_backup", "flutter_current", "flutter_refresh", "flutter_readonly", "flutter_error", "flutter_retry", "flutter_native", "flutter_minutes")
        val settingsNames = if (state["page"] == "/embedded-settings") listOf(
            "settings_advanced", "settings_data_management", "export_csv_btn", "import_csv_btn",
            "settings_help_reference", "settings_profile", "btn_backup_db", "btn_restore_db",
            "ai_config_title", "settings_timer_title", "timer_sound_title", "timer_sound_desc",
            "timer_prep_title", "timer_prep_desc", "timer_final_title", "timer_final_desc", "theme_style",
            "schedule_type_title", "type_core", "type_active", "type_light", "type_rest",
            "week_1", "week_2", "week_3", "week_4", "week_5", "week_6", "week_7",
            "about_version_format", "about_credit", "settings_old_title", "ai_provider_label",
            "ai_api_key_label", "ai_model_label", "ai_base_url_label", "btn_save_config",
            "label_ai_cache_title", "label_ai_cache_desc", "label_max_history",
            "settings_ai_saved", "settings_ai_connection_error" 
        ) else emptyList()
        return (names + settingsNames).associateWith { name -> c.getString(resources.getIdentifier(name, "string", packageName)) }
    }

    @Deprecated("Legacy activity result used by FlutterActivity")
    fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode != 6102) return
        val result = pendingBackup ?: return
        pendingBackup = null
        val uri = data?.data
        if (resultCode != Activity.RESULT_OK || uri == null) { result.success(false); return }
        scope.launch {
            try {
                val file = withContext(Dispatchers.IO) {
                    val dir = File(noBackupFilesDir, "flutter_preview").apply { mkdirs() }
                    val temporary = File.createTempFile("history-", ".sqlite", dir)
                    try {
                        contentResolver.openInputStream(uri).use { input ->
                            checkNotNull(input)
                            temporary.outputStream().use { out ->
                                val buffer = ByteArray(65536); var total = 0L
                                while (true) { val n = input.read(buffer); if (n < 0) break
                                    total += n; check(total <= 100L * 1024 * 1024); out.write(buffer, 0, n) }
                            }
                        }
                        SQLiteDatabase.openDatabase(temporary.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
                            db.rawQuery("PRAGMA quick_check", null).use { check(it.moveToFirst() && it.getString(0) == "ok") }
                            HistorySnapshot.read { sql -> db.rawQuery(sql, null) }
                        }
                        temporary
                    } catch (e: Exception) { temporary.delete(); throw e }
                }
                backupCopy?.delete(); backupCopy = file; result.success(true)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { result.error("backup", "Invalid or unsupported backup", null) }
        }
    }

    fun close() {
        scope.cancel(); pendingBackup = null; backupCopy?.delete()
        channel?.setMethodCallHandler(null); channel = null
    }
}
