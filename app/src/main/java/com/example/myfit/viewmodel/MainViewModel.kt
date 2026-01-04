package com.example.myfit.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.myfit.data.AppDatabase
import com.example.myfit.model.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.temporal.ChronoUnit

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val dao = AppDatabase.getDatabase(application).workoutDao()

    private val _selectedDate = MutableStateFlow(LocalDate.now())
    val selectedDate = _selectedDate.asStateFlow()

    // --- V3.0 主题状态 ---
    val currentTheme = dao.getAppSettings()
        .map { it?.themeId ?: 0 }
        .map { AppTheme.fromId(it) }
        .stateIn(viewModelScope, SharingStarted.Lazily, AppTheme.DARK)

    // --- 日程与任务 ---
    val todayScheduleType = combine(_selectedDate, dao.getAllSchedules()) { date, schedules ->
        val dayOfWeek = date.dayOfWeek.value
        schedules.find { it.dayOfWeek == dayOfWeek }?.dayType ?: DayType.REST
    }.stateIn(viewModelScope, SharingStarted.Lazily, DayType.REST)

    val todayTasks = _selectedDate.flatMapLatest { dao.getTasksForDate(it.toString()) }
    val historyRecords = dao.getHistoryRecords()
    val allTemplates = dao.getAllTemplates()

    val showWeightAlert = dao.getLatestWeight().map { record ->
        if (record == null) true else ChronoUnit.DAYS.between(LocalDate.parse(record.date), LocalDate.now()) > 7
    }.stateIn(viewModelScope, SharingStarted.Lazily, false)

    // --- 操作方法 ---

    // 切换主题
    fun switchTheme(theme: AppTheme) {
        viewModelScope.launch { dao.saveAppSettings(AppSetting(themeId = theme.id)) }
    }

    // 动作库管理
    fun saveTemplate(template: ExerciseTemplate) {
        viewModelScope.launch {
            if (template.id == 0L) dao.insertTemplate(template) else dao.updateTemplate(template)
        }
    }

    fun deleteTemplate(id: Long) {
        viewModelScope.launch { dao.softDeleteTemplate(id) }
    }

    // 日常任务操作
    fun addTaskFromTemplate(template: ExerciseTemplate) {
        viewModelScope.launch {
            val newTask = WorkoutTask(
                date = _selectedDate.value.toString(),
                templateId = template.id,
                name = template.name,
                target = template.defaultTarget,
                type = template.category
            )
            dao.insertTask(newTask)
        }
    }

    fun updateTask(task: WorkoutTask) = viewModelScope.launch { dao.updateTask(task) }
    fun removeTask(task: WorkoutTask) = viewModelScope.launch { dao.deleteTask(task) }

    fun updateScheduleConfig(dayOfWeek: Int, newType: DayType) = viewModelScope.launch {
        dao.insertSchedule(ScheduleConfig(dayOfWeek, newType))
    }

    fun logWeight(weight: Float) = viewModelScope.launch {
        dao.insertWeight(WeightRecord(date = LocalDate.now().toString(), weight = weight))
    }
}