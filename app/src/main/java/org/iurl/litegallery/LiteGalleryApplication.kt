package org.iurl.litegallery

import android.app.Application
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import java.io.InputStream

class LiteGalleryApplication : Application() {

    companion object {
        val sharedFolderViewPool: RecyclerView.RecycledViewPool =
            RecyclerView.RecycledViewPool().apply {
                setMaxRecycledViews(0, 30)
                setMaxRecycledViews(1, 20)
                setMaxRecycledViews(2, 20)
                setMaxRecycledViews(10, 12)
                setMaxRecycledViews(11, 30)
                setMaxRecycledViews(12, 20)
                setMaxRecycledViews(13, 20)
            }
    }

    override fun onCreate() {
        super.onCreate()

        // Apply locale before theme (order matters)
        LocaleHelper.applyLocale(this)

        // First-launch / upgrade: ensure a pack pref exists (default Warm Paper)
        ThemeHelper.migrateLegacyIfNeeded(this)

        // Apply theme when app starts (consults the active pack to decide night mode)
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
