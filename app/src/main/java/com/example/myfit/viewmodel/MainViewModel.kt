package com.example.myfit.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.room.withTransaction
import com.example.myfit.data.RustTimer
import com.example.myfit.data.RustWeeklyRoutine
import com.example.myfit.data.RustWorkoutStorage
import com.example.myfit.data.RustLiveQueries
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.myfit.R
import com.example.myfit.data.AppDatabase
import com.example.myfit.data.DatabaseBackups
import com.example.myfit.data.WorkoutDao
import com.example.myfit.model.*
import com.example.myfit.ui.ChartDataPoint
import com.example.myfit.ui.ChartGranularity
import com.example.myfit.util.NotificationHelper
import com.example.myfit.util.TimerService
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.currentCoroutineContext
import com.example.myfit.data.ai.*
import com.example.myfit.data.api.StreamingChatRequest
import com.example.myfit.data.api.ChatRequest

enum class TimerPhase {
    IDLE, PREP, WORK
}

data class HeatmapPoint(val intensity: Float, val volume: Float)

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val database by lazy { AppDatabase.getDatabase(application) }
    private val dao: WorkoutDao by lazy { database.workoutDao() }

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
        val savedConfig = if (com.example.myfit.BuildConfig.RUST_HISTORY_ENABLED)
            RustLiveQueries.read<AiProviderConfig>(getApplication<Application>(), "providerConfigs")
                .firstOrNull { it.providerName == providerName }
            else dao.getAiProviderConfig(providerName)
        if (savedConfig != null) {
            return Triple(savedConfig.apiKey, savedConfig.model, savedConfig.baseUrl)
        }
        val preset = availableProviders.find { it.name == providerName }
        return Triple("", preset?.defaultModel ?: "", preset?.defaultBaseUrl ?: "")
    }

    private val toneGenerator = ToneGenerator(AudioManager.STREAM_ALARM, 100)
    private val prefs = application.getSharedPreferences("myfit_prefs", Context.MODE_PRIVATE)

    fun getTimerPrepEnabled() = prefs.getBoolean(KEY_TIMER_PREP_ENABLED, true)
    fun setTimerPrepEnabled(enable: Boolean) =
        prefs.edit().putBoolean(KEY_TIMER_PREP_ENABLED, enable).apply()

    fun getTimerPrepSeconds() = prefs.getInt(KEY_TIMER_PREP_SECS, 10)
    fun setTimerPrepSeconds(secs: Int) = prefs.edit().putInt(KEY_TIMER_PREP_SECS, secs).apply()
    fun getTimerFinalEnabled() = prefs.getBoolean(KEY_TIMER_FINAL_ENABLED, true)
    fun setTimerFinalEnabled(enable: Boolean) =
        prefs.edit().putBoolean(KEY_TIMER_FINAL_ENABLED, enable).apply()

    fun getTimerFinalSeconds() = prefs.getInt(KEY_TIMER_FINAL_SECS, 5)
    fun setTimerFinalSeconds(secs: Int) = prefs.edit().putInt(KEY_TIMER_FINAL_SECS, secs).apply()
    fun getTimerSoundEnabled() = prefs.getBoolean(KEY_TIMER_SOUND_ENABLED, true)
    fun setTimerSoundEnabled(enable: Boolean) =
        prefs.edit().putBoolean(KEY_TIMER_SOUND_ENABLED, enable).apply()

    private val _selectedDate = MutableStateFlow(LocalDate.now())
    val selectedDate: StateFlow<LocalDate> = _selectedDate.asStateFlow()

    private val initialLanguage = com.example.myfit.util.LocaleHelper.cachedLanguage(application)
    private val profileData: Flow<AppSetting?> = if (com.example.myfit.BuildConfig.RUST_HISTORY_ENABLED)
        RustLiveQueries.observe<AppSetting>(getApplication<Application>(), "app_settings",
            RustWorkoutStorage.profileRevision, "profile").map { it.firstOrNull() }
        else RustWorkoutStorage.profileRevision.flatMapLatest { dao.getAppSettings() }
    val userProfile = profileData
        .map { it ?: AppSetting(themeId = 1) }
        .stateIn(viewModelScope, SharingStarted.Lazily, AppSetting(themeId = 1, languageCode = initialLanguage))

    val currentTheme = userProfile
        .map { AppTheme.fromId(it.themeId) }
        .stateIn(viewModelScope, SharingStarted.Lazily, AppTheme.GREEN)

    val currentLanguage = userProfile
        .map { it.languageCode }
        .stateIn(viewModelScope, SharingStarted.Lazily, initialLanguage)

    val allSchedules: Flow<List<ScheduleConfig>> = if (com.example.myfit.BuildConfig.RUST_HISTORY_ENABLED)
        RustLiveQueries.observe<ScheduleConfig>(getApplication<Application>(), "schedule_config",
            RustWorkoutStorage.scheduleRevision, "schedules")
        else dao.getAllSchedules()

    private val _todayScheduleType = MutableStateFlow(DayType.CORE)
    val todayScheduleType = combine(_selectedDate, allSchedules) { date, schedules ->
        val dayOfWeek = date.dayOfWeek.value
        val type = schedules.find { it.dayOfWeek == dayOfWeek }?.dayType ?: DayType.CORE
        _todayScheduleType.value = type
        type
    }.stateIn(viewModelScope, SharingStarted.Lazily, DayType.CORE)

    private val latestWeight = if (com.example.myfit.BuildConfig.RUST_HISTORY_ENABLED)
        RustLiveQueries.observe<WeightRecord>(getApplication<Application>(), "weight_records",
            RustWorkoutStorage.weightRevision, "latestWeight").map { it.firstOrNull() }
        else RustWorkoutStorage.weightRevision.flatMapLatest { dao.getLatestWeight() }
    val showWeightAlert = latestWeight.map { record ->
        if (record == null) true else ChronoUnit.DAYS.between(
            LocalDate.parse(record.date),
            LocalDate.now()
        ) > 7
    }.stateIn(viewModelScope, SharingStarted.Lazily, false)

    internal val dailyPlanTasks = _selectedDate.flatMapLatest { date ->
        val tasks = if (com.example.myfit.BuildConfig.RUST_HISTORY_ENABLED)
            RustLiveQueries.observe<WorkoutTask>(getApplication<Application>(), "workout_tasks",
                RustWorkoutStorage.workoutRevision, "daily", date.toString())
        else RustWorkoutStorage.workoutRevision.flatMapLatest { dao.getTasksForDate(date.toString()) }
        tasks.map { date to it }
    }
    val todayTasks: Flow<List<WorkoutTask>> = dailyPlanTasks.map { it.second }

    val allTemplates: Flow<List<ExerciseTemplate>> = if (com.example.myfit.BuildConfig.RUST_HISTORY_ENABLED)
        RustLiveQueries.observe<ExerciseTemplate>(getApplication<Application>(), "exercise_templates",
            RustWorkoutStorage.templateRevision, "templates")
        else RustWorkoutStorage.templateRevision.flatMapLatest { dao.getAllTemplates() }
    val historyRecords: Flow<List<WorkoutTask>> = if (com.example.myfit.BuildConfig.RUST_HISTORY_ENABLED)
        RustLiveQueries.observe<WorkoutTask>(getApplication<Application>(), "workout_tasks",
            RustWorkoutStorage.workoutRevision, "completed")
        else RustWorkoutStorage.workoutRevision.flatMapLatest { dao.getAllHistoryTasks() }
    val weightHistory: Flow<List<WeightRecord>> = if (com.example.myfit.BuildConfig.RUST_HISTORY_ENABLED)
        RustLiveQueries.observe<WeightRecord>(getApplication<Application>(), "weight_records",
            RustWorkoutStorage.weightRevision, "weights")
        else RustWorkoutStorage.weightRevision.flatMapLatest { dao.getAllWeightRecords() }

    private val _hasShownLockScreenGuide =
        MutableStateFlow(prefs.getBoolean("key_lockscreen_guide_shown", false))
    val hasShownLockScreenGuide = _hasShownLockScreenGuide.asStateFlow()

    val muscleHeatMapData: Flow<Map<String, HeatmapPoint>> = historyRecords.map { tasks ->
        val currentVolumeMap = mutableMapOf<String, Float>()
        fun parseVal(input: String): Float =
            Regex("[0-9]+(\\.[0-9]+)?").find(input)?.value?.toFloatOrNull() ?: 0f

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
            currentVolumeMap[task.bodyPart] =
                currentVolumeMap.getOrDefault(task.bodyPart, 0f) + taskVolume
        }
        val globalMaxVolume = currentVolumeMap.values.maxOrNull() ?: 1f
        currentVolumeMap.mapValues { (_, vol) ->
            HeatmapPoint(
                intensity = (vol / globalMaxVolume).coerceIn(
                    0f,
                    1f
                ), volume = vol
            )
        }
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

    private fun startPrepPhase(
        context: Context,
        taskId: Long,
        setIndex: Int,
        durationMinutes: Float
    ) {
        val prepSeconds = getTimerPrepSeconds()
        val now = SystemClock.elapsedRealtime()
        val endTime = now + (prepSeconds * 1000L)
        _timerState.value = if (com.example.myfit.BuildConfig.RUST_HISTORY_ENABLED)
            RustTimer.transition("prep", TimerState(taskId = taskId, setIndex = setIndex), now,
                mapOf("prepSeconds" to prepSeconds)).state
        else TimerState(
            taskId,
            setIndex,
            prepSeconds,
            prepSeconds,
            endTime,
            true,
            phase = TimerPhase.PREP,
            showBigAlert = true
        )
        runTimerLoop(context, isPrep = true) {
            startWorkPhase(
                context,
                taskId,
                setIndex,
                durationMinutes
            )
        }
    }

    private fun startWorkPhase(
        context: Context,
        taskId: Long,
        setIndex: Int,
        durationMinutes: Float
    ) {
        val current = _timerState.value
        val now = SystemClock.elapsedRealtime()
        val durationMillis = (durationMinutes * 60 * 1000).toLong()
        val next = if (com.example.myfit.BuildConfig.RUST_HISTORY_ENABLED) {
            RustTimer.transition("work", current, now, mapOf(
                "taskId" to taskId, "setIndex" to setIndex, "durationMillis" to durationMillis,
                "totalSeconds" to (durationMinutes * 60).toInt()
            )).state
        } else {
            val end = if (current.taskId == taskId && current.setIndex == setIndex &&
                current.isPaused && current.phase == TimerPhase.WORK)
                now + current.remainingSeconds * 1000L else now + durationMillis
            TimerState(taskId, setIndex, (durationMinutes * 60).toInt(),
                ((end - now) / 1000).toInt(), end, isRunning = true, phase = TimerPhase.WORK)
        }
        _timerState.value = next
        val endTimeMillis = next.endTimeMillis

        viewModelScope.launch(Dispatchers.IO) {
            val task = readTask(taskId)
            val taskName = task?.name ?: "Training"
            val wallClockEndTime = System.currentTimeMillis() + (endTimeMillis - now)
            val intent = Intent(context, TimerService::class.java).apply {
                action = TimerService.ACTION_START_TIMER
                putExtra(TimerService.EXTRA_TASK_NAME, taskName)
                putExtra(TimerService.EXTRA_END_TIME, wallClockEndTime)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(
                intent
            ) else context.startService(intent)
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
                if (com.example.myfit.BuildConfig.RUST_HISTORY_ENABLED) {
                    val observed = _timerState.value
                    val result = RustTimer.transition("tick", observed, SystemClock.elapsedRealtime(), mapOf(
                        "finalSeconds" to finalSeconds, "finalEnabled" to finalEnabled,
                        "soundEnabled" to soundEnabled, "lastBeepSecond" to lastBeepSecond
                    ))
                    if (!_timerState.compareAndSet(observed, result.state)) continue
                    if (result.beep) {
                        try { toneGenerator.startTone(ToneGenerator.TONE_CDMA_PIP, 150) } catch (_: Exception) {}
                        lastBeepSecond = result.state.remainingSeconds
                    }
                    if (result.finished) {
                        if (soundEnabled) try {
                            toneGenerator.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 400)
                        } catch (_: Exception) {}
                        withContext(Dispatchers.Main) {
                            if (!isPrep) stopService(context)
                            onFinish()
                        }
                        break
                    }
                    delay(100)
                    continue
                }
                val currentNow = SystemClock.elapsedRealtime()
                val targetEnd = _timerState.value.endTimeMillis
                val remSeconds = ((targetEnd - currentNow) / 1000).toInt() + 1
                val displaySeconds = if ((targetEnd - currentNow) <= 0) 0 else remSeconds
                val isFinalCountdown =
                    displaySeconds > 0 && displaySeconds <= if (isPrep) 3 else finalSeconds

                _timerState.update {
                    it.copy(
                        remainingSeconds = displaySeconds,
                        showBigAlert = isPrep || (finalEnabled && isFinalCountdown)
                    )
                }

                if (soundEnabled && displaySeconds != lastBeepSecond && isFinalCountdown) {
                    try {
                        toneGenerator.startTone(ToneGenerator.TONE_CDMA_PIP, 150)
                    } catch (e: Exception) {
                    }
                    lastBeepSecond = displaySeconds
                }

                if ((targetEnd - currentNow) <= 0) {
                    if (soundEnabled) try {
                        toneGenerator.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 400)
                    } catch (e: Exception) {
                    }
                    _timerState.update {
                        it.copy(
                            remainingSeconds = 0,
                            isRunning = false,
                            showBigAlert = false
                        )
                    }
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
        _timerState.update {
            if (com.example.myfit.BuildConfig.RUST_HISTORY_ENABLED)
                RustTimer.transition("pause", it, SystemClock.elapsedRealtime()).state
            else it.copy(isRunning = false, isPaused = true)
        }
        timerJob?.cancel()
        try {
            NotificationHelper.updateTimerNotification(context, null, null)
        } catch (e: Exception) {
        }
    }

    fun stopTimer(context: Context) {
        _timerState.value = TimerState(phase = TimerPhase.IDLE)
        timerJob?.cancel()
        stopService(context)
    }

    private fun stopService(context: Context) {
        try {
            val intent = Intent(context, TimerService::class.java).apply {
                action = TimerService.ACTION_STOP_TIMER
            }
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
        val task = readTask(taskId) ?: return
        if (com.example.myfit.BuildConfig.RUST_HISTORY_ENABLED) {
            val timeStr = if (durationMinutes % 1.0f == 0f) "${durationMinutes.toInt()}min" else "${durationMinutes}min"
            RustWorkoutStorage.mutate(getApplication<Application>(), task.date,
                listOf(mapOf("action" to "timerFinished", "id" to taskId, "index" to setIndex, "value" to timeStr)))
            val complete = readTask(taskId)?.isCompleted == true
            withContext(Dispatchers.Main) {
                val app = getApplication<Application>()
                Toast.makeText(app, app.getString(if (complete) R.string.toast_task_auto_completed
                    else R.string.toast_set_completed), Toast.LENGTH_SHORT).show()
            }
            return
        }
        val newSets = task.sets.toMutableList()
        if (setIndex < newSets.size) {
            val timeStr =
                if (durationMinutes % 1.0f == 0f) "${durationMinutes.toInt()}min" else "${durationMinutes}min"
            newSets[setIndex] = newSets[setIndex].copy(weightOrDuration = timeStr, reps = "Done")
        }
        var updatedTask = task.copy(sets = newSets)
        val allSetsDone = newSets.all { it.weightOrDuration.isNotBlank() }
        if (allSetsDone) updatedTask = updatedTask.copy(isCompleted = true)
        dao.updateTask(updatedTask)

        withContext(Dispatchers.Main) {
            val app = getApplication<Application>()
            val msg =
                if (allSetsDone) app.getString(R.string.toast_task_auto_completed) else app.getString(
                    R.string.toast_set_completed
                )
            Toast.makeText(app, msg, Toast.LENGTH_SHORT).show()
        }
    }

    private fun calculateBMI(weight: Float, heightCm: Float): Float =
        if (heightCm <= 0) 0f else weight / ((heightCm / 100f) * (heightCm / 100f))

    private fun calculateBMR(weight: Float, heightCm: Float, age: Int, gender: Int): Float =
        if (heightCm <= 0 || age <= 0) 0f else (10 * weight) + (6.25f * heightCm) - (5 * age) + (if (gender == 0) 5 else -161)

    fun getBMIChartData(granularity: ChartGranularity): Flow<List<ChartDataPoint>> = combine(
        weightHistory,
        userProfile
    ) { weights, profile ->
        groupAndFormatData(weights.map {
            Pair(
                LocalDate.parse(it.date),
                calculateBMI(it.weight, profile.height)
            )
        }, granularity)
    }

    fun getBMRChartData(granularity: ChartGranularity): Flow<List<ChartDataPoint>> = combine(
        weightHistory,
        userProfile
    ) { weights, profile ->
        groupAndFormatData(weights.map {
            Pair(
                LocalDate.parse(it.date),
                calculateBMR(it.weight, profile.height, profile.age, profile.gender)
            )
        }, granularity)
    }

    fun getBodyFatChartData(granularity: ChartGranularity): Flow<List<ChartDataPoint>> =
        weightHistory.map {
            groupAndFormatData(it.filter { it.bodyFatKg != null }
                .map { Pair(LocalDate.parse(it.date), it.bodyFatKg!!) }, granularity)
        }

    fun getSkeletalMuscleChartData(granularity: ChartGranularity): Flow<List<ChartDataPoint>> =
        weightHistory.map {
            groupAndFormatData(it.filter { it.skeletalMuscleKg != null }
                .map { Pair(LocalDate.parse(it.date), it.skeletalMuscleKg!!) }, granularity)
        }

    fun getBodyWaterChartData(granularity: ChartGranularity): Flow<List<ChartDataPoint>> =
        weightHistory.map {
            groupAndFormatData(it.filter { it.bodyWaterPercentage != null }
                .map { Pair(LocalDate.parse(it.date), it.bodyWaterPercentage!!) }, granularity)
        }

    fun getWHRChartData(granularity: ChartGranularity): Flow<List<ChartDataPoint>> =
        weightHistory.map {
            groupAndFormatData(it.filter { it.waistCircumference != null && it.hipCircumference != null }
                .map { Pair(LocalDate.parse(it.date), it.waistHipRatio ?: 0f) }, granularity)
        }

    private fun groupAndFormatData(
        raw: List<Pair<LocalDate, Float>>,
        granularity: ChartGranularity
    ): List<ChartDataPoint> {
        val grouped = when (granularity) {
            ChartGranularity.DAILY -> raw.groupBy { it.first }
            ChartGranularity.MONTHLY -> raw.groupBy { it.first.withDayOfMonth(1) }
        }
        return grouped.map { (date, list) ->
            ChartDataPoint(
                date,
                list.map { it.second }.average().toFloat(),
                date.format(DateTimeFormatter.ofPattern("MM/dd"))
            )
        }.sortedBy { it.date }
    }

    internal suspend fun persistSetting(field: String, value: Any) {
        if (com.example.myfit.BuildConfig.RUST_HISTORY_ENABLED) {
            RustWorkoutStorage.mutate(getApplication<Application>(), LocalDate.now().toString(),
                listOf(mapOf("action" to "patchSettings", "field" to field, "value" to value)))
            return
        }
        database.withTransaction {
            val current = dao.getAppSettings().first() ?: AppSetting()
            val next = when (field) {
                "themeId" -> current.copy(themeId = value as Int)
                "languageCode" -> current.copy(languageCode = value as String)
                "useLocalAiCache" -> current.copy(useLocalAiCache = value as Boolean)
                "maxHistoryLimit" -> current.copy(maxHistoryLimit = value as Int)
                else -> error("Unknown setting")
            }
            dao.saveAppSettings(next)
        }
    }

    fun switchTheme(theme: AppTheme) = viewModelScope.launch { persistSetting("themeId", theme.id) }

    fun switchLanguage(lang: String) = viewModelScope.launch { persistSetting("languageCode", lang) }

    fun logWeightAndProfile(
        weight: Float,
        age: Int?,
        height: Float?,
        gender: Int?,
        bodyFat: Float?,
        muscle: Float?,
        water: Float?,
        waist: Float?,
        hip: Float?
    ) = viewModelScope.launch {
        if (com.example.myfit.BuildConfig.RUST_HISTORY_ENABLED) {
            val fields = mutableMapOf<String, Any?>("weight" to weight, "fat" to bodyFat,
                "muscle" to muscle, "water" to water, "waist" to waist, "hip" to hip)
            if (age != null && height != null) fields["profile"] = mapOf(
                "age" to age, "height" to height, "gender" to (gender ?: 0))
            RustWorkoutStorage.mutate(getApplication<Application>(), LocalDate.now().toString(),
                listOf(mapOf("action" to "saveWeight", "fields" to fields)))
            return@launch
        }
        if (age != null && height != null) updateProfile(age, height, gender ?: 0)
        dao.insertWeight(
            WeightRecord(
                date = LocalDate.now().toString(),
                weight = weight,
                bodyFatKg = bodyFat,
                skeletalMuscleKg = muscle,
                bodyWaterPercentage = water,
                waistCircumference = waist,
                hipCircumference = hip
            )
        )
    }

    fun updateProfile(age: Int, height: Float, gender: Int) = viewModelScope.launch {
        if (com.example.myfit.BuildConfig.RUST_HISTORY_ENABLED) {
            RustWorkoutStorage.mutate(getApplication<Application>(), LocalDate.now().toString(),
                listOf(mapOf("action" to "saveProfile", "fields" to mapOf("age" to age, "height" to height, "gender" to gender))))
            return@launch
        }
        dao.saveAppSettings(
            userProfile.value.copy(
                age = age,
                height = height,
                gender = gender
            )
        )
    }

    fun deleteTemplate(id: Long) = viewModelScope.launch { deletePersonalTemplate(id) }

    internal suspend fun deletePersonalTemplate(id: Long) {
        if (com.example.myfit.BuildConfig.RUST_HISTORY_ENABLED) {
            RustWorkoutStorage.mutate(getApplication<Application>(), LocalDate.now().toString(),
                listOf(mapOf("action" to "deleteTemplate", "id" to id)))
        } else {
            dao.getTemplateById(id)?.let { dao.deleteTemplate(it) }
        }
    }

    fun saveTemplate(t: ExerciseTemplate) = viewModelScope.launch { savePersonalTemplate(t) }

    internal suspend fun savePersonalTemplate(t: ExerciseTemplate) {
        if (com.example.myfit.BuildConfig.RUST_HISTORY_ENABLED) {
            val fields = mapOf(
                "name" to t.name, "target" to t.defaultTarget, "category" to t.category,
                "bodyPart" to t.bodyPart, "equipment" to t.equipment, "instruction" to t.instruction,
                "isUnilateral" to t.isUnilateral, "logType" to t.logType, "imageUri" to t.imageUri,
                "animationUri" to t.animationUri
            )
            RustWorkoutStorage.mutate(getApplication<Application>(), LocalDate.now().toString(),
                listOf(mapOf("action" to "saveTemplate", "id" to t.id, "fields" to fields)))
            return
        }
        if (t.id == 0L) {
            dao.insertTemplate(t)
        } else {
            database.withTransaction {
                val previous = dao.getTemplateById(t.id)
                dao.updateTemplate(t)
                val removedPhoto = previous?.let { com.example.myfit.data.CatalogMedia.photoUri(it) }
                if (removedPhoto != null && t.imageUri == null) {
                    dao.clearTaskPhoto(t.id, removedPhoto)
                    dao.clearRoutinePhoto(t.id, removedPhoto)
                }
            }
        }
    }

    fun addTaskFromTemplate(t: ExerciseTemplate) = viewModelScope.launch {
        if (com.example.myfit.BuildConfig.RUST_HISTORY_ENABLED) {
            RustWorkoutStorage.mutate(getApplication<Application>(), selectedDate.value.toString(),
                listOf(mapOf("action" to "addExercise", "id" to t.id)))
            return@launch
        }
        dao.insertTask(
            WorkoutTask(
                date = _selectedDate.value.toString(),
                templateId = t.id,
                name = t.name,
                category = t.category,
                target = t.defaultTarget,
                bodyPart = t.bodyPart,
                equipment = t.equipment,
                isUnilateral = t.isUnilateral,
                logType = t.logType,
                sets = listOf(WorkoutSet(setNumber = 1, weightOrDuration = "", reps = ""))
            )
        )
    }

    private suspend fun readTask(id: Long): WorkoutTask? =
        if (com.example.myfit.BuildConfig.RUST_HISTORY_ENABLED)
            RustLiveQueries.read<WorkoutTask>(getApplication<Application>(), "task", id = id).firstOrNull()
        else dao.getTaskById(id)

    internal suspend fun dailyTask(id: Long) =
        readTask(id)?.takeIf { it.date == selectedDate.value.toString() }

    internal suspend fun mutateDailyTask(id: Long, delete: Boolean = false, transform: (WorkoutTask) -> WorkoutTask) =
        database.withTransaction {
            val task = dao.getTaskById(id)?.takeIf { it.date == selectedDate.value.toString() } ?: return@withTransaction
            if (delete) dao.deleteTask(task) else dao.updateTask(transform(task))
        }

    internal suspend fun <T> updateDailyTasks(
        changes: Map<Long, T>,
        transform: (WorkoutTask, T) -> WorkoutTask
    ) = database.withTransaction {
        val date = selectedDate.value.toString()
        for ((id, change) in changes) {
            val task = dao.getTaskById(id)?.takeIf { it.date == date } ?: continue
            val updated = transform(task, change)
            if (updated != task) dao.updateTask(updated)
        }
    }

    // The archived Compose page uses the same field-level transactions as Flutter.
    fun editWorkout(task: WorkoutTask, action: String, index: Int = -1, set: WorkoutSet? = null) = viewModelScope.launch {
        if (action == "removeSet" && timerState.value.taskId == task.id) stopTimer(getApplication<Application>())
        if (com.example.myfit.BuildConfig.RUST_HISTORY_ENABLED) {
            val edits = set?.let { value ->
                listOf("value" to value.weightOrDuration, "reps" to value.reps,
                    "rightWeight" to value.rightWeight, "rightReps" to value.rightReps).map { (field, text) ->
                    mapOf("index" to index, "field" to field, "text" to text.orEmpty())
                }
            }
            RustWorkoutStorage.mutate(getApplication<Application>(), task.date,
                listOf(mapOf("action" to action, "id" to task.id, "index" to index, "edits" to edits)))
        } else {
            val current = dao.getTaskById(task.id) ?: return@launch
            // The non-Rust build retains the original Kotlin interaction rules.
            val next = when (action) {
                "check" -> {
                    val done = !current.isCompleted
                    val auto = current.logType in listOf(1, 2) || current.category.contains("有氧") || current.category.contains("核心")
                    val target = current.target.replace(" ", "")
                    current.copy(isCompleted = done, sets = current.sets.map {
                        if (done && auto && it.weightOrDuration.isBlank() && it.reps.isBlank())
                            it.copy(weightOrDuration = target, reps = if (current.logType == 1) "Done" else target) else it
                    })
                }
                "addSet" -> current.copy(sets = current.sets + (current.sets.lastOrNull()
                    ?.copy(setNumber = current.sets.size + 1) ?: WorkoutSet(1, "", "")))
                "removeSet" -> current.copy(sets = current.sets.filterIndexed { i, _ -> i != index })
                "setFields" -> current.copy(sets = current.sets.mapIndexed { i, item -> if (i == index) set ?: item else item })
                else -> error("Unknown workout edit")
            }
            dao.updateTask(next)
        }
    }

    fun removeTask(t: WorkoutTask) = viewModelScope.launch {
        if (timerState.value.taskId == t.id) stopTimer(getApplication<Application>())
        if (com.example.myfit.BuildConfig.RUST_HISTORY_ENABLED)
            RustWorkoutStorage.mutate(getApplication<Application>(), t.date,
                listOf(mapOf("action" to "removeTask", "id" to t.id)))
        else dao.deleteTask(t)
    }

    fun applyWeeklyRoutineToToday() = viewModelScope.launch(Dispatchers.IO) {
        applyWeeklyRoutine()
    }

    internal suspend fun applyWeeklyRoutine() {
        val date = _selectedDate.value
        if (com.example.myfit.BuildConfig.RUST_HISTORY_ENABLED) {
            RustWorkoutStorage.mutate(getApplication<Application>(), date.toString(), listOf(mapOf("action" to "routine")))
            return
        }
        database.withTransaction {
            val rows = dao.getRoutineForDay(date.dayOfWeek.value)
            val tasks = RustWeeklyRoutine.plan(date.toString(), rows) ?: rows.map { item ->
                WorkoutTask(
                    date = date.toString(),
                    templateId = item.templateId,
                    name = item.name,
                    category = item.category,
                    target = item.target,
                    bodyPart = item.bodyPart,
                    equipment = item.equipment,
                    isUnilateral = item.isUnilateral,
                    logType = item.logType,
                    sets = listOf(WorkoutSet(setNumber = 1, weightOrDuration = "", reps = ""))
                )
            }
            tasks.forEach { dao.insertTask(it) }
        }
    }

    internal suspend fun weeklyRoutineSummary(day: Int): List<Map<String, Any?>> =
        withContext(Dispatchers.IO) {
            val native = if (com.example.myfit.BuildConfig.RUST_HISTORY_ENABLED)
                RustWorkoutStorage.weekly(getApplication<Application>(), day) else null
            native ?: dao.getRoutineForDay(day).sortedBy { it.id }.map {
                mapOf("id" to it.id, "name" to it.name, "target" to it.target)
            }
        }

    internal suspend fun editWeeklyRoutine(action: String, day: Int, id: Long): List<Map<String, Any?>> =
        if (com.example.myfit.BuildConfig.RUST_HISTORY_ENABLED) {
            checkNotNull(RustWorkoutStorage.weekly(getApplication<Application>(), day, action, id))
        } else database.withTransaction {
            require(day in 1..7 && id > 0 && (action == "add" || action == "delete"))
            if (action == "add") {
                val template = checkNotNull(dao.getTemplateById(id)?.takeUnless { it.isDeleted })
                dao.insertRoutineItem(
                    WeeklyRoutineItem(
                        dayOfWeek = day, templateId = template.id, name = template.name,
                        target = template.defaultTarget, category = template.category,
                        bodyPart = template.bodyPart, equipment = template.equipment,
                        isUnilateral = template.isUnilateral, logType = template.logType
                    )
                )
            } else {
                dao.getRoutineForDay(day).firstOrNull { it.id == id }?.let { dao.deleteRoutineItem(it) }
            }
            dao.getRoutineForDay(day).sortedBy { it.id }.map {
                mapOf("id" to it.id, "name" to it.name, "target" to it.target)
            }
        }

    fun addRoutineItem(day: Int, template: ExerciseTemplate) = viewModelScope.launch {
        editWeeklyRoutine("add", day, template.id)
    }

    fun removeRoutineItem(item: WeeklyRoutineItem) = viewModelScope.launch {
        // Legacy editor can carry a placeholder id until the page is reloaded.
        if (item.id > 0) editWeeklyRoutine("delete", item.dayOfWeek, item.id)
    }

    fun updateScheduleConfig(day: Int, type: DayType) = viewModelScope.launch {
        persistSchedule(day, type)
    }

    internal suspend fun persistSchedule(day: Int, type: DayType) {
        if (com.example.myfit.BuildConfig.RUST_HISTORY_ENABLED) {
            RustWorkoutStorage.mutate(getApplication<Application>(), LocalDate.now().toString(),
                listOf(mapOf("action" to "saveSchedule", "day" to day, "type" to type.name)))
        } else dao.insertSchedule(ScheduleConfig(dayOfWeek = day, dayType = type))
        if (day == _selectedDate.value.dayOfWeek.value) _todayScheduleType.value = type
    }

    suspend fun getRoutineForDay(day: Int): List<WeeklyRoutineItem> =
        if (com.example.myfit.BuildConfig.RUST_HISTORY_ENABLED)
            RustLiveQueries.read<WeeklyRoutineItem>(getApplication<Application>(), "routines").filter { it.dayOfWeek == day }
        else dao.getRoutineForDay(day)

    fun exportHistoryToCsv(uri: Uri, context: Context) = viewModelScope.launch(Dispatchers.IO) {
        try {
            val records = if (com.example.myfit.BuildConfig.RUST_HISTORY_ENABLED)
                RustLiveQueries.read<WorkoutTask>(getApplication<Application>(), "completed")
            else dao.getHistoryRecordsSync()
            val csv = com.example.myfit.data.RustSettingsCsv.encode(records) ?: buildString {
                append("Date,Name,Category,Target,IsUnilateral,LogType,Sets_Detail\n")
                records.forEach { task ->
                    val sets = task.sets.joinToString(" | ") {
                        if (task.isUnilateral) "L: ${it.weightOrDuration}x${it.reps} / R: ${it.rightWeight ?: ""}x${it.rightReps ?: ""}"
                        else "${it.weightOrDuration}x${it.reps}"
                    }
                    append("${task.date},${task.name.replace(',', ' ')},${task.category},${task.target},${task.isUnilateral},${task.logType},$sets\n")
                }
            }
            context.contentResolver.openOutputStream(uri)?.use { it.write(csv.toByteArray()) }
            withContext(Dispatchers.Main) {
                Toast.makeText(
                    context,
                    context.getString(R.string.msg_backup_success),
                    Toast.LENGTH_SHORT
                ).show()
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                Toast.makeText(
                    context,
                    "Export failed: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    fun backupDatabase(uri: Uri, context: Context) = viewModelScope.launch(Dispatchers.IO) {
        try {
            RustWorkoutStorage.withDatabaseFile {
                if (com.example.myfit.BuildConfig.RUST_HISTORY_ENABLED) {
                    RustWorkoutStorage.initialize(context)
                    RustWorkoutStorage.snapshot(context) { input ->
                        checkNotNull(context.contentResolver.openOutputStream(uri))
                            .use { output -> input.copyTo(output); output.flush() }
                    }
                } else {
                    val db = database.openHelper.writableDatabase
                    db.query("PRAGMA wal_checkpoint(TRUNCATE)").use { cursor ->
                        check(cursor.moveToFirst() && cursor.getInt(0) == 0) { "Database checkpoint busy" }
                    }
                    val dbPath = context.getDatabasePath(AppDatabase.FILE_NAME)
                    checkNotNull(context.contentResolver.openOutputStream(uri))
                        .use { output -> FileInputStream(dbPath).use { it.copyTo(output); output.flush() } }
                }
            }
            withContext(Dispatchers.Main) {
                Toast.makeText(context, context.getString(R.string.msg_backup_success), Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                Toast.makeText(
                    context,
                    context.getString(R.string.msg_backup_failed, e.message),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    fun restoreDatabase(uri: Uri, context: Context, launchOptions: Intent? = null) = viewModelScope.launch(Dispatchers.IO) {
        var liveClosed = false
        try {
            RustWorkoutStorage.withDatabaseFile {
                val prepared = DatabaseBackups.prepareRestore(context, uri)
                try {
                    currentCoroutineContext().ensureActive()
                    val path = context.getDatabasePath(AppDatabase.FILE_NAME)
                    if (com.example.myfit.BuildConfig.RUST_HISTORY_ENABLED) {
                        RustWorkoutStorage.checkpoint(context)
                    } else database.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(TRUNCATE)").use { cursor ->
                        check(cursor.moveToFirst() && cursor.getInt(0) == 0) { "Database checkpoint busy" }
                    }
                    withContext(NonCancellable) {
                        stopTimer(context)
                        if (!com.example.myfit.BuildConfig.RUST_HISTORY_ENABLED) database.close()
                        liveClosed = true
                        val wal = File(path.path + "-wal")
                        check(!wal.exists() || wal.length() == 0L) { "Database still has pending writes" }
                        for (suffix in listOf("-wal", "-shm")) {
                            val sidecar = File(path.path + suffix)
                            check(!sidecar.exists() || sidecar.delete()) { "Could not remove database sidecar" }
                        }
                        DatabaseBackups.install(prepared, path)
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, context.getString(R.string.msg_restore_success), Toast.LENGTH_SHORT).show()
                            triggerRestart(context, launchOptions)
                        }
                    }
                } finally { prepared.delete() }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, context.getString(R.string.msg_restore_failed, error.message), Toast.LENGTH_LONG).show()
                // A failed atomic rename leaves the original file in place. Reopen it in a fresh process.
                if (liveClosed) triggerRestart(context, launchOptions)
            }
        }
    }

    private fun triggerRestart(context: Context, launchOptions: Intent? = null) {
        val launch = checkNotNull(context.packageManager.getLaunchIntentForPackage(context.packageName))
        val restart = Intent.makeRestartActivityTask(checkNotNull(launch.component))
        // Retain renderer options explicitly supplied when this Flutter host was launched.
        for (key in listOf("enable-software-rendering", "enable-impeller")) {
            if (launchOptions?.hasExtra(key) == true)
                restart.putExtra(key, launchOptions.getBooleanExtra(key, false))
        }
        context.startActivity(restart)
        exitProcess(0)
    }

    fun getWeightChartData(granularity: ChartGranularity): Flow<List<ChartDataPoint>> =
        weightHistory.map {
            groupAndFormatData(
                it.map { Pair(LocalDate.parse(it.date), it.weight) },
                granularity
            )
        }

    fun getCardioTotalChartData(granularity: ChartGranularity): Flow<List<ChartDataPoint>> =
        historyRecords.map { tasks ->
            groupAndFormatData(tasks.filter { it.category == "CARDIO" }
                .groupBy { LocalDate.parse(it.date) }.map { (d, list) ->
                Pair(
                    d,
                    list.sumOf { t ->
                        if (t.sets.isNotEmpty()) t.sets.sumOf { parseDuration(it.weightOrDuration).toDouble() } else parseDuration(
                            t.target
                        ).toDouble()
                    }.toFloat()
                )
            }, granularity)
        }

    fun getExerciseNamesByCategory(category: String): Flow<List<String>> = historyRecords.map {
        it.filter { it.category == category }.map { it.name }.distinct().sorted()
    }

    fun getSingleExerciseChartData(
        name: String,
        mode: Int,
        granularity: ChartGranularity
    ): Flow<List<ChartDataPoint>> = historyRecords.map { tasks ->
        groupAndFormatData(tasks.filter { it.name == name }.groupBy { LocalDate.parse(it.date) }
            .map { (d, list) ->
                val values = list.flatMap { t ->
                    t.sets.ifEmpty {
                        listOf(
                            WorkoutSet(
                                1,
                                t.actualWeight.ifEmpty { t.target },
                                t.target
                            )
                        )
                    }
                }
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

    private fun parseValue(input: String): Float =
        Regex("[0-9]+(\\.[0-9]+)?").find(input)?.value?.toFloatOrNull() ?: 0f

    private fun parseDuration(input: String): Float {
        val lower = input.lowercase();
        val num = parseValue(lower); return when {
            lower.contains("h") -> num * 60; lower.contains("s") && !lower.contains("m") -> num / 60; else -> num
        }
    }

    fun importWeeklyRoutine(context: Context, csv: String) = viewModelScope.launch(Dispatchers.IO) {
        try {
            importWeeklyRoutineNow(context, csv)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, context.getString(R.string.import_error), Toast.LENGTH_LONG).show()
            }
        }
    }

    internal suspend fun importWeeklyRoutineNow(context: Context, csv: String) {
        if (com.example.myfit.BuildConfig.RUST_HISTORY_ENABLED) {
            RustWorkoutStorage.mutate(context, LocalDate.now().toString(),
                listOf(mapOf("action" to "importRoutine", "csv" to csv)))
            withContext(Dispatchers.Main) {
                Toast.makeText(context, context.getString(R.string.import_success), Toast.LENGTH_SHORT).show()
            }
            return
        }
        try {
            data class PendingItem(
                val day: Int,
                val name: String,
                val category: String,
                val target: String,
                val bodyPart: String,
                val equipment: String,
                val isUni: Boolean,
                val logType: Int,
                val instruction: String
            )

            val pending = mutableListOf<PendingItem>()
            val daysToOverwrite = mutableSetOf<Int>()

            csv.split("\n").forEachIndexed { i, line ->
                if (i == 0 || line.isBlank()) return@forEachIndexed
                val parts = line.split(Regex(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)"))
                    .map { it.trim().removeSurrounding("\"") }
                if (parts.size >= 4) {
                    val day = parts[0].toIntOrNull() ?: 1
                    val category = parts[2]
                    val inferred = when {
                        category.equals(
                            "CARDIO",
                            true
                        ) || category.contains("有氧") -> 1; category.equals(
                            "CORE",
                            true
                        ) || category.contains("核心") -> 2; else -> 0
                    }
                    daysToOverwrite.add(day)
                    pending.add(
                        PendingItem(
                            day,
                            parts[1],
                            category,
                            parts[3],
                            parts.getOrNull(4)?.ifBlank { "part_other" } ?: "part_other",
                            parts.getOrNull(5)?.ifBlank { "equip_other" } ?: "equip_other",
                            parts.getOrNull(6)?.toBoolean() ?: false,
                            parts.getOrNull(7)?.toIntOrNull() ?: inferred,
                            parts.getOrNull(8)?.replace("\"", "") ?: ""))
                }
            }
            if (pending.isEmpty()) throw Exception("Invalid CSV")
            daysToOverwrite.forEach { day ->
                dao.getRoutineForDaySync(day).forEach { dao.deleteRoutineItem(it) }
            }
            pending.forEach { item ->
                val tid = dao.getTemplateByName(item.name)?.id ?: dao.insertTemplate(
                    ExerciseTemplate(
                        name = item.name,
                        category = item.category,
                        defaultTarget = item.target,
                        bodyPart = item.bodyPart,
                        equipment = item.equipment,
                        isUnilateral = item.isUni,
                        logType = item.logType,
                        instruction = item.instruction
                    )
                )
                dao.insertRoutineItem(
                    WeeklyRoutineItem(
                        dayOfWeek = item.day,
                        templateId = tid,
                        name = item.name,
                        target = item.target,
                        category = item.category,
                        bodyPart = item.bodyPart,
                        equipment = item.equipment,
                        isUnilateral = item.isUni,
                        logType = item.logType
                    )
                )
            }
            withContext(Dispatchers.Main) {
                Toast.makeText(
                    context,
                    "${context.getString(R.string.import_success)}: ${pending.size}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                Toast.makeText(
                    context,
                    "${context.getString(R.string.import_error)}\n${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
            throw e
        }
    }

    fun reloadStandardExercises(context: Context, languageCode: String) =
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val fileName = when (languageCode) {
                    "en" -> "exercises_en.json"; "ja" -> "exercises_ja.json"; "de" -> "exercises_de.json"; "es" -> "exercises_es.json"; else -> "default_exercises.json"
                }
                val json = try {
                    context.assets.open(fileName).bufferedReader().use { it.readText() }
                } catch (e: Exception) {
                    context.assets.open("default_exercises.json").bufferedReader()
                        .use { it.readText() }
                }
                val raw: List<ExerciseTemplate> =
                    Gson().fromJson(json, object : TypeToken<List<ExerciseTemplate>>() {}.type)
                if (com.example.myfit.BuildConfig.RUST_HISTORY_ENABLED) {
                    val active = RustLiveQueries.read<ExerciseTemplate>(context, "templates").associateBy { it.name }
                    val changes = raw.map { t ->
                        mapOf("action" to "saveTemplate", "id" to (active[t.name]?.id ?: 0L), "fields" to mapOf(
                            "name" to t.name, "target" to t.defaultTarget, "category" to t.category,
                            "bodyPart" to t.bodyPart, "equipment" to t.equipment, "instruction" to t.instruction,
                            "isUnilateral" to t.isUnilateral, "logType" to t.logType,
                            "imageUri" to t.imageUri, "animationUri" to t.animationUri))
                    }
                    RustWorkoutStorage.mutate(context, LocalDate.now().toString(), changes)
                } else {
                    val nameIdMap = dao.getAllTemplatesSync().associate { it.name to it.id }
                    dao.insertTemplates(raw.map { it.copy(id = nameIdMap[it.name] ?: 0L) })
                }
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        context,
                        context.getString(R.string.import_success),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        context,
                        "${context.getString(R.string.import_error)}: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }

    fun getLogTypeForExercise(name: String): Flow<Int> = historyRecords.map { list ->
        list.find { it.name == name }?.logType ?: LogType.WEIGHT_REPS.value
    }

    private fun compressImageToBase64(context: Context, uri: Uri): String? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val bm = BitmapFactory.decodeStream(stream) ?: return null
                val scale = 1024f / maxOf(bm.width, bm.height).coerceAtLeast(1)
                val scaled = if (scale < 1) Bitmap.createScaledBitmap(
                    bm,
                    (bm.width * scale).toInt(),
                    (bm.height * scale).toInt(),
                    true
                ) else bm
                val out = ByteArrayOutputStream()
                scaled.compress(Bitmap.CompressFormat.JPEG, 70, out)
                Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
            }
        } catch (e: Exception) {
            null
        }
    }

    private val _aiResponse = MutableStateFlow<String?>(null)
    val aiResponse = _aiResponse.asStateFlow()
    private val _aiIsLoading = MutableStateFlow(false)
    val aiIsLoading = _aiIsLoading.asStateFlow()

    private val _aiStreamingEnabled = MutableStateFlow(true)
    val aiStreamingEnabled = _aiStreamingEnabled.asStateFlow()
    private val _aiStreamState = MutableStateFlow(AiStreamSnapshot())
    val aiStreamState = _aiStreamState.asStateFlow()
    private val _aiCanImport = MutableStateFlow(false)
    val aiCanImport = _aiCanImport.asStateFlow()
    private var aiRequestJob: Job? = null
    private val aiRequestGeneration = java.util.concurrent.atomic.AtomicLong()

    @Synchronized fun setAiStreamingEnabled(enabled: Boolean) { if (!_aiIsLoading.value) _aiStreamingEnabled.value = enabled }
    @Synchronized fun cancelAiGeneration() {
        if (!_aiIsLoading.value) return
        aiRequestGeneration.incrementAndGet()
        aiRequestJob?.cancel(); aiRequestJob = null
        _aiIsLoading.value = false; _aiCanImport.value = false
        _aiStreamState.value = _aiStreamState.value.copy(status = "cancelled")
    }

    private suspend fun runAiCompletion(settings: AppSetting, messages: List<ChatMessage>,
        requestId: String = java.util.UUID.randomUUID().toString(), onUpdate: (AiStreamSnapshot) -> Unit = {}): String {
        val api = getAiApi(settings)
        if (!_aiStreamingEnabled.value) {
            val response = api.generateChatCompletion("Bearer ${settings.aiApiKey}", ChatRequest(settings.aiModel, messages))
            val choice = response.choices.firstOrNull() ?: throw AiStreamFailure("empty_response")
            if (choice.finishReason != null && choice.finishReason != "stop") throw AiStreamFailure("incomplete")
            return choice.message.content?.toString()?.takeIf { it.isNotBlank() }
                ?: throw AiStreamFailure("empty_response")
        }
        return AiStreamRunner.run(api.streamChatCompletion("Bearer ${settings.aiApiKey}",
            StreamingChatRequest(settings.aiModel, messages)),
            AiStreamCapabilities(AiStreamProtocol.CHAT_COMPLETIONS, streaming = true), requestId = requestId, onUpdate = onUpdate)
    }

    /** Coordinates prompts, generation state and final persistence. */
    @Synchronized private fun launchCoachRequest(settings: AppSetting, goal: String, buildMessages: suspend () -> List<ChatMessage>) {
        if (_aiIsLoading.value) return
        val generation = aiRequestGeneration.incrementAndGet()
        val id = java.util.UUID.randomUUID().toString()
        _aiIsLoading.value = true; _aiCanImport.value = false; _aiResponse.value = null
        _aiStreamState.value = AiStreamSnapshot(requestId = id, status = if (_aiStreamingEnabled.value) "connecting" else "answering")
        aiRequestJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val content = runAiCompletion(settings, buildMessages(), id) { snapshot ->
                    synchronized(this@MainViewModel) {
                        if (aiRequestGeneration.get() == generation) {
                            _aiStreamState.value = snapshot
                            _aiResponse.value = snapshot.answer.takeIf { it.isNotEmpty() }
                        }
                    }
                }
                currentCoroutineContext().ensureActive()
                synchronized(this@MainViewModel) {
                    if (aiRequestGeneration.get() != generation) return@launch
                    _aiResponse.value = content
                    _aiStreamState.value = _aiStreamState.value.copy(status = "completed", answer = content)
                    _aiCanImport.value = true
                }
                persistAiRecord(AiChatRecord(timestamp = System.currentTimeMillis(), role = "assistant",
                    content = content, userGoal = goal, modelUsed = settings.aiModel))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                synchronized(this@MainViewModel) { if (aiRequestGeneration.get() == generation) {
                    val code = (e as? AiStreamFailure)?.code ?: "request_failed"
                    val incomplete = code in setOf("incomplete", "truncated_sse_event", "stream_ended_without_terminal_event")
                    _aiCanImport.value = false
                    _aiStreamState.value = _aiStreamState.value.copy(status = if (incomplete) "incomplete" else "failed", errorCode = code)
                } }
            } finally { synchronized(this@MainViewModel) { if (aiRequestGeneration.get() == generation) _aiIsLoading.value = false } }
        }
    }

    var currentTrainingGoal by mutableStateOf("")
    var historyWeeks by mutableIntStateOf(3)
    var selectedFocus by mutableStateOf(setOf("COMPREHENSIVE"))
    var selectedScene by mutableStateOf(setOf("GYM"))
    var selectedInjuries by mutableStateOf(setOf<String>())

    sealed class ConnectionState {
        object Idle : ConnectionState();
        object Testing : ConnectionState();
        object Success : ConnectionState();
        data class Error(val message: String) : ConnectionState()
    }

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Idle)
    val connectionState = _connectionState.asStateFlow()
    val aiChatHistory: Flow<List<AiChatRecord>> = if (com.example.myfit.BuildConfig.RUST_HISTORY_ENABLED)
        RustLiveQueries.observe<AiChatRecord>(getApplication<Application>(), "ai_chat_history",
            RustWorkoutStorage.aiRevision, "aiHistory")
        else dao.getAllAiChatRecords()

    private fun getAiApi(settings: AppSetting): AiService {
        val client = OkHttpClient.Builder().retryOnConnectionFailure(false).connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS).build()
        val rawUrl = settings.aiBaseUrl.ifBlank {
            availableProviders.find { it.name == settings.aiProvider }?.defaultBaseUrl
                ?: "https://api.openai.com/"
        }
        val finalUrl =
            "${rawUrl.trim().replace(Regex("v1/?$", RegexOption.IGNORE_CASE), "").trimEnd('/')}/"
        return Retrofit.Builder().baseUrl(finalUrl).client(client)
            .addConverterFactory(GsonConverterFactory.create()).build()
            .create(AiService::class.java)
    }

    fun testAiConnection(provider: String, apiKey: String, model: String, baseUrl: String) {
        if (apiKey.isBlank()) {
            _connectionState.value =
                ConnectionState.Error(getApplication<Application>().getString(R.string.msg_config_missing)); return
        }
        viewModelScope.launch(Dispatchers.IO) {
            _connectionState.value = ConnectionState.Testing
            try {
                val api = getAiApi(
                    AppSetting(
                        aiProvider = provider,
                        aiApiKey = apiKey,
                        aiModel = model,
                        aiBaseUrl = baseUrl
                    )
                )
                val res = api.generateChatCompletion(
                    "Bearer $apiKey",
                    ChatRequest(
                        model = model,
                        messages = listOf(ChatMessage("user", "Hello")),
                        temperature = 0.7
                    )
                )
                _connectionState.value =
                    if (res.choices.isNotEmpty()) ConnectionState.Success else ConnectionState.Error(
                        "Empty response"
                    )
            } catch (e: Exception) {
                _connectionState.value = ConnectionState.Error(
                    getApplication<Application>().getString(
                        R.string.msg_connection_failed,
                        e.message
                    )
                )
            }
        }
    }

    fun saveAiConfig(provider: String, apiKey: String, model: String, baseUrl: String) {
        viewModelScope.launch(Dispatchers.IO) { persistAiConfig(provider, apiKey, model, baseUrl) }
    }

    internal suspend fun persistAiConfig(provider: String, apiKey: String, model: String, baseUrl: String) {
        if (com.example.myfit.BuildConfig.RUST_HISTORY_ENABLED) {
            val fields = mapOf("provider" to provider, "apiKey" to apiKey, "model" to model, "baseUrl" to baseUrl)
            RustWorkoutStorage.mutate(getApplication<Application>(), LocalDate.now().toString(),
                listOf(mapOf("action" to "saveModel", "fields" to fields)))
            return
        }
        val clean = baseUrl.trim().replace(Regex("v1/?$", RegexOption.IGNORE_CASE), "").trimEnd('/')
        database.withTransaction {
            val settings = dao.getAppSettings().first() ?: AppSetting()
            dao.saveAppSettings(settings.copy(aiProvider = provider, aiApiKey = apiKey, aiModel = model, aiBaseUrl = clean))
            dao.saveAiProviderConfig(AiProviderConfig(provider, apiKey, model, clean))
        }
    }

    fun toggleAiCache(enabled: Boolean) = viewModelScope.launch {
        persistSetting("useLocalAiCache", enabled)
    }

    fun updateMaxHistoryLimit(limit: Int) = viewModelScope.launch {
        persistSetting("maxHistoryLimit", limit)
    }

    fun generateWeeklyPlan(context: Context) {
        val settings = userProfile.value
        if (settings.aiApiKey.isBlank()) {
            Toast.makeText(context, context.getString(R.string.msg_config_missing), Toast.LENGTH_SHORT).show(); return
        }
        // Snapshot mutable form values before the request leaves the UI thread.
        val goal = currentTrainingGoal; val weeks = historyWeeks
        val focus = selectedFocus; val scene = selectedScene; val injuries = selectedInjuries
        launchCoachRequest(settings, goal) {
            val cutoff = LocalDate.now().minusWeeks(weeks.toLong()).toString()
            val app = getApplication<Application>()
            val native = com.example.myfit.BuildConfig.RUST_HISTORY_ENABLED
            val weight = if (native) RustLiveQueries.read<WeightRecord>(app, "latestWeight").firstOrNull() else dao.getLatestWeightSync()
            val schedule = if (native) RustLiveQueries.read<ScheduleConfig>(app, "schedules") else dao.getAllSchedulesSync()
            val history = if (native) RustLiveQueries.read<WorkoutTask>(app, "completed") else dao.getHistoryRecordsSync()
            PromptManager.buildWeeklyPlanPrompt(context, settings, weight,
                readAiMemory()?.longTermContext, schedule,
                history.filter { it.date >= cutoff }, goal, weeks, focus, scene, injuries)
        }
    }

    private suspend fun persistAiRecord(record: AiChatRecord) {
        if (com.example.myfit.BuildConfig.RUST_HISTORY_ENABLED) {
            val fields = mapOf("timestamp" to record.timestamp, "role" to record.role,
                "content" to record.content, "userGoal" to record.userGoal, "modelUsed" to record.modelUsed)
            RustWorkoutStorage.mutate(getApplication<Application>(), LocalDate.now().toString(),
                listOf(mapOf("action" to "saveAiRecord", "fields" to fields)))
        } else dao.insertAiChatRecord(record)
    }

    private suspend fun removeAiRecord(id: Long) {
        if (com.example.myfit.BuildConfig.RUST_HISTORY_ENABLED) {
            RustWorkoutStorage.mutate(getApplication<Application>(), LocalDate.now().toString(),
                listOf(mapOf("action" to "deleteAiRecord", "id" to id)))
        } else dao.deleteAiChatRecord(id)
    }

    private suspend fun recentAiHistory(limit: Int): List<AiChatRecord> =
        if (com.example.myfit.BuildConfig.RUST_HISTORY_ENABLED)
            RustLiveQueries.read<AiChatRecord>(getApplication<Application>(), "aiRecent", limit)
        else dao.getRecentAiChatRecords(limit)

    private suspend fun readAiMemory(): AiMemory? =
        if (com.example.myfit.BuildConfig.RUST_HISTORY_ENABLED)
            RustLiveQueries.read<AiMemory>(getApplication<Application>(), "aiMemory").firstOrNull()
        else dao.getAiMemorySync()

    private suspend fun persistAiMemory(memory: AiMemory) {
        if (com.example.myfit.BuildConfig.RUST_HISTORY_ENABLED) {
            val fields = mapOf("longTermContext" to memory.longTermContext, "lastSyncTime" to memory.lastSyncTime)
            RustWorkoutStorage.mutate(getApplication<Application>(), LocalDate.now().toString(),
                listOf(mapOf("action" to "saveAiMemory", "fields" to fields)))
        } else dao.saveAiMemory(memory)
    }

    fun saveAiChatRecord(
        content: String,
        role: String,
        userGoal: String? = null,
        model: String? = null
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            persistAiRecord(
                AiChatRecord(
                    timestamp = System.currentTimeMillis(),
                    role = role,
                    content = content,
                    userGoal = userGoal,
                    modelUsed = model
                )
            )
        }
    }

    fun deleteAiChatRecord(id: Long) =
        viewModelScope.launch(Dispatchers.IO) { removeAiRecord(id) }

    internal fun requestAiHistoryDeletion(id: Long) =
        viewModelScope.async(Dispatchers.IO) { removeAiRecord(id) }

    @Synchronized fun loadAiResponseFromHistory(content: String) {
        cancelAiGeneration()
        _aiResponse.value = content; _aiCanImport.value = content.isNotBlank()
        _aiStreamState.value = AiStreamSnapshot(status = "completed", answer = content)
    }

    fun analyzeImage(context: Context, uri: Uri) {
        val settings = userProfile.value
        if (settings.aiApiKey.isBlank()) {
            Toast.makeText(context, context.getString(R.string.msg_config_missing), Toast.LENGTH_SHORT).show(); return
        }
        launchCoachRequest(settings, "[Vision] Analysis") {
            val b64 = compressImageToBase64(context, uri) ?: throw AiStreamFailure("image_error")
            PromptManager.buildImageAnalysisPrompt(context, b64)
        }
    }

    fun sendFreeChat(context: Context, query: String, imageUri: Uri? = null) {
        val settings = userProfile.value
        if (settings.aiApiKey.isBlank()) {
            Toast.makeText(context, context.getString(R.string.msg_config_missing), Toast.LENGTH_SHORT).show(); return
        }
        launchCoachRequest(settings, "Free Chat: $query") {
            val history = if (settings.useLocalAiCache) recentAiHistory(settings.maxHistoryLimit) else emptyList()
            val memory = if (settings.useLocalAiCache) readAiMemory()?.longTermContext else null
            if (imageUri != null) {
                val b64 = compressImageToBase64(context, imageUri) ?: throw AiStreamFailure("image_error")
                PromptManager.buildMultimodalChatPrompt(context, query, b64)
            } else PromptManager.buildFreeChatPrompt(context, query, history, memory)
        }
    }

    fun summarizeAndSaveMemory(context: Context) {
        val settings = userProfile.value
        if (!settings.useLocalAiCache || settings.aiApiKey.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val history = recentAiHistory(20)
                if (history.isEmpty()) return@launch
                val messages = PromptManager.buildMemorySummaryPrompt(context, history)
                val summary = runAiCompletion(settings, messages)
                if (!summary.isNullOrBlank()) persistAiMemory(
                    AiMemory(
                        0,
                        summary,
                        System.currentTimeMillis()
                    )
                )
            } catch (e: CancellationException) { throw e
            } catch (_: Exception) { /* Never persist partial memory. */ }
        }
    }

    private val _isGeneratingCsv = MutableStateFlow(false)
    val isGeneratingCsv = _isGeneratingCsv.asStateFlow()

    fun generateCsvFromAdvice(
        context: Context,
        advice: String,
        userFeedback: String,
        onResult: (String) -> Unit
    ) {
        val settings = userProfile.value
        if (settings.aiApiKey.isBlank() || _aiIsLoading.value || !_aiCanImport.value || _isGeneratingCsv.value) return
        _isGeneratingCsv.value = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val messages = PromptManager.buildCsvPlanPrompt(context, advice, userFeedback)
                val content = runAiCompletion(settings, messages)
                val clean = content.replace(
                    "```csv",
                    ""
                ).replace("```", "").trim()
                withContext(Dispatchers.Main) { onResult(clean) }
            } catch (e: CancellationException) { throw e
            } catch (_: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        context,
                        context.getString(R.string.coach_stream_failed),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } finally {
                _isGeneratingCsv.value = false
            }
        }
    }
}
