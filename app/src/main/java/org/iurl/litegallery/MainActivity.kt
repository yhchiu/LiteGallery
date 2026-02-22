package org.iurl.litegallery

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.SystemClock
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import org.iurl.litegallery.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityMainBinding
    private lateinit var folderAdapter: FolderAdapter
    private lateinit var mediaScanner: MediaScanner
    private var permissionsGrantedOnStart = false
    private var isLoadingFolders = false
    private var lastUserRefreshAtMs = 0L
    
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.all { it.value }) {
            loadMediaFolders()
        } else {
            showPermissionRequired()
        }
    }

    private val folderViewLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val data = result.data
        val hasMediaChanged = result.resultCode == RESULT_OK &&
            data?.getBooleanExtra(MediaViewerActivity.RESULT_MEDIA_CHANGED, false) == true
        if (hasMediaChanged && hasStoragePermissions()) {
            loadMediaFolders()
        }
    }

    private val trashBinLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val data = result.data
        val hasMediaChanged = result.resultCode == RESULT_OK &&
            data?.getBooleanExtra(MediaViewerActivity.RESULT_MEDIA_CHANGED, false) == true
        if (hasMediaChanged && hasStoragePermissions()) {
            loadMediaFolders(showBlockingLoading = false)
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
        setupSwipeRefresh()
        
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
            cleanupExpiredTrashInBackground()
        } else {
            cleanupExpiredTrashInBackground()
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
            R.id.action_trash_bin -> {
                trashBinLauncher.launch(Intent(this, TrashBinActivity::class.java))
                true
            }
            R.id.action_refresh -> {
                refreshFromUserAction(fromSwipeRefresh = false)
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
            folderViewLauncher.launch(intent)
        }
        
        binding.recyclerView.apply {
            adapter = folderAdapter
            layoutManager = GridLayoutManager(this@MainActivity, 1)
        }
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefresh.setOnRefreshListener {
            refreshFromUserAction(fromSwipeRefresh = true)
        }
    }

    private fun refreshFromUserAction(fromSwipeRefresh: Boolean) {
        if (!hasStoragePermissions()) {
            if (fromSwipeRefresh) {
                binding.swipeRefresh.isRefreshing = false
            }
            showPermissionRequired()
            return
        }

        val now = SystemClock.elapsedRealtime()
        if (now - lastUserRefreshAtMs < REFRESH_THROTTLE_MS) {
            if (fromSwipeRefresh) {
                binding.swipeRefresh.isRefreshing = false
            }
            return
        }
        lastUserRefreshAtMs = now

        val showBlockingLoading = !fromSwipeRefresh && folderAdapter.itemCount == 0
        loadMediaFolders(showBlockingLoading = showBlockingLoading, fromSwipeRefresh = fromSwipeRefresh)
    }
    
    private fun hasStoragePermissions(): Boolean {
        val sdkInt = android.os.Build.VERSION.SDK_INT
        android.util.Log.d("MainActivity", "Checking permissions for SDK $sdkInt")
        
        return if (sdkInt >= android.os.Build.VERSION_CODES.TIRAMISU) {
            val imagesPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED
            val videosPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED
            val canUseAllFilesAccess = canUseAdvancedAllFilesAccess()
            android.util.Log.d("MainActivity", "Android 13+: Images=$imagesPermission, Videos=$videosPermission, AdvancedAllFiles=$canUseAllFilesAccess")
            (imagesPermission && videosPermission) || canUseAllFilesAccess
        } else if (sdkInt >= android.os.Build.VERSION_CODES.R) {
            val hasReadStorage = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
            val canUseAllFilesAccess = canUseAdvancedAllFilesAccess()
            android.util.Log.d("MainActivity", "Android 11+: ReadStorage=$hasReadStorage, AdvancedAllFiles=$canUseAllFilesAccess")
            hasReadStorage || canUseAllFilesAccess
        } else {
            val hasReadStorage = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
            android.util.Log.d("MainActivity", "Android <11: ReadStorage=$hasReadStorage")
            hasReadStorage
        }
    }
    
    private fun requestStoragePermissions() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            // Android 13+: request media-only permissions by default.
            val imagesPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED
            val videosPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED
            
            if (!imagesPermission || !videosPermission) {
                android.util.Log.d("MainActivity", "Requesting Android 13+ media permissions")
                requestRegularPermissions()
            } else {
                loadMediaFolders()
            }
        } else if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            val hasReadStorage = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
            if (!hasReadStorage) {
                requestRegularPermissions()
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
    
    private fun loadMediaFolders(
        showBlockingLoading: Boolean = true,
        fromSwipeRefresh: Boolean = false
    ) {
        if (isLoadingFolders) {
            if (fromSwipeRefresh) {
                binding.swipeRefresh.isRefreshing = false
            }
            return
        }

        if (showBlockingLoading) {
            binding.progressBar.visibility = View.VISIBLE
            binding.emptyView.visibility = View.GONE
            binding.recyclerView.visibility = View.GONE
        } else if (fromSwipeRefresh) {
            binding.swipeRefresh.isRefreshing = true
        }

        isLoadingFolders = true
        
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
            } finally {
                isLoadingFolders = false
                binding.swipeRefresh.isRefreshing = false
            }
        }
    }
    
    private fun showPermissionRequired() {
        binding.swipeRefresh.isRefreshing = false
        binding.progressBar.visibility = View.GONE
        binding.emptyView.apply {
            visibility = View.VISIBLE
            setOnClickListener {
                requestStoragePermissions()
            }
        }
        binding.recyclerView.visibility = View.GONE
    }

    private fun cleanupExpiredTrashInBackground() {
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                TrashBinStore.cleanupExpiredTrash(this@MainActivity)
            }

            if (result.removedScannerPaths.isNotEmpty()) {
                result.removedScannerPaths.forEach { oldPath ->
                    android.media.MediaScannerConnection.scanFile(
                        this@MainActivity,
                        arrayOf(oldPath),
                        null
                    ) { _, _ -> }
                }
            }
        }
    }

    companion object {
        private const val REFRESH_THROTTLE_MS = 1_200L
    }

    private fun canUseAdvancedAllFilesAccess(): Boolean {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.R) return false
        return android.os.Environment.isExternalStorageManager()
    }
}
