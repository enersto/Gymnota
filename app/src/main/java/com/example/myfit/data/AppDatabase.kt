package com.example.myfit.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.myfit.model.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

// Version 3: Added AppSetting
@Database(entities = [WorkoutTask::class, ExerciseTemplate::class, ScheduleConfig::class, WeightRecord::class, AppSetting::class], version = 3)
abstract class AppDatabase : RoomDatabase() {
    abstract fun workoutDao(): WorkoutDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return instance ?: synchronized(this) {
                Room.databaseBuilder(context, AppDatabase::class.java, "myfit_v3.db")
                    .fallbackToDestructiveMigration()
                    .addCallback(PrepopulateCallback())
                    .build().also { instance = it }
            }
        }
    }

    private class PrepopulateCallback : RoomDatabase.Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            super.onCreate(db)
            instance?.let { database ->
                CoroutineScope(Dispatchers.IO).launch {
                    val dao = database.workoutDao()
                    // 默认主题设置
                    dao.saveAppSettings(AppSetting(themeId = 0))

                    if (dao.getScheduleCount() == 0) {
                        val types = listOf(DayType.CORE, DayType.CORE, DayType.ACTIVE_REST, DayType.CORE, DayType.CORE, DayType.LIGHT, DayType.REST)
                        types.forEachIndexed { index, type -> dao.insertSchedule(ScheduleConfig(index + 1, type)) }

                        val templates = listOf(
                            ExerciseTemplate(name = "坐姿蹬腿机", defaultTarget = "3组 x 12次", category = "STRENGTH"),
                            ExerciseTemplate(name = "高位下拉机", defaultTarget = "3组 x 12次", category = "STRENGTH"),
                            ExerciseTemplate(name = "坐姿推胸机", defaultTarget = "3组 x 12次", category = "STRENGTH"),
                            ExerciseTemplate(name = "坐姿划船机", defaultTarget = "3组 x 12次", category = "STRENGTH"),
                            ExerciseTemplate(name = "椭圆仪", defaultTarget = "30分钟", category = "CARDIO"),
                            ExerciseTemplate(name = "去程骑行", defaultTarget = "2.5km 热身", category = "CARDIO"),
                            ExerciseTemplate(name = "返程骑行", defaultTarget = "2.5km 放松", category = "CARDIO"),
                            ExerciseTemplate(name = "动态拉伸/泡沫轴", defaultTarget = "20分钟", category = "STRENGTH")
                        )
                        templates.forEach { dao.insertTemplate(it) }
                    }
                }
            }
        }
    }
}