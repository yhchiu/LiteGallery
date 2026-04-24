package org.iurl.litegallery

import android.content.Context
import com.bumptech.glide.Priority
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.Options
import com.bumptech.glide.load.data.DataFetcher
import com.bumptech.glide.load.model.ModelLoader
import com.bumptech.glide.load.model.ModelLoaderFactory
import com.bumptech.glide.load.model.MultiModelLoaderFactory
import com.bumptech.glide.signature.ObjectKey
import java.io.InputStream

/**
 * Glide ModelLoader for loading images from SMB shares.
 * Intercepts String models starting with "smb://" and loads them via SmbClient.
 */
class SmbModelLoader(private val context: Context) : ModelLoader<String, InputStream> {

    companion object {
        private val IMAGE_EXTENSIONS = setOf(
            "jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "heif"
        )

        /** Check if an SMB path points to an image (not a video). */
        fun isSmbImage(path: String): Boolean {
            if (!SmbPath.isSmb(path)) return false
            val ext = path.substringAfterLast('.', "").lowercase()
            return IMAGE_EXTENSIONS.contains(ext)
        }
    }

    override fun buildLoadData(
        model: String,
        width: Int,
        height: Int,
        options: Options
    ): ModelLoader.LoadData<InputStream>? {
        if (!isSmbImage(model)) {
            return null // Only handle SMB image paths; videos can't be decoded as images
        }
        return ModelLoader.LoadData(ObjectKey(model), SmbDataFetcher(context, model))
    }

    override fun handles(model: String): Boolean {
        return isSmbImage(model)
    }
}

/**
 * Glide DataFetcher that reads image data from SMB shares.
 */
class SmbDataFetcher(
    private val context: Context,
    private val smbUrl: String
) : DataFetcher<InputStream> {

    private var inputStream: InputStream? = null

    override fun loadData(priority: Priority, callback: DataFetcher.DataCallback<in InputStream>) {
        try {
            val smbPath = SmbPath.parse(smbUrl)
            if (smbPath == null) {
                callback.onLoadFailed(Exception("Invalid SMB path: $smbUrl"))
                return
            }

            val stream = SmbClient.openInputStream(
                context,
                smbPath.host,
                smbPath.share,
                smbPath.path
            )
            inputStream = stream
            callback.onDataReady(stream)
        } catch (e: Exception) {
            callback.onLoadFailed(e)
        }
    }

    override fun cleanup() {
        try {
            inputStream?.close()
        } catch (_: Exception) {
        }
        inputStream = null
    }

    override fun cancel() {
        // No-op: SMB reads are synchronous
    }

    override fun getDataClass(): Class<InputStream> = InputStream::class.java

    override fun getDataSource(): DataSource = DataSource.REMOTE
}

/**
 * Factory for creating SmbModelLoader instances.
 * Register this with Glide in LiteGalleryApplication.
 */
class SmbModelLoaderFactory(private val context: Context) :
    ModelLoaderFactory<String, InputStream> {

    override fun build(multiFactory: MultiModelLoaderFactory): ModelLoader<String, InputStream> {
        return SmbModelLoader(context)
    }

    override fun teardown() {
        // No-op
    }
}
