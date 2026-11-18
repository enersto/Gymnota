package com.example.myfit.data

import android.content.Context
import android.net.Uri
import com.example.myfit.BuildConfig
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID

/** Android supplies streams and atomic rename; Rust supplies SQLite snapshots and validation. */
internal object DatabaseBackups {
    suspend fun prepareRestore(context: Context, uri: Uri): File {
        val token = UUID.randomUUID().toString()
        val stagedName = "restore-$token.db"
        val staged = context.getDatabasePath(stagedName)
        val prepared = context.getDatabasePath("restore-$token.prepared")
        try {
            checkNotNull(context.contentResolver.openInputStream(uri)) { "Backup stream unavailable" }.use { input ->
                FileOutputStream(staged).use { output -> input.copyTo(output); output.fd.sync() }
            }
            if (BuildConfig.RUST_HISTORY_ENABLED) RustWorkoutStorage.prepareBackup(context, stagedName)
            val migrated = AppDatabase.openForRestore(context, stagedName)
            try {
                // Room checks the migrated structure while legacy Android consumers remain in use.
                val db = migrated.openHelper.writableDatabase
                db.query("PRAGMA integrity_check").use { cursor ->
                    check(cursor.moveToFirst() && cursor.getString(0) == "ok") { "Backup integrity check failed" }
                }
                db.query("PRAGMA wal_checkpoint(TRUNCATE)").use { cursor ->
                    check(cursor.moveToFirst() && cursor.getInt(0) == 0) { "Backup checkpoint busy" }
                }
            } finally { migrated.close() }
            if (BuildConfig.RUST_HISTORY_ENABLED) {
                RustWorkoutStorage.snapshot(context, stagedName) { input ->
                    FileOutputStream(prepared).use { output -> input.copyTo(output); output.fd.sync() }
                }
            } else {
                FileInputStream(staged).use { input ->
                    FileOutputStream(prepared).use { output -> input.copyTo(output); output.fd.sync() }
                }
            }
            return prepared
        } catch (error: Throwable) {
            prepared.delete()
            throw error
        } finally { context.deleteDatabase(stagedName) }
    }

    fun install(prepared: File, destination: File) {
        // Both files live in the private databases directory on the same filesystem.
        Files.move(prepared.toPath(), destination.toPath(),
            StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
    }
}
