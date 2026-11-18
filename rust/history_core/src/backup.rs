use rusqlite::{Connection, OpenFlags};
use serde_json::{json, Value};
use std::time::Duration;

fn validate(connection: &Connection) -> Result<i64, &'static str> {
    let version: i64 = connection
        .pragma_query_value(None, "user_version", |row| row.get(0))
        .map_err(|_| "backup_version_failed")?;
    if !(7..=19).contains(&version) {
        return Err("unsupported_backup_version");
    }
    let mut check = connection
        .prepare("PRAGMA integrity_check")
        .map_err(|_| "backup_integrity_failed")?;
    let mut rows = check.query([]).map_err(|_| "backup_integrity_failed")?;
    while let Some(row) = rows.next().map_err(|_| "backup_integrity_failed")? {
        if row
            .get::<_, String>(0)
            .map_err(|_| "backup_integrity_failed")?
            != "ok"
        {
            return Err("backup_integrity_failed");
        }
    }
    for (table, columns) in [
        ("workout_tasks", "id,date,templateId,name,sets,isCompleted"),
        ("exercise_templates", "id,name,defaultTarget,category"),
        ("weekly_routine", "id,dayOfWeek,templateId,name,target"),
        ("weight_records", "id,date,weight"),
        ("app_settings", "id,themeId,languageCode"),
        ("schedule_config", "dayOfWeek,dayType"),
    ] {
        connection
            .prepare(&format!("SELECT {columns} FROM {table} LIMIT 0"))
            .map_err(|_| "backup_schema_mismatch")?;
    }
    Ok(version)
}

pub fn execute(input: &str) -> Result<String, &'static str> {
    let request: Value = serde_json::from_str(input).map_err(|_| "invalid_backup_request")?;
    let source = request["source"].as_str().ok_or("missing_backup_source")?;
    if request["action"] == "migrate" {
        let version = super::migrations::migrate(source)?;
        return Ok(
            json!({"version": version.parse::<i64>().map_err(|_| "migration_result_failed")?})
                .to_string(),
        );
    }
    if request["action"] == "checkpoint" {
        let connection = Connection::open_with_flags(source, OpenFlags::SQLITE_OPEN_READ_WRITE)
            .map_err(|_| "backup_open_failed")?;
        connection.busy_timeout(Duration::from_secs(5)).map_err(|_| "backup_timeout_failed")?;
        let busy: i64 = connection.query_row("PRAGMA wal_checkpoint(TRUNCATE)", [], |row| row.get(0))
            .map_err(|_| "backup_checkpoint_failed")?;
        if busy != 0 { return Err("backup_checkpoint_busy"); }
        return Ok(json!({"checkpointed":true}).to_string());
    }
    let connection = Connection::open_with_flags(source, OpenFlags::SQLITE_OPEN_READ_ONLY)
        .map_err(|_| "backup_open_failed")?;
    connection
        .busy_timeout(Duration::from_secs(5))
        .map_err(|_| "backup_timeout_failed")?;
    match request["action"].as_str() {
        Some("validate") => Ok(json!({"version": validate(&connection)?}).to_string()),
        Some("snapshot") => {
            let destination = request["destination"]
                .as_str()
                .ok_or("missing_backup_destination")?;
            if std::path::Path::new(destination).exists() {
                return Err("backup_destination_exists");
            }
            connection
                .execute("VACUUM INTO ?1", [destination])
                .map_err(|_| "backup_snapshot_failed")?;
            let copy = Connection::open_with_flags(destination, OpenFlags::SQLITE_OPEN_READ_ONLY)
                .map_err(|_| "backup_copy_open_failed")?;
            Ok(json!({"version": validate(&copy)?}).to_string())
        }
        _ => Err("invalid_backup_action"),
    }
}
