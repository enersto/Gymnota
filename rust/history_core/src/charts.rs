//! Chart-compatible calculations. Kept separate from the validated summary metrics.
use super::{date_valid, legacy_number, string};
use serde_json::{json, Value};
use std::collections::BTreeMap;

fn number(v: &Value) -> f32 {
    v.as_f64().filter(|v| v.is_finite()).unwrap_or(0.) as f32
}
fn duration(s: &str) -> f32 {
    let n = legacy_number(s) as f32;
    let s = s.to_lowercase();
    if s.contains('h') {
        n * 60.
    } else if s.contains('s') && !s.contains('m') {
        n / 60.
    } else {
        n
    }
}
fn points(rows: &[(String, f32)], monthly: bool) -> Vec<Value> {
    let mut groups = BTreeMap::<String, Vec<f32>>::new();
    for (date, value) in rows {
        if !date_valid(date) || !value.is_finite() {
            continue;
        }
        let key = if monthly {
            format!("{}-01", &date[..7])
        } else {
            date.clone()
        };
        groups.entry(key).or_default().push(*value);
    }
    groups
        .into_iter()
        .map(|(date, values)| {
            let mean = (values.iter().map(|v| *v as f64).sum::<f64>() / values.len() as f64) as f32;
            json!({"date":date,"value":mean})
        })
        .collect()
}
fn series(rows: Vec<(String, f32)>) -> Value {
    json!({"daily":points(&rows,false),"monthly":points(&rows,true)})
}
fn order(a: &&Value, b: &&Value) -> std::cmp::Ordering {
    string(a, "date").cmp(&string(b, "date")).then_with(|| {
        string(a, "id")
            .parse::<i64>()
            .unwrap_or(0)
            .cmp(&string(b, "id").parse::<i64>().unwrap_or(0))
    })
}

pub(super) fn build(snapshot: &Value) -> Value {
    let mut tasks: Vec<_> = snapshot["tasks"].as_array().unwrap().iter().collect();
    tasks.sort_by(order);
    let weights = snapshot["weights"].as_array().unwrap();
    let mut names = BTreeMap::<String, Vec<&Value>>::new();
    let mut categories = BTreeMap::<(String, String), ()>::new();
    let mut cardio = BTreeMap::<String, f64>::new();
    let mut heat = BTreeMap::<String, f32>::new();
    for task in &tasks {
        let name = string(task, "name");
        let category = string(task, "category");
        names.entry(name.clone()).or_default().push(task);
        categories.insert((category.clone(), name), ());
        let sets = task["sets"].as_array().unwrap();
        let date = string(task, "date");
        if category == "CARDIO" {
            let time = if sets.is_empty() {
                duration(&string(task, "target")) as f64
            } else {
                sets.iter()
                    .map(|s| duration(&string(s, "weightOrDuration")) as f64)
                    .sum()
            };
            *cardio.entry(date).or_default() += time;
        }
        let mut volume = 0f32;
        if sets.is_empty() {
            let n = legacy_number(&string(task, "target")) as f32;
            volume += if n > 0. { n } else { 1. };
        } else {
            for set in sets {
                let w = (legacy_number(&string(set, "weightOrDuration")) as f32).max(1.);
                let r = (legacy_number(&string(set, "reps")) as f32).max(1.);
                volume += w * r;
                if task["unilateral"] == true {
                    let w = legacy_number(&string(set, "rightWeight")) as f32;
                    let r = legacy_number(&string(set, "rightReps")) as f32;
                    if w > 0. && r > 0. {
                        volume += w * r;
                    }
                }
            }
        }
        *heat.entry(string(task, "bodyPart")).or_default() += volume;
    }
    let mut exercise_series = BTreeMap::<String, Value>::new();
    for (name, rows) in &names {
        let mut daily = BTreeMap::<String, Vec<&Value>>::new();
        for task in rows {
            daily.entry(string(task, "date")).or_default().push(task);
        }
        let mut modes: [Vec<(String, f32)>; 4] = Default::default();
        for (date, tasks) in daily {
            let mut minutes = 0f64;
            let mut reps = 0f64;
            let mut left = 0f32;
            let mut right = 0f32;
            for task in tasks {
                let sets = task["sets"].as_array().unwrap();
                let fallback = json!({"weightOrDuration":if string(task,"actualWeight").is_empty(){string(task,"target")}else{string(task,"actualWeight")},"reps":string(task,"target")});
                let values = if sets.is_empty() {
                    vec![&fallback]
                } else {
                    sets.iter().collect()
                };
                for set in values {
                    minutes += duration(&string(set, "weightOrDuration")) as f64;
                    reps += (legacy_number(&string(set, "reps")) as f32) as f64;
                    left = left.max(legacy_number(&string(set, "weightOrDuration")) as f32);
                    right = right.max(legacy_number(&string(set, "rightWeight")) as f32);
                }
            }
            for (i, v) in [minutes as f32, left, reps as f32, right]
                .into_iter()
                .enumerate()
            {
                modes[i].push((date.clone(), v));
            }
        }
        let [time, left, reps, right] = modes;
        exercise_series.insert(name.clone(),json!({"duration":series(time),"weight":series(left),"reps":series(reps),"rightWeight":series(right)}));
    }
    let mut exercises:Vec<_>=categories.into_keys().map(|(category,name)|{
        let rows=&names[&name];
        json!({"name":name,"category":category,"logType":rows[0]["logType"],"unilateral":rows.iter().any(|r|r["unilateral"]==true)})
    }).collect();
    exercises.sort_by(|a, b| {
        string(a, "category")
            .cmp(&string(b, "category"))
            .then_with(|| {
                string(a, "name")
                    .encode_utf16()
                    .cmp(string(b, "name").encode_utf16())
            })
    });

    let height = number(&snapshot["profile"]["height"]);
    let age = number(&snapshot["profile"]["age"]);
    let gender = number(&snapshot["profile"]["gender"]);
    let mut body = BTreeMap::<String, Value>::new();
    for key in [
        "weight",
        "bmi",
        "bmr",
        "bodyFatKg",
        "skeletalMuscleKg",
        "bodyWaterPercentage",
        "whr",
    ] {
        let mut rows = Vec::new();
        for w in weights {
            let weight = number(&w["weight"]);
            let v = match key {
                "weight" => Some(weight),
                "bmi" => Some(if height <= 0. {
                    0.
                } else {
                    weight / ((height / 100.) * (height / 100.))
                }),
                "bmr" => Some(if height <= 0. || age <= 0. {
                    0.
                } else {
                    10. * weight + 6.25 * height - 5. * age + if gender == 0. { 5. } else { -161. }
                }),
                "whr" => {
                    if w["waistCircumference"].is_number() && w["hipCircumference"].is_number() {
                        let hip = number(&w["hipCircumference"]);
                        Some(if hip > 0. {
                            number(&w["waistCircumference"]) / hip
                        } else {
                            0.
                        })
                    } else {
                        None
                    }
                }
                field => w[field].as_f64().map(|v| v as f32),
            };
            if let Some(value) = v {
                rows.push((string(w, "date"), value));
            }
        }
        body.insert(key.into(), series(rows));
    }
    let max = heat.values().copied().reduce(f32::max).unwrap_or(1.);
    let heat:Vec<_>=heat.into_iter().map(|(part,volume)|json!({"bodyPart":part,"volume":volume,"intensity":(volume/max).clamp(0.,1.)})).collect();
    json!({"policy":"native-history-v1","exercises":exercises,"exerciseSeries":exercise_series,
        "cardio":series(cardio.into_iter().map(|(date,v)|(date,v as f32)).collect()),"body":body,"heatmap":heat})
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn month_is_mean_of_daily_values_not_total_or_max() {
        let input = json!({"tasks":[
            {"id":"2","date":"2026-11-02","name":"Press","category":"STRENGTH","logType":0,"sets":[{"weightOrDuration":"40","reps":"8"}]},
            {"id":"1","date":"2026-11-01","name":"Press","category":"STRENGTH","logType":0,"sets":[{"weightOrDuration":"20","reps":"10","rightWeight":"15","rightReps":"9"}],"unilateral":true}
        ],"weights":[]});
        let v: Value =
            serde_json::from_str(&crate::process_json(&input.to_string()).unwrap()).unwrap();
        assert_eq!(
            v["charts"]["exerciseSeries"]["Press"]["weight"]["monthly"][0]["value"],
            30.
        );
        assert_eq!(
            v["charts"]["exerciseSeries"]["Press"]["rightWeight"]["daily"][0]["value"],
            15.
        );
        assert_eq!(v["charts"]["exercises"][0]["logType"], 0);
    }
    #[test]
    fn body_profile_uses_only_allowed_fields_and_averages_records() {
        let v:Value=serde_json::from_str(&crate::process_json(&json!({"tasks":[],"weights":[{"date":"2026-11-01","weight":80.,"waistCircumference":80.,"hipCircumference":100.,"bodyFatKg":20.}],"profile":{"height":200.,"age":30,"gender":0,"aiApiKey":"secret"}}).to_string()).unwrap()).unwrap();
        assert_eq!(v["charts"]["body"]["bmi"]["daily"][0]["value"], 20.);
        assert_eq!(v["charts"]["body"]["bmr"]["daily"][0]["value"], 1905.);
        assert!(
            (v["charts"]["body"]["whr"]["daily"][0]["value"]
                .as_f64()
                .unwrap()
                - 0.8)
                .abs()
                < 1e-6
        );
        assert!(!v.to_string().contains("secret"));
    }
    #[test]
    fn body_month_averages_records_while_cardio_month_averages_daily_totals() {
        let input = json!({"tasks":[
            {"date":"2026-11-01","name":"Run","category":"CARDIO","logType":1,"sets":[{"weightOrDuration":"10min"},{"weightOrDuration":"20min"}]},
            {"date":"2026-11-02","name":"Run","category":"CARDIO","logType":1,"sets":[{"weightOrDuration":"60min"}]}
        ],"weights":[{"date":"2026-11-01","weight":60.},{"date":"2026-11-01","weight":80.},{"date":"2026-11-02","weight":100.}]});
        let value: Value =
            serde_json::from_str(&crate::process_json(&input.to_string()).unwrap()).unwrap();
        assert_eq!(
            value["charts"]["body"]["weight"]["monthly"][0]["value"],
            80.
        );
        assert_eq!(value["charts"]["cardio"]["monthly"][0]["value"], 45.);
    }
}
