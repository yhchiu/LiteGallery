# LiteGallery

A high-performance Android gallery application focused on speed and elegant design.

## 📱 Features

### Core Functionality
- **Folder-based Media Organization**: Browse photos and videos organized by folders
- **High-Performance Viewing**: Fast thumbnail loading with Glide caching
- **Full-Screen Media Viewer**: Immersive photo and video viewing experience
- **Video Playback**: Advanced video controls with ExoPlayer integration
- **Intent Integration**: Opens media files from other applications

### User Interface
- **Material Design 3**: Clean, modern interface following Google's design guidelines
- **Touch Controls**: Tap to show/hide UI controls in full-screen mode
- **Customizable Action Bar**: Scrollable action buttons (Delete, Share, Edit, Rotate, Properties)
- **Responsive Layout**: Optimized for various screen sizes and orientations

### Video Features
- **Frame Navigation**: Step through video frames with precision controls
- **Advanced Controls**: Brightness and contrast adjustment (Android 12+)
- **Standard Playback**: Play, pause, seek functionality
- **Double-tap Support**: Quick play/pause control

## 🛠️ Technical Specifications

- **Target SDK**: Android 36 (Android 15+)
- **Minimum SDK**: Android 24 (Android 7.0+)
- **Language**: Kotlin
- **Architecture**: Modern Android development practices with ViewBinding
- **Performance**: Coroutines for background operations, optimized image loading

## 📋 Requirements

- Android Studio Arctic Fox or later
- Android SDK 36
- Minimum device: Android 7.0 (API 24)
- Storage permissions for media access

## 🚀 Installation

### From Source
1. Clone or download the project
2. Open in Android Studio
3. Sync Gradle dependencies
4. Build and install on device

```bash
git clone <repository-url>
cd LiteGallery
# Open in Android Studio and build
```

### APK Installation
1. Enable "Install from Unknown Sources" in device settings
2. Download and install the APK file
3. Grant storage permissions when prompted

## 📖 Usage

### Basic Navigation
1. **Launch App**: View all folders containing photos/videos
2. **Browse Folder**: Tap any folder to see its contents in grid view
3. **View Media**: Tap any photo/video for full-screen viewing
4. **Navigate**: Swipe left/right to browse through media files

### Media Viewer Controls
- **Single Tap**: Show/hide interface controls
- **Action Buttons**: Access Delete, Share, Edit, Rotate, Properties
- **Video Controls**: Play/pause, seek, frame navigation
- **Menu Button**: Additional options and settings

### Video Playback
- **Play/Pause**: Tap the play button or double-tap the video
- **Seek**: Use the progress bar to jump to any position
- **Frame Step**: Use arrow buttons for precise frame navigation
- **Advanced**: Expand controls for brightness/contrast (Android 12+)

## 🔒 Permissions

The app requires the following permissions:

- **Storage Access**: To read photos and videos from device storage
  - `READ_EXTERNAL_STORAGE` (Android 12 and below)
  - `READ_MEDIA_IMAGES` and `READ_MEDIA_VIDEO` (Android 13+)
- **Write Access**: For file operations (if implemented)
  - `WRITE_EXTERNAL_STORAGE` (Android 12 and below)

## 🗂️ Project Structure

```
app/src/main/
├── java/com/litegallery/
│   ├── MainActivity.kt              # Main folder browser
│   ├── FolderViewActivity.kt        # Media grid view
│   ├── MediaViewerActivity.kt       # Full-screen viewer
│   ├── MediaScanner.kt              # Media file discovery
│   ├── MediaItem.kt & MediaFolder.kt # Data models
│   └── Adapters (FolderAdapter, MediaAdapter)
├── res/
│   ├── layout/                      # UI layouts
│   ├── drawable/                    # Icons and graphics
│   ├── values/                      # Strings, colors, themes
│   └── menu/                        # Action bar menus
└── AndroidManifest.xml
```

## 🧩 Dependencies

### Core Libraries
- **AndroidX Core**: `androidx.core:core-ktx:1.12.0`
- **AppCompat**: `androidx.appcompat:appcompat:1.6.1`
- **Material Design**: `com.google.android.material:material:1.10.0`

### Media & UI
- **Glide**: `com.github.bumptech.glide:glide:4.16.0` (Image loading)
- **ExoPlayer**: `com.google.android.exoplayer:exoplayer:2.19.1` (Video playback)
- **RecyclerView**: `androidx.recyclerview:recyclerview:1.3.2`
- **ViewPager2**: For smooth media navigation

### Utilities
- **Preferences**: `androidx.preference:preference-ktx:1.2.1`
- **EXIF**: `androidx.exifinterface:exifinterface:1.3.6`
- **Lifecycle**: `androidx.lifecycle:lifecycle-*:2.7.0`

## 🔧 Configuration

### Build Configuration
```gradle
android {
    namespace 'com.litegallery'
    compileSdk 36
    targetSdk 36
    minSdk 24
}
```

### Proguard (Release)
The app includes proguard rules for release builds to optimize size and performance.

## 🌐 Supported Formats

### Images
- JPEG, PNG, GIF, WebP
- HEIC/HEIF (on supported devices)
- RAW formats (basic support)

### Videos
- MP4, AVI, MOV, MKV
- 3GP, WebM
- Hardware-accelerated decoding support

## 🤝 Contributing

Contributions are welcome! Please follow these guidelines:

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

### Development Setup
1. Use Android Studio with Kotlin support
2. Follow Android development best practices
3. Maintain code formatting and documentation
4. Test on multiple device configurations

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## 🐛 Bug Reports & Feature Requests

Please use the GitHub Issues tab to report bugs or request features:

### Bug Report Template
- Device model and Android version
- Steps to reproduce the issue
- Expected vs actual behavior
- Screenshots (if applicable)

### Feature Request Template
- Description of the proposed feature
- Use case and benefits
- Implementation suggestions (optional)

## 📞 Support

For support and questions:
- Check the [Issues](https://github.com/your-repo/LiteGallery/issues) page
- Review the documentation
- Contact the development team

## 🔄 Version History

### v1.0.0 (Current)
- Initial release
- Core gallery functionality
- Full-screen media viewer
- Video playback with advanced controls
- Material Design 3 interface
- Android 15 (SDK 36) support

## 🙏 Acknowledgments

- **Google**: Android SDK, Material Design, ExoPlayer
- **Bumptech**: Glide image loading library  
- **Android Community**: Best practices and development patterns
- **Contributors**: All developers who contribute to this project

---

**LiteGallery** - Experience your photos and videos like never before. Fast, elegant, and powerful.