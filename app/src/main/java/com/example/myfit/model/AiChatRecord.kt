package com.example.myfit.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "ai_chat_history")
data class AiChatRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val role: String, // "user" 或 "assistant"
    val content: String,
    val userGoal: String? = null, // 记录当时用户的输入目标
    val modelUsed: String? = null // 记录使用的模型
)