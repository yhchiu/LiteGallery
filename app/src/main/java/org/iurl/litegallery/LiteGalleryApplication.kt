package org.iurl.litegallery

import android.app.Application

class LiteGalleryApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // Apply locale before theme (order matters)
        LocaleHelper.applyLocale(this)

        // First-launch / upgrade: ensure a pack pref exists (default Warm Paper)
        ThemeHelper.migrateLegacyIfNeeded(this)

        // Apply theme when app starts (consults the active pack to decide night mode)
        ThemeHelper.applyTheme(this)

        // Register any optional media sources for the current flavor (no-op in `core`).
        MediaSourceBootstrap.init(this)
    }

    override fun onTerminate() {
        super.onTerminate()
        // Release any media-source resources (e.g. SMB connections).
        MediaSourceRegistry.all().forEach { it.shutdown() }
    }
}
