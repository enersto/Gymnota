use rusqlite::{params, OptionalExtension, Transaction};
use serde_json::Value;

pub fn save(transaction: &Transaction<'_>, change: &Value) -> Result<i64, &'static str> {
    let id = change["id"]
        .as_i64()
        .filter(|id| *id >= 0)
        .ok_or("invalid_template_id")?;
    let fields = &change["fields"];
    let text = |key: &str| fields[key].as_str().ok_or("invalid_template_text");
    let name = text("name")?;
    if name.trim().is_empty() {
        return Err("invalid_template_name");
    }
    let category = text("category")?;
    let target = text("target")?;
    let body_part = text("bodyPart")?;
    let equipment = text("equipment")?;
    let instruction = text("instruction")?;
    let unilateral = fields["isUnilateral"]
        .as_bool()
        .ok_or("invalid_unilateral")?;
    let log_type = fields["logType"].as_i64().ok_or("invalid_log_type")?;
    let photo = fields["imageUri"].as_str();
    let animation = fields["animationUri"].as_str();
    if id == 0 {
        transaction.execute(
            "INSERT INTO exercise_templates (name, defaultTarget, category, bodyPart, equipment,
              isDeleted, isUnilateral, logType, instruction, imageUri, animationUri)
             VALUES (?1, ?2, ?3, ?4, ?5, 0, ?6, ?7, ?8, ?9, ?10)",
            params![name, target, category, body_part, equipment, unilateral, log_type, instruction, photo, animation],
        ).map_err(|_| "template_insert_failed")?;
        return Ok(transaction.last_insert_rowid());
    }
    let old_photo = transaction
        .query_row(
            "SELECT imageUri FROM exercise_templates WHERE id = ?1 AND isDeleted = 0",
            [id],
            |row| row.get::<_, Option<String>>(0),
        )
        .optional()
        .map_err(|_| "template_read_failed")?
        .ok_or("template_not_found")?;
    transaction
        .execute(
            "UPDATE exercise_templates SET name = ?1, defaultTarget = ?2, category = ?3,
         bodyPart = ?4, equipment = ?5, isUnilateral = ?6, logType = ?7, instruction = ?8,
         imageUri = ?9, animationUri = ?10 WHERE id = ?11",
            params![
                name,
                target,
                category,
                body_part,
                equipment,
                unilateral,
                log_type,
                instruction,
                photo,
                animation,
                id
            ],
        )
        .map_err(|_| "template_update_failed")?;
    if let Some(old) = old_photo.filter(|uri| {
        !uri.trim().is_empty()
            && !uri.starts_with("file:///android_asset/catalog/")
            && photo.is_none()
    }) {
        transaction
            .execute(
                "UPDATE workout_tasks SET imageUri = NULL WHERE templateId = ?1 AND imageUri = ?2",
                params![id, old],
            )
            .map_err(|_| "task_photo_clear_failed")?;
        transaction
            .execute(
                "UPDATE weekly_routine SET imageUri = NULL WHERE templateId = ?1 AND imageUri = ?2",
                params![id, old],
            )
            .map_err(|_| "routine_photo_clear_failed")?;
    }
    Ok(id)
}

pub fn delete(transaction: &Transaction<'_>, change: &Value) -> Result<Option<i64>, &'static str> {
    let id = change["id"]
        .as_i64()
        .filter(|id| *id > 0)
        .ok_or("invalid_template_id")?;
    let affected = transaction
        .execute("DELETE FROM exercise_templates WHERE id = ?1", [id])
        .map_err(|_| "template_delete_failed")?;
    Ok((affected != 0).then_some(id))
}
