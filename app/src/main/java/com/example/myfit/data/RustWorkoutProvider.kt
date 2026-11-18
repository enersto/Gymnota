package com.example.myfit.data

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.os.Binder
import android.os.Process
import android.os.ParcelFileDescriptor
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** Dedicated process so Room and bundled SQLite use process-level SQLite locks. */
class RustWorkoutProvider : ContentProvider() {
    override fun onCreate() = true

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle {
        check(Binder.getCallingUid() == Process.myUid())
        val app = requireNotNull(context)
        val path = app.getDatabasePath(AppDatabase.FILE_NAME).absolutePath
        return when (method) {
            "initialize" -> {
                val language = java.util.Locale.getDefault().language
                val file = when (language) {
                    "en", "ja", "de", "es" -> "exercises_$language.json"
                    else -> "default_exercises.json"
                }
                val seeds = app.assets.open(file).bufferedReader().use { JSONArray(it.readText()) }
                app.getDatabasePath(AppDatabase.FILE_NAME).parentFile?.mkdirs()
                RustWorkout.initialize(JSONObject().put("path", path).put("templates", seeds))
                Bundle.EMPTY
            }
            "checkpoint" -> {
                RustWorkout.backup(JSONObject().put("action", "checkpoint").put("source", path))
                Bundle.EMPTY
            }
            "prepareBackup" -> {
                val name = requireNotNull(arg)
                require(File(name).name == name && name.startsWith("restore-"))
                val staged = app.getDatabasePath(name)
                RustWorkout.backup(JSONObject().put("action", "validate").put("source", staged.path))
                val result = RustWorkout.backup(JSONObject().put("action", "migrate").put("source", staged.path))
                Bundle().apply { putInt("version", result.getInt("version")) }
            }
            "mutate" -> {
                val request = JSONObject(requireNotNull(arg)).put("path", path)
                Bundle().apply { putString("updated", RustWorkout.store(request)) }
            }
            "weekly" -> {
                val request = JSONObject(requireNotNull(arg))
                val action = request.optString("action", "list")
                val day = request.getInt("day")
                val id = if (action == "list") null else request.getLong("id")
                val rows = RustWeeklyRoutine.rows(path, day, action, id)
                if (action != "list") checkNotNull(rows) { "Could not edit weekly routine" }
                Bundle().apply { if (rows != null) putString("rows", rows.toString()) }
            }
            else -> throw IllegalArgumentException("Unknown store method")
        }
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        check(Binder.getCallingUid() == Process.myUid())
        require(uri.path in setOf("/history", "/query", "/backup") && mode == "r")
        val app = requireNotNull(context)
        val path = app.getDatabasePath(AppDatabase.FILE_NAME).absolutePath
        if (uri.path == "/backup") {
            val name = uri.getQueryParameter("source") ?: AppDatabase.FILE_NAME
            require(File(name).name == name && (name == AppDatabase.FILE_NAME || name.startsWith("restore-")))
            val snapshot = File.createTempFile("backup-", ".db", app.cacheDir)
            try {
                check(snapshot.delete())
                RustWorkout.backup(JSONObject().put("action", "snapshot")
                    .put("source", app.getDatabasePath(name).path).put("destination", snapshot.path))
                return ParcelFileDescriptor.open(snapshot, ParcelFileDescriptor.MODE_READ_ONLY)
            } finally { snapshot.delete() }
        }
        // A file descriptor transfers large snapshots without Binder's byte-array limit.
        val file = File.createTempFile("history-", ".json", app.cacheDir)
        try {
            val text = if (uri.path == "/history") RustWorkout.history(path)
                else RustWorkout.query(JSONObject().put("path", path)
                    .put("query", uri.getQueryParameter("query")).put("date", uri.getQueryParameter("date"))
                    .put("limit", uri.getQueryParameter("limit")?.toInt())
                    .put("id", uri.getQueryParameter("id")?.toLong()))
            file.writeText(text, Charsets.UTF_8)
            return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        } finally { file.delete() }
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?,
        selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = throw UnsupportedOperationException()
    override fun getType(uri: Uri): String? = throw UnsupportedOperationException()
    override fun insert(uri: Uri, values: ContentValues?): Uri? = throw UnsupportedOperationException()
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = throw UnsupportedOperationException()
    override fun update(uri: Uri, values: ContentValues?, selection: String?,
        selectionArgs: Array<out String>?): Int = throw UnsupportedOperationException()
}

internal object RustWorkoutStorage {
    private val fileAccess = Mutex()
    private val _historyRevision = MutableStateFlow(0L)
    val historyRevision = _historyRevision.asStateFlow()
    private val _workoutRevision = MutableStateFlow(0L)
    val workoutRevision = _workoutRevision.asStateFlow()
    private val _templateRevision = MutableStateFlow(0L)
    val templateRevision = _templateRevision.asStateFlow()
    private val _weightRevision = MutableStateFlow(0L)
    val weightRevision = _weightRevision.asStateFlow()
    private val _profileRevision = MutableStateFlow(0L)
    val profileRevision = _profileRevision.asStateFlow()
    private val _scheduleRevision = MutableStateFlow(0L)
    val scheduleRevision = _scheduleRevision.asStateFlow()
    private val _aiRevision = MutableStateFlow(0L)
    val aiRevision = _aiRevision.asStateFlow()

    @Volatile private var initialized = false
    @Synchronized fun initialize(context: Context) {
        if (initialized) return
        val app = context.applicationContext
        checkNotNull(app.contentResolver.call(uri(app), "initialize", null, null)) { "Database initialization failed" }
        initialized = true
    }

    suspend fun checkpoint(context: Context) = withContext(Dispatchers.IO) {
        checkNotNull(context.contentResolver.call(uri(context), "checkpoint", null, null))
        Unit
    }

    suspend fun <T> withDatabaseFile(action: suspend () -> T): T = fileAccess.withLock { action() }

    private fun uri(app: Context): Uri = Uri.parse("content://${app.packageName}.rust-workout")

    suspend fun snapshot(context: Context, sourceName: String = AppDatabase.FILE_NAME,
        consume: (java.io.InputStream) -> Unit) = withContext(Dispatchers.IO) {
        val app = context.applicationContext
        val address = uri(app).buildUpon().appendPath("backup").appendQueryParameter("source", sourceName).build()
        checkNotNull(app.contentResolver.openInputStream(address)).use(consume)
    }

    suspend fun prepareBackup(context: Context, stagedName: String) = withContext(Dispatchers.IO) {
        val app = context.applicationContext
        checkNotNull(app.contentResolver.call(uri(app), "prepareBackup", stagedName, null))
        Unit
    }

    suspend fun history(context: Context): String = withContext(Dispatchers.IO) {
        withDatabaseFile {
            val app = context.applicationContext
            initialize(app)
            val address = uri(app).buildUpon().appendPath("history").build()
            checkNotNull(app.contentResolver.openInputStream(address)) { "History snapshot unavailable" }
                .bufferedReader(Charsets.UTF_8).use { it.readText() }
        }
    }

    suspend fun query(context: Context, query: String, date: String? = null, limit: Int? = null, id: Long? = null): String = withContext(Dispatchers.IO) {
        withDatabaseFile {
            val app = context.applicationContext
            initialize(app)
            val address = uri(app).buildUpon().appendPath("query")
                .appendQueryParameter("query", query).apply {
                    if (date != null) appendQueryParameter("date", date)
                    if (limit != null) appendQueryParameter("limit", limit.toString())
                    if (id != null) appendQueryParameter("id", id.toString())
                }.build()
            checkNotNull(app.contentResolver.openInputStream(address))
                .bufferedReader(Charsets.UTF_8).use { it.readText() }
        }
    }

    suspend fun weekly(context: Context, day: Int, action: String = "list", id: Long? = null): List<Map<String, Any?>>? =
        withContext(Dispatchers.IO) {
            withDatabaseFile {
                val app = context.applicationContext
                initialize(app)
                val request = JSONObject().put("day", day).put("action", action).put("id", id)
                val rows = if (action == "list") {
                    app.contentResolver.call(uri(app), "weekly", request.toString(), null)?.getString("rows")
                } else withContext(NonCancellable) {
                    checkNotNull(app.contentResolver.call(uri(app), "weekly", request.toString(), null)
                        ?.getString("rows")) { "Weekly routine response missing" }
                }
                rows?.let { RustWeeklyRoutine.summaries(JSONArray(it)) }
            }
        }

    suspend fun mutate(context: Context, date: String, changes: List<Map<String, Any?>>) {
        if (changes.isEmpty()) return
        val app = context.applicationContext
        withContext(Dispatchers.IO) {
            withDatabaseFile {
                // Rust owns initialization and schema migrations before any query or write.
                initialize(app)
                val request = JSONObject().put("date", date)
                    .put("changes", JSONArray(changes.map(::JSONObject)))
                withContext(NonCancellable) {
                    var refresh = true
                    try {
                        val result = app.contentResolver.call(uri(app), "mutate", request.toString(), null)
                        val updated = checkNotNull(result?.getString("updated")) { "Rust workout response missing" }
                        refresh = JSONArray(updated).length() > 0
                    } finally {
                        // A transport failure can follow a successful commit. Refresh but do not replay.
                        if (refresh) {
                            if (changes.any { it["action"] in setOf("saveProfile", "patchSettings", "saveModel") ||
                                    (it["fields"] as? Map<*, *>)?.containsKey("profile") == true })
                                _profileRevision.update { it + 1 }
                            if (changes.any { it["action"] in setOf("saveTemplate", "deleteTemplate", "importRoutine") })
                                _templateRevision.update { it + 1 }
                            if (changes.any { it["action"] in setOf("saveAiRecord", "deleteAiRecord") })
                                _aiRevision.update { it + 1 }
                            if (changes.any { it["action"] == "saveSchedule" }) _scheduleRevision.update { it + 1 }
                            if (changes.any { it["action"] == "saveWeight" }) _weightRevision.update { it + 1 }
                            if (changes.any { it["action"] !in setOf("saveWeight", "saveProfile", "saveSchedule", "patchSettings", "saveModel", "saveAiRecord", "deleteAiRecord", "saveAiMemory") })
                                _workoutRevision.update { it + 1 }
                            if (changes.any { it["action"] !in setOf("patchSettings", "saveModel",
                                    "saveSchedule", "saveTemplate", "deleteTemplate", "importRoutine", "saveAiRecord", "deleteAiRecord", "saveAiMemory") })
                                _historyRevision.update { it + 1 }
                        }
                    }
                }
            }
        }
    }
}
