# Trash and Delete Strategy

## Purpose
This document explains the current app behavior for:
- Move to Trash
- Delete Permanently
- Restore from Trash

It also explains platform differences (Android 9 and below, Android 10, Android 11+).

## Core Design
The app uses two trash systems:
1. App Trash (same-folder rename with `.trashed-` prefix)
2. System Trash (MediaStore `IS_TRASHED`)

App Trash is now URI-first:
- Local files are stored as `file://` URI records.
- SAF documents are stored as `content://` URI records.

Routing rules:
1. MediaStore-backed media on Android 11+ uses System Trash first.
2. Non-MediaStore items use App Trash when rename is supported.
3. Permanent delete is a separate path and is not used as implicit fallback when trash option is explicitly selected.

## Terms
- App Trash:
  The app renames the original item in the same folder with `.trashed-` prefix and stores a URI record.
- System Trash:
  MediaStore trash state (`IS_TRASHED=1`) and related system APIs.
- MediaStore-backed item:
  An item that can be resolved to a MediaStore URI (`content://media/...`).
- URI-first trash record:
  Stored metadata includes `trashed_uri`, `original_uri`, `original_name`, `original_path_hint`, `trashed_at`.

## Viewer Delete Flow (Single Item)
Entry point: delete dialog in Media Viewer.

### 1) Dialog behavior
1. The app checks if "Move to Trash" is supported for the current item.
2. If supported, the checkbox is shown and previous user choice is restored.
3. If not supported, the checkbox is hidden and only permanent delete is offered.

### 2) If user selects "Move to Trash"
1. Content URI item:
   - If MediaStore-backed and Android 11+: use System Trash (`IS_TRASHED=1` or system consent request).
   - Else if it is a SAF Document URI: rename document to `.trashed-...` and store App Trash record.
2. File path item:
   - If Android 11+ and MediaStore URI can be resolved: use System Trash.
   - Else: rename local file to `.trashed-...` and store App Trash record (as `file://` URI).
3. On success:
   - Remove item from viewer list.
   - Mark media collection changed.
   - Notify scanner for local file paths when applicable.

### 3) If user selects "Delete Permanently"
1. For `content://` items:
   - Try provider delete (`DocumentsContract.deleteDocument` / `ContentResolver.delete`).
   - If blocked by scoped storage policy, request system user consent when supported (`MediaStore.createDeleteRequest` on Android 11+, `RecoverableSecurityException` on Android 10).
2. For file-path items:
   - Try `file.delete()` first.
   - If failed and MediaStore-backed URI exists, try MediaStore delete path (with user consent when needed).

## Trash Bin Page (Unified Bin)
The Trash page shows a merged list from both sources:
1. App Trash items from URI-first `TrashBinStore` records.
2. System Trash items (MediaStore query `IS_TRASHED=1`, Android 11+).

The list is merged and sorted by modified time.

Source classification rule:
- If an item exists in the App Trash record map, it is handled as App Trash.
- Otherwise it is handled as System Trash.

This avoids incorrect classification based only on `content://` prefix.

## Trash Bin Actions
The app routes actions by item source.

### 1) Restore
- App Trash item:
  1. Local file record: rename file back to original name.
  2. SAF document record: rename document back to original name.
  3. Handle name conflict with indexed name when needed.
  4. Remove record from `TrashBinStore`.
- System Trash item:
  1. Set `IS_TRASHED=0` (or use system consent request when required).

### 2) Delete Permanently
- App Trash item:
  1. Local file record: physical file delete.
  2. SAF document record: provider delete (`DocumentsContract.deleteDocument` / `ContentResolver.delete`).
  3. Remove record from `TrashBinStore`.
- System Trash item:
  1. MediaStore delete.
  2. Use system consent request when required.

### 3) Bulk actions
Bulk restore/delete split input into two groups:
1. App Trash group -> URI-aware restore/delete logic.
2. System Trash group -> MediaStore restore/delete logic.

If system user consent is required, the app launches one consent flow and refreshes list after result.

## Retention and Cleanup
1. Retention is controlled by `trash_retention_days`.
2. Expired App Trash records are processed by URI:
   - local file URI -> file delete
   - content URI -> provider delete
3. Missing targets are treated as stale and records are removed.
4. Cleanup reports removed scanner paths so UI callers can notify MediaScanner only for valid local paths.

## Data Model and Upgrade Policy
1. Trash DB schema is URI-first and replaces old path-based schema.
2. Upgrade policy is destructive (`drop + recreate`).
3. No old data migration logic is kept in app code.

## Version Matrix

### Android 11+ (API 30+)
1. Full System Trash behavior is available.
2. App supports mixed trash model:
   - MediaStore items -> System Trash preferred.
   - Non-MediaStore local files and SAF docs -> App Trash.
3. Trash page can show both App Trash and System Trash.

### Android 10 (API 29)
1. No complete System Trash API support.
2. Move to Trash behavior:
   - Local files and SAF docs -> App Trash (rename-based).
   - MediaStore system trash path is not fully available.
3. Delete behavior:
   - Uses delete APIs and can request user consent via `RecoverableSecurityException` when needed.
4. Trash page mainly represents App Trash.

### Android 9 and below (API <= 28)
1. No System Trash API.
2. Move to Trash uses App Trash for local files.
3. Provider-backed `content://` trash depends on document provider rename support.
4. Permanent delete uses file/provider delete only.

## Privacy and Performance Notes
1. App Trash avoids cross-folder moves; it renames in place.
2. System Trash uses platform-managed metadata/state.
3. Heavy operations run off main thread.
4. UI refresh propagates to parent lists through `RESULT_MEDIA_CHANGED`.
5. URI-first records avoid storing large file copies in app private space.

## Related Files
- `app/src/main/java/org/iurl/litegallery/MediaViewerActivity.kt`
- `app/src/main/java/org/iurl/litegallery/TrashBinActivity.kt`
- `app/src/main/java/org/iurl/litegallery/TrashBinStore.kt`
- `app/src/main/java/org/iurl/litegallery/TrashBinDatabase.kt`
- `app/src/main/java/org/iurl/litegallery/MainActivity.kt`
