package com.example.myfit.data

import com.example.myfit.BuildConfig
import org.json.JSONObject

internal object RustWorkout {
    private val loaded by lazy {
        if (!BuildConfig.RUST_HISTORY_ENABLED) false
        else try { System.loadLibrary("gymnota_history_core"); true } catch (_: LinkageError) { false }
    }

    @JvmStatic private external fun storeNative(input: String): String?

    @JvmStatic private external fun historyNative(path: String): String?

    @JvmStatic private external fun queryNative(input: String): String?

    @JvmStatic private external fun backupNative(input: String): String?

    @JvmStatic private external fun initializeNative(input: String): String?

    fun initialize(request: JSONObject) {
        check(loaded) { "Rust initializer unavailable" }
        val result = JSONObject(checkNotNull(initializeNative(request.toString())))
        check(!result.has("error")) { result.optString("error") }
        check(result.getInt("version") == 19)
    }

    fun backup(request: JSONObject): JSONObject {
        check(loaded) { "Rust backup unavailable" }
        val result = JSONObject(checkNotNull(backupNative(request.toString())) { "Rust backup response missing" })
        check(!result.has("error")) { result.optString("error") }
        return result
    }

    fun query(request: JSONObject): String {
        check(loaded) { "Rust query unavailable" }
        val text = checkNotNull(queryNative(request.toString())) { "Rust query result missing" }
        check(!text.startsWith("{")) { JSONObject(text).optString("error") }
        return text
    }

    fun history(path: String): String {
        check(loaded) { "Rust history store unavailable" }
        val text = checkNotNull(historyNative(path)) { "Rust history response missing" }
        val response = JSONObject(text)
        check(!response.has("error")) { response.optString("error") }
        return text
    }

    fun store(input: JSONObject): String {
        check(loaded) { "Rust workout store unavailable" }
        val result = checkNotNull(storeNative(input.toString())) { "Rust workout store returned no result" }
        check(!result.startsWith("{")) { JSONObject(result).getString("error") }
        return result
    }
}
