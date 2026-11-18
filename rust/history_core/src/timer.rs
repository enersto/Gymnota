use serde_json::{json, Value};

pub fn execute(input: &str) -> Result<String, &'static str> {
    let request: Value = serde_json::from_str(input).map_err(|_| "invalid_timer_request")?;
    let mut state = request["state"]
        .as_object()
        .ok_or("invalid_timer_state")?
        .clone();
    let now = request["now"]
        .as_i64()
        .filter(|v| *v >= 0)
        .ok_or("invalid_timer_clock")?;
    let options = &request["options"];
    let mut finished = false;
    let mut beep = false;
    match request["action"].as_str() {
        Some("prep") => {
            let seconds = options["prepSeconds"]
                .as_i64()
                .filter(|v| *v > 0)
                .ok_or("invalid_prep")?;
            state.insert("phase".into(), json!("PREP"));
            state.insert("totalSeconds".into(), json!(seconds));
            state.insert("remainingSeconds".into(), json!(seconds));
            state.insert(
                "endTimeMillis".into(),
                json!(now
                    .checked_add(seconds.checked_mul(1000).ok_or("timer_overflow")?)
                    .ok_or("timer_overflow")?),
            );
            state.insert("isRunning".into(), json!(true));
            state.insert("isPaused".into(), json!(false));
            state.insert("showBigAlert".into(), json!(true));
        }
        Some("work") => {
            let duration = options["durationMillis"]
                .as_i64()
                .filter(|v| *v > 0)
                .ok_or("invalid_duration")?;
            let same = state.get("taskId") == options.get("taskId")
                && state.get("setIndex") == options.get("setIndex");
            let resume = same
                && state.get("isPaused") == Some(&json!(true))
                && state.get("phase") == Some(&json!("WORK"));
            let remaining = if resume {
                state
                    .get("remainingSeconds")
                    .and_then(Value::as_i64)
                    .ok_or("invalid_remaining")?
                    .checked_mul(1000)
                    .ok_or("timer_overflow")?
            } else {
                duration
            };
            state.insert("taskId".into(), options["taskId"].clone());
            state.insert("setIndex".into(), options["setIndex"].clone());
            state.insert("totalSeconds".into(), options["totalSeconds"].clone());
            state.insert("remainingSeconds".into(), json!(remaining / 1000));
            state.insert(
                "endTimeMillis".into(),
                json!(now.checked_add(remaining).ok_or("timer_overflow")?),
            );
            state.insert("phase".into(), json!("WORK"));
            state.insert("isRunning".into(), json!(true));
            state.insert("isPaused".into(), json!(false));
            state.insert("showBigAlert".into(), json!(false));
        }
        Some("tick") => {
            if state.get("isRunning") == Some(&json!(true)) {
                let end = state
                    .get("endTimeMillis")
                    .and_then(Value::as_i64)
                    .ok_or("invalid_deadline")?;
                let diff = end - now;
                let seconds = if diff <= 0 { 0 } else { diff / 1000 + 1 };
                let prep = state.get("phase") == Some(&json!("PREP"));
                let threshold = if prep {
                    3
                } else {
                    options["finalSeconds"]
                        .as_i64()
                        .ok_or("invalid_final_seconds")?
                };
                let countdown = seconds > 0 && seconds <= threshold;
                beep = options["soundEnabled"] == true
                    && options["lastBeepSecond"].as_i64() != Some(seconds)
                    && countdown;
                finished = diff <= 0;
                state.insert("remainingSeconds".into(), json!(seconds));
                state.insert(
                    "showBigAlert".into(),
                    json!(!finished && (prep || (options["finalEnabled"] == true && countdown))),
                );
                if finished {
                    state.insert("isRunning".into(), json!(false));
                }
            }
        }
        Some("pause") => {
            state.insert("isRunning".into(), json!(false));
            state.insert("isPaused".into(), json!(true));
        }
        _ => return Err("invalid_timer_action"),
    }
    Ok(json!({"state": state, "beep": beep, "finished": finished}).to_string())
}

#[cfg(test)]
mod tests {
    use super::*;
    fn transition(action: &str, state: Value, now: i64, options: Value) -> Value {
        serde_json::from_str(&execute(&json!({"action":action,"state":state,"now":now,"options":options}).to_string()).unwrap()).unwrap()
    }

    #[test]
    fn countdown_preserves_rounding_sound_and_visual_preferences() {
        let work = json!({"isRunning":true,"phase":"WORK","endTimeMillis":10000});
        let options = json!({"finalSeconds":3,"finalEnabled":false,"soundEnabled":true,"lastBeepSecond":-1});
        let exact = transition("tick", work.clone(), 7000, options.clone());
        assert_eq!(exact["state"]["remainingSeconds"], 4);
        assert_eq!(exact["beep"], false);
        let within = transition("tick", work.clone(), 7001, options.clone());
        assert_eq!(within["state"]["remainingSeconds"], 3);
        assert_eq!(within["beep"], true);
        assert_eq!(within["state"]["showBigAlert"], false);
        let mut repeated = options;
        repeated["lastBeepSecond"] = json!(3);
        assert_eq!(transition("tick", work, 7100, repeated)["beep"], false);
    }

    #[test]
    fn prep_and_paused_timers_do_not_complete_work_early() {
        let state = json!({"taskId":7,"setIndex":1,"phase":"IDLE","isRunning":false});
        let prep = transition("prep", state, 1000, json!({"prepSeconds":10}));
        assert_eq!(prep["state"]["endTimeMillis"], 11000);
        let tick = transition("tick", prep["state"].clone(), 5000,
            json!({"finalSeconds":0,"finalEnabled":false,"soundEnabled":false}));
        assert_eq!(tick["state"]["showBigAlert"], true);
        assert_eq!(tick["finished"], false);
        let paused = transition("pause", tick["state"].clone(), 5000, json!({}));
        let later = transition("tick", paused["state"].clone(), 50000, json!({}));
        assert_eq!(later["state"], paused["state"]);
        assert_eq!(later["finished"], false);
    }

    #[test]
    fn resume_uses_remaining_time_and_background_ticks_finish_once() {
        let initial = json!({"taskId": 7,"setIndex":0,"phase":"WORK","isRunning":false,"isPaused":true,"remainingSeconds":4,"totalSeconds":60,"showBigAlert":false,"endTimeMillis":2000});
        let request = json!({"state":initial,"action":"work","now":5000,"options":{"taskId":7,"setIndex":0,"durationMillis":60000,"totalSeconds":60}});
        let started: Value = serde_json::from_str(&execute(&request.to_string()).unwrap()).unwrap();
        assert_eq!(started["state"]["endTimeMillis"], 9000);
        let tick = json!({"state": started["state"],"action":"tick","now":20000,"options":{"finalSeconds":3,"soundEnabled":true,"lastBeepSecond":-1,"finalEnabled":true}});
        let done: Value = serde_json::from_str(&execute(&tick.to_string()).unwrap()).unwrap();
        assert_eq!(done["finished"], true);
        assert_eq!(done["state"]["remainingSeconds"], 0);
        let mut again = tick;
        again["state"] = done["state"].clone();
        let done: Value = serde_json::from_str(&execute(&again.to_string()).unwrap()).unwrap();
        assert_eq!(done["finished"], false);
    }
}
