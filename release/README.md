# RailBite — Release APKs

> **.apk files are NOT committed to git.** They are built locally with
> `cd android && ./gradlew :customer:assembleRelease :picker:assembleRelease`
> and copied here by `android/scripts/copy-release.sh`.

## Native Kotlin apps (current)

| File | App | Package | Version | Min SDK | Target SDK | Size | SHA-256 |
|------|-----|---------|---------|---------|-----------|------|---------|
| `railbite-customer.apk` | RailBite (Passenger) | `com.railbite.customer` | 1.2.2 (6) | 29 | 35 | 10,782,082 B (~10.3 MB) | `3064b6343b38d80892962ff05e89c46e51006af7c14339621db98a8ad16608bf` |
| `railbite-picker.apk` | RailBite Picker (Vendor) | `com.railbite.picker` | 1.2.2 (6) | 29 | 35 | 9,010,543 B (~8.6 MB) | `91c9ed25d96803dcda9895b249a692d363b0008a43165020971ca55318b05c99` |

Native Kotlin + Jetpack Compose builds, R8-minified and signed with the prototype
keystore (`android/keystore.properties`; store/key pass `railbite-demo`, alias `railbite`).
Distinct package names — both install side by side. Build & connect instructions:
[`README.md` → "Native Android apps"](../README.md#native-android-apps-kotlin--jetpack-compose).

> Release builds keep `org.jctools.**`, `io.netty.**` and `com.hivemq.**` unobfuscated
> (HiveMQ's MQTT client reflects on its own queue fields) and run with R8 full mode off.
>
> Both APKs request the Bluetooth LE permissions (`BLUETOOTH_ADVERTISE` / `BLUETOOTH_SCAN`
> `neverForLocation` / `BLUETOOTH_CONNECT`) for the optional offline **Bluetooth mesh relay**
> (off by default; see the root README). BLE is not used by the normal cloud flow.

### Install via ADB

```bash
adb install -r railbite-customer.apk
adb install -r railbite-picker.apk
```

### Direct phone installation

1. Copy both APKs to the phone (USB, Drive, etc.).
2. Settings → Apps → Special app access → Install unknown apps → allow your file manager.
3. Tap each APK and confirm **Install**.

### Demo credentials

- Passenger: sign in with any valid 10-digit Indian mobile number (e.g. `98765 43210`). The app
  generates the 6-digit OTP on the device and shows it on screen — no SMS is sent. Then use any PNR
  from the seeded manifest `8492000001`–`8492000120` (coach/seat from the manifest, e.g. `B1` / seat `1`).
- Picker: staff IDs `c1`–`c4`, PIN `1234` (zones B1–B2, B3–B4, S1–S2, S3–S4).
- Server: **baked in** (`https://railbite-api.onrender.com`, deployable with `render.yaml` —
  REST + MQTT-over-WebSocket `wss://…/mqtt` on one HTTPS port). No IP entry needed; phones can
  be on any internet connection. A custom-server override for local testing (e.g.
  `http://<laptop-ip>:8080`) hides in customer Settings and the picker login screen.

---

## Historical (Expo builds, superseded)

The first prototype shipped as ~85 MB Expo JS-bundle APKs (`railbite-passenger.apk`,
`railbite-picker.apk`, package `com.railbite.passenger` / `com.railbite.picker`,
version 1.0.0 (1)). They have been removed from this folder and replaced by the native
builds above. Note: the old Expo picker used the same `com.railbite.picker` package,
so on a device that still has it, uninstall the Expo build first (signature mismatch),
then install the native one.
