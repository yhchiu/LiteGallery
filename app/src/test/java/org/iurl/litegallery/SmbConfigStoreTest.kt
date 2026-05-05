package org.iurl.litegallery

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
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
class SmbConfigStoreTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        clearStore()
    }

    @After
    fun tearDown() {
        clearStore()
    }

    @Test
    fun addServer_generatesIdAndPersistsFields() {
        val saved = SmbConfigStore.addServer(
            context,
            SmbConfig(
                id = "",
                displayName = "NAS",
                host = "nas.local",
                share = "Photos",
                path = "Trips",
                port = 1445,
                username = "arthur",
                password = "secret"
            )
        )

        assertNotEquals("", saved.id)
        assertTrue(saved.isAuthenticated)
        assertEquals(saved, SmbConfigStore.getServerById(context, saved.id))
    }

    @Test
    fun getServerByHost_matchesIgnoringCase() {
        val saved = SmbConfigStore.addServer(
            context,
            SmbConfig(id = "one", displayName = "NAS", host = "NAS.LOCAL", isGuest = true)
        )

        assertEquals(saved, SmbConfigStore.getServerByHost(context, "nas.local"))
    }

    @Test
    fun updateServer_replacesMatchingIdOnly() {
        SmbConfigStore.addServer(context, SmbConfig(id = "one", displayName = "Old", host = "one.local"))
        SmbConfigStore.addServer(context, SmbConfig(id = "two", displayName = "Other", host = "two.local"))

        SmbConfigStore.updateServer(
            context,
            SmbConfig(id = "one", displayName = "New", host = "one.local", share = "Media")
        )

        assertEquals("New", SmbConfigStore.getServerById(context, "one")?.displayName)
        assertEquals("Media", SmbConfigStore.getServerById(context, "one")?.share)
        assertEquals("Other", SmbConfigStore.getServerById(context, "two")?.displayName)
    }

    @Test
    fun deleteServer_removesMatchingIdOnly() {
        SmbConfigStore.addServer(context, SmbConfig(id = "one", displayName = "One", host = "one.local"))
        SmbConfigStore.addServer(context, SmbConfig(id = "two", displayName = "Two", host = "two.local"))

        SmbConfigStore.deleteServer(context, "one")

        assertNull(SmbConfigStore.getServerById(context, "one"))
        assertNotNull(SmbConfigStore.getServerById(context, "two"))
    }

    @Test
    fun getAllServers_returnsEmptyListForMalformedJson() {
        context.getSharedPreferences("smb_servers", Context.MODE_PRIVATE)
            .edit()
            .putString("servers_json", "{not json")
            .commit()

        assertTrue(SmbConfigStore.getAllServers(context).isEmpty())
    }

    private fun clearStore() {
        context.getSharedPreferences("smb_servers", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }
}
