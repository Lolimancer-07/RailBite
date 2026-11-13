# RailBite — Release APKs

> **.apk files are NOT committed to git.** They are built locally using Android SDK / JDK 17 (or EAS Build) and output to this folder.

## Generated APK Files

| File | App | Package Name | Version | Size | SHA-256 Checksum |
|------|-----|--------------|---------|------|------------------|
| `railbite-passenger.apk` | RailBite (Passenger) | `com.railbite.passenger` | 1.0.0 (1) | ~82 MB | `708a1abc53436437ea21eee554bdf5d5e49c554ff8c756fa7de8d870ff10225c` |
| `railbite-picker.apk` | RailBite Picker (Vendor) | `com.railbite.picker` | 1.0.0 (1) | ~82 MB | `51611c8dee72f3918e049c7f518eed7524afdc2803d653ff85e95f2d2efdd520` |

Both APKs have distinct package names and can be installed concurrently on the same Android device.

---

## Direct Phone Installation

### Method 1: Sideload via USB / File Sharing
1. Copy `railbite-passenger.apk` and `railbite-picker.apk` to your phone via USB cable, Google Drive, WhatsApp, or local share.
2. On your phone: **Settings → Apps → Special app access → Install unknown apps**, and allow your file manager / browser.
3. Tap each `.apk` file and confirm **Install**.

### Method 2: Install via ADB (USB Debugging)
With your Android phone connected with USB debugging enabled:
```bash
adb install -r release/railbite-passenger.apk
adb install -r release/railbite-picker.apk
```

---

## Local Build Instructions (How these APKs were built)

These APKs are release-style standalone builds with embedded JavaScript bundles and compiled Hermes bytecode (no Metro bundler or dev server needed).

### Prerequisites
- Android SDK installed (`minSdk 29`, `compileSdk 34`, `targetSdk 34`)
- JDK 17 (e.g. Eclipse Temurin 17)
- Node.js 20+ & pnpm

### Build Passenger APK
```bash
cd apps/passenger/android
export ANDROID_HOME=$HOME/Android/Sdk
export JAVA_HOME=$HOME/.jdks/jdk-17.0.21+9   # Path to JDK 17
export PATH=$JAVA_HOME/bin:$PATH
./gradlew assembleRelease --console=plain
cp app/build/outputs/apk/release/app-release.apk ../../../release/railbite-passenger.apk
```

### Build Picker APK
```bash
cd apps/captain/android
export ANDROID_HOME=$HOME/Android/Sdk
export JAVA_HOME=$HOME/.jdks/jdk-17.0.21+9   # Path to JDK 17
export PATH=$JAVA_HOME/bin:$PATH
./gradlew assembleRelease --console=plain
cp app/build/outputs/apk/release/app-release.apk ../../../release/railbite-picker.apk
```

---

## Verification Badging & Permissions

```bash
# Verify passenger APK
aapt dump badging release/railbite-passenger.apk | grep -E "package|launchable-activity|uses-permission"

# Verify picker APK
aapt dump badging release/railbite-picker.apk | grep -E "package|launchable-activity|uses-permission"
```

- **Passenger permissions:** `INTERNET`, `ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE`, `CHANGE_WIFI_STATE`, `VIBRATE`, `POST_NOTIFICATIONS`
- **Picker permissions:** `INTERNET`, `ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE`, `CHANGE_WIFI_STATE`, `CHANGE_NETWORK_STATE`, `VIBRATE`, `POST_NOTIFICATIONS`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK`, `WAKE_LOCK`, `USE_FULL_SCREEN_INTENT`
