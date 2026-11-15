package com.example.myfit.data

import com.example.myfit.BuildConfig
import org.json.JSONArray
import org.json.JSONObject

/** Only trusted installed catalogue paths cross JNI. Personal Room is never opened here. */
internal object RustCatalog {
    private val loaded by lazy {
        if (!BuildConfig.RUST_HISTORY_ENABLED) false
        else try { System.loadLibrary("gymnota_history_core"); true } catch (_: LinkageError) { false }
    }
    @JvmStatic private external fun queryNative(input: String): String

    fun searchPersonal(names: List<Pair<Long, String>>, query: String): List<Long>? {
        if (!loaded) return null
        return try {
            val entries = JSONArray()
            names.forEach { (id, name) -> entries.put(JSONObject().put("id", id).put("name", name)) }
            val result = JSONObject(queryNative(JSONObject().put("operation", "personalSearch")
                .put("query", query).put("entries", entries).toString()))
            val matches = result.getJSONArray("matches")
            (0 until matches.length()).map(matches::getLong)
        } catch (_: Exception) { null } catch (_: LinkageError) { null }
    }

    fun query(path: String, language: String, query: String, part: String?): List<CatalogExercise>? {
        if (!loaded) return null
        return try {
            val result = JSONObject(queryNative(JSONObject().put("path", path).put("language", language)
                .put("query", query).put("part", part ?: JSONObject.NULL).toString()))
            check(result.optInt("schemaVersion") == 1 && !result.has("error"))
            val rows = result.getJSONArray("entries")
            (0 until rows.length()).map { index ->
                val r = rows.getJSONObject(index)
                CatalogExercise(r.getString("id"), r.getString("name"), r.getString("bodyPart"),
                    r.getString("equipment"), r.getString("instruction"),
                    if (r.isNull("category")) null else r.getString("category"),
                    if (r.isNull("logType")) null else r.getInt("logType"),
                    if (r.isNull("unilateral")) null else r.getBoolean("unilateral"),
                    if (r.isNull("target")) null else r.getString("target"),
                    CatalogMedia.bundledUri("file:///android_asset/catalog/" + r.getString("path")), r.getString("canonical"))
            }
        } catch (_: Exception) { null } catch (_: LinkageError) { null }
    }
}
