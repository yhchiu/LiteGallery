package org.iurl.litegallery

import android.content.Context
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class PlaybackDiagnosticsTest {

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
    fun recordMethods_doNothingWhenDiagnosticsDisabled() {
        PlaybackDiagnostics.recordManualReload(context, "/storage/DCIM/photo.jpg")

        assertFalse(PlaybackDiagnostics.isEnabled(context))
        assertEquals("", PlaybackDiagnostics.buildRecentReport(context))
        assertFalse(PlaybackDiagnostics.copyRecentReportToClipboard(context))
    }

    @Test
    fun buildRecentReport_includesRecentEnabledEventsAndSanitizesDetails() {
        enableDiagnostics()

        PlaybackDiagnostics.recordPlaybackError(
            context = context,
            mediaPath = "content://media/external/video/media/12",
            errorCodeName = "SOURCE_ERROR",
            errorMessage = "line one\nline two",
            retryCount = 2
        )

        val report = PlaybackDiagnostics.buildRecentReport(context, count = 5)

        assertTrue(report.contains("LiteGallery Playback Diagnostics (1 events)"))
        assertTrue(report.contains("player_error"))
        assertTrue(report.contains("file=12"))
        assertTrue(report.contains("uri_type=content"))
        assertTrue(report.contains("message=line one line two"))
    }

    @Test
    fun buildRecentReport_limitsStoredEventsToMaximumAndReturnsRequestedTail() {
        enableDiagnostics()

        repeat(105) { index ->
            PlaybackDiagnostics.recordRetry(
                context = context,
                mediaPath = "/storage/DCIM/clip-$index.mp4",
                reason = "timeout",
                retryCount = index,
                maxRetries = 105
            )
        }

        val report = PlaybackDiagnostics.buildRecentReport(context, count = 120)

        assertTrue(report.contains("LiteGallery Playback Diagnostics (100 events)"))
        assertFalse(report.contains("clip-0.mp4"))
        assertFalse(report.contains("clip-4.mp4"))
        assertTrue(report.contains("clip-5.mp4"))
        assertTrue(report.contains("clip-104.mp4"))
    }

    private fun enableDiagnostics() {
        PreferenceManager.getDefaultSharedPreferences(context)
            .edit()
            .putBoolean(PlaybackDiagnostics.KEY_ENABLE_PLAYBACK_DIAGNOSTICS, true)
            .commit()
    }

    private fun clearPrefs() {
        PreferenceManager.getDefaultSharedPreferences(context)
            .edit()
            .clear()
            .commit()
        context.getSharedPreferences("playback_diagnostics", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }
}
