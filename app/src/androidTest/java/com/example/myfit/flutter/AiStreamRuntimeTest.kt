package com.example.myfit.flutter

import android.os.Looper
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.myfit.data.ai.*
import com.example.myfit.data.api.*
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

@RunWith(AndroidJUnit4::class)
class AiStreamRuntimeTest {
    private val caps = AiStreamCapabilities(AiStreamProtocol.CHAT_COMPLETIONS, true)
    private fun delta(text: String) = "data: {\"choices\":[{\"index\":0,\"delta\":{\"content\":\"$text\"}}]}\n\n"
    private val done = "data: {\"choices\":[{\"index\":0,\"delta\":{},\"finish_reason\":\"stop\"}]}\n\ndata: [DONE]\n\n"
    private fun body(text: String, type: String = "text/event-stream") = text.toResponseBody(type.toMediaType())

    private class FakeCall(private val body: ResponseBody, private val code: Int = 200) : Call<ResponseBody> {
        private var executed = false
        @Volatile private var cancelled = false
        val starts = AtomicInteger()
        override fun enqueue(callback: Callback<ResponseBody>) {
            executed = true; starts.incrementAndGet()
            Thread({ callback.onResponse(this, if (code == 200) retrofit2.Response.success(body) else retrofit2.Response.error(code, body)) }, "test-stream").start()
        }
        override fun cancel() { cancelled = true; body.close() }
        override fun isCanceled() = cancelled
        override fun isExecuted() = executed
        override fun execute(): retrofit2.Response<ResponseBody> = error("enqueue only")
        override fun clone(): Call<ResponseBody> = FakeCall(body, code)
        override fun request() = Request.Builder().url("https://example.invalid/unused").build()
        override fun timeout() = Timeout.NONE
    }
    private class GatedBody(first: String, private val tail: String) : ResponseBody() {
        val gate = CountDownLatch(1)
        val closed = AtomicBoolean()
        private val buffer = Buffer().writeUtf8(first)
        private var waited = false
        private val input = object : Source {
            override fun timeout() = Timeout.NONE
            override fun read(sink: Buffer, byteCount: Long): Long {
                check(Looper.myLooper() != Looper.getMainLooper()) { "Network stream read on main thread" }
                if (buffer.size == 0L && !waited) {
                    waited = true
                    check(gate.await(5, TimeUnit.SECONDS)) { "test stream timed out" }
                    if (closed.get()) throw java.io.IOException("closed")
                    buffer.writeUtf8(tail)
                }
                return buffer.read(sink, byteCount)
            }
            override fun close() { closed.set(true); gate.countDown() }
        }.buffer()
        override fun contentType() = "text/event-stream".toMediaType()
        override fun contentLength() = -1L
        override fun source() = input
    }

    @Test fun jniOwnsSplitUtf8AndClosedHandleIsRejected() {
        val parser = RustAiStream(caps)
        val output = StringBuilder()
        for (byte in (delta("你好") + done).toByteArray()) {
            val events = parser.push(byteArrayOf(byte)).getJSONArray("events")
            for (i in 0 until events.length()) if (events.getJSONObject(i).optString("kind") == "answer.delta") output.append(events.getJSONObject(i).getString("text"))
        }
        assertEquals("你好", output.toString()); parser.push(byteArrayOf(), true)
        parser.close(); parser.close()
        try { parser.push(byteArrayOf()); fail("Closed parser accepted bytes") } catch (e: AiStreamFailure) { assertEquals("closed_stream", e.code) }
    }
    @Test fun actualRetrofitRequestShowsPartialBeforeDoneAndNeverReadsOnMain() = runBlocking {
        val response = GatedBody(delta("First"), delta(" second") + done)
        val count = AtomicInteger()
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            count.incrementAndGet()
            val sent = Buffer(); chain.request().body!!.writeTo(sent)
            val json = org.json.JSONObject(sent.readUtf8())
            assertTrue(json.getBoolean("stream")); assertEquals("user-configured-model", json.getString("model"))
            okhttp3.Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK").body(response).build()
        }.build()
        val api = Retrofit.Builder().baseUrl("https://example.invalid/").client(client).addConverterFactory(GsonConverterFactory.create()).build().create(AiService::class.java)
        val first = CompletableDeferred<AiStreamSnapshot>()
        val request = async(Dispatchers.IO) {
            AiStreamRunner.run(api.streamChatCompletion("Bearer fake-test-only", StreamingChatRequest("user-configured-model", listOf(ChatMessage("user", "test")))), caps) {
                assertNotEquals(Looper.getMainLooper(), Looper.myLooper())
                if (it.answer == "First") first.complete(it)
            }
        }
        try {
            assertEquals("answering", withTimeout(5000) { first.await() }.status)
            assertFalse(request.isCompleted)
            response.gate.countDown()
            assertEquals("First second", withTimeout(5000) { request.await() })
            assertTrue(response.closed.get()); assertEquals(1, count.get())
        } finally { response.gate.countDown(); request.cancel(); client.dispatcher.executorService.shutdown(); client.connectionPool.evictAll() }
    }
    @Test fun cancellationCancelsCallClosesBodyAndPreventsLateUpdates() = runBlocking {
        val response = GatedBody(delta("Partial"), delta("late") + done)
        val call = FakeCall(response); val first = CompletableDeferred<Unit>(); val updates = AtomicInteger()
        val job = launch(Dispatchers.IO) { AiStreamRunner.run(call, caps) { updates.incrementAndGet(); first.complete(Unit) } }
        withTimeout(5000) { first.await() }
        job.cancelAndJoin(); val before = updates.get(); response.gate.countDown(); delay(100)
        assertTrue(call.isCanceled); assertTrue(response.closed.get()); assertEquals(before, updates.get()); assertEquals(1, call.starts.get())
    }
    @Test fun truncatedLengthRefusalAndErrorNeverReturnAnImportableAnswer() = runBlocking {
        val samples = listOf(
            delta("partial"),
            delta("partial") + "data: {\"choices\":[{\"finish_reason\":\"length\"}]}\n\ndata: [DONE]\n\n",
            "data: {\"choices\":[{\"delta\":{\"refusal\":\"No\"},\"finish_reason\":\"stop\"}]}\n\ndata: [DONE]\n\n",
            delta("partial") + "data: {\"error\":{\"message\":\"private server text\"}}\n\n"
        )
        for (sample in samples) {
            val call = FakeCall(body(sample)); val states = mutableListOf<AiStreamSnapshot>()
            try { AiStreamRunner.run(call, caps, onUpdate = { states.add(it) }); fail("Incomplete output returned as success") }
            catch (_: AiStreamFailure) { }
            assertTrue(states.last().status in setOf("incomplete", "failed"))
            assertFalse(states.any { it.answer.contains("private server text") }); assertEquals(1, call.starts.get())
        }
    }
    @Test fun httpAndNonStreamFailuresNeverReplayTheRequest() = runBlocking {
        for ((response, code) in listOf(body("private", "application/json") to 400, body("{}", "application/json") to 200)) {
            val call = FakeCall(response, code)
            try { AiStreamRunner.run(call, caps); fail("Unsupported stream accepted") } catch (e: AiStreamFailure) {
                assertEquals(if (code == 400) "http_400" else "stream_unsupported", e.code)
            }
            assertEquals(1, call.starts.get())
        }
    }
    @Test fun outputLimitPreservesBoundedPartialAndRejectsEmptySuccess() = runBlocking {
        val chunk = "a".repeat(600_000)
        var last: AiStreamSnapshot? = null
        try { AiStreamRunner.run(FakeCall(body(delta(chunk) + delta(chunk) + done)), caps) { last = it }; fail("Unbounded text accepted") }
        catch (e: AiStreamFailure) { assertEquals("output_too_large", e.code) }
        assertTrue(last!!.answer.length <= 1_000_000); assertEquals("failed", last!!.status)
        try { AiStreamRunner.run(FakeCall(body(done)), caps); fail("Empty answer accepted") }
        catch (e: AiStreamFailure) { assertEquals("incomplete", e.code) }
    }
    @Test fun publicReasoningIsOptInAndKeptSeparateFromTheAnswer() = runBlocking {
        val stream = "data: {\"choices\":[{\"delta\":{\"reasoning_content\":\"public explanation\",\"content\":\"answer\"}}]}\n\n" + done
        var state: AiStreamSnapshot? = null
        AiStreamRunner.run(FakeCall(body(stream)), caps) { state = it }
        assertEquals("", state!!.providerReasoning)
        AiStreamRunner.run(FakeCall(body(stream)), caps.copy(publicReasoningField = "reasoning_content")) { state = it }
        assertEquals("answer", state!!.answer); assertEquals("public explanation", state!!.providerReasoning)
    }
    @Test fun concurrentResponsesKeepTheirTextAndIdentitySeparate() = runBlocking {
        val states = java.util.Collections.synchronizedList(mutableListOf<AiStreamSnapshot>())
        val a = async(Dispatchers.IO) { AiStreamRunner.run(FakeCall(body(delta("A") + done)), caps, "a") { states.add(it) } }
        val b = async(Dispatchers.IO) { AiStreamRunner.run(FakeCall(body(delta("B") + done)), caps, "b") { states.add(it) } }
        assertEquals("A", a.await()); assertEquals("B", b.await())
        assertTrue(states.filter { it.requestId == "a" }.all { it.answer == "A" })
        assertTrue(states.filter { it.requestId == "b" }.all { it.answer == "B" })
    }
}
