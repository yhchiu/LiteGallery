package org.iurl.litegallery

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract

object ExternalFolderGrantStore {

    private const val PREFS_NAME = "external_folder_grants"
    private const val KEY_PREFIX = "grant::"

    data class GrantMapping(
        val authority: String,
        val parentDocumentId: String,
        val treeUri: Uri?
    ) {
        val lookupKey: String
            get() = "$authority|$parentDocumentId"
    }

    fun rememberTreeUriForContentUri(context: Context, contentUri: Uri, treeUri: Uri) {
        val lookupKey = buildLookupKey(context, contentUri) ?: return
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(buildPreferenceKey(lookupKey), treeUri.toString())
            .apply()
    }

    fun findTreeUriForContentUri(context: Context, contentUri: Uri): Uri? {
        val lookupKey = buildLookupKey(context, contentUri) ?: return null
        val value = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(buildPreferenceKey(lookupKey), null)
            ?: return null
        return try {
            Uri.parse(value)
        } catch (_: Exception) {
            null
        }
    }

    fun getAllMappings(context: Context): List<GrantMapping> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.all.asSequence()
            .mapNotNull { (key, value) ->
                if (!key.startsWith(KEY_PREFIX)) return@mapNotNull null
                val lookupKey = key.removePrefix(KEY_PREFIX)
                val (authority, parentDocumentId) = parseLookupKey(lookupKey) ?: return@mapNotNull null
                val treeUri = parseUriOrNull(value as? String)
                GrantMapping(
                    authority = authority,
                    parentDocumentId = parentDocumentId,
                    treeUri = treeUri
                )
            }
            .sortedWith(compareBy({ it.authority }, { it.parentDocumentId }))
            .toList()
    }

    fun clearAllMappings(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val keysToRemove = prefs.all.keys.filter { it.startsWith(KEY_PREFIX) }
        if (keysToRemove.isEmpty()) return 0
        val editor = prefs.edit()
        keysToRemove.forEach { editor.remove(it) }
        editor.apply()
        return keysToRemove.size
    }

    private fun buildPreferenceKey(lookupKey: String): String {
        return "$KEY_PREFIX$lookupKey"
    }

    private fun parseLookupKey(lookupKey: String): Pair<String, String>? {
        val delimiterIndex = lookupKey.indexOf('|')
        if (delimiterIndex <= 0 || delimiterIndex >= lookupKey.lastIndex) {
            return null
        }
        val authority = lookupKey.substring(0, delimiterIndex)
        val parentDocumentId = lookupKey.substring(delimiterIndex + 1)
        if (authority.isBlank() || parentDocumentId.isBlank()) {
            return null
        }
        return authority to parentDocumentId
    }

    private fun parseUriOrNull(value: String?): Uri? {
        if (value.isNullOrBlank()) return null
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
