# RailBite — Release APKs

> **.apk files are NOT committed to git.** They are built locally with
> `cd android && ./gradlew :customer:assembleRelease :picker:assembleRelease`
> and copied here by `android/scripts/copy-release.sh`.

## Native Kotlin apps (current)

| File | App | Package | Version | Min SDK | Target SDK | Size | SHA-256 |
|------|-----|---------|---------|---------|-----------|------|---------|
| `railbite-customer.apk` | RailBite (Passenger) | `com.railbite.customer` | 1.0.0 (1) | 29 | 35 | 6,937,470 B (~6.6 MB) | `2023b4073378898d13b511e485e25287b0ce3939b108fd7c2b6ac87bec56173b` |
| `railbite-picker.apk` | RailBite Picker (Vendor) | `com.railbite.picker` | 1.0.0 (1) | 29 | 35 | 7,450,905 B (~7.1 MB) | `2f06511cfbc38f3fafa5abda8360ccc891ba5e53f9f3a7d41ca5698ac99134f6` |

Native Kotlin + Jetpack Compose builds, R8-minified and signed with the prototype
keystore (`android/keystore.properties`; store/key pass `railbite-demo`, alias `railbite`).
Distinct package names — both install side by side. Build & connect instructions:
[`README.md` → "Native Android apps"](../README.md#native-android-apps-kotlin--jetpack-compose).

> Release builds keep `org.jctools.**`, `io.netty.**` and `com.hivemq.**` unobfuscated
> (HiveMQ's MQTT client reflects on its own queue fields) and run with R8 full mode off.

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

- Passenger: any PNR from the seeded manifest `8492000001`–`8492000120` (coach/seat from the manifest, e.g. `B1` / seat `1`).
- Picker: staff IDs `c1`–`c4`, PIN `1234` (zones B1–B2, B3–B4, S1–S2, S3–S4).
- Edge server: `http://<laptop-ip>:8080` (HTTP), MQTT `:1883` TCP / `:8883` WS.

---

## Historical (Expo builds, superseded)

The first prototype shipped as ~85 MB Expo JS-bundle APKs (`railbite-passenger.apk`,
`railbite-picker.apk`, package `com.railbite.passenger` / `com.railbite.picker`,
version 1.0.0 (1)). They have been removed from this folder and replaced by the native
builds above. Note: the old Expo picker used the same `com.railbite.picker` package,
so on a device that still has it, uninstall the Expo build first (signature mismatch),
then install the native one.
