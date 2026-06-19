package org.iurl.litegallery

import android.app.Application
import com.bumptech.glide.Glide
import java.io.InputStream

/**
 * `plus` flavor: registers the SMB media source and its Glide image loader.
 */
object MediaSourceBootstrap {
    fun init(app: Application) {
        MediaSourceRegistry.register(SmbMediaSource)

        // Allow Glide to load image thumbnails from SMB shares (smb:// String models).
        Glide.get(app).registry.prepend(
            String::class.java,
            InputStream::class.java,
            SmbModelLoaderFactory(app)
        )
    }
}
