# HA Frame — Fedora 44 development setup

**Git checkout note:** Run build commands from the repository directory. If `./scripts/build-fedora.sh` reports `Permission denied`, use `bash scripts/build-fedora.sh` or `chmod +x scripts/build-fedora.sh`. Never run Gradle builds with `sudo`.

This document covers the **verified development path** used to get the native Java project building for the PF1007. The Android frame itself stays on Android **6.0.1 / API 23**; the **Android 35 SDK** below is a *compile SDK*, not a firmware update.

## 1. Tools and Java

```bash
sudo dnf install -y android-tools unzip curl
sudo dnf install -y adoptium-temurin-java-repository
sudo dnf config-manager setopt adoptium-temurin-java-repository.enabled=1
sudo dnf install -y temurin-17-jdk
```

Fedora 44's default Java 25 caused `Unsupported class file major version 69` with Gradle 8.7. The project build script accepts Java 17–21. Use JDK 17 without replacing Fedora's system-wide Java:

```bash
export JAVA_HOME=/usr/lib/jvm/temurin-17-jdk
export PATH="$JAVA_HOME/bin:$PATH"
"$JAVA_HOME/bin/java" -version
"$JAVA_HOME/bin/javac" -version
```

If the JDK directory differs, discover it using `ls -d /usr/lib/jvm/*` and set `JAVA_HOME` to the actual location. The Adoptium package's `.repo` may say `enabled=0` even when a `dnf config-manager` override enables it; `dnf repolist --enabled` shows the effective state.

## 2. Install the Android SDK (without Android Studio)

Obtain the current **Command-line Tools for Linux** ZIP from https://developer.android.com/studio#command-line-tools-only. Follow Google's current download link instead of relying on a hardcoded historical archive URL.

Unpack the ZIP so the final path is `~/Android/Sdk/cmdline-tools/latest/bin/sdkmanager`:

```bash
export ANDROID_HOME="$HOME/Android/Sdk"
mkdir -p "$ANDROID_HOME/cmdline-tools/latest"
# From the directory where you downloaded commandlinetools-linux-*.zip:
unzip -q commandlinetools-linux-*.zip
cp -a cmdline-tools/. "$ANDROID_HOME/cmdline-tools/latest/"
export PATH="$ANDROID_HOME/cmdline-tools/latest/bin:$PATH"

sdkmanager --sdk_root="$ANDROID_HOME" --licenses
sdkmanager --sdk_root="$ANDROID_HOME" \
  'platform-tools' 'platforms;android-35' 'build-tools;35.0.0'
```

Check that `"$ANDROID_HOME/platforms/android-35/android.jar"` exists. If you already use Android Studio, point `ANDROID_HOME` at its existing SDK instead.

## 3. Build the APK

```bash
git clone https://github.com/maelremrem/HA-DashboardLite-PF1007.git
cd HA-DashboardLite-PF1007

export JAVA_HOME=/usr/lib/jvm/temurin-17-jdk
export PATH="$JAVA_HOME/bin:$PATH"
export ANDROID_HOME="$HOME/Android/Sdk"
printf 'sdk.dir=%s\n' "$ANDROID_HOME" > local.properties

bash scripts/build-fedora.sh
```

The script downloads Gradle 8.7 into `~/.cache/haframe`, verifies its official SHA-256, then builds `assembleDebug` using Android Gradle Plugin 8.6.1. The output APK is:

```text
app/build/outputs/apk/debug/app-debug.apk
```

`local.properties`, built APKs and the Gradle cache must remain uncommitted. The `.gitignore` excludes them.

## 4. Install and test on the PF1007

```bash
adb devices -l
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n dev.maelab.haframe/.MainActivity
adb shell dumpsys meminfo dev.maelab.haframe
```

**Keep Rootless Launcher installed as a fallback.** Do not switch HA Frame to the default Home app until reconnect, touch controls and reboot recovery are confirmed.

To inspect crashes:

```bash
adb logcat -d | grep -Ei 'haframe|AndroidRuntime|FATAL EXCEPTION' | tail -60
```

For configuration, use HA Frame's **native Settings screen** to save the URL and the long-lived access token. Its optional web editor is available on the frame LAN IP, port `8765`, only after manual activation and for 15 minutes. Never commit tokens or `local.properties` to GitHub. See the root [README](../README.md) for web editor and connection behavior.

## 5. Common errors

| Error | Fix |
|---|---|
| `Unsupported class file major version 69` | Gradle is running under Java 25; set `JAVA_HOME` to JDK 17 |
| `JAVA_HOME is set to an invalid directory` | Install JDK 17 and use its actual location |
| `SDK location not found` | Install the Android SDK; set `ANDROID_HOME` or `sdk.dir` |
| `adb devices` empty | Check cable, USB port, vendor ADB activation |
| Entity state missing | Verify entity IDs, Home Assistant permissions and dashboard connection diagnostics |

## 6. Device-specific rendering notes

The PF1007 has a 1280×800 display, ARMv7 32-bit CPU, ~1 GiB RAM, and (originally) WebView 44. Upgrading WebView to 106 was successfully tested on the second frame, but **HA Frame is native Canvas Java and does not use WebView for rendering**. Installing WebView 106 is *not* a prerequisite for this application. Earlier Fully Kiosk tests showed high memory use and EGL/dex2oat warnings, which is why this project uses no Chromium frontend.
