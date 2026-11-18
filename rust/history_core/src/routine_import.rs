use rusqlite::{params, OptionalExtension, Transaction};
use serde_json::Value;
use std::collections::BTreeSet;

// CSV fields may contain commas, escaped quotes and quoted line breaks.
fn records(csv: &str) -> Result<Vec<Vec<String>>, &'static str> {
    let mut rows = Vec::new();
    let mut row = Vec::new();
    let mut field = String::new();
    let mut quoted = false;
    let mut closed = false;
    let mut chars = csv.trim_start_matches('\u{feff}').chars().peekable();
    while let Some(ch) = chars.next() {
        if quoted {
            if ch == '"' {
                if chars.peek() == Some(&'"') {
                    chars.next();
                    field.push('"');
                } else {
                    quoted = false;
                    closed = true;
                }
            } else {
                field.push(ch);
            }
            continue;
        }
        match ch {
            '"' if field.trim().is_empty() && !closed => {
                field.clear();
                quoted = true;
            }
            ',' => {
                row.push(field.trim().to_owned());
                field.clear();
                closed = false;
            }
            '\r' | '\n' => {
                if ch == '\r' && chars.peek() == Some(&'\n') {
                    chars.next();
                }
                row.push(field.trim().to_owned());
                field.clear();
                closed = false;
                if row.iter().any(|part| !part.is_empty()) {
                    rows.push(row);
                    row = Vec::new();
                } else {
                    row.clear();
                }
            }
            ch if !closed || ch.is_whitespace() => {
                field.push(ch);
            }
            _ => return Err("invalid_csv_quotes"),
        }
    }
    if quoted {
        return Err("unclosed_csv_quote");
    }
    row.push(field.trim().to_owned());
    if row.iter().any(|part| !part.is_empty()) {
        rows.push(row);
    }
    Ok(rows)
}

struct Item {
    day: i64,
    name: String,
    category: String,
    target: String,
    body: String,
    equipment: String,
    unilateral: bool,
    log_type: i64,
    instruction: String,
}

fn parse(csv: &str) -> Result<Vec<Item>, &'static str> {
    let rows = records(csv)?;
    let mut items = Vec::new();
    for row in rows.into_iter().skip(1) {
        if row.len() < 4 {
            return Err("missing_csv_columns");
        }
        let day = row[0]
            .parse::<i64>()
            .ok()
            .filter(|day| (1..=7).contains(day))
            .ok_or("invalid_csv_day")?;
        if row[1].is_empty() {
            return Err("missing_csv_name");
        }
        let inferred = if row[2].eq_ignore_ascii_case("CARDIO")
            || row[2].contains("有氧")
            || row[2].eq_ignore_ascii_case("CORE")
            || row[2].contains("核心")
        {
            1
        } else {
            0
        };
        let log_type = match row.get(7).filter(|value| !value.is_empty()) {
            Some(value) => value
                .parse::<i64>()
                .ok()
                .filter(|value| (0..=2).contains(value))
                .ok_or("invalid_csv_log_type")?,
            None => inferred,
        };
        let text = |index: usize, fallback: &str| {
            row.get(index)
                .filter(|value| !value.is_empty())
                .cloned()
                .unwrap_or_else(|| fallback.into())
        };
        items.push(Item {
            day,
            name: row[1].clone(),
            category: row[2].clone(),
            target: row[3].clone(),
            body: text(4, "part_other"),
            equipment: text(5, "equip_other"),
            unilateral: row
                .get(6)
                .is_some_and(|value| value.eq_ignore_ascii_case("true")),
            log_type,
            instruction: text(8, ""),
        });
    }
    if items.is_empty() {
        return Err("empty_csv");
    }
    Ok(items)
}

pub fn import(transaction: &Transaction<'_>, change: &Value) -> Result<i64, &'static str> {
    let items = parse(change["csv"].as_str().ok_or("missing_csv")?)?;
    let days: BTreeSet<_> = items.iter().map(|item| item.day).collect();
    for day in days {
        transaction
            .execute("DELETE FROM weekly_routine WHERE dayOfWeek = ?1", [day])
            .map_err(|_| "routine_replace_failed")?;
    }
    for item in &items {
        let existing = transaction.query_row(
            "SELECT id FROM exercise_templates WHERE name = ?1 AND isDeleted = 0 ORDER BY id LIMIT 1",
            [&item.name], |row| row.get::<_, i64>(0),
        ).optional().map_err(|_| "template_lookup_failed")?;
        let id = match existing {
            Some(id) => id,
            None => {
                transaction.execute(
                    "INSERT INTO exercise_templates (name, defaultTarget, category, bodyPart, equipment,
                     isDeleted, isUnilateral, logType, instruction, imageUri, animationUri)
                     VALUES (?1, ?2, ?3, ?4, ?5, 0, ?6, ?7, ?8, NULL, NULL)",
                    params![item.name, item.target, item.category, item.body, item.equipment, item.unilateral, item.log_type, item.instruction],
                ).map_err(|_| "template_insert_failed")?;
                transaction.last_insert_rowid()
            }
        };
        transaction.execute(
            "INSERT INTO weekly_routine (dayOfWeek, templateId, name, target, category, bodyPart,
             equipment, isUnilateral, logType, imageUri) VALUES (?1,?2,?3,?4,?5,?6,?7,?8,?9,NULL)",
            params![item.day, id, item.name, item.target, item.category, item.body, item.equipment, item.unilateral, item.log_type],
        ).map_err(|_| "routine_insert_failed")?;
    }
    Ok(items.len() as i64)
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn import_failure_restores_replaced_days_and_created_templates() {
        let mut database = rusqlite::Connection::open_in_memory().unwrap();
        database.execute_batch(
            "CREATE TABLE exercise_templates (id INTEGER PRIMARY KEY, name TEXT, defaultTarget TEXT,
             category TEXT, bodyPart TEXT, equipment TEXT, isDeleted INTEGER, isUnilateral INTEGER,
             logType INTEGER, instruction TEXT, imageUri TEXT, animationUri TEXT);
             CREATE TABLE weekly_routine (id INTEGER PRIMARY KEY, dayOfWeek INTEGER, templateId INTEGER,
             name TEXT, target TEXT, category TEXT, bodyPart TEXT, equipment TEXT,
             isUnilateral INTEGER, logType INTEGER, imageUri TEXT);
             INSERT INTO weekly_routine (id,dayOfWeek,name) VALUES(1,2,'original');
             CREATE TRIGGER fail BEFORE INSERT ON weekly_routine WHEN NEW.name = 'FAIL'
             BEGIN SELECT RAISE(ABORT,'injected'); END;"
        ).unwrap();
        {
            let transaction = database.transaction().unwrap();
            let change = serde_json::json!({"csv": "Day,Name,Category,Target\n2,New,STRENGTH,12\n2,FAIL,STRENGTH,12"});
            assert!(import(&transaction, &change).is_err());
        }
        let name: String = database
            .query_row("SELECT name FROM weekly_routine", [], |row| row.get(0))
            .unwrap();
        assert_eq!(name, "original");
        let count: i64 = database
            .query_row("SELECT COUNT(*) FROM exercise_templates", [], |row| {
                row.get(0)
            })
            .unwrap();
        assert_eq!(count, 0);
    }

    #[test]
    fn parses_unicode_quotes_linebreaks_and_legacy_optional_fields() {
        let csv = "\u{feff}Day,Name,Category,Target,BodyPart,Equipment,IsUnilateral,LogType,Instruction\r\n1,\"My, curl\",STRENGTH,12,,,TRUE,2,\"Say \"\"hold\"\"\nslowly\"\r\n2,核心,核心,20s";
        let rows = parse(csv).unwrap();
        assert_eq!(rows.len(), 2);
        assert_eq!(rows[0].name, "My, curl");
        assert_eq!(rows[0].instruction, "Say \"hold\"\nslowly");
        assert!(rows[0].unilateral);
        assert_eq!(rows[0].body, "part_other");
        assert_eq!(rows[1].log_type, 1);
    }
    #[test]
    fn malformed_input_cannot_start_a_replacement() {
        for csv in [
            "header",
            "header\n8,X,STRENGTH,12",
            "header\n1,,STRENGTH,12",
            "header\n1,\"unfinished,STRENGTH,12",
            "header\n1,X,STRENGTH,12,,,,7",
        ] {
            assert!(parse(csv).is_err());
        }
    }
}
