package com.example

import android.app.Application
import com.example.core.di.AppContainer
import com.example.core.di.DefaultAppContainer
import com.example.core.pdf.PdfEngine
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader

class MyPrevApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = DefaultAppContainer(this)
        PdfEngine.clearUnlockedCache(applicationContext)
        // Initialize PdfBox Android resources
        try {
            PDFBoxResourceLoader.init(applicationContext)
        } catch (_: Exception) {
            // Safe fallback if assets are not initialized in preview/test environments
        }
    }
}
