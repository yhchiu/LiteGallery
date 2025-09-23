package org.iurl.litegallery

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import org.iurl.litegallery.databinding.ActivityTrashBinBinding

class TrashBinActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityTrashBinBinding
    private lateinit var mediaAdapter: MediaAdapter
    private var currentColorTheme: String? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        // Apply theme and color theme before setting content view
        ThemeHelper.applyTheme(this)
        ThemeHelper.applyColorTheme(this)
        
        super.onCreate(savedInstanceState)
        binding = ActivityTrashBinBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Initialize current color theme
        currentColorTheme = ThemeHelper.getCurrentColorTheme(this)
        
        setupToolbar()
        setupRecyclerView()
        loadTrashItems()
    }
    
    override fun onResume() {
        super.onResume()
        // Apply theme in case it was changed in settings
        ThemeHelper.applyTheme(this)
        
        // Check if color theme changed and recreate if necessary
        val newColorTheme = ThemeHelper.getCurrentColorTheme(this)
        if (currentColorTheme != null && currentColorTheme != newColorTheme) {
            recreate()
            return
        }
        currentColorTheme = newColorTheme
    }
    
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.trash_bin_menu, menu)
        return true
    }
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            R.id.action_empty_trash -> {
                // TODO: Implement empty trash functionality
                true
            }
            R.id.action_restore_all -> {
                // TODO: Implement restore all functionality
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
            title = getString(R.string.trash_bin_title)
        }
    }
    
    private fun setupRecyclerView() {
        mediaAdapter = MediaAdapter { mediaItem, position ->
            // TODO: Implement trash item click (restore/delete permanently)
        }
        
        binding.recyclerView.apply {
            adapter = mediaAdapter
            layoutManager = GridLayoutManager(this@TrashBinActivity, 3)
        }
    }
    
    private fun loadTrashItems() {
        // TODO: Implement trash bin loading logic
        // For now, show empty state
        showEmptyState()
    }
    
    private fun showEmptyState() {
        binding.recyclerView.visibility = View.GONE
        binding.emptyView.visibility = View.VISIBLE
        binding.emptyView.text = getString(R.string.trash_bin_empty)
    }
}