package com.example.myfit.flutter

import android.database.sqlite.SQLiteDatabase
import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MediaAndRustCoreTest {
    @Test fun realJniMatchesExistingDecoderAndFallback() {
        SQLiteDatabase.create(null).use { db ->
            db.execSQL("CREATE TABLE workout_tasks(id INTEGER,date TEXT,name TEXT,category TEXT,logType INTEGER,isUnilateral INTEGER,target TEXT,actualWeight TEXT,sets TEXT,isCompleted INTEGER)")
            db.execSQL("CREATE TABLE weight_records(date TEXT,weight REAL)")
            val sets = """[{"setNumber":1,"weightOrDuration":"25","reps":"12","rightWeight":"20","rightReps":"10"}]"""
            db.execSQL("INSERT INTO workout_tasks VALUES(1,'2026-11-01','Personal unchanged','STRENGTH',0,1,'','',?,1)", arrayOf(sets))
            db.execSQL("INSERT INTO workout_tasks VALUES(2,'2026-11-02','Legacy','CARDIO',1,0,'90s','','[]',1)")
            val ambiguous = """[{"setNumber":2,"weightOrDuration":true,"reps":null}]"""
            db.execSQL("INSERT INTO workout_tasks VALUES(3,'2026-11-01','Ambiguous legacy','STRENGTH',0,0,'','',?,1)", arrayOf(ambiguous))
            db.execSQL("INSERT INTO weight_records VALUES('2026-11-01',76.4)")
            val raw = HistorySnapshot.read(encodedSets = true) { db.rawQuery(it,null) }
            val old = HistorySnapshot.read { db.rawQuery(it,null) }
            val result = RustHistoryCore.process(raw)
            assertEquals("rust", result["engine"])
            assertEquals(old["tasks"], result["tasks"])
            assertEquals(old["weights"], result["weights"])
            val summary = result["summary"] as Map<*, *>
            assertEquals(500.0, (summary["volumeKg"] as Number).toDouble(), 1e-9)
            assertEquals(22.0, (summary["reps"] as Number).toDouble(), 1e-9)
            assertEquals(1.5, (summary["durationMinutes"] as Number).toDouble(), 1e-9)
            assertEquals(2, (summary["trainingDays"] as Number).toInt())
            assertEquals(1, (result["byMonth"] as List<*>).size)
            val fallback = RustHistoryCore.process(raw, enableRust = false)
            assertEquals("kotlin-fallback",fallback["engine"])
            assertEquals(old["tasks"],fallback["tasks"])
            assertEquals(old["weights"],fallback["weights"])
        }
    }

    @Test fun animatedWebpWorksInAndroidDecoderAndMatchesFirstFrame() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val assets = instrumentation.context.assets
        val manifest = JSONArray(assets.open("media_bench/manifest.json").bufferedReader().use { it.readText() })
        for (i in 0 until manifest.length()) {
            val case = manifest.getJSONObject(i)
            val gifSource = ImageDecoder.createSource(assets, "media_bench/$i.gif")
            val webpSource = ImageDecoder.createSource(assets, "media_bench/$i.webp")
            val gif = ImageDecoder.decodeDrawable(gifSource) as AnimatedImageDrawable
            val webp = ImageDecoder.decodeDrawable(webpSource) as AnimatedImageDrawable
            assertEquals(case.getInt("width"), webp.intrinsicWidth)
            assertEquals(case.getInt("height"), webp.intrinsicHeight)
            assertEquals(gif.repeatCount, webp.repeatCount)
            instrumentation.runOnMainSync { webp.start(); assertTrue(webp.isRunning); webp.stop() }
            val g = ImageDecoder.decodeBitmap(gifSource) { decoder,_,_ -> decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE }
            val w = ImageDecoder.decodeBitmap(webpSource) { decoder,_,_ -> decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE }
            assertTrue("First frame differs: $i", g.sameAs(w))
            assertEquals(g.allocationByteCount,w.allocationByteCount)
            g.recycle(); w.recycle()
            fun timing(source: ImageDecoder.Source): Long {
                val times = (0 until 9).map {
                    val start = SystemClock.elapsedRealtimeNanos()
                    ImageDecoder.decodeDrawable(source)
                    SystemClock.elapsedRealtimeNanos()-start
                }.drop(2).sorted()
                return times[times.size/2]
            }
            Log.i("GYMNOTA_MEDIA", "case=$i frames=${case.getInt("frames")} gifFirstNs=${timing(gifSource)} webpFirstNs=${timing(webpSource)} decodedFrameBytes=${case.getInt("width")*case.getInt("height")*4}")
        }
    }
}
