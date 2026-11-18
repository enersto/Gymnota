use rusqlite::{params, Transaction};
use serde_json::Value;

pub fn save_record(transaction: &Transaction<'_>, change: &Value) -> Result<i64, &'static str> {
    let fields = &change["fields"];
    let timestamp = fields["timestamp"].as_i64().ok_or("invalid_timestamp")?;
    let role = fields["role"].as_str().ok_or("missing_role")?;
    let content = fields["content"].as_str().ok_or("missing_content")?;
    transaction
        .execute(
            "INSERT INTO ai_chat_history(timestamp, role, content, userGoal, modelUsed)
         VALUES (?1, ?2, ?3, ?4, ?5)",
            params![
                timestamp,
                role,
                content,
                fields["userGoal"].as_str(),
                fields["modelUsed"].as_str()
            ],
        )
        .map_err(|_| "ai_history_insert_failed")?;
    Ok(transaction.last_insert_rowid())
}

pub fn delete_record(
    transaction: &Transaction<'_>,
    change: &Value,
) -> Result<Option<i64>, &'static str> {
    let id = change["id"]
        .as_i64()
        .filter(|id| *id > 0)
        .ok_or("invalid_history_id")?;
    let changed = transaction
        .execute("DELETE FROM ai_chat_history WHERE id = ?1", [id])
        .map_err(|_| "ai_history_delete_failed")?;
    Ok((changed > 0).then_some(id))
}

pub fn save_memory(transaction: &Transaction<'_>, change: &Value) -> Result<(), &'static str> {
    let fields = &change["fields"];
    let text = fields["longTermContext"].as_str().ok_or("missing_memory")?;
    let timestamp = fields["lastSyncTime"]
        .as_i64()
        .ok_or("invalid_memory_timestamp")?;
    transaction.execute(
        "INSERT INTO ai_memory(id, longTermContext, lastSyncTime) VALUES(0, ?1, ?2)
         ON CONFLICT(id) DO UPDATE SET longTermContext=excluded.longTermContext, lastSyncTime=excluded.lastSyncTime",
        params![text,timestamp],
    ).map_err(|_| "ai_memory_write_failed")?;
    Ok(())
}
