package org.iurl.litegallery

import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

class MediaIndexRepository(context: Context) {

    private val appContext = context.applicationContext
    private val database = MediaIndexDatabase.getInstance(appContext)
    private val dao = database.mediaIndexDao()
    private val primaryExternalRootPath: String? =
        Environment.getExternalStorageDirectory()?.absolutePath

    suspend fun getFolders(): List<MediaFolder> = withContext(Dispatchers.IO) {
        synchronizeIfNeeded()
        dao.getFolders().map { it.toMediaFolder() }
    }

    suspend fun getCachedFolders(): List<MediaFolder> = withContext(Dispatchers.IO) {
        dao.getFolders().map { it.toMediaFolder() }
    }

    suspend fun getMediaInFolder(folderPath: String): List<MediaItem> = withContext(Dispatchers.IO) {
        synchronizeIfNeeded()
        dao.getMediaInFolder(folderPath).map { it.toMediaItem() }
    }

    suspend fun getCachedMediaByIds(ids: List<Long>): List<MediaItem> = withContext(Dispatchers.IO) {
        if (ids.isEmpty()) return@withContext emptyList()
        dao.findByIds(ids).map { it.toMediaItem() }
    }

    suspend fun getCachedMediaInFolder(folderPath: String): List<MediaItem> = withContext(Dispatchers.IO) {
        dao.getMediaInFolder(folderPath).map { it.toMediaItem() }
    }

    suspend fun removePath(path: String) = withContext(Dispatchers.IO) {
        if (path.isBlank()) return@withContext
        database.withTransaction {
            if (dao.deleteMediaByPath(path) > 0) {
                rebuildFolderIndexLocked(System.currentTimeMillis())
            }
        }
    }

    suspend fun updateMediaItem(oldPath: String, item: MediaItem) = withContext(Dispatchers.IO) {
        if (oldPath.isBlank() || item.path.isBlank() || item.isSmb) return@withContext
        val folderPath = folderPathForMediaItem(item) ?: return@withContext
        val nowMs = System.currentTimeMillis()
        database.withTransaction {
            if (dao.updateMediaByPath(
                    oldPath = oldPath,
                    newPath = item.path,
                    folderPath = folderPath,
                    name = item.name,
                    dateModifiedMs = item.dateModified,
                    sizeBytes = item.size.coerceAtLeast(0L),
                    mimeType = item.mimeType,
                    durationMs = item.duration.coerceAtLeast(0L),
                    width = item.width.coerceAtLeast(0),
                    height = item.height.coerceAtLeast(0),
                    updatedAtMs = nowMs
                ) > 0
            ) {
                rebuildFolderIndexLocked(nowMs)
            }
        }
    }

    suspend fun updateMetadata(items: List<MediaItem>) = withContext(Dispatchers.IO) {
        val localItems = items.filter { !it.isSmb && it.path.isNotBlank() }
        if (localItems.isEmpty()) return@withContext

        val nowMs = System.currentTimeMillis()
        database.withTransaction {
            localItems.forEach { item ->
                dao.updateMetadataByPath(
                    path = item.path,
                    sizeBytes = item.size.coerceAtLeast(0L),
                    width = item.width.coerceAtLeast(0),
                    height = item.height.coerceAtLeast(0),
                    durationMs = item.duration.coerceAtLeast(0L),
                    updatedAtMs = nowMs
                )
            }
            rebuildFolderIndexLocked(nowMs)
        }
    }

    private suspend fun synchronizeIfNeeded() {
        syncMutex.withLock {
            val checkpoint = readMediaStoreCheckpoint()
            val state = dao.getSyncState(checkpoint.volumeName)
            val nowMs = System.currentTimeMillis()

            if (state != null) {
                if (checkpoint.supportsGeneration &&
                    state.version == checkpoint.version &&
                    state.generation == checkpoint.generation
                ) {
                    return
                }

                if (!checkpoint.supportsGeneration &&
                    checkpoint.supportsVersion &&
                    state.version == checkpoint.version
                ) {
                    return
                }

                if (!checkpoint.supportsGeneration &&
                    !checkpoint.supportsVersion &&
                    nowMs - state.syncedAtMs < LEGACY_SYNC_THROTTLE_MS
                ) {
                    return
                }
            }

            val canIncrementalSync = checkpoint.supportsGeneration &&
                state != null &&
                state.version == checkpoint.version &&
                state.generation >= 0L &&
                checkpoint.generation >= state.generation
            val changedSinceGeneration = if (canIncrementalSync) state?.generation else null

            synchronizeMediaStore(checkpoint, changedSinceGeneration)
        }
    }

    private suspend fun synchronizeMediaStore(
        checkpoint: MediaStoreCheckpoint,
        changedSinceGeneration: Long?
    ) {
        val scanId = System.currentTimeMillis()
        val imageSpec = imageSpec()
        val videoSpec = videoSpec()
        val imageEntities = queryMediaEntities(imageSpec, changedSinceGeneration, scanId)
        val videoEntities = queryMediaEntities(videoSpec, changedSinceGeneration, scanId)
        val imageIds = if (changedSinceGeneration != null) queryCurrentMediaIds(imageSpec) else null
        val videoIds = if (changedSinceGeneration != null) queryCurrentMediaIds(videoSpec) else null

        if (changedSinceGeneration != null && (imageIds == null || videoIds == null)) {
            synchronizeMediaStore(checkpoint, changedSinceGeneration = null)
            return
        }

        database.withTransaction {
            if (changedSinceGeneration == null) {
                dao.clearMedia()
            }

            dao.upsertMedia(imageEntities)
            dao.upsertMedia(videoEntities)

            if (changedSinceGeneration != null) {
                markCurrentIds(MEDIA_INDEX_TYPE_IMAGE, imageIds.orEmpty(), scanId)
                markCurrentIds(MEDIA_INDEX_TYPE_VIDEO, videoIds.orEmpty(), scanId)
                dao.deleteMediaNotSeen(MEDIA_INDEX_TYPE_IMAGE, scanId)
                dao.deleteMediaNotSeen(MEDIA_INDEX_TYPE_VIDEO, scanId)
            }

            rebuildFolderIndexLocked(scanId)
            dao.upsertSyncState(
                MediaSyncStateEntity(
                    volumeName = checkpoint.volumeName,
                    version = checkpoint.version,
                    generation = checkpoint.generation,
                    syncedAtMs = scanId
                )
            )
        }
    }

    private suspend fun markCurrentIds(mediaType: String, ids: List<Long>, scanId: Long) {
        ids.chunked(SQLITE_BIND_PARAMETER_LIMIT).forEach { chunk ->
            if (chunk.isNotEmpty()) {
                dao.markMediaSeen(mediaType, chunk, scanId)
            }
        }
    }

    private suspend fun rebuildFolderIndexLocked(nowMs: Long) {
        val folders = dao.getFolderAggregates().map { it.toFolderIndexEntity(nowMs) }
        dao.clearFolderIndex()
        if (folders.isNotEmpty()) {
            dao.upsertFolders(folders)
        }
    }

    private fun queryMediaEntities(
        spec: MediaQuerySpec,
        changedSinceGeneration: Long?,
        scanId: Long
    ): List<MediaIndexEntity> {
        val generationSelection = if (
            changedSinceGeneration != null &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
        ) {
            "${MediaStore.MediaColumns.GENERATION_ADDED} > ? OR ${MediaStore.MediaColumns.GENERATION_MODIFIED} > ?"
        } else {
            null
        }
        val selectionArgs = changedSinceGeneration?.let {
            arrayOf(it.toString(), it.toString())
        }

        val cursor = appContext.contentResolver.query(
            spec.collectionUri,
            spec.projection(),
            generationSelection,
            selectionArgs,
            null
        ) ?: return emptyList()

        return cursor.use { readMediaEntities(it, spec, scanId) }
    }

    private fun readMediaEntities(
        cursor: Cursor,
        spec: MediaQuerySpec,
        scanId: Long
    ): List<MediaIndexEntity> {
        val entities = ArrayList<MediaIndexEntity>()
        val idColumn = cursor.getColumnIndex(spec.idColumn)
        val nameColumn = cursor.getColumnIndex(spec.nameColumn)
        val relativePathColumn = cursor.getColumnIndex(spec.relativePathColumn)
        val dataColumn = cursor.getColumnIndex(spec.dataColumn)
        val dateColumn = cursor.getColumnIndex(spec.dateModifiedColumn)
        val mimeColumn = cursor.getColumnIndex(spec.mimeTypeColumn)
        val sizeColumn = cursor.getColumnIndex(spec.sizeColumn)
        val widthColumn = cursor.getColumnIndex(spec.widthColumn)
        val heightColumn = cursor.getColumnIndex(spec.heightColumn)
        val durationColumn = spec.durationColumn?.let { cursor.getColumnIndex(it) } ?: -1
        val generationAddedColumn = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            cursor.getColumnIndex(MediaStore.MediaColumns.GENERATION_ADDED)
        } else {
            -1
        }
        val generationModifiedColumn = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            cursor.getColumnIndex(MediaStore.MediaColumns.GENERATION_MODIFIED)
        } else {
            -1
        }

        while (cursor.moveToNext()) {
            if (idColumn < 0) continue
            val mediaStoreId = cursor.getLong(idColumn)
            if (mediaStoreId <= 0L) continue

            val fileName = cursor.getStringOrNull(nameColumn)
            if (isTrashedName(fileName)) continue

            val path = resolveCursorMediaPath(
                cursor = cursor,
                dataColumn = dataColumn,
                relativePathColumn = relativePathColumn,
                nameColumn = nameColumn,
                idColumn = idColumn,
                fallbackContentUri = spec.collectionUri
            ) ?: continue
            val folderPath = deriveFolderPathFromCursor(cursor, path, relativePathColumn) ?: continue

            if (!path.startsWith("content://")) {
                val file = File(path)
                if (isTrashedFile(file)) continue
            }

            entities.add(
                MediaIndexEntity(
                    mediaType = spec.mediaType,
                    mediaStoreId = mediaStoreId,
                    path = path,
                    folderPath = folderPath,
                    name = fileName ?: File(path).name,
                    dateModifiedMs = cursor.getLongOrDefault(dateColumn, 0L) * 1000L,
                    sizeBytes = cursor.getLongOrDefault(sizeColumn, 0L).coerceAtLeast(0L),
                    mimeType = cursor.getStringOrNull(mimeColumn) ?: spec.defaultMimeType,
                    durationMs = if (durationColumn >= 0) {
                        cursor.getLong(durationColumn).coerceAtLeast(0L)
                    } else {
                        0L
                    },
                    width = cursor.getIntOrDefault(widthColumn, 0).coerceAtLeast(0),
                    height = cursor.getIntOrDefault(heightColumn, 0).coerceAtLeast(0),
                    generationAdded = cursor.getLongOrDefault(generationAddedColumn, 0L),
                    generationModified = cursor.getLongOrDefault(generationModifiedColumn, 0L),
                    lastSeenScanId = scanId,
                    updatedAtMs = scanId
                )
            )
        }

        return entities
    }

    private fun queryCurrentMediaIds(spec: MediaQuerySpec): List<Long>? {
        val cursor = appContext.contentResolver.query(
            spec.collectionUri,
            arrayOf(spec.idColumn, spec.nameColumn),
            null,
            null,
            null
        ) ?: return null

        return cursor.use {
            val ids = ArrayList<Long>()
            val idColumn = it.getColumnIndex(spec.idColumn)
            val nameColumn = it.getColumnIndex(spec.nameColumn)
            while (it.moveToNext()) {
                if (idColumn < 0) continue
                if (isTrashedName(it.getStringOrNull(nameColumn))) continue
                val id = it.getLong(idColumn)
                if (id > 0L) ids.add(id)
            }
            ids
        }
    }

    private fun readMediaStoreCheckpoint(): MediaStoreCheckpoint {
        val volumeName = externalVolumeName()
        val version = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            runCatching { MediaStore.getVersion(appContext, volumeName) }.getOrNull()
        } else {
            null
        }
        val generation = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            runCatching { MediaStore.getGeneration(appContext, volumeName) }.getOrDefault(-1L)
        } else {
            -1L
        }

        return MediaStoreCheckpoint(
            volumeName = volumeName,
            version = version,
            generation = generation,
            supportsVersion = version != null,
            supportsGeneration = generation >= 0L
        )
    }

    private fun externalVolumeName(): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.VOLUME_EXTERNAL
        } else {
            LEGACY_EXTERNAL_VOLUME
        }
    }

    private fun imageSpec(): MediaQuerySpec {
        return MediaQuerySpec(
            mediaType = MEDIA_INDEX_TYPE_IMAGE,
            collectionUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            idColumn = MediaStore.Images.Media._ID,
            nameColumn = MediaStore.Images.Media.DISPLAY_NAME,
            relativePathColumn = MediaStore.Images.Media.RELATIVE_PATH,
            dataColumn = MediaStore.Images.Media.DATA,
            dateModifiedColumn = MediaStore.Images.Media.DATE_MODIFIED,
            mimeTypeColumn = MediaStore.Images.Media.MIME_TYPE,
            sizeColumn = MediaStore.Images.Media.SIZE,
            widthColumn = MediaStore.Images.Media.WIDTH,
            heightColumn = MediaStore.Images.Media.HEIGHT,
            durationColumn = null,
            defaultMimeType = "image/*"
        )
    }

    private fun videoSpec(): MediaQuerySpec {
        return MediaQuerySpec(
            mediaType = MEDIA_INDEX_TYPE_VIDEO,
            collectionUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            idColumn = MediaStore.Video.Media._ID,
            nameColumn = MediaStore.Video.Media.DISPLAY_NAME,
            relativePathColumn = MediaStore.Video.Media.RELATIVE_PATH,
            dataColumn = MediaStore.Video.Media.DATA,
            dateModifiedColumn = MediaStore.Video.Media.DATE_MODIFIED,
            mimeTypeColumn = MediaStore.Video.Media.MIME_TYPE,
            sizeColumn = MediaStore.Video.Media.SIZE,
            widthColumn = MediaStore.Video.Media.WIDTH,
            heightColumn = MediaStore.Video.Media.HEIGHT,
            durationColumn = MediaStore.Video.Media.DURATION,
            defaultMimeType = "video/*"
        )
    }

    private fun resolveCursorMediaPath(
        cursor: Cursor,
        dataColumn: Int,
        relativePathColumn: Int,
        nameColumn: Int,
        idColumn: Int,
        fallbackContentUri: Uri
    ): String? {
        if (dataColumn >= 0) {
            val dataPath = cursor.getStringOrNull(dataColumn)
            if (!dataPath.isNullOrBlank()) return dataPath
        }

        val relativePath = cursor.getStringOrNull(relativePathColumn)
        val displayName = cursor.getStringOrNull(nameColumn)
        resolveAbsolutePathFromRelative(relativePath, displayName)?.let { return it }

        if (idColumn >= 0) {
            val id = cursor.getLong(idColumn)
            if (id > 0L) {
                return ContentUris.withAppendedId(fallbackContentUri, id).toString()
            }
        }

        return null
    }

    private fun deriveFolderPathFromCursor(
        cursor: Cursor,
        mediaPath: String,
        relativePathColumn: Int
    ): String? {
        if (!mediaPath.startsWith("content://")) {
            return File(mediaPath).parent
        }

        val relativePath = cursor.getStringOrNull(relativePathColumn) ?: return null
        val root = primaryExternalRootPath ?: return null
        return File(root, relativePath).absolutePath.trimEnd(File.separatorChar)
    }

    private fun resolveAbsolutePathFromRelative(relativePath: String?, displayName: String?): String? {
        if (relativePath.isNullOrBlank() || displayName.isNullOrBlank()) return null
        val root = primaryExternalRootPath ?: return null
        return File(File(root, relativePath), displayName).absolutePath
    }

    private fun folderPathForMediaItem(item: MediaItem): String? {
        if (item.path.startsWith("content://")) return null
        return File(item.path).parent
    }

    private fun isTrashedName(fileName: String?): Boolean {
        return fileName?.startsWith(TrashBinStore.TRASH_FILE_PREFIX) == true
    }

    private fun isTrashedFile(file: File): Boolean {
        return file.name.startsWith(TrashBinStore.TRASH_FILE_PREFIX)
    }

    suspend fun loadWindow(folderPath: String, offset: Int, limit: Int): List<MediaItem> = withContext(Dispatchers.IO) {
        dao.loadWindow(folderPath, offset, limit).map { it.toMediaItem() }
    }

    suspend fun findIndexOfPath(folderPath: String, targetPath: String): Int = withContext(Dispatchers.IO) {
        dao.findIndexOfPath(folderPath, targetPath)
    }

    private fun Cursor.getStringOrNull(column: Int): String? {
        if (column < 0 || isNull(column)) return null
        return getString(column)
    }

    private fun Cursor.getLongOrDefault(column: Int, defaultValue: Long): Long {
        if (column < 0 || isNull(column)) return defaultValue
        return getLong(column)
    }

    private fun Cursor.getIntOrDefault(column: Int, defaultValue: Int): Int {
        if (column < 0 || isNull(column)) return defaultValue
        return getInt(column)
    }

    private data class MediaStoreCheckpoint(
        val volumeName: String,
        val version: String?,
        val generation: Long,
        val supportsVersion: Boolean,
        val supportsGeneration: Boolean
    )

    private data class MediaQuerySpec(
        val mediaType: String,
        val collectionUri: Uri,
        val idColumn: String,
        val nameColumn: String,
        val relativePathColumn: String,
        val dataColumn: String,
        val dateModifiedColumn: String,
        val mimeTypeColumn: String,
        val sizeColumn: String,
        val widthColumn: String,
        val heightColumn: String,
        val durationColumn: String?,
        val defaultMimeType: String
    ) {
        fun projection(): Array<String> {
            val columns = mutableListOf(
                idColumn,
                nameColumn,
                relativePathColumn,
                dataColumn,
                dateModifiedColumn,
                mimeTypeColumn,
                sizeColumn,
                widthColumn,
                heightColumn
            )
            durationColumn?.let(columns::add)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                columns.add(MediaStore.MediaColumns.GENERATION_ADDED)
                columns.add(MediaStore.MediaColumns.GENERATION_MODIFIED)
            }
            return columns.distinct().toTypedArray()
        }
    }

    companion object {
        private const val LEGACY_EXTERNAL_VOLUME = "external"
        private const val LEGACY_SYNC_THROTTLE_MS = 60_000L
        private const val SQLITE_BIND_PARAMETER_LIMIT = 900
        private val syncMutex = Mutex()
    }
}
