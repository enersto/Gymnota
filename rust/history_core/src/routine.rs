use rusqlite::{Connection, OpenFlags, TransactionBehavior};
use serde_json::{json, Value};
use std::time::Duration;

const ADD_SQL: &str = "INSERT INTO weekly_routine
    (dayOfWeek, templateId, name, target, category, bodyPart, equipment, isUnilateral, logType)
    SELECT ?1, id, name, defaultTarget, category, bodyPart, equipment, isUnilateral, logType
    FROM exercise_templates WHERE id = ?2 AND isDeleted = 0";
const DELETE_SQL: &str = "DELETE FROM weekly_routine WHERE dayOfWeek = ?1 AND id = ?2";

fn mutation(request: &Value) -> Result<(&'static str, i64, i64), &'static str> {
    let day = request["day"]
        .as_i64()
        .filter(|d| (1..=7).contains(d))
        .ok_or("invalid_day")?;
    let id = request["id"]
        .as_i64()
        .filter(|id| *id > 0)
        .ok_or("invalid_id")?;
    let sql = match request["action"].as_str() {
        Some("add") => ADD_SQL,
        Some("delete") => DELETE_SQL,
        _ => return Err("invalid_mutation"),
    };
    Ok((sql, day, id))
}

fn apply_mutation(connection: &Connection, request: &Value) -> Result<i64, &'static str> {
    let (sql, day, id) = mutation(request)?;
    let changed = connection
        .execute(sql, [day, id])
        .map_err(|_| "mutation_failed")?;
    if sql == ADD_SQL && changed != 1 {
        return Err("template_not_found");
    }
    Ok(day)
}

fn list(connection: &Connection, day: i64) -> Result<Vec<Value>, &'static str> {
    let mut statement = connection
        .prepare("SELECT id, name, target FROM weekly_routine WHERE dayOfWeek = ?1 ORDER BY id")
        .map_err(|_| "routine_schema_mismatch")?;
    let rows = statement
        .query_map([day], |row| {
            Ok(json!({
                "id": row.get::<_, i64>(0)?,
                "name": row.get::<_, String>(1)?,
                "target": row.get::<_, String>(2)?,
            }))
        })
        .map_err(|_| "routine_query_failed")?;
    rows.collect::<Result<Vec<_>, _>>()
        .map_err(|_| "routine_row_invalid")
}

fn open(path: &str, flags: OpenFlags) -> Result<Connection, &'static str> {
    let database = Connection::open_with_flags(path, flags).map_err(|_| "database_open_failed")?;
    database
        .busy_timeout(Duration::from_secs(2))
        .map_err(|_| "database_config_failed")?;
    Ok(database)
}

/// Exercise the production SQL against a disposable SQLite copy.
pub fn mutate_copy(input: &str) -> Result<String, &'static str> {
    let request: Value = serde_json::from_str(input).map_err(|_| "invalid_request")?;
    let path = request["path"].as_str().ok_or("missing_path")?;
    let operations = request["mutations"].as_array().ok_or("missing_mutations")?;
    let mut db = open(path, OpenFlags::SQLITE_OPEN_READ_WRITE)?;
    let transaction = db
        .transaction_with_behavior(TransactionBehavior::Immediate)
        .map_err(|_| "transaction_failed")?;
    for operation in operations {
        apply_mutation(&transaction, operation)?;
    }
    transaction.commit().map_err(|_| "commit_failed")?;
    Ok("true".into())
}

pub fn execute(input: &str) -> Result<String, &'static str> {
    let request: Value = serde_json::from_str(input).map_err(|_| "invalid_request")?;
    match request["action"].as_str() {
        Some("add" | "delete") => {
            let path = request["path"].as_str().ok_or("missing_path")?;
            let mut db = open(path, OpenFlags::SQLITE_OPEN_READ_WRITE)?;
            let transaction = db
                .transaction_with_behavior(TransactionBehavior::Immediate)
                .map_err(|_| "transaction_failed")?;
            let day = apply_mutation(&transaction, &request)?;
            let rows = list(&transaction, day)?;
            transaction.commit().map_err(|_| "commit_failed")?;
            Ok(json!(rows).to_string())
        }
        Some("list") => {
            let path = request["path"].as_str().ok_or("missing_path")?;
            let day = request["day"]
                .as_i64()
                .filter(|d| (1..=7).contains(d))
                .ok_or("invalid_day")?;
            let db = open(path, OpenFlags::SQLITE_OPEN_READ_ONLY)?;
            Ok(json!(list(&db, day)?).to_string())
        }
        Some("apply") => {
            let date = request["date"]
                .as_str()
                .filter(|d| super::date_valid(d))
                .ok_or("invalid_date")?;
            let rows = request["rows"].as_array().ok_or("missing_rows")?;
            let drafts = rows
                .iter()
                .map(|row| {
                    let draft = row.as_object().ok_or("routine_row_invalid")?;
                    let mut draft = draft.clone();
                    draft.insert("date".into(), json!(date));
                    draft.insert(
                        "sets".into(),
                        json!([{"setNumber": 1, "weightOrDuration": "", "reps": ""}]),
                    );
                    Ok(Value::Object(draft))
                })
                .collect::<Result<Vec<_>, &'static str>>()?;
            Ok(json!(drafts).to_string())
        }
        _ => Err("invalid_action"),
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn application_preserves_personal_fields_and_initializes_an_empty_set() {
        let request = json!({"action": "apply", "date": "2026-11-17",
            "rows": [{"templateId": 42, "name": "个人动作", "isUnilateral": true, "logType": 2}]});
        let output: Value = serde_json::from_str(&execute(&request.to_string()).unwrap()).unwrap();
        assert_eq!(output[0]["name"], "个人动作");
        assert_eq!(output[0]["templateId"], 42);
        assert_eq!(output[0]["isUnilateral"], true);
        assert_eq!(output[0]["logType"], 2);
        assert_eq!(output[0]["sets"][0]["reps"], "");
        assert_eq!(output[0]["date"], "2026-11-17");
    }
}
