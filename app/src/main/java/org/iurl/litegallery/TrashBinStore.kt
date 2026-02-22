package org.iurl.litegallery

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import androidx.preference.PreferenceManager
import java.io.File

object TrashBinStore {

    const val TRASH_FILE_PREFIX = ".trashed-"
    const val TRASH_RETENTION_DAYS_KEY = "trash_retention_days"
    const val TRASH_RETENTION_DEFAULT_DAYS = 30

    fun rememberTrashedFile(
        context: Context,
        trashedUri: Uri,
        originalUri: Uri?,
        originalName: String,
        originalPathHint: String? = null,
        trashedAtMs: Long = System.currentTimeMillis()
    ) {
        val normalizedOriginalName = originalName.ifBlank {
            fallbackOriginalNameFromTrashedName(
                trashedUri.lastPathSegment.orEmpty()
            )
        }
        val record = TrashBinDatabase.TrashRecord(
            trashedUri = trashedUri.toString(),
            originalUri = originalUri?.toString(),
            originalName = normalizedOriginalName,
            originalPathHint = originalPathHint,
            trashedAtMs = trashedAtMs
        )
        openDatabase(context).upsertRecord(record)
    }

    fun getTrashedRecords(context: Context): List<TrashBinDatabase.TrashRecord> {
        return openDatabase(context).getAllRecords()
    }

    fun getTrashedRecord(context: Context, trashedUri: String): TrashBinDatabase.TrashRecord? {
        return openDatabase(context).getRecordByTrashedUri(trashedUri)
    }

    fun removeTrashedUri(context: Context, trashedUri: String) {
        if (trashedUri.isBlank()) return
        val database = openDatabase(context)
        database.removeByTrashedUris(listOf(trashedUri))

        val scannerPath = resolveScannerPath(trashedUri) ?: trashedUri.takeIf { it.startsWith("/") }
        if (scannerPath.isNullOrBlank()) return

        val matchedRecordUri = database.getAllRecords()
            .firstOrNull { resolveScannerPath(it.trashedUri) == scannerPath }
            ?.trashedUri
            ?: return
        database.removeByTrashedUris(listOf(matchedRecordUri))
    }

    fun removeTrashedUris(context: Context, trashedUris: Collection<String>) {
        if (trashedUris.isEmpty()) return
        openDatabase(context).removeByTrashedUris(trashedUris)
    }

    fun fallbackOriginalNameFromTrashedName(trashedName: String): String {
        if (!trashedName.startsWith(TRASH_FILE_PREFIX)) return trashedName

        val payload = trashedName.removePrefix(TRASH_FILE_PREFIX)
        if (payload.isEmpty()) return trashedName

        val firstDash = payload.indexOf('-')
        if (firstDash > 0) {
            val maybeIndex = payload.substring(0, firstDash)
            val remainder = payload.substring(firstDash + 1)
            if (maybeIndex.all { it.isDigit() } && remainder.isNotBlank()) {
                return remainder
            }
        }

        return payload
    }

    fun getTrashRetentionDays(context: Context): Int {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        return prefs.getString(TRASH_RETENTION_DAYS_KEY, TRASH_RETENTION_DEFAULT_DAYS.toString())
            ?.toIntOrNull()
            ?.coerceAtLeast(0)
            ?: TRASH_RETENTION_DEFAULT_DAYS
    }

    fun cleanupExpiredTrash(context: Context, nowMs: Long = System.currentTimeMillis()): CleanupResult {
        val retentionDays = getTrashRetentionDays(context)
        if (retentionDays <= 0) {
            return CleanupResult(emptyList(), emptyList(), 0)
        }

        val thresholdMs = nowMs - retentionDays * 24L * 60L * 60L * 1000L
        val records = openDatabase(context).getAllRecords()

        val removedUris = mutableListOf<String>()
        val removedScannerPaths = mutableListOf<String>()
        var failedCount = 0

        records.forEach { record ->
            if (record.trashedAtMs >= thresholdMs) return@forEach

            when (val result = removeTrashedReference(context, record.trashedUri)) {
                is RemoveResult.Removed -> {
                    removedUris.add(record.trashedUri)
                    result.scannerPath?.let(removedScannerPaths::add)
                }
                is RemoveResult.Missing -> {
                    removedUris.add(record.trashedUri)
                    result.scannerPath?.let(removedScannerPaths::add)
                }
                is RemoveResult.Failed -> {
                    failedCount++
                }
            }
        }

        if (removedUris.isNotEmpty()) {
            openDatabase(context).removeByTrashedUris(removedUris)
        }

        return CleanupResult(
            removedUris = removedUris,
            removedScannerPaths = removedScannerPaths,
            failedCount = failedCount
        )
    }

    fun resolveScannerPath(reference: String): String? {
        if (reference.isBlank()) return null
        if (reference.startsWith("/")) return reference
        val uri = runCatching { Uri.parse(reference) }.getOrNull() ?: return null
        return when (uri.scheme) {
            "file" -> uri.path?.takeIf { it.isNotBlank() }
            else -> null
        }
    }

    private fun openDatabase(context: Context): TrashBinDatabase {
        return TrashBinDatabase.getInstance(context.applicationContext)
    }

    private sealed class RemoveResult(val scannerPath: String?) {
        class Removed(scannerPath: String? = null) : RemoveResult(scannerPath)
        class Missing(scannerPath: String? = null) : RemoveResult(scannerPath)
        class Failed(scannerPath: String? = null) : RemoveResult(scannerPath)
    }

    private fun removeTrashedReference(context: Context, trashedUriString: String): RemoveResult {
        if (trashedUriString.isBlank()) return RemoveResult.Missing()
        val parsedUri = runCatching { Uri.parse(trashedUriString) }.getOrNull()
        val scannerPath = resolveScannerPath(trashedUriString)

        if (parsedUri == null || parsedUri.scheme.isNullOrBlank()) {
            val file = File(trashedUriString)
            return when {
                !file.exists() -> RemoveResult.Missing(file.absolutePath)
                file.delete() -> RemoveResult.Removed(file.absolutePath)
                else -> RemoveResult.Failed(file.absolutePath)
            }
        }

        return when (parsedUri.scheme) {
            "file" -> {
                val path = parsedUri.path
                if (path.isNullOrBlank()) {
                    RemoveResult.Missing()
                } else {
                    val file = File(path)
                    when {
                        !file.exists() -> RemoveResult.Missing(file.absolutePath)
                        file.delete() -> RemoveResult.Removed(file.absolutePath)
                        else -> RemoveResult.Failed(file.absolutePath)
                    }
                }
            }
            "content" -> {
                if (isContentUriMissing(context, parsedUri)) return RemoveResult.Missing(scannerPath)

                try {
                    val deleted = if (DocumentsContract.isDocumentUri(context, parsedUri)) {
                        DocumentsContract.deleteDocument(context.contentResolver, parsedUri)
                    } else {
                        context.contentResolver.delete(parsedUri, null, null) > 0
                    }
                    if (deleted) RemoveResult.Removed(scannerPath) else RemoveResult.Failed(scannerPath)
                } catch (_: SecurityException) {
                    RemoveResult.Failed(scannerPath)
                } catch (_: Exception) {
                    RemoveResult.Failed(scannerPath)
                }
            }
            else -> RemoveResult.Failed(scannerPath)
        }
    }

    private fun isContentUriMissing(context: Context, uri: Uri): Boolean {
        return try {
            context.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor ->
                !cursor.moveToFirst()
            } ?: true
        } catch (_: SecurityException) {
            false
        } catch (_: Exception) {
            false
        }
    }

    data class CleanupResult(
        val removedUris: List<String>,
        val removedScannerPaths: List<String>,
        val failedCount: Int
    )
}
