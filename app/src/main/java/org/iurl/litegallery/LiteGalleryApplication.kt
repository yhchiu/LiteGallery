package org.iurl.litegallery

import android.app.Application
import com.bumptech.glide.Glide
import java.io.InputStream

class LiteGalleryApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // Apply locale before theme (order matters)
        LocaleHelper.applyLocale(this)

        // Apply theme when app starts
        ThemeHelper.applyTheme(this)

        // Register SMB model loader with Glide for loading images from SMB shares
        Glide.get(this).registry.prepend(
            String::class.java,
            InputStream::class.java,
            SmbModelLoaderFactory(this)
        )
    }

    override fun onTerminate() {
        super.onTerminate()
        // Clean up SMB connections
        SmbClient.disconnectAll()
    }
}