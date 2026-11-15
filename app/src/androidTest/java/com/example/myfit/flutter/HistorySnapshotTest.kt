package com.example.myfit.flutter

import android.database.sqlite.SQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.myfit.data.ExerciseCatalog
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HistorySnapshotTest {
    @Test fun snapshotPreservesBothSidesAndExcludesUnfinishedAndPrivateTables() {
        SQLiteDatabase.create(null).use { db ->
            db.execSQL("""CREATE TABLE workout_tasks(id INTEGER, date TEXT, name TEXT, category TEXT, logType INTEGER,
                isUnilateral INTEGER, target TEXT, actualWeight TEXT, sets TEXT, isCompleted INTEGER)""")
            db.execSQL("CREATE TABLE weight_records(date TEXT, weight REAL)")
            db.execSQL("CREATE TABLE app_settings(aiApiKey TEXT)")
            db.execSQL("INSERT INTO app_settings VALUES ('private-test-value')")
            val sets = """[{"setNumber":1,"weightOrDuration":"25","reps":"12","rightWeight":"20","rightReps":"10"}]"""
            db.execSQL("INSERT INTO workout_tasks VALUES(1,'2026-11-01','Personal name','STRENGTH',0,1,'','',?,1)", arrayOf(sets))
            db.execSQL("INSERT INTO workout_tasks VALUES(2,'2026-11-02','Pending','CARDIO',1,0,'','', '[]',0)")
            db.execSQL("INSERT INTO weight_records VALUES('2026-11-01',70.5)")
            val snapshot = HistorySnapshot.read { db.rawQuery(it, null) }
            val tasks = snapshot["tasks"] as List<*>
            assertEquals(1, tasks.size)
            val task = tasks.single() as Map<*, *>
            assertEquals("Personal name", task["name"])
            assertEquals(true, task["unilateral"])
            val set = (task["sets"] as List<*>).single() as Map<*, *>
            assertEquals("20", set["rightWeight"])
            assertEquals("10", set["rightReps"])
            assertEquals(setOf("tasks", "weights"), snapshot.keys)
            assertFalse(snapshot.toString().contains("private-test-value"))
        }
    }

    @Test fun legacyMissingOptionalFieldsUseSafeDefaults() {
        SQLiteDatabase.create(null).use { db ->
            db.execSQL("CREATE TABLE workout_tasks(id INTEGER,date TEXT,name TEXT,sets TEXT,isCompleted INTEGER)")
            db.execSQL("CREATE TABLE weight_records(date TEXT, weight REAL)")
            db.execSQL("INSERT INTO workout_tasks VALUES(1,'2026-11-01','Legacy',NULL,1)")
            val task = ((HistorySnapshot.read { db.rawQuery(it, null) })["tasks"] as List<*>).single() as Map<*, *>
            assertEquals(0, task["logType"])
            assertEquals(emptyList<Any>(), task["sets"])
        }
    }

    @Test fun allFiveCatalogLanguagesRetainIdsAndBundledGif() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val catalog = ExerciseCatalog(context)
        val english = catalog.load("en")
        assertEquals(1324, english.size)
        for (language in listOf("zh", "es", "de", "ja")) {
            val rows = catalog.load(language)
            assertEquals(english.map { it.sourceId }, rows.map { it.sourceId })
            assertTrue(rows.all { it.name.isNotBlank() })
            assertTrue(rows.zip(english).any { (a,b) -> a.name != b.name })
        }
        val first = english.first()
        context.assets.open(first.gifUri.removePrefix("file:///android_asset/")).use {
            val header = String(it.readNBytes(12), Charsets.US_ASCII)
            assertTrue(header.startsWith("RIFF") && header.endsWith("WEBP"))
        }
    }
}
