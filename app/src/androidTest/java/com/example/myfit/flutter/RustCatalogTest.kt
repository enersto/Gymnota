package com.example.myfit.flutter

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.myfit.data.ExerciseCatalog
import com.example.myfit.data.RustCatalog
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.Locale

@RunWith(AndroidJUnit4::class)
class RustCatalogTest {
    @Test fun catalogueIsReusableAcrossPageInstancesAndLocaleTags() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val first = ExerciseCatalog(context).load("zh")
        assertSame(first, ExerciseCatalog.cached("zh-CN"))
        assertSame(first, ExerciseCatalog(context).load("zh"))
        val filtered = ExerciseCatalog(context).search("en", " PRESS ", "part_chest")
        assertSame(filtered, ExerciseCatalog.cached("en-US", "press", "part_chest"))
        assertTrue(filtered.isNotEmpty())
        assertTrue(filtered.all { it.bodyPart == "part_chest" })
        assertNull(ExerciseCatalog.cached("en", "press", "part_back"))
    }

    @Test fun rustMatchesLegacyForEveryLanguageAndSearch() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val catalog = ExerciseCatalog(context)
        // Installs the immutable catalogue; bypass the fallback below so JNI failures fail the test.
        catalog.load("en")
        val path = File(context.noBackupFilesDir, "exercise_catalog/gymnota-catalog-v4.sqlite").path
        for (language in listOf("zh", "en", "es", "de", "ja", "zh-CN", "xx")) {
            val expected = catalog.loadLegacy(language)
            val actual = RustCatalog.query(path, language, "", null)
            assertNotNull("Rust must execute for $language", actual)
            assertEquals(1324, actual!!.size)
            assertEquals("All fields, order and nullable values: $language", expected, actual)
            val terms = listOf("", " PRESS ", "卧推", "ü", "上", "プレス", "0001", "%", "_", "' OR 1=1 --", expected.first().instruction.take(12))
            for (query in terms) for (part in listOf(null, "", "part_chest", "part_back", "missing")) {
                val q = query.trim().lowercase(Locale.ROOT)
                val filtered = expected.filter { e -> (part == null || e.bodyPart == part) &&
                    listOf(e.name, e.canonicalName, e.sourceId, e.instruction).any { it.lowercase(Locale.ROOT).contains(q) } }
                assertEquals("$language / $query / $part", filtered, RustCatalog.query(path, language, query, part))
            }
        }
        assertNull(RustCatalog.query(path + ".missing", "en", "", null))
        assertFalse(File(path + ".missing").exists())
        assertTrue(RustCatalog.query(path, "en' OR 1=1 --", "", null)!!.isNotEmpty())
    }
}
