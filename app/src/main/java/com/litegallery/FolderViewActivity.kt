package com.litegallery

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.litegallery.databinding.ActivityFolderViewBinding
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
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFolderViewBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        folderPath = intent.getStringExtra(EXTRA_FOLDER_PATH) ?: ""
        folderName = intent.getStringExtra(EXTRA_FOLDER_NAME) ?: ""
        
        setupToolbar()
        setupRecyclerView()
        
        mediaScanner = MediaScanner(this)
        loadMediaItems()
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
                // TODO: Toggle between grid/list view modes
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
        
        binding.recyclerView.apply {
            adapter = mediaAdapter
            layoutManager = GridLayoutManager(this@FolderViewActivity, 3)
        }
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