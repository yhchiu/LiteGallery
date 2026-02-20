package org.iurl.litegallery

import android.content.Context
import android.text.format.DateFormat
import androidx.preference.PreferenceManager

object PlaybackDiagnostics {

    private const val PREFS_NAME = "playback_diagnostics"
    private const val KEY_EVENTS = "events"
    const val KEY_ENABLE_PLAYBACK_DIAGNOSTICS = "enable_playback_diagnostics"
    private const val MAX_EVENTS = 100
    private const val DEFAULT_REPORT_COUNT = 40

    fun isEnabled(context: Context): Boolean {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        return prefs.getBoolean(KEY_ENABLE_PLAYBACK_DIAGNOSTICS, false)
    }

    fun recordManualReload(context: Context, mediaPath: String?) {
        record(
            context = context,
            type = "manual_reload",
            mediaPath = mediaPath,
            details = mapOf("uri_type" to uriType(mediaPath))
        )
    }

    fun recordPlaybackError(
        context: Context,
        mediaPath: String?,
        errorCodeName: String,
        errorMessage: String?,
        retryCount: Int
    ) {
        val message = errorMessage?.take(180).orEmpty()
        record(
            context = context,
            type = "player_error",
            mediaPath = mediaPath,
            details = mapOf(
                "uri_type" to uriType(mediaPath),
                "error_code" to errorCodeName,
                "retry_count" to retryCount.toString(),
                "message" to message
            )
        )
    }

    fun recordLoadingTimeout(
        context: Context,
        mediaPath: String?,
        playbackState: Int,
        retryCount: Int,
        maxRetries: Int
    ) {
        record(
            context = context,
            type = "loading_timeout",
            mediaPath = mediaPath,
            details = mapOf(
                "uri_type" to uriType(mediaPath),
                "state" to playbackState.toString(),
                "retry_count" to retryCount.toString(),
                "max_retries" to maxRetries.toString()
            )
        )
    }

    fun recordRetry(
        context: Context,
        mediaPath: String?,
        reason: String,
        retryCount: Int,
        maxRetries: Int
    ) {
        record(
            context = context,
            type = "retry",
            mediaPath = mediaPath,
            details = mapOf(
                "uri_type" to uriType(mediaPath),
                "reason" to reason,
                "retry_count" to retryCount.toString(),
                "max_retries" to maxRetries.toString()
            )
        )
    }

    fun recordMarkedInvalid(
        context: Context,
        mediaPath: String?,
        reason: String,
        retryCount: Int
    ) {
        record(
            context = context,
            type = "marked_invalid",
            mediaPath = mediaPath,
            details = mapOf(
                "uri_type" to uriType(mediaPath),
                "reason" to reason,
                "retry_count" to retryCount.toString()
            )
        )
    }

    fun buildRecentReport(context: Context, count: Int = DEFAULT_REPORT_COUNT): String {
        if (!isEnabled(context)) return ""

        val events = getRecentEvents(context, count)
        if (events.isEmpty()) return ""

        val header = "LiteGallery Playback Diagnostics (${events.size} events)"
        return buildString {
            appendLine(header)
            appendLine("-".repeat(header.length))
            events.forEach { appendLine(it) }
        }
    }

    fun copyRecentReportToClipboard(context: Context, count: Int = DEFAULT_REPORT_COUNT): Boolean {
        val report = buildRecentReport(context, count)
        if (report.isBlank()) return false

        val clipboardManager =
            context.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                ?: return false
        val clip = android.content.ClipData.newPlainText("Playback diagnostics", report)
        clipboardManager.setPrimaryClip(clip)
        return true
    }

    @Synchronized
    private fun record(
        context: Context,
        type: String,
        mediaPath: String?,
        details: Map<String, String>
    ) {
        if (!isEnabled(context)) return

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val currentEvents = prefs.getString(KEY_EVENTS, "")
            ?.split('\n')
            ?.filter { it.isNotBlank() }
            ?.toMutableList()
            ?: mutableListOf()

        val timestamp = DateFormat.format("yyyy-MM-dd HH:mm:ss", System.currentTimeMillis()).toString()
        val baseName = mediaPath?.substringAfterLast('/') ?: "-"
        val compactDetails = details
            .filter { it.value.isNotBlank() }
            .entries
            .joinToString(separator = " | ") { "${it.key}=${sanitize(it.value)}" }

        val line = if (compactDetails.isBlank()) {
            "[$timestamp] $type | file=$baseName"
        } else {
            "[$timestamp] $type | file=$baseName | $compactDetails"
        }

        currentEvents.add(line)
        while (currentEvents.size > MAX_EVENTS) {
            currentEvents.removeAt(0)
        }

        prefs.edit().putString(KEY_EVENTS, currentEvents.joinToString("\n")).apply()
    }

    private fun getRecentEvents(context: Context, count: Int): List<String> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val allEvents = prefs.getString(KEY_EVENTS, "")
            ?.split('\n')
            ?.filter { it.isNotBlank() }
            .orEmpty()
        return allEvents.takeLast(count)
    }

    private fun sanitize(value: String): String {
        return value.replace('\n', ' ').replace('\r', ' ')
    }

    private fun uriType(path: String?): String {
        return when {
            path.isNullOrBlank() -> "unknown"
            path.startsWith("content://") -> "content"
            else -> "file"
        }
    }
}
