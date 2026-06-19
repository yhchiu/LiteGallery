package org.iurl.litegallery

import android.app.Application

/**
 * `core` flavor: registers no media sources.
 *
 * This is what makes the core build provably network-free — it ships no SMB code,
 * declares no `INTERNET` permission, and registers nothing here.
 */
object MediaSourceBootstrap {
    fun init(app: Application) {
        // Intentionally empty — the core flavor has zero network capability.
    }
}
