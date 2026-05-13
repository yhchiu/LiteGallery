package org.iurl.litegallery

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.SystemClock
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.GridLayoutManager
import org.iurl.litegallery.databinding.ActivityMainBinding
import org.iurl.litegallery.theme.ThemeColorResolver
import org.iurl.litegallery.theme.ThemeVariant
import com.google.android.material.datepicker.MaterialDatePicker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityMainBinding
    private lateinit var folderAdapter: FolderAdapter
    private lateinit var headerAdapter: HomeOverviewAdapter
    private lateinit var folderConcatAdapter: ConcatAdapter
    private lateinit var mediaScanner: MediaScanner
    private var permissionsGrantedOnStart = false
    private var isLoadingFolders = false
    private var lastUserRefreshAtMs = 0L
    private var currentHomeSortOrder: String = HomeFolderSorter.DEFAULT_SORT_ORDER
    private var currentHomeFolders: List<MediaFolder> = emptyList()
    private var mediaIndexSyncJob: Job? = null
    private var homeTransformJob: Job? = null
    private var searchMenuItem: MenuItem? = null
    private var searchView: SearchView? = null
    private var suppressSearchEvents = false
    private var currentHomeSearchName = ""
    private var currentHomeFilters = HomeFilterState()
    private var homeDisplayGeneration = 0

    private data class HomeFilterState(
        val typeFilter: MediaTypeFilter = MediaTypeFilter.ALL,
        val dateRange: TimeRange? = null,
        val sizeRangeBytes: LongRange? = null
    )
    
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
        collapseHomeSearchIfNeeded()
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
        collapseHomeSearchIfNeeded()
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
        setupSearchFilters()
        
        checkPermissionsAndLoad()
    }

    override fun onDestroy() {
        homeTransformJob?.cancel()
        mediaIndexSyncJob?.cancel()
        super.onDestroy()
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

        collapseHomeSearchIfNeeded()

        val homeSortOrderChanged = loadHomeSortOrderPreference()
        if (homeSortOrderChanged) {
            updateHomeSortIndicator()
            if (::folderAdapter.isInitialized && currentHomeFolders.isNotEmpty()) {
                applyHomeDisplayTransform(scrollToTop = false)
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
        } else if (currentHomeFolders.isEmpty()) {
            loadMediaFolders()
            cleanupExpiredTrashInBackground()
        } else {
            synchronizeMediaIndexInBackground()
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
        searchMenuItem = menu.findItem(R.id.action_search)
        setupSearchMenuItem(searchMenuItem)
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

        folderConcatAdapter = ConcatAdapter(headerAdapter, folderAdapter)
        showFolderAdapter()
    }

    private fun showFolderAdapter() {
        if (binding.recyclerView.adapter === folderConcatAdapter) return
        val gridManager = GridLayoutManager(this@MainActivity, GRID_SPAN_COUNT).apply {
            spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
                override fun getSpanSize(position: Int): Int {
                    return if (position < headerAdapter.itemCount) GRID_SPAN_COUNT else 1
                }
            }
        }

        binding.recyclerView.apply {
            adapter = folderConcatAdapter
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

    private fun setupSearchMenuItem(item: MenuItem?) {
        val view = item?.actionView as? SearchView ?: return
        searchView = view
        view.queryHint = getString(R.string.search_hint_global_media)
        view.maxWidth = Int.MAX_VALUE
        view.setIconifiedByDefault(false)
        view.setSubmitButtonEnabled(false)
        view.imeOptions = EditorInfo.IME_ACTION_SEARCH
        view.findViewById<View>(androidx.appcompat.R.id.search_mag_icon)?.setOnClickListener {
            submitHomeSearchFromView(view)
        }
        view.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                if (suppressSearchEvents) return true
                submitHomeSearchFromView(view)
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                if (suppressSearchEvents) return true
                currentHomeSearchName = newText.orEmpty()
                updateHomeSearchChips()
                return true
            }
        })
        view.setOnCloseListener {
            searchMenuItem?.collapseActionView()
            true
        }
        item.setOnActionExpandListener(object : MenuItem.OnActionExpandListener {
            override fun onMenuItemActionExpand(item: MenuItem): Boolean {
                binding.searchFilterRow.visibility = View.VISIBLE
                updateHomeSearchChips()
                view.post {
                    view.requestFocus()
                    val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                    imm?.showSoftInput(view.findFocus() ?: view, InputMethodManager.SHOW_IMPLICIT)
                }
                return true
            }

            override fun onMenuItemActionCollapse(item: MenuItem): Boolean {
                binding.searchFilterRow.visibility = View.GONE
                clearHomeSearch(collapseSearchView = false)
                return true
            }
        })
    }

    private fun submitHomeSearchFromView(view: SearchView) {
        currentHomeSearchName = view.query?.toString().orEmpty()
        openGlobalSearchResultsIfReady()
        view.clearFocus()
    }

    private fun setupSearchFilters() {
        binding.searchTypeChip.setOnClickListener {
            val nextType = when (currentHomeFilters.typeFilter) {
                MediaTypeFilter.ALL -> MediaTypeFilter.IMAGES
                MediaTypeFilter.IMAGES -> MediaTypeFilter.VIDEOS
                MediaTypeFilter.VIDEOS -> MediaTypeFilter.ALL
            }
            currentHomeFilters = currentHomeFilters.copy(typeFilter = nextType)
            updateHomeSearchChips()
        }
        binding.searchDateChip.setOnClickListener {
            showHomeDateRangePicker()
        }
        binding.searchSizeChip.setOnClickListener {
            SearchFilterUi.showSizeRangeDialog(this, currentHomeFilters.sizeRangeBytes) { range ->
                currentHomeFilters = currentHomeFilters.copy(sizeRangeBytes = range)
                updateHomeSearchChips()
            }
        }
        binding.searchClearChip.setOnClickListener {
            clearHomeSearch(collapseSearchView = false)
        }
        binding.homeClearFiltersButton.setOnClickListener {
            clearHomeSearch(collapseSearchView = false)
        }
    }

    private fun showHomeDateRangePicker() {
        val picker = MaterialDatePicker.Builder.dateRangePicker()
            .setTitleText(getString(R.string.search_date_title))
            .build()
        picker.addOnPositiveButtonClickListener { selection ->
            val range = SearchDateRangeConverter.toTimeRangeOrNull(selection?.first, selection?.second)
            if (range == null) {
                android.widget.Toast.makeText(this, R.string.error, android.widget.Toast.LENGTH_SHORT).show()
            } else {
                currentHomeFilters = currentHomeFilters.copy(dateRange = range)
                updateHomeSearchChips()
            }
        }
        picker.show(supportFragmentManager, "home_search_date")
    }

    private fun clearHomeSearch(collapseSearchView: Boolean) {
        currentHomeSearchName = ""
        currentHomeFilters = HomeFilterState()

        suppressSearchEvents = true
        searchView?.setQuery("", false)
        searchView?.clearFocus()
        suppressSearchEvents = false

        updateHomeSearchChips()
        if (collapseSearchView) {
            searchMenuItem?.collapseActionView()
        }
    }

    private fun buildGlobalMediaQueryOrNull(normalizedName: String): MediaSearchQuery? {
        return HomeSearchQueryFactory.buildGlobalMediaQuery(
            normalizedName = normalizedName,
            typeFilter = currentHomeFilters.typeFilter,
            dateRange = currentHomeFilters.dateRange,
            sizeRangeBytes = currentHomeFilters.sizeRangeBytes
        )
    }

    private fun updateHomeSearchChips() {
        binding.searchTypeChip.text = when (currentHomeFilters.typeFilter) {
            MediaTypeFilter.ALL -> getString(R.string.search_chip_type_all)
            MediaTypeFilter.IMAGES -> getString(R.string.search_chip_type_images)
            MediaTypeFilter.VIDEOS -> getString(R.string.search_chip_type_videos)
        }
        binding.searchDateChip.text = SearchFilterUi.formatDateRange(this, currentHomeFilters.dateRange)
        binding.searchSizeChip.text = SearchFilterUi.formatSizeRange(this, currentHomeFilters.sizeRangeBytes)
        binding.searchClearChip.visibility = if (hasAnyHomeSearchInput()) View.VISIBLE else View.GONE
    }

    private fun hasAnyHomeSearchInput(): Boolean =
        currentHomeSearchName.isNotBlank() ||
            currentHomeFilters != HomeFilterState()

    private fun openGlobalSearchResultsIfReady() {
        val normalizedName = NameMatcher.normalizePattern(currentHomeSearchName)
        val query = buildGlobalMediaQueryOrNull(normalizedName)
        if (query == null) {
            android.widget.Toast.makeText(
                this,
                R.string.search_global_media_prompt_subtitle,
                android.widget.Toast.LENGTH_SHORT
            ).show()
            return
        }

        val intent = GlobalSearchResultsActivity.createIntent(
            context = this,
            nameQuery = currentHomeSearchName,
            typeFilter = currentHomeFilters.typeFilter,
            dateRange = currentHomeFilters.dateRange,
            sizeRangeBytes = currentHomeFilters.sizeRangeBytes
        )
        folderViewLauncher.launch(intent)
    }

    private fun collapseHomeSearchIfNeeded() {
        val shouldCollapse = searchMenuItem?.isActionViewExpanded == true ||
            binding.searchFilterRow.visibility == View.VISIBLE ||
            hasAnyHomeSearchInput()
        if (!shouldCollapse) return
        val item = searchMenuItem
        if (item?.isActionViewExpanded == true) {
            item.collapseActionView()
        } else {
            binding.searchFilterRow.visibility = View.GONE
            clearHomeSearch(collapseSearchView = false)
        }
        restoreHomeSearchMenuIcon()
    }

    private fun restoreHomeSearchMenuIcon() {
        searchMenuItem?.isVisible = true
        binding.toolbar.post {
            searchMenuItem?.isVisible = true
            invalidateOptionsMenu()
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

        val showBlockingLoading = !fromSwipeRefresh && currentHomeFolders.isEmpty()
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
                val folders = buildHomeDisplayFolders()
                
                headerAdapter.submitStats(buildOverviewStats(scannedFolders))
                renderHomeFolders(folders)
                binding.progressBar.visibility = View.GONE
            } catch (e: Exception) {
                binding.progressBar.visibility = View.GONE
                showHomeEmptyState(noResultsFromSearch = false)
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
        binding.homeEmptyTitleTextView.setText(R.string.permission_required)
        binding.homeEmptySubtitleTextView.setText(R.string.grant_permission)
        binding.homeClearFiltersButton.visibility = View.GONE
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

    private fun synchronizeMediaIndexInBackground() {
        if (!::mediaScanner.isInitialized || isLoadingFolders) return
        if (mediaIndexSyncJob?.isActive == true) return

        mediaIndexSyncJob = lifecycleScope.launch {
            runCatching { mediaScanner.synchronizeMediaIndexIfNeeded() }
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
                applyHomeDisplayTransform(scrollToTop = true)
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

    private fun applyHomeDisplayTransform(scrollToTop: Boolean) {
        homeTransformJob?.cancel()
        val generation = ++homeDisplayGeneration

        val sourceFolders = currentHomeFolders
        val sortOrder = currentHomeSortOrder
        binding.progressBar.visibility = View.GONE
        showFolderAdapter()

        if (sourceFolders.size > HOME_BACKGROUND_THRESHOLD) {
            homeTransformJob = lifecycleScope.launch {
                val folders = withContext(Dispatchers.Default) {
                    buildHomeDisplayFolders(sourceFolders, sortOrder)
                }
                if (generation != homeDisplayGeneration) return@launch
                renderHomeFolders(folders, scrollToTop)
            }
        } else {
            renderHomeFolders(buildHomeDisplayFolders(sourceFolders, sortOrder), scrollToTop)
        }
    }

    private fun buildHomeDisplayFolders(
        sourceFolders: List<MediaFolder> = currentHomeFolders,
        sortOrder: String = currentHomeSortOrder
    ): List<MediaFolder> {
        return HomeFolderSorter.sort(sourceFolders, sortOrder)
    }

    private fun renderHomeFolders(folders: List<MediaFolder>, scrollToTop: Boolean = false) {
        showFolderAdapter()
        binding.progressBar.visibility = View.GONE
        if (folders.isEmpty()) {
            showHomeEmptyState(noResultsFromSearch = false)
            binding.recyclerView.visibility = View.GONE
            return
        }

        binding.emptyView.visibility = View.GONE
        binding.recyclerView.visibility = View.VISIBLE
        folderAdapter.submitList(folders) {
            if (scrollToTop) binding.recyclerView.scrollToPosition(0)
        }
    }

    private fun showHomeEmptyState(noResultsFromSearch: Boolean) {
        binding.emptyView.setOnClickListener(null)
        binding.homeEmptyTitleTextView.setText(
            if (noResultsFromSearch) R.string.search_no_folders else R.string.no_media_folders
        )
        binding.homeEmptySubtitleTextView.setText(
            if (noResultsFromSearch) R.string.search_clear_filters else R.string.tap_to_scan_media
        )
        binding.homeClearFiltersButton.visibility = if (noResultsFromSearch) View.VISIBLE else View.GONE
        binding.emptyView.visibility = View.VISIBLE
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
        private const val HOME_BACKGROUND_THRESHOLD = 500
        private const val GRID_SPAN_COUNT = 2
        private const val PREF_HOME_FOLDER_SORT_ORDER = "home_folder_sort_order"
    }

    private fun canUseAdvancedAllFilesAccess(): Boolean {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.R) return false
        return android.os.Environment.isExternalStorageManager()
    }
}
