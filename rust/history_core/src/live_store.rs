use rusqlite::{types::ValueRef, Connection, OpenFlags};
use serde_json::{Map, Value};
use std::time::Duration;

pub fn execute(input: &str) -> Result<String, &'static str> {
    let request: Value = serde_json::from_str(input).map_err(|_| "invalid_query")?;
    let path = request["path"].as_str().ok_or("missing_path")?;
    let limit = request["limit"].as_i64().unwrap_or(10);
    if !(1..=1000).contains(&limit) {
        return Err("invalid_limit");
    }
    let task_id = if request["query"] == "task" {
        Some(request["id"].as_i64().filter(|id| *id > 0).ok_or("invalid_task_id")?)
    } else { None };
    let (sql, date) = match request["query"].as_str() {
        Some("task") => ("SELECT * FROM workout_tasks WHERE id = ?1", None),
        Some("daily") => (
            "SELECT * FROM workout_tasks WHERE date = ?1",
            Some(
                request["date"]
                    .as_str()
                    .filter(|date| super::date_valid(date))
                    .ok_or("invalid_date")?,
            ),
        ),
        Some("aiHistory") => (
            "SELECT * FROM ai_chat_history ORDER BY timestamp DESC",
            None,
        ),
        Some("aiRecent") => (
            "SELECT * FROM ai_chat_history ORDER BY timestamp DESC LIMIT ?1",
            None,
        ),
        Some("aiMemory") => ("SELECT * FROM ai_memory WHERE id = 0", None),
        Some("providerConfigs") => ("SELECT * FROM ai_provider_configs", None),
        Some("profile") => ("SELECT * FROM app_settings WHERE id = 0", None),
        Some("schedules") => ("SELECT * FROM schedule_config", None),
        Some("routines") => ("SELECT * FROM weekly_routine", None),
        Some("templates") => ("SELECT * FROM exercise_templates WHERE isDeleted = 0", None),
        Some("completed") => (
            "SELECT * FROM workout_tasks WHERE isCompleted = 1 ORDER BY date ASC",
            None,
        ),
        Some("weights") => ("SELECT * FROM weight_records ORDER BY date ASC", None),
        Some("latestWeight") => (
            "SELECT * FROM weight_records ORDER BY date DESC LIMIT 1",
            None,
        ),
        _ => return Err("unknown_query"),
    };
    let database = Connection::open_with_flags(path, OpenFlags::SQLITE_OPEN_READ_ONLY)
        .map_err(|_| "database_open_failed")?;
    database
        .busy_timeout(Duration::from_secs(2))
        .map_err(|_| "database_timeout_failed")?;
    let mut statement = database.prepare(sql).map_err(|_| "query_schema_mismatch")?;
    let columns: Vec<String> = statement
        .column_names()
        .iter()
        .map(|name| name.to_string())
        .collect();
    let mut cursor = if request["query"] == "aiRecent" {
        statement.query([limit])
    } else if let Some(id) = task_id {
        statement.query([id])
    } else {
        match date {
            Some(date) => statement.query([date]),
            None => statement.query([]),
        }
    }
    .map_err(|_| "query_failed")?;
    let mut rows = Vec::new();
    while let Some(row) = cursor.next().map_err(|_| "row_failed")? {
        let mut record = Map::new();
        for (index, column) in columns.iter().enumerate() {
            let value = match row.get_ref(index).map_err(|_| "column_failed")? {
                ValueRef::Null => Value::Null,
                ValueRef::Integer(value)
                    if matches!(
                        column.as_str(),
                        "isCompleted" | "isUnilateral" | "isDeleted" | "useLocalAiCache"
                    ) =>
                {
                    Value::Bool(value != 0)
                }
                ValueRef::Integer(value) => Value::from(value),
                ValueRef::Real(value) => Value::from(value),
                ValueRef::Text(value) if column == "sets" => {
                    let text = std::str::from_utf8(value).map_err(|_| "invalid_sets_encoding")?;
                    if text.trim().is_empty() {
                        Value::Array(Vec::new())
                    } else {
                        serde_json::from_str::<Value>(text).map_err(|_| "invalid_stored_sets")?
                    }
                }
                ValueRef::Text(value) => Value::String(
                    std::str::from_utf8(value)
                        .map_err(|_| "invalid_text")?
                        .into(),
                ),
                ValueRef::Blob(_) => return Err("unexpected_blob"),
            };
            record.insert(column.clone(), value);
        }
        rows.push(Value::Object(record));
    }
    Ok(Value::Array(rows).to_string())
}
