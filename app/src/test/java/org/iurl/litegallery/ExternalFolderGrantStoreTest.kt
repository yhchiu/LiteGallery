package org.iurl.litegallery

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class ExternalFolderGrantStoreTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        clearPrefs()
    }

    @After
    fun tearDown() {
        clearPrefs()
    }

    @Test
    fun getAllMappings_returnsSortedValidGrantMappings() {
        val treeUri = Uri.parse("content://com.android.externalstorage.documents/tree/primary%3ADCIM")
        context.getSharedPreferences("external_folder_grants", Context.MODE_PRIVATE)
            .edit()
            .putString("grant::z.authority|primary:Pictures", treeUri.toString())
            .putString("grant::a.authority|primary:DCIM", "")
            .putString("grant::malformed", "content://ignored")
            .putString("other", "content://ignored")
            .commit()

        val mappings = ExternalFolderGrantStore.getAllMappings(context)

        assertEquals(listOf("a.authority|primary:DCIM", "z.authority|primary:Pictures"), mappings.map { it.lookupKey })
        assertNull(mappings[0].treeUri)
        assertEquals(treeUri, mappings[1].treeUri)
    }

    @Test
    fun clearAllMappings_removesOnlyGrantPrefsAndReturnsRemovedCount() {
        val prefs = context.getSharedPreferences("external_folder_grants", Context.MODE_PRIVATE)
        prefs.edit()
            .putString("grant::a.authority|primary:DCIM", "content://one")
            .putString("grant::b.authority|primary:Pictures", "content://two")
            .putString("unrelated", "kept")
            .commit()

        val removed = ExternalFolderGrantStore.clearAllMappings(context)

        assertEquals(2, removed)
        assertEquals("kept", prefs.getString("unrelated", null))
        assertTrue(ExternalFolderGrantStore.getAllMappings(context).isEmpty())
    }

    private fun clearPrefs() {
        context.getSharedPreferences("external_folder_grants", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }
}
