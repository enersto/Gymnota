package com.example.myfit.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.mapLatest

/** Rust executes queries; committed native revisions wake subscribers without opening Room. */
internal object RustLiveQueries {
    private val gson = Gson()

    inline fun <reified T> observe(
        context: Context, table: String, revision: StateFlow<Long>, query: String, date: String? = null
    ): Flow<List<T>> {
        val app = context.applicationContext
        val type = TypeToken.getParameterized(List::class.java, T::class.java).type
        return revision.mapLatest {
            val text = RustWorkoutStorage.query(app, query, date)
            decode<T>(text, type)
        }.distinctUntilChanged()
    }

    suspend inline fun <reified T> read(context: Context, query: String, limit: Int? = null, id: Long? = null): List<T> {
        val type = TypeToken.getParameterized(List::class.java, T::class.java).type
        return decode(RustWorkoutStorage.query(context, query, limit = limit, id = id), type)
    }

    fun <T> decode(text: String, type: java.lang.reflect.Type): List<T> = gson.fromJson(text, type)
}
