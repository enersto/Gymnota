use serde_json::{json, Value};

pub fn execute(input: &str) -> Result<String, &'static str> {
    let request: Value = serde_json::from_str(input).map_err(|_| "invalid_workout")?;
    let completed = request["completed"].as_bool().ok_or("invalid_completion")?;
    let mut result_completed = completed;
    let mut sets = request["sets"].as_array().ok_or("invalid_sets")?.clone();
    match request["action"].as_str() {
        Some("timerFinished") => {
            let index = request["index"].as_u64().ok_or("invalid_set_index")? as usize;
            if let Some(set) = sets.get_mut(index) {
                set["weightOrDuration"] =
                    json!(request["value"].as_str().ok_or("missing_duration")?);
                set["reps"] = json!("Done");
            }
            if sets.iter().all(|set| {
                !set["weightOrDuration"]
                    .as_str()
                    .unwrap_or("")
                    .trim()
                    .is_empty()
            }) {
                result_completed = true;
            }
        }
        Some("check") => {
            result_completed = !completed;
            let log_type = request["logType"].as_i64().ok_or("invalid_log_type")?;
            let category = request["category"].as_str().unwrap_or("");
            if result_completed
                && (matches!(log_type, 1 | 2)
                    || category.contains("有氧")
                    || category.contains("核心"))
            {
                let value = request["target"].as_str().unwrap_or("").replace(' ', "");
                for set in &mut sets {
                    let weight = set["weightOrDuration"].as_str().unwrap_or("");
                    let reps = set["reps"].as_str().unwrap_or("");
                    if weight.trim().is_empty() && reps.trim().is_empty() {
                        set["weightOrDuration"] = json!(value);
                        set["reps"] = if log_type == 1 {
                            json!("Done")
                        } else {
                            json!(value)
                        };
                    }
                }
            }
        }
        Some("addSet") => {
            let mut set = sets
                .last()
                .cloned()
                .unwrap_or_else(|| json!({"weightOrDuration": "", "reps": ""}));
            set["setNumber"] = json!(sets.len() + 1);
            sets.push(set);
        }
        Some("removeSet") => {
            if let Some(index) = request["index"]
                .as_u64()
                .and_then(|index| usize::try_from(index).ok())
            {
                if index < sets.len() {
                    sets.remove(index);
                }
            }
        }
        Some("setFields") => {
            for edit in request["edits"].as_array().ok_or("invalid_edits")? {
                let Some(index) = edit["index"]
                    .as_u64()
                    .and_then(|index| usize::try_from(index).ok())
                else {
                    continue;
                };
                let Some(set) = sets.get_mut(index) else {
                    continue;
                };
                let field = match edit["field"].as_str() {
                    Some("value") => "weightOrDuration",
                    Some("reps") => "reps",
                    Some("rightWeight") => "rightWeight",
                    Some("rightReps") => "rightReps",
                    _ => continue,
                };
                set[field] = json!(edit["text"].as_str().unwrap_or(""));
            }
        }
        _ => return Err("unknown_workout_action"),
    }
    Ok(json!({"completed": result_completed, "sets": sets}).to_string())
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn applies_ordered_edits_and_preserves_right_side_when_adding_sets() {
        let mut task = json!({"action": "setFields", "completed": false,
            "sets": [{"setNumber": 1, "weightOrDuration": "20", "reps": "12", "rightWeight": "19"}],
            "edits": [{"index": 0, "field": "rightReps", "text": "8"}, {"index": 0, "field": "rightReps", "text": "9"}]});
        let edited: Value = serde_json::from_str(&execute(&task.to_string()).unwrap()).unwrap();
        task["action"] = json!("addSet");
        task["sets"] = edited["sets"].clone();
        let added: Value = serde_json::from_str(&execute(&task.to_string()).unwrap()).unwrap();
        assert_eq!(
            added["sets"][1],
            json!({"setNumber": 2, "weightOrDuration": "20", "reps": "12", "rightWeight": "19", "rightReps": "9"})
        );
    }

    #[test]
    fn completing_timed_tasks_fills_only_empty_sets() {
        let mut task = json!({"action": "check", "completed": false, "category": "CARDIO", "logType": 1, "target":" 8 min ",
            "sets": [{"setNumber": 1, "weightOrDuration": "", "reps": ""}, {"setNumber": 2, "weightOrDuration": "4", "reps": ""}]});
        let output: Value = serde_json::from_str(&execute(&task.to_string()).unwrap()).unwrap();
        assert_eq!(output["completed"], true);
        assert_eq!(output["sets"][0]["weightOrDuration"], "8min");
        assert_eq!(output["sets"][0]["reps"], "Done");
        assert_eq!(output["sets"][1]["weightOrDuration"], "4");
        task["completed"] = json!(true);
        task["sets"] = output["sets"].clone();
        let undo: Value = serde_json::from_str(&execute(&task.to_string()).unwrap()).unwrap();
        assert_eq!(undo["completed"], false);
        assert_eq!(undo["sets"], output["sets"]);
    }
}
