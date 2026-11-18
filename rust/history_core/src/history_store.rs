use rusqlite::{Connection, OpenFlags, OptionalExtension};
use serde_json::{json, Value};
use std::time::Duration;

pub fn snapshot(path: &str) -> Result<Value, &'static str> {
    let mut database = Connection::open_with_flags(path, OpenFlags::SQLITE_OPEN_READ_ONLY)
        .map_err(|_| "history_open_failed")?;
    database
        .busy_timeout(Duration::from_secs(2))
        .map_err(|_| "history_config_failed")?;
    let transaction = database
        .transaction()
        .map_err(|_| "history_transaction_failed")?;
    let tasks = {
        let mut statement = transaction.prepare(
            "SELECT id, date, name, category, logType, isUnilateral, target, actualWeight, sets, bodyPart
             FROM workout_tasks WHERE isCompleted = 1 ORDER BY date DESC, id DESC"
        ).map_err(|_| "history_tasks_schema")?;
        let rows = statement
            .query_map([], |row| {
                Ok(json!({
                    "id": row.get::<_, i64>(0)?.to_string(),
                    "date": row.get::<_, String>(1)?, "name": row.get::<_, String>(2)?,
                    "category": row.get::<_, String>(3)?, "logType": row.get::<_, i64>(4)?,
                    "unilateral": row.get::<_, bool>(5)?, "target": row.get::<_, String>(6)?,
                    "actualWeight": row.get::<_, String>(7)?, "sets": row.get::<_, String>(8)?,
                    "bodyPart": row.get::<_, String>(9)?,
                }))
            })
            .map_err(|_| "history_tasks_query")?;
        rows.collect::<Result<Vec<_>, _>>()
            .map_err(|_| "history_task_row")?
    };
    let weights = {
        let mut statement = transaction
            .prepare(
                "SELECT date, weight, bodyFatKg, skeletalMuscleKg, bodyWaterPercentage,
                    waistCircumference, hipCircumference FROM weight_records ORDER BY date DESC",
            )
            .map_err(|_| "history_weight_schema")?;
        let rows = statement
            .query_map([], |row| {
                Ok(json!({
                    "date": row.get::<_, String>(0)?, "weight": row.get::<_, f64>(1)?,
                    "bodyFatKg": row.get::<_, Option<f64>>(2)?,
                    "skeletalMuscleKg": row.get::<_, Option<f64>>(3)?,
                    "bodyWaterPercentage": row.get::<_, Option<f64>>(4)?,
                    "waistCircumference": row.get::<_, Option<f64>>(5)?,
                    "hipCircumference": row.get::<_, Option<f64>>(6)?,
                }))
            })
            .map_err(|_| "history_weights_query")?;
        rows.collect::<Result<Vec<_>, _>>()
            .map_err(|_| "history_weight_row")?
    };
    let profile = transaction.query_row(
        "SELECT height, age, gender FROM app_settings WHERE id = 0 LIMIT 1", [],
        |row| Ok(json!({"height": row.get::<_, f64>(0)?, "age": row.get::<_, i64>(1)?, "gender": row.get::<_, i64>(2)?}))
    ).optional().map_err(|_| "history_profile_query")?
        .unwrap_or_else(|| json!({"height": 0.0, "age": 0, "gender": 0}));
    transaction.commit().map_err(|_| "history_commit_failed")?;
    Ok(json!({"tasks": tasks, "weights": weights, "profile": profile}))
}

pub fn process(path: &str) -> Result<String, &'static str> {
    super::process_json(&snapshot(path)?.to_string())
}
