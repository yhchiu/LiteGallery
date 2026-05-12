package org.iurl.litegallery

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface MediaIndexDao {

    @Query("SELECT COUNT(*) FROM media_items")
    suspend fun getMediaCount(): Int

    @Query("SELECT * FROM media_sync_state WHERE volumeName = :volumeName LIMIT 1")
    suspend fun getSyncState(volumeName: String): MediaSyncStateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSyncState(state: MediaSyncStateEntity)

    @Query("SELECT * FROM folder_index ORDER BY name COLLATE NOCASE ASC")
    suspend fun getFolders(): List<FolderIndexEntity>

    @Query(
        """
        SELECT * FROM media_items
        WHERE folderPath = :folderPath
        ORDER BY dateModifiedMs DESC, name COLLATE NOCASE ASC
        """
    )
    suspend fun getMediaInFolder(folderPath: String): List<MediaIndexEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMedia(items: List<MediaIndexEntity>)

    @Query("UPDATE media_items SET lastSeenScanId = :scanId WHERE mediaType = :mediaType AND mediaStoreId IN (:ids)")
    suspend fun markMediaSeen(mediaType: String, ids: List<Long>, scanId: Long)

    @Query("DELETE FROM media_items WHERE mediaType = :mediaType AND lastSeenScanId != :scanId")
    suspend fun deleteMediaNotSeen(mediaType: String, scanId: Long)

    @Query("DELETE FROM media_items")
    suspend fun clearMedia()

    @Query("DELETE FROM folder_index")
    suspend fun clearFolderIndex()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFolders(folders: List<FolderIndexEntity>)

    @Query(
        """
        SELECT
            m.folderPath AS path,
            COUNT(*) AS itemCount,
            SUM(CASE WHEN m.mediaType = 'image' THEN 1 ELSE 0 END) AS imageCount,
            SUM(CASE WHEN m.mediaType = 'video' THEN 1 ELSE 0 END) AS videoCount,
            SUM(CASE WHEN m.sizeBytes > 0 THEN m.sizeBytes ELSE 0 END) AS totalSizeBytes,
            MAX(m.dateModifiedMs) AS latestDateModifiedMs,
            (
                SELECT latest.path
                FROM media_items AS latest
                WHERE latest.folderPath = m.folderPath
                ORDER BY latest.dateModifiedMs DESC, latest.name COLLATE NOCASE ASC
                LIMIT 1
            ) AS thumbnail
        FROM media_items AS m
        GROUP BY m.folderPath
        """
    )
    suspend fun getFolderAggregates(): List<FolderAggregateRow>

    @Query("DELETE FROM media_items WHERE path = :path")
    suspend fun deleteMediaByPath(path: String): Int

    @Query(
        """
        UPDATE media_items SET
            name = :name,
            path = :newPath,
            folderPath = :folderPath,
            dateModifiedMs = :dateModifiedMs,
            sizeBytes = :sizeBytes,
            mimeType = :mimeType,
            durationMs = :durationMs,
            width = :width,
            height = :height,
            updatedAtMs = :updatedAtMs
        WHERE path = :oldPath
        """
    )
    suspend fun updateMediaByPath(
        oldPath: String,
        newPath: String,
        folderPath: String,
        name: String,
        dateModifiedMs: Long,
        sizeBytes: Long,
        mimeType: String,
        durationMs: Long,
        width: Int,
        height: Int,
        updatedAtMs: Long
    ): Int

    @Query(
        """
        UPDATE media_items SET
            sizeBytes = CASE WHEN :sizeBytes > 0 THEN :sizeBytes ELSE sizeBytes END,
            width = CASE WHEN :width > 0 THEN :width ELSE width END,
            height = CASE WHEN :height > 0 THEN :height ELSE height END,
            durationMs = CASE WHEN :durationMs > 0 THEN :durationMs ELSE durationMs END,
            updatedAtMs = :updatedAtMs
        WHERE path = :path
        """
    )
    suspend fun updateMetadataByPath(
        path: String,
        sizeBytes: Long,
        width: Int,
        height: Int,
        durationMs: Long,
        updatedAtMs: Long
    ): Int

    @Query("SELECT * FROM media_items WHERE mediaStoreId IN (:ids)")
    suspend fun findByIds(ids: List<Long>): List<MediaIndexEntity>

    @Query("SELECT * FROM media_items WHERE mediaStoreId = :id LIMIT 1")
    suspend fun findById(id: Long): MediaIndexEntity?

    @Query("""
        SELECT * FROM media_items 
        WHERE folderPath = :folderPath 
        ORDER BY dateModifiedMs DESC, name COLLATE NOCASE ASC 
        LIMIT :limit OFFSET :offset
    """)
    suspend fun loadWindow(folderPath: String, offset: Int, limit: Int): List<MediaIndexEntity>

    @Query("""
        SELECT COUNT(*) FROM media_items 
        WHERE folderPath = :folderPath AND (
            dateModifiedMs > (SELECT dateModifiedMs FROM media_items WHERE path = :targetPath)
            OR (dateModifiedMs = (SELECT dateModifiedMs FROM media_items WHERE path = :targetPath) 
                AND name COLLATE NOCASE < (SELECT name FROM media_items WHERE path = :targetPath))
        )
    """)
    suspend fun findIndexOfPath(folderPath: String, targetPath: String): Int
}
