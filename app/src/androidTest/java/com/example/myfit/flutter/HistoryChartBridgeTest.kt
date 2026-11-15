package com.example.myfit.flutter

import android.database.sqlite.SQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HistoryChartBridgeTest {
    @Test fun chartWhitelistAndRealJniPreserveNativeMonthlyPolicy() {
        SQLiteDatabase.create(null).use { db ->
            db.execSQL("CREATE TABLE workout_tasks(id INTEGER,date TEXT,name TEXT,category TEXT,bodyPart TEXT,logType INTEGER,isUnilateral INTEGER,target TEXT,actualWeight TEXT,sets TEXT,isCompleted INTEGER)")
            db.execSQL("CREATE TABLE weight_records(date TEXT,weight REAL,bodyFatKg REAL,skeletalMuscleKg REAL,bodyWaterPercentage REAL,waistCircumference REAL,hipCircumference REAL)")
            db.execSQL("CREATE TABLE app_settings(id INTEGER,height REAL,age INTEGER,gender INTEGER,aiApiKey TEXT)")
            db.execSQL("INSERT INTO app_settings VALUES(0,200,30,0,'private-must-not-cross-bridge')")
            val first = """[{"setNumber":1,"weightOrDuration":"20","reps":"10","rightWeight":"15","rightReps":"9"}]"""
            val second = """[{"setNumber":1,"weightOrDuration":"40","reps":"8"}]"""
            db.execSQL("INSERT INTO workout_tasks VALUES(1,'2026-11-01','Personal name','STRENGTH','part_chest',0,1,'','',?,1)",arrayOf(first))
            db.execSQL("INSERT INTO workout_tasks VALUES(2,'2026-11-02','Personal name','STRENGTH','part_chest',0,0,'','',?,1)",arrayOf(second))
            db.execSQL("INSERT INTO weight_records VALUES('2026-11-01',80,20,30,55,80,100)")
            db.execSQL("INSERT INTO weight_records VALUES('2026-11-02',100,NULL,NULL,60,90,100)")
            val raw = HistorySnapshot.read(encodedSets=true,chartFields=true) { db.rawQuery(it,null) }
            assertFalse(raw.toString().contains("private-must-not-cross-bridge"))
            val result = RustHistoryCore.process(raw)
            assertEquals("rust",result["engine"])
            val charts = result["charts"] as Map<*, *>
            fun field(map:Any?,key:String)= (map as Map<*, *>)[key]
            fun point(series:Any?,period:String)= ((field(series,period) as List<*>).first() as Map<*, *>)["value"] as Number
            val exercise = field(field(charts,"exerciseSeries"),"Personal name")
            assertEquals(30.0,point(field(exercise,"weight"),"monthly").toDouble(),1e-6)
            assertEquals(15.0,point(field(exercise,"rightWeight"),"daily").toDouble(),1e-6)
            val body=charts["body"]
            assertEquals(90.0,point(field(body,"weight"),"monthly").toDouble(),1e-6)
            assertEquals(22.5,point(field(body,"bmi"),"monthly").toDouble(),1e-6)
            assertEquals(2005.0,point(field(body,"bmr"),"monthly").toDouble(),1e-6)
            assertEquals(0.85,point(field(body,"whr"),"monthly").toDouble(),1e-6)
            val heat=(charts["heatmap"] as List<*>).first() as Map<*, *>
            assertEquals(655.0,(heat["volume"] as Number).toDouble(),1e-6)
            val fallback=RustHistoryCore.process(raw,enableRust=false)
            assertEquals(result["tasks"],fallback["tasks"])
            assertEquals(result["weights"],fallback["weights"])
            assertFalse(fallback.containsKey("charts"))
        }
    }

    @Test fun oldBackupWithoutProfileAndBodyColumnsStillLoads() {
        SQLiteDatabase.create(null).use { db ->
            db.execSQL("CREATE TABLE workout_tasks(id INTEGER,date TEXT,name TEXT,sets TEXT,isCompleted INTEGER)")
            db.execSQL("CREATE TABLE weight_records(date TEXT,weight REAL)")
            db.execSQL("INSERT INTO weight_records VALUES('2026-11-01',80)")
            val raw=HistorySnapshot.read(encodedSets=true,chartFields=true) { db.rawQuery(it,null) }
            val result=RustHistoryCore.process(raw)
            assertEquals("rust",result["engine"])
            val body=(result["charts"] as Map<*, *>)["body"] as Map<*, *>
            assertTrue(((body["bodyFatKg"] as Map<*, *>)["daily"] as List<*>).isEmpty())
            val bmi=(((body["bmi"] as Map<*, *>)["daily"] as List<*>).first() as Map<*, *>)["value"] as Number
            assertEquals(0.0,bmi.toDouble(),1e-6)
        }
    }
}
