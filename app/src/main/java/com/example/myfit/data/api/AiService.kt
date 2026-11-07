package com.example.myfit.data.api

import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.Streaming
import retrofit2.SkipCallbackExecutor
import retrofit2.Call
import okhttp3.ResponseBody
import com.google.gson.annotations.SerializedName

// 请求体
data class ChatRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val temperature: Double = 0.7
)

data class ChatMessage(
    val role: String, // "system", "user", "assistant"
    val content: Any
)

// 响应体
data class ChatResponse(
    val choices: List<Choice>
)

data class Choice(
    val message: ChatMessage,
    @SerializedName("finish_reason") val finishReason: String? = null
)

// Explicit opt-in streaming request; optional fields stay absent for compatibility.
data class StreamingChatRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val stream: Boolean = true,
    val temperature: Double? = null,
    @SerializedName("stream_options") val streamOptions: Map<String, Boolean>? = null
) { init { require(stream) } }

interface AiService {
    @POST("v1/chat/completions") // 标准 OpenAI 路径
    suspend fun generateChatCompletion(
        @Header("Authorization") authHeader: String,
        @Body request: ChatRequest
    ): ChatResponse
    // AiStreamRunner reads on the OkHttp callback worker, cancels the Call with its coroutine,
    // and normalizes response bytes through the owned Rust stream session.
    @SkipCallbackExecutor // Stream reads must remain on the OkHttp worker, never Android main.
    @Streaming
    @POST("v1/chat/completions")
    fun streamChatCompletion(
        @Header("Authorization") authHeader: String,
        @Body request: StreamingChatRequest
    ): Call<ResponseBody>

}