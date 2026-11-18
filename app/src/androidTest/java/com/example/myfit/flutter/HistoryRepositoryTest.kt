package com.example.myfit.flutter

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.myfit.BuildConfig
import com.example.myfit.data.AppDatabase
import com.example.myfit.data.RustLiveQueries
import com.example.myfit.data.RustWorkoutStorage
import com.example.myfit.model.ExerciseTemplate
import com.example.myfit.model.WorkoutTask
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HistoryRepositoryTest {
    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val date get() = java.time.LocalDate.now().toString()

    private suspend fun fixture(block: suspend (WorkoutTask) -> Unit) {
        val name = "Rust history ${java.util.UUID.randomUUID()}"
        var templateId: Long? = null
        var taskId: Long? = null
        try {
            RustWorkoutStorage.mutate(context, date, listOf(mapOf("action" to "saveTemplate", "id" to 0L,
                "fields" to mapOf("name" to name, "target" to "3x12", "category" to "STRENGTH",
                    "bodyPart" to "part_other", "equipment" to "equip_other", "instruction" to "",
                    "isUnilateral" to true, "logType" to 0))))
            templateId = RustLiveQueries.read<ExerciseTemplate>(context, "templates").single { it.name == name }.id
            RustWorkoutStorage.mutate(context, date, listOf(mapOf("action" to "addExercise", "id" to templateId)))
            val task = RustLiveQueries.decode<WorkoutTask>(RustWorkoutStorage.query(context, "daily", date),
                object : com.google.gson.reflect.TypeToken<List<WorkoutTask>>() {}.type).single { it.name == name }
            taskId = task.id
            block(task)
        } finally {
            taskId?.let { RustWorkoutStorage.mutate(context, date, listOf(mapOf("action" to "removeTask", "id" to it))) }
            templateId?.let { RustWorkoutStorage.mutate(context, date, listOf(mapOf("action" to "deleteTemplate", "id" to it))) }
        }
    }

    @Test fun rustCheckInRefreshesAnAlreadyLoadedHistoryAndDeletion() = runBlocking {
        assumeTrue(BuildConfig.RUST_HISTORY_ENABLED)
        val before = withTimeout(10_000) { HistoryRepository.current(context) }
        fixture { task ->
            fun contains(snapshot: Map<String, Any?>) =
                (snapshot["tasks"] as List<*>).any { (it as Map<*, *>)["name"] == task.name }
            assertFalse(contains(before))
            RustWorkoutStorage.mutate(context, date, listOf(
                mapOf("action" to "setFields", "id" to task.id, "edits" to listOf(
                    mapOf("index" to 0, "field" to "value", "text" to "25"),
                    mapOf("index" to 0, "field" to "reps", "text" to "12"),
                    mapOf("index" to 0, "field" to "rightWeight", "text" to "20"))),
                mapOf("action" to "check", "id" to task.id)))
            val next = withTimeout(10_000) {
                HistoryRepository.observe(context).first { it.getOrNull()?.let(::contains) == true }.getOrThrow()
            }
            assertTrue((next["revision"] as Number).toLong() > (before["revision"] as Number).toLong())
            val stored = RustLiveQueries.read<WorkoutTask>(context, "task", id = task.id).single()
            assertTrue(stored.isCompleted)
            assertEquals("20", stored.sets.single().rightWeight)
            assertEquals(next["revision"], HistoryRepository.current(context)["revision"])
            RustWorkoutStorage.mutate(context, date, listOf(mapOf("action" to "removeTask", "id" to task.id)))
            withTimeout(10_000) { HistoryRepository.observe(context).first { it.getOrNull()?.let(::contains) == false } }
        }
        Unit
    }

    @Test fun failedRustBatchRollsBackEarlierEdits() = runBlocking {
        assumeTrue(BuildConfig.RUST_HISTORY_ENABLED)
        fixture { task ->
            val before = RustLiveQueries.read<WorkoutTask>(context, "task", id = task.id).single()
            var failed = false
            try {
                RustWorkoutStorage.mutate(context, date, listOf(
                    mapOf("action" to "setFields", "id" to task.id,
                        "edits" to listOf(mapOf("index" to 0, "field" to "value", "text" to "999"))),
                    mapOf("action" to "unknown-action", "id" to task.id)))
            } catch (_: Exception) { failed = true }
            assertTrue("Malformed batch must fail", failed)
            assertEquals(before, RustLiveQueries.read<WorkoutTask>(context, "task", id = task.id).single())
        }
        Unit
    }

    @Test fun rustBuildCannotOpenLiveRoomDatabase() {
        assumeTrue(BuildConfig.RUST_HISTORY_ENABLED)
        try {
            AppDatabase.getDatabase(context)
            fail("Rust runtime must never open the live Room database")
        } catch (expected: IllegalStateException) {
            assertTrue(expected.message.orEmpty().contains("Room live database is disabled"))
        }
    }
}
