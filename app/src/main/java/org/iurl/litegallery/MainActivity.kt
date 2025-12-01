package org.iurl.litegallery

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
import org.iurl.litegallery.databinding.ActivityMainBinding
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityMainBinding
    private lateinit var folderAdapter: FolderAdapter
    private lateinit var mediaScanner: MediaScanner
    private var permissionsGrantedOnStart = false
    
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.all { it.value }) {
            // Check if we need to request additional storage manager permission for non-media folders
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU && 
                !android.os.Environment.isExternalStorageManager()) {
                android.util.Log.d("MainActivity", "Media permissions granted, now requesting storage manager access")
                try {
                    val intent = android.content.Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                    intent.data = android.net.Uri.parse("package:$packageName")
                    startActivity(intent)
                } catch (e: Exception) {
                    // Continue with media permissions only
                    loadMediaFolders()
                }
            } else {
                loadMediaFolders()
            }
        } else {
            showPermissionRequired()
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        // Apply theme and color theme before setting content view
        ThemeHelper.applyTheme(this)
        ThemeHelper.applyColorTheme(this)
        
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
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

        setSupportActionBar(binding.toolbar)
        
        // Initialize current color theme
        currentColorTheme = ThemeHelper.getCurrentColorTheme(this)
        
        mediaScanner = MediaScanner(this)
        setupRecyclerView()
        
        checkPermissionsAndLoad()
    }
    
    private var currentColorTheme: String? = null

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
        
        // Check permissions again when returning from settings
        if (!hasStoragePermissions()) {
            // If permissions were granted on start but now failing, try once more after a delay
            if (permissionsGrantedOnStart) {
                android.util.Log.d("MainActivity", "Permissions were granted on start but failing now, retrying...")
                binding.root.postDelayed({
                    if (hasStoragePermissions()) {
                        loadMediaFolders()
                    } else {
                        showPermissionRequired()
                    }
                }, 100)
            } else {
                showPermissionRequired()
            }
        } else if (folderAdapter.itemCount == 0) {
            loadMediaFolders()
        }
    }
    
    private fun checkPermissionsAndLoad() {
        if (hasStoragePermissions()) {
            permissionsGrantedOnStart = true
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
            // Trash Bin not yet implemented
            /*
            R.id.action_trash_bin -> {
                startActivity(Intent(this, TrashBinActivity::class.java))
                true
            }
            */
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
        val sdkInt = android.os.Build.VERSION.SDK_INT
        android.util.Log.d("MainActivity", "Checking permissions for SDK $sdkInt")
        
        return if (sdkInt >= android.os.Build.VERSION_CODES.TIRAMISU) {
            val imagesPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED
            val videosPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED
            val isStorageManager = android.os.Environment.isExternalStorageManager()
            android.util.Log.d("MainActivity", "Android 13+: Images=$imagesPermission, Videos=$videosPermission, StorageManager=$isStorageManager")
            // For gallery apps that need to access non-media folders, we need either media permissions OR storage manager access
            (imagesPermission && videosPermission) || isStorageManager
        } else if (sdkInt >= android.os.Build.VERSION_CODES.R) {
            val isStorageManager = android.os.Environment.isExternalStorageManager()
            val hasReadStorage = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
            android.util.Log.d("MainActivity", "Android 11+: StorageManager=$isStorageManager, ReadStorage=$hasReadStorage")
            isStorageManager || hasReadStorage
        } else {
            val hasReadStorage = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
            android.util.Log.d("MainActivity", "Android <11: ReadStorage=$hasReadStorage")
            hasReadStorage
        }
    }
    
    private fun requestStoragePermissions() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            // For Android 13+, we need to handle both media permissions and full storage access
            val imagesPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED
            val videosPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED
            val isStorageManager = android.os.Environment.isExternalStorageManager()
            
            if (!imagesPermission || !videosPermission) {
                // First try to get media permissions
                android.util.Log.d("MainActivity", "Requesting Android 13+ media permissions")
                requestRegularPermissions()
            } else if (!isStorageManager) {
                // If media permissions are granted but we still can't access non-media folders,
                // offer to request MANAGE_EXTERNAL_STORAGE for full access
                android.util.Log.d("MainActivity", "Media permissions granted, requesting full storage access for non-media folders")
                try {
                    val intent = android.content.Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                    intent.data = android.net.Uri.parse("package:$packageName")
                    startActivity(intent)
                } catch (e: Exception) {
                    // Continue with media permissions only
                    loadMediaFolders()
                }
            } else {
                loadMediaFolders()
            }
        } else if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            // For Android 11-12, request MANAGE_EXTERNAL_STORAGE for full access
            if (!android.os.Environment.isExternalStorageManager()) {
                try {
                    android.util.Log.d("MainActivity", "Requesting MANAGE_EXTERNAL_STORAGE permission")
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