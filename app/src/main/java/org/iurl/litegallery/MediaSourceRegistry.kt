package org.iurl.litegallery

/**
 * Registry of optional [MediaSource]s available in the current build flavor.
 *
 * The `core` flavor registers none (its `MediaSourceBootstrap` is a no-op), so the
 * app behaves as a purely local gallery with no network capability. The `plus`
 * flavor registers the SMB source. Core code branches on this registry instead of
 * referencing any concrete source type, which keeps SMB entirely out of `core`.
 */
object MediaSourceRegistry {

    private val sources = mutableListOf<MediaSource>()

    /** Register a source. Idempotent per source type. */
    fun register(source: MediaSource) {
        if (sources.none { it::class == source::class }) {
            sources.add(source)
        }
    }

    fun all(): List<MediaSource> = sources

    /** The source that owns [path], or null if it is a local path. */
    fun forPath(path: String): MediaSource? = sources.firstOrNull { it.handles(path) }

    /** True if [path] is owned by a registered (non-local) source. */
    fun isManaged(path: String): Boolean = forPath(path) != null
}
