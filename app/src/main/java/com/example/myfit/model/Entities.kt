package com.example.myfit.model

import androidx.room.Entity
import androidx.room.PrimaryKey

// 1. 系统设置 (新增: 存储主题偏好)
@Entity(tableName = "app_settings")
data class AppSetting(
    @PrimaryKey val id: Int = 0, // 永远只有 ID=0 的一条数据
    val themeId: Int = 0 // 0=深色(默认), 1=浅绿, 2=浅蓝, 3=浅黄, 4=灰
)

// 2. 动作模板库
@Entity(tableName = "exercise_templates")
data class ExerciseTemplate(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val defaultTarget: String, // 例如 "3组x12次" 或 "30分钟"
    val category: String,      // STRENGTH (力量), CARDIO (有氧)
    val isDeleted: Boolean = false
)

// 3. 每日日程配置
@Entity(tableName = "schedule_config")
data class ScheduleConfig(
    @PrimaryKey val dayOfWeek: Int,
    val dayType: DayType
)

// 4. 每日实际训练任务
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

// 5. 体重记录
@Entity(tableName = "weight_records")
data class WeightRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,
    val weight: Float
)

// 日程类型枚举
enum class DayType(val label: String, val colorHex: Long) {
    CORE("核心训练日", 0xFFFF5722),
    ACTIVE_REST("动态恢复日", 0xFF4CAF50),
    LIGHT("轻松活动日", 0xFF03A9F4),
    REST("休息日", 0xFF9E9E9E)
}

// 主题枚举 (V3.0 新增)
enum class AppTheme(val id: Int, val label: String, val primary: Long, val background: Long, val onBackground: Long) {
    DARK(0, "硬核深色", 0xFFFF5722, 0xFF121212, 0xFFFFFFFF),
    GREEN(1, "清新浅绿", 0xFF4CAF50, 0xFFF1F8E9, 0xFF1B5E20),
    BLUE(2, "宁静浅蓝", 0xFF2196F3, 0xFFE3F2FD, 0xFF0D47A1),
    YELLOW(3, "活力浅黄", 0xFFFFC107, 0xFFFFFDE7, 0xFFBF360C),
    GREY(4, "极简商务", 0xFF607D8B, 0xFFECEFF1, 0xFF263238);

    companion object {
        fun fromId(id: Int): AppTheme = values().find { it.id == id } ?: DARK
    }
}