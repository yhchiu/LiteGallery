package org.iurl.litegallery

import android.content.Context
import android.content.Intent
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/**
 * [MediaSource] implementation for SMB network shares.
 *
 * Only present in the `plus` flavor. It wraps the existing SMB classes
 * ([SmbClient], [SmbMediaScanner], [SmbDataSource], [SmbBrowseActivity], …) and
 * is registered with [MediaSourceRegistry] at startup by `MediaSourceBootstrap`.
 */
object SmbMediaSource : MediaSource {

    override fun handles(path: String): Boolean = SmbPath.isSmb(path)

    override fun homeEntry(context: Context): MediaFolder {
        val serverCount = SmbConfigStore.getAllServers(context).size
        return MediaFolder(
            name = context.getString(R.string.smb_browse_title),
            path = "smb://",
            itemCount = serverCount,
            thumbnail = null
        )
    }

    override fun browseIntent(context: Context): Intent =
        Intent(context, SmbBrowseActivity::class.java)

    override val folderIconRes: Int = R.drawable.ic_network

    override fun canLoadThumbnail(path: String): Boolean = SmbModelLoader.isSmbImage(path)

    override suspend fun scanFolder(context: Context, folderPath: String): List<MediaItem> =
        SmbMediaScanner(context).scanSmbMediaInFolder(folderPath)

    override fun scanFolderStreamed(context: Context, folderPath: String): Flow<LoadEvent> =
        SmbMediaScanner(context).scanSmbMediaInFolderStreamed(folderPath)

    override fun createExoMediaSource(
        context: Context,
        path: String
    ): androidx.media3.exoplayer.source.MediaSource {
        val dataSourceFactory = SmbDataSourceFactory(context)
        return ProgressiveMediaSource.Factory(dataSourceFactory)
            .createMediaSource(
                androidx.media3.common.MediaItem.fromUri(android.net.Uri.parse(path))
            )
    }

    override suspend fun rename(
        context: Context,
        path: String,
        newBaseName: String,
        extension: String
    ): RenameOutcome = withContext(Dispatchers.IO) {
        val smbPath = SmbPath.parse(path) ?: return@withContext RenameOutcome.Failed
        val newFileName = if (extension.isNotEmpty()) "$newBaseName.$extension" else newBaseName
        val oldFilePath = smbPath.path
        val newFilePath =
            if (smbPath.parentPath.isBlank()) newFileName else "${smbPath.parentPath}/$newFileName"
        val newSmbFullPath = "smb://${smbPath.host}/${smbPath.share}/$newFilePath"

        when {
            SmbClient.fileExists(context, smbPath.host, smbPath.share, newFilePath) ->
                RenameOutcome.AlreadyExists
            SmbClient.rename(context, smbPath.host, smbPath.share, oldFilePath, newFilePath) ->
                RenameOutcome.Success(newSmbFullPath)
            else -> RenameOutcome.Failed
        }
    }

    override fun shutdown() {
        SmbClient.disconnectAll()
    }
}
