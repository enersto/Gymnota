//! Owned stream sessions. Opaque integer IDs, never native pointers or credentials.
use crate::ai_stream::{EventNormalizer, Protocol, ReasoningPolicy, SseDecoder};
use serde_json::{json, Value};
use std::{
    collections::HashMap,
    sync::{
        atomic::{AtomicI64, Ordering},
        Mutex, OnceLock,
    },
};
struct Session {
    decoder: SseDecoder,
    normalizer: EventNormalizer,
    failed: bool,
}
static SESSIONS: OnceLock<Mutex<HashMap<i64, Session>>> = OnceLock::new();
static NEXT: AtomicI64 = AtomicI64::new(1);
fn sessions() -> &'static Mutex<HashMap<i64, Session>> {
    SESSIONS.get_or_init(|| Mutex::new(HashMap::new()))
}
pub fn create(config: &str) -> Result<i64, &'static str> {
    if config.len() > 4096 {
        return Err("invalid_config");
    }
    let v: Value = serde_json::from_str(config).map_err(|_| "invalid_config")?;
    let protocol = match v["protocol"].as_str() {
        Some("chat") => Protocol::ChatCompletions,
        Some("responses") => Protocol::Responses,
        _ => return Err("invalid_protocol"),
    };
    let policy = if let Some(field) = v["publicReasoningField"].as_str() {
        if field.is_empty()
            || field.len() > 64
            || ["content", "role", "refusal"].contains(&field)
            || matches!(protocol, Protocol::Responses)
        {
            return Err("invalid_reasoning_field");
        }
        ReasoningPolicy::ProviderField(field.into())
    } else if v["reasoningSummary"].as_bool() == Some(true) {
        ReasoningPolicy::Summary
    } else {
        ReasoningPolicy::None
    };
    let mut map = sessions().lock().map_err(|_| "session_lock")?;
    if map.len() >= 8 {
        return Err("too_many_streams");
    }
    let id = NEXT.fetch_add(1, Ordering::Relaxed);
    if id <= 0 {
        return Err("session_id_exhausted");
    }
    map.insert(
        id,
        Session {
            decoder: SseDecoder::default(),
            normalizer: EventNormalizer::new(protocol, policy),
            failed: false,
        },
    );
    Ok(id)
}
pub fn push(id: i64, bytes: &[u8], eof: bool) -> Result<String, &'static str> {
    let mut map = sessions().lock().map_err(|_| "session_lock")?;
    let s = map.get_mut(&id).ok_or("invalid_stream_handle")?;
    if s.failed {
        return Err("stream_failed");
    }
    let result = (|| {
        if bytes.len() > 65536 {
            return Err("chunk_too_large");
        }
        let mut events = vec![];
        for frame in s.decoder.push(bytes)? {
            events.extend(s.normalizer.push(frame)?);
        }
        if eof {
            s.decoder.finish()?;
            s.normalizer.finish()?;
        }
        Ok(json!({"events":events,"terminal":s.normalizer.is_terminal()}).to_string())
    })();
    if result.is_err() {
        s.failed = true;
    }
    result
}
pub fn close(id: i64) {
    if let Ok(mut map) = sessions().lock() {
        map.remove(&id);
    }
}
#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn owned_sessions_are_isolated_and_close_is_idempotent() {
        let a = create(r#"{"protocol":"chat"}"#).unwrap();
        let b = create(r#"{"protocol":"chat"}"#).unwrap();
        let bytes="data: {\"error\":null,\"choices\":[{\"delta\":{\"content\":\"你好\"},\"finish_reason\":\"stop\"}]}\n\ndata: [DONE]\n\n".as_bytes();
        let mut events = vec![];
        for byte in bytes {
            let v: Value = serde_json::from_str(&push(a, &[*byte], false).unwrap()).unwrap();
            events.extend(v["events"].as_array().unwrap().clone());
        }
        assert_eq!(events[0]["text"], "你好");
        assert_eq!(events.last().unwrap()["status"], "completed");
        assert!(push(a, &[], true).is_ok());
        assert!(push(b, &[], true).is_err());
        close(a);
        close(a);
        close(b);
        assert_eq!(push(a, &[], false), Err("invalid_stream_handle"));
    }
    #[test]
    fn invalid_capabilities_are_rejected_before_allocating_a_session() {
        assert!(create(r#"{"protocol":"chat","publicReasoningField":"content"}"#).is_err());
        assert!(
            create(r#"{"protocol":"responses","publicReasoningField":"reasoning_content"}"#)
                .is_err()
        );
    }
}
