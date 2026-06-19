package org.iurl.litegallery

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.bumptech.glide.load.Options
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class SmbModelLoaderTest {

    private lateinit var loader: SmbModelLoader

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        loader = SmbModelLoader(context)
    }

    @Test
    fun isSmbImage_acceptsOnlySmbImageExtensions() {
        assertTrue(SmbModelLoader.isSmbImage("smb://host/share/photo.JPG"))
        assertTrue(SmbModelLoader.isSmbImage("smb://host/share/folder/image.heic"))
        assertFalse(SmbModelLoader.isSmbImage("smb://host/share/video.mp4"))
        assertFalse(SmbModelLoader.isSmbImage("smb://host/share/no-extension"))
        assertFalse(SmbModelLoader.isSmbImage("/storage/emulated/0/DCIM/photo.jpg"))
    }

    @Test
    fun handles_returnsTrueOnlyForSmbImages() {
        assertTrue(loader.handles("smb://host/share/photo.webp"))
        assertFalse(loader.handles("smb://host/share/movie.mkv"))
        assertFalse(loader.handles("content://media/external/images/media/1"))
    }

    @Test
    fun buildLoadData_returnsFetcherOnlyForSmbImages() {
        assertNotNull(loader.buildLoadData("smb://host/share/photo.png", 100, 100, Options()))
        assertNull(loader.buildLoadData("smb://host/share/movie.mp4", 100, 100, Options()))
        assertNull(loader.buildLoadData("/storage/emulated/0/DCIM/photo.png", 100, 100, Options()))
    }
}
