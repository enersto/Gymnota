package com.example.myfit.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val dao: WorkoutDao = AppDatabase.getDatabase(application).workoutDao()

    private val _selectedDate = MutableStateFlow(LocalDate.now())
    val selectedDate: StateFlow<LocalDate> = _selectedDate.asStateFlow()

    // --- 主题与语言设置 (修复：多语言下主题切换失效问题) ---
    // 逻辑：监听数据库设置 -> 转换为 AppTheme 对象
    val currentTheme = dao.getAppSettings()
        .map { it?.themeId ?: 0 }
        .map { AppTheme.fromId(it) } // 依赖 AppTheme.fromId 的正确实现
        .stateIn(viewModelScope, SharingStarted.Lazily, AppTheme.DARK) // 默认使用 DARK

    val currentLanguage = dao.getAppSettings()
        .map { it?.languageCode ?: "zh" }
        .stateIn(viewModelScope, SharingStarted.Lazily, "zh")

    // --- 日程类型 ---
    private val _todayScheduleType = MutableStateFlow(DayType.CORE) // 默认值
    val todayScheduleType = combine(_selectedDate, dao.getAllSchedules()) { date, schedules ->
        val dayOfWeek = date.dayOfWeek.value
        val type = schedules.find { it.dayOfWeek == dayOfWeek }?.dayType ?: DayType.CORE
        _todayScheduleType.value = type
        type
    }.stateIn(viewModelScope, SharingStarted.Lazily, DayType.CORE)

    // --- 体重提醒 ---
    private val _showWeightAlert = MutableStateFlow(false)
    val showWeightAlert = dao.getLatestWeight().map { record ->
        val shouldShow = if (record == null) true else ChronoUnit.DAYS.between(LocalDate.parse(record.date), LocalDate.now()) > 7
        _showWeightAlert.value = shouldShow
        shouldShow
    }.stateIn(viewModelScope, SharingStarted.Lazily, false)

    // --- 数据流 ---
    val todayTasks: Flow<List<WorkoutTask>> = _selectedDate.flatMapLatest { date ->
        dao.getTasksForDate(date.toString())
    }

    val allTemplates: Flow<List<ExerciseTemplate>> = dao.getAllTemplates()
    val historyRecords: Flow<List<WorkoutTask>> = dao.getAllHistoryTasks()
    val weightHistory: Flow<List<WeightRecord>> = dao.getAllWeightRecords()

    // --- 计时器状态 ---
    data class TimerState(
        val taskId: Long = -1L,
        val setIndex: Int = -1,
        val totalSeconds: Int = 0,
        val remainingSeconds: Int = 0,
        val isRunning: Boolean = false,
        val isPaused: Boolean = false
    )

    private val _timerState = MutableStateFlow(TimerState())
    val timerState = _timerState.asStateFlow()
    private var timerJob: Job? = null

    init {
        NotificationHelper.createNotificationChannel(application)
    }

    // --- 计时器逻辑 ---
    fun startTimer(context: Context, taskId: Long, setIndex: Int, durationMinutes: Int) {
        val current = _timerState.value
        val initialSeconds = if (current.taskId == taskId && current.setIndex == setIndex && current.isPaused) {
            current.remainingSeconds
        } else {
            if (durationMinutes <= 0) return
            durationMinutes * 60
        }

        _timerState.value = TimerState(taskId, setIndex, durationMinutes * 60, initialSeconds, true, false)

        timerJob?.cancel()
        timerJob = viewModelScope.launch(Dispatchers.Default) {
            val task = dao.getTaskById(taskId)
            // 修复：从资源获取字符串，支持多语言占位符
            val titleTemplate = getApplication<Application>().getString(R.string.notify_training_title)
            val taskName = task?.name ?: "Training"
            val notificationTitle = String.format(titleTemplate, taskName)

            while (_timerState.value.remainingSeconds > 0 && _timerState.value.isRunning) {
                delay(1000)
                _timerState.update { it.copy(remainingSeconds = it.remainingSeconds - 1) }
                try {
                    val s = _timerState.value.remainingSeconds
                    val timeStr = String.format("%02d:%02d", s / 60, s % 60)
                    withContext(Dispatchers.Main) {
                        NotificationHelper.updateTimerNotification(context, notificationTitle, timeStr)
                    }
                } catch (e: Exception) { e.printStackTrace() }
            }
            if (_timerState.value.remainingSeconds <= 0 && _timerState.value.isRunning) {
                withContext(Dispatchers.Main) {
                    try { NotificationHelper.cancelNotification(context) } catch (e: Exception) { e.printStackTrace() }
                    onTimerFinished(taskId, setIndex, durationMinutes)
                }
            }
        }
    }

    fun pauseTimer(context: Context) {
        _timerState.update { it.copy(isRunning = false, isPaused = true) }
        timerJob?.cancel()
        try {
            val pausedTitle = getApplication<Application>().getString(R.string.notify_paused_title)
            val resumeText = getApplication<Application>().getString(R.string.notify_click_resume)
            NotificationHelper.updateTimerNotification(context, pausedTitle, resumeText)
        } catch (e: Exception) { e.printStackTrace() }
    }

    fun stopTimer(context: Context) {
        _timerState.value = TimerState()
        timerJob?.cancel()
        try { NotificationHelper.cancelNotification(context) } catch (e: Exception) { e.printStackTrace() }
    }

    private suspend fun onTimerFinished(taskId: Long, setIndex: Int, durationMinutes: Int) {
        _timerState.value = TimerState()
        val task = dao.getTaskById(taskId) ?: return
        val newSets = task.sets.toMutableList()
        if (setIndex < newSets.size) {
            newSets[setIndex] = newSets[setIndex].copy(
                weightOrDuration = "${durationMinutes}min",
                reps = "Done"
            )
        }
        var updatedTask = task.copy(sets = newSets)
        val allSetsDone = newSets.all { it.weightOrDuration.isNotBlank() }

        if (allSetsDone) {
            updatedTask = updatedTask.copy(isCompleted = true)
        }
        dao.updateTask(updatedTask)

        withContext(Dispatchers.Main) {
            val app = getApplication<Application>()
            val msg = if (allSetsDone) {
                app.getString(R.string.toast_task_auto_completed)
            } else {
                app.getString(R.string.toast_set_completed)
            }
            Toast.makeText(app, msg, Toast.LENGTH_SHORT).show()
        }
    }

    // --- 核心业务逻辑 ---

    fun deleteTemplate(id: Long) = viewModelScope.launch {
        val t = dao.getTemplateById(id)
        if (t != null) {
            dao.deleteTemplate(t)
        }
    }

    fun saveTemplate(t: ExerciseTemplate) = viewModelScope.launch {
        if (t.id == 0L) dao.insertTemplate(t) else dao.updateTemplate(t)
    }

    fun addTaskFromTemplate(t: ExerciseTemplate) = viewModelScope.launch {
        dao.insertTask(WorkoutTask(
            date = _selectedDate.value.toString(),
            templateId = t.id,
            name = t.name,
            category = t.category,
            target = t.defaultTarget,
            bodyPart = t.bodyPart,
            equipment = t.equipment,
            sets = listOf(WorkoutSet(1, "", ""))
        ))
    }

    fun updateTask(t: WorkoutTask) = viewModelScope.launch { dao.updateTask(t) }
    fun removeTask(t: WorkoutTask) = viewModelScope.launch { dao.deleteTask(t) }

    fun applyWeeklyRoutineToToday() = viewModelScope.launch(Dispatchers.IO) {
        val dateStr = _selectedDate.value.toString()
        val dayOfWeek = _selectedDate.value.dayOfWeek.value
        val routineItems = dao.getRoutineForDaySync(dayOfWeek)
        routineItems.forEach { item ->
            dao.insertTask(WorkoutTask(
                date = dateStr,
                templateId = item.templateId,
                name = item.name,
                category = item.category,
                target = item.target,
                bodyPart = item.bodyPart,
                equipment = item.equipment,
                sets = listOf(WorkoutSet(1, "", ""))
            ))
        }
    }

    fun logWeight(w: Float) = viewModelScope.launch {
        dao.insertWeight(WeightRecord(date = LocalDate.now().toString(), weight = w))
        _showWeightAlert.value = false
    }

    // --- 日程与计划支持 ---

    fun addRoutineItem(day: Int, template: ExerciseTemplate) = viewModelScope.launch {
        dao.insertRoutineItem(WeeklyRoutineItem(
            dayOfWeek = day,
            templateId = template.id,
            name = template.name,
            target = template.defaultTarget,
            category = template.category,
            bodyPart = template.bodyPart,
            equipment = template.equipment
        ))
    }

    fun removeRoutineItem(item: WeeklyRoutineItem) = viewModelScope.launch {
        dao.deleteRoutineItem(item)
    }

    fun updateScheduleConfig(day: Int, type: DayType) = viewModelScope.launch {
        dao.insertSchedule(ScheduleConfig(dayOfWeek = day, dayType = type))
        if (day == _selectedDate.value.dayOfWeek.value) {
            _todayScheduleType.value = type
        }
    }

    suspend fun getRoutineForDay(day: Int): List<WeeklyRoutineItem> {
        return dao.getRoutineForDay(day)
    }

    // --- 图表数据辅助方法 ---

    fun getWeightChartData(granularity: ChartGranularity): Flow<List<ChartDataPoint>> {
        return weightHistory.map { records ->
            val raw = records.map { Pair(LocalDate.parse(it.date), it.weight) }
            val grouped = when (granularity) {
                ChartGranularity.DAILY -> raw.groupBy { it.first }
                ChartGranularity.MONTHLY -> raw.groupBy { it.first.withDayOfMonth(1) }
            }
            grouped.map { (date, list) ->
                ChartDataPoint(date, list.map { it.second }.average().toFloat(), date.format(DateTimeFormatter.ofPattern("MM/dd")))
            }.sortedBy { it.date }
        }
    }

    fun getCardioTotalChartData(granularity: ChartGranularity): Flow<List<ChartDataPoint>> {
        return historyRecords.map { tasks ->
            val cardio = tasks.filter { it.category == "CARDIO" }
            val raw = cardio.groupBy { LocalDate.parse(it.date) }.map { (date, tList) ->
                val sum = tList.sumOf { t ->
                    // 优先使用 sets 中的数据，如果 sets 为空则解析 target
                    if (t.sets.isNotEmpty()) {
                        t.sets.sumOf { s -> parseDuration(s.weightOrDuration).toDouble() }
                    } else {
                        parseDuration(t.target).toDouble()
                    }
                }.toFloat()
                Pair(date, sum)
            }
            val grouped = when (granularity) {
                ChartGranularity.DAILY -> raw.groupBy { it.first }
                ChartGranularity.MONTHLY -> raw.groupBy { it.first.withDayOfMonth(1) }
            }
            grouped.map { (date, list) ->
                ChartDataPoint(date, list.sumOf { it.second.toDouble() }.toFloat(), date.format(DateTimeFormatter.ofPattern("MM/dd")))
            }.sortedBy { it.date }
        }
    }

    fun getExerciseNamesByCategory(category: String): Flow<List<String>> {
        return historyRecords.map { tasks ->
            tasks.filter { it.category == category }.map { it.name }.distinct().sorted()
        }
    }

    fun getSingleExerciseChartData(name: String, mode: Int, granularity: ChartGranularity): Flow<List<ChartDataPoint>> {
        return historyRecords.map { tasks ->
            val targetTasks = tasks.filter { it.name == name }
            val raw = targetTasks.groupBy { LocalDate.parse(it.date) }.map { (date, tList) ->
                val values = tList.flatMap { t ->
                    if (t.sets.isNotEmpty()) t.sets else listOf(WorkoutSet(1, t.actualWeight.ifEmpty { t.target }, t.target))
                }
                // Mode 0: Duration, 1: Max Weight, 2: Total Reps
                val dailyVal = when(mode) {
                    0 -> values.sumOf { parseDuration(it.weightOrDuration).toDouble() }.toFloat()
                    1 -> values.maxOfOrNull { parseValue(it.weightOrDuration) } ?: 0f
                    2 -> values.sumOf { parseValue(it.reps).toDouble() }.toFloat()
                    else -> 0f
                }
                Pair(date, dailyVal)
            }
            val grouped = when (granularity) {
                ChartGranularity.DAILY -> raw.groupBy { it.first }
                ChartGranularity.MONTHLY -> raw.groupBy { it.first.withDayOfMonth(1) }
            }
            grouped.map { (date, list) ->
                // Average for weight, Sum for duration/reps
                val finalVal = if (mode == 1) list.map { it.second }.average().toFloat() else list.sumOf { it.second.toDouble() }.toFloat()
                ChartDataPoint(date, finalVal, date.format(DateTimeFormatter.ofPattern("MM/dd")))
            }.sortedBy { it.date }
        }
    }

    private fun parseValue(input: String): Float {
        val regex = Regex("[0-9]+(\\.[0-9]+)?")
        return regex.find(input)?.value?.toFloatOrNull() ?: 0f
    }

    private fun parseDuration(input: String): Float {
        val lower = input.lowercase()
        val num = parseValue(lower)
        return when {
            lower.contains("h") -> num * 60
            lower.contains("s") && !lower.contains("m") -> num / 60
            else -> num
        }
    }

    // --- 杂项与备份 ---
    fun switchTheme(theme: AppTheme) = viewModelScope.launch {
        // 修复：保留当前语言设置
        val currentLang = currentLanguage.value
        dao.saveAppSettings(AppSetting(0, theme.id, currentLang))
    }

    fun switchLanguage(lang: String) = viewModelScope.launch {
        // 修复：保留当前主题设置
        val currentThemeId = currentTheme.value.id
        dao.saveAppSettings(AppSetting(0, currentThemeId, lang))
    }

    fun exportHistoryToCsv(context: Context) {
        // ... (省略具体实现，保持原样或按需添加)
    }
    fun importWeeklyRoutine(context: Context, csv: String) {
        // ... (省略具体实现，保持原样或按需添加)
    }
    fun backupDatabase(uri: Uri, context: Context) {}
    fun restoreDatabase(uri: Uri, context: Context) {}
    suspend fun optimizeExerciseLibrary(): Int = 0
}