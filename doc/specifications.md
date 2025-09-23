# LiteGallery - Technical Specifications

## Project Overview
- **App Name**: LiteGallery
- **Platform**: Android
- **Primary Goal**: Maximum performance and speed
- **Target SDK**: 36
- **Compile SDK**: 36
- **Namespace**: org.iurl.litegallery
- **Application ID**: org.iurl.litegallery

## Core Features

### 1. Folder Gallery View
- Display all folders containing photos or videos
- Navigate into folders to view media files
- Support three view modes:
  - Thumbnails view
  - Simple list view
  - Detailed list view

### 2. Media Sorting & Organization
- **Default sorting**: File date (newest first)
- **Alternative sorting**: Filename
- **Sort order**: Ascending/Descending (user configurable)

### 3. Media Viewer
#### Photo Viewer
- Full-screen display by default
- Tap to show/hide UI controls
- Double-tap zoom levels: 1x/2x/3x cycling
- Pinch-to-zoom gesture support
- EXIF data display in properties

#### Video Player
- Full-screen playback
- Standard video controls (play/pause/seek)
- Frame-by-frame navigation (left/right arrows)
- Double-tap for play/pause
- Brightness and contrast adjustment (API 30+)
  - Accessible via down arrow in control bar
  - Two horizontal scrollbars for brightness/contrast

### 4. User Interface Controls
#### Top Bar
- Scrolling filename display (tap to show full name in toast)
- Menu button (⋯)

#### Action Bar (Bottom)
- Customizable button order and visibility
- Available actions:
  - Rotate screen
  - Delete (with trash bin support)
  - Rename file
    - Remember previous rename strings in dropdown
    - Ensure video playback compatibility after rename
  - Properties (filename, size, date, EXIF)
  - Share (ACTION_SEND)
  - Edit (ACTION_EDIT - external editor)
  - Rotate photo orientation
  - Copy
  - Move

### 5. Gesture Controls
- **Left/Right swipe**: Navigate between files in folder
- **Left edge swipe**: Screen brightness adjustment
- **Right edge swipe**: Volume adjustment
- **Pinch-to-zoom**: Photo scaling
- **Double-tap**: Photo zoom (1x/2x/3x), Video play/pause

### 6. File Operations
#### Conflict Resolution
- Check for filename conflicts before copy/move/rename
- Options when conflict detected:
  - Cancel operation
  - Overwrite existing file
  - Use new name (default: "filename (1).ext", "filename (2).ext")

#### Trash Bin System
- Default: Move files to trash bin
- Option: Direct delete (checkbox)
- View trash bin contents
- Restore files from trash
- Empty trash bin functionality

### 7. Intent Handling
- Register as intent handler for:
  - All image types (image/*)
  - All video types (video/*)
- Support navigation in external folders (ignore .nomedia)
- Enable left/right swipe navigation even for external files

### 8. Settings & Configuration
- Customize action bar button order and visibility
- Set default sorting preferences
- Configure other user preferences
- Brightness/volume restore on app exit

### 9. Internationalization
- **Supported Languages**:
  - English
  - Traditional Chinese (繁體中文)
  - Simplified Chinese (简体中文)

## Technical Requirements

### Performance Optimization
- Prioritize speed in all operations
- Efficient thumbnail generation and caching
- Smooth scrolling and navigation
- Optimized memory usage for large media collections

### System Integration
- Media scanner integration
- Proper permission handling for storage access
- Brightness and volume system integration with restoration
- External app integration via intents

### UI/UX Design
- Clean and elegant interface
- Intuitive gesture controls
- Responsive touch interactions
- Consistent design language throughout

### Compatibility
- Android API level support from minimum to SDK 36
- Graceful degradation for older API levels (brightness/contrast on API <30)
- Device orientation support
- Various screen sizes and densities

## File Structure Considerations
- Support for various image formats (JPEG, PNG, GIF, WebP, etc.)
- Support for various video formats (MP4, AVI, MOV, etc.)
- Handle large file collections efficiently
- Proper metadata extraction and display