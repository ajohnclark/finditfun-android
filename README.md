# Find It Fun

A command-line-built radio playground for modern Android phones. The app keeps
all observations on the phone and includes:

- **Nearby:** continuously surveys detectable Bluetooth Low Energy advertisers.
- **Hunt:** tap a device for warmer/colder guidance, proximity clicks, haptics,
  a compass-sector survey, a step-based trail map, and honest stale/lost states.
- **My Devices:** hold a device row or use Name / device info while hunting to
  save a local nickname and inspect manufacturer, service, PHY, transmit-power,
  pairing, and advertisement metadata.
- **Space:** a live GNSS sky plot with satellite signal strength.
- **Magnetic:** a three-axis magnetic field meter and live history.

Bluetooth devices must be advertising to appear. This app measures signal
strength, not direction, and it does not upload observations.

The Android app has no Internet permission, analytics, advertisements, or user
accounts. See [PRIVACY.md](PRIVACY.md) for its data and permission behavior.

The Hunt compass arrow is an experimental inference: stand still, keep the phone
flat, and rotate slowly through a full circle. It is hidden until enough of the
sweep has samples and one hot sector is clearly stronger than nonadjacent
sectors. The displayed readiness score is a UI heuristic, not a statistical
confidence interval. Trail mode uses heading plus Android's step detector; grant the
Physical activity permission when prompted. A dogleg walk is more informative
than a straight line.

Paired devices remain listed even while silent. Android cannot expose live RSSI
for an idle Classic-only Bluetooth device, so those rows explain the limitation
instead of pretending to track them. Custom names are keyed locally to Android's
hidden device identifier; privacy-preserving devices that rotate identifiers may
occasionally need to be named again.

## Command-line build

From PowerShell in this directory:

```powershell
.\build.ps1
```

That command discovers the Android SDK from `ANDROID_SDK_ROOT`, `ANDROID_HOME`,
or the standard Windows SDK location, and discovers Java from `JAVA_HOME`,
`PATH`, or Android Studio. It runs unit tests, Android lint, assembles the debug
APK, verifies its signature, and copies the final artifact to:

```text
artifacts/finditfun-mvp-debug.apk
```

All Gradle, Android-user, signing, and build state is kept inside this folder.

## Install on a connected Android device

Enable Developer options and USB debugging, connect the phone, approve its RSA
prompt, then run:

```powershell
.\install.ps1
```

The install script rebuilds and verifies the APK before using `adb install -r`.

## Direct Gradle use

`build.ps1` supplies the discovered Java and Android paths. If those paths are
already in your environment, the usual commands also work:

```powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug
```

## Optional Windows BLE survey helper

`tools/scan_ble.ps1` performs a local BLE scan from a Windows PC and prints the
results as JSON. It uses `uv` to run `tools/scan_ble.py` with Bleak; it does not
save or upload scan results unless you explicitly redirect its output.

## Publishing

Generated APKs, machine-local Android paths, Gradle caches, scan captures, and
debug signing material are ignored by Git. The source is released under the MIT
License; see [LICENSE](LICENSE).

Publish the files selected by Git; do not upload a ZIP of the entire developer
folder. Ignored build reports can contain the computer hostname and absolute
paths, and `.local/` contains the reusable debug signing key for updating a
locally installed debug build.

The product idea was informed by
[`findphone`](https://github.com/ben-z/findphone) and
[`superfind`](https://github.com/p4r1ch4y/superfind). This repository does not
vendor either project or require their source code.
