package org.iurl.litegallery

import android.content.Context
import androidx.preference.PreferenceManager
import java.io.File

object TrashBinStore {

    const val TRASH_FILE_PREFIX = ".trashed-"
    const val TRASH_RETENTION_DAYS_KEY = "trash_retention_days"
    const val TRASH_RETENTION_DEFAULT_DAYS = 30

    private const val PREFS_NAME = "trash_bin_store"
    private const val KEY_TRASHED_PATHS = "trashed_paths"
    private const val KEY_ORIGINAL_NAME_PREFIX = "original_name::"
    private const val KEY_TRASHED_AT_PREFIX = "trashed_at::"
    private const val KEY_LAST_REINDEX_AT = "last_reindex_at"
    private const val REINDEX_INTERVAL_MS = 6L * 60L * 60L * 1000L

    fun rememberTrashedFile(
        context: Context,
        trashedPath: String,
        originalName: String,
        trashedAtMs: Long = System.currentTimeMillis()
    ) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val paths = prefs.getStringSet(KEY_TRASHED_PATHS, emptySet())?.toMutableSet() ?: mutableSetOf()
        paths.add(trashedPath)

        prefs.edit()
            .putStringSet(KEY_TRASHED_PATHS, paths)
            .putString(originalNameKey(trashedPath), originalName)
            .putLong(trashedAtKey(trashedPath), trashedAtMs)
            .apply()
    }

    fun getTrashedPaths(context: Context): Set<String> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getStringSet(KEY_TRASHED_PATHS, emptySet())?.toSet() ?: emptySet()
    }

    fun removeTrashedPath(context: Context, trashedPath: String) {
        removeTrashedPaths(context, listOf(trashedPath))
    }

    fun removeTrashedPaths(context: Context, trashedPaths: Collection<String>) {
        if (trashedPaths.isEmpty()) return

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val currentPaths = prefs.getStringSet(KEY_TRASHED_PATHS, emptySet())?.toMutableSet() ?: mutableSetOf()
        val editor = prefs.edit()

        trashedPaths.forEach { path ->
            currentPaths.remove(path)
            editor.remove(originalNameKey(path))
            editor.remove(trashedAtKey(path))
        }

        editor.putStringSet(KEY_TRASHED_PATHS, currentPaths).apply()
    }

    fun resolveOriginalName(context: Context, trashedFile: File): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val rememberedName = prefs.getString(originalNameKey(trashedFile.absolutePath), null)
        return rememberedName ?: fallbackOriginalNameFromTrashedName(trashedFile.name)
    }

    fun fallbackOriginalNameFromTrashedName(trashedName: String): String {
        if (!trashedName.startsWith(TRASH_FILE_PREFIX)) return trashedName

        val payload = trashedName.removePrefix(TRASH_FILE_PREFIX)
        if (payload.isEmpty()) return trashedName

        // Backward-compat parser for ".trashed-<index>-<original>" naming.
        // If no clear pattern can be determined, keep full payload.
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

    private fun originalNameKey(path: String): String {
        return "$KEY_ORIGINAL_NAME_PREFIX$path"
    }

    private fun trashedAtKey(path: String): String {
        return "$KEY_TRASHED_AT_PREFIX$path"
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
            return CleanupResult(emptyList(), 0)
        }

        val thresholdMs = nowMs - retentionDays * 24L * 60L * 60L * 1000L
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val backfillEditor = prefs.edit()
        var hasBackfill = false

        val removedPaths = mutableListOf<String>()
        var failedCount = 0

        getTrashedPaths(context).forEach { path ->
            val file = File(path)
            val storedTrashedAt = prefs.getLong(trashedAtKey(path), -1L)
            val normalizedTrashedAt = if (storedTrashedAt > 0L) {
                storedTrashedAt
            } else {
                // Legacy entries might not have trashedAt.
                // Use "now" as a safe migration default to avoid accidental immediate deletion.
                hasBackfill = true
                backfillEditor.putLong(trashedAtKey(path), nowMs)
                nowMs
            }

            when {
                !file.exists() -> removedPaths.add(path)
                normalizedTrashedAt >= thresholdMs -> Unit
                file.delete() -> removedPaths.add(path)
                else -> failedCount++
            }
        }

        if (hasBackfill) {
            backfillEditor.apply()
        }

        if (removedPaths.isNotEmpty()) {
            removeTrashedPaths(context, removedPaths)
        }

        return CleanupResult(removedPaths, failedCount)
    }

    fun reindexOrphanTrashedFiles(
        context: Context,
        nowMs: Long = System.currentTimeMillis(),
        force: Boolean = false
    ): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastReindexAt = prefs.getLong(KEY_LAST_REINDEX_AT, 0L)
        if (!force && nowMs - lastReindexAt < REINDEX_INTERVAL_MS) {
            return 0
        }

        val existingPaths = getTrashedPaths(context).toMutableSet()
        var addedCount = 0

        getAccessibleStoragePaths(context).forEach { rootPath ->
            val root = File(rootPath)
            scanForTrashedFiles(
                directory = root,
                maxDepth = 6,
                currentDepth = 0
            ) { file ->
                val path = file.absolutePath
                if (path in existingPaths) return@scanForTrashedFiles

                val originalName = fallbackOriginalNameFromTrashedName(file.name)
                // Orphan files have no reliable trashed timestamp; use now for safe retention behavior.
                rememberTrashedFile(
                    context = context,
                    trashedPath = path,
                    originalName = originalName,
                    trashedAtMs = nowMs
                )
                existingPaths.add(path)
                addedCount++
            }
        }

        prefs.edit().putLong(KEY_LAST_REINDEX_AT, nowMs).apply()
        return addedCount
    }

    private fun getAccessibleStoragePaths(context: Context): List<String> {
        val paths = mutableListOf<String>()

        android.os.Environment.getExternalStorageDirectory()?.let { primaryStorage ->
            if (primaryStorage.exists() && primaryStorage.canRead()) {
                paths.add(primaryStorage.absolutePath)
            }
        }

        context.getExternalFilesDirs(null)?.forEach { dir ->
            dir?.let { externalDir ->
                var parent = externalDir.parentFile
                while (parent != null && parent.name != "Android") {
                    val tempParent = parent.parentFile
                    if (tempParent == null) break
                    parent = tempParent
                }
                parent?.let { root ->
                    if (root.exists() && root.canRead() && !paths.contains(root.absolutePath)) {
                        paths.add(root.absolutePath)
                    }
                }
            }
        }

        return paths
    }

    private fun scanForTrashedFiles(
        directory: File,
        maxDepth: Int,
        currentDepth: Int,
        onFound: (File) -> Unit
    ) {
        if (currentDepth > maxDepth || !directory.exists() || !directory.isDirectory) return

        try {
            directory.listFiles()?.forEach { file ->
                when {
                    file.isFile && file.name.startsWith(TRASH_FILE_PREFIX) -> onFound(file)
                    file.isDirectory && !file.name.startsWith(".") -> {
                        scanForTrashedFiles(file, maxDepth, currentDepth + 1, onFound)
                    }
                }
            }
        } catch (_: SecurityException) {
            // Skip restricted folders.
        } catch (_: Exception) {
            // Skip unreadable folders.
        }
    }

    data class CleanupResult(
        val removedPaths: List<String>,
        val failedCount: Int
    )
}
