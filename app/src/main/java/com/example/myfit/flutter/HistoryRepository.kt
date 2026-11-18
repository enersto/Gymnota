package com.example.myfit.flutter

import android.content.Context
import androidx.room.InvalidationTracker
import androidx.room.withTransaction
import com.example.myfit.BuildConfig
import com.example.myfit.data.AppDatabase
import com.example.myfit.data.RustWorkoutStorage
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
            if (BuildConfig.RUST_HISTORY_ENABLED) {
                launch {
                    RustWorkoutStorage.historyRevision.collect { nativeRevision ->
                        refresh(app, immediate = nativeRevision == 0L)
                    }
                }
                com.example.myfit.data.RustLiveQueries.observe<com.example.myfit.model.AppSetting>(app,
                    "app_settings", RustWorkoutStorage.profileRevision, "profile")
                    .map { rows -> rows.firstOrNull()?.let { Triple(it.age, it.height, it.gender) } }
                    .distinctUntilChanged().drop(1).collect { refresh(app) }
                return@launch
            }
            val db = AppDatabase.getDatabase(app)
            db.invalidationTracker.addObserver(object : InvalidationTracker.Observer("workout_tasks", "weight_records") {
                override fun onInvalidated(tables: Set<String>) { refresh(app) }
            })
            launch {
                RustWorkoutStorage.historyRevision.collect { revision ->
                    refresh(app, immediate = revision == 0L)
                }
            }
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
                val snapshot = if (BuildConfig.RUST_HISTORY_ENABLED) {
                    RustHistoryCore.decodeOutput(RustWorkoutStorage.history(context))
                } else {
                    val db = AppDatabase.getDatabase(context)
                    val raw = db.withTransaction {
                        HistorySnapshot.read(encodedSets = true, chartFields = true) { sql ->
                            db.openHelper.writableDatabase.query(sql)
                        }
                    }
                    withContext(Dispatchers.Default) { RustHistoryCore.process(raw) }
                } + mapOf("revision" to revision.incrementAndGet())
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
