package org.iurl.litegallery

object ActionBarPreferences {
    const val PREFS_NAME = "action_bar_prefs"
    const val KEY_ORDER = "order"
    const val KEY_VISIBLE = "visible"

    // Keep first-install defaults aligned with Customize Action Bar settings UI.
    val DEFAULT_ACTION_ORDER = listOf(
        "delete",
        "rename",
        "rotate_screen",
        "properties",
        "reload_video"
    )
}
