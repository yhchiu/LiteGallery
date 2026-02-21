# Trash and Delete Strategy

## Purpose
This document explains the app's full behavior for:
- Move to Trash
- Delete Permanently

It includes platform differences (Android 9 and below, Android 10, Android 11+).

## Core Design
The app uses two trash systems:
1. App Trash (file rename in the same folder)
2. System Trash (MediaStore `IS_TRASHED`)

Routing rule:
1. MediaStore-backed media should use System Trash when available.
2. Non-MediaStore local files should use App Trash.
3. If trash is not supported for a source/platform, the app falls back to permanent delete logic.

## Terms
- App Trash:
  The app renames a local file in the same folder with `.trashed-` prefix.
- System Trash:
  MediaStore trash state (`IS_TRASHED=1`) and related system APIs.
- MediaStore-backed item:
  An item that can be resolved to a MediaStore URI (`content://media/...`).

## Viewer Delete Flow (Single Item)
Entry point: delete dialog in Media Viewer.

### 1) Dialog behavior
1. The app checks if "Move to Trash" is supported for the current item.
2. If supported, the checkbox is shown and previous user choice is restored.
3. If not supported, the checkbox is hidden and only permanent delete is offered.

### 2) If user selects "Move to Trash"
1. If item is MediaStore-backed and Android is 11+:
   - Try System Trash first (`IS_TRASHED=1` or user-consent trash request).
2. Else if item is a non-MediaStore local file path:
   - Use App Trash: rename to `.trashed-<original>` in the same folder.
   - Save trash metadata to `TrashBinStore`.
3. If source does not support trash:
   - Fall back to delete path.

### 3) If user selects "Delete Permanently"
1. For `content://` items:
   - Try provider delete (`DocumentsContract.deleteDocument` / `ContentResolver.delete`).
   - If blocked by scoped storage policy, request system user consent when supported.
2. For file-path items:
   - Try `file.delete()` first.
   - If failed and item is MediaStore-backed, try MediaStore delete path (with user consent when needed).

### 4) On success
1. Mark media collection as changed.
2. Remove the item from current viewer list.
3. Notify media scanner for file-path updates when needed.
4. Return `RESULT_MEDIA_CHANGED` to parent pages.

## Trash Bin Page (Unified Bin)
The Trash page shows a merged list from both sources:
1. App Trash items (`.trashed-` files tracked in `TrashBinStore`).
2. System Trash items (MediaStore query `IS_TRASHED=1`, Android 11+).

The list is merged, deduplicated by path, and sorted by modified time.

## Trash Bin Actions
The app routes actions by item source.

### 1) Restore
- App Trash item:
  1. Rename file back to original name.
  2. Handle name conflict (overwrite or auto new name).
  3. Remove record from `TrashBinStore`.
- System Trash item:
  1. Set `IS_TRASHED=0` (or use system consent request when required).

### 2) Delete Permanently
- App Trash item:
  1. Physical file delete.
  2. Remove record from `TrashBinStore`.
- System Trash item:
  1. MediaStore delete.
  2. Use system consent request when required.

### 3) Bulk actions
Bulk restore/delete split input into two groups:
1. App Trash group -> file rename/delete logic.
2. System Trash group -> MediaStore restore/delete logic.

If system user consent is required, the app launches one consent flow and refreshes list after result.

## Version Matrix

### Android 11+ (API 30+)
1. Full System Trash behavior is available.
2. App supports mixed trash model:
   - MediaStore items -> System Trash preferred.
   - Non-MediaStore local files -> App Trash.
3. Trash page can show both App Trash and System Trash.

### Android 10 (API 29)
1. No complete System Trash API support.
2. Move to Trash behavior:
   - Non-MediaStore local files -> App Trash.
   - MediaStore system trash path is not fully available.
3. Delete behavior:
   - Uses delete APIs and can request user consent via `RecoverableSecurityException` when needed.
4. Trash page mainly represents App Trash.

### Android 9 and below (API <= 28)
1. No System Trash API.
2. Move to Trash uses App Trash for local files.
3. Permanent delete uses file/provider delete only.
4. `content://` deletes depend on provider capability; no modern MediaStore consent flow.

## Privacy and Performance Notes
1. App Trash avoids cross-folder moves; it renames in place.
2. System Trash uses platform-managed metadata/state.
3. Heavy operations run off main thread where possible.
4. UI refresh propagates to parent lists through `RESULT_MEDIA_CHANGED`.

## Related Files
- `app/src/main/java/org/iurl/litegallery/MediaViewerActivity.kt`
- `app/src/main/java/org/iurl/litegallery/TrashBinActivity.kt`
- `app/src/main/java/org/iurl/litegallery/TrashBinStore.kt`
