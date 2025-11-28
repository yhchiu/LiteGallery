package org.iurl.litegallery

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.preference.PreferenceManager
import org.iurl.litegallery.databinding.ActivityFolderViewBinding
import kotlinx.coroutines.launch

class FolderViewActivity : AppCompatActivity() {
    
    companion object {
        const val EXTRA_FOLDER_PATH = "extra_folder_path"
        const val EXTRA_FOLDER_NAME = "extra_folder_name"
    }
    
    private lateinit var binding: ActivityFolderViewBinding
    private lateinit var mediaAdapter: MediaAdapter
    private lateinit var mediaScanner: MediaScanner
    
    private var folderPath: String = ""
    private var folderName: String = ""
    private var mediaItems: List<MediaItem> = emptyList()
    private var currentColorTheme: String? = null
    private var currentViewMode: MediaAdapter.ViewMode = MediaAdapter.ViewMode.GRID
    
    override fun onCreate(savedInstanceState: Bundle?) {
        // Apply theme and color theme before setting content view
        ThemeHelper.applyTheme(this)
        ThemeHelper.applyColorTheme(this)
        
        super.onCreate(savedInstanceState)
        binding = ActivityFolderViewBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        folderPath = intent.getStringExtra(EXTRA_FOLDER_PATH) ?: ""
        folderName = intent.getStringExtra(EXTRA_FOLDER_NAME) ?: ""
        
        // Initialize current color theme
        currentColorTheme = ThemeHelper.getCurrentColorTheme(this)

        // Load default view mode from preferences
        loadViewModePreference()

        setupToolbar()
        setupRecyclerView()

        mediaScanner = MediaScanner(this)
        loadMediaItems()
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
        menuInflater.inflate(R.menu.folder_view_menu, menu)
        return true
    }
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            R.id.action_view_mode -> {
                toggleViewMode()
                true
            }
            R.id.action_sort -> {
                // TODO: Show sort options dialog
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
            title = folderName
        }
    }
    
    private fun setupRecyclerView() {
        mediaAdapter = MediaAdapter { mediaItem, position ->
            val intent = Intent(this, MediaViewerActivity::class.java).apply {
                putExtra(MediaViewerActivity.EXTRA_MEDIA_PATH, mediaItem.path)
                putExtra(MediaViewerActivity.EXTRA_FOLDER_PATH, folderPath)
                putExtra(MediaViewerActivity.EXTRA_CURRENT_POSITION, position)
            }
            startActivity(intent)
        }

        binding.recyclerView.adapter = mediaAdapter

        // Apply view mode
        updateLayoutManager()
    }

    private fun updateLayoutManager() {
        binding.recyclerView.layoutManager = when (currentViewMode) {
            MediaAdapter.ViewMode.GRID -> GridLayoutManager(this, 3)
            MediaAdapter.ViewMode.LIST, MediaAdapter.ViewMode.DETAILED -> LinearLayoutManager(this)
        }
        mediaAdapter.viewMode = currentViewMode
    }

    private fun loadViewModePreference() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val viewModeValue = prefs.getString("default_view_mode", "grid")
        currentViewMode = when (viewModeValue) {
            "list" -> MediaAdapter.ViewMode.LIST
            "detailed" -> MediaAdapter.ViewMode.DETAILED
            else -> MediaAdapter.ViewMode.GRID
        }
    }

    private fun toggleViewMode() {
        currentViewMode = when (currentViewMode) {
            MediaAdapter.ViewMode.GRID -> MediaAdapter.ViewMode.LIST
            MediaAdapter.ViewMode.LIST -> MediaAdapter.ViewMode.DETAILED
            MediaAdapter.ViewMode.DETAILED -> MediaAdapter.ViewMode.GRID
        }
        updateLayoutManager()

        // Show toast to indicate current view mode
        val modeName = when (currentViewMode) {
            MediaAdapter.ViewMode.GRID -> getString(R.string.thumbnail_view)
            MediaAdapter.ViewMode.LIST -> getString(R.string.list_view)
            MediaAdapter.ViewMode.DETAILED -> getString(R.string.detailed_view)
        }
        android.widget.Toast.makeText(this, modeName, android.widget.Toast.LENGTH_SHORT).show()
    }
    
    private fun loadMediaItems() {
        binding.progressBar.visibility = View.VISIBLE
        binding.emptyView.visibility = View.GONE
        binding.recyclerView.visibility = View.GONE
        
        lifecycleScope.launch {
            try {
                mediaItems = mediaScanner.scanMediaInFolder(folderPath)
                
                if (mediaItems.isEmpty()) {
                    binding.progressBar.visibility = View.GONE
                    binding.emptyView.visibility = View.VISIBLE
                    binding.recyclerView.visibility = View.GONE
                } else {
                    mediaAdapter.submitList(mediaItems)
                    binding.progressBar.visibility = View.GONE
                    binding.emptyView.visibility = View.GONE
                    binding.recyclerView.visibility = View.VISIBLE
                }
            } catch (e: Exception) {
                binding.progressBar.visibility = View.GONE
                binding.emptyView.visibility = View.VISIBLE
                binding.recyclerView.visibility = View.GONE
            }
        }
    }
}