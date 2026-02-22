package org.iurl.litegallery

import android.os.Bundle
import android.view.MenuItem
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceFragmentCompat
import org.iurl.litegallery.databinding.ActivitySettingsBinding

class SettingsActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivitySettingsBinding
    
    override fun onCreate(savedInstanceState: Bundle?) {
        // Apply theme and color theme before setting content view
        ThemeHelper.applyTheme(this)
        ThemeHelper.applyColorTheme(this)
        
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Handle window insets for navigation bar
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
        }
        binding.root.setOnApplyWindowInsetsListener { view, insets ->
            val navigationInsets = insets.getInsets(android.view.WindowInsets.Type.navigationBars())
            val statusInsets = insets.getInsets(android.view.WindowInsets.Type.statusBars())
            // Apply padding to prevent navigation bar overlap
            view.setPadding(0, statusInsets.top, 0, navigationInsets.bottom)
            insets
        }

        setupToolbar()
        
        if (savedInstanceState == null) {
            supportFragmentManager
                .beginTransaction()
                .replace(R.id.settings_container, SettingsFragment())
                .commit()
        }
    }
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
    
    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
            title = getString(R.string.settings_title)
        }
    }
    
    class SettingsFragment : PreferenceFragmentCompat() {

        private lateinit var settingsHelper: SettingsExportImportHelper

        // Activity result launchers for file picking
        private val exportFileLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
            uri?.let { exportToUri(it) }
        }

        private val importFileLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let { importFromUri(it) }
        }

        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.preferences, rootKey)

            // Initialize settings helper
            settingsHelper = SettingsExportImportHelper(requireContext())

            // Handle language preference change
            findPreference<androidx.preference.ListPreference>("app_language")?.setOnPreferenceChangeListener { _, newValue ->
                val language = newValue as String
                LocaleHelper.setLanguage(requireContext(), language)

                // Update summary to show selected language
                val preference = findPreference<androidx.preference.ListPreference>("app_language")
                preference?.summary = LocaleHelper.getLanguageDisplayName(requireContext(), language)

                // Apply locale immediately - AppCompatDelegate handles recreation
                LocaleHelper.applyLocale(requireContext())

                true
            }

            // Handle theme preference change
            findPreference<androidx.preference.ListPreference>("theme_preference")?.setOnPreferenceChangeListener { _, newValue ->
                val theme = newValue as String
                ThemeHelper.setTheme(requireContext(), theme)

                // Update summary to show selected theme
                val preference = findPreference<androidx.preference.ListPreference>("theme_preference")
                preference?.summary = ThemeHelper.getThemeDisplayName(requireContext(), theme)

                // Recreate activity to apply theme immediately
                activity?.recreate()
                true
            }

            // Handle color theme preference change
            findPreference<ColorThemePreference>("color_theme_preference")?.setOnPreferenceChangeListener { _, newValue ->
                val colorTheme = newValue as String
                ThemeHelper.setColorTheme(requireContext(), colorTheme)
                
                // Update summary to show selected color theme
                val preference = findPreference<ColorThemePreference>("color_theme_preference")
                preference?.summary = ThemeHelper.getColorThemeDisplayName(requireContext(), colorTheme)
                
                // Recreate activity to apply color theme immediately
                activity?.recreate()
                true
            }

            // Handle Customize Action Bar click
            findPreference<androidx.preference.Preference>("customize_action_bar")?.setOnPreferenceClickListener {
                showCustomizeActionBarDialog()
                true
            }

            // Handle Export Settings click
            findPreference<androidx.preference.Preference>("export_settings")?.setOnPreferenceClickListener {
                exportSettings()
                true
            }

            // Handle Import Settings click
            findPreference<androidx.preference.Preference>("import_settings")?.setOnPreferenceClickListener {
                importSettings()
                true
            }
            
            // Handle rename history count preference change
            findPreference<androidx.preference.ListPreference>("rename_history_count")?.setOnPreferenceChangeListener { _, newValue ->
                val count = newValue as String
                val preference = findPreference<androidx.preference.ListPreference>("rename_history_count")
                preference?.summary = "$count items"
                true
            }
            
            // Handle rename default sort preference change
            findPreference<androidx.preference.ListPreference>("rename_default_sort")?.setOnPreferenceChangeListener { _, newValue ->
                val sortValue = newValue as String
                val preference = findPreference<androidx.preference.ListPreference>("rename_default_sort")
                preference?.summary = when (sortValue) {
                    "time_desc" -> getString(R.string.time_descending)
                    "time_asc" -> getString(R.string.time_ascending)
                    "text_asc" -> getString(R.string.text_ascending)
                    "text_desc" -> getString(R.string.text_descending)
                    "text_ignore_asc" -> getString(R.string.text_ignore_alphanum_ascending)
                    "text_ignore_desc" -> getString(R.string.text_ignore_alphanum_descending)
                    else -> getString(R.string.time_descending)
                }
                true
            }
            
            // Handle display settings preference changes
            setupDisplaySettingsListeners()

            // Handle video gesture settings preference changes
            setupVideoGestureSettingsListeners()

            // Handle trash settings preference changes
            setupTrashSettingsListeners()

            // Handle optional advanced full storage access mode
            setupAdvancedStorageAccessListener()
            setupExternalFolderGrantManagementListeners()

            // Set initial summaries
            updateLanguageSummary()
            updateThemeSummary()
            updateRenameSummary()
            updateDisplaySummary()
            updateVideoGestureSummary()
            updateTrashSettingsSummary()
            updateAdvancedStorageAccessSummary()
            updateExternalFolderGrantSummary()
        }

        override fun onResume() {
            super.onResume()
            updateAdvancedStorageAccessSummary()
            updateExternalFolderGrantSummary()
        }
        
        private fun updateThemeSummary() {
            val themePreference = findPreference<androidx.preference.ListPreference>("theme_preference")
            val currentTheme = ThemeHelper.getCurrentTheme(requireContext())
            themePreference?.summary = ThemeHelper.getThemeDisplayName(requireContext(), currentTheme)

            val colorThemePreference = findPreference<ColorThemePreference>("color_theme_preference")
            val currentColorTheme = ThemeHelper.getCurrentColorTheme(requireContext())
            colorThemePreference?.summary = ThemeHelper.getColorThemeDisplayName(requireContext(), currentColorTheme)
        }

        private fun updateLanguageSummary() {
            val languagePreference = findPreference<androidx.preference.ListPreference>("app_language")
            val currentLanguage = LocaleHelper.getCurrentLanguage(requireContext())
            languagePreference?.summary = LocaleHelper.getLanguageDisplayName(requireContext(), currentLanguage)
        }

        private fun updateRenameSummary() {
            val prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(requireContext())

            // Update history count summary
            val historyCount = prefs.getString("rename_history_count", "30")
            val historyCountPreference = findPreference<androidx.preference.ListPreference>("rename_history_count")
            historyCountPreference?.summary = "$historyCount items"

            // Update default sort summary
            val sortValue = prefs.getString("rename_default_sort", "time_desc")
            val sortPreference = findPreference<androidx.preference.ListPreference>("rename_default_sort")
            sortPreference?.summary = when (sortValue) {
                "time_desc" -> getString(R.string.time_descending)
                "time_asc" -> getString(R.string.time_ascending)
                "text_asc" -> getString(R.string.text_ascending)
                "text_desc" -> getString(R.string.text_descending)
                "text_ignore_asc" -> getString(R.string.text_ignore_alphanum_ascending)
                "text_ignore_desc" -> getString(R.string.text_ignore_alphanum_descending)
                else -> getString(R.string.time_descending)
            }
        }

        private fun setupDisplaySettingsListeners() {
            // Handle default sort order preference change
            findPreference<androidx.preference.ListPreference>("default_sort_order")?.setOnPreferenceChangeListener { _, newValue ->
                val sortValue = newValue as String
                val preference = findPreference<androidx.preference.ListPreference>("default_sort_order")
                preference?.summary = when (sortValue) {
                    "date_desc" -> getString(R.string.sort_by_date_desc)
                    "date_asc" -> getString(R.string.sort_by_date_asc)
                    "name_asc" -> getString(R.string.sort_by_name_asc)
                    "name_desc" -> getString(R.string.sort_by_name_desc)
                    else -> getString(R.string.sort_by_date_desc)
                }
                true
            }

            // Handle default view mode preference change
            findPreference<androidx.preference.ListPreference>("default_view_mode")?.setOnPreferenceChangeListener { _, newValue ->
                val viewMode = newValue as String
                val preference = findPreference<androidx.preference.ListPreference>("default_view_mode")
                preference?.summary = when (viewMode) {
                    "grid" -> getString(R.string.thumbnail_view)
                    "list" -> getString(R.string.list_view)
                    "detailed" -> getString(R.string.detailed_view)
                    else -> getString(R.string.thumbnail_view)
                }
                true
            }

            // Handle filename max lines preference change
            findPreference<androidx.preference.ListPreference>("filename_max_lines")?.setOnPreferenceChangeListener { _, newValue ->
                val maxLines = newValue as String
                val preference = findPreference<androidx.preference.ListPreference>("filename_max_lines")
                preference?.summary = when (maxLines) {
                    "1" -> getString(R.string.filename_max_lines_1)
                    "2" -> getString(R.string.filename_max_lines_2)
                    "3" -> getString(R.string.filename_max_lines_3)
                    "0" -> getString(R.string.filename_max_lines_0)
                    else -> getString(R.string.filename_max_lines_1)
                }
                true
            }

            // Handle zoom max scale preference change
            findPreference<androidx.preference.ListPreference>("zoom_max_scale")?.setOnPreferenceChangeListener { _, newValue ->
                val maxScale = newValue as String
                val preference = findPreference<androidx.preference.ListPreference>("zoom_max_scale")
                preference?.summary = when (maxScale) {
                    "2" -> getString(R.string.zoom_scale_2x)
                    "3" -> getString(R.string.zoom_scale_3x)
                    "4" -> getString(R.string.zoom_scale_4x)
                    "5" -> getString(R.string.zoom_scale_5x)
                    "6" -> getString(R.string.zoom_scale_6x)
                    "8" -> getString(R.string.zoom_scale_8x)
                    "10" -> getString(R.string.zoom_scale_10x)
                    else -> getString(R.string.zoom_scale_3x)
                }
                true
            }
        }

        private fun setupVideoGestureSettingsListeners() {
            // Handle video single tap preference change
            findPreference<androidx.preference.ListPreference>("video_single_tap_action")?.setOnPreferenceChangeListener { _, newValue ->
                val action = newValue as String
                val preference = findPreference<androidx.preference.ListPreference>("video_single_tap_action")
                preference?.summary = getVideoActionDisplayName(action)
                true
            }

            // Handle video double tap preference change
            findPreference<androidx.preference.ListPreference>("video_double_tap_action")?.setOnPreferenceChangeListener { _, newValue ->
                val action = newValue as String
                val preference = findPreference<androidx.preference.ListPreference>("video_double_tap_action")
                preference?.summary = getVideoActionDisplayName(action)
                true
            }

            // Handle video left side swipe up preference change
            findPreference<androidx.preference.ListPreference>("video_left_swipe_up_action")?.setOnPreferenceChangeListener { _, newValue ->
                val action = newValue as String
                val preference = findPreference<androidx.preference.ListPreference>("video_left_swipe_up_action")
                preference?.summary = getVideoActionDisplayName(action)
                true
            }

            // Handle video left side swipe down preference change
            findPreference<androidx.preference.ListPreference>("video_left_swipe_down_action")?.setOnPreferenceChangeListener { _, newValue ->
                val action = newValue as String
                val preference = findPreference<androidx.preference.ListPreference>("video_left_swipe_down_action")
                preference?.summary = getVideoActionDisplayName(action)
                true
            }

            // Handle video right side swipe up preference change
            findPreference<androidx.preference.ListPreference>("video_right_swipe_up_action")?.setOnPreferenceChangeListener { _, newValue ->
                val action = newValue as String
                val preference = findPreference<androidx.preference.ListPreference>("video_right_swipe_up_action")
                preference?.summary = getVideoActionDisplayName(action)
                true
            }

            // Handle video right side swipe down preference change
            findPreference<androidx.preference.ListPreference>("video_right_swipe_down_action")?.setOnPreferenceChangeListener { _, newValue ->
                val action = newValue as String
                val preference = findPreference<androidx.preference.ListPreference>("video_right_swipe_down_action")
                preference?.summary = getVideoActionDisplayName(action)
                true
            }
        }

        private fun getVideoActionDisplayName(action: String): String {
            return when (action) {
                "play_pause" -> getString(R.string.video_action_play_pause)
                "show_hide_ui" -> getString(R.string.video_action_show_hide_ui)
                "cycle_zoom" -> getString(R.string.video_action_cycle_zoom)
                "zoom_in_out" -> getString(R.string.video_action_zoom_in_out)
                "show_ui" -> getString(R.string.video_action_show_ui)
                "hide_ui" -> getString(R.string.video_action_hide_ui)
                "zoom_in" -> getString(R.string.video_action_zoom_in)
                "zoom_out" -> getString(R.string.video_action_zoom_out)
                "brightness_up" -> getString(R.string.video_action_brightness_up)
                "brightness_down" -> getString(R.string.video_action_brightness_down)
                "volume_up" -> getString(R.string.video_action_volume_up)
                "volume_down" -> getString(R.string.video_action_volume_down)
                else -> action
            }
        }

        private fun updateVideoGestureSummary() {
            val prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(requireContext())

            // Update video single tap summary
            val singleTapAction = prefs.getString("video_single_tap_action", "show_hide_ui")
            val singleTapPreference = findPreference<androidx.preference.ListPreference>("video_single_tap_action")
            singleTapPreference?.summary = getVideoActionDisplayName(singleTapAction ?: "show_hide_ui")

            // Update video double tap summary
            val doubleTapAction = prefs.getString("video_double_tap_action", "play_pause")
            val doubleTapPreference = findPreference<androidx.preference.ListPreference>("video_double_tap_action")
            doubleTapPreference?.summary = getVideoActionDisplayName(doubleTapAction ?: "play_pause")

            // Update video left side swipe up summary
            val leftSwipeUpAction = prefs.getString("video_left_swipe_up_action", "show_ui")
            val leftSwipeUpPreference = findPreference<androidx.preference.ListPreference>("video_left_swipe_up_action")
            leftSwipeUpPreference?.summary = getVideoActionDisplayName(leftSwipeUpAction ?: "show_ui")

            // Update video left side swipe down summary
            val leftSwipeDownAction = prefs.getString("video_left_swipe_down_action", "hide_ui")
            val leftSwipeDownPreference = findPreference<androidx.preference.ListPreference>("video_left_swipe_down_action")
            leftSwipeDownPreference?.summary = getVideoActionDisplayName(leftSwipeDownAction ?: "hide_ui")

            // Update video right side swipe up summary
            val rightSwipeUpAction = prefs.getString("video_right_swipe_up_action", "brightness_up")
            val rightSwipeUpPreference = findPreference<androidx.preference.ListPreference>("video_right_swipe_up_action")
            rightSwipeUpPreference?.summary = getVideoActionDisplayName(rightSwipeUpAction ?: "brightness_up")

            // Update video right side swipe down summary
            val rightSwipeDownAction = prefs.getString("video_right_swipe_down_action", "brightness_down")
            val rightSwipeDownPreference = findPreference<androidx.preference.ListPreference>("video_right_swipe_down_action")
            rightSwipeDownPreference?.summary = getVideoActionDisplayName(rightSwipeDownAction ?: "brightness_down")
        }

        private fun setupTrashSettingsListeners() {
            findPreference<androidx.preference.ListPreference>(TrashBinStore.TRASH_RETENTION_DAYS_KEY)?.setOnPreferenceChangeListener { _, newValue ->
                val retentionDays = (newValue as String).toIntOrNull() ?: TrashBinStore.TRASH_RETENTION_DEFAULT_DAYS
                val preference = findPreference<androidx.preference.ListPreference>(TrashBinStore.TRASH_RETENTION_DAYS_KEY)
                preference?.summary = getTrashRetentionSummary(retentionDays)
                true
            }
        }

        private fun setupAdvancedStorageAccessListener() {
            findPreference<androidx.preference.Preference>(StorageAccessPreferences.KEY_ADVANCED_FULL_STORAGE_MODE)
                ?.setOnPreferenceClickListener {
                    if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.R) return@setOnPreferenceClickListener true
                    if (!openAdvancedAllFilesSettings()) {
                        showToast(getString(R.string.advanced_full_storage_open_failed))
                    }
                    true
                }
        }

        private fun setupExternalFolderGrantManagementListeners() {
            findPreference<androidx.preference.Preference>(StorageAccessPreferences.KEY_MANAGE_EXTERNAL_FOLDER_GRANTS)
                ?.setOnPreferenceClickListener {
                    showExternalFolderGrantManagementDialog()
                    true
                }

            findPreference<androidx.preference.Preference>(StorageAccessPreferences.KEY_RESET_EXTERNAL_FOLDER_GRANTS)
                ?.setOnPreferenceClickListener {
                    showResetExternalFolderGrantsConfirmation()
                    true
                }
        }

        private fun updateTrashSettingsSummary() {
            val prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(requireContext())
            val retentionDays = prefs.getString(
                TrashBinStore.TRASH_RETENTION_DAYS_KEY,
                TrashBinStore.TRASH_RETENTION_DEFAULT_DAYS.toString()
            )?.toIntOrNull() ?: TrashBinStore.TRASH_RETENTION_DEFAULT_DAYS

            val preference = findPreference<androidx.preference.ListPreference>(TrashBinStore.TRASH_RETENTION_DAYS_KEY)
            preference?.summary = getTrashRetentionSummary(retentionDays)
        }

        private fun updateAdvancedStorageAccessSummary() {
            val preference =
                findPreference<androidx.preference.Preference>(StorageAccessPreferences.KEY_ADVANCED_FULL_STORAGE_MODE)
                    ?: return

            if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.R) {
                preference.isVisible = false
                return
            }

            val granted = android.os.Environment.isExternalStorageManager()
            preference.summary = if (granted) {
                getString(R.string.advanced_full_storage_mode_summary_enabled)
            } else {
                getString(R.string.advanced_full_storage_mode_summary_permission_missing)
            }
        }

        private fun updateExternalFolderGrantSummary() {
            val preference =
                findPreference<androidx.preference.Preference>(StorageAccessPreferences.KEY_MANAGE_EXTERNAL_FOLDER_GRANTS)
                    ?: return
            val mappingCount = ExternalFolderGrantStore.getAllMappings(requireContext()).size
            val persistedTreeGrantCount = getPersistedTreePermissions().size
            preference.summary = if (mappingCount == 0 && persistedTreeGrantCount == 0) {
                getString(R.string.manage_external_folder_grants_summary_none)
            } else {
                getString(
                    R.string.manage_external_folder_grants_summary_format,
                    mappingCount,
                    persistedTreeGrantCount
                )
            }
        }

        private fun openAdvancedAllFilesSettings(): Boolean {
            return try {
                val intent = android.content.Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = android.net.Uri.parse("package:${requireContext().packageName}")
                }
                startActivity(intent)
                true
            } catch (_: Exception) {
                try {
                    startActivity(android.content.Intent(android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
                    true
                } catch (_: Exception) {
                    false
                }
            }
        }

        private fun showExternalFolderGrantManagementDialog() {
            val context = requireContext()
            val mappings = ExternalFolderGrantStore.getAllMappings(context)
            val persistedTreePermissions = getPersistedTreePermissions()
            val message = buildString {
                append(getString(R.string.manage_external_folder_grants_dialog_mappings_header))
                append(" (")
                append(mappings.size)
                append(")")
                append('\n')
                if (mappings.isEmpty()) {
                    append(getString(R.string.manage_external_folder_grants_dialog_none))
                } else {
                    mappings.forEach { mapping ->
                        append("- ")
                        append(formatMappingForDisplay(mapping))
                        append('\n')
                    }
                }
                if (mappings.isNotEmpty()) {
                    setLength(length - 1)
                }
                append("\n\n")
                append(getString(R.string.manage_external_folder_grants_dialog_persisted_header))
                append(" (")
                append(persistedTreePermissions.size)
                append(")")
                append('\n')
                if (persistedTreePermissions.isEmpty()) {
                    append(getString(R.string.manage_external_folder_grants_dialog_none))
                } else {
                    persistedTreePermissions.forEachIndexed { index, permission ->
                        append("- ")
                        append(formatTreeUriForDisplay(permission.uri))
                        if (index != persistedTreePermissions.lastIndex) {
                            append('\n')
                        }
                    }
                }
            }

            android.app.AlertDialog.Builder(context)
                .setTitle(R.string.manage_external_folder_grants_dialog_title)
                .setMessage(message)
                .setPositiveButton(R.string.close, null)
                .show()
        }

        private fun showResetExternalFolderGrantsConfirmation() {
            android.app.AlertDialog.Builder(requireContext())
                .setTitle(R.string.reset_external_folder_grants_confirm_title)
                .setMessage(R.string.reset_external_folder_grants_confirm_message)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.ok) { _, _ ->
                    resetExternalFolderGrants()
                }
                .show()
        }

        private fun resetExternalFolderGrants() {
            val context = requireContext()
            val contentResolver = context.contentResolver
            val persistedTreePermissions = getPersistedTreePermissions()

            var revokedCount = 0
            persistedTreePermissions.forEach { permission ->
                val uri = permission.uri
                try {
                    contentResolver.releasePersistableUriPermission(
                        uri,
                        buildPersistablePermissionFlags(permission)
                    )
                    revokedCount++
                } catch (e: Exception) {
                    android.util.Log.w(
                        "SettingsFragment",
                        "Failed to revoke persisted tree permission: $uri",
                        e
                    )
                }
            }

            val removedMappingsCount = ExternalFolderGrantStore.clearAllMappings(context)
            updateExternalFolderGrantSummary()
            showToast(
                getString(
                    R.string.reset_external_folder_grants_done,
                    removedMappingsCount,
                    revokedCount
                )
            )
        }

        private fun getPersistedTreePermissions(): List<android.content.UriPermission> {
            return requireContext()
                .contentResolver
                .persistedUriPermissions
                .filter { permission ->
                    android.provider.DocumentsContract.isTreeUri(permission.uri)
                }
                .sortedBy { permission ->
                    formatTreeUriForDisplay(permission.uri)
                }
        }

        private fun buildPersistablePermissionFlags(permission: android.content.UriPermission): Int {
            var flags = 0
            if (permission.isReadPermission) {
                flags = flags or android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
            if (permission.isWritePermission) {
                flags = flags or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            }
            if (flags == 0) {
                flags =
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            }
            return flags
        }

        private fun formatMappingForDisplay(mapping: ExternalFolderGrantStore.GrantMapping): String {
            val parentFolderDisplay = formatDocumentIdForDisplay(mapping.authority, mapping.parentDocumentId)
            val treeUriDisplay = mapping.treeUri?.let { formatTreeUriForDisplay(it) } ?: getString(R.string.unknown_value)
            return "$parentFolderDisplay -> $treeUriDisplay"
        }

        private fun formatTreeUriForDisplay(treeUri: android.net.Uri): String {
            val authority = treeUri.authority ?: return treeUri.toString()
            val treeDocumentId = try {
                android.provider.DocumentsContract.getTreeDocumentId(treeUri)
            } catch (_: Exception) {
                null
            } ?: return treeUri.toString()
            return formatDocumentIdForDisplay(authority, treeDocumentId)
        }

        private fun formatDocumentIdForDisplay(authority: String, documentId: String): String {
            if (authority == "com.android.externalstorage.documents") {
                val volume = documentId.substringBefore(':', "")
                val relativePath = documentId.substringAfter(':', "").trim('/')

                if (volume.equals("primary", ignoreCase = true)) {
                    val rootLabel = getString(R.string.storage_internal_label)
                    return if (relativePath.isBlank()) rootLabel else "$rootLabel/$relativePath"
                }

                if (volume.isNotBlank()) {
                    val rootLabel = getString(R.string.storage_external_label_format, volume)
                    return if (relativePath.isBlank()) rootLabel else "$rootLabel/$relativePath"
                }
            }
            return "$authority:$documentId"
        }

        private fun getTrashRetentionSummary(retentionDays: Int): String {
            return if (retentionDays <= 0) {
                getString(R.string.trash_retention_days_summary_disabled)
            } else {
                getString(R.string.trash_retention_days_summary_format, retentionDays)
            }
        }

        private fun updateDisplaySummary() {
            val prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(requireContext())

            // Update default sort order summary
            val sortOrder = prefs.getString("default_sort_order", "date_desc")
            val sortOrderPreference = findPreference<androidx.preference.ListPreference>("default_sort_order")
            sortOrderPreference?.summary = when (sortOrder) {
                "date_desc" -> getString(R.string.sort_by_date_desc)
                "date_asc" -> getString(R.string.sort_by_date_asc)
                "name_asc" -> getString(R.string.sort_by_name_asc)
                "name_desc" -> getString(R.string.sort_by_name_desc)
                else -> getString(R.string.sort_by_date_desc)
            }

            // Update default view mode summary
            val viewMode = prefs.getString("default_view_mode", "grid")
            val viewModePreference = findPreference<androidx.preference.ListPreference>("default_view_mode")
            viewModePreference?.summary = when (viewMode) {
                "grid" -> getString(R.string.thumbnail_view)
                "list" -> getString(R.string.list_view)
                "detailed" -> getString(R.string.detailed_view)
                else -> getString(R.string.thumbnail_view)
            }

            // Update filename max lines summary
            val maxLines = prefs.getString("filename_max_lines", "1")
            val maxLinesPreference = findPreference<androidx.preference.ListPreference>("filename_max_lines")
            maxLinesPreference?.summary = when (maxLines) {
                "1" -> getString(R.string.filename_max_lines_1)
                "2" -> getString(R.string.filename_max_lines_2)
                "3" -> getString(R.string.filename_max_lines_3)
                "0" -> getString(R.string.filename_max_lines_0)
                else -> getString(R.string.filename_max_lines_1)
            }

            // Update zoom max scale summary
            val zoomMaxScale = prefs.getString("zoom_max_scale", "3")
            val zoomMaxScalePreference = findPreference<androidx.preference.ListPreference>("zoom_max_scale")
            zoomMaxScalePreference?.summary = when (zoomMaxScale) {
                "2" -> getString(R.string.zoom_scale_2x)
                "3" -> getString(R.string.zoom_scale_3x)
                "4" -> getString(R.string.zoom_scale_4x)
                "5" -> getString(R.string.zoom_scale_5x)
                "6" -> getString(R.string.zoom_scale_6x)
                "8" -> getString(R.string.zoom_scale_8x)
                "10" -> getString(R.string.zoom_scale_10x)
                else -> getString(R.string.zoom_scale_3x)
            }
        }

        private fun showCustomizeActionBarDialog() {
            val ctx = requireContext()
            val inflater = android.view.LayoutInflater.from(ctx)
            val container = android.widget.LinearLayout(ctx).apply {
                orientation = android.widget.LinearLayout.VERTICAL
                setPadding(24, 24, 24, 0)
            }

            // Preferences for order/visibility
            val prefs = ctx.getSharedPreferences(ActionBarPreferences.PREFS_NAME, android.content.Context.MODE_PRIVATE)
            val defaultOrder = ActionBarPreferences.DEFAULT_ACTION_ORDER

            data class Item(val key: String, val label: String)
            val labelMap = mapOf(
                "delete" to getString(R.string.delete),
                //"share" to getString(R.string.share),
                //"edit" to getString(R.string.edit),
                "rename" to getString(R.string.rename),
                "rotate_screen" to getString(R.string.rotate_screen),
                "properties" to getString(R.string.properties),
                //"rotate_photo" to getString(R.string.rotate),
                //"copy" to getString(R.string.copy),
                //"move" to getString(R.string.move),
                "reload_video" to getString(R.string.reload_video),
            )

            val iconMap = mapOf(
                "delete" to R.drawable.ic_delete,
                //"share" to R.drawable.ic_share,
                //"edit" to R.drawable.ic_edit,
                "rename" to R.drawable.ic_rename,
                "rotate_screen" to R.drawable.ic_rotate_right,
                "properties" to R.drawable.ic_info,
                //"rotate_photo" to R.drawable.ic_rotate_right,
                //"copy" to R.drawable.ic_copy,
                //"move" to R.drawable.ic_move,
                "reload_video" to R.drawable.ic_refresh,
            )

            // Load stored preferences and filter out items not in labelMap
            val storedOrder = prefs.getString(ActionBarPreferences.KEY_ORDER, null)?.split(',')?.filter { it.isNotBlank() }
            val order = (storedOrder ?: defaultOrder).filter { labelMap.containsKey(it) }.toMutableList()
            val visibleSet = prefs.getString(ActionBarPreferences.KEY_VISIBLE, null)
                ?.split(',')
                ?.filter { it.isNotBlank() && labelMap.containsKey(it) }
                ?.toMutableSet()
                ?: defaultOrder.toMutableSet()

            val items = order.map { Item(it, labelMap[it] ?: it) }.toMutableList()

            // RecyclerView with drag handle
            val recyclerView = androidx.recyclerview.widget.RecyclerView(ctx)
            recyclerView.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(ctx)

            lateinit var touchHelper: androidx.recyclerview.widget.ItemTouchHelper
            class ActionAdapter : androidx.recyclerview.widget.RecyclerView.Adapter<ActionAdapter.VH>() {
                inner class VH(view: android.view.View) : androidx.recyclerview.widget.RecyclerView.ViewHolder(view) {
                    val check: android.widget.CheckBox = view.findViewById(R.id.checkbox)
                    val handle: android.widget.ImageView = view.findViewById(R.id.dragHandle)
                    val icon: android.widget.ImageView = view.findViewById(R.id.actionIcon)
                }
                override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): VH {
                    val v = inflater.inflate(R.layout.item_action_bar_option, parent, false)
                    return VH(v)
                }
                override fun getItemCount() = items.size
                override fun onBindViewHolder(holder: VH, position: Int) {
                    val item = items[position]
                    holder.check.text = item.label
                    holder.check.setOnCheckedChangeListener(null)
                    holder.check.isChecked = visibleSet.contains(item.key)
                    holder.check.setOnCheckedChangeListener { _, isChecked ->
                        if (isChecked) visibleSet.add(item.key) else visibleSet.remove(item.key)
                    }
                    // Set action icon
                    iconMap[item.key]?.let { iconRes ->
                        holder.icon.setImageResource(iconRes)
                    }
                    holder.handle.setOnTouchListener { _, event ->
                        if (event.actionMasked == android.view.MotionEvent.ACTION_DOWN) touchHelper.startDrag(holder)
                        false
                    }
                }
                fun moveItem(from: Int, to: Int) {
                    if (from == to) return
                    val it = items.removeAt(from)
                    items.add(to, it)
                    notifyItemMoved(from, to)
                }
            }

            val adapter = ActionAdapter()
            recyclerView.adapter = adapter

            val callback = object : androidx.recyclerview.widget.ItemTouchHelper.SimpleCallback(
                androidx.recyclerview.widget.ItemTouchHelper.UP or androidx.recyclerview.widget.ItemTouchHelper.DOWN, 0
            ) {
                override fun onMove(rv: androidx.recyclerview.widget.RecyclerView, vh: androidx.recyclerview.widget.RecyclerView.ViewHolder, target: androidx.recyclerview.widget.RecyclerView.ViewHolder): Boolean {
                    adapter.moveItem(vh.adapterPosition, target.adapterPosition)
                    return true
                }
                override fun onSwiped(viewHolder: androidx.recyclerview.widget.RecyclerView.ViewHolder, direction: Int) {}
                override fun isLongPressDragEnabled(): Boolean = false
            }
            val ith = androidx.recyclerview.widget.ItemTouchHelper(callback)
            touchHelper = ith
            ith.attachToRecyclerView(recyclerView)

            container.addView(recyclerView, android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                0, 1f
            ))

            // Bottom controls
            val controls = android.widget.LinearLayout(ctx).apply {
                orientation = android.widget.LinearLayout.HORIZONTAL
                setPadding(0, 16, 0, 0)
                gravity = android.view.Gravity.END
            }
            val cancelBtn = android.widget.Button(ctx).apply { text = getString(R.string.cancel) }
            val saveBtn = android.widget.Button(ctx).apply { text = getString(R.string.done) }
            controls.addView(cancelBtn)
            controls.addView(saveBtn)
            container.addView(controls)

            val dialog = android.app.AlertDialog.Builder(ctx)
                .setTitle(R.string.customize_action_bar)
                .setView(container)
                .create()

            cancelBtn.setOnClickListener { dialog.dismiss() }
            saveBtn.setOnClickListener {
                val newOrder = items.map { it.key }
                val newVisible = visibleSet.toList()
                prefs.edit()
                    .putString(ActionBarPreferences.KEY_ORDER, newOrder.joinToString(","))
                    .putString(ActionBarPreferences.KEY_VISIBLE, newVisible.joinToString(","))
                    .apply()
                dialog.dismiss()
            }

            dialog.show()
        }

        private fun exportSettings() {
            try {
                val defaultFilename = settingsHelper.generateDefaultFilename()
                exportFileLauncher.launch(defaultFilename)
            } catch (e: Exception) {
                android.util.Log.e("SettingsFragment", "Failed to launch export file picker", e)
                showToast(getString(R.string.export_failed))
            }
        }

        private fun importSettings() {
            try {
                importFileLauncher.launch(arrayOf("application/json", "*/*"))
            } catch (e: Exception) {
                android.util.Log.e("SettingsFragment", "Failed to launch import file picker", e)
                showToast(getString(R.string.import_failed))
            }
        }

        private fun exportToUri(uri: android.net.Uri) {
            try {
                requireContext().contentResolver.openOutputStream(uri)?.use { outputStream ->
                    val success = settingsHelper.exportSettings(outputStream)
                    if (success) {
                        showToast(getString(R.string.export_success))
                    } else {
                        showToast(getString(R.string.export_failed))
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("SettingsFragment", "Failed to export settings", e)
                showToast(getString(R.string.export_failed))
            }
        }

        private fun importFromUri(uri: android.net.Uri) {
            try {
                requireContext().contentResolver.openInputStream(uri)?.use { inputStream ->
                    val (importedCount, skippedCount) = settingsHelper.importSettings(inputStream)

                    // Show result message
                    val message = if (skippedCount > 0) {
                        getString(R.string.settings_imported_count, importedCount, skippedCount)
                    } else {
                        getString(R.string.import_success)
                    }
                    showToast(message)

                    // Refresh UI to show updated settings
                    updateLanguageSummary()
                    updateThemeSummary()
                    updateRenameSummary()
                    updateDisplaySummary()
                    updateVideoGestureSummary()
                    updateTrashSettingsSummary()
                    updateAdvancedStorageAccessSummary()
                    updateExternalFolderGrantSummary()

                    // Recreate activity to apply theme changes if any
                    activity?.recreate()
                }
            } catch (e: IllegalArgumentException) {
                android.util.Log.e("SettingsFragment", "Invalid settings file", e)
                showToast(getString(R.string.invalid_settings_file))
            } catch (e: Exception) {
                android.util.Log.e("SettingsFragment", "Failed to import settings", e)
                showToast(getString(R.string.import_failed))
            }
        }

        private fun showToast(message: String) {
            android.widget.Toast.makeText(requireContext(), message, android.widget.Toast.LENGTH_LONG).show()
        }
    }
}
