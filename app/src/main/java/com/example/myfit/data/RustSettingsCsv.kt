package com.example.myfit.data

import com.example.myfit.BuildConfig
import com.example.myfit.model.WorkoutTask
import org.json.JSONArray

internal object RustSettingsCsv {
    private val loaded by lazy {
        if (!BuildConfig.RUST_HISTORY_ENABLED) false
        else try { System.loadLibrary("gymnota_history_core"); true } catch (_: LinkageError) { false }
    }

    @JvmStatic private external fun encodeNative(input: String): String?

    fun encode(tasks: List<WorkoutTask>): String? {
        if (!loaded) return null
        return try {
            val rows = JSONArray()
            for (task in tasks) {
                val sets = JSONArray()
                for (set in task.sets) {
                    sets.put(JSONArray().put(set.weightOrDuration).put(set.reps)
                        .put(set.rightWeight).put(set.rightReps))
                }
                rows.put(JSONArray().put(task.date).put(task.name).put(task.category)
                    .put(task.target).put(task.isUnilateral).put(task.logType).put(sets))
            }
            encodeNative(rows.toString())
        } catch (_: Exception) { null } catch (_: LinkageError) { null }
    }
}
