package com.example.myfit.flutter

import com.example.myfit.BuildConfig
import org.json.JSONArray
import org.json.JSONObject

/** JNI stays at the existing Android bridge. Rust owns no database handles or Android objects. */
internal object RustHistoryCore {
    private val loaded by lazy {
        if (!BuildConfig.RUST_HISTORY_ENABLED) false
        else try { System.loadLibrary("gymnota_history_core"); true } catch (_: LinkageError) { false }
    }
    @JvmStatic private external fun processNative(input: String): String

    @Suppress("UNCHECKED_CAST")
    fun process(raw: Map<String, Any>, enableRust: Boolean = true): Map<String, Any?> {
        if (enableRust && loaded) {
            try {
                val output = JSONObject(processNative(JSONObject(raw).toString()))
                check(output.optInt("schemaVersion") == 1 && !output.has("error"))
                val result = value(output.getJSONObject("snapshot")) as Map<String, Any?>
                return result + mapOf("charts" to value(output.getJSONObject("charts")), "engine" to "rust", "summary" to value(output.getJSONObject("summary")),
                    "byDay" to value(output.getJSONArray("byDay")), "byMonth" to value(output.getJSONArray("byMonth")),
                    "byExercise" to value(output.getJSONArray("byExercise")), "warnings" to value(output.getJSONArray("warnings")),
                    "legacyCharts" to value(output.getJSONArray("legacyCharts")))
            } catch (_: Exception) { /* Existing snapshot decoding remains the availability fallback. */ }
            catch (_: LinkageError) { }
        }
        return decodeLegacy(raw) + mapOf("engine" to "kotlin-fallback")
    }

    @Suppress("UNCHECKED_CAST")
    fun decodeLegacy(raw: Map<String, Any>): Map<String, Any?> {
        val tasks = raw["tasks"] as List<Map<String, Any?>>
        val normalized = tasks.map { task ->
            val sets = task["sets"] as? String ?: ""
            val array = if (sets.isBlank()) JSONArray() else JSONArray(sets)
            val rows = (0 until array.length()).map { index ->
                val set = array.getJSONObject(index)
                listOf("setNumber", "weightOrDuration", "reps", "rightWeight", "rightReps").associateWith { key ->
                    if (set.isNull(key)) null else set.optString(key)
                }
            }
            task + mapOf("sets" to rows)
        }
        return mapOf("tasks" to normalized, "weights" to raw["weights"])
    }

    internal fun value(v: Any?): Any? = when(v) {
        null, JSONObject.NULL -> null
        is JSONObject -> v.keys().asSequence().associateWith { value(v.get(it)) }
        is JSONArray -> (0 until v.length()).map { value(v.get(it)) }
        else -> v
    }
}
