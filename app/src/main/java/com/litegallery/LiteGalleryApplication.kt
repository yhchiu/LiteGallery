package com.litegallery

import android.app.Application

class LiteGalleryApplication : Application() {
    
    override fun onCreate() {
        super.onCreate()
        
        // Apply theme when app starts
        ThemeHelper.applyTheme(this)
    }
}