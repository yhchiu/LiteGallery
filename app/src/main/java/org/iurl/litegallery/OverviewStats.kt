package org.iurl.litegallery

/**
 * Aggregate statistics shown on the Home hero card.
 * Computed in [MainActivity.loadMediaFolders] from the scanned folder list,
 * excluding synthetic entries like the SMB virtual folder.
 */
data class OverviewStats(
    val totalItems: Int,
    val totalPhotos: Int,
    val totalVideos: Int,
    val totalFolders: Int,
    val totalSizeBytes: Long
) {
    companion object {
        val EMPTY = OverviewStats(0, 0, 0, 0, 0L)
    }
}
