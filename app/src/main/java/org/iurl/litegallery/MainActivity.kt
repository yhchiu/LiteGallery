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
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.GridLayoutManager
import org.iurl.litegallery.databinding.ActivityMainBinding
import org.iurl.litegallery.theme.ThemeColorResolver
import org.iurl.litegallery.theme.ThemeVariant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityMainBinding
    private lateinit var folderAdapter: FolderAdapter
    private lateinit var headerAdapter: HomeOverviewAdapter
    private lateinit var mediaScanner: MediaScanner
    private var permissionsGrantedOnStart = false
    private var isLoadingFolders = false
    private var lastUserRefreshAtMs = 0L
    private var currentHomeSortOrder: String = HomeFolderSorter.DEFAULT_SORT_ORDER
    private var currentHomeFolders: List<MediaFolder> = emptyList()
    
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        if (hasStoragePermissions()) {
            permissionsGrantedOnStart = true
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
        // Apply theme pack before setting content view
        ThemeHelper.applyTheme(this)
        ThemeHelper.applyPackTheme(this, ThemeVariant.NoActionBar)

        super.onCreate(savedInstanceState)
        ThemeHelper.captureCustomThemeGeneration(this)
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
        ThemeHelper.applyRuntimeCustomColors(this)
        
        // Track current pack so onResume can detect changes from Settings/Picker
        currentPackKey = ThemeHelper.getCurrentPack(this).key

        mediaScanner = MediaScanner(this)
        loadHomeSortOrderPreference()
        setupRecyclerView()
        setupSwipeRefresh()
        
        checkPermissionsAndLoad()
    }
    
    private var currentPackKey: String? = null

    override fun onResume() {
        super.onResume()
        // Apply theme in case it was changed in settings
        ThemeHelper.applyTheme(this)

        // Recreate if the pack changed (e.g., user picked a new pack from settings)
        val newPackKey = ThemeHelper.getCurrentPack(this).key
        if (currentPackKey != null && currentPackKey != newPackKey) {
            recreate()
            return
        }
        currentPackKey = newPackKey
        if (ThemeHelper.checkAndRecreateForCustomThemeChange(this)) return

        val homeSortOrderChanged = loadHomeSortOrderPreference()
        if (homeSortOrderChanged) {
            updateHomeSortIndicator()
            if (::folderAdapter.isInitialized && currentHomeFolders.isNotEmpty()) {
                folderAdapter.submitList(sortHomeFolders())
            }
        }

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
        headerAdapter = HomeOverviewAdapter(
            onSortClick = { showHomeSortDialog() }
        )
        updateHomeSortIndicator()

        folderAdapter = FolderAdapter { folder ->
            if (SmbPath.isSmb(folder.path)) {
                startActivity(Intent(this, SmbBrowseActivity::class.java))
            } else {
                val intent = Intent(this, FolderViewActivity::class.java).apply {
                    putExtra(FolderViewActivity.EXTRA_FOLDER_PATH, folder.path)
                    putExtra(FolderViewActivity.EXTRA_FOLDER_NAME, folder.name)
                }
                folderViewLauncher.launch(intent)
            }
        }

        val gridManager = GridLayoutManager(this@MainActivity, GRID_SPAN_COUNT).apply {
            spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
                override fun getSpanSize(position: Int): Int {
                    return if (position < headerAdapter.itemCount) GRID_SPAN_COUNT else 1
                }
            }
        }

        binding.recyclerView.apply {
            adapter = ConcatAdapter(headerAdapter, folderAdapter)
            layoutManager = gridManager
        }
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefresh.setOnRefreshListener {
            refreshFromUserAction(fromSwipeRefresh = true)
        }
        binding.swipeRefresh.setColorSchemeColors(
            ThemeColorResolver.resolveColor(this, com.google.android.material.R.attr.colorPrimary),
        )
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
        val sdkInt = android.os.Build.VERSION.SDK_INT
        val permissions = when {
            sdkInt >= android.os.Build.VERSION_CODES.TIRAMISU -> {
                arrayOf(
                    Manifest.permission.READ_MEDIA_IMAGES,
                    Manifest.permission.READ_MEDIA_VIDEO
                )
            }
            sdkInt >= android.os.Build.VERSION_CODES.Q -> {
                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
            else -> {
                arrayOf(
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                )
            }
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
                val scannedFolders = mediaScanner.scanMediaFolders()

                // Add SMB virtual folder, then sort through the same path as user-initiated changes.
                val smbServerCount = withContext(Dispatchers.IO) {
                    SmbConfigStore.getAllServers(this@MainActivity).size
                }
                currentHomeFolders = scannedFolders + MediaFolder(
                    name = getString(R.string.smb_browse_title),
                    path = "smb://",
                    itemCount = smbServerCount,
                    thumbnail = null
                )
                val folders = sortHomeFolders()
                
                if (folders.isEmpty()) {
                    binding.progressBar.visibility = View.GONE
                    binding.emptyView.visibility = View.VISIBLE
                    binding.recyclerView.visibility = View.GONE
                } else {
                    headerAdapter.submitStats(buildOverviewStats(scannedFolders))
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

    private fun showHomeSortDialog() {
        val sortOptions = arrayOf(
            getString(R.string.sort_by_date_desc),
            getString(R.string.sort_by_date_asc),
            getString(R.string.sort_by_name_asc),
            getString(R.string.sort_by_name_desc),
            getString(R.string.sort_by_size_desc),
            getString(R.string.sort_by_size_asc)
        )
        val sortValues = arrayOf(
            HomeFolderSorter.SORT_DATE_DESC,
            HomeFolderSorter.SORT_DATE_ASC,
            HomeFolderSorter.SORT_NAME_ASC,
            HomeFolderSorter.SORT_NAME_DESC,
            HomeFolderSorter.SORT_SIZE_DESC,
            HomeFolderSorter.SORT_SIZE_ASC
        )
        val currentIndex = sortValues.indexOf(currentHomeSortOrder).takeIf { it >= 0 } ?: 0

        android.app.AlertDialog.Builder(this)
            .setTitle(R.string.sort)
            .setSingleChoiceItems(sortOptions, currentIndex) { dialog, which ->
                currentHomeSortOrder = HomeFolderSorter.parseSortOrder(sortValues[which])
                persistHomeSortOrder()
                updateHomeSortIndicator()
                folderAdapter.submitList(sortHomeFolders()) {
                    binding.recyclerView.scrollToPosition(0)
                }
                dialog.dismiss()
            }
            .setNegativeButton(R.string.cancel, null)
            .showThemed()
    }

    private fun android.app.AlertDialog.Builder.showThemed(): android.app.AlertDialog {
        val dialog = create()
        dialog.show()
        ThemeHelper.applyRuntimeCustomColors(dialog)
        return dialog
    }

    private fun sortHomeFolders(): List<MediaFolder> {
        return HomeFolderSorter.sort(currentHomeFolders, currentHomeSortOrder)
    }

    private fun loadHomeSortOrderPreference(): Boolean {
        val previousSortOrder = currentHomeSortOrder
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        currentHomeSortOrder = HomeFolderSorter.parseSortOrder(
            prefs.getString(PREF_HOME_FOLDER_SORT_ORDER, HomeFolderSorter.DEFAULT_SORT_ORDER)
        )
        return currentHomeSortOrder != previousSortOrder
    }

    private fun persistHomeSortOrder() {
        PreferenceManager.getDefaultSharedPreferences(this)
            .edit()
            .putString(PREF_HOME_FOLDER_SORT_ORDER, currentHomeSortOrder)
            .apply()
    }

    private fun updateHomeSortIndicator() {
        val label = homeSortOrderLabel()
        headerAdapter.submitSortIndicator(
            label = label,
            contentDescription = getString(R.string.folder_sort_content_description, label)
        )
    }

    private fun homeSortOrderLabel(sortOrder: String = currentHomeSortOrder): String {
        return when (sortOrder) {
            HomeFolderSorter.SORT_DATE_DESC -> getString(R.string.folder_sort_chip_date_desc)
            HomeFolderSorter.SORT_DATE_ASC -> getString(R.string.folder_sort_chip_date_asc)
            HomeFolderSorter.SORT_NAME_ASC -> getString(R.string.folder_sort_chip_name_asc)
            HomeFolderSorter.SORT_NAME_DESC -> getString(R.string.folder_sort_chip_name_desc)
            HomeFolderSorter.SORT_SIZE_DESC -> getString(R.string.folder_sort_chip_size_desc)
            HomeFolderSorter.SORT_SIZE_ASC -> getString(R.string.folder_sort_chip_size_asc)
            else -> getString(R.string.folder_sort_chip_date_desc)
        }
    }

    private fun buildOverviewStats(scannedFolders: List<MediaFolder>): OverviewStats {
        if (scannedFolders.isEmpty()) return OverviewStats.EMPTY
        var photos = 0
        var videos = 0
        var size = 0L
        for (folder in scannedFolders) {
            photos += folder.imageCount
            videos += folder.videoCount
            size += folder.totalSizeBytes
        }
        return OverviewStats(
            totalItems = photos + videos,
            totalPhotos = photos,
            totalVideos = videos,
            totalFolders = scannedFolders.size,
            totalSizeBytes = size
        )
    }

    companion object {
        private const val REFRESH_THROTTLE_MS = 1_200L
        private const val GRID_SPAN_COUNT = 2
        private const val PREF_HOME_FOLDER_SORT_ORDER = "home_folder_sort_order"
    }

    private fun canUseAdvancedAllFilesAccess(): Boolean {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.R) return false
        return android.os.Environment.isExternalStorageManager()
    }
}
