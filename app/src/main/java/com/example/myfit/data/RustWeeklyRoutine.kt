package com.example.myfit.data

import com.example.myfit.BuildConfig
import com.example.myfit.model.WeeklyRoutineItem
import com.example.myfit.model.WorkoutSet
import com.example.myfit.model.WorkoutTask
import org.json.JSONArray
import org.json.JSONObject

internal object RustWeeklyRoutine {
    private val loaded by lazy {
        BuildConfig.RUST_HISTORY_ENABLED && try {
            System.loadLibrary("gymnota_history_core")
            true
        } catch (_: LinkageError) { false }
    }

    @JvmStatic private external fun executeNative(input: String): String?

    private fun execute(input: JSONObject): JSONArray? {
        if (!loaded) return null
        return try {
            executeNative(input.toString())?.let(::JSONArray)
        } catch (_: Exception) { null } catch (_: LinkageError) { null }
    }

    fun rows(path: String, day: Int, action: String = "list", id: Long? = null): JSONArray? =
        execute(JSONObject().put("action", action).put("path", path).put("day", day).put("id", id))

    fun summaries(rows: JSONArray): List<Map<String, Any?>> {
        return List(rows.length()) { index ->
            val row = rows.getJSONObject(index)
            mapOf("id" to row.getLong("id"), "name" to row.getString("name"), "target" to row.getString("target"))
        }
    }

    fun plan(date: String, rows: List<WeeklyRoutineItem>): List<WorkoutTask>? {
        val request = JSONArray()
        for (item in rows) {
            request.put(JSONObject().put("templateId", item.templateId).put("name", item.name)
                .put("category", item.category).put("target", item.target).put("bodyPart", item.bodyPart)
                .put("equipment", item.equipment).put("isUnilateral", item.isUnilateral).put("logType", item.logType))
        }
        val drafts = execute(JSONObject().put("action", "apply").put("date", date).put("rows", request))
            ?: return null
        return List(drafts.length()) { index ->
            val draft = drafts.getJSONObject(index)
            val sets = draft.getJSONArray("sets")
            WorkoutTask(
                date = draft.getString("date"),
                templateId = draft.getLong("templateId"),
                name = draft.getString("name"),
                category = draft.getString("category"),
                target = draft.getString("target"),
                bodyPart = draft.getString("bodyPart"),
                equipment = draft.getString("equipment"),
                isUnilateral = draft.getBoolean("isUnilateral"),
                logType = draft.getInt("logType"),
                sets = List(sets.length()) { setIndex ->
                    val set = sets.getJSONObject(setIndex)
                    WorkoutSet(set.getInt("setNumber"), set.getString("weightOrDuration"), set.getString("reps"))
                }
            )
        }
    }
}
