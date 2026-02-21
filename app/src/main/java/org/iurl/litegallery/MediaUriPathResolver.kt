package org.iurl.litegallery

import android.database.Cursor
import android.net.Uri
import android.provider.MediaStore

object MediaUriPathResolver {

    fun resolveRealPath(
        uri: Uri,
        queryDataCursor: () -> Cursor?
    ): String? {
        if (uri.scheme == "file") {
            return uri.path
        }
        if (uri.scheme != "content") {
            return null
        }

        return try {
            queryDataCursor()?.use { cursor ->
                val columnIndex = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)
                if (columnIndex >= 0 && cursor.moveToFirst()) {
                    cursor.getString(columnIndex)?.takeIf { it.isNotBlank() }
                } else {
                    null
                }
            }
        } catch (_: Exception) {
            null
        }
    }
}
