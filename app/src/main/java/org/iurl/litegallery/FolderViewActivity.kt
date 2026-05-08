package org.iurl.litegallery

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.SystemClock
import android.text.format.Formatter
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.preference.PreferenceManager
import org.iurl.litegallery.databinding.ActivityFolderViewBinding
import org.iurl.litegallery.theme.GradientHelper
import org.iurl.litegallery.theme.ThemeColorResolver
import org.iurl.litegallery.theme.ThemeVariant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class FolderViewActivity : AppCompatActivity() {
    
    companion object {
        const val EXTRA_FOLDER_PATH = "extra_folder_path"
        const val EXTRA_FOLDER_NAME = "extra_folder_name"
        private const val SWIPE_REFRESH_THROTTLE_MS = 1_200L
        private const val PREF_DEFAULT_SORT_ORDER = "default_sort_order"
        private const val PREF_REMEMBER_FOLDER_SORT_ORDER = "remember_folder_sort_order"
        private const val PREF_LAST_FOLDER_SORT_ORDER = "last_folder_sort_order"
        private const val PREF_DEFAULT_VIEW_MODE = "default_view_mode"
        private const val PREF_REMEMBER_FOLDER_VIEW_MODE = "remember_folder_view_mode"
        private const val PREF_LAST_FOLDER_VIEW_MODE = "last_folder_view_mode"
        private const val PREF_DEFAULT_GROUP_BY = "default_group_by"
        private const val PREF_REMEMBER_FOLDER_GROUP_BY = "remember_folder_group_by"
        private const val PREF_LAST_FOLDER_GROUP_BY = "last_folder_group_by"
    }
    
    private lateinit var binding: ActivityFolderViewBinding
    private lateinit var mediaAdapter: MediaAdapter
    private lateinit var groupedMediaAdapter: GroupedMediaAdapter
    private lateinit var mediaScanner: MediaScanner
    
    private var folderPath: String = ""
    private var folderName: String = ""
    private var mediaItems: List<MediaItem> = emptyList()
    private var currentPackKey: String? = null
    private var currentViewMode: MediaAdapter.ViewMode = MediaAdapter.ViewMode.GRID
    private var currentSortOrder: String = "date_desc"
    private var currentGroupBy: FolderGroupBy = FolderGroupBy.NONE
    private var sortMenuItem: MenuItem? = null
    private var isLoadingMediaItems = false
    private var lastSwipeRefreshAtMs = 0L
    private var transformJob: Job? = null
    private var displayGeneration = 0

    private val mediaViewerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val data = result.data
        val hasMediaChanged = result.resultCode == RESULT_OK &&
            data?.getBooleanExtra(MediaViewerActivity.RESULT_MEDIA_CHANGED, false) == true
        if (!hasMediaChanged) return@registerForActivityResult

        val changedFolderPath = data?.getStringExtra(MediaViewerActivity.RESULT_FOLDER_PATH)
        if (changedFolderPath.isNullOrEmpty() || changedFolderPath == folderPath) {
            loadMediaItems(showBlockingLoading = displayedItemCount == 0)
        }

        setResult(
            RESULT_OK,
            Intent().apply {
                putExtra(MediaViewerActivity.RESULT_MEDIA_CHANGED, true)
                putExtra(MediaViewerActivity.RESULT_FOLDER_PATH, changedFolderPath ?: folderPath)
            }
        )
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        // Apply theme pack before setting content view
        ThemeHelper.applyTheme(this)
        ThemeHelper.applyPackTheme(this, ThemeVariant.NoActionBar)

        super.onCreate(savedInstanceState)
        ThemeHelper.captureCustomThemeGeneration(this)
        binding = ActivityFolderViewBinding.inflate(layoutInflater)
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

        folderPath = intent.getStringExtra(EXTRA_FOLDER_PATH) ?: ""
        folderName = intent.getStringExtra(EXTRA_FOLDER_NAME) ?: ""
        
        // Track current pack so onResume can detect changes from Settings/Picker
        currentPackKey = ThemeHelper.getCurrentPack(this).key

        // Load default view mode and sort order from preferences
        loadViewModePreference()
        loadSortOrderPreference()
        loadGroupByPreference()

        setupToolbar()
        setupRecyclerView()
        setupSwipeRefresh()
        ThemeHelper.applyRuntimeCustomColors(this)
        applyFolderHeroGradientAccent()
        binding.root.post { applyFolderHeroGradientAccent() }

        mediaScanner = MediaScanner(this)
        loadMediaItems()
    }

    override fun onDestroy() {
        transformJob?.cancel()
        super.onDestroy()
    }
    
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
        applyFolderHeroGradientAccent()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.folder_view_menu, menu)
        sortMenuItem = menu.findItem(R.id.action_sort)
        updateSortIndicator()
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
                showSortDialog()
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
            // Title lives in the hero block below the toolbar, not the toolbar itself.
            title = ""
        }
        binding.folderTitleTextView.text = folderName
        binding.folderHeroDivider.visibility = View.GONE
        binding.folderStatsTextView.visibility = View.GONE
        binding.groupStatusChip.setOnClickListener { showGroupDialog() }
        binding.sortStatusChip.setOnClickListener { showSortDialog() }
        updateGroupIndicator()
        updateSortIndicator()
    }

    private fun applyFolderHeroGradientAccent() {
        val gradient = GradientHelper.createForCurrentPack(this, binding.heroContainer.radius) ?: return
        val onGradient = ThemeColorResolver.resolveColor(
            this,
            com.google.android.material.R.attr.colorOnPrimary,
            Color.WHITE,
        )

        binding.folderHeroInnerLayout.background = gradient
        binding.folderHeroInnerLayout.setTag(R.id.tag_custom_theme_skip_subtree, true)
        binding.heroContainer.strokeWidth = 0
        binding.heroContainer.setCardBackgroundColor(Color.TRANSPARENT)
        binding.folderHeroDivider.setBackgroundColor(onGradient.withAlpha(0x33))
        binding.folderEyebrowTextView.setTextColor(onGradient)
        binding.folderTitleTextView.setTextColor(onGradient)
        binding.folderStatsTextView.setTextColor(onGradient)
    }

    private fun Int.withAlpha(alpha: Int): Int =
        (this and 0x00FFFFFF) or (alpha.coerceIn(0, 255) shl 24)

    private fun bindFolderStats(items: List<MediaItem>) {
        if (items.isEmpty()) {
            binding.folderHeroDivider.visibility = View.GONE
            binding.folderStatsTextView.visibility = View.GONE
            return
        }
        val parts = mutableListOf(getString(R.string.items_count, items.size))
        val totalSize = items.sumOf { it.size.coerceAtLeast(0L) }
        if (totalSize > 0L) {
            parts.add(Formatter.formatShortFileSize(this, totalSize))
        }
        val videoCount = items.count { it.isVideo }
        if (videoCount > 0) {
            parts.add(getString(R.string.folder_stat_videos_format, videoCount))
        }
        binding.folderStatsTextView.text = parts.joinToString(" · ")
        binding.folderHeroDivider.visibility = View.VISIBLE
        binding.folderStatsTextView.visibility = View.VISIBLE
    }
    
    private fun setupRecyclerView() {
        mediaAdapter = MediaAdapter(
            onMediaClick = { mediaItem, position -> openMediaViewer(mediaItem, position) }
        )
        groupedMediaAdapter = GroupedMediaAdapter(
            onMediaClick = { mediaItem, mediaIndex -> openMediaViewer(mediaItem, mediaIndex) }
        )

        binding.recyclerView.adapter = if (currentGroupBy == FolderGroupBy.NONE) {
            mediaAdapter
        } else {
            groupedMediaAdapter
        }

        // Apply view mode
        updateLayoutManager()
    }

    private val displayedItemCount: Int
        get() {
            if (!::mediaAdapter.isInitialized || !::groupedMediaAdapter.isInitialized) return 0
            return when (binding.recyclerView.adapter) {
                groupedMediaAdapter -> groupedMediaAdapter.itemCount
                mediaAdapter -> mediaAdapter.itemCount
                else -> 0
            }
        }

    private fun openMediaViewer(mediaItem: MediaItem, position: Int) {
        val intent = Intent(this, MediaViewerActivity::class.java).apply {
            putExtra(MediaViewerActivity.EXTRA_MEDIA_PATH, mediaItem.path)
            putExtra(MediaViewerActivity.EXTRA_FOLDER_PATH, folderPath)
            putExtra(MediaViewerActivity.EXTRA_CURRENT_POSITION, position)
        }
        mediaViewerLauncher.launch(intent)
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefresh.setOnRefreshListener {
            val now = SystemClock.elapsedRealtime()
            if (now - lastSwipeRefreshAtMs < SWIPE_REFRESH_THROTTLE_MS) {
                binding.swipeRefresh.isRefreshing = false
                return@setOnRefreshListener
            }
            lastSwipeRefreshAtMs = now

            loadMediaItems(showBlockingLoading = false, fromSwipeRefresh = true)
        }
        binding.swipeRefresh.setColorSchemeColors(
            ThemeColorResolver.resolveColor(this, com.google.android.material.R.attr.colorPrimary),
        )
    }

    private fun updateLayoutManager() {
        binding.recyclerView.layoutManager = when (currentViewMode) {
            MediaAdapter.ViewMode.GRID -> GridLayoutManager(this, 3).apply {
                spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
                    override fun getSpanSize(position: Int): Int {
                        return if (
                            binding.recyclerView.adapter == groupedMediaAdapter &&
                            groupedMediaAdapter.isHeaderPosition(position)
                        ) {
                            spanCount
                        } else {
                            1
                        }
                    }
                }
            }
            MediaAdapter.ViewMode.LIST, MediaAdapter.ViewMode.DETAILED -> LinearLayoutManager(this)
        }
        mediaAdapter.viewMode = currentViewMode
        groupedMediaAdapter.viewMode = currentViewMode
    }

    private fun loadViewModePreference() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val defaultViewMode = prefs.getString(PREF_DEFAULT_VIEW_MODE, "grid") ?: "grid"
        val rememberViewMode = prefs.getBoolean(PREF_REMEMBER_FOLDER_VIEW_MODE, false)
        val resolvedViewMode = if (rememberViewMode) {
            prefs.getString(PREF_LAST_FOLDER_VIEW_MODE, defaultViewMode)
        } else {
            defaultViewMode
        }
        currentViewMode = parseViewModePreference(resolvedViewMode)

        // Initialize remembered value when feature is enabled for the first time.
        if (rememberViewMode && !prefs.contains(PREF_LAST_FOLDER_VIEW_MODE)) {
            prefs.edit().putString(PREF_LAST_FOLDER_VIEW_MODE, viewModeToPreferenceValue(currentViewMode)).apply()
        }
    }

    private fun toggleViewMode() {
        currentViewMode = when (currentViewMode) {
            MediaAdapter.ViewMode.GRID -> MediaAdapter.ViewMode.LIST
            MediaAdapter.ViewMode.LIST -> MediaAdapter.ViewMode.DETAILED
            MediaAdapter.ViewMode.DETAILED -> MediaAdapter.ViewMode.GRID
        }
        updateLayoutManager()
        maybeReloadForDetailedMode()
        persistCurrentViewModeIfNeeded()

        // Show toast to indicate current view mode
        val modeName = when (currentViewMode) {
            MediaAdapter.ViewMode.GRID -> getString(R.string.thumbnail_view)
            MediaAdapter.ViewMode.LIST -> getString(R.string.list_view)
            MediaAdapter.ViewMode.DETAILED -> getString(R.string.detailed_view)
        }
        android.widget.Toast.makeText(this, modeName, android.widget.Toast.LENGTH_SHORT).show()
    }

    private fun parseViewModePreference(viewModeValue: String?): MediaAdapter.ViewMode {
        return when (viewModeValue) {
            "list" -> MediaAdapter.ViewMode.LIST
            "detailed" -> MediaAdapter.ViewMode.DETAILED
            else -> MediaAdapter.ViewMode.GRID
        }
    }

    private fun viewModeToPreferenceValue(viewMode: MediaAdapter.ViewMode): String {
        return when (viewMode) {
            MediaAdapter.ViewMode.GRID -> "grid"
            MediaAdapter.ViewMode.LIST -> "list"
            MediaAdapter.ViewMode.DETAILED -> "detailed"
        }
    }

    private fun persistCurrentViewModeIfNeeded() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        if (!prefs.getBoolean(PREF_REMEMBER_FOLDER_VIEW_MODE, false)) return
        prefs.edit().putString(PREF_LAST_FOLDER_VIEW_MODE, viewModeToPreferenceValue(currentViewMode)).apply()
    }

    private fun loadSortOrderPreference() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val defaultSortOrder = parseSortOrderPreference(
            prefs.getString(PREF_DEFAULT_SORT_ORDER, "date_desc")
        )
        val rememberSortOrder = prefs.getBoolean(PREF_REMEMBER_FOLDER_SORT_ORDER, false)
        currentSortOrder = if (rememberSortOrder) {
            parseSortOrderPreference(prefs.getString(PREF_LAST_FOLDER_SORT_ORDER, defaultSortOrder))
        } else {
            defaultSortOrder
        }

        // Initialize remembered value when feature is enabled for the first time.
        if (rememberSortOrder && !prefs.contains(PREF_LAST_FOLDER_SORT_ORDER)) {
            prefs.edit().putString(PREF_LAST_FOLDER_SORT_ORDER, currentSortOrder).apply()
        }
    }

    private fun loadGroupByPreference() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val defaultGroupBy = FolderGroupBy.fromPreference(
            prefs.getString(PREF_DEFAULT_GROUP_BY, FolderGroupBy.DATE.preferenceValue)
        )
        val rememberGroupBy = prefs.getBoolean(PREF_REMEMBER_FOLDER_GROUP_BY, false)
        currentGroupBy = if (rememberGroupBy) {
            FolderGroupBy.fromPreference(prefs.getString(PREF_LAST_FOLDER_GROUP_BY, defaultGroupBy.preferenceValue))
        } else {
            defaultGroupBy
        }

        if (rememberGroupBy && !prefs.contains(PREF_LAST_FOLDER_GROUP_BY)) {
            prefs.edit().putString(PREF_LAST_FOLDER_GROUP_BY, currentGroupBy.preferenceValue).apply()
        }
    }

    private fun parseSortOrderPreference(sortOrderValue: String?): String {
        return when (sortOrderValue) {
            "date_desc", "date_asc", "name_asc", "name_desc", "size_desc", "size_asc" -> sortOrderValue
            else -> "date_desc"
        }
    }

    private fun sortOrderLabel(sortOrder: String = currentSortOrder): String {
        return when (sortOrder) {
            "date_desc" -> getString(R.string.folder_sort_chip_date_desc)
            "date_asc" -> getString(R.string.folder_sort_chip_date_asc)
            "name_asc" -> getString(R.string.folder_sort_chip_name_asc)
            "name_desc" -> getString(R.string.folder_sort_chip_name_desc)
            "size_desc" -> getString(R.string.folder_sort_chip_size_desc)
            "size_asc" -> getString(R.string.folder_sort_chip_size_asc)
            else -> getString(R.string.folder_sort_chip_date_desc)
        }
    }

    private fun updateSortIndicator() {
        val label = sortOrderLabel()
        val description = getString(R.string.folder_sort_content_description, label)
        binding.sortStatusChip.text = label
        binding.sortStatusChip.contentDescription = description
        sortMenuItem?.title = description
    }

    private fun groupByLabel(groupBy: FolderGroupBy = currentGroupBy): String {
        return when (groupBy) {
            FolderGroupBy.NONE -> getString(R.string.group_none)
            FolderGroupBy.DATE -> getString(R.string.group_date)
            FolderGroupBy.NAME -> getString(R.string.group_name)
            FolderGroupBy.SIZE -> getString(R.string.group_size)
            FolderGroupBy.TYPE -> getString(R.string.group_type)
        }
    }

    private fun updateGroupIndicator() {
        val label = groupByLabel()
        val chipText = getString(R.string.folder_group_chip_format, label)
        binding.groupStatusChip.text = chipText
        binding.groupStatusChip.contentDescription =
            getString(R.string.folder_group_content_description, label)
    }

    private fun persistCurrentSortOrderIfNeeded() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        if (!prefs.getBoolean(PREF_REMEMBER_FOLDER_SORT_ORDER, false)) return
        prefs.edit().putString(PREF_LAST_FOLDER_SORT_ORDER, currentSortOrder).apply()
    }

    private fun persistCurrentGroupByIfNeeded() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        if (!prefs.getBoolean(PREF_REMEMBER_FOLDER_GROUP_BY, false)) return
        prefs.edit().putString(PREF_LAST_FOLDER_GROUP_BY, currentGroupBy.preferenceValue).apply()
    }

    private fun showSortDialog() {
        val sortOptions = arrayOf(
            getString(R.string.sort_by_date_desc),
            getString(R.string.sort_by_date_asc),
            getString(R.string.sort_by_name_asc),
            getString(R.string.sort_by_name_desc),
            getString(R.string.sort_by_size_desc),
            getString(R.string.sort_by_size_asc)
        )

        val sortValues = arrayOf("date_desc", "date_asc", "name_asc", "name_desc", "size_desc", "size_asc")

        val currentIndex = sortValues.indexOf(currentSortOrder).takeIf { it >= 0 } ?: 0

        android.app.AlertDialog.Builder(this)
            .setTitle(R.string.sort)
            .setSingleChoiceItems(sortOptions, currentIndex) { dialog, which ->
                currentSortOrder = parseSortOrderPreference(sortValues[which])
                applyDisplayTransform(scrollToTop = true)
                persistCurrentSortOrderIfNeeded()
                updateSortIndicator()
                dialog.dismiss()
            }
            .setNegativeButton(R.string.cancel, null)
            .showThemed()
    }

    private fun showGroupDialog() {
        val groupValues = arrayOf(
            FolderGroupBy.DATE,
            FolderGroupBy.NAME,
            FolderGroupBy.SIZE,
            FolderGroupBy.TYPE,
            FolderGroupBy.NONE
        )
        val groupOptions = groupValues.map { groupByLabel(it) }.toTypedArray()
        val currentIndex = groupValues.indexOf(currentGroupBy).takeIf { it >= 0 } ?: 0

        android.app.AlertDialog.Builder(this)
            .setTitle(R.string.group_by)
            .setSingleChoiceItems(groupOptions, currentIndex) { dialog, which ->
                currentGroupBy = groupValues[which]
                applyDisplayTransform(scrollToTop = true)
                persistCurrentGroupByIfNeeded()
                updateGroupIndicator()
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

    private fun applyDisplayTransform(scrollToTop: Boolean) {
        transformJob?.cancel()
        if (isLoadingMediaItems) return
        if (mediaItems.isEmpty()) {
            clearDisplayedItems()
            return
        }

        val generation = ++displayGeneration
        val sourceItems = mediaItems
        val sortOrderSnapshot = currentSortOrder
        val groupBySnapshot = currentGroupBy
        val labels = folderDisplayLabels()

        transformJob = lifecycleScope.launch {
            val result = withContext(Dispatchers.Default) {
                FolderDisplayBuilder.build(
                    items = sourceItems,
                    sortOrder = sortOrderSnapshot,
                    groupBy = groupBySnapshot,
                    labels = labels
                )
            }
            if (generation != displayGeneration) return@launch

            mediaItems = result.sortedMediaItems
            submitDisplayResult(result, scrollToTop)
        }
    }

    private fun submitDisplayResult(result: FolderDisplayResult, scrollToTop: Boolean = false) {
        if (result.isGrouped) {
            if (binding.recyclerView.adapter != groupedMediaAdapter) {
                binding.recyclerView.adapter = groupedMediaAdapter
            }
            groupedMediaAdapter.viewMode = currentViewMode
            groupedMediaAdapter.submitList(result.displayItems) {
                if (scrollToTop) binding.recyclerView.scrollToPosition(0)
            }
        } else {
            if (binding.recyclerView.adapter != mediaAdapter) {
                binding.recyclerView.adapter = mediaAdapter
            }
            mediaAdapter.viewMode = currentViewMode
            mediaAdapter.submitList(result.sortedMediaItems) {
                if (scrollToTop) binding.recyclerView.scrollToPosition(0)
            }
        }
    }

    private fun clearDisplayedItems() {
        mediaAdapter.submitList(emptyList())
        groupedMediaAdapter.submitList(emptyList())
    }

    private fun folderDisplayLabels(): FolderDisplayLabels {
        val appContext = applicationContext
        return FolderDisplayLabels(
            unknownDate = getString(R.string.group_unknown_date),
            unknownSize = getString(R.string.group_unknown_size),
            imageType = getString(R.string.group_type_image),
            videoType = getString(R.string.group_type_video),
            otherType = getString(R.string.group_type_other),
            formatSize = { bytes -> Formatter.formatShortFileSize(appContext, bytes) }
        )
    }

    private suspend fun buildDisplayResultForCurrentState(items: List<MediaItem>): FolderDisplayResult {
        while (true) {
            val sortOrderSnapshot = currentSortOrder
            val groupBySnapshot = currentGroupBy
            val labels = folderDisplayLabels()
            val result = withContext(Dispatchers.Default) {
                FolderDisplayBuilder.build(
                    items = items,
                    sortOrder = sortOrderSnapshot,
                    groupBy = groupBySnapshot,
                    labels = labels
                )
            }
            if (sortOrderSnapshot == currentSortOrder && groupBySnapshot == currentGroupBy) {
                return result
            }
        }
    }

    private fun maybeReloadForDetailedMode() {
        if (currentViewMode != MediaAdapter.ViewMode.DETAILED) return
        if (mediaItems.isEmpty()) return

        val hasDeferredMetadata = mediaItems.any { item ->
            item.size <= 0L || item.width <= 0 || item.height <= 0
        }
        if (!hasDeferredMetadata) return

        loadMediaItems(showBlockingLoading = false)
    }
    
    private fun loadMediaItems(
        showBlockingLoading: Boolean = true,
        fromSwipeRefresh: Boolean = false
    ) {
        if (isLoadingMediaItems) {
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

        isLoadingMediaItems = true
        
        lifecycleScope.launch {
            try {
                transformJob?.cancel()
                val scannedItems = if (SmbPath.isSmb(folderPath)) {
                    // SMB folder: use SmbMediaScanner
                    val smbScanner = SmbMediaScanner(this@FolderViewActivity)
                    smbScanner.scanSmbMediaInFolder(folderPath)
                } else {
                    // Local folder: use MediaScanner
                    val includeDeferredMetadata = currentViewMode == MediaAdapter.ViewMode.DETAILED
                    var localItems = mediaScanner.scanMediaInFolder(
                        folderPath = folderPath,
                        includeDeferredMetadata = includeDeferredMetadata,
                        mergeFileSystemFallback = false
                    )

                    if (localItems.isEmpty()) {
                        localItems = mediaScanner.scanMediaInFolder(
                            folderPath = folderPath,
                            includeDeferredMetadata = includeDeferredMetadata,
                            mergeFileSystemFallback = true
                        )
                    }
                    localItems
                }

                if (scannedItems.isEmpty()) {
                    mediaItems = emptyList()
                    clearDisplayedItems()
                    binding.progressBar.visibility = View.GONE
                    binding.emptyView.visibility = View.VISIBLE
                    binding.recyclerView.visibility = View.GONE
                    bindFolderStats(mediaItems)
                } else {
                    val generation = ++displayGeneration
                    val displayResult = buildDisplayResultForCurrentState(scannedItems)
                    if (generation != displayGeneration) return@launch

                    mediaItems = displayResult.sortedMediaItems
                    submitDisplayResult(displayResult)
                    binding.progressBar.visibility = View.GONE
                    binding.emptyView.visibility = View.GONE
                    binding.recyclerView.visibility = View.VISIBLE
                    bindFolderStats(mediaItems)
                }
            } catch (e: Exception) {
                binding.progressBar.visibility = View.GONE
                binding.emptyView.visibility = View.VISIBLE
                binding.recyclerView.visibility = View.GONE
            } finally {
                isLoadingMediaItems = false
                binding.swipeRefresh.isRefreshing = false
            }
        }
    }
}
