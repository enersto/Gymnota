use rusqlite::{
    params, Connection, OpenFlags, OptionalExtension, Transaction, TransactionBehavior,
};
use serde_json::{json, Value};
use std::time::Duration;

const INITIAL_SETS: &str = r#"[{"setNumber":1,"weightOrDuration":"","reps":""}]"#;

fn save_profile(transaction: &Transaction<'_>, fields: &Value) -> Result<(), &'static str> {
    let age = fields["age"]
        .as_i64()
        .filter(|value| (0..=i32::MAX as i64).contains(value))
        .ok_or("invalid_age")?;
    let height = fields["height"]
        .as_f64()
        .filter(|value| value.is_finite() && *value >= 0.0)
        .ok_or("invalid_height")?;
    let gender = fields["gender"]
        .as_i64()
        .filter(|value| matches!(value, 0 | 1))
        .ok_or("invalid_gender")?;
    transaction
        .execute(
            "INSERT INTO app_settings
         (id, themeId, languageCode, age, height, gender, aiProvider, aiApiKey, aiModel,
          aiBaseUrl, useLocalAiCache, maxHistoryLimit)
         VALUES (0, 1, 'zh', ?1, ?2, ?3, 'OpenAI', '', 'gpt-3.5-turbo', '', 1, 10)
         ON CONFLICT(id) DO UPDATE SET age = excluded.age, height = excluded.height,
                                       gender = excluded.gender",
            params![age, height, gender],
        )
        .map_err(|_| "profile_write_failed")?;
    Ok(())
}

fn save_weight(
    transaction: &Transaction<'_>,
    date: &str,
    fields: &Value,
) -> Result<i64, &'static str> {
    let weight = fields["weight"]
        .as_f64()
        .filter(|value| value.is_finite() && *value > 0.0)
        .ok_or("invalid_weight")?;
    let metric = |key: &str| -> Result<Option<f64>, &'static str> {
        if fields[key].is_null() {
            return Ok(None);
        }
        fields[key]
            .as_f64()
            .filter(|value| value.is_finite() && *value > 0.0)
            .map(Some)
            .ok_or("invalid_body_metric")
    };
    if let Some(profile) = fields.get("profile").filter(|value| !value.is_null()) {
        save_profile(transaction, profile)?;
    }
    transaction.execute(
        "INSERT INTO weight_records
         (date, weight, bodyFatKg, skeletalMuscleKg, bodyWaterPercentage, waistCircumference, hipCircumference)
         VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7)",
        params![date, weight, metric("fat")?, metric("muscle")?, metric("water")?, metric("waist")?, metric("hip")?],
    ).map_err(|_| "weight_write_failed")?;
    Ok(transaction.last_insert_rowid())
}

pub fn execute(input: &str) -> Result<String, &'static str> {
    let request: Value = serde_json::from_str(input).map_err(|_| "invalid_request")?;
    let path = request["path"].as_str().ok_or("missing_path")?;
    let date = request["date"]
        .as_str()
        .filter(|date| super::date_valid(date))
        .ok_or("invalid_date")?;
    let changes = request["changes"].as_array().ok_or("invalid_changes")?;
    let mut database = Connection::open_with_flags(path, OpenFlags::SQLITE_OPEN_READ_WRITE)
        .map_err(|_| "open_failed")?;
    database
        .busy_timeout(Duration::from_secs(2))
        .map_err(|_| "timeout_failed")?;
    let transaction = database
        .transaction_with_behavior(TransactionBehavior::Immediate)
        .map_err(|_| "transaction_failed")?;
    let mut updated = Vec::new();
    for change in changes {
        match change["action"].as_str() {
            Some("saveAiRecord") => {
                updated.push(super::ai_store::save_record(&transaction, change)?);
                continue;
            }
            Some("deleteAiRecord") => {
                updated.extend(super::ai_store::delete_record(&transaction, change)?);
                continue;
            }
            Some("saveAiMemory") => {
                super::ai_store::save_memory(&transaction, change)?;
                updated.push(0);
                continue;
            }
            Some("patchSettings") => {
                super::settings_store::patch(&transaction, change)?;
                updated.push(0);
                continue;
            }
            Some("saveSchedule") => {
                super::settings_store::schedule(&transaction, change)?;
                updated.push(change["day"].as_i64().ok_or("invalid_day")?);
                continue;
            }
            Some("saveModel") => {
                super::settings_store::model(&transaction, change)?;
                updated.push(0);
                continue;
            }
            Some("importRoutine") => {
                updated.push(super::routine_import::import(&transaction, change)?);
                continue;
            }
            Some("saveTemplate") => {
                updated.push(super::template_store::save(&transaction, change)?);
                continue;
            }
            Some("deleteTemplate") => {
                updated.extend(super::template_store::delete(&transaction, change)?);
                continue;
            }
            Some("saveProfile") => {
                save_profile(&transaction, &change["fields"])?;
                updated.push(0);
                continue;
            }
            Some("saveWeight") => {
                updated.push(save_weight(&transaction, date, &change["fields"])?);
                continue;
            }
            Some("addExercise") => {
                let id = change["id"]
                    .as_i64()
                    .filter(|id| *id > 0)
                    .ok_or("invalid_id")?;
                let changed = transaction
                    .execute(
                        "INSERT INTO workout_tasks
                     (date, templateId, name, category, bodyPart, equipment, sets, isCompleted,
                      target, actualWeight, isUnilateral, logType, imageUri)
                     SELECT ?1, id, name, category, bodyPart, equipment, ?2, 0,
                            defaultTarget, '', isUnilateral, logType, NULL
                     FROM exercise_templates WHERE id = ?3 AND isDeleted = 0",
                        params![date, INITIAL_SETS, id],
                    )
                    .map_err(|_| "insert_task_failed")?;
                if changed != 1 {
                    return Err("template_not_found");
                }
                updated.push(transaction.last_insert_rowid());
                continue;
            }
            Some("routine") => {
                let changed = transaction
                    .execute(
                        "INSERT INTO workout_tasks
                     (date, templateId, name, category, bodyPart, equipment, sets, isCompleted,
                      target, actualWeight, isUnilateral, logType, imageUri)
                     SELECT ?1, templateId, name, category, bodyPart, equipment, ?2, 0,
                            target, '', isUnilateral, logType, NULL
                     FROM weekly_routine
                     WHERE dayOfWeek = ((CAST(strftime('%w', ?1) AS INTEGER) + 6) % 7) + 1
                     ORDER BY id",
                        params![date, INITIAL_SETS],
                    )
                    .map_err(|_| "apply_routine_failed")?;
                if changed > 0 {
                    updated.push(transaction.last_insert_rowid());
                }
                continue;
            }
            _ => {}
        }

        let id = change["id"]
            .as_i64()
            .filter(|id| *id > 0)
            .ok_or("invalid_id")?;
        let current = transaction.query_row(
            "SELECT sets, isCompleted, logType, category, target FROM workout_tasks WHERE id = ?1 AND date = ?2",
            params![id, date],
            |row| Ok((row.get::<_, String>(0)?, row.get::<_, bool>(1)?,
                row.get::<_, i64>(2)?, row.get::<_, String>(3)?, row.get::<_, String>(4)?)),
        ).optional().map_err(|_| "query_failed")?;
        let Some((sets, completed, log_type, category, target)) = current else {
            continue;
        };
        if change["action"] == "removeTask" {
            transaction
                .execute(
                    "DELETE FROM workout_tasks WHERE id = ?1 AND date = ?2",
                    params![id, date],
                )
                .map_err(|_| "delete_failed")?;
            updated.push(id);
            continue;
        }
        let parsed: Value = serde_json::from_str(&sets).map_err(|_| "invalid_stored_sets")?;
        let action = json!({
            "action": change["action"], "index": change["index"], "edits": change["edits"], "value": change["value"],
            "sets": parsed, "completed": completed, "logType": log_type, "category": category, "target": target,
        });
        let result: Value = serde_json::from_str(&super::workout::execute(&action.to_string())?)
            .map_err(|_| "invalid_workout_result")?;
        let next_completed = result["completed"]
            .as_bool()
            .ok_or("invalid_workout_result")?;
        if parsed == result["sets"] && completed == next_completed {
            continue;
        }
        transaction
            .execute(
                "UPDATE workout_tasks SET sets = ?1, isCompleted = ?2 WHERE id = ?3 AND date = ?4",
                params![result["sets"].to_string(), next_completed, id, date],
            )
            .map_err(|_| "update_failed")?;
        updated.push(id);
    }
    transaction.commit().map_err(|_| "commit_failed")?;
    Ok(json!(updated).to_string())
}
