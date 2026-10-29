# Wunder

A personal Android replacement for the Weather Underground app: current conditions, a 10-day
forecast with hourly charts, a single-day detail view, and a 2×2 home screen widget. It uses the
same weather.com API as the official app.

## Features

- **Current conditions** for your approximate location or any searched place (city, ZIP code
  or weather station): temperature, condition, chance of precipitation, wind, and the official
  app's "Tomorrow's temperature is forecast to be…" comparison
- **10-day forecast** with day headers and hourly charts (temperature/feels like, cloud cover,
  precipitation chance, snow, liquid precipitation, wind, humidity, pressure). Charts scroll
  together, and tap or long-press-drag shows values across all of them. Choose which charts
  show from the settings sheet.
- **Sunrise and sunset** graph with civil twilight and day length
- **Single-day view** (tap a day): day/night narratives, hourly strip, hourly table with a
  pinned header, air quality (today), current observations, station almanac and astronomy
- **Widget** (2×2, light and dark): location, condition, temperature, high/low, chance of rain
  and last update. It refreshes about every two hours. Tap "Updated…" to refresh now; tap
  anywhere else to open the app.
- °F/°C. Light/dark follows the device theme.

The app has no app-drawer entry; add the widget and open the app from it. Android 10+ still
shows a placeholder icon that opens the app's system settings.

### Location and privacy

The app only ever requests **approximate** location (`ACCESS_COARSE_LOCATION`). The manifest
explicitly removes `ACCESS_FINE_LOCATION`, even if a library asks for it. For the widget to
follow you while the app is closed, grant location "Allow all the time" from the app's settings
sheet. Otherwise the widget keeps the last location the app found.

## Building

### Requirements

- A full JDK 17 or newer (with `javac`, not just a JRE)
- Android SDK with platform 37 (`compileSdk = 37`, needed by Compose BOM 2026.09)
- `local.properties` pointing at the SDK, or `ANDROID_HOME` set:

  ```
  sdk.dir=/path/to/Android/Sdk
  ```

### Debug build

```sh
./gradlew assembleDebug                # app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest            # JVM unit tests
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

If your default `java` lacks a compiler, point Gradle at a full JDK, e.g.
`JAVA_HOME=~/.gradle/jdks/<jdk> ./gradlew assembleDebug`.

### Release build and signing

Release builds are minified and signed with a release key so each new APK installs over the
previous one. Signing settings come from either:

- **Environment variables** (used by CI): `WUNDER_KEYSTORE_FILE`, `WUNDER_KEYSTORE_PASSWORD`,
  `WUNDER_KEY_ALIAS`, `WUNDER_KEY_PASSWORD`, or
- **`keystore.properties`** in the project root (local builds):

  ```
  storeFile=release.jks
  storePassword=...
  keyAlias=wunder
  keyPassword=...
  ```

With neither, release builds fall back to the debug key. `release.jks` and
`keystore.properties` are git-ignored; back them up somewhere safe. If the key is lost or
leaked, make a new one; you'll have to uninstall the app once to switch keys.

To create a new key:

```sh
keytool -genkeypair -keystore release.jks -storetype PKCS12 -alias wunder \
  -keyalg RSA -keysize 4096 -validity 36500 -dname "CN=Wunder"
```

Then `./gradlew assembleRelease` writes `app/build/outputs/apk/release/app-release.apk`.

> A debug build and a release build are signed with different keys, so switching between them
> on a device needs an uninstall first (which removes the widget and settings).

## Continuous builds

`.github/workflows/build.yml` runs on every push. It runs the unit tests, builds a signed release
APK and publishes it as a GitHub Release tagged `build-<run number>`. Pushes to the default
branch are marked "latest"; other branches are pre-releases. The version code is the run number,
so every release installs over the last.

One-time setup (the keystore password doubles as the key password):

```sh
gh secret set WUNDER_KEYSTORE_BASE64 < <(base64 -w0 release.jks)
gh secret set WUNDER_KEYSTORE_PASSWORD   # paste storePassword from keystore.properties
```

To install a release on the phone, download the APK from the release page and allow your
browser or file manager to install unknown apps, or use `adb install -r wunder-*.apk`.

## API key

The weather.com API key is in `app/build.gradle.kts` (`WU_API_KEY`) and is compiled into the
APK. This is meant for personal use only; if the key stops working, replace it there.

## Layout

```
app/src/main/java/app/wunder/
  data/      API URLs, HTTP + disk cache (stale fallback), parsing, view models, formatting,
             location, preferences
  ui/        Compose screens: home, 10-day strip charts, single day, search, settings sheet
  widget/    Home screen widget provider and the WorkManager refresh job
app/src/test/  JVM unit tests for formatting, the cache, quickie logic and astronomy parsing
```

## Credits

Weather icons: [Meteocons](https://github.com/basmilius/weather-icons) by Bas Milius, MIT
License, converted to Android vector drawables. Data from The Weather Company API.
