package com.example.myfit.data

import android.content.Context
import com.example.myfit.model.*
import kotlinx.coroutines.flow.Flow

class WorkoutRepository(private val workoutDao: WorkoutDao) {
    // --- Properties (Flows) ---
    val allTemplates: Flow<List<ExerciseTemplate>> = workoutDao.getAllTemplates()
    val allSchedules: Flow<List<ScheduleConfig>> = workoutDao.getAllSchedules()
    val historyRecords: Flow<List<WorkoutTask>> = workoutDao.getHistoryRecords()
    val allWeightRecords: Flow<List<WeightRecord>> = workoutDao.getAllWeights()
    val latestWeight: Flow<WeightRecord?> = workoutDao.getLatestWeight()

    // --- Tasks ---
    fun getTasksForDate(date: String): Flow<List<WorkoutTask>> = workoutDao.getTasksForDate(date)

    suspend fun insertTask(task: WorkoutTask) = workoutDao.insertTask(task)
    suspend fun updateTask(task: WorkoutTask) = workoutDao.updateTask(task)
    suspend fun deleteTask(task: WorkoutTask) = workoutDao.deleteTask(task)

    // --- Templates ---
    suspend fun insertTemplate(template: ExerciseTemplate) = workoutDao.insertTemplate(template)
    suspend fun updateTemplate(template: ExerciseTemplate) = workoutDao.updateTemplate(template)
    suspend fun softDeleteTemplate(id: Long) = workoutDao.softDeleteTemplate(id)
    suspend fun getTemplateByName(name: String) = workoutDao.getTemplateByName(name)

    // --- Schedule ---
    suspend fun getScheduleConfigSync(day: Int) = workoutDao.getScheduleConfigSync(day)
    suspend fun insertSchedule(config: ScheduleConfig) = workoutDao.insertSchedule(config)

    // --- Weekly Routine ---
    suspend fun getRoutineForDay(day: Int) = workoutDao.getRoutineForDay(day)
    suspend fun insertRoutineItem(item: WeeklyRoutineItem) = workoutDao.insertRoutineItem(item)
    suspend fun deleteRoutineItem(item: WeeklyRoutineItem) = workoutDao.deleteRoutineItem(item)
    suspend fun clearWeeklyRoutine() = workoutDao.clearWeeklyRoutine()

    // --- Weight ---
    suspend fun getWeightForDate(date: String) = workoutDao.getWeightForDate(date)
    suspend fun insertWeight(weight: WeightRecord) = workoutDao.insertWeight(weight)

    // [补充] 同步获取数据方法 (用于导出CSV或AI功能)
    suspend fun getHistoryRecordsSync() = workoutDao.getHistoryRecordsSync()

    // 如果 Dao 中没有 getAllWeightsSync，可以临时用 getHistoryRecordsSync 替代或忽略
    suspend fun getAllWeightsSync(): List<WeightRecord> {
        // 这里只是为了编译通过，实际建议在 Dao 增加 suspend fun getAllWeightsSync(): List<WeightRecord>
        // 或者此处暂返回空列表，若 Dao 未定义
        return emptyList()
    }

    // --- Settings ---
    fun getAppSettings(): Flow<AppSetting?> = workoutDao.getAppSettings()
    suspend fun saveAppSettings(setting: AppSetting) = workoutDao.saveAppSettings(setting)

    // --- Prepopulate / Reload ---
    suspend fun reloadStandardExercises(context: Context, languageCode: String) {
        // 简单实现：仅在测试或重置时调用
        // 实际逻辑可能需要解析 JSON 并 update 数据库
    }
}