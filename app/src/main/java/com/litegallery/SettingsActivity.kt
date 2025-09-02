package com.litegallery

import android.os.Bundle
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceFragmentCompat
import com.litegallery.databinding.ActivitySettingsBinding

class SettingsActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivitySettingsBinding
    
    override fun onCreate(savedInstanceState: Bundle?) {
        // Apply theme before setting content view
        ThemeHelper.applyTheme(this)
        
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

            // Handle Customize Action Bar click
            findPreference<androidx.preference.Preference>("customize_action_bar")?.setOnPreferenceClickListener {
                showCustomizeActionBarDialog()
                true
            }
            
            // Set initial theme summary
            updateThemeSummary()
        }
        
        private fun updateThemeSummary() {
            val themePreference = findPreference<androidx.preference.ListPreference>("theme_preference")
            val currentTheme = ThemeHelper.getCurrentTheme(requireContext())
            themePreference?.summary = ThemeHelper.getThemeDisplayName(requireContext(), currentTheme)
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
