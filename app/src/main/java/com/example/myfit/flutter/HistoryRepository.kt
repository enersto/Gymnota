package com.example.myfit.flutter

import android.content.Context
import android.os.Trace
import androidx.room.InvalidationTracker
import androidx.room.withTransaction
import com.example.myfit.data.AppDatabase
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.concurrent.atomic.AtomicLong

/** One application-scoped immutable snapshot; no Activity, credentials or backup file. */
internal object HistoryRepository {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val revision = AtomicLong()
    private val state = MutableStateFlow<Result<Map<String, Any?>>?>(null)
    private var started = false
    private var refreshJob: Job? = null
    @Synchronized fun start(context: Context) {
        if (started) return
        started = true
        val app = context.applicationContext
        scope.launch {
          try {
            val db = AppDatabase.getDatabase(app)
            db.invalidationTracker.addObserver(object : InvalidationTracker.Observer("workout_tasks", "weight_records") {
                override fun onInvalidated(tables: Set<String>) { refresh(app) }
            })
            refresh(app, immediate = true)
            db.workoutDao().getAppSettings()
                .map { setting -> setting?.let { Triple(it.age, it.height, it.gender) } }
                .distinctUntilChanged()
                .drop(1)
                .collect { refresh(app) }
          } catch (e: CancellationException) { throw e }
          catch (e: Exception) { state.value = Result.failure(e) }
        }
    }
    @Synchronized private fun refresh(context: Context, immediate: Boolean = false) {
        refreshJob?.cancel()
        refreshJob = scope.launch {
            if (!immediate) delay(80)
            try {
                val db = AppDatabase.getDatabase(context)
                val raw = db.withTransaction {
                    Trace.beginSection("Gymnota: read history from Room")
                    try { HistorySnapshot.read(encodedSets = true, chartFields = true) { sql -> db.openHelper.writableDatabase.query(sql) } }
                    finally { Trace.endSection() }
                }
                val snapshot = withContext(Dispatchers.Default) {
                    Trace.beginSection("Gymnota: build history snapshot")
                    try { RustHistoryCore.process(raw) }
                    finally { Trace.endSection() }
                } +
                    mapOf("revision" to revision.incrementAndGet())
                ensureActive()
                state.value = Result.success(snapshot)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { state.value = Result.failure(e) }
        }
    }
    fun observe(context: Context): Flow<Result<Map<String, Any?>>> { start(context); return state.filterNotNull() }
    fun currentOrNull(): Map<String, Any?>? = state.value?.getOrNull()

    suspend fun current(context: Context): Map<String, Any?> {
        start(context)
        if (state.value?.isFailure == true) return reload(context)
        return state.filterNotNull().first().getOrThrow()
    }
    suspend fun reload(context: Context): Map<String, Any?> {
        start(context)
        val previous = state.value
        refresh(context, immediate = true)
        return state.filterNotNull().first { it != previous }.getOrThrow()
    }
}
