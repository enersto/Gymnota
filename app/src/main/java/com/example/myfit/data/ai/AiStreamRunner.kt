package com.example.myfit.data.ai

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.ResponseBody
import org.json.JSONObject
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Immutable snapshots are safe for Compose now and a future Flutter EventChannel. */
data class AiStreamSnapshot(
    val requestId: String = "", val sequence: Long = -1, val status: String = "idle",
    val answer: String = "", val reasoningSummary: String = "", val providerReasoning: String = "",
    val errorCode: String? = null
)

internal object AiStreamRunner {
    private const val MAX_TEXT = 1_000_000
    suspend fun run(call: Call<ResponseBody>, capabilities: AiStreamCapabilities,
        requestId: String = UUID.randomUUID().toString(), onUpdate: (AiStreamSnapshot) -> Unit = {}): String {
        RustAiStream(capabilities).use { parser ->
            return suspendCancellableCoroutine { continuation ->
                continuation.invokeOnCancellation { call.cancel() }
                call.enqueue(object : Callback<ResponseBody> {
                    override fun onFailure(call: Call<ResponseBody>, t: Throwable) {
                        if (continuation.isActive) continuation.resumeWithException(AiStreamFailure("network_error"))
                    }
                    override fun onResponse(call: Call<ResponseBody>, response: Response<ResponseBody>) {
                        try {
                            if (!response.isSuccessful) {
                                response.errorBody()?.close(); throw AiStreamFailure("http_${response.code()}")
                            }
                            val body = response.body() ?: throw AiStreamFailure("empty_response")
                            val answer = body.use {
                                if (it.contentType()?.subtype?.lowercase() != "event-stream") throw AiStreamFailure("stream_unsupported")
                                consume(it, parser, requestId, { continuation.isActive }, onUpdate)
                            }
                            if (continuation.isActive) continuation.resume(answer)
                        } catch (e: Exception) {
                            if (continuation.isActive) continuation.resumeWithException(
                                if (e is AiStreamFailure) e else AiStreamFailure("network_error"))
                        }
                    }
                })
            }
        }
    }
    private fun consume(body: ResponseBody, parser: RustAiStream, id: String, active: () -> Boolean,
        onUpdate: (AiStreamSnapshot) -> Unit): String {
        val answer = StringBuilder(); val summary = StringBuilder(); val reasoning = StringBuilder()
        var seq = -1L; var status = "connecting"; var terminal = false; var refused = false
        var failure: String? = null; var lastPublish = 0L
        fun publish(force: Boolean = false) {
            val now = System.nanoTime()
            if (active() && (force || now - lastPublish >= 40_000_000)) {
                lastPublish = now
                onUpdate(AiStreamSnapshot(id, seq, status, answer.toString(), summary.toString(), reasoning.toString(), failure))
            }
        }
        fun accept(batch: JSONObject) {
            val events = batch.getJSONArray("events")
            for (i in 0 until events.length()) {
                val event = events.getJSONObject(i); seq++
                val text = event.optString("text")
                if (answer.length.toLong() + summary.length + reasoning.length + text.length > MAX_TEXT) throw AiStreamFailure("output_too_large")
                when (event.getString("kind")) {
                    "answer.delta" -> { answer.append(text); status = "answering" }
                    "reasoning.summary.delta" -> { summary.append(text); if (answer.isEmpty()) status = "reasoning" }
                    "reasoning.provider.delta" -> { reasoning.append(text); if (answer.isEmpty()) status = "reasoning" }
                    "refusal.delta" -> if (text.isNotEmpty()) { refused = true; failure = "refused" }
                    "failed" -> { failure = event.optString("code", "remote_error"); status = "failed" }
                    "completed" -> {
                        status = if (event.optString("status") == "completed" && event.optString("finishReason") == "stop" && !refused && answer.isNotBlank()) "completed" else "incomplete"
                    }
                }
            }
            terminal = batch.optBoolean("terminal")
            publish(terminal)
        }
        try {
            val source = body.source(); val bytes = ByteArray(8192)
            while (active() && !terminal) {
                val count = source.read(bytes)
                if (count < 0) { accept(parser.push(byteArrayOf(), true)); break }
                accept(parser.push(bytes.copyOf(count)))
            }
            if (!active()) throw AiStreamFailure("cancelled")
            if (failure != null) throw AiStreamFailure(failure!!)
            if (status != "completed") throw AiStreamFailure("incomplete")
            return answer.toString()
        } catch (e: Exception) {
            seq++ // A local terminal failure must also advance the snapshot sequence.
            failure = if (e is AiStreamFailure) e.code else "network_error"
            status = if (failure in setOf("incomplete", "truncated_sse_event", "stream_ended_without_terminal_event")) "incomplete" else "failed"
            publish(true); throw e
        }
    }
}
