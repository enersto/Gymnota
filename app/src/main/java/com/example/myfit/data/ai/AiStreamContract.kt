package com.example.myfit.data.ai

/** Preparation contract only; the existing coach screen still uses its current one-shot calls. */
enum class AiStreamProtocol { CHAT_COMPLETIONS, RESPONSES }
data class AiStreamCapabilities(
    val protocol: AiStreamProtocol,
    val streaming: Boolean,
    val usageChunks: Boolean = false,
    val reasoningSummary: Boolean = false,
    val publicReasoningField: String? = null
) {
    init {
        require(publicReasoningField == null ||
            (publicReasoningField.length in 1..64 && publicReasoningField !in setOf("content", "role", "refusal")))
        require(protocol != AiStreamProtocol.RESPONSES || publicReasoningField == null)
    }
}

/** Host-assigned identity/order must not be overridden by a remote JSON payload. */
data class AiStreamEnvelope(val requestId: String, val sequence: Long, val payload: Map<String, Any?>) {
    init { require(requestId.isNotBlank()); require(sequence >= 0); require(payload["kind"] is String) }
    fun toMap(): Map<String, Any?> = payload + mapOf("requestId" to requestId, "seq" to sequence)
}
