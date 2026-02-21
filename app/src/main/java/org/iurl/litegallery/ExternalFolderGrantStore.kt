package org.iurl.litegallery

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract

object ExternalFolderGrantStore {

    private const val PREFS_NAME = "external_folder_grants"
    private const val KEY_PREFIX = "grant::"

    fun rememberTreeUriForContentUri(context: Context, contentUri: Uri, treeUri: Uri) {
        val lookupKey = buildLookupKey(context, contentUri) ?: return
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString("$KEY_PREFIX$lookupKey", treeUri.toString())
            .apply()
    }

    fun findTreeUriForContentUri(context: Context, contentUri: Uri): Uri? {
        val lookupKey = buildLookupKey(context, contentUri) ?: return null
        val value = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString("$KEY_PREFIX$lookupKey", null)
            ?: return null
        return try {
            Uri.parse(value)
        } catch (_: Exception) {
            null
        }
    }

    private fun buildLookupKey(context: Context, contentUri: Uri): String? {
        val authority = contentUri.authority ?: return null
        if (!DocumentsContract.isDocumentUri(context, contentUri)) return null

        val documentId = try {
            DocumentsContract.getDocumentId(contentUri)
        } catch (_: Exception) {
            return null
        }

        val parentDocumentId = documentId.substringBeforeLast('/', documentId)
        if (parentDocumentId.isBlank()) return null
        return "$authority|$parentDocumentId"
    }
}
