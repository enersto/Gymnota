package com.example.myfit.data.ai

import com.example.myfit.BuildConfig
import org.json.JSONObject

/** One native parser per HTTP response. Keys, messages and images never cross this JNI boundary. */
internal class RustAiStream(capabilities: AiStreamCapabilities) : AutoCloseable {
    companion object {
        private val loaded by lazy {
            BuildConfig.RUST_HISTORY_ENABLED && try { System.loadLibrary("gymnota_history_core"); true } catch (_: LinkageError) { false }
        }
        @JvmStatic private external fun createNative(config: String): Long
        @JvmStatic private external fun pushNative(handle: Long, bytes: ByteArray, eof: Boolean): String
        @JvmStatic private external fun closeNative(handle: Long)
    }
    private var handle: Long
    init {
        if (!loaded) throw AiStreamFailure("parser_unavailable")
        val config = JSONObject().put("protocol", if (capabilities.protocol == AiStreamProtocol.CHAT_COMPLETIONS) "chat" else "responses")
            .put("reasoningSummary", capabilities.reasoningSummary)
        capabilities.publicReasoningField?.let { config.put("publicReasoningField", it) }
        handle = try { createNative(config.toString()) } catch (_: LinkageError) { throw AiStreamFailure("parser_unavailable") }
        if (handle == 0L) throw AiStreamFailure("parser_unavailable")
    }
    @Synchronized fun push(bytes: ByteArray, eof: Boolean = false): JSONObject {
        if (handle == 0L) throw AiStreamFailure("closed_stream")
        val value = JSONObject(pushNative(handle, bytes, eof))
        if (value.has("error")) throw AiStreamFailure(value.getString("error"))
        return value
    }
    @Synchronized override fun close() { if (handle != 0L) { closeNative(handle); handle = 0L } }
}

internal class AiStreamFailure(val code: String) : java.io.IOException(code)
