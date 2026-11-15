//! Offline protocol preparation. No HTTP, credentials, persistence or JNI ownership here.
use serde_json::{json, Value};

const MAX_EVENT_BYTES: usize = 1024 * 1024;
#[derive(Debug, PartialEq, Eq)]
pub struct SseFrame {
    pub event: String,
    pub data: String,
}
pub struct SseDecoder {
    line: Vec<u8>,
    data: Vec<String>,
    event: String,
    bytes: usize,
    first_line: bool,
    skip_lf: bool,
    failed: bool,
}
impl Default for SseDecoder {
    fn default() -> Self {
        Self {
            line: vec![],
            data: vec![],
            event: String::new(),
            bytes: 0,
            first_line: true,
            skip_lf: false,
            failed: false,
        }
    }
}
impl SseDecoder {
    pub fn push(&mut self, input: &[u8]) -> Result<Vec<SseFrame>, &'static str> {
        if self.failed {
            return Err("decoder_failed");
        }
        let mut output = vec![];
        for &byte in input {
            if self.skip_lf {
                self.skip_lf = false;
                if byte == b'\n' {
                    continue;
                }
            }
            if byte == b'\r' || byte == b'\n' {
                self.skip_lf = byte == b'\r';
                if let Err(e) = self.consume_line(&mut output) {
                    self.failed = true;
                    return Err(e);
                }
            } else {
                self.bytes += 1;
                if self.bytes > MAX_EVENT_BYTES {
                    self.failed = true;
                    return Err("event_too_large");
                }
                self.line.push(byte);
            }
        }
        Ok(output)
    }
    fn consume_line(&mut self, output: &mut Vec<SseFrame>) -> Result<(), &'static str> {
        let raw = std::mem::take(&mut self.line);
        let raw = if self.first_line {
            self.first_line = false;
            raw.strip_prefix(&[0xef, 0xbb, 0xbf]).unwrap_or(&raw)
        } else {
            &raw
        };
        let line = std::str::from_utf8(raw).map_err(|_| "invalid_utf8")?;
        if line.is_empty() {
            if !self.data.is_empty() {
                output.push(SseFrame {
                    event: std::mem::take(&mut self.event),
                    data: self.data.join("\n"),
                });
            }
            self.data.clear();
            self.event.clear();
            self.bytes = 0;
        } else if !line.starts_with(':') {
            let (name, value) = line.split_once(':').unwrap_or((line, ""));
            let value = value.strip_prefix(' ').unwrap_or(value);
            match name {
                "event" => self.event = value.to_owned(),
                "data" => self.data.push(value.to_owned()),
                _ => {}
            }
        }
        Ok(())
    }
    pub fn finish(&self) -> Result<(), &'static str> {
        if self.failed {
            Err("decoder_failed")
        } else if !self.line.is_empty() || !self.data.is_empty() {
            Err("truncated_sse_event")
        } else {
            Ok(())
        }
    }
}
#[derive(Clone, Copy)]
pub enum Protocol {
    ChatCompletions,
    Responses,
}
pub enum ReasoningPolicy {
    None,
    Summary,
    ProviderField(String),
}
pub struct EventNormalizer {
    protocol: Protocol,
    policy: ReasoningPolicy,
    finish_reason: Option<String>,
    terminal: bool,
}
impl EventNormalizer {
    pub fn new(protocol: Protocol, policy: ReasoningPolicy) -> Self {
        Self {
            protocol,
            policy,
            finish_reason: None,
            terminal: false,
        }
    }
    pub fn push(&mut self, frame: SseFrame) -> Result<Vec<Value>, &'static str> {
        if self.terminal || frame.data.is_empty() {
            return Ok(vec![]);
        }
        if frame.data.trim() == "[DONE]" {
            self.terminal = true;
            let reason = self.finish_reason.as_deref().unwrap_or("unknown");
            return Ok(vec![json!({"kind":"completed", "finishReason":reason,
                "status":if reason == "stop" {"completed"} else {"incomplete"}})]);
        }
        let value: Value = serde_json::from_str(&frame.data).map_err(|_| "invalid_event_json")?;
        if value.get("error").is_some_and(|v| !v.is_null()) {
            self.terminal = true;
            return Ok(vec![json!({"kind":"failed", "code":"remote_error"})]);
        }
        let mut out = vec![];
        match self.protocol {
            Protocol::ChatCompletions => {
                if let Some(choices) = value["choices"].as_array() {
                    for choice in choices {
                        if choice["index"].as_u64().unwrap_or(0) != 0 {
                            continue;
                        }
                        let delta = &choice["delta"];
                        if delta["tool_calls"]
                            .as_array()
                            .is_some_and(|v| !v.is_empty())
                        {
                            self.terminal = true;
                            return Ok(vec![
                                json!({"kind":"failed", "code":"unsupported_tool_call"}),
                            ]);
                        }
                        if let Some(text) = delta["content"].as_str() {
                            out.push(json!({"kind":"answer.delta", "text":text}));
                        }
                        if let Some(text) = delta["refusal"].as_str() {
                            out.push(json!({"kind":"refusal.delta", "text":text}));
                        }
                        // Opt-in only, with a provider-declared PUBLIC reasoning field. Not inferred from answer tags.
                        if let ReasoningPolicy::ProviderField(field) = &self.policy {
                            if let Some(text) = delta[field].as_str() {
                                out.push(json!({"kind":"reasoning.provider.delta", "text":text}));
                            }
                        }
                        if let Some(reason) = choice["finish_reason"].as_str() {
                            self.finish_reason = Some(reason.to_owned());
                            out.push(json!({"kind":"finish", "reason":reason}));
                        }
                    }
                }
                if value["usage"].is_object() {
                    out.push(json!({"kind":"usage", "usage":value["usage"]}));
                }
            }
            Protocol::Responses => {
                let kind = value["type"].as_str().unwrap_or(&frame.event);
                match kind {
                    "response.output_text.delta" => {
                        if let Some(text) = value["delta"].as_str() {
                            out.push(json!({"kind":"answer.delta", "text":text, "itemId":value["item_id"], "outputIndex":value["output_index"], "contentIndex":value["content_index"]}));
                        }
                    }
                    "response.reasoning_summary_text.delta"
                        if !matches!(self.policy, ReasoningPolicy::None) =>
                    {
                        if let Some(text) = value["delta"].as_str() {
                            out.push(json!({"kind":"reasoning.summary.delta", "text":text,
                            "itemId":value["item_id"], "summaryIndex":value["summary_index"]}));
                        }
                    }
                    "response.refusal.delta" => {
                        if let Some(text) = value["delta"].as_str() {
                            out.push(json!({"kind":"refusal.delta", "text":text}));
                        }
                    }
                    "response.completed" | "response.incomplete" => {
                        if value["response"]["usage"].is_object() {
                            out.push(json!({"kind":"usage", "usage":value["response"]["usage"]}));
                        }
                        self.terminal = true;
                        let complete = kind == "response.completed"
                            && value["response"]["status"].as_str().unwrap_or("completed")
                                == "completed";
                        out.push(json!({"kind":"completed", "finishReason":if complete {"stop"} else {"incomplete"},
                            "status":if complete {"completed"} else {"incomplete"}}));
                    }
                    "response.failed" | "error" => {
                        self.terminal = true;
                        out.push(json!({"kind":"failed", "code":"remote_error"}));
                    }
                    "response.output_item.added" if value["item"]["type"] == "function_call" => {
                        self.terminal = true;
                        out.push(json!({"kind":"failed", "code":"unsupported_tool_call"}));
                    }
                    // Opaque reasoning, encrypted content and raw reasoning text are never treated as user-facing summaries.
                    _ => {}
                }
            }
        }
        Ok(out)
    }
    pub fn is_terminal(&self) -> bool {
        self.terminal
    }
    pub fn finish(&self) -> Result<(), &'static str> {
        if self.terminal {
            Ok(())
        } else {
            Err("stream_ended_without_terminal_event")
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    fn frame(data: &str) -> SseFrame {
        SseFrame {
            event: String::new(),
            data: data.into(),
        }
    }
    #[test]
    fn byte_fragmentation_unicode_bom_crlf_comments_multiline() {
        let mut decoder = SseDecoder::default();
        let mut events = vec![];
        for byte in
            "\u{feff}: heartbeat\r\nevent: test\r\ndata: 浣犲ソ\r\ndata: world\r\n\r\n".as_bytes()
        {
            events.extend(decoder.push(&[*byte]).unwrap());
        }
        assert_eq!(
            events,
            vec![SseFrame {
                event: "test".into(),
                data: "浣犲ソ\nworld".into()
            }]
        );
        assert!(decoder.finish().is_ok());
    }
    #[test]
    fn eof_and_size_errors_never_invent_completion() {
        let mut d = SseDecoder::default();
        d.push(b"data: {}").unwrap();
        assert_eq!(d.finish(), Err("truncated_sse_event"));
        let mut d = SseDecoder::default();
        assert_eq!(
            d.push(&vec![b'x'; MAX_EVENT_BYTES + 1]),
            Err("event_too_large")
        );
        assert_eq!(d.push(b"\n\n"), Err("decoder_failed"));
    }
    #[test]
    fn chat_body_usage_finish_and_done() {
        let mut n = EventNormalizer::new(Protocol::ChatCompletions, ReasoningPolicy::Summary);
        assert_eq!(
            n.push(frame(r#"{"choices":[{"delta":{"content":"Hi"}}]}"#))
                .unwrap()[0]["text"],
            "Hi"
        );
        n.push(frame(r#"{"choices":[{"finish_reason":"stop"}]}"#))
            .unwrap();
        assert_eq!(
            n.push(frame(r#"{"choices":[],"usage":{"total_tokens":5}}"#))
                .unwrap()[0]["kind"],
            "usage"
        );
        assert_eq!(n.push(frame("[DONE]")).unwrap()[0]["status"], "completed");
        assert!(n.finish().is_ok());
    }
    #[test]
    fn public_provider_reasoning_requires_explicit_capability() {
        let data = r#"{"choices":[{"delta":{"reasoning_content":"public explanation","content":"Answer"}}]}"#;
        let mut n = EventNormalizer::new(Protocol::ChatCompletions, ReasoningPolicy::Summary);
        assert_eq!(n.push(frame(data)).unwrap().len(), 1);
        let mut n = EventNormalizer::new(
            Protocol::ChatCompletions,
            ReasoningPolicy::ProviderField("reasoning_content".into()),
        );
        assert_eq!(
            n.push(frame(data)).unwrap()[1]["kind"],
            "reasoning.provider.delta"
        );
    }
    #[test]
    fn responses_summary_is_separate_and_raw_reasoning_is_not_summary() {
        let mut n = EventNormalizer::new(Protocol::Responses, ReasoningPolicy::Summary);
        assert!(n
            .push(frame(
                r#"{"type":"response.reasoning_text.delta","delta":"not exposed"}"#
            ))
            .unwrap()
            .is_empty());
        assert_eq!(n.push(frame(r#"{"type":"response.reasoning_summary_text.delta","delta":"Summary","summary_index":0}"#)).unwrap()[0]["kind"],"reasoning.summary.delta");
        assert_eq!(
            n.push(frame(
                r#"{"type":"response.output_text.delta","delta":"Answer"}"#
            ))
            .unwrap()[0]["kind"],
            "answer.delta"
        );
        assert_eq!(
            n.push(frame(
                r#"{"type":"response.completed","response":{"status":"completed"}}"#
            ))
            .unwrap()[0]["status"],
            "completed"
        );
    }
    #[test]
    fn truncated_or_missing_terminal_stays_incomplete() {
        let mut n = EventNormalizer::new(Protocol::ChatCompletions, ReasoningPolicy::None);
        n.push(frame(r#"{"choices":[{"finish_reason":"length"}]}"#))
            .unwrap();
        assert!(n.finish().is_err());
        assert_eq!(n.push(frame("[DONE]")).unwrap()[0]["status"], "incomplete");
    }
    #[test]
    fn malformed_error_and_unexpected_tools_do_not_become_answers() {
        let mut n = EventNormalizer::new(Protocol::ChatCompletions, ReasoningPolicy::None);
        assert!(n.push(frame("{broken")).is_err());
        assert_eq!(
            n.push(frame(r#"{"error":{"message":"private details"}}"#))
                .unwrap(),
            vec![json!({"kind":"failed","code":"remote_error"})]
        );
        let mut n = EventNormalizer::new(Protocol::ChatCompletions, ReasoningPolicy::None);
        assert_eq!(
            n.push(frame(r#"{"choices":[{"delta":{"tool_calls":[{}]}}]}"#))
                .unwrap()[0]["code"],
            "unsupported_tool_call"
        );
    }
}
