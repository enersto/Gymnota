use rusqlite::{params, Connection, TransactionBehavior};
use serde_json::{json, Value};
use std::time::Duration;

pub fn execute(input: &str) -> Result<String, &'static str> {
    let request: Value = serde_json::from_str(input).map_err(|_| "invalid_bootstrap")?;
    let path = request["path"].as_str().ok_or("missing_database_path")?;
    let mut database = Connection::open(path).map_err(|_| "bootstrap_open_failed")?;
    database
        .busy_timeout(Duration::from_secs(5))
        .map_err(|_| "bootstrap_timeout_failed")?;
    let transaction = database
        .transaction_with_behavior(TransactionBehavior::Immediate)
        .map_err(|_| "bootstrap_transaction_failed")?;
    let version: i64 = transaction
        .pragma_query_value(None, "user_version", |row| row.get(0))
        .map_err(|_| "bootstrap_version_failed")?;
    if version != 0 {
        transaction
            .commit()
            .map_err(|_| "bootstrap_commit_failed")?;
        drop(database);
        if version != 19 {
            super::migrations::migrate(path)?;
        }
        return Ok(json!({"created": false, "version": 19}).to_string());
    }
    let tables: i64 = transaction.query_row(
        "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name NOT IN ('android_metadata','room_master_table','sqlite_sequence')",
        [], |row| row.get(0),
    ).map_err(|_| "bootstrap_schema_failed")?;
    if tables != 0 {
        return Err("unversioned_database_has_data");
    }
    transaction
        .execute_batch(include_str!("schema_v19.sql"))
        .map_err(|_| "bootstrap_create_failed")?;
    transaction.execute(
        "INSERT INTO app_settings(id,themeId,languageCode,age,height,gender,aiProvider,aiApiKey,aiModel,aiBaseUrl,useLocalAiCache,maxHistoryLimit)
         VALUES(0,1,'zh',0,0,0,'OpenAI','','gpt-3.5-turbo','',1,10)", [],
    ).map_err(|_| "bootstrap_profile_failed")?;
    for (index, kind) in [
        "CORE",
        "CORE",
        "ACTIVE_REST",
        "CORE",
        "CORE",
        "LIGHT",
        "REST",
    ]
    .iter()
    .enumerate()
    {
        transaction
            .execute(
                "INSERT INTO schedule_config(dayOfWeek,dayType) VALUES(?1,?2)",
                params![index as i64 + 1, kind],
            )
            .map_err(|_| "bootstrap_schedule_failed")?;
    }
    for template in request["templates"]
        .as_array()
        .ok_or("missing_seed_templates")?
    {
        let text = |key: &str| template[key].as_str().unwrap_or("");
        let id = template["id"].as_i64().filter(|id| *id != 0);
        transaction.execute(
            "INSERT INTO exercise_templates(id,name,defaultTarget,category,bodyPart,equipment,isDeleted,isUnilateral,logType,instruction,imageUri,animationUri)
             VALUES(?1,?2,?3,?4,?5,?6,?7,?8,?9,?10,?11,?12)",
            params![id,text("name"),text("defaultTarget"),text("category"),text("bodyPart"),text("equipment"),
                template["isDeleted"].as_bool().unwrap_or(false),template["isUnilateral"].as_bool().unwrap_or(false),
                template["logType"].as_i64().unwrap_or(0),text("instruction"),
                template["imageUri"].as_str(),template["animationUri"].as_str()],
        ).map_err(|_| "bootstrap_template_failed")?;
    }
    transaction
        .pragma_update(None, "user_version", 19)
        .map_err(|_| "bootstrap_version_write_failed")?;
    transaction
        .commit()
        .map_err(|_| "bootstrap_commit_failed")?;
    Ok(json!({"created": true, "version": 19}).to_string())
}
