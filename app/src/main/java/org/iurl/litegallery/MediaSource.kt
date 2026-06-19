package org.iurl.litegallery

import android.content.Context
import android.content.Intent
import kotlinx.coroutines.flow.Flow

/**
 * A pluggable provider for media that does not live in the local device storage /
 * MediaStore — for example an SMB network share.
 *
 * The core app compiles only against this seam. Concrete implementations live in
 * optional build flavors (e.g. `plus`) and register themselves with
 * [MediaSourceRegistry] at startup via `MediaSourceBootstrap`. The `core` flavor
 * registers none, so it ships with no network capability whatsoever.
 */
interface MediaSource {

    /** True if [path] belongs to this source (e.g. it starts with `smb://`). */
    fun handles(path: String): Boolean

    /** A virtual home-screen folder representing this source, or null to show none. */
    fun homeEntry(context: Context): MediaFolder?

    /** Intent that opens this source's browse UI. */
    fun browseIntent(context: Context): Intent

    /** Drawable resource used as the folder icon for this source on the home screen. */
    val folderIconRes: Int

    /** Whether a thumbnail can be loaded (via Glide) for the given media [path]. */
    fun canLoadThumbnail(path: String): Boolean

    /** Scan a folder owned by this source into full media items. */
    suspend fun scanFolder(context: Context, folderPath: String): List<MediaItem>

    /** Stream-scan a folder owned by this source, chunk by chunk. */
    fun scanFolderStreamed(context: Context, folderPath: String): Flow<LoadEvent>

    /** Build a Media3 media source for streaming the video [path] owned by this source. */
    fun createExoMediaSource(
        context: Context,
        path: String
    ): androidx.media3.exoplayer.source.MediaSource

    /** Rename a file owned by this source. */
    suspend fun rename(
        context: Context,
        path: String,
        newBaseName: String,
        extension: String
    ): RenameOutcome

    /** Release any held resources (connections, caches) on app shutdown. */
    fun shutdown()
}

/** Result of a [MediaSource.rename] attempt. */
sealed class RenameOutcome {
    data class Success(val newPath: String) : RenameOutcome()
    object AlreadyExists : RenameOutcome()
    object Failed : RenameOutcome()
}
