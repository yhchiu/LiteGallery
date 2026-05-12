# Large Folder Loading Strategy

> Target Issue: How to maintain high performance, low memory usage, and zero scrolling "jank" (stuttering) under a "windowed loader" framework when a single view contains more than 50,000 (or even 200,000+) media items.
> Three Main Pillars:
> 1. **Batched Emit**: Fast cold start to show the first screen quickly, while continuing to load in the background.
> 2. **ID-only Skeleton + LRU Metadata Cache**: Store only skeletons in memory, and look up metadata only when needed.
> 3. **Bypass DiffUtil for Single Item Updates**: Avoid temporary double-sized list copies to prevent Out of Memory (OOM) errors.
> 
> 

---

## 0. Terminology & Assumptions

* **MediaItem**: Full metadata (id, uri, path, mime, date, size, w, h, duration, exif, etc.). Approximately 200–400 bytes per item.
* **MediaItemSkeleton**: Contains only the ID and sort key (date or name). Approximately 24 bytes per item.
* **Adapter**: `MediaAdapter` (currently your `ListAdapter<MediaItem, …>`).
* **Target Environment**: minSdk 24, targetSdk 36. It should run smoothly with 200,000 items even on low-end devices with 2 GB RAM.

---

## 1. Batched Emit — Cold Start to First Screen < 300 ms

### The Problem

Fetching 200,000 items from a Cursor, creating objects, and sorting them takes about 1–3 seconds on the main flow. During this time, the user just sees a loading spinner.

### The Strategy

Data fetching is divided into **three phases**:

```
Phase A: MediaStore Cursor fetches the first N=500 items → immediately emit to the UI (First Screen).
Phase B: Continue fetching the rest in the background → emit when the chunk size is reached (once every 5,000 items).
Phase C: Fully completed → final emit + start calculating the fast scroller index.

```

### Kotlin Pseudocode (Extension for FolderMediaRepository.kt)

```kotlin
sealed class LoadEvent {
    data class FirstScreen(val items: List<MediaItemSkeleton>) : LoadEvent()
    data class Progress(val items: List<MediaItemSkeleton>, val isFinal: Boolean) : LoadEvent()
    data class Failed(val error: Throwable) : LoadEvent()
}

class FolderMediaRepository(...) {
    companion object {
        private const val FIRST_SCREEN_LIMIT = 500
        private const val CHUNK_SIZE = 5_000
    }

    fun loadFolderStreamed(folder: MediaFolder): Flow<LoadEvent> = flow {
        val acc = ArrayList<MediaItemSkeleton>(50_000)
        val cursor = contentResolver.query(uri, SKELETON_PROJECTION, ...)
            ?: return@flow

        cursor.use {
            var firstScreenSent = false
            var lastEmit = 0
            while (it.moveToNext()) {
                acc += MediaItemSkeleton(
                    id = it.getLong(idCol),
                    dateModified = it.getLong(dateCol),
                    isVideo = ...
                )
                // Emit the first screen with high priority
                if (!firstScreenSent && acc.size >= FIRST_SCREEN_LIMIT) {
                    emit(LoadEvent.FirstScreen(acc.toList()))
                    firstScreenSent = true
                    lastEmit = acc.size
                }
                // Later, emit once every CHUNK_SIZE (without blocking the cursor)
                else if (firstScreenSent && acc.size - lastEmit >= CHUNK_SIZE) {
                    emit(LoadEvent.Progress(acc.toList(), isFinal = false))
                    lastEmit = acc.size
                }
            }
            emit(LoadEvent.Progress(acc.toList(), isFinal = true))
        }
    }.flowOn(Dispatchers.IO)
}

```

### Consumer on the Activity Side

```kotlin
lifecycleScope.launch {
    repository.loadFolderStreamed(folder).collect { event ->
        when (event) {
            is LoadEvent.FirstScreen -> {
                // There is something to see immediately; the user can scroll
                mediaAdapter.submitSkeletonList(event.items)
                binding.progressBar.isVisible = true   // Still loading in the background
            }
            is LoadEvent.Progress -> {
                mediaAdapter.submitSkeletonList(event.items)
                if (event.isFinal) {
                    binding.progressBar.isVisible = false
                    fastScroller.buildIndex(event.items)   // Only build index when the list is complete
                }
            }
            is LoadEvent.Failed -> showError(event.error)
        }
    }
}

```

### Key Points

* **No sorting on the first screen**: Just use MediaStore's default `DATE_MODIFIED DESC`. When the user changes the sorting, sort the whole batch then.
* **Do not use LiveData**: LiveData will drop intermediate values if emitted too frequently; use Flow + `flowOn(Dispatchers.IO)`.
* **Chunk size tuning**: 5,000 is a trade-off. If it's too small, frequent emits trigger DiffUtil too often. If it's too large, the app feels laggy after the first screen.
* **Always use `cursor.use { }**`: If you don't close a 200,000-item cursor, it will cause window memory leaks (the worst kind of memory leak).

---

## 2. ID-only Skeleton + LRU Metadata Cache

### The Problem

Keeping full `MediaItem` objects for 200,000 items takes about 40 MB. Adding the temporary lists needed for DiffUtil brings the peak to about 90 MB. This is dangerous for low-end phones.

### The Strategy

**Store only the skeleton in memory (24 bytes × 200,000 ≈ 4.8 MB)**. Look up the full metadata only inside `onBindViewHolder`. Use an LRU Cache to store the latest 1,000 items (the hit rate is 99% because users only view items near the current screen).

### Data Structure

```kotlin
data class MediaItemSkeleton(
    val id: Long,                 // 8 bytes
    val dateModified: Long,       // 8
    val isVideo: Boolean,         // 1
    val sortHash: Int = 0         // 4, pre-calculated sort key (like filename hash)
)
// Object header + padding ≈ 24 bytes/item

```

### Adapter Refactoring

```kotlin
class MediaAdapter(...) : ListAdapter<MediaItemSkeleton, RecyclerView.ViewHolder>(SkeletonDiff()) {

    private val metadataCache = LruCache<Long, MediaItem>(1_000)
    private val pendingFetches = mutableSetOf<Long>()

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val skel = getItem(position)
        val cached = metadataCache.get(skel.id)
        if (cached != null) {
            bindFull(holder, cached, skel)
        } else {
            bindPlaceholder(holder, skel)   // Gray background + isVideo icon, keep the cell from being empty
            fetchMetadataAsync(skel.id, holder)
        }
    }

    private fun fetchMetadataAsync(id: Long, holder: ViewHolder) {
        if (id in pendingFetches) return
        pendingFetches += id
        scope.launch {
            val item = withContext(Dispatchers.IO) { dao.findById(id) } ?: return@launch
            metadataCache.put(id, item)
            pendingFetches -= id
            // Use payload to update only that cell (do not refresh the whole list)
            notifyItemChanged(holder.bindingAdapterPosition, PAYLOAD_META_LOADED)
        }
    }
}

```

### Glide Thumbnail Loading

Thumbnail requests **only need the ID and isVideo flag**. They do not need to wait for metadata:

```kotlin
private fun bindPlaceholder(holder: GridViewHolder, skel: MediaItemSkeleton) {
    val uri = if (skel.isVideo)
        ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, skel.id)
    else
        ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, skel.id)

    Glide.with(holder.itemView)
        .load(uri)
        .override(thumbSize, thumbSize)
        .centerCrop()
        .placeholder(R.drawable.bg_card)
        .into(holder.binding.imageView)
}

```

→ **You don't need to wait for the DB to look up metadata to show thumbnails.** Users will feel the app opens "instantly."

### Add a Batch Query to Room DAO

```kotlin
@Dao
interface MediaIndexDao {
    @Query("SELECT * FROM media_items WHERE mediaStoreId IN (:ids)")
    suspend fun findByIds(ids: List<Long>): List<MediaIndexEntity>

    @Query("SELECT * FROM media_items WHERE mediaStoreId = :id LIMIT 1")
    suspend fun findById(id: Long): MediaIndexEntity?
}

```

### Advanced: Prefetch Window Metadata

Attach a `RecyclerView.OnScrollListener`. When the first or last visible item changes, **batch query 50 items before and after the visible window**:

```kotlin
recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
    override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
        val lm = rv.layoutManager as GridLayoutManager
        val first = lm.findFirstVisibleItemPosition()
        val last = lm.findLastVisibleItemPosition()
        val from = (first - 20).coerceAtLeast(0)
        val to = (last + 20).coerceAtMost(adapter.itemCount - 1)
        adapter.prefetchMetadataRange(from, to)
    }
})

```

### Memory Test Results (Theoretical)

| Data Scale | Skeleton Array | LRU 1,000 Items | DiffUtil Temp List | Total Peak |
| --- | --- | --- | --- | --- |
| 50,000 | 1.2 MB | 0.4 MB | 2.4 MB | **~4 MB** |
| 200,000 | 4.8 MB | 0.4 MB | 9.6 MB | **~15 MB** |
| 500,000 | 12 MB | 0.4 MB | 24 MB | **~36 MB** |

→ Even 500,000 items are still in the safe zone.

---

## 3. Bypass DiffUtil for Single Item Updates

### The Problem

If an item is renamed, deleted, or moved, calling `submitList(newFullList)` forces DiffUtil to calculate changes for 200,000 items. This takes 300 ms–2 seconds and creates a temporary double copy of the list.

### The Strategy

**For all "single item" changes, call `notifyItem*` directly.** Only use DiffUtil for "global" changes, like changing the sort order, switching groups, or refreshing the whole list.

### Maintain a Mutable Backing List in the Adapter

`ListAdapter` doesn't provide an API to modify the list directly. Instead, manage your own list + `AsyncListDiffer`:

```kotlin
class MediaAdapter(...) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val backing = mutableListOf<MediaItemSkeleton>()
    private val differ = AsyncListDiffer(this, AsyncDifferConfig.Builder(SkeletonDiff()).build())

    fun submitListFull(items: List<MediaItemSkeleton>) {
        // Global changes go through DiffUtil
        backing.clear()
        backing += items
        differ.submitList(items.toList())
    }

    /** Single item rename: metadata changes but ID stays the same → partial refresh with payload */
    fun onItemRenamed(id: Long, newName: String) {
        metadataCache.get(id)?.let { old ->
            metadataCache.put(id, old.copy(displayName = newName))
        }
        val pos = backing.indexOfFirst { it.id == id }
        if (pos >= 0) notifyItemChanged(pos, PAYLOAD_NAME)
    }

    /** Single item delete */
    fun onItemDeleted(id: Long) {
        val pos = backing.indexOfFirst { it.id == id }
        if (pos < 0) return
        backing.removeAt(pos)
        metadataCache.remove(id)
        // Sync the list inside differ (without triggering full DiffUtil)
        val newList = backing.toList()
        differ.submitList(newList)   // Since only one item changed, DiffUtil uses a fast path
        // Or faster: notify directly (Risk: differ's internal list might go out of sync → not recommended)
        notifyItemRemoved(pos)
    }

    /** Batch delete (e.g., multi-select) */
    fun onItemsDeleted(ids: Set<Long>) {
        val removed = mutableListOf<Int>()
        for (i in backing.indices.reversed()) {
            if (backing[i].id in ids) {
                backing.removeAt(i)
                removed += i
            }
        }
        // Calling notifyItemRangeRemoved on a reversed list is more accurate
        for (pos in removed) notifyItemRemoved(pos)
    }
}

```

> ⚠️ **Important**: Mixing `AsyncListDiffer` and manual `notifyItem*` calls can break the differ's internal state. In practice, there are two safe options:
> * **Option A (Recommended)**: Always use `differ.submitList(newList)`, but force the **fast path** for single changes. If the list only has a one-item difference, DiffUtil performs at O(N). For 200k items, this takes about 100 ms, which is acceptable.
> * **Option B**: Abandon `AsyncListDiffer` entirely. Maintain the backing list yourself and call the appropriate `notifyItem*` for all changes. The trade-off is that you must handle thread safety yourself.
> 
> 

### Payload Mechanism — Avoid Re-inflating Views

```kotlin
companion object {
    const val PAYLOAD_NAME = "name"
    const val PAYLOAD_META_LOADED = "meta"
    const val PAYLOAD_SELECTION = "sel"
}

override fun onBindViewHolder(holder: ViewHolder, position: Int, payloads: List<Any>) {
    if (payloads.isEmpty()) {
        onBindViewHolder(holder, position)   // Full bind
        return
    }
    val skel = getItem(position)
    for (payload in payloads) when (payload) {
        PAYLOAD_NAME -> (holder as? ListViewHolder)?.updateName(skel)
        PAYLOAD_META_LOADED -> bindMetadata(holder, skel)
        PAYLOAD_SELECTION -> holder.updateSelectionState(skel)
    }
}

```

### Payload Partial Update Policy

For row-level UI state changes that do not alter list structure, sorting, grouping, or filtering, use targeted adapter updates instead of replacing the whole list. Examples include metadata loaded, favorite state, cloud sync status, selection state, or a visible name refresh.

* Add a dedicated payload constant for each row-level state change.
* Update the adapter's backing state or shared cache first, then call `notifyItemChanged(position, payload)`.
* Handle the payload in `onBindViewHolder(holder, position, payloads)` and redraw only the affected views.
* Avoid `submitList` and `notifyDataSetChanged` for high-frequency metadata, status, or single-row visual
updates.

Full list replacement is still allowed for structural changes, including initial load, final sort/group transform, view mode changes, rebuilding grouped display items, insertion/removal, or refreshing the whole folder.

---

## 4. Fast Scroller Index — Asynchronous Building

Grouping 200,000 items by date for the `FastScrollSection` takes about 200–400 ms. **This must not be done on the main thread.**

```kotlin
private suspend fun buildIndex(items: List<MediaItemSkeleton>): FastScrollIndex =
    withContext(Dispatchers.Default) {
        val sections = TreeMap<String, IntRange>()
        var currentLabel = ""
        var start = 0
        items.forEachIndexed { i, item ->
            val label = formatYearMonth(item.dateModified)
            if (label != currentLabel && currentLabel.isNotEmpty()) {
                sections[currentLabel] = start until i
                start = i
            }
            currentLabel = label
        }
        if (currentLabel.isNotEmpty()) sections[currentLabel] = start until items.size
        FastScrollIndex(sections)
    }

```

**Do not show the fast scroller on the first screen.** Wait until Phase C (all data has arrived) and the index calculation is finished, then fade it in.

---

## 5. RecyclerView Settings (One-time setup)

```kotlin
binding.recyclerView.apply {
    setHasFixedSize(true)
    setItemViewCacheSize(20)
    // Shared pool across the whole app (prevents re-inflating when switching Activities)
    recycledViewPool = LiteGalleryApplication.sharedViewPool
    // Preload 20 thumbnails before and after
    addOnScrollListener(RecyclerViewPreloader(
        Glide.with(this@FolderViewActivity),
        thumbnailPreloadProvider,
        ViewPreloadSizeProvider<Uri>(thumbSize, thumbSize),
        20
    ))
}

```

---

## 6. Controlling the Cost of Sorting Switches

When changing the sort order, you cannot avoid re-sorting the whole list (O(N log N) ≈ 50 ms for 200k items). However:

```kotlin
fun changeSortOrder(order: SortOrder) {
    lifecycleScope.launch {
        val sorted = withContext(Dispatchers.Default) {
            backing.sortedWith(order.comparator)
        }
        backing.clear()
        backing += sorted
        // The whole order changed → DiffUtil isn't worth it, just call notifyDataSetChanged
        notifyDataSetChanged()
        // Rebuild the fast scroller index
        fastScroller.setIndex(buildIndex(sorted))
    }
}

```

When the entire sorting order changes, using DiffUtil actually hurts performance. Calling `notifyDataSetChanged()` along with proper placeholders provides a much better user experience.

---

## 7. Checklist

Check these off when implementing:

* [ ] `MediaItemSkeleton` is defined; removed unused fields for the grid.
* [ ] `FolderMediaRepository.loadFolderStreamed` uses Flow for batched emits.
* [ ] First screen emits ≤ 500 items, subsequent emits happen every 5,000 items.
* [ ] `MediaAdapter` switched to use skeleton + LRU metadata cache (capacity 1,000).
* [ ] `MediaIndexDao.findByIds(ids)` batch query has been added.
* [ ] Glide loads thumbnails **without** waiting for full metadata to load.
* [ ] `onScrollListener` prefetches metadata for 50 items around the visible window.
* [ ] Renames, deletions, and single-item changes use `notifyItem*` + payload instead of a full DiffUtil pass.
* [ ] Fast scroller index is calculated in `Dispatchers.Default` and shown only after completion.
* [ ] Applied `setHasFixedSize(true)` + `setItemViewCacheSize(20)` + shared `RecycledViewPool`.
* [ ] Sort switching uses `notifyDataSetChanged()` (bypasses DiffUtil).
* [ ] All Cursors are properly wrapped inside `cursor.use { }`.

---

## 8. When to Abandon this Strategy for Paging 3

Only consider switching to Paging 3 if any of these are true:

1. A single view contains > 500,000 items (extremely rare).
2. The data source is over a network (like SMB lists or cloud search) → Use Paging for those specific parts.
3. You have implemented all the optimizations above but still have proof of OOMs / jank (measured using a profiler).

Otherwise, a windowed loader + ID-only skeleton is the best solution for LiteGallery's scale.

---

## 9. Expected Performance

After completing these optimizations, the target metrics (tested with 200,000 items on a Pixel 5-level device) are:

| Metric | Target |
| --- | --- |
| Cold start to first screen | < 300 ms |
| Cold start to fully loaded | < 2 s |
| Scrolling FPS | Stable 60 fps (might drop to 50 if thumbnails aren't cached) |
| Fast scroll jump delay | < 16 ms |
| Refresh delay after renaming one item | < 50 ms |
| Peak memory | < 20 MB (excluding Glide bitmap pool) |
| Sorting switch time | < 100 ms |

---
