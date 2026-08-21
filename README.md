# VideoToMP3 - Android App

A powerful Android application that converts videos from various online platforms (YouTube, TikTok, Instagram, etc.) directly to MP3 audio files. Built with Kotlin and powered by yt-dlp for reliable video downloading.

## 📱 App Features

- **Auto Video Detection**: Automatically fetches and displays video details (title, thumbnail, duration, uploader) when you paste a URL
- **Multiple Format Support**: Download from YouTube, TikTok, Instagram, Facebook, Twitter, and 1000+ other platforms
- **Quality Selection**: Choose between Low, Medium, and High audio quality
- **Direct MP3 Conversion**: Converts video to MP3 on your device
- **Background Downloads**: Downloads run as background service with notifications
- **Fast Processing**: Minimal storage usage with efficient conversion
- **Material Design UI**: Modern, intuitive interface

## 📋 System Requirements

### Minimum Requirements
- **Android Version**: Android 12 (API 32) or higher
- **RAM**: 2GB minimum (4GB+ recommended)
- **Storage**: 500MB free space (varies based on downloaded files)
- **Internet**: Stable internet connection required

### Supported Devices
- All arm64-v8a architecture Android devices
- Tablets and phones running Android 12+

## 🛠️ Development Requirements (For Building)

- **Android Studio**: Flamingo or newer
- **Java/Kotlin**: JDK 17+
- **Gradle**: 8.12+
- **Android SDK**: 
  - Min SDK: 32
  - Target SDK: 36
  - Compile SDK: 36
- **FFmpegKitNext**: Build the Android archive locally from the cloned repo at `D:\github\backend\ffmpeg-kit-next` before compiling this app. The app points at the generated Maven repo under `prebuilt/bundle-android-aar-24-maven`.

### FFmpegKitNext local build

```powershell
cd D:\github\backend\ffmpeg-kit-next
./nix-android.sh -p android-r27d
```

This must run on a supported host (Linux/macOS; WSL is recommended on Windows). It produces a local Maven repository such as:

```text
D:\github\backend\ffmpeg-kit-next\prebuilt\bundle-android-aar-24-maven\com\arthenica\ffmpeg-kit-next\8.1.1\
```

Once that repo exists, this project can resolve `com.arthenica:ffmpeg-kit-next:8.1.1` normally.

## 🚀 Getting Started

### Prerequisites
1. Download and install [Android Studio](https://developer.android.com/studio)
2. Install Java Development Kit (JDK 17+)
3. Clone or download this repository

### Building the APK

#### Option 1: Using IntelliJ IDEA / Android Studio (Recommended)

1. **Open the Project**
   - Launch IntelliJ IDEA or Android Studio
   - Click "File" → "Open"
   - Navigate to the `vidtomp3app` folder
   - Click "Open"

2. **Wait for Gradle Sync**
   - The IDE will automatically sync Gradle files
   - This may take several minutes on first build
   - Wait for the sync to complete (check the sync status bar)

3. **Build Release APK - Method A (Using Gradle Panel)**
   - Open the "Gradle" panel on the right side
   - Expand: `vidtomp3app` → `app` → `Tasks` → `build`
   - Double-click on `assembleRelease`
   - The APK will be built automatically

4. **Build Release APK - Method B (Using Terminal)**
   - Click "Terminal" at the bottom of the IDE
   - Run: `gradlew.bat assembleRelease`
   - Wait for build to complete

5. **Locate the APK**
   - Built APK location: `app/build/outputs/apk/release/VideoToMP3-release.apk`
   - Or navigate to the folder: `app` → `build` → `outputs` → `apk` → `release`
   - The APK file will be named: `VideoToMP3-release.apk`

#### Option 2: Using Command Line

**Windows (PowerShell):**
```powershell
# Navigate to project directory
cd D:\local\vidtomp3app

# Build release APK (fastest and most reliable method)
.\gradlew.bat assembleRelease

# APK will be at: app\build\outputs\apk\release\VideoToMP3-release.apk

# Alternative: Build and run tests
.\gradlew.bat build
```

**macOS/Linux:**
```bash
# Navigate to project directory
cd /path/to/vidtomp3app

# Build release APK
./gradlew assembleRelease

# APK will be at: app/build/outputs/apk/release/VideoToMP3-release.apk

# Alternative: Build and run tests
./gradlew build
```

#### Option 3: Using IDE Terminal (Quickest Method)

1. **Open Terminal in IDE**
   - Click "Terminal" tab at the bottom of IntelliJ IDEA
   - The terminal opens in your project directory

2. **Run Build Command**
   ```bash
   # For Windows
   gradlew.bat assembleRelease
   
   # For macOS/Linux
   ./gradlew assembleRelease
   ```

3. **Wait for Build Completion**
   - You'll see build progress in the terminal
   - Once complete, you'll see "BUILD SUCCESSFUL" message

4. **Find Your APK**
   - APK location: `app/build/outputs/apk/release/VideoToMP3-release.apk`
   - Right-click in IDE's Project panel and select "Synchronize" to refresh and see the build folder

#### Option 4: Building Debug APK (For Testing)

If you want to test before release:

```bash
# Using Gradle panel: app → Tasks → build → assembleDebug
# Or via terminal:
gradlew.bat assembleDebug

# Debug APK will be at: app/build/outputs/apk/debug/VideoToMP3-debug.apk
```

## 📦 Installing & Running on Devices

### Method 1: Using IntelliJ IDEA / Android Studio

1. **Connect Android Device**
   - Enable USB Debugging on your Android device
   - Connect via USB cable
   - IntelliJ IDEA / Android Studio will automatically detect the device

2. **Run the App**
   - Click the green play button (▶) in the toolbar
   - Select your connected device from the dropdown
   - The app will build and install automatically

3. **Verify Installation**
   - App appears in your device's app drawer
   - Launch from home screen

4. **Monitor Installation**
   - Check the "Logcat" panel at the bottom for installation progress
   - Once you see "Installation successful", the app is ready to use

### Method 2: Manual APK Installation via ADB

**Windows:**
```powershell
# Install APK on connected device
adb install app/release/VideoToMP3-release.apk

# If update is needed
adb install -r app/release/VideoToMP3-release.apk

# Uninstall app
adb uninstall com.example.videotomp3
```

**macOS/Linux:**
```bash
# Install APK
adb install app/release/VideoToMP3-release.apk

# Reinstall/Update
adb install -r app/release/VideoToMP3-release.apk

# Uninstall
adb uninstall com.example.videotomp3
```

### Method 3: Direct File Transfer

1. **Transfer APK to Device**
   - Copy `VideoToMP3-release.apk` to your Android device
   - Use USB cable, cloud storage, or email

2. **Install on Device**
   - Open File Manager on your Android phone
   - Navigate to the downloaded APK file
   - Tap to install (you may need to allow installation from unknown sources)
   - Grant permissions when prompted

## 🔄 Sharing the App

### 1. Direct APK Sharing
- Share the APK file via:
  - Cloud storage (Google Drive, OneDrive, Dropbox)
  - Email attachments
  - Messaging apps (WhatsApp, Telegram)
  - Bluetooth transfer

### 2. Create Release Build for Distribution
```bash
# Create optimized release APK
gradlew assembleRelease --release

# Or for Play Store (AAB format)
gradlew bundleRelease
```

### 3. Recommended Distribution Platforms
- **GitHub Releases**: Upload APK for easy public sharing
- **Google Play Store**: Publish for broader distribution
- **Direct Sharing**: Send via email or cloud services
- **Internal Testing**: Use Firebase App Distribution

### 4. Installation Instructions for Users

Share these steps with users who receive your APK:

**First Time Installation:**
1. Allow installation from unknown sources:
   - Go to Settings → Security or Safety
   - Enable "Unknown Sources" or "Install Unknown Apps"

2. Install the APK:
   - Tap the downloaded APK file
   - Read permissions carefully
   - Tap "Install"
   - Wait for installation to complete

3. Launch the App:
   - Tap "Open" or find in app drawer
   - Grant required permissions
   - Start converting videos!

**Permissions Explained:**
- 🌐 **Internet**: Required to download videos
- 📱 **Notifications**: Shows download progress
- 💾 **Storage**: Saves MP3 files to device
- ⏰ **Background Execution**: Allows downloads while app is closed

## 📝 Usage Guide

1. **Paste Video URL**
   - Tap the URL input field
   - Paste a video link from any supported platform
   - Video info loads automatically (title, thumbnail, uploader, duration)

2. **Select Quality**
   - Choose between Low, Medium, or High audio quality
   - Higher quality = larger file size

3. **Start Download**
   - Tap the "Convert to MP3" button
   - Check notification bar for download progress
   - MP3 will be saved to your Downloads folder

4. **Access Your Files**
   - Open Files app
   - Navigate to: `Internal Storage` → `Downloads` → `VideoToMP3`
   - Files are named after the video title

## ⚙️ Troubleshooting

| Issue | Solution |
|-------|----------|
| App crashes on launch | Ensure Android 12+ is installed |
| Download fails | Check internet connection, verify URL is valid |
| "Unknown Sources" error | Enable in Settings → Security → Unknown Sources |
| Permissions denied | Go to Settings → Apps → VideoToMP3 → Permissions, grant required permissions |
| APK won't install | Delete previous version first, or use `adb install -r` |
| Slow downloads | Close other apps, reduce quality setting, check connection |

## 🔒 Permissions

The app requires these permissions:
- **INTERNET**: Download videos from online sources
- **FOREGROUND_SERVICE**: Keep downloads running in background
- **POST_NOTIFICATIONS**: Show download progress notifications
- **WRITE_EXTERNAL_STORAGE**: Save MP3 files
- **READ_EXTERNAL_STORAGE**: Access device storage
- **FOREGROUND_SERVICE_DATA_SYNC**: Background sync service

## 📲 Device Compatibility

| Device Type | Minimum OS | Status |
|-------------|-----------|--------|
| Android Phones | Android 12 | ✅ Supported |
| Android Tablets | Android 12 | ✅ Supported |
| arm64-v8a Architecture | Required | ✅ Required |
| 32-bit Devices (armeabi-v7a) | Not supported | ❌ Not compatible |

## 🐛 Reporting Issues

Found a bug or have a suggestion?
- Open an issue on GitHub
- Include:
  - Android version
  - Device model
  - Video URL (if applicable)
  - Error message
  - Steps to reproduce

## 📄 License

This project is licensed under the MIT License - see the LICENSE file for details.

## 🙏 Acknowledgments

- **yt-dlp**: Powerful video downloading library
- **Chaquo Python**: Python integration for Android
- **Material Design**: UI/UX guidelines
- **Glide**: Image loading library

## 📞 Support

For issues or questions:
1. Check the Troubleshooting section
2. Review GitHub Issues
3. Contact via email (update with your contact info)

---

**Current Version**: 1.0  
**Last Updated**: 2026-04-09  
**Minimum Android Version**: 12 (API 32)  
**Target Android Version**: 14 (API 36)
