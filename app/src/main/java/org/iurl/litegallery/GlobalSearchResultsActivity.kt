package org.iurl.litegallery

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.SystemClock
import android.text.format.Formatter
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.ListPreloader
import com.bumptech.glide.RequestBuilder
import com.bumptech.glide.integration.recyclerview.RecyclerViewPreloader
import com.google.android.material.datepicker.MaterialDatePicker
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.iurl.litegallery.databinding.ActivityFolderViewBinding
import org.iurl.litegallery.theme.GradientHelper
import org.iurl.litegallery.theme.ThemeColorResolver
import org.iurl.litegallery.theme.ThemeVariant
import java.io.File
import kotlin.math.roundToInt

class GlobalSearchResultsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFolderViewBinding
    private lateinit var mediaAdapter: MediaAdapter
    private lateinit var groupedMediaAdapter: GroupedMediaAdapter
    private lateinit var mediaScanner: MediaScanner

    private var unfilteredMediaItems: List<MediaItemSkeleton> = emptyList()
    private var mediaItems: List<MediaItemSkeleton> = emptyList()
    private var currentViewMode: MediaAdapter.ViewMode = MediaAdapter.ViewMode.GRID
    private var currentSortOrder: String = "date_desc"
    private var currentGroupBy: FolderGroupBy = FolderGroupBy.NONE
    private var currentSearchName = ""
    private var currentSearchFilters = SearchFilterState()
    private var appliedSearchQuery: MediaSearchQuery? = null
    private var appliedSearchKey: SearchQueryKey? = null
    private var currentPackKey: String? = null
    private var sortMenuItem: MenuItem? = null
    private var searchMenuItem: MenuItem? = null
    private var searchView: SearchView? = null
    private var suppressSearchEvents = false
    private var expandSearchWithoutFocus = false
    private var isLoadingMediaItems = false
    private var lastSwipeRefreshAtMs = 0L
    private var loadJob: Job? = null
    private var transformJob: Job? = null
    private var mediaIndexSyncJob: Job? = null
    private var displayGeneration = 0
    private var fastScrollSections: List<FastScrollSection> = emptyList()
    private var lockedFastScrollRange = 0
    private var swipeRefreshEnabledBeforeFastScroll = true
    private var deferFastScrollerUntilFinalLoad = false
    private val pendingMetadataIds = mutableSetOf<Long>()

    private data class SearchFilterState(
        val typeFilter: MediaTypeFilter = MediaTypeFilter.ALL,
        val dateRange: TimeRange? = null,
        val sizeRangeBytes: LongRange? = null
    )

    private data class SearchQueryKey(
        val normalizedNameQuery: String,
        val filters: SearchFilterState
    )

    private val mediaViewerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val data = result.data
        val hasMediaChanged = result.resultCode == RESULT_OK &&
            data?.getBooleanExtra(MediaViewerActivity.RESULT_MEDIA_CHANGED, false) == true
        if (!hasMediaChanged) return@registerForActivityResult

        FolderMediaRepository.invalidate(currentSearchFolderKey())
        setResult(
            RESULT_OK,
            Intent().apply {
                putExtra(MediaViewerActivity.RESULT_MEDIA_CHANGED, true)
            }
        )
        appliedSearchQuery?.let { loadSearchResults(it, showBlockingLoading = false) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeHelper.applyTheme(this)
        ThemeHelper.applyPackTheme(this, ThemeVariant.NoActionBar)

        super.onCreate(savedInstanceState)
        ThemeHelper.captureCustomThemeGeneration(this)
        binding = ActivityFolderViewBinding.inflate(layoutInflater)
        setContentView(binding.root)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                finish()
            }
        })

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
        }
        binding.root.setOnApplyWindowInsetsListener { view, insets ->
            val navigationInsets = insets.getInsets(android.view.WindowInsets.Type.navigationBars())
            val statusInsets = insets.getInsets(android.view.WindowInsets.Type.statusBars())
            view.setPadding(0, statusInsets.top, 0, navigationInsets.bottom)
            insets
        }

        currentSearchName = intent.getStringExtra(EXTRA_NAME_QUERY).orEmpty()
        currentSearchFilters = readSearchFilters(intent)
        currentPackKey = ThemeHelper.getCurrentPack(this).key

        mediaScanner = MediaScanner(this)
        loadViewModePreference()
        loadSortOrderPreference()
        loadGroupByPreference()

        setupToolbar()
        setupRecyclerView()
        setupSwipeRefresh()
        setupSearchFilters()
        setupFastScroller()
        ThemeHelper.applyRuntimeCustomColors(this)
        applyFolderHeroGradientAccent()
        binding.root.post { applyFolderHeroGradientAccent() }

        binding.root.post {
            if (hasAnySearchInput()) {
                submitGlobalSearch(scrollToTop = true)
            } else {
                showSearchPrompt()
            }
        }
    }

    override fun onDestroy() {
        loadJob?.cancel()
        transformJob?.cancel()
        mediaIndexSyncJob?.cancel()
        pendingMetadataIds.clear()
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        ThemeHelper.applyTheme(this)
        val newPackKey = ThemeHelper.getCurrentPack(this).key
        if (currentPackKey != null && currentPackKey != newPackKey) {
            recreate()
            return
        }
        currentPackKey = newPackKey
        if (ThemeHelper.checkAndRecreateForCustomThemeChange(this)) return
        applyFolderHeroGradientAccent()
        synchronizeMediaIndexInBackground()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.global_search_menu, menu)
        sortMenuItem = menu.findItem(R.id.action_sort)
        searchMenuItem = menu.findItem(R.id.action_search)
        setupSearchMenuItem(searchMenuItem)
        updateSortIndicator()
        val initialSearchName = currentSearchName
        if (hasAnySearchInput()) {
            suppressSearchEvents = true
            expandSearchWithoutFocus = true
            searchMenuItem?.expandActionView()
            searchView?.post {
                currentSearchName = initialSearchName
                searchView?.setQuery(initialSearchName, false)
                searchView?.clearFocus()
                hideSearchKeyboard(searchView ?: binding.root)
                suppressSearchEvents = false
                updateSearchFilterChips()
            }
        }
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
            title = ""
        }
        binding.folderEyebrowTextView.setText(R.string.global_search_eyebrow)
        bindSearchTitle()
        binding.folderHeroDivider.visibility = View.GONE
        binding.folderStatsTextView.visibility = View.GONE
        binding.groupStatusChip.setOnClickListener { showGroupDialog() }
        binding.sortStatusChip.setOnClickListener { showSortDialog() }
        updateGroupIndicator()
        updateSortIndicator()
    }

    private fun Int.withAlpha(alpha: Int): Int =
        (this and 0x00FFFFFF) or (alpha.coerceIn(0, 255) shl 24)

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

    private fun setupRecyclerView() {
        mediaAdapter = MediaAdapter(
            onMediaClick = { skeleton, position -> openMediaViewer(skeleton, position) },
            sourceBadgeLabelProvider = { skeleton -> sourceFolderLabel(skeleton) },
            sourceBadgeContentDescriptionProvider = { skeleton ->
                sourceFolderLabel(skeleton)?.let { getString(R.string.search_result_folder_content_description, it) }
            },
            onDetailedMetadataNeeded = ::requestDetailedMetadataForVisibleItem
        )
        groupedMediaAdapter = GroupedMediaAdapter(
            onMediaClick = { skeleton, mediaIndex -> openMediaViewer(skeleton, mediaIndex) },
            onDetailedMetadataNeeded = ::requestDetailedMetadataForVisibleItem
        )

        binding.recyclerView.apply {
            setHasFixedSize(true)
            setItemViewCacheSize(20)
            addOnScrollListener(createThumbnailPreloader())
            adapter = mediaAdapter
        }
        updateLayoutManager()
    }

    private fun createThumbnailPreloader(): RecyclerViewPreloader<Any> {
        val requestManager = Glide.with(this)
        val preloadModelProvider = object : ListPreloader.PreloadModelProvider<Any> {
            override fun getPreloadItems(position: Int): MutableList<Any> {
                val skeleton = when (binding.recyclerView.adapter) {
                    mediaAdapter -> mediaAdapter.currentList.getOrNull(position)
                    groupedMediaAdapter -> (groupedMediaAdapter.currentList.getOrNull(position) as? FolderDisplayItem.Media)?.skeleton
                    else -> null
                }
                if (skeleton == null || (skeleton.isSmb && skeleton.isVideo)) return mutableListOf()
                return mutableListOf(skeleton.thumbnailModel())
            }

            override fun getPreloadRequestBuilder(item: Any): RequestBuilder<Drawable>? {
                return requestManager.load(item).centerCrop()
            }
        }
        val preloadSizeProvider = object : ListPreloader.PreloadSizeProvider<Any> {
            override fun getPreloadSize(item: Any, adapterPosition: Int, perItemPosition: Int): IntArray {
                val sizePx = (96 * resources.displayMetrics.density).roundToInt().coerceAtLeast(96)
                return intArrayOf(sizePx, sizePx)
            }
        }
        return RecyclerViewPreloader(requestManager, preloadModelProvider, preloadSizeProvider, 20)
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefresh.setOnRefreshListener {
            val now = SystemClock.elapsedRealtime()
            if (now - lastSwipeRefreshAtMs < SWIPE_REFRESH_THROTTLE_MS) {
                binding.swipeRefresh.isRefreshing = false
                return@setOnRefreshListener
            }
            lastSwipeRefreshAtMs = now
            val query = appliedSearchQuery
            if (query == null) {
                binding.swipeRefresh.isRefreshing = false
                showSearchPrompt()
            } else {
                loadSearchResults(query, showBlockingLoading = false)
            }
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
        suppressSearchEvents = true
        view.setQuery(currentSearchName, false)
        suppressSearchEvents = false
        view.findViewById<View>(androidx.appcompat.R.id.search_mag_icon)?.setOnClickListener {
            submitGlobalSearchFromView(view)
        }
        view.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                if (suppressSearchEvents) return true
                submitGlobalSearchFromView(view)
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                if (suppressSearchEvents) return true
                currentSearchName = newText.orEmpty()
                bindSearchTitle()
                if (currentSearchName.isEmpty()) {
                    clearResultsForEmptySearchText()
                    return true
                }
                updateSearchFilterChips()
                return true
            }
        })
        view.setOnCloseListener {
            if (view.query?.isNotEmpty() == true) {
                view.setQuery("", false)
            } else {
                finish()
            }
            true
        }
        item.setOnActionExpandListener(object : MenuItem.OnActionExpandListener {
            override fun onMenuItemActionExpand(item: MenuItem): Boolean {
                binding.searchFilterRow.visibility = View.VISIBLE
                updateSearchFilterChips()
                val searchNameSnapshot = currentSearchName
                val shouldFocusSearch = !expandSearchWithoutFocus
                expandSearchWithoutFocus = false
                if (!shouldFocusSearch) {
                    view.clearFocus()
                }
                view.post {
                    if (!suppressSearchEvents) {
                        suppressSearchEvents = true
                        currentSearchName = searchNameSnapshot
                        view.setQuery(searchNameSnapshot, false)
                        suppressSearchEvents = false
                    }
                    if (shouldFocusSearch) {
                        view.requestFocus()
                        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                        imm?.showSoftInput(view.findFocus() ?: view, InputMethodManager.SHOW_IMPLICIT)
                    } else {
                        view.clearFocus()
                        hideSearchKeyboard(view)
                    }
                }
                return true
            }

            override fun onMenuItemActionCollapse(item: MenuItem): Boolean {
                finish()
                return false
            }
        })
    }

    private fun submitGlobalSearchFromView(view: SearchView) {
        currentSearchName = view.query?.toString().orEmpty()
        submitGlobalSearch(scrollToTop = true)
        view.clearFocus()
    }

    private fun hideSearchKeyboard(view: View) {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(view.windowToken, 0)
    }

    private fun setupSearchFilters() {
        binding.searchTypeChip.setOnClickListener {
            val nextType = when (currentSearchFilters.typeFilter) {
                MediaTypeFilter.ALL -> MediaTypeFilter.IMAGES
                MediaTypeFilter.IMAGES -> MediaTypeFilter.VIDEOS
                MediaTypeFilter.VIDEOS -> MediaTypeFilter.ALL
            }
            currentSearchFilters = currentSearchFilters.copy(typeFilter = nextType)
            submitGlobalSearch(scrollToTop = true)
        }
        binding.searchDateChip.setOnClickListener {
            showDateRangePicker()
        }
        binding.searchSizeChip.setOnClickListener {
            SearchFilterUi.showSizeRangeDialog(this, currentSearchFilters.sizeRangeBytes) { range ->
                currentSearchFilters = currentSearchFilters.copy(sizeRangeBytes = range)
                submitGlobalSearch(scrollToTop = true)
            }
        }
        binding.searchClearChip.setOnClickListener {
            clearGlobalSearch(collapseSearchView = false)
        }
        binding.folderClearFiltersButton.setOnClickListener {
            clearGlobalSearch(collapseSearchView = false)
        }
        updateSearchFilterChips()
    }

    private fun showDateRangePicker() {
        val picker = MaterialDatePicker.Builder.dateRangePicker()
            .setTitleText(getString(R.string.search_date_title))
            .build()
        picker.addOnPositiveButtonClickListener { selection ->
            val range = SearchDateRangeConverter.toTimeRangeOrNull(selection?.first, selection?.second)
            if (range == null) {
                android.widget.Toast.makeText(this, R.string.error, android.widget.Toast.LENGTH_SHORT).show()
            } else {
                currentSearchFilters = currentSearchFilters.copy(dateRange = range)
                submitGlobalSearch(scrollToTop = true)
            }
        }
        picker.show(supportFragmentManager, "global_search_date")
    }

    private fun submitGlobalSearch(scrollToTop: Boolean) {
        bindSearchTitle()
        val normalizedName = NameMatcher.normalizePattern(currentSearchName)
        val query = HomeSearchQueryFactory.buildGlobalMediaQuery(
            normalizedName = normalizedName,
            typeFilter = currentSearchFilters.typeFilter,
            dateRange = currentSearchFilters.dateRange,
            sizeRangeBytes = currentSearchFilters.sizeRangeBytes
        )

        if (query == null) {
            appliedSearchQuery = null
            appliedSearchKey = null
            clearDisplayedItems()
            showSearchPrompt()
            return
        }

        appliedSearchQuery = query
        appliedSearchKey = SearchQueryKey(normalizedName, currentSearchFilters)
        updateSearchFilterChips()
        loadSearchResults(query, showBlockingLoading = true, scrollToTop = scrollToTop)
    }

    private fun clearGlobalSearch(
        collapseSearchView: Boolean,
        clearSearchViewText: Boolean = true
    ) {
        val folderKeyToInvalidate = currentSearchFolderKey()
        loadJob?.cancel()
        transformJob?.cancel()
        currentSearchName = ""
        currentSearchFilters = SearchFilterState()
        appliedSearchQuery = null
        appliedSearchKey = null
        unfilteredMediaItems = emptyList()
        mediaItems = emptyList()

        if (clearSearchViewText) {
            suppressSearchEvents = true
            searchView?.setQuery("", false)
            searchView?.clearFocus()
            suppressSearchEvents = false
        }

        clearDisplayedItems(folderKeyToInvalidate)
        bindSearchTitle()
        showSearchPrompt()
        updateSearchFilterChips()
        if (collapseSearchView) {
            searchMenuItem?.collapseActionView()
        }
    }

    private fun clearResultsForEmptySearchText() {
        val folderKeyToInvalidate = currentSearchFolderKey()
        loadJob?.cancel()
        transformJob?.cancel()
        appliedSearchQuery = null
        appliedSearchKey = null
        unfilteredMediaItems = emptyList()
        mediaItems = emptyList()
        clearDisplayedItems(folderKeyToInvalidate)
        bindSearchTitle()
        showSearchPrompt()
        updateSearchFilterChips()
    }

    private fun bindSearchTitle() {
        val title = currentSearchName.trim().ifBlank { getString(R.string.global_search_title) }
        binding.folderTitleTextView.text = title
    }

    private fun updateSearchFilterChips() {
        binding.searchTypeChip.text = when (currentSearchFilters.typeFilter) {
            MediaTypeFilter.ALL -> getString(R.string.search_chip_type_all)
            MediaTypeFilter.IMAGES -> getString(R.string.search_chip_type_images)
            MediaTypeFilter.VIDEOS -> getString(R.string.search_chip_type_videos)
        }
        binding.searchDateChip.text = SearchFilterUi.formatDateRange(this, currentSearchFilters.dateRange)
        binding.searchSizeChip.text = SearchFilterUi.formatSizeRange(this, currentSearchFilters.sizeRangeBytes)
        binding.searchClearChip.visibility = if (hasAnySearchInput()) View.VISIBLE else View.GONE
    }

    private fun hasAnySearchInput(): Boolean =
        currentSearchName.isNotBlank() || currentSearchFilters != SearchFilterState()

    private fun loadSearchResults(
        query: MediaSearchQuery,
        showBlockingLoading: Boolean,
        scrollToTop: Boolean = false
    ) {
        val generation = ++displayGeneration
        loadJob?.cancel()
        transformJob?.cancel()
        pendingMetadataIds.clear()
        isLoadingMediaItems = true
        deferFastScrollerUntilFinalLoad = true

        if (showBlockingLoading) {
            binding.progressBar.visibility = View.VISIBLE
            binding.emptyView.visibility = View.GONE
            binding.recyclerView.visibility = View.GONE
        } else {
            binding.swipeRefresh.isRefreshing = true
        }

        loadJob = lifecycleScope.launch {
            val loadedSkeletons = ArrayList<MediaItemSkeleton>()
            try {
                mediaScanner.searchIndexedMediaStreamed(query, currentSortOrder)
                    .collect { event ->
                        when (event) {
                            is LoadEvent.FirstScreen -> {
                                if (generation != displayGeneration) return@collect
                                loadedSkeletons.clear()
                                loadedSkeletons.addAll(event.items)
                                unfilteredMediaItems = loadedSkeletons.toList()
                                mediaItems = unfilteredMediaItems
                                cacheCurrentSearchMedia(mediaItems, isComplete = false)
                                submitLoadingSkeletons(mediaItems, replace = true)
                                binding.progressBar.visibility = if (mediaItems.isEmpty()) View.VISIBLE else View.GONE
                                if (mediaItems.isNotEmpty()) {
                                    binding.emptyView.visibility = View.GONE
                                    binding.recyclerView.visibility = View.VISIBLE
                                }
                            }
                            is LoadEvent.Progress -> {
                                if (generation != displayGeneration) return@collect
                                if (event.deltaItems.isNotEmpty()) {
                                    loadedSkeletons.addAll(event.deltaItems)
                                    unfilteredMediaItems = loadedSkeletons.toList()
                                    mediaItems = unfilteredMediaItems
                                    cacheCurrentSearchMedia(mediaItems, isComplete = false)
                                    submitLoadingSkeletons(mediaItems, event.deltaItems, replace = false)
                                    binding.progressBar.visibility = View.GONE
                                    binding.emptyView.visibility = View.GONE
                                    binding.recyclerView.visibility = View.VISIBLE
                                }

                                if (event.isFinal) {
                                    val displayResult = buildDisplayResultForCurrentState(loadedSkeletons.toList())
                                    if (generation != displayGeneration) return@collect
                                    deferFastScrollerUntilFinalLoad = false
                                    unfilteredMediaItems = loadedSkeletons.toList()
                                    mediaItems = displayResult.sortedMediaItems
                                    cacheCurrentSearchMedia(mediaItems, isComplete = true)
                                    submitDisplayResult(displayResult, scrollToTop = scrollToTop, bypassDiff = true)
                                    bindContentVisibility()
                                    isLoadingMediaItems = false
                                    binding.swipeRefresh.isRefreshing = false
                                    binding.progressBar.visibility = View.GONE
                                    binding.recyclerView.post {
                                        syncFastScrollerFromRecyclerView(showForScroll = false)
                                    }
                                }
                            }
                            is LoadEvent.Failed -> {
                                if (generation != displayGeneration) return@collect
                                showSearchError()
                                isLoadingMediaItems = false
                                binding.swipeRefresh.isRefreshing = false
                            }
                        }
                    }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                if (generation == displayGeneration) {
                    showSearchError()
                    isLoadingMediaItems = false
                    binding.swipeRefresh.isRefreshing = false
                }
            } finally {
                if (generation == displayGeneration && !isLoadingMediaItems) {
                    binding.progressBar.visibility = View.GONE
                    binding.swipeRefresh.isRefreshing = false
                }
            }
        }
    }

    private suspend fun buildDisplayResultForCurrentState(items: List<MediaItemSkeleton>): FolderDisplayResult {
        while (true) {
            val sortOrderSnapshot = currentSortOrder
            val groupBySnapshot = currentGroupBy
            val labels = folderDisplayLabels()
            val result = withContext(Dispatchers.Default) {
                FolderDisplayBuilder.build(
                    items = items,
                    sortOrder = sortOrderSnapshot,
                    groupBy = groupBySnapshot,
                    labels = labels,
                    searchQuery = null
                )
            }
            if (sortOrderSnapshot == currentSortOrder && groupBySnapshot == currentGroupBy) {
                return result
            }
        }
    }

    private fun applyDisplayTransform(scrollToTop: Boolean, bypassDiff: Boolean = false) {
        transformJob?.cancel()
        if (isLoadingMediaItems) return
        if (unfilteredMediaItems.isEmpty()) {
            mediaItems = emptyList()
            clearDisplayedItems()
            if (appliedSearchQuery == null) {
                showSearchPrompt()
            } else {
                showSearchEmptyState()
            }
            return
        }

        val generation = ++displayGeneration
        val sourceItems = unfilteredMediaItems
        val sortOrderSnapshot = currentSortOrder
        val groupBySnapshot = currentGroupBy
        val labels = folderDisplayLabels()

        transformJob = lifecycleScope.launch {
            val result = withContext(Dispatchers.Default) {
                FolderDisplayBuilder.build(
                    items = sourceItems,
                    sortOrder = sortOrderSnapshot,
                    groupBy = groupBySnapshot,
                    labels = labels,
                    searchQuery = null
                )
            }
            if (generation != displayGeneration) return@launch
            mediaItems = result.sortedMediaItems
            cacheCurrentSearchMedia(mediaItems, isComplete = true)
            submitDisplayResult(result, scrollToTop, bypassDiff)
            bindContentVisibility()
        }
    }

    private fun submitDisplayResult(
        result: FolderDisplayResult,
        scrollToTop: Boolean = false,
        bypassDiff: Boolean = false
    ) {
        disableFastScrollerForListMutation()
        bindSearchStats(result.stats)
        if (result.isGrouped) {
            if (binding.recyclerView.adapter != groupedMediaAdapter) {
                binding.recyclerView.adapter = groupedMediaAdapter
            }
            groupedMediaAdapter.viewMode = currentViewMode
            groupedMediaAdapter.submitList(result.displayItems, bypassDiff = bypassDiff) {
                if (scrollToTop) binding.recyclerView.scrollToPosition(0)
                applyFastScrollSectionsAfterCommit(result.fastScrollSections)
            }
        } else {
            if (binding.recyclerView.adapter != mediaAdapter) {
                binding.recyclerView.adapter = mediaAdapter
            }
            mediaAdapter.viewMode = currentViewMode
            mediaAdapter.submitList(result.sortedMediaItems, bypassDiff = bypassDiff) {
                if (scrollToTop) binding.recyclerView.scrollToPosition(0)
                applyFastScrollSectionsAfterCommit(result.fastScrollSections)
            }
        }
    }

    private fun submitLoadingSkeletons(
        items: List<MediaItemSkeleton>,
        deltaItems: List<MediaItemSkeleton> = emptyList(),
        replace: Boolean
    ) {
        if (binding.recyclerView.adapter != mediaAdapter) {
            binding.recyclerView.adapter = mediaAdapter
        }
        mediaAdapter.viewMode = currentViewMode
        if (replace) {
            disableFastScrollerForListMutation()
            bindSearchStats(FolderDisplayStats.EMPTY)
            mediaAdapter.submitList(items, bypassDiff = true)
        } else {
            mediaAdapter.appendSkeletons(deltaItems)
        }
    }

    private fun clearDisplayedItems(folderKeyToInvalidate: String = currentSearchFolderKey()) {
        disableFastScrollerForListMutation()
        bindSearchStats(FolderDisplayStats.EMPTY)
        mediaAdapter.submitList(emptyList(), bypassDiff = true)
        groupedMediaAdapter.submitList(emptyList(), bypassDiff = true)
        binding.recyclerView.visibility = View.GONE
        binding.progressBar.visibility = View.GONE
        isLoadingMediaItems = false
        binding.swipeRefresh.isRefreshing = false
        FolderMediaRepository.invalidate(folderKeyToInvalidate)
    }

    private fun bindSearchStats(stats: FolderDisplayStats) {
        if (stats.itemCount == 0) {
            binding.folderHeroDivider.visibility = View.GONE
            binding.folderStatsTextView.visibility = View.GONE
            return
        }
        val parts = mutableListOf(getString(R.string.items_count, stats.itemCount))
        if (stats.totalSizeBytes > 0L) {
            parts.add(Formatter.formatShortFileSize(this, stats.totalSizeBytes))
        }
        if (stats.videoCount > 0) {
            parts.add(getString(R.string.folder_stat_videos_format, stats.videoCount))
        }
        binding.folderStatsTextView.text = parts.joinToString(" / ")
        binding.folderHeroDivider.visibility = View.VISIBLE
        binding.folderStatsTextView.visibility = View.VISIBLE
    }

    private fun bindContentVisibility() {
        if (mediaItems.isEmpty()) {
            showSearchEmptyState()
            binding.recyclerView.visibility = View.GONE
        } else {
            binding.emptyView.visibility = View.GONE
            binding.recyclerView.visibility = View.VISIBLE
        }
    }

    private fun showSearchPrompt() {
        applyPromptEmptyLayout()
        binding.folderEmptyTextView.setText(R.string.search_global_media_prompt_subtitle)
        binding.folderClearFiltersButton.visibility = View.GONE
        binding.emptyView.visibility = View.VISIBLE
        binding.recyclerView.visibility = View.GONE
        binding.progressBar.visibility = View.GONE
    }

    private fun showSearchEmptyState() {
        applyPromptEmptyLayout()
        binding.folderEmptyTextView.setText(R.string.search_no_media)
        binding.folderClearFiltersButton.visibility = View.VISIBLE
        binding.emptyView.visibility = View.VISIBLE
        binding.progressBar.visibility = View.GONE
    }

    private fun showSearchError() {
        applyPromptEmptyLayout()
        binding.folderEmptyTextView.setText(R.string.search_global_media_error)
        binding.folderClearFiltersButton.visibility = View.VISIBLE
        binding.emptyView.visibility = View.VISIBLE
        binding.recyclerView.visibility = View.GONE
        binding.progressBar.visibility = View.GONE
    }

    private fun applyPromptEmptyLayout() {
        val params = binding.emptyView.layoutParams as? CoordinatorLayout.LayoutParams ?: return
        params.width = ViewGroup.LayoutParams.MATCH_PARENT
        params.height = ViewGroup.LayoutParams.WRAP_CONTENT
        params.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        params.topMargin = SEARCH_PROMPT_TOP_MARGIN_DP.dpToPx()
        binding.emptyView.layoutParams = params
        val horizontalPadding = SEARCH_PROMPT_HORIZONTAL_PADDING_DP.dpToPx()
        val verticalPadding = SEARCH_PROMPT_VERTICAL_PADDING_DP.dpToPx()
        binding.emptyView.setPadding(horizontalPadding, verticalPadding, horizontalPadding, verticalPadding)
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

    private fun setupFastScroller() {
        binding.recyclerView.isVerticalScrollBarEnabled = false
        binding.fastScrollerView.setSections(emptyList())
        binding.fastScrollerView.setEnabledForContent(false)
        binding.fastScrollerView.setDragListener(object : FolderFastScrollerView.DragListener {
            override fun onDragStarted(): Boolean {
                if (binding.swipeRefresh.isRefreshing) return false
                val scrollRange = binding.recyclerView.computeVerticalScrollRange()
                val scrollExtent = binding.recyclerView.computeVerticalScrollExtent()
                val scrollableRange = scrollRange - scrollExtent
                if (scrollableRange <= 0) return false
                lockedFastScrollRange = scrollableRange
                swipeRefreshEnabledBeforeFastScroll = binding.swipeRefresh.isEnabled
                binding.swipeRefresh.isEnabled = false
                return true
            }

            override fun onDragMoved(fraction: Float) {
                val scrollableRange = lockedFastScrollRange
                if (scrollableRange <= 0) return
                val desiredOffset = (fraction * scrollableRange).roundToInt()
                val delta = desiredOffset - binding.recyclerView.computeVerticalScrollOffset()
                if (delta != 0) {
                    scrollRecyclerViewForFastDrag(fraction, delta)
                }
                syncFastScrollerFromRecyclerView(showForScroll = false)
            }

            override fun onDragStopped() {
                lockedFastScrollRange = 0
                binding.swipeRefresh.isEnabled = swipeRefreshEnabledBeforeFastScroll
            }
        })

        binding.recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                syncFastScrollerFromRecyclerView(showForScroll = dy != 0)
                prefetchMetadataRange()
            }
        })
    }

    private fun scrollRecyclerViewForFastDrag(fraction: Float, delta: Int) {
        val recyclerView = binding.recyclerView
        val jumpThreshold = recyclerView.height * FAST_SCROLL_JUMP_VIEWPORT_MULTIPLIER
        if (kotlin.math.abs(delta) <= jumpThreshold) {
            recyclerView.scrollBy(0, delta)
            return
        }

        val itemCount = recyclerView.adapter?.itemCount ?: 0
        if (itemCount <= 0) return
        val targetPosition = (fraction * (itemCount - 1)).roundToInt().coerceIn(0, itemCount - 1)
        (recyclerView.layoutManager as? LinearLayoutManager)?.scrollToPositionWithOffset(targetPosition, 0)
    }

    private fun syncFastScrollerFromRecyclerView(showForScroll: Boolean) {
        val recyclerView = binding.recyclerView
        val scrollOffset = recyclerView.computeVerticalScrollOffset()
        val scrollRange = recyclerView.computeVerticalScrollRange()
        val scrollExtent = recyclerView.computeVerticalScrollExtent()
        val canScroll = recyclerView.visibility == View.VISIBLE &&
            displayedItemCount > 0 &&
            scrollRange > scrollExtent &&
            !deferFastScrollerUntilFinalLoad

        binding.fastScrollerView.setScrollMetrics(scrollOffset, scrollRange, scrollExtent)
        binding.fastScrollerView.setEnabledForContent(canScroll)
        updateFastScrollerSectionTitle()
        if (canScroll && showForScroll) {
            binding.fastScrollerView.show()
            binding.fastScrollerView.hideDelayed()
        }
    }

    private fun updateFastScrollerSectionTitle() {
        val firstVisiblePosition =
            (binding.recyclerView.layoutManager as? LinearLayoutManager)?.findFirstVisibleItemPosition()
                ?: RecyclerView.NO_POSITION
        val title = FastScrollSectionIndex.titleForPosition(fastScrollSections, firstVisiblePosition)
        binding.fastScrollerView.setCurrentSectionTitle(title)
    }

    private fun disableFastScrollerForListMutation() {
        fastScrollSections = emptyList()
        binding.fastScrollerView.setSections(emptyList())
        binding.fastScrollerView.setCurrentSectionTitle(null)
        binding.fastScrollerView.setEnabledForContent(false)
    }

    private fun applyFastScrollSectionsAfterCommit(sections: List<FastScrollSection>) {
        fastScrollSections = sections
        binding.fastScrollerView.setSections(sections)
        updateFastScrollerSectionTitle()
        binding.recyclerView.post {
            syncFastScrollerFromRecyclerView(showForScroll = false)
        }
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
        binding.recyclerView.post {
            syncFastScrollerFromRecyclerView(showForScroll = false)
        }
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

    private fun openMediaViewer(skeleton: MediaItemSkeleton, position: Int) {
        if (isLoadingMediaItems || deferFastScrollerUntilFinalLoad) {
            android.widget.Toast.makeText(this, R.string.search_wait_until_loaded, android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        if (mediaItems.isEmpty()) return

        cacheCurrentSearchMedia(mediaItems, isComplete = true)
        val intent = Intent(this, MediaViewerActivity::class.java).apply {
            putExtra(MediaViewerActivity.EXTRA_MEDIA_PATH, skeleton.path)
            putExtra(MediaViewerActivity.EXTRA_FOLDER_PATH, currentSearchFolderKey())
            putExtra(MediaViewerActivity.EXTRA_CURRENT_POSITION, position)
        }
        mediaViewerLauncher.launch(intent)
    }

    private fun cacheCurrentSearchMedia(
        items: List<MediaItemSkeleton> = mediaItems,
        isComplete: Boolean
    ) {
        if (items.isEmpty()) {
            FolderMediaRepository.invalidate(currentSearchFolderKey())
            return
        }
        FolderMediaRepository.putSkeleton(
            folderPath = currentSearchFolderKey(),
            items = items,
            isComplete = isComplete,
            sortOrder = currentSortOrder,
            groupBy = currentGroupBy
        )
    }

    private fun currentSearchFolderKey(): String {
        val key = appliedSearchKey ?: SearchQueryKey(
            normalizedNameQuery = NameMatcher.normalizePattern(currentSearchName),
            filters = currentSearchFilters
        )
        return "$SEARCH_FOLDER_KEY_PREFIX${key.hashCode()}"
    }

    private fun requestDetailedMetadataForVisibleItem(skeleton: MediaItemSkeleton) {
        if (skeleton.id <= MediaItem.NO_MEDIASTORE_ID) return
        if (skeleton.isSmb || skeleton.path.isBlank()) return
        if (MediaMetadataCache.get(skeleton) != null) return
        if (!pendingMetadataIds.add(skeleton.id)) return

        lifecycleScope.launch {
            try {
                val cachedItems = mediaScanner.getCachedMediaByIds(listOf(skeleton.id))
                val item = cachedItems.firstOrNull() ?: return@launch
                MediaMetadataCache.put(item)
                notifyMetadataLoaded(item)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
            } finally {
                pendingMetadataIds.remove(skeleton.id)
            }
        }
    }

    private fun notifyMetadataLoaded(item: MediaItem) {
        if (binding.recyclerView.adapter == groupedMediaAdapter) {
            val adapterPos = groupedMediaAdapter.currentList.indexOfFirst {
                it is FolderDisplayItem.Media && it.skeleton.id == item.id
            }
            if (adapterPos >= 0) {
                groupedMediaAdapter.notifyItemChanged(adapterPos, MediaAdapter.PAYLOAD_META_LOADED)
            }
        } else {
            val adapterPos = mediaAdapter.currentList.indexOfFirst { it.id == item.id }
            if (adapterPos >= 0) {
                mediaAdapter.notifyItemChanged(adapterPos, MediaAdapter.PAYLOAD_META_LOADED)
            }
        }
    }

    private fun prefetchMetadataRange() {
        val lm = binding.recyclerView.layoutManager as? LinearLayoutManager ?: return
        val first = lm.findFirstVisibleItemPosition()
        val last = lm.findLastVisibleItemPosition()
        if (first == RecyclerView.NO_POSITION || last == RecyclerView.NO_POSITION) return

        val from = (first - 20).coerceAtLeast(0)
        val to = (last + 50).coerceAtMost(displayedItemCount - 1)
        val skeletonsToFetch = mutableListOf<MediaItemSkeleton>()
        val isGrouped = binding.recyclerView.adapter == groupedMediaAdapter
        for (i in from..to) {
            val skeleton = if (isGrouped) {
                val item = groupedMediaAdapter.getItem(i)
                if (item is FolderDisplayItem.Media) item.skeleton else null
            } else {
                mediaAdapter.getItem(i)
            }
            if (
                skeleton != null &&
                skeleton.id > MediaItem.NO_MEDIASTORE_ID &&
                !skeleton.isSmb &&
                MediaMetadataCache.get(skeleton) == null &&
                skeleton.id !in pendingMetadataIds
            ) {
                skeletonsToFetch.add(skeleton)
            }
        }
        if (skeletonsToFetch.isEmpty()) return

        val idsToFetch = skeletonsToFetch.map { it.id }.distinct()
        pendingMetadataIds.addAll(idsToFetch)
        lifecycleScope.launch {
            try {
                val cachedItems = mediaScanner.getCachedMediaByIds(idsToFetch)
                cachedItems.forEach { item ->
                    MediaMetadataCache.put(item)
                    notifyMetadataLoaded(item)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
            } finally {
                idsToFetch.forEach { pendingMetadataIds.remove(it) }
            }
        }
    }

    private fun loadViewModePreference() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        currentViewMode = parseViewModePreference(prefs.getString(PREF_DEFAULT_VIEW_MODE, "grid"))
    }

    private fun loadSortOrderPreference() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        currentSortOrder = parseSortOrderPreference(prefs.getString(PREF_DEFAULT_SORT_ORDER, "date_desc"))
    }

    private fun loadGroupByPreference() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        currentGroupBy = FolderGroupBy.fromPreference(
            prefs.getString(PREF_DEFAULT_GROUP_BY, FolderGroupBy.NONE.preferenceValue)
        )
    }

    private fun toggleViewMode() {
        currentViewMode = when (currentViewMode) {
            MediaAdapter.ViewMode.GRID -> MediaAdapter.ViewMode.LIST
            MediaAdapter.ViewMode.LIST -> MediaAdapter.ViewMode.DETAILED
            MediaAdapter.ViewMode.DETAILED -> MediaAdapter.ViewMode.GRID
        }
        updateLayoutManager()
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
        binding.groupStatusChip.text = getString(R.string.folder_group_chip_format, label)
        binding.groupStatusChip.contentDescription = getString(R.string.folder_group_content_description, label)
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
                updateSortIndicator()
                if (isLoadingMediaItems) {
                    appliedSearchQuery?.let { loadSearchResults(it, showBlockingLoading = true, scrollToTop = true) }
                } else {
                    applyDisplayTransform(scrollToTop = true, bypassDiff = true)
                }
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
                updateGroupIndicator()
                applyDisplayTransform(scrollToTop = true, bypassDiff = true)
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

    private fun sourceFolderLabel(skeleton: MediaItemSkeleton): String? {
        if (skeleton.path.startsWith("content://")) return null
        return File(skeleton.path).parentFile?.name?.takeIf { it.isNotBlank() }
    }

    private fun Int.dpToPx(): Int = (this * resources.displayMetrics.density).roundToInt()

    private fun synchronizeMediaIndexInBackground() {
        if (!::mediaScanner.isInitialized || isLoadingMediaItems) return
        if (mediaIndexSyncJob?.isActive == true) return
        mediaIndexSyncJob = lifecycleScope.launch {
            try {
                mediaScanner.synchronizeMediaIndexIfNeeded()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
            }
        }
    }

    private fun readSearchFilters(intent: Intent): SearchFilterState {
        val typeFilter = runCatching {
            MediaTypeFilter.valueOf(intent.getStringExtra(EXTRA_TYPE_FILTER) ?: MediaTypeFilter.ALL.name)
        }.getOrDefault(MediaTypeFilter.ALL)
        val dateRange = if (intent.getBooleanExtra(EXTRA_HAS_DATE_RANGE, false)) {
            val start = intent.getLongExtra(EXTRA_DATE_START_MS, 0L)
            val end = intent.getLongExtra(EXTRA_DATE_END_MS, 0L)
            if (end > start) TimeRange(start, end) else null
        } else {
            null
        }
        val sizeRange = if (intent.getBooleanExtra(EXTRA_HAS_SIZE_RANGE, false)) {
            val min = intent.getLongExtra(EXTRA_SIZE_MIN_BYTES, 0L)
            val max = intent.getLongExtra(EXTRA_SIZE_MAX_BYTES, -1L)
            if (max >= min) min..max else null
        } else {
            null
        }
        return SearchFilterState(typeFilter, dateRange, sizeRange)
    }

    companion object {
        private const val EXTRA_NAME_QUERY = "extra_name_query"
        private const val EXTRA_TYPE_FILTER = "extra_type_filter"
        private const val EXTRA_HAS_DATE_RANGE = "extra_has_date_range"
        private const val EXTRA_DATE_START_MS = "extra_date_start_ms"
        private const val EXTRA_DATE_END_MS = "extra_date_end_ms"
        private const val EXTRA_HAS_SIZE_RANGE = "extra_has_size_range"
        private const val EXTRA_SIZE_MIN_BYTES = "extra_size_min_bytes"
        private const val EXTRA_SIZE_MAX_BYTES = "extra_size_max_bytes"
        private const val SEARCH_FOLDER_KEY_PREFIX = "search://global-media/"
        private const val SWIPE_REFRESH_THROTTLE_MS = 1_200L
        private const val FAST_SCROLL_JUMP_VIEWPORT_MULTIPLIER = 2
        private const val SEARCH_PROMPT_TOP_MARGIN_DP = 32
        private const val SEARCH_PROMPT_HORIZONTAL_PADDING_DP = 24
        private const val SEARCH_PROMPT_VERTICAL_PADDING_DP = 16
        private const val PREF_DEFAULT_SORT_ORDER = "default_sort_order"
        private const val PREF_DEFAULT_VIEW_MODE = "default_view_mode"
        private const val PREF_DEFAULT_GROUP_BY = "default_group_by"

        fun createIntent(
            context: Context,
            nameQuery: String,
            typeFilter: MediaTypeFilter,
            dateRange: TimeRange?,
            sizeRangeBytes: LongRange?
        ): Intent {
            return Intent(context, GlobalSearchResultsActivity::class.java).apply {
                putExtra(EXTRA_NAME_QUERY, nameQuery)
                putExtra(EXTRA_TYPE_FILTER, typeFilter.name)
                dateRange?.let { range ->
                    putExtra(EXTRA_HAS_DATE_RANGE, true)
                    putExtra(EXTRA_DATE_START_MS, range.startMsInclusive)
                    putExtra(EXTRA_DATE_END_MS, range.endMsExclusive)
                }
                sizeRangeBytes?.let { range ->
                    putExtra(EXTRA_HAS_SIZE_RANGE, true)
                    putExtra(EXTRA_SIZE_MIN_BYTES, range.first)
                    putExtra(EXTRA_SIZE_MAX_BYTES, range.last)
                }
            }
        }

    }
}
