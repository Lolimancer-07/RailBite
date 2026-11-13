# RailBite — Release APKs

> **.apk files are NOT committed to git.** Download them from EAS Build or build locally using the steps in the main README.

## APK Files

| File | App | Package |
|------|-----|---------|
| `railbite-passenger.apk` | RailBite (Passenger) | `com.railbite.passenger` |
| `railbite-picker.apk`    | RailBite Picker      | `com.railbite.picker`    |

Both can be installed on the same Android device at the same time.

## Install on Android

1. Copy the APK to your Android phone (USB, airdrop, cloud share).
2. On the phone: **Settings → Apps → Special access → Install unknown apps** → allow your file manager / browser.
3. Open the APK file and tap **Install**.
4. Or from a PC with `adb`:
   ```bash
   adb install -r railbite-passenger.apk
   adb install -r railbite-picker.apk
   ```

## First Run

1. Make sure your phone and the laptop running the edge server are on **the same Wi-Fi network**.
2. Find your laptop's LAN IP:
   - **Linux:** `hostname -I` (use the first IP, e.g. `192.168.1.45`)
   - **macOS:** `ipconfig getifaddr en0`
   - **Windows:** `ipconfig` → "IPv4 Address" under your Wi-Fi adapter
3. Open each app and enter the server URL: `http://<laptop-ip>:8080`
   - Passenger: tap the gear icon → Settings
   - Picker: the server URL field is on the login screen
4. The app shows **Connected** when it can reach the server.

## Build the APKs

See the main [README.md](../README.md) for full build steps.

**Quick EAS build:**
```bash
# Passenger
cd apps/passenger
npx eas-cli build --platform android --profile preview

# Picker
cd apps/captain
npx eas-cli build --platform android --profile preview
```

After the build finishes, download the `.apk` link from the EAS Build page and copy it here as `railbite-passenger.apk` / `railbite-picker.apk`.

