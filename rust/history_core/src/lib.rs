//! Pure history snapshots plus isolated catalogue and AI stream modules.
pub mod ai_sessions;
pub mod ai_stream;
#[cfg(feature = "catalog-sqlite")]
pub mod catalog;
mod charts;
use serde_json::{json, Value};
use std::collections::{BTreeMap, BTreeSet};

const MAX_INPUT: usize = 16 * 1024 * 1024;
fn string(v: &Value, key: &str) -> String {
    match &v[key] {
        Value::String(s) => s.clone(),
        Value::Number(n) => n.to_string(),
        _ => String::new(),
    }
}
fn scalar(v: &Value) -> Value {
    // Match JSONObject.optString: retain even ambiguous legacy values as text, never discard them.
    match v {
        Value::Null => Value::Null,
        Value::String(s) => json!(s),
        value => json!(value.to_string()),
    }
}

fn normalize(input: &Value) -> Result<Value, &'static str> {
    let tasks = input["tasks"].as_array().ok_or("tasks_must_be_array")?;
    let weights = input["weights"].as_array().ok_or("weights_must_be_array")?;
    let mut normalized = Vec::with_capacity(tasks.len());
    for t in tasks {
        if !t.is_object() {
            return Err("task_must_be_object");
        }
        let sets_value = match &t["sets"] {
            Value::String(s) if s.trim().is_empty() => json!([]),
            Value::String(s) => serde_json::from_str(s).map_err(|_| "invalid_sets_json")?,
            Value::Null => json!([]),
            value => value.clone(),
        };
        let sets = sets_value.as_array().ok_or("sets_must_be_array")?;
        let mut rows = Vec::with_capacity(sets.len());
        for s in sets {
            if !s.is_object() {
                return Err("set_must_be_object");
            }
            let mut row = serde_json::Map::new();
            for key in [
                "setNumber",
                "weightOrDuration",
                "reps",
                "rightWeight",
                "rightReps",
            ] {
                row.insert(key.into(), scalar(&s[key]));
            }
            rows.push(Value::Object(row));
        }
        let mut row = json!({"id":string(t,"id"), "date":string(t,"date"), "name":string(t,"name"),
            "category":string(t,"category"), "logType":t["logType"].as_i64().unwrap_or(0),
            "unilateral":t["unilateral"].as_bool().unwrap_or(false), "target":string(t,"target"),
            "actualWeight":string(t,"actualWeight"), "sets":rows});
        if t.get("bodyPart").is_some() {
            row["bodyPart"] = json!(string(t, "bodyPart"));
        }
        normalized.push(row);
    }
    let mut normalized_weights = Vec::with_capacity(weights.len());
    for w in weights {
        let value = w["weight"]
            .as_f64()
            .filter(|v| v.is_finite())
            .ok_or("invalid_weight")?;
        let mut row = json!({"date":string(w,"date"),"weight":value});
        for key in [
            "bodyFatKg",
            "skeletalMuscleKg",
            "bodyWaterPercentage",
            "waistCircumference",
            "hipCircumference",
        ] {
            if w.get(key).is_some() {
                row[key] = w[key]
                    .as_f64()
                    .filter(|v| v.is_finite())
                    .map(|v| json!(v))
                    .unwrap_or(Value::Null);
            }
        }
        normalized_weights.push(row);
    }
    let mut output = json!({"tasks":normalized,"weights":normalized_weights});
    if input["profile"].is_object() {
        output["profile"] = json!({"height":input["profile"]["height"].as_f64().filter(|v|v.is_finite()).unwrap_or(0.),
            "age":input["profile"]["age"].as_i64().unwrap_or(0),"gender":input["profile"]["gender"].as_i64().unwrap_or(0)});
    }
    Ok(output)
}

fn numeric(s: &str) -> Option<f64> {
    let s = s.trim();
    if s.is_empty() || !s.bytes().all(|c| c.is_ascii_digit() || c == b'.') {
        return None;
    }
    s.parse::<f64>().ok().filter(|v| v.is_finite() && *v >= 0.)
}

/// Minutes: bare UI values, seconds/minutes/hours and explicit m:ss or h:mm:ss.
pub fn duration_minutes(s: &str) -> Option<f64> {
    let text = s.trim().to_lowercase().replace(' ', "");
    if let Some(v) = numeric(&text) {
        return Some(v);
    }
    if text.contains(':') {
        let parts: Vec<_> = text.split(':').map(numeric).collect();
        if !(parts.len() == 2 || parts.len() == 3) || parts.iter().any(Option::is_none) {
            return None;
        }
        let vals: Vec<_> = parts.into_iter().map(Option::unwrap).collect();
        if vals[1..].iter().any(|v| *v >= 60.) {
            return None;
        }
        return Some(if vals.len() == 2 {
            vals[0] + vals[1] / 60.
        } else {
            vals[0] * 60. + vals[1] + vals[2] / 60.
        });
    }
    let mut rest = text.as_str();
    let mut total = 0.;
    let mut found = false;
    while !rest.is_empty() {
        let end = rest
            .find(|c: char| !c.is_ascii_digit() && c != '.')
            .unwrap_or(rest.len());
        let value = numeric(&rest[..end])?;
        rest = &rest[end..];
        let units = [
            ("minutes", 1.),
            ("minute", 1.),
            ("mins", 1.),
            ("min", 1.),
            ("分钟", 1.),
            ("分", 1.),
            ("hours", 60.),
            ("hour", 60.),
            ("hrs", 60.),
            ("hr", 60.),
            ("小时", 60.),
            ("h", 60.),
            ("seconds", 1. / 60.),
            ("second", 1. / 60.),
            ("secs", 1. / 60.),
            ("sec", 1. / 60.),
            ("秒", 1. / 60.),
            ("m", 1.),
            ("s", 1. / 60.),
        ];
        let (unit, factor) = units.iter().find(|(unit, _)| rest.starts_with(unit))?;
        total += value * factor;
        rest = &rest[unit.len()..];
        found = true;
    }
    (found && total.is_finite()).then_some(total)
}

fn date_valid(s: &str) -> bool {
    if s.len() != 10
        || s.as_bytes()[4] != b'-'
        || s.as_bytes()[7] != b'-'
        || !s
            .bytes()
            .enumerate()
            .all(|(i, c)| i == 4 || i == 7 || c.is_ascii_digit())
    {
        return false;
    }
    let y: u32 = s[..4].parse().unwrap_or(0);
    let m: usize = s[5..7].parse().unwrap_or(0);
    let d: u32 = s[8..].parse().unwrap_or(0);
    let days = [
        0,
        31,
        if y % 4 == 0 && (y % 100 != 0 || y % 400 == 0) {
            29
        } else {
            28
        },
        31,
        30,
        31,
        30,
        31,
        31,
        30,
        31,
        30,
        31,
    ];
    y > 0 && (1..=12).contains(&m) && d > 0 && d <= days[m]
}

#[derive(Default)]
struct Metrics {
    task_count: u64,
    set_count: u64,
    reps: f64,
    volume: f64,
    minutes: f64,
    max_weight: f64,
    days: BTreeSet<String>,
    legacy: u64,
}
impl Metrics {
    fn add(&mut self, other: &Metrics) {
        self.task_count += other.task_count;
        self.set_count += other.set_count;
        self.reps += other.reps;
        self.volume += other.volume;
        self.minutes += other.minutes;
        self.max_weight = self.max_weight.max(other.max_weight);
        self.days.extend(other.days.iter().cloned());
        self.legacy += other.legacy;
    }
    fn value(&self) -> Value {
        json!({"taskCount":self.task_count,"setCount":self.set_count,"reps":self.reps,
        "volumeKg":self.volume,"durationMinutes":self.minutes,"maxWeightKg":self.max_weight,"trainingDays":self.days.len(),"legacyFallbackCount":self.legacy})
    }
}
fn warning(warnings: &mut Vec<Value>, task: &Value, field: &str, code: &str) {
    if warnings.len() < 1000 {
        warnings.push(json!({"taskId":task["id"],"field":field,"code":code}));
    }
}
fn measure(task: &Value, warnings: &mut Vec<Value>) -> Metrics {
    let mut m = Metrics {
        task_count: 1,
        ..Default::default()
    };
    let date = string(task, "date");
    if date_valid(&date) {
        m.days.insert(date);
    } else {
        warning(warnings, task, "date", "invalid_date");
    }
    let log = task["logType"].as_i64().unwrap_or(0);
    if !(0..=2).contains(&log) {
        warning(warnings, task, "logType", "unknown_log_type");
        return m;
    }
    let sets = task["sets"].as_array().unwrap();
    m.set_count = sets.len() as u64;
    if sets.is_empty() {
        m.legacy = 1;
        // Legacy free-form targets are displayed intact. Do not infer sets or volume from "3 x 12".
        if log == 1 {
            if let Some(v) = duration_minutes(&string(task, "target")) {
                m.minutes = v;
            }
        } else if log == 0 {
            if let Some(v) = numeric(&string(task, "actualWeight")) {
                m.max_weight = v;
            }
        }
        return m;
    }
    for set in sets {
        let sides = if task["unilateral"] == true { 2 } else { 1 };
        for side in 0..sides {
            let wk = if side == 0 {
                "weightOrDuration"
            } else {
                "rightWeight"
            };
            let rk = if side == 0 { "reps" } else { "rightReps" };
            let w = string(set, wk);
            let r = string(set, rk);
            if log == 1 {
                if let Some(v) = duration_minutes(&w) {
                    m.minutes += v;
                } else if !w.is_empty() {
                    warning(warnings, task, wk, "invalid_duration");
                }
            } else {
                let reps = numeric(&r);
                if let Some(v) = reps {
                    m.reps += v;
                } else if !r.is_empty() {
                    warning(warnings, task, rk, "invalid_number");
                }
                if log == 0 {
                    let weight = numeric(&w);
                    if let Some(v) = weight {
                        m.max_weight = m.max_weight.max(v);
                        if let Some(reps) = reps {
                            m.volume += v * reps;
                        }
                    } else if !w.is_empty() {
                        warning(warnings, task, wk, "invalid_number");
                    }
                }
            }
        }
    }
    m
}

// Exact compatibility with the existing chart parser, intentionally separate from validated metrics.
pub fn legacy_number(s: &str) -> f64 {
    let bytes = s.as_bytes();
    let Some(start) = bytes.iter().position(u8::is_ascii_digit) else {
        return 0.;
    };
    let mut end = start;
    while end < bytes.len() && bytes[end].is_ascii_digit() {
        end += 1;
    }
    if end + 1 < bytes.len() && bytes[end] == b'.' && bytes[end + 1].is_ascii_digit() {
        end += 1;
        while end < bytes.len() && bytes[end].is_ascii_digit() {
            end += 1;
        }
    }
    s[start..end].parse().unwrap_or(0.)
}
pub fn legacy_duration(s: &str) -> f64 {
    let s = s.to_lowercase();
    let n = legacy_number(&s);
    if s.contains('h') {
        n * 60.
    } else if s.contains('s') && !s.contains('m') {
        n / 60.
    } else {
        n
    }
}

pub fn process_json(text: &str) -> Result<String, &'static str> {
    if text.len() > MAX_INPUT {
        return Err("snapshot_too_large");
    }
    let input: Value = serde_json::from_str(text).map_err(|_| "invalid_json")?;
    let snapshot = normalize(&input)?;
    let mut total = Metrics::default();
    let mut days = BTreeMap::<String, Metrics>::new();
    let mut months = BTreeMap::<String, Metrics>::new();
    let mut exercises = BTreeMap::<String, Metrics>::new();
    let mut warnings = Vec::new();
    let mut legacy_charts = BTreeMap::<(String, String), Value>::new();
    for task in snapshot["tasks"].as_array().unwrap() {
        let metrics = measure(task, &mut warnings);
        total.add(&metrics);
        let date = string(task, "date");
        let name = string(task, "name");
        if date_valid(&date) {
            days.entry(date.clone()).or_default().add(&metrics);
            months.entry(date[..7].into()).or_default().add(&metrics);
        }
        exercises.entry(name.clone()).or_default().add(&metrics);
        let sets = task["sets"].as_array().unwrap();
        let fallback = json!({"weightOrDuration": if string(task,"actualWeight").is_empty(){string(task,"target")}else{string(task,"actualWeight")},"reps":string(task,"target")});
        let values = if sets.is_empty() {
            vec![&fallback]
        } else {
            sets.iter().collect()
        };
        let key = (date.clone(), name.clone());
        let entry=legacy_charts.entry(key).or_insert_with(||json!({"date":date,"name":name,"durationMinutes":0.,"maxWeightKg":0.,"reps":0.,"rightMaxWeightKg":0.}));
        for set in values {
            entry["durationMinutes"] = json!(
                entry["durationMinutes"].as_f64().unwrap()
                    + legacy_duration(&string(set, "weightOrDuration"))
            );
            entry["reps"] =
                json!(entry["reps"].as_f64().unwrap() + legacy_number(&string(set, "reps")));
            entry["maxWeightKg"] = json!(entry["maxWeightKg"]
                .as_f64()
                .unwrap()
                .max(legacy_number(&string(set, "weightOrDuration"))));
            entry["rightMaxWeightKg"] = json!(entry["rightMaxWeightKg"]
                .as_f64()
                .unwrap()
                .max(legacy_number(&string(set, "rightWeight"))));
        }
    }
    let groups = |map: BTreeMap<String, Metrics>, key: &str| -> Vec<Value> {
        map.into_iter()
            .map(|(name, metrics)| {
                let mut v = metrics.value();
                v[key] = json!(name);
                v
            })
            .collect()
    };
    serde_json::to_string(&json!({"schemaVersion":1,"charts":charts::build(&snapshot),"snapshot":snapshot,"summary":total.value(),"byDay":groups(days,"date"),"byMonth":groups(months,"month"),"byExercise":groups(exercises,"name"),"legacyCharts":legacy_charts.into_values().collect::<Vec<_>>(),"warnings":warnings})).map_err(|_|"serialization_failed")
}

#[cfg(any(test, feature = "android-jni"))]
mod settings_csv;

#[cfg(feature = "android-jni")]
mod android {
    use jni::{
        objects::{JClass, JString},
        sys::jstring,
        JNIEnv,
    };
    #[no_mangle]
    pub extern "system" fn Java_com_example_myfit_data_ai_RustAiStream_createNative(
        mut env: JNIEnv,
        _class: JClass,
        config: JString,
    ) -> jni::sys::jlong {
        std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| {
            let text: String = env.get_string(&config).ok()?.into();
            super::ai_sessions::create(&text).ok()
        }))
        .ok()
        .flatten()
        .unwrap_or(0)
    }
    #[no_mangle]
    pub extern "system" fn Java_com_example_myfit_data_ai_RustAiStream_pushNative(
        mut env: JNIEnv,
        _class: JClass,
        id: jni::sys::jlong,
        data: jni::objects::JByteArray,
        eof: jni::sys::jboolean,
    ) -> jstring {
        let result = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| {
            let bytes = env
                .convert_byte_array(&data)
                .map_err(|_| "invalid_jni_bytes")?;
            super::ai_sessions::push(id, &bytes, eof != 0)
        }));
        let text = match result {
            Ok(Ok(text)) => text,
            Ok(Err(code)) => serde_json::json!({"error":code}).to_string(),
            Err(_) => "{\"error\":\"stream_panic\"}".into(),
        };
        env.new_string(text)
            .map(|s| s.into_raw())
            .unwrap_or(std::ptr::null_mut())
    }
    #[no_mangle]
    pub extern "system" fn Java_com_example_myfit_data_ai_RustAiStream_closeNative(
        _env: JNIEnv,
        _class: JClass,
        id: jni::sys::jlong,
    ) {
        let _ = std::panic::catch_unwind(|| super::ai_sessions::close(id));
    }
    #[no_mangle]
    pub extern "system" fn Java_com_example_myfit_data_RustSettingsCsv_encodeNative(
        mut env: JNIEnv,
        _class: JClass,
        input: JString,
    ) -> jstring {
        std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| {
            let text: String = env.get_string(&input).ok()?.into();
            let csv = super::settings_csv::encode(&text)?;
            env.new_string(csv).ok().map(|s| s.into_raw())
        }))
        .ok()
        .flatten()
        .unwrap_or(std::ptr::null_mut())
    }
    #[no_mangle]
    pub extern "system" fn Java_com_example_myfit_data_RustCatalog_queryNative(
        mut env: JNIEnv,
        _class: JClass,
        input: JString,
    ) -> jstring {
        let outcome = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| {
            let text: String = env
                .get_string(&input)
                .map_err(|_| "invalid_jni_string")?
                .into();
            super::catalog::query_json(&text)
        }));
        let text = match outcome {
            Ok(Ok(text)) => text,
            Ok(Err(code)) => serde_json::json!({"error":code}).to_string(),
            Err(_) => "{\"error\":\"catalog_panic\"}".into(),
        };
        env.new_string(text)
            .map(|s| s.into_raw())
            .unwrap_or(std::ptr::null_mut())
    }
    #[no_mangle]
    pub extern "system" fn Java_com_example_myfit_flutter_RustHistoryCore_processNative(
        mut env: JNIEnv,
        _class: JClass,
        input: JString,
    ) -> jstring {
        let outcome = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| {
            let text: String = env
                .get_string(&input)
                .map_err(|_| "invalid_jni_string")?
                .into();
            super::process_json(&text)
        }));
        let text = match outcome {
            Ok(Ok(text)) => text,
            Ok(Err(code)) => serde_json::json!({"error":code}).to_string(),
            Err(_) => "{\"error\":\"core_panic\"}".into(),
        };
        env.new_string(text)
            .map(|s| s.into_raw())
            .unwrap_or(std::ptr::null_mut())
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn durations_are_explicit() {
        for (s, n) in [
            ("25", 25.),
            ("90s", 1.5),
            ("1h30min", 90.),
            ("2:30", 2.5),
            ("1:02:30", 62.5),
            ("2分30秒", 2.5),
        ] {
            assert_eq!(duration_minutes(s), Some(n));
        }
        for s in ["-5", "NaN", "1:99", "3x12", "1hourjunk"] {
            assert_eq!(duration_minutes(s), None);
        }
    }
    #[test]
    fn names_sets_and_sides_survive() {
        let input = json!({"tasks":[{"id":"01","date":"2026-11-01","name":"个人动作","logType":0,"unilateral":true,"sets":"[{\"setNumber\":1,\"weightOrDuration\":\"25\",\"reps\":\"12\",\"rightWeight\":\"20\",\"rightReps\":\"10\"}]","secret":"x"}],"weights":[],"apiKey":"private"});
        let out = process_json(&input.to_string()).unwrap();
        let v: Value = serde_json::from_str(&out).unwrap();
        assert!(!out.contains("private"));
        assert!(!out.contains("secret"));
        assert_eq!(v["snapshot"]["tasks"][0]["name"], "个人动作");
        assert_eq!(v["summary"]["volumeKg"], 500.);
        assert_eq!(v["summary"]["reps"], 22.);
        assert_eq!(v["summary"]["setCount"], 1);
    }
    #[test]
    fn malformed_inputs_return_errors() {
        for s in [
            "{}",
            "{\"tasks\":[],\"weights\":null}",
            "{\"tasks\":[{\"sets\":\"bad\"}],\"weights\":[]}",
        ] {
            assert!(process_json(s).is_err());
        }
    }
    #[test]
    fn dates_and_unknown_values_do_not_invent_metrics() {
        assert!(date_valid("2024-02-29"));
        assert!(!date_valid("2026-02-29"));
        let v:Value=serde_json::from_str(&process_json(&json!({"tasks":[{"date":"bad","logType":0,"sets":[{"weightOrDuration":"-5","reps":"oops"}]}],"weights":[]}).to_string()).unwrap()).unwrap();
        assert_eq!(v["summary"]["volumeKg"], 0.);
        assert_eq!(v["byDay"], json!([]));
        assert_eq!(v["warnings"].as_array().unwrap().len(), 3);
    }
    #[test]
    fn period_metrics_respect_record_types() {
        let input = json!({"tasks":[
            {"date":"2026-10-31","name":"A","logType":0,"sets":[{"weightOrDuration":"10","reps":"5"}]},
            {"date":"2026-11-01","name":"A","logType":2,"sets":[{"weightOrDuration":"999","reps":"12"}]},
            {"date":"2026-11-01","name":"B","logType":1,"sets":[{"weightOrDuration":"90s","reps":"Done"}]}
        ],"weights":[]});
        let v: Value = serde_json::from_str(&process_json(&input.to_string()).unwrap()).unwrap();
        assert_eq!(v["summary"]["volumeKg"], 50.);
        assert_eq!(v["summary"]["maxWeightKg"], 10.);
        assert_eq!(v["summary"]["reps"], 17.);
        assert_eq!(v["summary"]["durationMinutes"], 1.5);
        assert_eq!(v["summary"]["trainingDays"], 2);
        assert_eq!(v["byMonth"].as_array().unwrap().len(), 2);
        assert_eq!(v["byExercise"].as_array().unwrap().len(), 2);
    }
    #[test]
    fn legacy_free_form_target_is_not_invented_volume() {
        let v:Value=serde_json::from_str(&process_json(&json!({"tasks":[{"date":"2026-11-01","name":"Legacy","logType":0,"sets":[],"target":"3 x 12","actualWeight":"30"}],"weights":[]}).to_string()).unwrap()).unwrap();
        assert_eq!(v["summary"]["volumeKg"], 0.);
        assert_eq!(v["summary"]["setCount"], 0);
        assert_eq!(v["summary"]["maxWeightKg"], 30.);
        assert_eq!(v["summary"]["legacyFallbackCount"], 1);
    }
    #[test]
    fn legacy_chart_semantics_are_preserved() {
        assert_eq!(legacy_duration("1h30m"), 60.);
        assert_eq!(legacy_number("-5kg"), 5.);
        assert_eq!(legacy_duration("90s"), 1.5);
    }
}
