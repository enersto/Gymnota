package com.example.myfit.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.myfit.R
import com.example.myfit.data.AppDatabase
import com.example.myfit.data.WorkoutDao
import com.example.myfit.model.*
import com.example.myfit.ui.ChartDataPoint
import com.example.myfit.ui.ChartGranularity
import com.example.myfit.util.NotificationHelper
import com.example.myfit.util.TimerService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import kotlin.system.exitProcess
import android.os.SystemClock
import android.media.AudioManager
import android.media.ToneGenerator
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import androidx.compose.runtime.mutableIntStateOf
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import java.io.ByteArrayOutputStream
import com.example.myfit.data.ai.PromptManager
import com.example.myfit.data.api.AiService
import com.example.myfit.data.api.ChatMessage
import com.example.myfit.data.api.ChatRequest

enum class TimerPhase {
    IDLE, PREP, WORK
}

data class HeatmapPoint(val intensity: Float, val volume: Float)

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getDatabase(application)
    private val dao: WorkoutDao = database.workoutDao()

    companion object {
        const val KEY_TIMER_PREP_ENABLED = "timer_prep_enabled"
        const val KEY_TIMER_PREP_SECS = "timer_prep_secs"
        const val KEY_TIMER_FINAL_ENABLED = "timer_final_enabled"
        const val KEY_TIMER_FINAL_SECS = "timer_final_secs"
        const val KEY_TIMER_SOUND_ENABLED = "timer_sound_enabled"
    }

    data class AiProviderPreset(
        val name: String,
        val defaultBaseUrl: String,
        val defaultModel: String,
        val website: String = ""
    )

    val availableProviders = listOf(
        AiProviderPreset("OpenAI", "https://api.openai.com/", "gpt-4o-mini"),
        AiProviderPreset("DeepSeek", "https://api.deepseek.com/", "deepseek-chat"),
        AiProviderPreset(
            "Gemini",
            "https://generativelanguage.googleapis.com/v1beta/openai/",
            "gemini-1.5-flash"
        ),
        AiProviderPreset("Xiaomi MiMo", "https://api.xiaomimimo.com/", "mimo-v2-flash"),
        AiProviderPreset("Kimi (Moonshot)", "https://api.moonshot.cn/", "moonshot-v1-8k"),
        AiProviderPreset(
            "Qwen (Aliyun)",
            "https://dashscope.aliyuncs.com/compatible-mode/",
            "qwen-turbo"
        ),
        AiProviderPreset(
            "SiliconFlow",
            "https://api.siliconflow.cn/",
            "deepseek-ai/DeepSeek-V3"
        ), // 硅基流动
        AiProviderPreset("Custom", "", "")
    )

    suspend fun getSavedProviderConfig(providerName: String): Triple<String, String, String> {
        val savedConfig = dao.getAiProviderConfig(providerName)
        if (savedConfig != null) {
            return Triple(savedConfig.apiKey, savedConfig.model, savedConfig.baseUrl)
        }
        val preset = availableProviders.find { it.name == providerName }
        return Triple("", preset?.defaultModel ?: "", preset?.defaultBaseUrl ?: "")
    }

    private val toneGenerator = ToneGenerator(AudioManager.STREAM_ALARM, 100)
    private val prefs = application.getSharedPreferences("myfit_prefs", Context.MODE_PRIVATE)

    fun getTimerPrepEnabled() = prefs.getBoolean(KEY_TIMER_PREP_ENABLED, true)
    fun setTimerPrepEnabled(enable: Boolean) = prefs.edit().putBoolean(KEY_TIMER_PREP_ENABLED, enable).apply()
    fun getTimerPrepSeconds() = prefs.getInt(KEY_TIMER_PREP_SECS, 10)
    fun setTimerPrepSeconds(secs: Int) = prefs.edit().putInt(KEY_TIMER_PREP_SECS, secs).apply()
    fun getTimerFinalEnabled() = prefs.getBoolean(KEY_TIMER_FINAL_ENABLED, true)
    fun setTimerFinalEnabled(enable: Boolean) = prefs.edit().putBoolean(KEY_TIMER_FINAL_ENABLED, enable).apply()
    fun getTimerFinalSeconds() = prefs.getInt(KEY_TIMER_FINAL_SECS, 5)
    fun setTimerFinalSeconds(secs: Int) = prefs.edit().putInt(KEY_TIMER_FINAL_SECS, secs).apply()
    fun getTimerSoundEnabled() = prefs.getBoolean(KEY_TIMER_SOUND_ENABLED, true)
    fun setTimerSoundEnabled(enable: Boolean) = prefs.edit().putBoolean(KEY_TIMER_SOUND_ENABLED, enable).apply()

    private val _selectedDate = MutableStateFlow(LocalDate.now())
    val selectedDate: StateFlow<LocalDate> = _selectedDate.asStateFlow()

    val userProfile = dao.getAppSettings()
        .map { it ?: AppSetting(themeId = 1) }
        .stateIn(viewModelScope, SharingStarted.Lazily, AppSetting(themeId = 1))

    val currentTheme = userProfile
        .map { AppTheme.fromId(it.themeId) }
        .stateIn(viewModelScope, SharingStarted.Lazily, AppTheme.GREEN)

    val currentLanguage = userProfile
        .map { it.languageCode }
        .stateIn(viewModelScope, SharingStarted.Lazily, "zh")

    val allSchedules: Flow<List<ScheduleConfig>> = dao.getAllSchedules()

    private val _todayScheduleType = MutableStateFlow(DayType.CORE)
    val todayScheduleType = combine(_selectedDate, allSchedules) { date, schedules ->
        val dayOfWeek = date.dayOfWeek.value
        val type = schedules.find { it.dayOfWeek == dayOfWeek }?.dayType ?: DayType.CORE
        _todayScheduleType.value = type
        type
    }.stateIn(viewModelScope, SharingStarted.Lazily, DayType.CORE)

    val showWeightAlert = dao.getLatestWeight().map { record ->
        if (record == null) true else ChronoUnit.DAYS.between(LocalDate.parse(record.date), LocalDate.now()) > 7
    }.stateIn(viewModelScope, SharingStarted.Lazily, false)

    val todayTasks: Flow<List<WorkoutTask>> = _selectedDate.flatMapLatest { date ->
        dao.getTasksForDate(date.toString())
    }

    val allTemplates: Flow<List<ExerciseTemplate>> = dao.getAllTemplates()
    val historyRecords: Flow<List<WorkoutTask>> = dao.getAllHistoryTasks()
    val weightHistory: Flow<List<WeightRecord>> = dao.getAllWeightRecords()

    private val _hasShownLockScreenGuide = MutableStateFlow(prefs.getBoolean("key_lockscreen_guide_shown", false))
    val hasShownLockScreenGuide = _hasShownLockScreenGuide.asStateFlow()

    val muscleHeatMapData: Flow<Map<String, HeatmapPoint>> = historyRecords.map { tasks ->
        val currentVolumeMap = mutableMapOf<String, Float>()
        fun parseVal(input: String): Float = Regex("[0-9]+(\\.[0-9]+)?").find(input)?.value?.toFloatOrNull() ?: 0f

        tasks.forEach { task ->
            var taskVolume = 0f
            if (task.sets.isNotEmpty()) {
                task.sets.forEach { set ->
                    val w = parseVal(set.weightOrDuration).coerceAtLeast(1f)
                    val r = parseVal(set.reps).coerceAtLeast(1f)
                    taskVolume += (w * r)
                    if (task.isUnilateral) {
                        val rw = parseVal(set.rightWeight ?: "0")
                        val rr = parseVal(set.rightReps ?: "0")
                        if (rw > 0 && rr > 0) taskVolume += (rw * rr)
                    }
                }
            } else {
                val targetVal = parseVal(task.target)
                taskVolume += if (targetVal > 0) targetVal else 1f
            }
            currentVolumeMap[task.bodyPart] = currentVolumeMap.getOrDefault(task.bodyPart, 0f) + taskVolume
        }
        val globalMaxVolume = currentVolumeMap.values.maxOrNull() ?: 1f
        currentVolumeMap.mapValues { (_, vol) -> HeatmapPoint(intensity = (vol / globalMaxVolume).coerceIn(0f, 1f), volume = vol) }
    }

    fun markLockScreenGuideShown() {
        prefs.edit().putBoolean("key_lockscreen_guide_shown", true).apply()
        _hasShownLockScreenGuide.value = true
    }

    data class TimerState(
        val taskId: Long = -1L,
        val setIndex: Int = -1,
        val totalSeconds: Int = 0,
        val remainingSeconds: Int = 0,
        val endTimeMillis: Long = 0L,
        val isRunning: Boolean = false,
        val isPaused: Boolean = false,
        val phase: TimerPhase = TimerPhase.IDLE,
        val showBigAlert: Boolean = false
    )

    private val _timerState = MutableStateFlow(TimerState())
    val timerState = _timerState.asStateFlow()
    private var timerJob: Job? = null

    init {
        NotificationHelper.createNotificationChannel(application)
    }

    fun startTimer(context: Context, taskId: Long, setIndex: Int, durationMinutes: Float) {
        val prepEnabled = getTimerPrepEnabled()
        if (prepEnabled && _timerState.value.phase == TimerPhase.IDLE) {
            startPrepPhase(context, taskId, setIndex, durationMinutes)
        } else {
            startWorkPhase(context, taskId, setIndex, durationMinutes)
        }
    }

    private fun startPrepPhase(context: Context, taskId: Long, setIndex: Int, durationMinutes: Float) {
        val prepSeconds = getTimerPrepSeconds()
        val now = SystemClock.elapsedRealtime()
        val endTime = now + (prepSeconds * 1000L)
        _timerState.value = TimerState(taskId, setIndex, prepSeconds, prepSeconds, endTime, true, phase = TimerPhase.PREP, showBigAlert = true)
        runTimerLoop(context, isPrep = true) { startWorkPhase(context, taskId, setIndex, durationMinutes) }
    }

    private fun startWorkPhase(context: Context, taskId: Long, setIndex: Int, durationMinutes: Float) {
        val current = _timerState.value
        val now = SystemClock.elapsedRealtime()
        val durationMillis = (durationMinutes * 60 * 1000).toLong()
        val endTimeMillis = if (current.taskId == taskId && current.setIndex == setIndex && current.isPaused && current.phase == TimerPhase.WORK) {
            now + (current.remainingSeconds * 1000L)
        } else {
            now + durationMillis
        }
        val initialRemSeconds = ((endTimeMillis - now) / 1000).toInt()
        _timerState.value = TimerState(taskId, setIndex, (durationMinutes * 60).toInt(), initialRemSeconds, endTimeMillis, isRunning = true, phase = TimerPhase.WORK)

        viewModelScope.launch(Dispatchers.IO) {
            val task = dao.getTaskById(taskId)
            val taskName = task?.name ?: "Training"
            val wallClockEndTime = System.currentTimeMillis() + (endTimeMillis - now)
            val intent = Intent(context, TimerService::class.java).apply {
                action = TimerService.ACTION_START_TIMER
                putExtra(TimerService.EXTRA_TASK_NAME, taskName)
                putExtra(TimerService.EXTRA_END_TIME, wallClockEndTime)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent) else context.startService(intent)
        }
        runTimerLoop(context, isPrep = false) { onTimerFinished(taskId, setIndex, durationMinutes) }
    }

    private fun runTimerLoop(context: Context, isPrep: Boolean, onFinish: suspend () -> Unit) {
        timerJob?.cancel()
        timerJob = viewModelScope.launch(Dispatchers.Default) {
            val finalEnabled = getTimerFinalEnabled()
            val finalSeconds = getTimerFinalSeconds()
            var lastBeepSecond = -1
            val soundEnabled = getTimerSoundEnabled()

            while (_timerState.value.isRunning) {
                val currentNow = SystemClock.elapsedRealtime()
                val targetEnd = _timerState.value.endTimeMillis
                val remSeconds = ((targetEnd - currentNow) / 1000).toInt() + 1
                val displaySeconds = if ((targetEnd - currentNow) <= 0) 0 else remSeconds
                val isFinalCountdown = displaySeconds > 0 && displaySeconds <= if (isPrep) 3 else finalSeconds

                _timerState.update { it.copy(remainingSeconds = displaySeconds, showBigAlert = isPrep || (finalEnabled && isFinalCountdown)) }

                if (soundEnabled && displaySeconds != lastBeepSecond && isFinalCountdown) {
                    try { toneGenerator.startTone(ToneGenerator.TONE_CDMA_PIP, 150) } catch (e: Exception) {}
                    lastBeepSecond = displaySeconds
                }

                if ((targetEnd - currentNow) <= 0) {
                    if (soundEnabled) try { toneGenerator.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 400) } catch (e: Exception) {}
                    _timerState.update { it.copy(remainingSeconds = 0, isRunning = false, showBigAlert = false) }
                    withContext(Dispatchers.Main) {
                        if (!isPrep) stopService(context)
                        onFinish()
                    }
                    break
                }
                delay(100)
            }
        }
    }

    fun pauseTimer(context: Context) {
        _timerState.update { it.copy(isRunning = false, isPaused = true) }
        timerJob?.cancel()
        try { NotificationHelper.updateTimerNotification(context, null, null) } catch (e: Exception) {}
    }

    fun stopTimer(context: Context) {
        _timerState.value = TimerState(phase = TimerPhase.IDLE)
        timerJob?.cancel()
        stopService(context)
    }

    private fun stopService(context: Context) {
        try {
            val intent = Intent(context, TimerService::class.java).apply { action = TimerService.ACTION_STOP_TIMER }
            context.startService(intent)
        } catch (e: Exception) {
            NotificationHelper.cancelNotification(context)
        }
    }

    override fun onCleared() {
        super.onCleared()
        toneGenerator.release()
    }

    private suspend fun onTimerFinished(taskId: Long, setIndex: Int, durationMinutes: Float) {
        _timerState.value = TimerState()
        val task = dao.getTaskById(taskId) ?: return
        val newSets = task.sets.toMutableList()
        if (setIndex < newSets.size) {
            val timeStr = if (durationMinutes % 1.0f == 0f) "${durationMinutes.toInt()}min" else "${durationMinutes}min"
            newSets[setIndex] = newSets[setIndex].copy(weightOrDuration = timeStr, reps = "Done")
        }
        var updatedTask = task.copy(sets = newSets)
        val allSetsDone = newSets.all { it.weightOrDuration.isNotBlank() }
        if (allSetsDone) updatedTask = updatedTask.copy(isCompleted = true)
        dao.updateTask(updatedTask)

        withContext(Dispatchers.Main) {
            val app = getApplication<Application>()
            val msg = if (allSetsDone) app.getString(R.string.toast_task_auto_completed) else app.getString(R.string.toast_set_completed)
            Toast.makeText(app, msg, Toast.LENGTH_SHORT).show()
        }
    }

    private fun calculateBMI(weight: Float, heightCm: Float): Float = if (heightCm <= 0) 0f else weight / ((heightCm / 100f) * (heightCm / 100f))
    private fun calculateBMR(weight: Float, heightCm: Float, age: Int, gender: Int): Float = if (heightCm <= 0 || age <= 0) 0f else (10 * weight) + (6.25f * heightCm) - (5 * age) + (if (gender == 0) 5 else -161)

    fun getBMIChartData(granularity: ChartGranularity): Flow<List<ChartDataPoint>> = combine(weightHistory, userProfile) { weights, profile -> groupAndFormatData(weights.map { Pair(LocalDate.parse(it.date), calculateBMI(it.weight, profile.height)) }, granularity) }
    fun getBMRChartData(granularity: ChartGranularity): Flow<List<ChartDataPoint>> = combine(weightHistory, userProfile) { weights, profile -> groupAndFormatData(weights.map { Pair(LocalDate.parse(it.date), calculateBMR(it.weight, profile.height, profile.age, profile.gender)) }, granularity) }
    fun getBodyFatChartData(granularity: ChartGranularity): Flow<List<ChartDataPoint>> = weightHistory.map { groupAndFormatData(it.filter { it.bodyFatKg != null }.map { Pair(LocalDate.parse(it.date), it.bodyFatKg!!) }, granularity) }
    fun getSkeletalMuscleChartData(granularity: ChartGranularity): Flow<List<ChartDataPoint>> = weightHistory.map { groupAndFormatData(it.filter { it.skeletalMuscleKg != null }.map { Pair(LocalDate.parse(it.date), it.skeletalMuscleKg!!) }, granularity) }
    fun getBodyWaterChartData(granularity: ChartGranularity): Flow<List<ChartDataPoint>> = weightHistory.map { groupAndFormatData(it.filter { it.bodyWaterPercentage != null }.map { Pair(LocalDate.parse(it.date), it.bodyWaterPercentage!!) }, granularity) }
    fun getWHRChartData(granularity: ChartGranularity): Flow<List<ChartDataPoint>> = weightHistory.map { groupAndFormatData(it.filter { it.waistCircumference != null && it.hipCircumference != null }.map { Pair(LocalDate.parse(it.date), it.waistHipRatio ?: 0f) }, granularity) }

    private fun groupAndFormatData(raw: List<Pair<LocalDate, Float>>, granularity: ChartGranularity): List<ChartDataPoint> {
        val grouped = when (granularity) {
            ChartGranularity.DAILY -> raw.groupBy { it.first }
            ChartGranularity.MONTHLY -> raw.groupBy { it.first.withDayOfMonth(1) }
        }
        return grouped.map { (date, list) -> ChartDataPoint(date, list.map { it.second }.average().toFloat(), date.format(DateTimeFormatter.ofPattern("MM/dd"))) }.sortedBy { it.date }
    }

    fun switchTheme(theme: AppTheme) = viewModelScope.launch { dao.saveAppSettings(userProfile.value.copy(themeId = theme.id)) }
    fun switchLanguage(lang: String) = viewModelScope.launch { dao.saveAppSettings(userProfile.value.copy(languageCode = lang)) }

    fun logWeightAndProfile(weight: Float, age: Int?, height: Float?, gender: Int?, bodyFat: Float?, muscle: Float?, water: Float?, waist: Float?, hip: Float?) = viewModelScope.launch {
        if (age != null && height != null) updateProfile(age, height, gender ?: 0)
        dao.insertWeight(WeightRecord(date = LocalDate.now().toString(), weight = weight, bodyFatKg = bodyFat, skeletalMuscleKg = muscle, bodyWaterPercentage = water, waistCircumference = waist, hipCircumference = hip))
    }

    fun updateProfile(age: Int, height: Float, gender: Int) = viewModelScope.launch { dao.saveAppSettings(userProfile.value.copy(age = age, height = height, gender = gender)) }
    fun deleteTemplate(id: Long) = viewModelScope.launch { dao.getTemplateById(id)?.let { dao.deleteTemplate(it) } }
    fun saveTemplate(t: ExerciseTemplate) = viewModelScope.launch { if (t.id == 0L) dao.insertTemplate(t) else dao.updateTemplate(t) }
    fun addTaskFromTemplate(t: ExerciseTemplate) = viewModelScope.launch { dao.insertTask(WorkoutTask(date = _selectedDate.value.toString(), templateId = t.id, name = t.name, category = t.category, target = t.defaultTarget, bodyPart = t.bodyPart, equipment = t.equipment, isUnilateral = t.isUnilateral, logType = t.logType, sets = listOf(WorkoutSet(setNumber = 1, weightOrDuration = "", reps = "")))) }
    fun updateTask(t: WorkoutTask) = viewModelScope.launch { dao.updateTask(t) }
    fun removeTask(t: WorkoutTask) = viewModelScope.launch { dao.deleteTask(t) }

    fun applyWeeklyRoutineToToday() = viewModelScope.launch(Dispatchers.IO) {
        val dateStr = _selectedDate.value.toString()
        dao.getRoutineForDaySync(_selectedDate.value.dayOfWeek.value).forEach { item ->
            dao.insertTask(WorkoutTask(date = dateStr, templateId = item.templateId, name = item.name, category = item.category, target = item.target, bodyPart = item.bodyPart, equipment = item.equipment, isUnilateral = item.isUnilateral, logType = item.logType, sets = listOf(WorkoutSet(setNumber = 1, weightOrDuration = "", reps = ""))))
        }
    }

    fun addRoutineItem(day: Int, template: ExerciseTemplate) = viewModelScope.launch { dao.insertRoutineItem(WeeklyRoutineItem(dayOfWeek = day, templateId = template.id, name = template.name, target = template.defaultTarget, category = template.category, bodyPart = template.bodyPart, equipment = template.equipment, isUnilateral = template.isUnilateral, logType = template.logType)) }
    fun removeRoutineItem(item: WeeklyRoutineItem) = viewModelScope.launch { dao.deleteRoutineItem(item) }
    fun updateScheduleConfig(day: Int, type: DayType) = viewModelScope.launch {
        dao.insertSchedule(ScheduleConfig(dayOfWeek = day, dayType = type))
        if (day == _selectedDate.value.dayOfWeek.value) _todayScheduleType.value = type
    }

    suspend fun getRoutineForDay(day: Int): List<WeeklyRoutineItem> = dao.getRoutineForDay(day)

    fun exportHistoryToCsv(uri: Uri, context: Context) = viewModelScope.launch(Dispatchers.IO) {
        try {
            val sb = StringBuilder("Date,Name,Category,Target,IsUnilateral,LogType,Sets_Detail\n")
            dao.getHistoryRecordsSync().forEach { t ->
                val setsStr = t.sets.joinToString(" | ") { if (t.isUnilateral) "L: ${it.weightOrDuration}x${it.reps} / R: ${it.rightWeight ?: ""}x${it.rightReps ?: ""}" else "${it.weightOrDuration}x${it.reps}" }
                sb.append("${t.date},${t.name.replace(",", " ")},${t.category},${t.target},${t.isUnilateral},${t.logType},$setsStr\n")
            }
            context.contentResolver.openOutputStream(uri)?.use { it.write(sb.toString().toByteArray()) }
            withContext(Dispatchers.Main) { Toast.makeText(context, context.getString(R.string.msg_backup_success), Toast.LENGTH_SHORT).show() }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) { Toast.makeText(context, "Export failed: ${e.message}", Toast.LENGTH_SHORT).show() }
        }
    }

    fun backupDatabase(uri: Uri, context: Context) = viewModelScope.launch(Dispatchers.IO) {
        try {
            if (database.isOpen) {
                val db = database.openHelper.writableDatabase
                db.query("PRAGMA wal_checkpoint(TRUNCATE)").use { it.moveToFirst() }
                db.query("PRAGMA wal_checkpoint(FULL)").use { it.moveToFirst() }
            }
            delay(1000)
            val dbPath = context.getDatabasePath("myfit_v7.db")
            if (dbPath.exists()) {
                context.contentResolver.openOutputStream(uri)?.use { out -> FileInputStream(dbPath).use { it.copyTo(out); out.flush() } }
                withContext(Dispatchers.Main) { Toast.makeText(context, context.getString(R.string.msg_backup_success), Toast.LENGTH_SHORT).show() }
            } else throw Exception("DB not found")
        } catch (e: Exception) {
            withContext(Dispatchers.Main) { Toast.makeText(context, context.getString(R.string.msg_backup_failed, e.message), Toast.LENGTH_LONG).show() }
        }
    }

    fun restoreDatabase(uri: Uri, context: Context) = viewModelScope.launch(Dispatchers.IO) {
        try {
            val dbPath = context.getDatabasePath("myfit_v7.db")
            if (database.isOpen) database.close()
            context.contentResolver.openInputStream(uri)?.use { input -> FileOutputStream(dbPath).use { input.copyTo(it); it.fd.sync() } }
            listOf("-wal", "-shm").forEach { File(dbPath.path + it).let { if (it.exists()) it.delete() } }
            delay(1000)
            if (!dbPath.exists() || dbPath.length() < 1024) throw Exception("Restore failed")
            withContext(Dispatchers.Main) { Toast.makeText(context, context.getString(R.string.msg_restore_success), Toast.LENGTH_SHORT).show(); triggerRestart(context) }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) { Toast.makeText(context, context.getString(R.string.msg_restore_failed, e.message), Toast.LENGTH_LONG).show() }
        }
    }

    private fun triggerRestart(context: Context) {
        context.packageManager.getLaunchIntentForPackage(context.packageName)?.let { context.startActivity(Intent.makeRestartActivityTask(it.component)); exitProcess(0) }
    }

    fun getWeightChartData(granularity: ChartGranularity): Flow<List<ChartDataPoint>> = weightHistory.map { groupAndFormatData(it.map { Pair(LocalDate.parse(it.date), it.weight) }, granularity) }
    fun getCardioTotalChartData(granularity: ChartGranularity): Flow<List<ChartDataPoint>> = historyRecords.map { tasks -> groupAndFormatData(tasks.filter { it.category == "CARDIO" }.groupBy { LocalDate.parse(it.date) }.map { (d, list) -> Pair(d, list.sumOf { t -> if (t.sets.isNotEmpty()) t.sets.sumOf { parseDuration(it.weightOrDuration).toDouble() } else parseDuration(t.target).toDouble() }.toFloat()) }, granularity) }
    fun getExerciseNamesByCategory(category: String): Flow<List<String>> = historyRecords.map { it.filter { it.category == category }.map { it.name }.distinct().sorted() }

    fun getSingleExerciseChartData(name: String, mode: Int, granularity: ChartGranularity): Flow<List<ChartDataPoint>> = historyRecords.map { tasks ->
        groupAndFormatData(tasks.filter { it.name == name }.groupBy { LocalDate.parse(it.date) }.map { (d, list) ->
            val values = list.flatMap { t -> t.sets.ifEmpty { listOf(WorkoutSet(1, t.actualWeight.ifEmpty { t.target }, t.target)) } }
            val dailyVal = when (mode) {
                0 -> values.sumOf { parseDuration(it.weightOrDuration).toDouble() }.toFloat()
                1 -> values.maxOfOrNull { parseValue(it.weightOrDuration) } ?: 0f
                2 -> values.sumOf { parseValue(it.reps).toDouble() }.toFloat()
                3 -> values.maxOfOrNull { parseValue(it.rightWeight ?: "0") } ?: 0f
                else -> 0f
            }
            Pair(d, dailyVal)
        }, granularity)
    }

    private fun parseValue(input: String): Float = Regex("[0-9]+(\\.[0-9]+)?").find(input)?.value?.toFloatOrNull() ?: 0f
    private fun parseDuration(input: String): Float { val lower = input.lowercase(); val num = parseValue(lower); return when { lower.contains("h") -> num * 60; lower.contains("s") && !lower.contains("m") -> num / 60; else -> num } }

    fun importWeeklyRoutine(context: Context, csv: String) = viewModelScope.launch(Dispatchers.IO) {
        try {
            data class PendingItem(val day: Int, val name: String, val category: String, val target: String, val bodyPart: String, val equipment: String, val isUni: Boolean, val logType: Int, val instruction: String)
            val pending = mutableListOf<PendingItem>()
            val daysToOverwrite = mutableSetOf<Int>()

            csv.split("\n").forEachIndexed { i, line ->
                if (i == 0 || line.isBlank()) return@forEachIndexed
                val parts = line.split(Regex(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)")).map { it.trim().removeSurrounding("\"") }
                if (parts.size >= 4) {
                    val day = parts[0].toIntOrNull() ?: 1
                    val category = parts[2]
                    val inferred = when { category.equals("CARDIO", true) || category.contains("有氧") -> 1; category.equals("CORE", true) || category.contains("核心") -> 2; else -> 0 }
                    daysToOverwrite.add(day)
                    pending.add(PendingItem(day, parts[1], category, parts[3], parts.getOrNull(4)?.ifBlank { "part_other" } ?: "part_other" , parts.getOrNull(5)?.ifBlank { "equip_other" } ?: "equip_other", parts.getOrNull(6)?.toBoolean() ?: false, parts.getOrNull(7)?.toIntOrNull() ?: inferred, parts.getOrNull(8)?.replace("\"", "") ?: ""))
                }
            }
            if (pending.isEmpty()) throw Exception("Invalid CSV")
            daysToOverwrite.forEach { day -> dao.getRoutineForDaySync(day).forEach { dao.deleteRoutineItem(it) } }
            pending.forEach { item ->
                val tid = dao.getTemplateByName(item.name)?.id ?: dao.insertTemplate(ExerciseTemplate(name = item.name, category = item.category, defaultTarget = item.target, bodyPart = item.bodyPart, equipment = item.equipment, isUnilateral = item.isUni, logType = item.logType, instruction = item.instruction))
                dao.insertRoutineItem(WeeklyRoutineItem(dayOfWeek = item.day, templateId = tid, name = item.name, target = item.target, category = item.category, bodyPart = item.bodyPart, equipment = item.equipment, isUnilateral = item.isUni, logType = item.logType))
            }
            withContext(Dispatchers.Main) { Toast.makeText(context, "${context.getString(R.string.import_success)}: ${pending.size}", Toast.LENGTH_SHORT).show() }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) { Toast.makeText(context, "${context.getString(R.string.import_error)}\n${e.message}", Toast.LENGTH_LONG).show() }
        }
    }

    fun reloadStandardExercises(context: Context, languageCode: String) = viewModelScope.launch(Dispatchers.IO) {
        try {
            val fileName = when (languageCode) { "en" -> "exercises_en.json"; "ja" -> "exercises_ja.json"; "de" -> "exercises_de.json"; "es" -> "exercises_es.json"; else -> "default_exercises.json" }
            val json = try { context.assets.open(fileName).bufferedReader().use { it.readText() } } catch (e: Exception) { context.assets.open("default_exercises.json").bufferedReader().use { it.readText() } }
            val raw: List<ExerciseTemplate> = Gson().fromJson(json, object : TypeToken<List<ExerciseTemplate>>() {}.type)
            val nameIdMap = dao.getAllTemplatesSync().associate { it.name to it.id }
            dao.insertTemplates(raw.map { it.copy(id = nameIdMap[it.name] ?: 0L) })
            withContext(Dispatchers.Main) { Toast.makeText(context, context.getString(R.string.import_success), Toast.LENGTH_SHORT).show() }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) { Toast.makeText(context, "${context.getString(R.string.import_error)}: ${e.message}", Toast.LENGTH_SHORT).show() }
        }
    }

    fun getLogTypeForExercise(name: String): Flow<Int> = historyRecords.map { list -> list.find { it.name == name }?.logType ?: LogType.WEIGHT_REPS.value }

    private fun compressImageToBase64(context: Context, uri: Uri): String? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val bm = BitmapFactory.decodeStream(stream) ?: return null
                val scale = 1024f / maxOf(bm.width, bm.height).coerceAtLeast(1)
                val scaled = if (scale < 1) Bitmap.createScaledBitmap(bm, (bm.width * scale).toInt(), (bm.height * scale).toInt(), true) else bm
                val out = ByteArrayOutputStream()
                scaled.compress(Bitmap.CompressFormat.JPEG, 70, out)
                Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
            }
        } catch (e: Exception) { null }
    }

    private val _aiResponse = MutableStateFlow<String?>(null)
    val aiResponse = _aiResponse.asStateFlow()
    private val _aiIsLoading = MutableStateFlow(false)
    val aiIsLoading = _aiIsLoading.asStateFlow()

    var currentTrainingGoal by mutableStateOf("")
    var historyWeeks by mutableIntStateOf(3)
    var selectedFocus by mutableStateOf(setOf("COMPREHENSIVE"))
    var selectedScene by mutableStateOf(setOf("GYM"))
    var selectedInjuries by mutableStateOf(setOf<String>())

    sealed class ConnectionState { object Idle : ConnectionState(); object Testing : ConnectionState(); object Success : ConnectionState(); data class Error(val message: String) : ConnectionState() }
    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Idle)
    val connectionState = _connectionState.asStateFlow()
    val aiChatHistory: Flow<List<AiChatRecord>> = dao.getAllAiChatRecords()

    private fun getAiApi(settings: AppSetting): AiService {
        val client = OkHttpClient.Builder().connectTimeout(30, TimeUnit.SECONDS).readTimeout(120, TimeUnit.SECONDS).build()
        val rawUrl = settings.aiBaseUrl.ifBlank { availableProviders.find { it.name == settings.aiProvider }?.defaultBaseUrl ?: "https://api.openai.com/" }
        val finalUrl = "${rawUrl.trim().replace(Regex("v1/?$", RegexOption.IGNORE_CASE), "").trimEnd('/')}/"
        return Retrofit.Builder().baseUrl(finalUrl).client(client).addConverterFactory(GsonConverterFactory.create()).build().create(AiService::class.java)
    }

    fun testAiConnection(provider: String, apiKey: String, model: String, baseUrl: String) {
        if (apiKey.isBlank()) { _connectionState.value = ConnectionState.Error(getApplication<Application>().getString(R.string.msg_config_missing)); return }
        viewModelScope.launch(Dispatchers.IO) {
            _connectionState.value = ConnectionState.Testing
            try {
                val api = getAiApi(AppSetting(aiProvider = provider, aiApiKey = apiKey, aiModel = model, aiBaseUrl = baseUrl))
                val res = api.generateChatCompletion("Bearer $apiKey", ChatRequest(model = model, messages = listOf(ChatMessage("user", "Hello")), temperature = 0.7))
                _connectionState.value = if (res.choices.isNotEmpty()) ConnectionState.Success else ConnectionState.Error("Empty response")
            } catch (e: Exception) {
                _connectionState.value = ConnectionState.Error(getApplication<Application>().getString(R.string.msg_connection_failed, e.message))
            }
        }
    }

    fun saveAiConfig(provider: String, apiKey: String, model: String, baseUrl: String) {
        val clean = baseUrl.trim().replace(Regex("v1/?$", RegexOption.IGNORE_CASE), "").trimEnd('/')
        viewModelScope.launch(Dispatchers.IO) {
            val settings = dao.getAppSettings().first() ?: AppSetting()
            dao.saveAppSettings(settings.copy(aiProvider = provider, aiApiKey = apiKey, aiModel = model, aiBaseUrl = clean))
            dao.saveAiProviderConfig(AiProviderConfig(provider, apiKey, model, clean))
        }
    }

    fun toggleAiCache(enabled: Boolean) = viewModelScope.launch {
        val current = dao.getAppSettings().first() ?: AppSetting()
        dao.saveAppSettings(current.copy(useLocalAiCache = enabled))
    }

    fun updateMaxHistoryLimit(limit: Int) = viewModelScope.launch {
        val current = dao.getAppSettings().first() ?: AppSetting()
        dao.saveAppSettings(current.copy(maxHistoryLimit = limit))
    }

    fun generateWeeklyPlan(context: Context) {
        val settings = userProfile.value
        if (settings.aiApiKey.isBlank()) { Toast.makeText(context, context.getString(R.string.msg_config_missing), Toast.LENGTH_SHORT).show(); return }
        _aiIsLoading.value = true; _aiResponse.value = null

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val cutoff = LocalDate.now().minusWeeks(historyWeeks.toLong()).toString()
                val history = dao.getHistoryRecordsSync().filter { it.date >= cutoff }
                val schedules = dao.getAllSchedulesSync()
                val latestWeight = dao.getLatestWeightSync()
                val memory = dao.getAiMemorySync()?.longTermContext

                val messages = PromptManager.buildWeeklyPlanPrompt(context, settings, latestWeight, memory, schedules, history, currentTrainingGoal, historyWeeks, selectedFocus, selectedScene, selectedInjuries)
                val response = getAiApi(settings).generateChatCompletion("Bearer ${settings.aiApiKey}", ChatRequest(model = settings.aiModel, messages = messages))
                val content = response.choices.firstOrNull()?.message?.content?.toString() ?: "No content"
                _aiResponse.value = content
                saveAiChatRecord(content, "assistant", currentTrainingGoal, settings.aiModel)
            } catch (e: Exception) { _aiResponse.value = "Error: ${e.message}" } finally { _aiIsLoading.value = false }
        }
    }

    fun saveAiChatRecord(content: String, role: String, userGoal: String? = null, model: String? = null) {
        viewModelScope.launch(Dispatchers.IO) { dao.insertAiChatRecord(AiChatRecord(timestamp = System.currentTimeMillis(), role = role, content = content, userGoal = userGoal, modelUsed = model)) }
    }

    fun deleteAiChatRecord(id: Long) = viewModelScope.launch(Dispatchers.IO) { dao.deleteAiChatRecord(id) }
    fun loadAiResponseFromHistory(content: String) { _aiResponse.value = content }

    fun analyzeImage(context: Context, uri: Uri) {
        val settings = userProfile.value
        if (settings.aiApiKey.isBlank()) { Toast.makeText(context, "Config required", Toast.LENGTH_SHORT).show(); return }
        _aiIsLoading.value = true; _aiResponse.value = context.getString(R.string.msg_processing)

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val b64 = compressImageToBase64(context, uri) ?: throw Exception("Image error")
                val messages = PromptManager.buildImageAnalysisPrompt(context, b64)
                val res = getAiApi(settings).generateChatCompletion("Bearer ${settings.aiApiKey}", ChatRequest(model = settings.aiModel, messages = messages))
                val content = res.choices.firstOrNull()?.message?.content?.toString() ?: "No result"
                _aiResponse.value = content
                saveAiChatRecord(content, "assistant", "[Vision] Analysis", settings.aiModel)
            } catch (e: Exception) { _aiResponse.value = "Error: ${e.message}" } finally { _aiIsLoading.value = false }
        }
    }

    fun sendFreeChat(context: Context, query: String, imageUri: Uri? = null) {
        val settings = userProfile.value
        if (settings.aiApiKey.isBlank()) { Toast.makeText(context, context.getString(R.string.msg_config_missing), Toast.LENGTH_SHORT).show(); return }
        _aiIsLoading.value = true; _aiResponse.value = null

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val chatHistory = if (settings.useLocalAiCache) dao.getRecentAiChatRecords(settings.maxHistoryLimit) else emptyList()
                val memory = if (settings.useLocalAiCache) dao.getAiMemorySync()?.longTermContext else null

                val messages = if (imageUri != null) {
                    val b64 = compressImageToBase64(context, imageUri)
                    if (b64 != null) PromptManager.buildMultimodalChatPrompt(context, query, b64)
                    else { withContext(Dispatchers.Main) { Toast.makeText(context, "Image fail, fallback", Toast.LENGTH_SHORT).show() }; PromptManager.buildFreeChatPrompt(context, query, chatHistory, memory) }
                } else PromptManager.buildFreeChatPrompt(context, query, chatHistory, memory)

                val res = getAiApi(settings).generateChatCompletion("Bearer ${settings.aiApiKey}", ChatRequest(model = settings.aiModel, messages = messages))
                val content = res.choices.firstOrNull()?.message?.content?.toString() ?: "AI No response"
                _aiResponse.value = content
                saveAiChatRecord(content, "assistant", "Free Chat: $query", settings.aiModel)
            } catch (e: Exception) { _aiResponse.value = "Error: ${e.message}" } finally { _aiIsLoading.value = false }
        }
    }

    fun summarizeAndSaveMemory(context: Context) {
        val settings = userProfile.value
        if (!settings.useLocalAiCache || settings.aiApiKey.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val history = dao.getRecentAiChatRecords(20)
                if (history.isEmpty()) return@launch
                val messages = PromptManager.buildMemorySummaryPrompt(context, history)
                val res = getAiApi(settings).generateChatCompletion("Bearer ${settings.aiApiKey}", ChatRequest(model = settings.aiModel, messages = messages))
                val summary = res.choices.firstOrNull()?.message?.content?.toString()
                if (!summary.isNullOrBlank()) dao.saveAiMemory(AiMemory(0, summary, System.currentTimeMillis()))
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    private val _isGeneratingCsv = MutableStateFlow(false)
    val isGeneratingCsv = _isGeneratingCsv.asStateFlow()

    fun generateCsvFromAdvice(context: Context, advice: String, userFeedback: String, onResult: (String) -> Unit) {
        val settings = userProfile.value
        if (settings.aiApiKey.isBlank()) return
        _isGeneratingCsv.value = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val messages = PromptManager.buildCsvPlanPrompt(context, advice, userFeedback)
                val res = getAiApi(settings).generateChatCompletion("Bearer ${settings.aiApiKey}", ChatRequest(model = settings.aiModel, messages = messages))
                val clean = (res.choices.firstOrNull()?.message?.content?.toString() ?: "").replace("```csv", "").replace("```", "").trim()
                withContext(Dispatchers.Main) { onResult(clean) }
            } catch (e: Exception) { withContext(Dispatchers.Main) { Toast.makeText(context, "CSV Gen Error: ${e.message}", Toast.LENGTH_SHORT).show() } }
            finally { _isGeneratingCsv.value = false }
        }
    }
}
