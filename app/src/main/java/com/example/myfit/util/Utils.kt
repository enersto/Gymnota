package com.example.myfit.util

import android.content.Context
import android.net.Uri
import com.example.myfit.model.WeightRecord
import com.example.myfit.model.WorkoutTask
import java.io.InputStream
import java.io.OutputStream

object CsvUtils {
    fun generateCsv(history: List<WorkoutTask>, weights: List<WeightRecord>): String {
        val sb = StringBuilder()
        sb.append("Date,Type,Name,Target,Actual,Category\n")
        history.forEach { task ->
            val safeName = task.name.replace(",", " ")
            val safeTarget = task.target.replace(",", " ")
            sb.append("${task.date},Task,$safeName,$safeTarget,${task.actualWeight},${task.category}\n")
        }
        weights.forEach { w ->
            sb.append("${w.date},Weight,BodyWeight,-,${w.weight},-\n")
        }
        return sb.toString()
    }
}

object DatabaseUtils {
    fun backupDatabase(context: Context, uri: Uri) {
        try {
            val dbFile = context.getDatabasePath("myfit_v7.db")
            context.contentResolver.openOutputStream(uri)?.use { output ->
                dbFile.inputStream().use { input ->
                    input.copyTo(output)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun restoreDatabase(context: Context, uri: Uri): Boolean {
        return try {
            val dbFile = context.getDatabasePath("myfit_v7.db")
            context.contentResolver.openInputStream(uri)?.use { input ->
                dbFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}