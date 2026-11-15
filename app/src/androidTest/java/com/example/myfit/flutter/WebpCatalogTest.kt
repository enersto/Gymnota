package com.example.myfit.flutter

import android.graphics.drawable.Animatable
import android.graphics.drawable.BitmapDrawable
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import coil.decode.BitmapFactoryDecoder
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.example.myfit.MyFitApplication
import com.example.myfit.data.CatalogMedia
import com.example.myfit.data.ExerciseCatalog
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WebpCatalogTest {
    @Test fun entireCatalogUsesRealWebpAndOldUrisStillAnimate() = runBlocking {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val context=instrumentation.targetContext
        val entries=ExerciseCatalog(context).load("en")
        assertEquals(1324,entries.size)
        for(entry in entries){
            assertTrue(entry.gifUri.endsWith(".webp"))
            context.assets.open(entry.gifUri.removePrefix("file:///android_asset/")).use {
                val header=String(it.readNBytes(12),Charsets.US_ASCII)
                assertTrue(header.startsWith("RIFF")&&header.endsWith("WEBP"))
            }
        }
        val entry=entries.first()
        val old=entry.gifUri.removeSuffix(".webp")+".gif"
        val template=entry.toTemplate().copy(animationUri=old,imageUri="/custom/photo.gif")
        assertEquals(entry.gifUri,CatalogMedia.animationUri(context,template))
        assertEquals("/custom/photo.gif",CatalogMedia.photoUri(template))
        assertEquals("file:///storage/emulated/0/custom.gif",CatalogMedia.bundledUri("file:///storage/emulated/0/custom.gif"))
        val loader=(context.applicationContext as MyFitApplication).newImageLoader()
        try {
            val request=ImageRequest.Builder(context).data(old).memoryCachePolicy(CachePolicy.DISABLED).diskCachePolicy(CachePolicy.DISABLED).build()
            val animated=loader.execute(request)
            assertTrue(animated is SuccessResult)
            val drawable=(animated as SuccessResult).drawable
            assertTrue(drawable is Animatable)
            instrumentation.runOnMainSync { (drawable as Animatable).start();assertTrue(drawable.isRunning);drawable.stop() }
            val thumbnail=loader.execute(request.newBuilder().decoderFactory(BitmapFactoryDecoder.Factory()).build())
            assertTrue(thumbnail is SuccessResult && thumbnail.drawable is BitmapDrawable)
        } finally { loader.shutdown() }
    }
}
