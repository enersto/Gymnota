package com.example.myfit.flutter

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.myfit.data.ai.*
import com.example.myfit.data.api.*
import com.google.gson.Gson
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AiStreamContractTest {
    @Test fun streamIsOptInAndOldCallsKeepTheirOriginalShape() {
        val gson = Gson()
        val messages = listOf(ChatMessage("user", "test"))
        val old = gson.toJsonTree(ChatRequest("configured-model", messages)).asJsonObject
        assertFalse(old.has("stream"))
        val stream = gson.toJsonTree(StreamingChatRequest("configured-model", messages)).asJsonObject
        assertTrue(stream["stream"].asBoolean)
        assertFalse(stream.has("temperature")); assertFalse(stream.has("stream_options"))
        assertEquals("configured-model", stream["model"].asString)
    }
    @Test fun hostOwnsRequestIdentityAndCapabilitiesDoNotAliasTheAnswerField() {
        val envelope = AiStreamEnvelope("real", 1, mapOf("kind" to "answer.delta", "requestId" to "remote", "seq" to 999))
        assertEquals("real", envelope.toMap()["requestId"])
        assertEquals(1L, envelope.toMap()["seq"])
        var rejected = false
        try { AiStreamCapabilities(AiStreamProtocol.CHAT_COMPLETIONS, true, publicReasoningField = "content") }
        catch (_: IllegalArgumentException) { rejected = true }
        assertTrue(rejected)
    }
}
