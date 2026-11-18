package com.example.myfit.flutter

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.toArgb
import com.example.myfit.MainActivity
import com.example.myfit.util.LocaleHelper
import com.example.myfit.util.NotificationHelper
import com.example.myfit.model.LogType
import com.example.myfit.model.WorkoutSet
import io.flutter.plugin.common.MethodChannel
import java.time.LocalDate
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import com.example.myfit.BuildConfig
import com.example.myfit.data.CatalogMedia
import com.example.myfit.data.RustWorkoutStorage
import com.example.myfit.model.AppTheme
import com.example.myfit.model.DayType
import com.example.myfit.model.ExerciseTemplate
import com.example.myfit.model.ScheduleConfig
import com.example.myfit.ui.getBodyPartResId
import com.example.myfit.ui.getEquipmentResId
import com.example.myfit.ui.getLogTypeResId
import com.example.myfit.viewmodel.MainViewModel
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.EventChannel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.lang.ref.WeakReference
import java.util.Locale
import java.util.UUID

/** Hosts the preview's platform adapters. The original activity keeps owning the live timer. */
internal class RootPreviewHost(private val activity: Activity) {
    companion object {
        const val PAGE = "/root-preview"
        const val RETURN_ROUTE = "previewReturnRoute"
        private const val SESSION = "rootPreviewSession"
        fun borrow(intent: Intent): MainViewModel? = plans[intent.getStringExtra(SESSION)]?.get()
        fun release(intent: Intent) { plans.remove(intent.getStringExtra(SESSION)) }
        private val plans = mutableMapOf<String, WeakReference<MainViewModel>>()

        private fun session(context: Context, viewModel: MainViewModel, target: Class<*>): Intent {
            plans.entries.removeAll { it.value.get() == null }
            val token = UUID.randomUUID().toString()
            plans[token] = WeakReference(viewModel)
            return Intent(context, target).putExtra(SESSION, token)
        }
        fun intent(context: Context, viewModel: MainViewModel) =
            session(context, viewModel, FlutterLibraryActivity::class.java).putExtra("page", PAGE)
    }

    private val primaryHost = !activity.intent.hasExtra("page")
    private val token = activity.intent.getStringExtra(SESSION)
    private val fallback = ViewModelStore()
    private val viewModel = plans[token]?.get() ?: ViewModelProvider(
        fallback, ViewModelProvider.AndroidViewModelFactory(activity.application)
    )[MainViewModel::class.java]
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var templates = emptyList<ExerciseTemplate>()
    private var schedules = emptyList<ScheduleConfig>()
    private var tabPersonal = true
    private var dailyChannel: EventChannel? = null
    private var dailySubscription: Job? = null
    private var workoutChannel: MethodChannel? = null

    private var dailyPhotos = emptySet<String>()
    private val editor = ExerciseEditorBridge(activity, { templates }, viewModel::savePersonalTemplate, { dailyPhotos })

    val library = LibraryBridge(activity, embedded = true,
        initialState = state(), onPersonalAction = ::personalAction,
        onExerciseEditor = { operation, args -> check(primaryHost); editor.dispatch(operation, args) },
        personalMedia = { id -> templates.firstOrNull { it.id == id }?.let {
            CatalogMedia.photoUri(it) ?: CatalogMedia.animationUri(activity, it)
        } }, personalDetailMedia = { id, animation ->
            templates.firstOrNull { it.id == id }?.let {
                if (animation) CatalogMedia.animationUri(activity, it) else CatalogMedia.photoUri(it)
            }
        }, onRoutine = ::weeklyRoutine, onSettingsAction = ::settingsAction,
        onSettingsModel = { operation, fields ->
            check(primaryHost)
            modelSettings(viewModel, operation, fields)
        })
    val coach = CoachBridge(activity, viewModel, openSettings = { if (primaryHost) library.rootEvent("rootModelSettings") else returnTo("ai-model") })

    private fun localeContext(language: String): Context = activity.createConfigurationContext(
        Configuration(activity.resources.configuration).apply { setLocale(Locale.forLanguageTag(language)) }
    )

    private fun Context.label(resource: Int, fallback: String = "") =
        if (resource != 0) getString(resource) else fallback

    private fun state(): Map<String, Any?> {
        val profile = viewModel.userProfile.value
        val language = profile.languageCode
        val theme = AppTheme.fromId(profile.themeId)
        val localized = localeContext(language)
        val rows = templates.map { template ->
            mapOf(
                "id" to template.id, "name" to template.name, "category" to template.category,
                "bodyLabel" to localized.label(getBodyPartResId(template.bodyPart), template.bodyPart),
                "equipment" to localized.label(getEquipmentResId(template.equipment), template.equipment),
                "instruction" to template.instruction, "target" to template.defaultTarget,
                "bodyPart" to template.bodyPart, "equipmentKey" to template.equipment, "logType" to template.logType,
                "imageUri" to CatalogMedia.photoUri(template), "animationUri" to CatalogMedia.animationUri(activity, template),
                "logTypeLabel" to localized.getString(getLogTypeResId(template.logType)),
                "unilateral" to template.isUnilateral,
                "hasPhoto" to (CatalogMedia.photoUri(template) != null),
                "hasAnimation" to (CatalogMedia.animationUri(activity, template) != null),
                "hasMedia" to (CatalogMedia.photoUri(template) != null || CatalogMedia.animationUri(activity, template) != null),
                "mediaRevision" to listOf(template.name, template.imageUri, template.animationUri).hashCode()
            )
        }
        return mapOf(
            "page" to PAGE, "primaryHost" to primaryHost,
            "bodyProfile" to mapOf("age" to profile.age, "height" to profile.height, "gender" to profile.gender),
            "secondary" to ((if (theme == AppTheme.DARK) darkColorScheme() else lightColorScheme())
                .primaryContainer.toArgb().toLong() and 0xffffffffL), "language" to language, "dark" to (theme == AppTheme.DARK),
            "primary" to (if (theme == AppTheme.DARK) 0xFF90CAF9L else theme.primary),
            "background" to theme.background, "onBackground" to theme.onBackground,
            "exerciseManager" to true, "personalRows" to rows, "personalSelected" to tabPersonal,
            "personalSearchRevision" to rows.hashCode(), "previewReadOnly" to !primaryHost,
            "bodyPartOptions" to com.example.myfit.ui.BODY_PART_OPTIONS.map { mapOf("key" to it, "label" to localized.label(getBodyPartResId(it), it)) },
            "equipmentOptions" to com.example.myfit.ui.EQUIPMENT_OPTIONS.map { mapOf("key" to it, "label" to localized.label(getEquipmentResId(it), it)) },
            "selectedTheme" to theme.id, "themes" to AppTheme.entries.map { mapOf("id" to it.id, "color" to it.primary) },
            "schedule" to schedules.map {
                mapOf("day" to it.dayOfWeek, "type" to it.dayType.name, "color" to it.dayType.colorHex)
            }, "version" to BuildConfig.VERSION_NAME, "provider" to profile.aiProvider,
            "aiCache" to profile.useLocalAiCache, "aiHistoryLimit" to profile.maxHistoryLimit,
            "aiConnection" to when (viewModel.connectionState.value) {
                MainViewModel.ConnectionState.Success -> "success"
                MainViewModel.ConnectionState.Testing -> "testing"
                is MainViewModel.ConnectionState.Error -> "error"
                else -> "idle"
            },
            "model" to profile.aiModel, "modelConfigured" to profile.aiApiKey.isNotBlank(), "timer" to timerPreferences()
        )
    }

    private fun timerPreferences() = mapOf(
        "sound" to viewModel.getTimerSoundEnabled(),
        "prep" to viewModel.getTimerPrepEnabled(), "prepSeconds" to viewModel.getTimerPrepSeconds(),
        "final" to viewModel.getTimerFinalEnabled(), "finalSeconds" to viewModel.getTimerFinalSeconds()
    )

    fun attach(engine: FlutterEngine) {
        library.configure(engine)
        coach.attach(engine)
        if (primaryHost) {
            workoutChannel = MethodChannel(engine.dartExecutor.binaryMessenger, "gymnota/workout")
            workoutChannel!!.setMethodCallHandler { call, result ->
                scope.launch {
                    try {
                        val args = call.arguments as? Map<*, *> ?: emptyMap<String, Any?>()
                        workoutAction(call.method, args)
                        result.success(null)
                    } catch (e: CancellationException) { throw e }
                    catch (_: Exception) { result.error("workout", null, null) }
                }
            }
        }
        scope.launch {
            combine(viewModel.userProfile, viewModel.allTemplates, viewModel.allSchedules, viewModel.connectionState) { profile, rows, schedule, _ ->
                LocaleHelper.rememberLanguage(activity, profile.languageCode)
                templates = rows
                schedules = schedule
                state()
            }.distinctUntilChanged().collect(library::update)
        }
        dailyChannel = EventChannel(engine.dartExecutor.binaryMessenger, "gymnota/preview/daily").also { channel ->
            channel.setStreamHandler(object : EventChannel.StreamHandler {
                override fun onListen(arguments: Any?, sink: EventChannel.EventSink) {
                    dailySubscription?.cancel()
                    dailySubscription = scope.launch {
                        val templateIndex = viewModel.allTemplates.map { rows -> rows.associateBy { it.id } }
                        val dailyWithTemplates = combine(viewModel.dailyPlanTasks, templateIndex) { daily, rows ->
                            daily to rows
                        }
                        combine(dailyWithTemplates, viewModel.allSchedules, viewModel.currentLanguage, viewModel.timerState, viewModel.showWeightAlert) { (daily, templateById), schedules, language, timer, weightAlert ->
                            val (date, tasks) = daily
                            dailyPhotos = tasks.mapNotNull { it.imageUri?.takeUnless { uri -> uri.startsWith("file:///android_asset/") } }.toSet()
                            val localized = localeContext(language)
                            val day = schedules.firstOrNull { it.dayOfWeek == date.dayOfWeek.value }?.dayType ?: DayType.CORE
                            mapOf("date" to date.toString(), "dayLabel" to localized.getString(day.labelResId),
                                "rest" to (day == DayType.REST), "weightAlert" to weightAlert,
                                "timer" to mapOf("id" to timer.taskId, "setIndex" to timer.setIndex,
                                    "phase" to timer.phase.name, "seconds" to timer.remainingSeconds,
                                    "running" to timer.isRunning, "paused" to timer.isPaused, "alert" to timer.showBigAlert, "total" to timer.totalSeconds),
                                "tasks" to tasks.map { task ->
                                    val template = templateById[task.templateId]
                                    val photo = template?.let(CatalogMedia::photoUri) ?: task.imageUri?.takeUnless { it.startsWith("file:///android_asset/") }
                                    val animation = template?.let { CatalogMedia.animationUri(activity, it) }
                                        ?: task.imageUri?.takeIf { it.startsWith("file:///android_asset/") }?.let(CatalogMedia::bundledUri)
                                        ?: CatalogMedia.uri(activity, task.name)
                                    mapOf("id" to task.id, "templateId" to task.templateId,
                                        "hasMedia" to (template?.let { CatalogMedia.photoUri(it) ?: CatalogMedia.animationUri(activity, it) } != null),
                                        "mediaRevision" to template?.let { listOf(it.name, it.imageUri, it.animationUri).hashCode() },
                                        "showSeconds" to (task.category.equals("CORE", true) || task.target.contains("s", true) || task.target.contains("秒")),
                                        "name" to task.name, "completed" to task.isCompleted,
                                        "category" to task.category, "instruction" to template?.instruction.orEmpty(),
                                        "imageUri" to photo, "animationUri" to animation,
                                        "hasPhoto" to (photo != null), "hasAnimation" to (animation != null),
                                        "bodyLabel" to localized.label(getBodyPartResId(task.bodyPart), task.bodyPart),
                                        "equipment" to localized.label(getEquipmentResId(task.equipment), task.equipment),
                                        "recordType" to localized.getString(getLogTypeResId(task.logType)),
                                        "logType" to task.logType, "unilateral" to task.isUnilateral,
                                        "target" to task.target, "actualWeight" to task.actualWeight,
                                        "sets" to task.sets.map { set ->
                                            mapOf("number" to set.setNumber, "value" to set.weightOrDuration, "reps" to set.reps,
                                                "rightWeight" to set.rightWeight, "rightReps" to set.rightReps)
                                        })
                                })
                        }.distinctUntilChanged().catch { sink.error("daily", null, null) }.collect(sink::success)
                    }
                }

                override fun onCancel(arguments: Any?) {
                    dailySubscription?.cancel()
                    dailySubscription = null
                }
            })
        }
    }

    private suspend fun personalAction(action: String, id: Long?) {
        if (action == "showPersonal" || action == "showCatalog") {
            tabPersonal = action == "showPersonal"
            library.update(state())
        } else if (primaryHost) {
            when (action) {
                "delete" -> id?.let { viewModel.deletePersonalTemplate(it) }
            }
        }
    }

    private suspend fun weeklyRoutine(action: String, day: Int, id: Long?): List<Map<String, Any?>> =
        when (action) {
            "add", "delete" -> viewModel.editWeeklyRoutine(action, day, requireNotNull(id))
            "list" -> viewModel.weeklyRoutineSummary(day)
            else -> error("Unknown routine action")
        }

    private suspend fun settingsAction(action: String, value: Any?) {
        when (action) {
            "language" -> (value as? String)?.takeIf { it in setOf("zh", "en", "es", "ja", "de") }?.let { viewModel.persistSetting("languageCode", it) }
            "theme" -> AppTheme.entries.firstOrNull { it.id == value }?.let { viewModel.persistSetting("themeId", it.id) }
            "schedule" -> schedules.firstOrNull { it.dayOfWeek == value }?.let {
                viewModel.persistSchedule(it.dayOfWeek, DayType.entries[(it.dayType.ordinal + 1) % DayType.entries.size])
            }
            "sound", "prep", "final", "prepSeconds", "finalSeconds" -> {
                when (action) {
                    "sound" -> (value as? Boolean)?.let(viewModel::setTimerSoundEnabled)
                    "prep" -> (value as? Boolean)?.let(viewModel::setTimerPrepEnabled)
                    "final" -> (value as? Boolean)?.let(viewModel::setTimerFinalEnabled)
                    "prepSeconds" -> (value as? Int)?.takeIf { it in 3..30 }?.let(viewModel::setTimerPrepSeconds)
                    "finalSeconds" -> (value as? Int)?.takeIf { it in 3..15 }?.let(viewModel::setTimerFinalSeconds)
                }
                library.update(state())
            }
            "old" -> returnTo("settings")
            "ai" -> if (primaryHost) library.rootEvent("rootModelSettings") else returnTo("ai-model")
            "aiCache" -> (value as? Boolean)?.let { viewModel.persistSetting("useLocalAiCache", it) }
            "aiHistoryLimit" -> (value as? Int)?.takeIf { it in 1..20 }?.let { viewModel.persistSetting("maxHistoryLimit", it) }
            "importCsv" -> viewModel.importWeeklyRoutineNow(activity, requireNotNull(value as? String))
            "saveProfile" -> (value as? Map<*, *>)?.let {
                val age = (it["age"] as? Number)?.toInt() ?: 0
                val height = (it["height"] as? Number)?.toFloat() ?: 0f
                val gender = (it["gender"] as? Number)?.toInt() ?: 0
                if (BuildConfig.RUST_HISTORY_ENABLED) {
                    val fields = mapOf("age" to age, "height" to height, "gender" to gender)
                    RustWorkoutStorage.mutate(activity, LocalDate.now().toString(),
                        listOf(mapOf("action" to action, "fields" to fields)))
                } else {
                    viewModel.updateProfile(age, height, gender).join()
                }
            }
            "import", "profile", "weekly", "help" -> if (primaryHost) openTool(action) else Unit
            "export" -> activity.startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                type = "text/csv"; addCategory(Intent.CATEGORY_OPENABLE); putExtra(Intent.EXTRA_TITLE, "history_${LocalDate.now()}.csv")
            }, 6211)
            "backup" -> activity.startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                type = "application/x-sqlite3"; addCategory(Intent.CATEGORY_OPENABLE); putExtra(Intent.EXTRA_TITLE, "backup.db")
            }, 6212)
            "restore" -> activity.startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                type = "*/*"; addCategory(Intent.CATEGORY_OPENABLE)
            }, 6213)
        }
    }

    fun notificationPermissionResult(request: Int, granted: Boolean) {
        if (editor.permissionResult(request, granted)) return
        if (request == 6214 && granted && !viewModel.hasShownLockScreenGuide.value)
            workoutChannel?.invokeMethod("notificationGuide", null)
    }

    fun activityResult(request: Int, result: Int, data: Intent?) {
        if (editor.activityResult(request, result, data)) return
        val uri = data?.data?.takeIf { result == Activity.RESULT_OK } ?: return
        val localized = localeContext(viewModel.currentLanguage.value)
        when (request) {
            6211 -> viewModel.exportHistoryToCsv(uri, localized)
            6212 -> viewModel.backupDatabase(uri, localized)
            6213 -> viewModel.restoreDatabase(uri, localized, activity.intent)
        }
    }

    private fun openTool(tool: String, id: Long = -1, sourceId: String? = null) {
        activity.startActivity(session(activity, viewModel, MainActivity::class.java)
            .putExtra("nativeTool", tool).putExtra("templateId", id).putExtra("sourceId", sourceId))
    }

    private suspend fun workoutAction(action: String, args: Map<*, *>) {
        val id = (args["id"] as? Number)?.toLong() ?: -1
        val index = (args["index"] as? Number)?.toInt() ?: -1
        val minutes = (args["minutes"] as? Number)?.toFloat() ?: 0f
        if (BuildConfig.RUST_HISTORY_ENABLED) {
            val changes = when (action) {
                "saveWeight" -> {
                    val fields = listOf("weight", "fat", "muscle", "water", "waist", "hip")
                        .associateWith<String, Any?> { args[it] as? Number }.toMutableMap()
                    val age = args["age"] as? Number
                    val height = args["height"] as? Number
                    if (age != null && height != null) {
                        fields["profile"] = mapOf("age" to age.toInt(), "height" to height,
                            "gender" to ((args["gender"] as? Number)?.toInt() ?: 0))
                    }
                    listOf(mapOf("action" to action, "fields" to fields))
                }
                "routine" -> listOf(mapOf("action" to action))
                "addExercise", "removeTask", "check", "addSet", "removeSet" -> {
                    if ((action == "removeTask" || action == "removeSet") &&
                        viewModel.timerState.value.taskId == id) viewModel.stopTimer(activity)
                    listOf(mapOf("action" to action, "id" to id, "index" to index))
                }
                "setFields" -> {
                    val edits = (args["edits"] as? List<*>).orEmpty().mapNotNull { it as? Map<*, *> }
                    edits.mapNotNull { edit ->
                        (edit["id"] as? Number)?.toLong()?.let { it to edit }
                    }.groupBy({ it.first }, { it.second }).map { (taskId, batch) ->
                        mapOf("action" to action, "id" to taskId, "edits" to batch)
                    }
                }
                else -> null
            }
            if (changes != null) {
                val date = if (action == "saveWeight") LocalDate.now() else viewModel.selectedDate.value
                RustWorkoutStorage.mutate(activity, date.toString(), changes)
                return
            }
        }
        when (action) {
            "pick" -> openTool("exercise-picker")
            "addExercise" -> templates.firstOrNull { it.id == id }?.let(viewModel::addTaskFromTemplate)
            "weight" -> openTool("weight")
            "saveWeight" -> (args["weight"] as? Number)?.toFloat()?.let { weight ->
                viewModel.logWeightAndProfile(
                    weight, (args["age"] as? Number)?.toInt(), (args["height"] as? Number)?.toFloat(),
                    (args["gender"] as? Number)?.toInt(), (args["fat"] as? Number)?.toFloat(),
                    (args["muscle"] as? Number)?.toFloat(), (args["water"] as? Number)?.toFloat(),
                    (args["waist"] as? Number)?.toFloat(), (args["hip"] as? Number)?.toFloat()
                )
            }
            "schedule" -> library.rootEvent("rootScheduleSettings")
            "routine" -> viewModel.applyWeeklyRoutine()
            "notification-settings", "notification-later" -> {
                viewModel.markLockScreenGuideShown()
                if (action == "notification-settings") NotificationHelper.openNotificationSettings(activity)
            }
            "pause" -> viewModel.pauseTimer(activity)
            "stop" -> viewModel.stopTimer(activity)
            "start" -> if (minutes.isFinite() && minutes > 0 && id >= 0 && index >= 0) {
                if (android.os.Build.VERSION.SDK_INT >= 33 && activity.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                    != android.content.pm.PackageManager.PERMISSION_GRANTED)
                    activity.requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 6214)
                viewModel.startTimer(activity, id, index, minutes)
            }
            "removeTask" -> {
                if (viewModel.timerState.value.taskId == id) viewModel.stopTimer(activity)
                viewModel.mutateDailyTask(id, delete = true) { it }
            }
            "check" -> viewModel.mutateDailyTask(id) { task ->
                val complete = !task.isCompleted
                val fill = complete && (task.logType in listOf(LogType.DURATION.value, LogType.REPS_ONLY.value) ||
                    task.category.contains("有氧") || task.category.contains("核心"))
                val value = task.target.replace(" ", "")
                task.copy(isCompleted = complete, sets = if (fill) task.sets.map {
                    if (it.weightOrDuration.isBlank() && it.reps.isBlank())
                        it.copy(weightOrDuration = value, reps = if (task.logType == LogType.DURATION.value) "Done" else value)
                    else it
                } else task.sets)
            }
            "addSet" -> viewModel.mutateDailyTask(id) { task ->
                val previous = task.sets.lastOrNull()
                val number = task.sets.size + 1
                task.copy(sets = task.sets + (previous?.copy(setNumber = number) ?: WorkoutSet(number, "", "")))
            }
            "removeSet" -> {
                if (viewModel.timerState.value.taskId == id) viewModel.stopTimer(activity)
                viewModel.mutateDailyTask(id) { task ->
                    task.copy(sets = task.sets.filterIndexed { i, _ -> i != index })
                }
            }
            "setFields" -> {
                val edits = (args["edits"] as? List<*>).orEmpty().mapNotNull { it as? Map<*, *> }
                val changes = edits.mapNotNull { edit ->
                    (edit["id"] as? Number)?.toLong()?.let { it to edit }
                }.groupBy({ it.first }, { it.second })
                viewModel.updateDailyTasks(changes) { task, taskEdits ->
                    val byIndex = taskEdits.groupBy { (it["index"] as? Number)?.toInt() }
                    task.copy(sets = task.sets.mapIndexed { i, set ->
                        byIndex[i].orEmpty().fold(set) { edited, change ->
                            val text = (change["text"] as? String).orEmpty()
                            when (change["field"]) {
                                "value" -> edited.copy(weightOrDuration = text)
                                "reps" -> edited.copy(reps = text)
                                "rightWeight" -> edited.copy(rightWeight = text)
                                "rightReps" -> edited.copy(rightReps = text)
                                else -> edited
                            }
                        }
                    })
                }
            }
        }
    }

    private fun returnTo(route: String) {
        activity.setResult(Activity.RESULT_OK, Intent().putExtra(RETURN_ROUTE, route))
        activity.finish()
    }

    fun close() {
        editor.close()
        workoutChannel?.setMethodCallHandler(null)
        dailyChannel?.setStreamHandler(null)
        scope.cancel()
        coach.close()
        library.close()
        fallback.clear()
        if (activity.isFinishing) plans.remove(token)
    }
}
