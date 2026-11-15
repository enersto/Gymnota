package com.example.myfit.flutter
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.myfit.data.AppDatabase
import com.example.myfit.model.WorkoutTask
import com.example.myfit.model.WorkoutSet
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HistoryRepositoryTest {
    @Test fun cachedSnapshotInvalidatesAfterCommittedRecordWritesAndDeletes() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val dao = AppDatabase.getDatabase(context).workoutDao()
        val before = withTimeout(10000) { HistoryRepository.current(context) }
        val task = WorkoutTask(id = -999771, date = "2026-11-09", templateId = 0,
            name = "Personal restored name", category = "STRENGTH", isCompleted = true,
            sets = listOf(WorkoutSet(1, "25", "12")))
        fun contains(snapshot: Map<String, Any?>) = (snapshot["tasks"] as List<*>).any { (it as Map<*, *>)["id"].toString() == task.id.toString() }
        try {
            dao.insertTask(task)
            val next = withTimeout(10000) { HistoryRepository.observe(context).first { it.isSuccess && contains(it.getOrThrow()) }.getOrThrow() }
            assertTrue((next["revision"] as Number).toLong() > (before["revision"] as Number).toLong())
            val stored = (next["tasks"] as List<*>).first { (it as Map<*, *>)["id"].toString() == task.id.toString() } as Map<*, *>
            assertEquals(task.name, stored["name"])
            assertEquals(next["revision"], HistoryRepository.current(context)["revision"])
        } finally { dao.deleteTask(task) }
        withTimeout(10000) { HistoryRepository.observe(context).first { it.isSuccess && !contains(it.getOrThrow()) } }
        Unit
    }
}
