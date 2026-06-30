# LiteGallery

A lightweight, fast, and privacy-first gallery app for Android that helps you browse and manage your photos and videos.

## Editions

LiteGallery builds in two product flavors, so you can choose your own feature/trust trade-off:

| Edition | Network | Use it if |
| --- | --- | --- |
| **Core** (default) | **None** — declares no `INTERNET` permission and bundles no networking code | You want a provably offline, network-free gallery |
| **Plus** | Adds optional **SMB** network-share browsing/streaming (requests `INTERNET`) | You need to view media from a NAS / Windows share |

Both editions share all local-gallery functionality; only the optional SMB feature — and the network permission it requires — is exclusive to **Plus**.

## Privacy First

**The Core edition is built so your data can never leave the device:**
- ✅ **No internet permission** - The Core build declares no `INTERNET` permission, so it cannot make network connections
- ✅ **No background services** - No sneaky background activities
- ✅ **Only storage permissions** - We only access what's necessary to show your media
- ✅ **No ads, no tracking** - 100% ad-free and tracker-free
- ✅ **No data collection** - We don't collect, store, or share any personal information
- ✅ **Offline-only** - Works completely offline, no server connections ever

**All your photos and videos stay on your device, always.**

> The **Plus** edition adds opt-in SMB network-share support and therefore requests the `INTERNET` permission. If a verifiable network-free build matters to you, use **Core**.

## Features

### Media Browsing
- **Folder-based organization** - Browse your media files organized by folders
- **Multiple view modes** - Switch between Grid, List, and Detailed views
- **Flexible sorting** - Sort by date or name, in ascending or descending order
- **Fast media scanning** - Quickly scan and display all your media files

### Media Viewing
- **Image viewer** - View images with smooth zooming and panning
- **Video player** - Play videos with customizable gesture controls
- **Fullscreen mode** - Immersive viewing experience

### Network Shares (Plus edition)
- **SMB browsing** - Browse photos and videos on SMB / Windows network shares
- **Streaming playback** - Stream videos directly from a share with seek support
- **Saved servers** - Bookmark servers with guest or username/password login

### File Management
- **Quick rename** - Easily rename files with prefix/suffix shortcuts
- **Rename history** - Access recently used names for faster renaming
- **Settings backup/restore** - Export and import your app settings

### Customization
- **Theme options** - Choose between Light, Dark, or Auto (follow system) themes
- **Color themes** - Multiple color themes to personalize your experience
- **Customizable gestures** - Configure video player gestures for tap, double-tap, and swipe actions
- **Adjustable zoom levels** - Set maximum zoom scale for images
- **Customizable action bar** - Personalize your action bar options

### Privacy & Permissions
**Minimal permissions for maximum privacy:**
- **Core edition — storage permissions only** - No network, camera, microphone, location, or any other invasive permissions
- **Required permissions**:
  - 📷 Read Media Images (Android 13+) - To display your photos
  - 🎥 Read Media Video (Android 13+) - To display your videos
  - 📁 Read External Storage (Android 12 and below) - To access your media files
  - 📂 Manage External Storage (optional) - Only if you want to access non-media folders
- **Plus edition** additionally requests `INTERNET` and `ACCESS_NETWORK_STATE`, used solely for SMB network shares.

**Core edition: no hidden permissions, no background access, no internet connection.**

## Requirements

- Android 7.0 (API level 24) or higher
- Storage permissions for accessing media files

## Installation

### Build from Source

1. Clone the repository:
```bash
git clone https://github.com/yhchiu/LiteGallery.git
cd LiteGallery
```

2. Open the project in Android Studio

3. Build the project:
   - Click **Build > Make Project** or press `Ctrl+F9` (Windows/Linux) or `Cmd+F9` (Mac)

4. Run on device/emulator:
   - Click **Run > Run 'app'** or press `Shift+F10` (Windows/Linux) or `Ctrl+R` (Mac)

### Select an Edition in Android Studio

LiteGallery variants combine one product flavor (`core` or `plus`) with one build type (`debug` or `release`):

| Variant | Use it for |
| --- | --- |
| `coreDebug` | Running/debugging the network-free Core edition |
| `plusDebug` | Running/debugging the Plus edition with SMB support |
| `coreRelease` | Building a release APK/AAB for the network-free Core edition |
| `plusRelease` | Building a release APK/AAB for the Plus edition with SMB support |

To switch editions in Android Studio:

1. Open **View > Tool Windows > Build Variants**.
2. In the `app` module row, open the **Active Build Variant** dropdown.
3. Choose `coreDebug` or `plusDebug` before clicking **Run**.
4. If the variants are missing, click **Sync Project with Gradle Files** and reopen the Build Variants panel.

### Build APK

LiteGallery has two product flavors: **core** (network-free) and **plus** (adds SMB).

```bash
# Network-free edition (declares no INTERNET permission)
./gradlew assembleCoreRelease    # -> app/build/outputs/apk/core/release/

# Edition with SMB network shares
./gradlew assemblePlusRelease    # -> app/build/outputs/apk/plus/release/
```

`./gradlew assembleRelease` builds both flavors at once. In Android Studio, pick
`coreDebug` or `plusDebug` in the **Build Variants** panel to run a specific edition.

## Testing and Coverage

LiteGallery currently uses JVM unit tests under `app/src/test` for pure Kotlin/Java logic and Robolectric-backed Android tests. There are no committed instrumentation tests under `app/src/androidTest` yet.

### Run all unit tests

The project has two flavors (`core`, `plus`), so unit tests run per flavor.
SMB-related tests live in the `plus` test source set (`app/src/testPlus`) and run
only under `testPlusDebugUnitTest`.

On Windows PowerShell:
```powershell
.\gradlew.bat testCoreDebugUnitTest testPlusDebugUnitTest
```

On macOS/Linux:
```bash
./gradlew testCoreDebugUnitTest testPlusDebugUnitTest
```

HTML test reports are generated per flavor at:
```text
app/build/reports/tests/testCoreDebugUnitTest/index.html
app/build/reports/tests/testPlusDebugUnitTest/index.html
```

Raw JUnit XML files are generated at:
```text
app/build/test-results/testCoreDebugUnitTest/
app/build/test-results/testPlusDebugUnitTest/
```

### Run a single test class or method

Windows PowerShell examples:
```powershell
.\gradlew.bat testPlusDebugUnitTest --tests org.iurl.litegallery.SmbPathTest
.\gradlew.bat testCoreDebugUnitTest --tests "org.iurl.litegallery.MediaUriPathResolverTest.resolveRealPath_returnsFilePathForFileScheme"
```

macOS/Linux examples:
```bash
./gradlew testPlusDebugUnitTest --tests org.iurl.litegallery.SmbPathTest
./gradlew testCoreDebugUnitTest --tests "org.iurl.litegallery.MediaUriPathResolverTest.resolveRealPath_returnsFilePathForFileScheme"
```

### Generate the JaCoCo coverage report

This task builds and covers the `plus` variant (the superset that also includes the SMB code).

On Windows PowerShell:
```powershell
.\gradlew.bat jacocoTestDebugUnitTestReport
```

On macOS/Linux:
```bash
./gradlew jacocoTestDebugUnitTestReport
```

Coverage outputs:
```text
app/build/reports/jacoco/jacocoTestDebugUnitTestReport/html/index.html
app/build/reports/jacoco/jacocoTestDebugUnitTestReport/jacocoTestDebugUnitTestReport.xml
```

The HTML report is best for local inspection. The XML report is intended for CI systems or coverage dashboards.

### Current unit test focus

The current JVM test suite covers:
- Settings export/import behavior
- Custom theme color resolution and theme helpers
- MediaStore projection builders and URI path resolution
- Trash bin persistence and cleanup behavior
- File-system media scanning helpers
- SMB path parsing, SMB config persistence, and SMB Glide image detection (plus flavor)
- Locale, playback diagnostics, and basic media model behavior

### Remaining test gaps

These areas need additional coverage:
- Activity workflows such as media viewer rename/delete/trash, trash restore/delete selection, settings UI summaries, and folder sorting interactions
- Instrumentation tests for permission flows, Android document/tree URI access, and end-to-end UI behavior on a device or emulator
- MediaStore delete/trash user-action intent flows, especially scoped storage behavior on recent Android versions
- SMB runtime I/O tests with fakeable client boundaries or a controlled test server
- Larger integration tests around external content URIs, SAF tree grants, and fallback scanning

Some of these gaps can be covered with more Robolectric tests, but the larger Activity and SMB runtime areas will be easier to test after small production-code refactors that separate UI code from file, MediaStore, and network boundaries.

### Notes

- Android Gradle Plugin 8.2.2 may warn that it was tested up to `compileSdk = 34` while this project compiles with SDK 36. This warning does not by itself mean the test run failed.
- If Gradle wrapper cache locking fails on Windows with a `.zip.lck` access error, stop Gradle daemons and retry:
```powershell
.\gradlew.bat --stop
.\gradlew.bat testDebugUnitTest
```

## Roadmap

- [ ] Trash bin functionality
- [ ] Image editing capabilities
- [ ] Slideshow mode
- [ ] Search functionality
- [ ] Album creation and management

## License

This project is licensed under the GNU General Public License v3 (GPL v3).

Copyright (C) Yu-Hsiung Chiu

## Support

If you find this project helpful, please consider:
- Starring the repository
- Reporting bugs or requesting features via [Issues](https://github.com/yhchiu/LiteGallery/issues)
- Contributing to the project
