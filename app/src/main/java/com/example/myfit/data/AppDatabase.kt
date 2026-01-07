package com.example.myfit.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.myfit.model.*
import com.example.myfit.model.Converters
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.room.migration.Migration


// 1. 定义迁移策略：版本 7 -> 8
val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // 为 weekly_routine 表添加两个新列，默认值设为空字符串
        database.execSQL("ALTER TABLE weekly_routine ADD COLUMN bodyPart TEXT NOT NULL DEFAULT ''")
        database.execSQL("ALTER TABLE weekly_routine ADD COLUMN equipment TEXT NOT NULL DEFAULT ''")
    }
}
@Database(
    entities = [
        WorkoutTask::class,
        ExerciseTemplate::class,
        ScheduleConfig::class,
        WeightRecord::class,
        AppSetting::class,
        WeeklyRoutineItem::class
    ],
    version = 8, // 🔴 升级版本号到 8
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    // 确保这行存在且没有拼写错误
    abstract fun workoutDao(): WorkoutDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return instance ?: synchronized(this) {
                Room.databaseBuilder(context, AppDatabase::class.java, "myfit_v7.db") // 文件名保持不变，内部结构升级
                    .addMigrations(MIGRATION_7_8) // 🔴 添加迁移策略
                    // .fallbackToDestructiveMigration() // 🔴 删除或注释掉这一行！
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

                    dao.saveAppSettings(AppSetting(themeId = 0, languageCode = "zh"))

                    if (dao.getScheduleCount() == 0) {
                        val types = listOf(DayType.CORE, DayType.CORE, DayType.ACTIVE_REST, DayType.CORE, DayType.CORE, DayType.LIGHT, DayType.REST)
                        types.forEachIndexed { index, type -> dao.insertSchedule(ScheduleConfig(index + 1, type)) }
                    }

                    if (dao.getTemplateCount() == 0) {
                        val defaults = listOf(
                            ExerciseTemplate(name = "坐姿推胸", defaultTarget = "3x12", category = "STRENGTH", bodyPart = "part_chest", equipment = "equip_machine"),
                            ExerciseTemplate(name = "高位下拉", defaultTarget = "3x12", category = "STRENGTH", bodyPart = "part_back", equipment = "equip_machine"),
                            ExerciseTemplate(name = "深蹲", defaultTarget = "4x10", category = "STRENGTH", bodyPart = "part_legs", equipment = "equip_barbell"),
                            ExerciseTemplate(name = "硬拉", defaultTarget = "4x8", category = "STRENGTH", bodyPart = "part_back", equipment = "equip_barbell"),
                            ExerciseTemplate(name = "哑铃侧平举", defaultTarget = "4x15", category = "STRENGTH", bodyPart = "part_shoulders", equipment = "equip_dumbbell"),
                            ExerciseTemplate(name = "平板支撑", defaultTarget = "3x60s", category = "CORE", bodyPart = "part_abs", equipment = "equip_bodyweight"),
                            ExerciseTemplate(name = "卷腹", defaultTarget = "4x20", category = "CORE", bodyPart = "part_abs", equipment = "equip_bodyweight"),
                            ExerciseTemplate(name = "热身跑", defaultTarget = "5 min", category = "CARDIO", bodyPart = "part_cardio", equipment = "equip_cardio_machine"),
                            ExerciseTemplate(name = "椭圆仪", defaultTarget = "30 min", category = "CARDIO", bodyPart = "part_cardio", equipment = "equip_cardio_machine")
                        )
                        defaults.forEach { dao.insertTemplate(it) }
                    }
                }
            }
        }
    }
}