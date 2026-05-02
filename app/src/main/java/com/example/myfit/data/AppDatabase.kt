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
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

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

val MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE exercise_templates ADD COLUMN isUnilateral INTEGER NOT NULL DEFAULT 0")
        database.execSQL("ALTER TABLE workout_tasks ADD COLUMN isUnilateral INTEGER NOT NULL DEFAULT 0")
        database.execSQL("ALTER TABLE weekly_routine ADD COLUMN isUnilateral INTEGER NOT NULL DEFAULT 0")
    }
}

val MIGRATION_10_11 = object : Migration(10, 11) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("UPDATE exercise_templates SET bodyPart = 'part_thighs' WHERE bodyPart = 'part_legs'")
        database.execSQL("UPDATE workout_tasks SET bodyPart = 'part_thighs' WHERE bodyPart = 'part_legs'")
        database.execSQL("UPDATE weekly_routine SET bodyPart = 'part_thighs' WHERE bodyPart = 'part_legs'")
    }
}

val MIGRATION_11_12 = object : Migration(11, 12) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE exercise_templates ADD COLUMN logType INTEGER NOT NULL DEFAULT 0")
        database.execSQL("ALTER TABLE workout_tasks ADD COLUMN logType INTEGER NOT NULL DEFAULT 0")
        database.execSQL("ALTER TABLE weekly_routine ADD COLUMN logType INTEGER NOT NULL DEFAULT 0")
        database.execSQL("ALTER TABLE exercise_templates ADD COLUMN instruction TEXT NOT NULL DEFAULT ''")
        database.execSQL("UPDATE exercise_templates SET logType = 1 WHERE category = 'CARDIO'")
        database.execSQL("UPDATE workout_tasks SET logType = 1 WHERE category = 'CARDIO'")
        database.execSQL("UPDATE weekly_routine SET logType = 1 WHERE category = 'CARDIO'")
        database.execSQL("UPDATE exercise_templates SET logType = 1 WHERE category = 'CORE'")
        database.execSQL("UPDATE workout_tasks SET logType = 1 WHERE category = 'CORE'")
        database.execSQL("UPDATE weekly_routine SET logType = 1 WHERE category = 'CORE'")
    }
}

val MIGRATION_12_13 = object : Migration(12, 13) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE exercise_templates ADD COLUMN imageUri TEXT")
        database.execSQL("ALTER TABLE workout_tasks ADD COLUMN imageUri TEXT")
        database.execSQL("ALTER TABLE weekly_routine ADD COLUMN imageUri TEXT")
    }
}

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

val MIGRATION_15_16 = object : Migration(15, 16) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("""
            CREATE TABLE IF NOT EXISTS `ai_provider_configs` (
                `providerName` TEXT NOT NULL, 
                `apiKey` TEXT NOT NULL, 
                `model` TEXT NOT NULL, 
                `baseUrl` TEXT NOT NULL, 
                PRIMARY KEY(`providerName`)
            )
        """)
        database.execSQL("INSERT OR REPLACE INTO ai_provider_configs (providerName, apiKey, model, baseUrl) SELECT aiProvider, aiApiKey, aiModel, aiBaseUrl FROM app_settings")
    }
}

val MIGRATION_16_17 = object : Migration(16, 17) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE weight_records ADD COLUMN bodyFatKg REAL")
        database.execSQL("ALTER TABLE weight_records ADD COLUMN skeletalMuscleKg REAL")
        database.execSQL("ALTER TABLE weight_records ADD COLUMN bodyWaterPercentage REAL")
        database.execSQL("ALTER TABLE weight_records ADD COLUMN waistCircumference REAL")
        database.execSQL("ALTER TABLE weight_records ADD COLUMN hipCircumference REAL")
    }
}

val MIGRATION_17_18 = object : Migration(17, 18) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // 1. 更新 app_settings
        database.execSQL("ALTER TABLE app_settings ADD COLUMN useLocalAiCache INTEGER NOT NULL DEFAULT 1")
        database.execSQL("ALTER TABLE app_settings ADD COLUMN maxHistoryLimit INTEGER NOT NULL DEFAULT 10")
        
        // 2. 创建 ai_memory 表
        database.execSQL("""
            CREATE TABLE IF NOT EXISTS `ai_memory` (
                `id` INTEGER PRIMARY KEY NOT NULL, 
                `longTermContext` TEXT NOT NULL, 
                `lastSyncTime` INTEGER NOT NULL
            )
        """)
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
        AiProviderConfig::class,
        AiMemory::class
    ],
    version = 18, // 🔴 升级版本号
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
                        MIGRATION_15_16, MIGRATION_16_17, MIGRATION_17_18
                        )
                    .addCallback(PrepopulateCallback(context.applicationContext))
                    .build().also { instance = it }
            }
        }
    }

    private class PrepopulateCallback(private val context: Context) : RoomDatabase.Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            super.onCreate(db)
            instance?.let { database ->
                CoroutineScope(Dispatchers.IO).launch {
                    val dao = database.workoutDao()
                    dao.saveAppSettings(AppSetting(themeId = 1, languageCode = "zh"))

                    if (dao.getScheduleCount() == 0) {
                        val types = listOf(DayType.CORE, DayType.CORE, DayType.ACTIVE_REST, DayType.CORE, DayType.CORE, DayType.LIGHT, DayType.REST)
                        types.forEachIndexed { index, type -> dao.insertSchedule(ScheduleConfig(index + 1, type)) }
                    }

                    if (dao.getTemplateCount() == 0) {
                        try {
                            val sysLang = java.util.Locale.getDefault().language
                            val defaultAppLang = if (sysLang in listOf("zh", "en", "ja", "de", "es")) sysLang else "zh"
                            val fileName = when (defaultAppLang) {
                                "en" -> "exercises_en.json"
                                "ja" -> "exercises_ja.json"
                                "de" -> "exercises_de.json"
                                "es" -> "exercises_es.json"
                                else -> "default_exercises.json"
                            }
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
