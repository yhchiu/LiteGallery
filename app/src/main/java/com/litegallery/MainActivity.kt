package com.litegallery

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.litegallery.databinding.ActivityMainBinding
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityMainBinding
    private lateinit var folderAdapter: FolderAdapter
    private lateinit var mediaScanner: MediaScanner
    
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.all { it.value }) {
            loadMediaFolders()
        } else {
            showPermissionRequired()
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        // Apply theme before setting content view
        ThemeHelper.applyTheme(this)
        
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setSupportActionBar(binding.toolbar)
        
        mediaScanner = MediaScanner(this)
        setupRecyclerView()
        
        checkPermissionsAndLoad()
    }
    
    override fun onResume() {
        super.onResume()
        // Apply theme in case it was changed in settings
        ThemeHelper.applyTheme(this)
        
        // Check permissions again when returning from settings
        if (!hasStoragePermissions()) {
            showPermissionRequired()
        } else if (folderAdapter.itemCount == 0) {
            loadMediaFolders()
        }
    }
    
    private fun checkPermissionsAndLoad() {
        if (hasStoragePermissions()) {
            loadMediaFolders()
        } else {
            requestStoragePermissions()
        }
    }
    
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_settings -> {
                startActivity(Intent(this, SettingsActivity::class.java))
                true
            }
            R.id.action_trash_bin -> {
                startActivity(Intent(this, TrashBinActivity::class.java))
                true
            }
            R.id.action_refresh -> {
                loadMediaFolders()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
    
    private fun setupRecyclerView() {
        folderAdapter = FolderAdapter { folder ->
            val intent = Intent(this, FolderViewActivity::class.java).apply {
                putExtra(FolderViewActivity.EXTRA_FOLDER_PATH, folder.path)
                putExtra(FolderViewActivity.EXTRA_FOLDER_NAME, folder.name)
            }
            startActivity(intent)
        }
        
        binding.recyclerView.apply {
            adapter = folderAdapter
            layoutManager = GridLayoutManager(this@MainActivity, 1)
        }
    }
    
    private fun hasStoragePermissions(): Boolean {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED
        } else if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            // For Android 11+, check if we have MANAGE_EXTERNAL_STORAGE permission
            android.os.Environment.isExternalStorageManager() ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }
    }
    
    private fun requestStoragePermissions() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            // For Android 11+, request MANAGE_EXTERNAL_STORAGE for full access
            if (!android.os.Environment.isExternalStorageManager()) {
                try {
                    val intent = android.content.Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                    intent.data = android.net.Uri.parse("package:$packageName")
                    startActivity(intent)
                } catch (e: Exception) {
                    // Fallback to regular permissions
                    requestRegularPermissions()
                }
            } else {
                loadMediaFolders()
            }
        } else {
            requestRegularPermissions()
        }
    }
    
    private fun requestRegularPermissions() {
        val permissions = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO
            )
        } else {
            arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
        }
        
        permissionLauncher.launch(permissions)
    }
    
    private fun loadMediaFolders() {
        binding.progressBar.visibility = View.VISIBLE
        binding.emptyView.visibility = View.GONE
        binding.recyclerView.visibility = View.GONE
        
        lifecycleScope.launch {
            try {
                val folders = mediaScanner.scanMediaFolders()
                
                if (folders.isEmpty()) {
                    binding.progressBar.visibility = View.GONE
                    binding.emptyView.visibility = View.VISIBLE
                    binding.recyclerView.visibility = View.GONE
                } else {
                    folderAdapter.submitList(folders)
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
    
    private fun showPermissionRequired() {
        binding.progressBar.visibility = View.GONE
        binding.emptyView.apply {
            visibility = View.VISIBLE
            setOnClickListener {
                requestStoragePermissions()
            }
        }
        binding.recyclerView.visibility = View.GONE
    }
}