package com.litegallery

import android.os.Bundle
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceFragmentCompat
import com.litegallery.databinding.ActivitySettingsBinding

class SettingsActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivitySettingsBinding
    
    override fun onCreate(savedInstanceState: Bundle?) {
        // Apply theme and color theme before setting content view
        ThemeHelper.applyTheme(this)
        ThemeHelper.applyColorTheme(this)
        
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
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
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.preferences, rootKey)

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

            // Set initial summaries
            updateThemeSummary()
            updateRenameSummary()
            updateDisplaySummary()
        }
        
        private fun updateThemeSummary() {
            val themePreference = findPreference<androidx.preference.ListPreference>("theme_preference")
            val currentTheme = ThemeHelper.getCurrentTheme(requireContext())
            themePreference?.summary = ThemeHelper.getThemeDisplayName(requireContext(), currentTheme)
            
            val colorThemePreference = findPreference<ColorThemePreference>("color_theme_preference")
            val currentColorTheme = ThemeHelper.getCurrentColorTheme(requireContext())
            colorThemePreference?.summary = ThemeHelper.getColorThemeDisplayName(requireContext(), currentColorTheme)
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
        }

        private fun showCustomizeActionBarDialog() {
            val ctx = requireContext()
            val inflater = android.view.LayoutInflater.from(ctx)
            val container = android.widget.LinearLayout(ctx).apply {
                orientation = android.widget.LinearLayout.VERTICAL
                setPadding(24, 24, 24, 0)
            }

            // Preferences for order/visibility
            val prefs = ctx.getSharedPreferences("action_bar_prefs", android.content.Context.MODE_PRIVATE)
            val defaultOrder = listOf("delete", "share", "edit", "rename", "rotate_screen", "properties", "rotate_photo", "copy", "move")
            val storedOrder = prefs.getString("order", null)?.split(',')?.filter { it.isNotBlank() }
            val order = (storedOrder ?: defaultOrder).toMutableList()
            val visibleSet = prefs.getString("visible", null)?.split(',')?.filter { it.isNotBlank() }?.toMutableSet() ?: defaultOrder.toMutableSet()

            data class Item(val key: String, val label: String)
            val labelMap = mapOf(
                "delete" to getString(R.string.delete),
                "share" to getString(R.string.share),
                "edit" to getString(R.string.edit),
                "rename" to getString(R.string.rename),
                "rotate_screen" to getString(R.string.rotate_screen),
                "properties" to getString(R.string.properties),
                "rotate_photo" to getString(R.string.rotate),
                "copy" to getString(R.string.copy),
                "move" to getString(R.string.move),
            )

            val items = order.map { Item(it, labelMap[it] ?: it) }.toMutableList()

            // RecyclerView with drag handle
            val recyclerView = androidx.recyclerview.widget.RecyclerView(ctx)
            recyclerView.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(ctx)

            lateinit var touchHelper: androidx.recyclerview.widget.ItemTouchHelper
            class ActionAdapter : androidx.recyclerview.widget.RecyclerView.Adapter<ActionAdapter.VH>() {
                inner class VH(view: android.view.View) : androidx.recyclerview.widget.RecyclerView.ViewHolder(view) {
                    val check: android.widget.CheckBox = view.findViewById(R.id.checkbox)
                    val handle: android.widget.ImageView = view.findViewById(R.id.dragHandle)
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
                    .putString("order", newOrder.joinToString(","))
                    .putString("visible", newVisible.joinToString(","))
                    .apply()
                dialog.dismiss()
            }

            dialog.show()
        }
    }
}
