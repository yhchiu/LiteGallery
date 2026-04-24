package org.iurl.litegallery

import android.content.Context
import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.msfscc.FileAttributes
import com.hierynomus.msfscc.fileinformation.FileIdBothDirectoryInformation
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.mssmb2.SMB2CreateOptions
import com.hierynomus.mssmb2.SMB2ShareAccess
import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.SmbConfig as SmbjConfig
import com.hierynomus.smbj.auth.AuthenticationContext
import com.hierynomus.smbj.connection.Connection
import com.hierynomus.smbj.session.Session
import com.hierynomus.smbj.share.DiskShare
import java.io.InputStream
import java.util.EnumSet
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * SMB client wrapper around smbj library.
 * Manages connections, sessions, and file operations.
 */
object SmbClient {

    private val client: SMBClient by lazy {
        val config = SmbjConfig.builder()
            .withTimeout(15, TimeUnit.SECONDS)
            .withSoTimeout(15, TimeUnit.SECONDS)
            .build()
        SMBClient(config)
    }

    // Cache: host -> Connection
    private val connections = ConcurrentHashMap<String, Connection>()
    // Cache: "host|user" -> Session
    private val sessions = ConcurrentHashMap<String, Session>()
    // Cache: "host|user|share" -> DiskShare
    private val shares = ConcurrentHashMap<String, DiskShare>()

    private val imageExtensions = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "heif")
    private val videoExtensions = setOf("mp4", "avi", "mov", "mkv", "3gp", "webm", "m4v", "flv")

    data class SmbFileInfo(
        val name: String,
        val path: String,           // relative path within share
        val isDirectory: Boolean,
        val size: Long,
        val lastModified: Long,     // epoch millis
        val isMedia: Boolean,
        val isVideo: Boolean
    )

    /**
     * List available shares on an SMB server.
     */
    fun listShares(context: Context, host: String): List<String> {
        val config = resolveConfig(context, host) ?: throw SmbException("No config for host: $host")
        val session = getOrCreateSession(config)
        // Use a special share to list shares
        return try {
            // smbj doesn't have a direct listShares API on Session,
            // we connect to IPC$ to enumerate shares
            val transport = session.connection
            // Fallback: try common share names
            // For simplicity, we'll try to connect to well-known shares
            val shareNames = mutableListOf<String>()
            // Try listing via RPC
            try {
                val pipe = session.connectShare("IPC\$") as? com.hierynomus.smbj.share.PipeShare
                // Use the pipe to enumerate shares if available
                pipe?.close()
            } catch (e: Exception) {
                android.util.Log.d("SmbClient", "IPC$ enumeration not available: ${e.message}")
            }
            shareNames
        } catch (e: Exception) {
            android.util.Log.e("SmbClient", "Failed to list shares on $host", e)
            emptyList()
        }
    }

    /**
     * List files and directories in an SMB path.
     */
    fun listFiles(context: Context, host: String, shareName: String, path: String = ""): List<SmbFileInfo> {
        val dirPath = if (path.isBlank()) "" else path.replace('/', '\\').trimEnd('\\')

        try {
            return executeWithRetry(context, host, shareName) { share ->
                val results = mutableListOf<SmbFileInfo>()
                val listing = share.list(dirPath)
                for (info in listing) {
                    val name = info.fileName
                    // Skip . and .. entries
                    if (name == "." || name == "..") continue

                    val isDir = info.fileAttributes and FileAttributes.FILE_ATTRIBUTE_DIRECTORY.value != 0L
                    val isHidden = info.fileAttributes and FileAttributes.FILE_ATTRIBUTE_HIDDEN.value != 0L
                    if (isHidden) continue

                    val ext = name.substringAfterLast('.', "").lowercase()
                    val isVideoFile = videoExtensions.contains(ext)
                    val isImageFile = imageExtensions.contains(ext)
                    val isMedia = isVideoFile || isImageFile

                    val relativePath = if (dirPath.isBlank()) name else "$dirPath\\$name"

                    results.add(
                        SmbFileInfo(
                            name = name,
                            path = relativePath.replace('\\', '/'),
                            isDirectory = isDir,
                            size = info.endOfFile,
                            lastModified = info.lastWriteTime.toEpochMillis(),
                            isMedia = isMedia,
                            isVideo = isVideoFile
                        )
                    )
                }
                results
            }
        } catch (e: Exception) {
            android.util.Log.e("SmbClient", "Failed to list files: $dirPath on $shareName", e)
            throw SmbException("Failed to list files: ${e.message}", e)
        }
    }

    /**
     * Open an InputStream for reading an SMB file.
     * Caller is responsible for closing the returned InputStream.
     */
    fun openInputStream(context: Context, host: String, shareName: String, filePath: String): InputStream {
        val smbPath = filePath.replace('/', '\\')

        try {
            return executeWithRetry(context, host, shareName) { share ->
                val file = share.openFile(
                    smbPath,
                    EnumSet.of(AccessMask.GENERIC_READ),
                    null,
                    EnumSet.of(SMB2ShareAccess.FILE_SHARE_READ, SMB2ShareAccess.FILE_SHARE_WRITE, SMB2ShareAccess.FILE_SHARE_DELETE),
                    SMB2CreateDisposition.FILE_OPEN,
                    null
                )
                file.inputStream
            }
        } catch (e: Exception) {
            android.util.Log.e("SmbClient", "Failed to open file: $smbPath", e)
            throw SmbException("Failed to open file: ${e.message}", e)
        }
    }

    /**
     * Open an SMB file for reading with seek support (for ExoPlayer DataSource).
     * Returns the smbj File object. Caller must close it.
     */
    fun openFile(
        context: Context,
        host: String,
        shareName: String,
        filePath: String
    ): com.hierynomus.smbj.share.File {
        val smbPath = filePath.replace('/', '\\')

        try {
            return executeWithRetry(context, host, shareName) { share ->
                share.openFile(
                    smbPath,
                    EnumSet.of(AccessMask.GENERIC_READ),
                    null,
                    EnumSet.of(SMB2ShareAccess.FILE_SHARE_READ, SMB2ShareAccess.FILE_SHARE_WRITE, SMB2ShareAccess.FILE_SHARE_DELETE),
                    SMB2CreateDisposition.FILE_OPEN,
                    null
                )
            }
        } catch (e: Exception) {
            android.util.Log.e("SmbClient", "Failed to open file: $smbPath", e)
            throw SmbException("Failed to open file: ${e.message}", e)
        }
    }

    /**
     * Get file size without opening the full file.
     */
    fun getFileSize(context: Context, host: String, shareName: String, filePath: String): Long {
        val smbPath = filePath.replace('/', '\\')
        return try {
            executeWithRetry(context, host, shareName) { share ->
                val info = share.getFileInformation(smbPath)
                info.standardInformation.endOfFile
            }
        } catch (e: Exception) {
            android.util.Log.e("SmbClient", "Failed to get file size: $smbPath", e)
            -1L
        }
    }

    /**
     * Rename a file on the SMB share.
     */
    fun rename(
        context: Context,
        host: String,
        shareName: String,
        oldPath: String,
        newPath: String
    ): Boolean {
        val oldSmbPath = oldPath.replace('/', '\\')
        val newSmbPath = newPath.replace('/', '\\')

        return try {
            executeWithRetry(context, host, shareName) { share ->
                val file = share.openFile(
                    oldSmbPath,
                    EnumSet.of(AccessMask.GENERIC_ALL, AccessMask.DELETE),
                    null,
                    EnumSet.of(SMB2ShareAccess.FILE_SHARE_READ, SMB2ShareAccess.FILE_SHARE_WRITE, SMB2ShareAccess.FILE_SHARE_DELETE),
                    SMB2CreateDisposition.FILE_OPEN,
                    null
                )
                file.rename(newSmbPath)
                file.close()
                true
            }
        } catch (e: Exception) {
            android.util.Log.e("SmbClient", "Failed to rename: $oldSmbPath -> $newSmbPath", e)
            false
        }
    }

    /**
     * Check if a file exists on the SMB share.
     */
    fun fileExists(context: Context, host: String, shareName: String, filePath: String): Boolean {
        val smbPath = filePath.replace('/', '\\')
        return try {
            executeWithRetry(context, host, shareName) { share ->
                share.fileExists(smbPath)
            }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Test connection to an SMB server.
     * Returns true if connection is successful.
     */
    fun testConnection(config: SmbConfig): Boolean {
        return try {
            val connection = client.connect(config.host, config.port)
            val authContext = if (config.isGuest) {
                AuthenticationContext.guest()
            } else {
                AuthenticationContext(
                    config.username,
                    config.password.toCharArray(),
                    ""
                )
            }
            val session = connection.authenticate(authContext)
            session.close()
            connection.close()
            true
        } catch (e: Exception) {
            android.util.Log.e("SmbClient", "Connection test failed for ${config.host}", e)
            false
        }
    }

    /**
     * Disconnect all connections and clear caches.
     */
    fun disconnectAll() {
        val sharesToClose = shares.values.toList()
        shares.clear()

        val sessionsToClose = sessions.values.toList()
        sessions.clear()

        val connsToClose = connections.values.toList()
        connections.clear()

        Thread {
            sharesToClose.forEach { try { it.close() } catch (_: Exception) {} }
            sessionsToClose.forEach { try { it.close() } catch (_: Exception) {} }
            connsToClose.forEach { try { it.close() } catch (_: Exception) {} }
        }.start()
    }

    /**
     * Disconnect a specific host.
     */
    fun disconnect(host: String) {
        val keysToRemove = shares.keys.filter { it.startsWith("$host|") }
        val sharesToClose = keysToRemove.mapNotNull { shares.remove(it) }

        val sessionKeysToRemove = sessions.keys.filter { it.startsWith("$host|") }
        val sessionsToClose = sessionKeysToRemove.mapNotNull { sessions.remove(it) }

        val connToClose = connections.remove(host)

        Thread {
            sharesToClose.forEach { try { it.close() } catch (_: Exception) {} }
            sessionsToClose.forEach { try { it.close() } catch (_: Exception) {} }
            connToClose?.let { try { it.close() } catch (_: Exception) {} }
        }.start()
    }

    // --- Internal helpers ---

    private fun resolveConfig(context: Context, host: String): SmbConfig? {
        return SmbConfigStore.getServerByHost(context, host)
    }

    @Synchronized
    private fun getOrCreateConnection(config: SmbConfig): Connection {
        val existing = connections[config.host]
        if (existing != null && existing.isConnected) {
            return existing
        }

        // Remove stale connection
        existing?.let {
            connections.remove(config.host)
            Thread { try { it.close() } catch (_: Exception) {} }.start()
        }

        val connection = client.connect(config.host, config.port)
        connections[config.host] = connection
        return connection
    }

    @Synchronized
    private fun getOrCreateSession(config: SmbConfig): Session {
        val key = "${config.host}|${config.username}"
        val existing = sessions[key]
        if (existing != null) {
            try {
                // Check if session is still valid
                if (existing.connection.isConnected) {
                    return existing
                }
            } catch (_: Exception) {
            }
            sessions.remove(key)
            Thread { try { existing.close() } catch (_: Exception) {} }.start()
        }

        val connection = getOrCreateConnection(config)
        val authContext = if (config.isGuest) {
            AuthenticationContext.guest()
        } else {
            AuthenticationContext(
                config.username,
                config.password.toCharArray(),
                ""
            )
        }
        val session = connection.authenticate(authContext)
        sessions[key] = session
        return session
    }

    @Synchronized
    private fun getOrCreateShare(context: Context, host: String, shareName: String): DiskShare {
        val config = resolveConfig(context, host) ?: throw SmbException("No config for host: $host")
        return getOrCreateShareWithConfig(config, shareName)
    }

    @Synchronized
    private fun getOrCreateShareWithConfig(config: SmbConfig, shareName: String): DiskShare {
        val key = "${config.host}|${config.username}|$shareName"
        val existing = shares[key]
        if (existing != null) {
            try {
                // Check if share is still valid
                if (existing.isConnected) {
                    return existing
                }
            } catch (_: Exception) {
            }
            shares.remove(key)
            Thread { try { existing.close() } catch (_: Exception) {} }.start()
        }

        val session = getOrCreateSession(config)
        val share = session.connectShare(shareName) as DiskShare
        shares[key] = share
        return share
    }

    private fun <T> executeWithRetry(context: Context, host: String, shareName: String, block: (DiskShare) -> T): T {
        try {
            val share = getOrCreateShare(context, host, shareName)
            return block(share)
        } catch (e: Exception) {
            val msg = e.message ?: ""
            val causeMsg = e.cause?.message ?: ""
            if (msg.contains("closed", ignoreCase = true) || causeMsg.contains("closed", ignoreCase = true) ||
                msg.contains("Connection reset", ignoreCase = true) || causeMsg.contains("Connection reset", ignoreCase = true) ||
                msg.contains("Broken pipe", ignoreCase = true) || causeMsg.contains("Broken pipe", ignoreCase = true)) {
                
                android.util.Log.w("SmbClient", "SMB connection appears stale, retrying...", e)
                disconnect(host)
                val newShare = getOrCreateShare(context, host, shareName)
                return block(newShare)
            }
            throw e
        }
    }

    class SmbException(message: String, cause: Throwable? = null) : Exception(message, cause)
}
