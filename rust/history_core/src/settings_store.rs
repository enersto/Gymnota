use rusqlite::{params, Transaction};
use serde_json::Value;

fn ensure_profile(transaction: &Transaction<'_>) -> Result<(), &'static str> {
    transaction
        .execute(
            "INSERT INTO app_settings (id, themeId, languageCode, age, height, gender,
         aiProvider, aiApiKey, aiModel, aiBaseUrl, useLocalAiCache, maxHistoryLimit)
         VALUES (0,1,'zh',0,0,0,'OpenAI','','gpt-3.5-turbo','',1,10)
         ON CONFLICT(id) DO NOTHING",
            [],
        )
        .map_err(|_| "settings_init_failed")?;
    Ok(())
}

pub fn patch(transaction: &Transaction<'_>, change: &Value) -> Result<(), &'static str> {
    let field = change["field"].as_str().ok_or("missing_setting_field")?;
    let value = &change["value"];
    let (sql, parameter) = match field {
        "themeId" => (
            "UPDATE app_settings SET themeId = ?1 WHERE id = 0",
            rusqlite::types::Value::Integer(
                value
                    .as_i64()
                    .filter(|value| (1..=9).contains(value))
                    .ok_or("invalid_theme")?,
            ),
        ),
        "languageCode" => (
            "UPDATE app_settings SET languageCode = ?1 WHERE id = 0",
            rusqlite::types::Value::Text(
                value
                    .as_str()
                    .filter(|value| matches!(*value, "zh" | "en" | "es" | "de" | "ja"))
                    .ok_or("invalid_language")?
                    .into(),
            ),
        ),
        "useLocalAiCache" => (
            "UPDATE app_settings SET useLocalAiCache = ?1 WHERE id = 0",
            rusqlite::types::Value::Integer(i64::from(
                value.as_bool().ok_or("invalid_cache_flag")?,
            )),
        ),
        "maxHistoryLimit" => (
            "UPDATE app_settings SET maxHistoryLimit = ?1 WHERE id = 0",
            rusqlite::types::Value::Integer(
                value
                    .as_i64()
                    .filter(|value| (1..=20).contains(value))
                    .ok_or("invalid_history_limit")?,
            ),
        ),
        _ => return Err("unknown_setting"),
    };
    ensure_profile(transaction)?;
    transaction
        .execute(sql, [parameter])
        .map_err(|_| "setting_write_failed")?;
    Ok(())
}

pub fn schedule(transaction: &Transaction<'_>, change: &Value) -> Result<(), &'static str> {
    let day = change["day"]
        .as_i64()
        .filter(|day| (1..=7).contains(day))
        .ok_or("invalid_day")?;
    let kind = change["type"]
        .as_str()
        .filter(|kind| matches!(*kind, "CORE" | "ACTIVE_REST" | "LIGHT" | "REST"))
        .ok_or("invalid_day_type")?;
    transaction
        .execute(
            "INSERT INTO schedule_config(dayOfWeek,dayType) VALUES(?1,?2)
         ON CONFLICT(dayOfWeek) DO UPDATE SET dayType = excluded.dayType",
            params![day, kind],
        )
        .map_err(|_| "schedule_write_failed")?;
    Ok(())
}

pub fn model(transaction: &Transaction<'_>, change: &Value) -> Result<(), &'static str> {
    let fields = &change["fields"];
    let string = |key: &str| fields[key].as_str().ok_or("invalid_model_field");
    let provider = string("provider")?;
    let key = string("apiKey")?;
    let model = string("model")?;
    let url = string("baseUrl")?.trim();
    let lower = url.to_ascii_lowercase();
    let url = if lower.ends_with("v1/") {
        &url[..url.len() - 3]
    } else if lower.ends_with("v1") {
        &url[..url.len() - 2]
    } else {
        url
    };
    let url = url.trim_end_matches('/');
    ensure_profile(transaction)?;
    transaction.execute(
        "UPDATE app_settings SET aiProvider=?1, aiApiKey=?2, aiModel=?3, aiBaseUrl=?4 WHERE id=0",
        params![provider,key,model,url],
    ).map_err(|_| "model_write_failed")?;
    transaction.execute(
        "INSERT INTO ai_provider_configs(providerName,apiKey,model,baseUrl) VALUES(?1,?2,?3,?4)
         ON CONFLICT(providerName) DO UPDATE SET apiKey=excluded.apiKey, model=excluded.model, baseUrl=excluded.baseUrl",
        params![provider,key,model,url],
    ).map_err(|_| "provider_write_failed")?;
    Ok(())
}
