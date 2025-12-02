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
