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
import com.google.gson.Gson // [修复] 添加 Gson 引用
import com.google.gson.reflect.TypeToken // [修复] 添加 TypeToken 引用

import androidx.room.Entity
import androidx.room.PrimaryKey

val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(database: SupportSQLiteDatabase) {}
}

val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE app_settings ADD COLUMN age INTEGER NOT NULL DEFAULT 0")
        database.execSQL("ALTER TABLE app_settings ADD COLUMN height REAL NOT NULL DEFAULT 0")
        database.execSQL("ALTER TABLE app_settings ADD COLUMN gender INTEGER NOT NULL DEFAULT 0")
    }
}

// [新增] 2. 定义迁移策略：版本 9 -> 10
val MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // 为 exercise_templates, workout_tasks, weekly_routine 添加 isUnilateral 列
        // SQLite 不支持一次性添加多列或多个表，需分步执行
        database.execSQL("ALTER TABLE exercise_templates ADD COLUMN isUnilateral INTEGER NOT NULL DEFAULT 0")
        database.execSQL("ALTER TABLE workout_tasks ADD COLUMN isUnilateral INTEGER NOT NULL DEFAULT 0")
        database.execSQL("ALTER TABLE weekly_routine ADD COLUMN isUnilateral INTEGER NOT NULL DEFAULT 0")
    }
}

// [新增] 3. 定义迁移策略：版本 10 -> 11
// 目标：将所有 'part_legs' (腿部) 的数据迁移为 'part_thighs' (大腿)
val MIGRATION_10_11 = object : Migration(10, 11) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // 更新动作模板表
        database.execSQL("UPDATE exercise_templates SET bodyPart = 'part_thighs' WHERE bodyPart = 'part_legs'")
        // 更新历史记录表
        database.execSQL("UPDATE workout_tasks SET bodyPart = 'part_thighs' WHERE bodyPart = 'part_legs'")
        // 更新周计划表
        database.execSQL("UPDATE weekly_routine SET bodyPart = 'part_thighs' WHERE bodyPart = 'part_legs'")
    }
}

// [新增] MIGRATION_11_12
val MIGRATION_11_12 = object : Migration(11, 12) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // 1. 添加 logType 列 (默认为 0: WEIGHT_REPS)
        database.execSQL("ALTER TABLE exercise_templates ADD COLUMN logType INTEGER NOT NULL DEFAULT 0")
        database.execSQL("ALTER TABLE workout_tasks ADD COLUMN logType INTEGER NOT NULL DEFAULT 0")
        database.execSQL("ALTER TABLE weekly_routine ADD COLUMN logType INTEGER NOT NULL DEFAULT 0")

        // 2. 添加 instruction 列
        database.execSQL("ALTER TABLE exercise_templates ADD COLUMN instruction TEXT NOT NULL DEFAULT ''")

        // 3. 数据清洗与迁移逻辑
        // 3.1 有氧运动 (CARDIO) -> 统一设为 DURATION (1)
        database.execSQL("UPDATE exercise_templates SET logType = 1 WHERE category = 'CARDIO'")
        database.execSQL("UPDATE workout_tasks SET logType = 1 WHERE category = 'CARDIO'")
        database.execSQL("UPDATE weekly_routine SET logType = 1 WHERE category = 'CARDIO'")

        // 3.2 核心运动 (CORE) -> 统一先设为 DURATION (1)，保持旧版本计时习惯
        database.execSQL("UPDATE exercise_templates SET logType = 1 WHERE category = 'CORE'")
        database.execSQL("UPDATE workout_tasks SET logType = 1 WHERE category = 'CORE'")
        database.execSQL("UPDATE weekly_routine SET logType = 1 WHERE category = 'CORE'")

        // 3.3 力量训练 (STRENGTH) -> 保持默认 0 (WEIGHT_REPS)，无需操作
    }
}

// [新增] MIGRATION_12_13：添加 imageUri 字段
val MIGRATION_12_13 = object : Migration(12, 13) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // 为三个表添加 imageUri 列，允许为空
        database.execSQL("ALTER TABLE exercise_templates ADD COLUMN imageUri TEXT")
        database.execSQL("ALTER TABLE workout_tasks ADD COLUMN imageUri TEXT")
        database.execSQL("ALTER TABLE weekly_routine ADD COLUMN imageUri TEXT")
    }
}

// [新增] MIGRATION_13_14: 添加 AI 配置字段
val MIGRATION_13_14 = object : Migration(13, 14) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE app_settings ADD COLUMN aiProvider TEXT NOT NULL DEFAULT 'OpenAI'")
        database.execSQL("ALTER TABLE app_settings ADD COLUMN aiApiKey TEXT NOT NULL DEFAULT ''")
        database.execSQL("ALTER TABLE app_settings ADD COLUMN aiModel TEXT NOT NULL DEFAULT 'gpt-3.5-turbo'")
        database.execSQL("ALTER TABLE app_settings ADD COLUMN aiBaseUrl TEXT NOT NULL DEFAULT ''")
    }
}

val MIGRATION_14_15 = object : Migration(14, 15) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `ai_chat_history` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, 
                `timestamp` INTEGER NOT NULL, 
                `role` TEXT NOT NULL, 
                `content` TEXT NOT NULL, 
                `userGoal` TEXT, 
                `modelUsed` TEXT
            )
        """)
    }
}

// [新增] 迁移策略 15 -> 16
val MIGRATION_15_16 = object : Migration(15, 16) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // 创建新的配置表
        database.execSQL("""
            CREATE TABLE IF NOT EXISTS `ai_provider_configs` (
                `providerName` TEXT NOT NULL, 
                `apiKey` TEXT NOT NULL, 
                `model` TEXT NOT NULL, 
                `baseUrl` TEXT NOT NULL, 
                PRIMARY KEY(`providerName`)
            )
        """)

        // (可选) 如果希望升级时自动把当前正在使用的配置存入新表，可以加这一句：
        database.execSQL("INSERT OR REPLACE INTO ai_provider_configs (providerName, apiKey, model, baseUrl) SELECT aiProvider, aiApiKey, aiModel, aiBaseUrl FROM app_settings")
    }
}


@Database(
    entities = [
        WorkoutTask::class,
        ExerciseTemplate::class,
        ScheduleConfig::class,
        WeightRecord::class,
        AppSetting::class,
        WeeklyRoutineItem::class,
        AiChatRecord::class,
        AiProviderConfig::class
    ],
    version = 16, // 🔴 升级版本号
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun workoutDao(): WorkoutDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return instance ?: synchronized(this) {
                Room.databaseBuilder(context, AppDatabase::class.java, "myfit_v7.db")
                    .addMigrations(MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11,
                        MIGRATION_11_12, MIGRATION_12_13,MIGRATION_13_14, MIGRATION_14_15,
                        MIGRATION_15_16
                        ) // 🔴 添加新迁移
                    .addCallback(PrepopulateCallback(context.applicationContext))
                    .build().also { instance = it }
            }
        }
    }

    // [修复] 类定义中添加构造函数接收 context
    private class PrepopulateCallback(private val context: Context) : RoomDatabase.Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            super.onCreate(db)
            instance?.let { database ->
                CoroutineScope(Dispatchers.IO).launch {
                    val dao = database.workoutDao()

                    // 1. 保存默认设置 (根据系统语言决定默认 app 语言)
                    val sysLang = java.util.Locale.getDefault().language // "zh", "en", "ja"...
                    // 确保是我们支持的语言，否则默认 en
                    val defaultAppLang = if (sysLang in listOf("zh", "en", "ja", "de", "es")) sysLang else "zh"

                    dao.saveAppSettings(AppSetting(themeId = 1, languageCode = "zh"))

                    if (dao.getScheduleCount() == 0) {
                        val types = listOf(DayType.CORE, DayType.CORE, DayType.ACTIVE_REST, DayType.CORE, DayType.CORE, DayType.LIGHT, DayType.REST)
                        types.forEachIndexed { index, type -> dao.insertSchedule(ScheduleConfig(index + 1, type)) }
                    }

                    if (dao.getTemplateCount() == 0) {
                        try {
                            // 动态决定文件名
                            val fileName = when (defaultAppLang) {
                                "en" -> "exercises_en.json"
                                "ja" -> "exercises_ja.json"
                                "de" -> "exercises_de.json"
                                "es" -> "exercises_es.json"
                                else -> "default_exercises.json" // 默认中文
                            }
                            // [逻辑] 读取 assets/fileName
                            val jsonString = context.assets.open(fileName)
                                .bufferedReader()
                                .use { it.readText() }

                            val listType = object : TypeToken<List<ExerciseTemplate>>() {}.type
                            val templates: List<ExerciseTemplate> = Gson().fromJson(jsonString, listType)

                            dao.insertTemplates(templates)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
            }
        }
    }
}