package com.example.myfit.data

import android.content.Context
import org.json.JSONObject
import com.example.myfit.model.ExerciseTemplate

/** Name aliases add media to existing personal records without rewriting their names or IDs. */
object CatalogMedia {
    /** Resolve old backup/alias URIs without rewriting personal records. */
    fun bundledUri(uri: String): String =
        if (uri.startsWith("file:///android_asset/catalog/videos/") && uri.endsWith(".gif")) uri.removeSuffix(".gif") + ".webp" else uri

    @Volatile private var aliases: Map<String, String>? = null

    fun photoUri(template: ExerciseTemplate): String? =
        template.imageUri?.takeIf { it.isNotBlank() && !it.startsWith("file:///android_asset/catalog/") }

    fun animationUri(context: Context, template: ExerciseTemplate): String? =
        template.animationUri?.takeIf { it.isNotBlank() }?.let(::bundledUri)
            ?: template.imageUri?.takeIf { it.startsWith("file:///android_asset/catalog/") }?.let(::bundledUri)
            ?: uri(context, template.name)

    fun uri(context: Context, name: String): String? {
        val map = aliases ?: synchronized(this) {
            aliases ?: context.assets.open("catalog/name_matches.json").bufferedReader().use {
                val json = JSONObject(it.readText())
                json.keys().asSequence().associateWith { key -> json.getString(key) }
            }.also { aliases = it }
        }
        return map[name.trim()]?.let { bundledUri("file:///android_asset/catalog/$it") }
    }
}
