package org.iurl.litegallery

import android.app.Application

class LiteGalleryApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // Apply locale before theme (order matters)
        LocaleHelper.applyLocale(this)

        // Apply theme when app starts
        ThemeHelper.applyTheme(this)
    }
}