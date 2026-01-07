package com.example.myfit.model

import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class Converters {
    private val gson = Gson()

    // --- WorkoutSet List 转换 ---
    @TypeConverter
    fun fromWorkoutSetList(value: List<WorkoutSet>?): String {
        return gson.toJson(value ?: emptyList<WorkoutSet>())
    }

    @TypeConverter
    fun toWorkoutSetList(value: String?): List<WorkoutSet> {
        if (value.isNullOrEmpty()) return emptyList()
        val listType = object : TypeToken<List<WorkoutSet>>() {}.type
        return gson.fromJson(value, listType)
    }

    // --- DayType 枚举转换 (修复 Room 无法保存日程类型的问题) ---
    @TypeConverter
    fun fromDayType(value: DayType): String = value.name

    @TypeConverter
    fun toDayType(value: String): DayType = try {
        DayType.valueOf(value)
    } catch (e: Exception) {
        DayType.CORE // 默认回退值，对应您的枚举定义
    }
}