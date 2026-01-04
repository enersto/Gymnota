package com.example.myfit.data

import androidx.room.*
import com.example.myfit.model.*
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkoutDao {
    // --- 系统设置 (V3.0) ---
    @Query("SELECT * FROM app_settings WHERE id = 0")
    fun getAppSettings(): Flow<AppSetting?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveAppSettings(setting: AppSetting)

    // --- 日程配置 ---
    @Query("SELECT * FROM schedule_config ORDER BY dayOfWeek ASC")
    fun getAllSchedules(): Flow<List<ScheduleConfig>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSchedule(config: ScheduleConfig)

    // --- 动作模板 (CRUD) ---
    @Query("SELECT * FROM exercise_templates WHERE isDeleted = 0 ORDER BY id DESC")
    fun getAllTemplates(): Flow<List<ExerciseTemplate>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTemplate(template: ExerciseTemplate)

    @Update
    suspend fun updateTemplate(template: ExerciseTemplate)

    // 软删除
    @Query("UPDATE exercise_templates SET isDeleted = 1 WHERE id = :id")
    suspend fun softDeleteTemplate(id: Long)

    // --- 每日任务 ---
    @Query("SELECT * FROM workout_tasks WHERE date = :date")
    fun getTasksForDate(date: String): Flow<List<WorkoutTask>>

    @Query("SELECT * FROM workout_tasks WHERE isCompleted = 1 ORDER BY date DESC")
    fun getHistoryRecords(): Flow<List<WorkoutTask>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: WorkoutTask)

    @Update
    suspend fun updateTask(task: WorkoutTask)

    @Delete
    suspend fun deleteTask(task: WorkoutTask)

    // --- 体重 ---
    @Query("SELECT * FROM weight_records ORDER BY date DESC LIMIT 1")
    fun getLatestWeight(): Flow<WeightRecord?>

    @Insert
    suspend fun insertWeight(record: WeightRecord)

    @Query("SELECT COUNT(*) FROM schedule_config")
    suspend fun getScheduleCount(): Int
}