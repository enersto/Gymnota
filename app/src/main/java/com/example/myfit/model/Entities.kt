package com.example.myfit.model

import androidx.room.Entity
import androidx.room.PrimaryKey

// 修改了 AppSetting，增加 languageCode
@Entity(tableName = "app_settings")
data class AppSetting(
    @PrimaryKey val id: Int = 0,
    val themeId: Int = 0,
    val languageCode: String = "zh" // 默认为中文
)

// 其他实体保持不变
@Entity(tableName = "exercise_templates")
data class ExerciseTemplate(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val defaultTarget: String,
    val category: String, // "STRENGTH" or "CARDIO"
    val isDeleted: Boolean = false
)

@Entity(tableName = "schedule_config")
data class ScheduleConfig(
    @PrimaryKey val dayOfWeek: Int,
    val dayType: DayType
)

@Entity(tableName = "workout_tasks")
data class WorkoutTask(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,
    val templateId: Long,
    val name: String,
    var target: String,
    var actualWeight: String = "",
    var isCompleted: Boolean = false,
    val type: String
)

@Entity(tableName = "weight_records")
data class WeightRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,
    val weight: Float
)

@Entity(tableName = "weekly_routine")
data class WeeklyRoutineItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dayOfWeek: Int,
    val templateId: Long,
    val name: String,
    val target: String,
    val category: String
)

enum class DayType(val labelResId: Int, val colorHex: Long) {
    // 这里做了一个关键修改：把 hardcode 的 string 换成了 R.string 资源ID
    CORE(com.example.myfit.R.string.type_core, 0xFFFF5722),
    ACTIVE_REST(com.example.myfit.R.string.type_active, 0xFF4CAF50),
    LIGHT(com.example.myfit.R.string.type_light, 0xFF03A9F4),
    REST(com.example.myfit.R.string.type_rest, 0xFF9E9E9E)
}

enum class AppTheme(val id: Int, val primary: Long, val background: Long, val onBackground: Long) {
    DARK(0, 0xFFFF5722, 0xFF121212, 0xFFFFFFFF),
    GREEN(1, 0xFF4CAF50, 0xFFF1F8E9, 0xFF1B5E20),
    BLUE(2, 0xFF2196F3, 0xFFE3F2FD, 0xFF0D47A1),
    YELLOW(3, 0xFFFFC107, 0xFFFFFDE7, 0xFFBF360C),
    GREY(4, 0xFF607D8B, 0xFFECEFF1, 0xFF263238);

    companion object {
        fun fromId(id: Int): AppTheme = values().find { it.id == id } ?: DARK
    }
}