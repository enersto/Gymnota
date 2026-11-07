package com.example.myfit.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import com.example.myfit.model.ExerciseTemplate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.util.zip.GZIPInputStream

data class CatalogExercise(
    val sourceId: String,
    val name: String,
    val bodyPart: String,
    val equipment: String,
    val instruction: String,
    val category: String?,
    val logType: Int?,
    val isUnilateral: Boolean?,
    val defaultTarget: String?,
    val gifUri: String,
    val canonicalName: String
) {
    fun toTemplate() = ExerciseTemplate(
        name = name,
        defaultTarget = defaultTarget.orEmpty(),
        category = category ?: "STRENGTH",
        bodyPart = bodyPart,
        equipment = equipment,
        instruction = instruction,
        logType = logType ?: 0,
        isUnilateral = isUnilateral ?: false,
        animationUri = gifUri
    )
}

/** Versioned, disposable catalogue. Personal backups contain only the main Room database. */
class ExerciseCatalog(context: Context) {
    private val context = context.applicationContext

    companion object {
        private const val FILE_NAME = "gymnota-catalog-v4.sqlite"
        private val installLock = Any()
        private data class QueryKey(val language: String, val query: String, val part: String?)
        private val queryLock = Mutex()
        private val results = object : LinkedHashMap<QueryKey, List<CatalogExercise>>(16, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<QueryKey, List<CatalogExercise>>): Boolean = size > 16
        }
        private fun key(language: String, query: String, part: String?) =
            QueryKey(language.substringBefore('-').substringBefore('_'), query.trim().lowercase(java.util.Locale.ROOT), part)

        /** Immutable bundled data: cached results are valid for this app process/version. */
        fun cached(language: String, query: String = "", part: String? = null): List<CatalogExercise>? =
            synchronized(results) { results[key(language, query, part)] }

    }

    private fun databaseFile(): File = synchronized(installLock) {
        val directory = File(context.noBackupFilesDir, "exercise_catalog")
        check(directory.isDirectory || directory.mkdirs()) { "Cannot create catalogue directory" }
        val target = File(directory, FILE_NAME)
        if (!target.exists()) {
            val temporary = File(directory, "$FILE_NAME.tmp")
            try {
                context.assets.open("catalog/$FILE_NAME.bin").use { asset ->
                    GZIPInputStream(asset).use { input ->
                        temporary.outputStream().use { input.copyTo(it) }
                    }
                }
                SQLiteDatabase.openDatabase(temporary.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
                    db.rawQuery("PRAGMA integrity_check", null).use {
                        check(it.moveToFirst() && it.getString(0) == "ok")
                    }
                    db.rawQuery("SELECT count(*) FROM catalog_exercise", null).use {
                        check(it.moveToFirst() && it.getInt(0) == 1324)
                    }
                }
                check(temporary.renameTo(target)) { "Cannot install catalogue" }
            } finally {
                temporary.delete()
            }
        }
        target
    }

    suspend fun load(language: String): List<CatalogExercise> = search(language)

    suspend fun search(language: String, query: String = "", part: String? = null): List<CatalogExercise> {
        cached(language, query, part)?.let { return it }
        return withContext(Dispatchers.IO) {
            queryLock.withLock {
                cached(language, query, part)?.let { return@withLock it }
                val rows = RustCatalog.query(databaseFile().path, language, query, part)
                    ?: loadLegacy(language).filter { e ->
                        (part == null || e.bodyPart == part) && listOf(e.name, e.canonicalName, e.sourceId, e.instruction)
                            .any { it.lowercase(java.util.Locale.ROOT).contains(query.trim().lowercase(java.util.Locale.ROOT)) }
                    }
                synchronized(results) { results[key(language, query, part)] = rows }
                rows
            }
        }
    }

    internal suspend fun loadLegacy(language: String): List<CatalogExercise> = withContext(Dispatchers.IO) {
        SQLiteDatabase.openDatabase(databaseFile().path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            db.rawQuery(
                """
                SELECT e.source_id, COALESCE(NULLIF(t.display_name, ''), e.canonical_name),
                       e.bodyPart, e.equipment,
                       COALESCE(NULLIF(t.instruction, ''), en.instruction, ''),
                       e.category, e.logType, e.isUnilateral, e.defaultTarget, e.gif_path, e.canonical_name
                FROM catalog_exercise e
                LEFT JOIN catalog_translation t ON t.source_id = e.source_id AND t.locale = ?
                LEFT JOIN catalog_translation en ON en.source_id = e.source_id AND en.locale = 'en'
                ORDER BY e.canonical_name, e.source_id
                """.trimIndent(),
                arrayOf(language.substringBefore('-').substringBefore('_'))
            ).use { cursor ->
                buildList {
                    while (cursor.moveToNext()) {
                        add(CatalogExercise(
                            sourceId = cursor.getString(0),
                            name = cursor.getString(1),
                            bodyPart = cursor.getString(2) ?: "part_other",
                            equipment = cursor.getString(3) ?: "equip_other",
                            instruction = cursor.getString(4),
                            category = cursor.getString(5),
                            logType = if (cursor.isNull(6)) null else cursor.getInt(6),
                            isUnilateral = if (cursor.isNull(7)) null else cursor.getInt(7) != 0,
                            defaultTarget = cursor.getString(8),
                            gifUri = CatalogMedia.bundledUri("file:///android_asset/catalog/" + cursor.getString(9)),
                            canonicalName = cursor.getString(10)
                        ))
                    }
                }
            }
        }
    }
}

