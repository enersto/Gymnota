use rusqlite::{Connection, OpenFlags, TransactionBehavior};
use std::time::Duration;

/// Mirrors the shipped Room 7..19 SQL, with a single rollback boundary.
pub fn migrate(path: &str) -> Result<String, &'static str> {
    let mut database = Connection::open_with_flags(path, OpenFlags::SQLITE_OPEN_READ_WRITE)
        .map_err(|_| "migration_open_failed")?;
    database
        .busy_timeout(Duration::from_secs(5))
        .map_err(|_| "migration_timeout_failed")?;
    let transaction = database
        .transaction_with_behavior(TransactionBehavior::Immediate)
        .map_err(|_| "migration_transaction_failed")?;
    let version: i64 = transaction
        .pragma_query_value(None, "user_version", |row| row.get(0))
        .map_err(|_| "migration_version_failed")?;
    if !(7..=19).contains(&version) {
        return Err("unsupported_migration_version");
    }
    for current in version..19 {
        let statements: &[&str] = match current {
            7 => &[
            ],
            8 => &[
                "ALTER TABLE app_settings ADD COLUMN age INTEGER NOT NULL DEFAULT 0",
                "ALTER TABLE app_settings ADD COLUMN height REAL NOT NULL DEFAULT 0",
                "ALTER TABLE app_settings ADD COLUMN gender INTEGER NOT NULL DEFAULT 0",
            ],
            9 => &[
                "ALTER TABLE exercise_templates ADD COLUMN isUnilateral INTEGER NOT NULL DEFAULT 0",
                "ALTER TABLE workout_tasks ADD COLUMN isUnilateral INTEGER NOT NULL DEFAULT 0",
                "ALTER TABLE weekly_routine ADD COLUMN isUnilateral INTEGER NOT NULL DEFAULT 0",
            ],
            10 => &[
                "UPDATE exercise_templates SET bodyPart = 'part_thighs' WHERE bodyPart = 'part_legs'",
                "UPDATE workout_tasks SET bodyPart = 'part_thighs' WHERE bodyPart = 'part_legs'",
                "UPDATE weekly_routine SET bodyPart = 'part_thighs' WHERE bodyPart = 'part_legs'",
            ],
            11 => &[
                "ALTER TABLE exercise_templates ADD COLUMN logType INTEGER NOT NULL DEFAULT 0",
                "ALTER TABLE workout_tasks ADD COLUMN logType INTEGER NOT NULL DEFAULT 0",
                "ALTER TABLE weekly_routine ADD COLUMN logType INTEGER NOT NULL DEFAULT 0",
                "ALTER TABLE exercise_templates ADD COLUMN instruction TEXT NOT NULL DEFAULT ''",
                "UPDATE exercise_templates SET logType = 1 WHERE category = 'CARDIO'",
                "UPDATE workout_tasks SET logType = 1 WHERE category = 'CARDIO'",
                "UPDATE weekly_routine SET logType = 1 WHERE category = 'CARDIO'",
                "UPDATE exercise_templates SET logType = 1 WHERE category = 'CORE'",
                "UPDATE workout_tasks SET logType = 1 WHERE category = 'CORE'",
                "UPDATE weekly_routine SET logType = 1 WHERE category = 'CORE'",
            ],
            12 => &[
                "ALTER TABLE exercise_templates ADD COLUMN imageUri TEXT",
                "ALTER TABLE workout_tasks ADD COLUMN imageUri TEXT",
                "ALTER TABLE weekly_routine ADD COLUMN imageUri TEXT",
            ],
            13 => &[
                "ALTER TABLE app_settings ADD COLUMN aiProvider TEXT NOT NULL DEFAULT 'OpenAI'",
                "ALTER TABLE app_settings ADD COLUMN aiApiKey TEXT NOT NULL DEFAULT ''",
                "ALTER TABLE app_settings ADD COLUMN aiModel TEXT NOT NULL DEFAULT 'gpt-3.5-turbo'",
                "ALTER TABLE app_settings ADD COLUMN aiBaseUrl TEXT NOT NULL DEFAULT ''",
            ],
            14 => &[
                "CREATE TABLE IF NOT EXISTS `ai_chat_history` (\n    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, \n    `timestamp` INTEGER NOT NULL, \n    `role` TEXT NOT NULL, \n    `content` TEXT NOT NULL, \n    `userGoal` TEXT, \n    `modelUsed` TEXT\n)",
            ],
            15 => &[
                "CREATE TABLE IF NOT EXISTS `ai_provider_configs` (\n    `providerName` TEXT NOT NULL, \n    `apiKey` TEXT NOT NULL, \n    `model` TEXT NOT NULL, \n    `baseUrl` TEXT NOT NULL, \n    PRIMARY KEY(`providerName`)\n)",
                "INSERT OR REPLACE INTO ai_provider_configs (providerName, apiKey, model, baseUrl) SELECT aiProvider, aiApiKey, aiModel, aiBaseUrl FROM app_settings",
            ],
            16 => &[
                "ALTER TABLE weight_records ADD COLUMN bodyFatKg REAL",
                "ALTER TABLE weight_records ADD COLUMN skeletalMuscleKg REAL",
                "ALTER TABLE weight_records ADD COLUMN bodyWaterPercentage REAL",
                "ALTER TABLE weight_records ADD COLUMN waistCircumference REAL",
                "ALTER TABLE weight_records ADD COLUMN hipCircumference REAL",
            ],
            17 => &[
                "ALTER TABLE app_settings ADD COLUMN useLocalAiCache INTEGER NOT NULL DEFAULT 1",
                "ALTER TABLE app_settings ADD COLUMN maxHistoryLimit INTEGER NOT NULL DEFAULT 10",
                "CREATE TABLE IF NOT EXISTS `ai_memory` (\n    `id` INTEGER PRIMARY KEY NOT NULL, \n    `longTermContext` TEXT NOT NULL, \n    `lastSyncTime` INTEGER NOT NULL\n)",
            ],
            18 => &[
                "ALTER TABLE exercise_templates ADD COLUMN animationUri TEXT",
                "UPDATE exercise_templates\nSET animationUri = imageUri, imageUri = NULL\nWHERE imageUri LIKE 'file:///android_asset/catalog/%'",
            ],
            _ => return Err("missing_migration"),
        };
        for sql in statements {
            transaction
                .execute_batch(sql)
                .map_err(|_| "migration_sql_failed")?;
        }
        transaction
            .pragma_update(None, "user_version", current + 1)
            .map_err(|_| "migration_version_write_failed")?;
    }
    if version < 19 {
        // Room validates the migrated schema on its first open and records its identity.
        transaction
            .execute_batch("DROP TABLE IF EXISTS room_master_table")
            .map_err(|_| "migration_identity_failed")?;
    }
    transaction
        .commit()
        .map_err(|_| "migration_commit_failed")?;
    Ok("19".into())
}
