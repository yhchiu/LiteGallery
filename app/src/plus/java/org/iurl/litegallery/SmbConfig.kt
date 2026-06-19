package org.iurl.litegallery

/**
 * Data model for an SMB server connection configuration.
 */
data class SmbConfig(
    val id: String,           // Unique ID (UUID)
    val displayName: String,  // User-friendly display name
    val host: String,         // IP or hostname
    val share: String = "",   // The default share to connect to
    val path: String = "",    // The path within the share
    val port: Int = 445,      // SMB port (default 445)
    val username: String = "",
    val password: String = "",
    val isGuest: Boolean = false
) {
    val isAuthenticated: Boolean
        get() = !isGuest && username.isNotBlank()
}
