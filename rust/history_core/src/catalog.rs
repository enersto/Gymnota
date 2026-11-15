//! Read-only bundled catalogue. Paths come only from the Android installer, never Dart.
use rusqlite::{params, Connection, OpenFlags};
use serde_json::{json, Value};

pub fn query_json(text: &str) -> Result<String, &'static str> {
    let input: Value = serde_json::from_str(text).map_err(|_| "invalid_request")?;
    if input["operation"] == "personalSearch" {
        let query = input["query"].as_str().unwrap_or("").trim().to_lowercase();
        if query.len() > 4096 {
            return Err("query_too_long");
        }
        let entries = input["entries"].as_array().ok_or("missing_entries")?;
        let matches: Vec<i64> = entries
            .iter()
            .filter_map(|row| {
                let name = row["name"].as_str()?;
                let id = row["id"].as_i64()?;
                name.to_lowercase().contains(&query).then_some(id)
            })
            .collect();
        return Ok(json!({ "schemaVersion": 1, "matches": matches }).to_string());
    }
    let path = input["path"].as_str().ok_or("missing_path")?;
    let language = input["language"].as_str().unwrap_or("en");
    let language = language.split(['-', '_']).next().unwrap_or("en");
    let query = input["query"].as_str().unwrap_or("").trim().to_lowercase();
    let part = input["part"].as_str();
    if query.len() > 4096 {
        return Err("query_too_long");
    }
    let db = Connection::open_with_flags(
        path,
        OpenFlags::SQLITE_OPEN_READ_ONLY | OpenFlags::SQLITE_OPEN_NO_MUTEX,
    )
    .map_err(|_| "catalog_open_failed")?;
    let mut statement = db
        .prepare(
            "SELECT e.source_id, COALESCE(NULLIF(t.display_name, ''), e.canonical_name),
        e.bodyPart, e.equipment, COALESCE(NULLIF(t.instruction, ''), en.instruction, ''),
        e.category, e.logType, e.isUnilateral, e.defaultTarget, e.gif_path, e.canonical_name
        FROM catalog_exercise e
        LEFT JOIN catalog_translation t ON t.source_id=e.source_id AND t.locale=?1
        LEFT JOIN catalog_translation en ON en.source_id=e.source_id AND en.locale='en'
        WHERE (?2 IS NULL OR COALESCE(e.bodyPart,'part_other')=?2)
        ORDER BY e.canonical_name,e.source_id",
        )
        .map_err(|_| "catalog_schema_invalid")?;
    let rows = statement.query_map(params![language, part], |r| {
        Ok(json!({"id":r.get::<_,String>(0)?,"name":r.get::<_,String>(1)?,
            "bodyPart":r.get::<_,Option<String>>(2)?.unwrap_or("part_other".into()),
            "equipment":r.get::<_,Option<String>>(3)?.unwrap_or("equip_other".into()),
            "instruction":r.get::<_,String>(4)?,"category":r.get::<_,Option<String>>(5)?,
            "logType":r.get::<_,Option<i64>>(6)?,"unilateral":r.get::<_,Option<i64>>(7)?.map(|v|v!=0),
            "target":r.get::<_,Option<String>>(8)?,"path":r.get::<_,String>(9)?,"canonical":r.get::<_,String>(10)?}))
    }).map_err(|_| "catalog_query_failed")?;
    let mut entries = Vec::new();
    for row in rows {
        let row = row.map_err(|_| "catalog_row_invalid")?;
        // Unicode lowercase matching matches the former Dart contains search, without SQL wildcard semantics.
        if query.is_empty()
            || ["name", "canonical", "id", "instruction"]
                .iter()
                .any(|key| {
                    row[key]
                        .as_str()
                        .unwrap_or("")
                        .to_lowercase()
                        .contains(&query)
                })
        {
            entries.push(row);
        }
    }
    serde_json::to_string(&json!({"schemaVersion":1,"engine":"rust","entries":entries}))
        .map_err(|_| "serialization_failed")
}
