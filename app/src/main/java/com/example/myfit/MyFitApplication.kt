package com.example.myfit

import android.app.Application
import com.example.myfit.data.BundledAnimationInterceptor
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.ImageDecoderDecoder

class MyFitApplication : Application(), ImageLoaderFactory {
    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .components {
            add(BundledAnimationInterceptor())
            add(ImageDecoderDecoder.Factory())
        }
        .build()
}
