package com.example.myfit.flutter

import android.database.Cursor
import org.json.JSONArray

/** Explicit whitelist: API keys, AI conversation and settings never cross the bridge. */
internal object HistorySnapshot {
    fun read(encodedSets: Boolean = false, chartFields: Boolean = false, query: (String) -> Cursor): Map<String, Any> {
        val tasks = query("SELECT * FROM workout_tasks WHERE isCompleted = 1 ORDER BY date DESC, id DESC").use { c ->
            buildList {
                while (c.moveToNext()) {
                    fun s(key: String): String = c.getColumnIndex(key).let { if (it < 0 || c.isNull(it)) "" else c.getString(it) }
                    val rawSets = s("sets")
                    val sets = if (encodedSets || rawSets.isBlank()) JSONArray() else JSONArray(rawSets)
                    val row = mapOf("id" to s("id"), "date" to s("date"), "name" to s("name"),
                        "category" to s("category"), "logType" to (s("logType").toIntOrNull() ?: 0),
                        "unilateral" to (s("isUnilateral") == "1"), "target" to s("target"), "actualWeight" to s("actualWeight"),
                        "sets" to if (encodedSets) rawSets else (0 until sets.length()).map { i ->
                            val set = sets.getJSONObject(i)
                            listOf("setNumber", "weightOrDuration", "reps", "rightWeight", "rightReps").associateWith { k ->
                                if (set.isNull(k)) null else set.optString(k)
                            }
                        })
                    add(if (chartFields) row + mapOf("bodyPart" to s("bodyPart")) else row)
                }
            }
        }
        val weights = query("SELECT * FROM weight_records ORDER BY date DESC").use { c ->
            buildList {
                while (c.moveToNext()) {
                    val row = mutableMapOf<String, Any?>("date" to c.getString(c.getColumnIndexOrThrow("date")), "weight" to c.getDouble(c.getColumnIndexOrThrow("weight")))
                    if (chartFields) for (key in listOf("bodyFatKg", "skeletalMuscleKg", "bodyWaterPercentage", "waistCircumference", "hipCircumference")) {
                        val index = c.getColumnIndex(key)
                        row[key] = if (index < 0 || c.isNull(index)) null else c.getDouble(index)
                    }
                    add(row)
                }
            }
        }
        val output = mutableMapOf<String, Any>("tasks" to tasks, "weights" to weights)
        if (chartFields) {
            var height = 0.0; var age = 0; var gender = 0
            try {
                query("SELECT * FROM app_settings WHERE id = 0 LIMIT 1").use { c ->
                    if (c.moveToFirst()) {
                        fun number(key: String): Double = c.getColumnIndex(key).let { if (it < 0 || c.isNull(it)) 0.0 else c.getDouble(it) }
                        height = number("height"); age = number("age").toInt(); gender = number("gender").toInt()
                    }
                }
            } catch (_: android.database.sqlite.SQLiteException) { /* Optional in old backups. */ }
            output["profile"] = mapOf("height" to height, "age" to age, "gender" to gender)
        }
        return output
    }
}
