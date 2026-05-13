# Media Index Room DB Strategy

## Purpose
This document explains why LiteGallery maintains its own Room/SQLite media index instead of querying MediaStore directly for every screen. It also describes how the index stays in sync, where it should be used, and the tradeoffs to keep in mind.

## Short Answer
MediaStore is the source of truth. The Room database is a local performance index.

The app should continue treating MediaStore as authoritative for real media state, permissions, writes, deletes, and system-level changes. The Room database exists to make common read paths fast and predictable, especially for:

- Home folder statistics
- Global media search
- Large folder loading
- Viewer snapshots
- Fast position lookup
- Windowed and streamed result loading

This is not meant to replace MediaStore. It is a cache/index layer built from MediaStore data.

## Why Not Query MediaStore Directly?
MediaStore may use SQLite internally, but apps do not query that database directly. Apps access it through `ContentResolver` and a system `ContentProvider`.

That has practical limits:

- Every query crosses a provider boundary.
- Cursor setup and cursor walking have overhead.
- SQL capabilities are limited to what MediaStore exposes.
- The app cannot add its own indexes to MediaStore.
- Behavior can vary across Android versions and OEM implementations.
- Complex search, sorting, grouping, and paging can become harder to optimize.

For simple gallery apps, querying MediaStore directly is usually enough. For LiteGallery's target behavior, especially 50k+ media items and fast global search, a local index is a useful optimization.

## What The Room DB Stores
The Room DB should store lightweight metadata only:

- Media identity and path/URI fields
- Folder path
- File name and normalized search keys
- Media type
- Modified date
- Size
- Basic dimensions or duration when available
- Folder aggregate data
- MediaStore sync checkpoint state

It should not store media file contents or bitmap thumbnails. Thumbnails should continue to be loaded lazily by the image loading layer.

## Performance Model
There are three different ways to get a total media count:

1. Already computed home stats

   `MainActivity` builds `OverviewStats.totalItems` while loading the home folder list. Reusing this value has almost no extra cost because the app already did the work for the home screen.

2. `SELECT COUNT(*) FROM media_items`

   This queries the app's own Room/SQLite database. For tens of thousands of rows, this is usually cheap, especially when executed on `Dispatchers.IO`. It is a good fallback if the home total is not available.

3. `mediaScanner.scanMediaFolders()`

   This can be more expensive. It usually tries to read indexed folders through `MediaIndexRepository.getFolders()`, but that path may call `synchronizeIfNeeded()`. If the index is stale, synchronization may touch MediaStore. If indexed folders are unavailable, it can fall back to MediaStore folder scanning.

For this reason, do not call `scanMediaFolders()` only to get a total count. Prefer the already computed home total, then fall back to an app DB count if needed.

## How Sync Works
The Room index is refreshed through `synchronizeIfNeeded()`.

The basic flow is:

1. Read the current MediaStore checkpoint, such as version and generation when supported.
2. Read the last synced checkpoint from the app DB.
3. If the checkpoint has not changed, skip synchronization.
4. If it changed, synchronize MediaStore changes into the Room tables.
5. Update the stored sync state.

This allows the app to avoid rescanning everything on every screen open.

Current sync trigger points include:

- Home folder loading
- Folder loading
- Global search
- Activity resume background sync
- Explicit refresh paths

This means external media changes may not appear instantly, but they should be picked up the next time a relevant load or resume path checks the index.

## ContentObserver
A `ContentObserver` can improve freshness by observing MediaStore changes and scheduling index invalidation or sync.

It is useful for:

- Faster visibility for media added by another app
- Better foreground freshness
- Reducing the time window where the Room index is stale

It is not a replacement for `synchronizeIfNeeded()`. The checkpoint-based sync should remain the final safety net because observers can be missed, delayed, or suppressed by lifecycle and system behavior.

## When To Use Room
Use the Room index for read-heavy app features:

- Home overview totals and folder aggregates
- Global search over indexed local media
- Fast sorting and filtering by indexed fields
- Large result streaming and windowed loading
- Reusing lightweight `MediaItemSkeleton` lists between views

Keep these reads on background dispatchers unless they are already exposed as asynchronous APIs.

## When To Use MediaStore
Use MediaStore or platform APIs when the app needs authoritative system behavior:

- Creating, deleting, trashing, or restoring media
- Requesting scoped storage user consent
- Reading system permission-controlled fields
- Resolving content URIs
- Confirming state after external changes when correctness matters

After successful writes or deletes, invalidate affected cached data and let the index synchronize.

## Risks
Maintaining a Room index adds real complexity:

- Data can become stale.
- Sync logic must handle inserts, updates, deletes, and renames.
- Schema migrations must be maintained.
- The app stores duplicate metadata.
- Bugs in sync logic can produce incorrect search or folder results.

These risks are acceptable only if the index is kept lightweight and treated as a cache, not as the final truth.

## Guardrails
Follow these rules:

- MediaStore remains the source of truth.
- Room is a performance index/cache.
- Store only lightweight metadata.
- Do not store media blobs or thumbnails in Room.
- Never block the main thread with index sync or large queries.
- Prefer streamed/windowed loading for large result sets.
- Reuse already computed totals when they are available.
- Use `SELECT COUNT(*) FROM media_items` only as a cheap fallback.
- Avoid calling folder scans just to calculate a count.
- Keep `synchronizeIfNeeded()` as the safety net even if a `ContentObserver` is added.

## Current Recommendation
For Global Search result stats, use the home screen's `OverviewStats.totalItems` when launching `GlobalSearchResultsActivity`.

This gives the same denominator as the home hero, adds no extra DB query, and avoids triggering MediaStore sync work just to display a count. If that value is not available, the app can fall back to showing only the result count or to a Room `COUNT(*)` query on a background dispatcher.
