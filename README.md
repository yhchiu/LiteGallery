# LiteGallery

A lightweight, fast, and privacy-first gallery app for Android that helps you browse and manage your photos and videos.

## Privacy First

**Your privacy is our priority:**
- ✅ **No internet permission** - Your data never leaves your device
- ✅ **No background services** - No sneaky background activities
- ✅ **Only storage permissions** - We only access what's necessary to show your media
- ✅ **No ads, no tracking** - 100% ad-free and tracker-free
- ✅ **No data collection** - We don't collect, store, or share any personal information
- ✅ **Offline-only** - Works completely offline, no server connections ever

**All your photos and videos stay on your device, always.**

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
- **Storage permissions only** - No network, camera, microphone, location, or any other invasive permissions
- **Required permissions**:
  - 📷 Read Media Images (Android 13+) - To display your photos
  - 🎥 Read Media Video (Android 13+) - To display your videos
  - 📁 Read External Storage (Android 12 and below) - To access your media files
  - 📂 Manage External Storage (optional) - Only if you want to access non-media folders

**That's it! No hidden permissions, no background access, no internet connection.**

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

### Build APK
```bash
./gradlew assembleRelease
```
The APK will be generated in `app/build/outputs/apk/release/`

## Testing and Coverage

LiteGallery currently uses JVM unit tests under `app/src/test` for pure Kotlin/Java logic and Robolectric-backed Android tests. There are no committed instrumentation tests under `app/src/androidTest` yet.

### Run all unit tests

On Windows PowerShell:
```powershell
.\gradlew.bat testDebugUnitTest
```

On macOS/Linux:
```bash
./gradlew testDebugUnitTest
```

The HTML test report is generated at:
```text
app/build/reports/tests/testDebugUnitTest/index.html
```

Raw JUnit XML files are generated at:
```text
app/build/test-results/testDebugUnitTest/
```

### Run a single test class or method

Windows PowerShell examples:
```powershell
.\gradlew.bat testDebugUnitTest --tests org.iurl.litegallery.SmbPathTest
.\gradlew.bat testDebugUnitTest --tests "org.iurl.litegallery.MediaUriPathResolverTest.resolveRealPath_returnsFilePathForFileScheme"
```

macOS/Linux examples:
```bash
./gradlew testDebugUnitTest --tests org.iurl.litegallery.SmbPathTest
./gradlew testDebugUnitTest --tests "org.iurl.litegallery.MediaUriPathResolverTest.resolveRealPath_returnsFilePathForFileScheme"
```

### Generate the JaCoCo coverage report

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
- SMB path parsing, SMB config persistence, and SMB Glide image detection
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
