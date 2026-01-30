package com.example.myfit.data.api

import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.POST

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
    val message: ChatMessage
)

interface AiService {
    @POST("v1/chat/completions") // 标准 OpenAI 路径
    suspend fun generateChatCompletion(
        @Header("Authorization") authHeader: String,
        @Body request: ChatRequest
    ): ChatResponse
}