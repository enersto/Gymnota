package com.example.myfit.data

import com.example.myfit.viewmodel.MainViewModel.TimerState
import com.google.gson.Gson
import org.json.JSONObject

internal object RustTimer {
    private val gson = Gson()
    private val loaded by lazy { System.loadLibrary("gymnota_history_core"); true }
    @JvmStatic private external fun transitionNative(input: String): String?
    data class Result(val state: TimerState, val beep: Boolean, val finished: Boolean)

    fun transition(action: String, state: TimerState, now: Long, options: Map<String, Any> = emptyMap()): Result {
        check(loaded)
        val request = JSONObject().put("action", action).put("state", JSONObject(gson.toJson(state)))
            .put("now", now).put("options", JSONObject(options))
        return gson.fromJson(checkNotNull(transitionNative(request.toString())), Result::class.java)
    }
}
