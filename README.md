# HA Frame — PF1007 native dashboard (Android v0.7 / HA integration v0.8)

A lightweight, native Java dashboard for Android 6.0.1 (API 23), built for the PF1007 1280 × 800 ARMv7 photo frame.

## Install from GitHub

The Android APK and the Home Assistant integration are separate installs.

- **Android / Fedora:** `git clone`, use the `main` branch (v0.7), then run `bash scripts/build-fedora.sh`. Avoid `sudo` for the build.
- **Home Assistant:** the integration is under `custom_components/ha_frame/`, with `hacs.json` for Marketplace/HACS support.
- **Marketplace / HACS:** The v0.7 integration and `hacs.json` are on `main`. The repository must be **public** for installation from the Home Assistant Marketplace; if it is private, make it public in GitHub's repository settings or install manually from a Git checkout.

See [install from GitHub / Marketplace](docs/marketplace-installation.md) for both options.

## V0.8 — Dashboard editor inside Home Assistant

The Home Assistant integration now exposes a **Configure** wizard that edits the dashboard on the frame directly over its existing authenticated LAN API. No browser on the PF1007 and no temporary web configuration server needed.

- Pick **Original / Ocean / Amber / Light** presets or keep your custom CSS.
- Edit the full supported native Canvas CSS subset in a multiline field.
- Create, rename, move and delete up to **4 pages**, selecting **2–4 columns**.
- Add, edit, move and remove **metric, control, gauge, thermostat, light and cover** cards (up to **3 rows/page**) using Home Assistant's searchable entity selector.
- Changes are sent to the frame immediately; no YAML or JSON editing required.

This release changes **only the HA integration**. The existing Android **v0.7 APK** continues to work; stored HA credentials and brightness/night settings are unchanged. The editor is a guided native Home Assistant form (up/down reordering, **not drag-and-drop**).

See [Home Assistant editor guide](docs/home-assistant-editor.md).

## V0.9 — Android device diagnostics

The Home Assistant **Diagnostic** panel now exposes the running Android APK version, Android OS version, device uptime since boot, Wi-Fi SSID, IP address, RSSI (dBm), estimated signal quality (%), link rate (Mbit/s), frequency (MHz), and connection status. The app's reported version is read at runtime, rather than hardcoded. Data comes from the paired frame's existing authenticated LAN endpoint and refreshes approximately every 30 seconds.

**Both updates are required:** Android APK v0.9.0 and Home Assistant integration v0.9.0. Diagnostics appear as unavailable until the newer APK is running. Existing pairing and dashboard settings remain intact. See [Device diagnostics](docs/device-diagnostics.md).

## V0.7 — Specialized cards, ambient light and Home Assistant integration

- Native Canvas **gauge**, **thermostat**, **light dimmer**, and **cover position** cards in addition to metrics and command buttons.
- Configurable **night blackout** with an HH:mm window + illuminance threshold, optional fallback to time-only if no lux reading exists; tap or HA wake/notify temporarily wakes the display.
- **Auto brightness** toggle and manual slider, reading `Sensor.TYPE_LIGHT` or a selectable HA `sensor.*` entity; no physical light sensor is assumed.
- New **opt-in HA local integration API** on port 8766 with short-lived PIN pairing. The Home Assistant custom integration lives in `custom_components/ha_frame` and provides switches, numbers, times, text config, sensors, wake button, `notify` entity and `ha_frame.set_dashboard` action.
- The HA LLA token stays on the frame, Android Keystore-protected; no authentication credentials are committed to GitHub. Remote integration uses a distinct device token.
- **Caution:** blanking is software black + minimum backlight setting, *not validated hardware power-off*. Device-side Android 6/API 23 runtime and Home Assistant runtime still require testing.

See [Home Assistant integration](docs/home-assistant-integration.md) and [v0.7 native cards](docs/cards-v07.md).

## V0.6 — Dynamic pages and cards

The Android dashboard is still rendered with **Canvas** (no WebView). The built-in web editor now supports:

- **1–4 pages** with editable titles, order, and swipe/tap navigation on the frame.
- **0–12 cards per page** (depending on columns: 2 = 6 cards, 3 = 9, 4 = 12; always 3 rows maximum).
- A freely ordered mix of **sensor cards** and **touch command cards**; cards can be added, removed, reordered, and searched by Home Assistant friendly name or entity ID.
- Per-page grid settings (2/3/4 columns) with a consistent touch target size.
- Native CSS styles for `.card`, `.page-tab`, `.page-tab.active` and ordinal selectors through `.metric-12` / `.control-12`.
- **Automatic migration** of the previous 3 sensors + 6 commands into the first page named `Maison`; existing labels, entity IDs, CSS, URL and encrypted token are preserved. No data wipe and no APK package change.

To edit the new layout: open **Settings → Start web server** on the PF1007, then visit the displayed address from a trusted LAN computer, enter its six-digit PIN, configure pages/cards, and save. The web server is off by default and expires in 15 minutes. See [Pages and layout](docs/pages-and-layout.md) and [CSS guide](docs/css-theming.md).

**Safety:** Native control cards support `light`, `switch`, `fan`, `input_boolean`, `cover`, `scene`, `script`, `button` and `input_button`. The browser's picker filters to these domains for action cards. Never expose the HTTP configuration server to the public Internet; it does not return the stored Home Assistant token.

**Testing:** `./tests/test-css.sh` validates the native CSS parser, bundled theme presets and dynamic web-model limits on a development machine. Android compilation and real PF1007 behavior still require validation on the device.

**Not a WebView, not Lovelace, not Fully Kiosk.** The UI uses Android Canvas (software-rendered, updated only on state changes and clock ticks). The networking layer uses a single persistent WebSocket connection to Home Assistant, requesting only the configured entities via `subscribe_entities`. No animations, background sync service, or external assets.

## Documentation

- [ADB access and Wi-Fi setup](docs/adb-access.md)
- [Vendor app debloat, backup and security audit](docs/debloat-security.md)
- [Fedora 44 Android development setup](docs/fedora-build.md)
- [CSS customization guide (native Canvas)](docs/css-theming.md)

## Changes in v0.5.0 — native CSS themes

- **CSS customization without WebView:** strict CSS subset interpreted by the native Canvas renderer; supported selectors include `body`, `.clock`, `.metric-1`, `.control.on` and more. Customize colors, font sizes/weights and card corner radii. Unsupported CSS is rejected with an error.
- **Presets:** Original, Ocean, Amber and Light. Apply a preset from the local web configuration editor, or author custom CSS.
- **Theme import/export:** upload or download a `.css` file from the web editor, without involving an external service. A 12,000-character limit bounds parsing and storage.
- **Custom dashboard title** from the same editor.
- **Compatibility:** the Android package name remains `dev.maelab.haframe`. Existing Home Assistant URL, encrypted token, entity IDs and card ordering are preserved with `adb install -r`.
- **Tests:** pure-Java parser tests, preset validation, JavaScript syntax and existing slot-order tests. Android build/hardware validation is still required.

See [CSS theming documentation](docs/css-theming.md) for the supported syntax, selectors and examples. **Full browser CSS/HTML is not supported**; the goal is to keep the PF1007 fast.

## Changes in v0.4.0 — touch and reliability

- **Faster redraws:** incoming Home Assistant state bursts are coalesced into one native Canvas redraw every ~80 ms, and unchanged connection-status counters no longer trigger full redraws.
- **Safer touch actions:** tiles visibly react while pressed, give haptic feedback when available, and suppress duplicate commands to the same entity within 700 ms. A swipe crossing a tile boundary does not send an action.
- **Honest offline state:** stale cached values are labelled as offline rather than presented as live; the controls are visually inactive when disconnected.
- **Manual reconnect:** tap the top connection-status area to immediately retry if disconnected; normal capped automatic reconnect remains enabled.
- **Web editor reorder:** up/down arrows reorder the 3 sensor and 6 command cards *within their groups*. Saving applies the new order immediately to the frame. Existing entity IDs, labels and encrypted token remain unchanged on upgrade.
- **Browser tests:** run `node tests/web-config.test.cjs` to validate reordering boundaries and slot preservation (Node.js optional, only for tests).

**Upgrade:** `adb install -r app/build/outputs/apk/debug/app-debug.apk`. Keep `dev.maelab.haframe` installed (do not uninstall) to preserve your saved settings and token.

**Validation:** browser source syntax and reordering tests are checked. The Android APK still needs building and a hardware touch/reconnect test on the PF1007.

## Changes in v0.3.0 (bug fixes)

- Fixed the dashboard clock: the previous partial redraw covered y=0–135 while the clock was drawn around y=171. The view now invalidates at each wall-clock minute boundary and handles date changes.
- Fixed web changes not appearing on the dashboard: a successful web save now notifies the Android UI, closes stale native Settings fields, reloads preferences, and reconnects the targeted Home Assistant subscription automatically.
- Improved initial entity loading: shows `Connected · N/M entities`, and falls back to `get_states` + `subscribe_events` if no compact subscription event arrives. **Normal live updates still use lightweight `subscribe_entities`**.
- Retains the package name `dev.maelab.haframe`, encrypted token, and existing saved entity IDs; install with `adb install -r` (do not uninstall the app).
- Added status diagnostics for configured-but-unavailable entities.

## Changes in v0.2.0

- Removed the "PF1007 · Android 6 · WebView non utilisé" footer from the canvas dashboard.
- Added **on-demand web configuration**, running directly inside the Android app (no LXC, Docker, or WebView on the frame).
- Web settings allow editing the Home Assistant URL, replacing the token if needed, and configuring all 3 sensor cards and 6 commands.
- Home Assistant entity picker searches by `friendly_name` or `entity_id`, including accented names, using authenticated `GET /api/states`. The long-lived token is never returned to the browser.
- The web editor only starts manually from the frame's Settings page; it binds to the current `wlan0` IPv4 address, uses a displayed one-use six-digit PIN and a short-lived session, limits failed attempts, and **shuts down automatically after 15 minutes**. Port: `8765`.
- Existing local configuration and encrypted token remain intact when upgrading with `adb install -r` (same package ID).

## Web configuration

1. Open **RÉGLAGES** on the PF1007.
2. Tap **ACTIVER LE SERVEUR WEB (15 MIN)**.
3. The screen shows `http://<frame-wifi-ip>:8765` and a six-digit access code. Open that URL from a browser on the **same trusted Wi-Fi/LAN**.
4. Enter the code displayed on the frame. The code can be used once; the session expires when the 15-minute server window ends.
5. Click **Chercher** on any sensor or control card. Search by entity name or `entity_id`, then select the result. Controls only list supported actionable domains (`light`, `switch`, `fan`, `input_boolean`, `cover`, `scene`, `script`, `button`, `input_button`).
6. Edit labels and any other settings, and click **Enregistrer la configuration**. The frame automatically returns to the dashboard and applies the new entity configuration without another manual Save.
7. Tap **DÉSACTIVER LE SERVEUR WEB** or **Terminer** to close the configuration server immediately.

**Security notes:** This temporary web UI serves **plain HTTP on the local network**, so a new token typed into it is not transport-encrypted. On the first configuration, enter the token using the native Android Settings UI or via trusted ADB rather than sending it over LAN HTTP. Keep the token field empty in the browser when a token is already stored. Never expose TCP/8765 to the Internet, place the frame on an IoT network and allow temporary administration only from a trusted machine. The web server has no always-on mode and never starts at boot. Anyone with root access to the frame can still compromise credentials; Android 6 is unmaintained.

When Home Assistant cannot provide the entity list, verify the stored URL/token and that the frame can reach the Home Assistant REST API. The selector depends on `GET /api/states` and will not work without valid authentication, but manual IDs can still be entered.

## Features

- Native landscape full-screen dashboard, 3 metrics + 6 touch controls.
- Native settings page: Home Assistant URL, token, custom labels and entity IDs; optional time-limited web configuration with searchable entity picker.
- Home Assistant `auth_required` / `auth_ok` authentication.
- `subscribe_entities` initial state + real-time incremental updates; fallback to `get_states` + `subscribe_events` if unsupported.
- Commands via `call_service`: toggle (light, switch, fan, input_boolean), open/close cover, activate scenes/scripts, press buttons.
- Automatic reconnection with capped exponential backoff and websocket pings.
- Displays network and authentication status.
- Long-lived access token encrypted using Android Keystore AES-GCM, not included in the APK or source.
- Optional Home-launcher capability. **Do not enable until thoroughly tested.**

## Build

Prerequisites on Fedora:

- JDK **17**. On Fedora 44 install Eclipse Temurin: `sudo dnf install adoptium-temurin-java-repository`, `sudo dnf config-manager setopt adoptium-temurin-java-repository.enabled=1`, then `sudo dnf install temurin-17-jdk`. Fedora 44 Java 25 cannot run Gradle 8.7.
- Android SDK platform **35**, build-tools **35.x**; Android Studio is easiest
- An internet connection at build time for Gradle/Android Gradle Plugin/OkHttp

Set `ANDROID_HOME` to the SDK path (or `ANDROID_SDK_ROOT`). The helper automatically uses `/usr/lib/jvm/temurin-17-jdk` if installed and `JAVA_HOME` is not set. To use the helper:

```bash
cd HAFrame-PF1007
bash scripts/build-fedora.sh
```

This downloads official Gradle 8.7, verifies its published SHA-256, then runs `assembleDebug`. Alternatively open the project in Android Studio and build an APK. Android Gradle Plugin 8.6.1 requires JDK 17.

**This update is source code only. v0.1/v0.2 were reported running on the PF1007, but v0.3 has not been compiled or tested on the actual device in this environment.**

Install from Fedora (ADB over USB):

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n dev.maelab.haframe/.MainActivity
```

If the APK will not install, show `adb install` output and `adb logcat -d | tail`.

## Configure

On the frame, select **RÉGLAGES**. Enter:

- Home Assistant URL, e.g. `http://192.168.1.100:8123`, or ideally `https://ha.example.internal` with a valid trusted certificate.
- Long-lived access token generated in your Home Assistant user profile (use a dedicated, non-admin account).
- Up to 3 sensor entities and 6 action entities, e.g. `sensor.living_room_temperature` and `light.living_room`. Configure actual entity IDs: defaults are empty and examples are not real.

After saving, dashboard authenticates to `ws(s)://<host>/api/websocket`. It shows connection status, subscribes to only the specified IDs, and responds immediately to state updates. No hardcoded IP, token or entity ID.

Use controls only for allowed, non-dangerous entities; the physical screen is not access-controlled.

## Make the app the default Home launcher (optional)

Leave **Rootless Launcher installed as a fallback** during testing. After confirming the dashboard works:

```bash
adb shell am start -a android.settings.HOME_SETTINGS
```

Choose **HA Frame** as the default Home app. Press Home via `adb shell input keyevent 3` and verify. Restart the frame only after confirming you can always open settings with the upper-right button. The PF1007 vendor firmware may still affect startup; test the actual power-cycle behavior.

Revert by choosing Rootless Launcher in HOME_SETTINGS, or by launching Settings over ADB:

```bash
adb shell am start -a android.settings.HOME_SETTINGS
adb shell input keyevent 3
```

## Performance and security

- No WebView dependency, so WebView 106/dex2oat/EGL warnings do not affect this app's rendering path.
- The dashboard uses a very small number of native controls; actual latency/RAM must be measured on the frame.
- Connections to `http://` use unencrypted `ws://`: only suitable for a trusted, isolated LAN. Prefer HTTPS/WSS with a valid certificate when possible. **The app intentionally does not bypass TLS certificate checks.**
- Android 6 and the vendor firmware remain unpatched and root-capable: isolate the frame on an IoT VLAN and limit access to your Home Assistant endpoint.
- Android Keystore protects the stored token against casual file extraction; this is not a guarantee against compromise of a rooted device.
- Home Assistant permissions depend on the selected account. Avoid giving the dashboard account admin permissions.
- All buttons are immediate; do not map locks, alarms or other safety-critical actions without adding authentication/confirmation.

## Troubleshooting

```bash
adb shell pm list packages -3
adb shell dumpsys meminfo dev.maelab.haframe
adb logcat -d | grep -Ei 'haframe|AndroidRuntime|FATAL EXCEPTION' | tail -40
```

If certificate verification fails, use a valid cert; do not disable TLS verification.
If the screen remains on “Configuration nécessaire”, open Settings and save URL/token.
If the entity shows “En attente…”, ensure the ID exactly matches the Home Assistant entity ID and that the account can view it.

## Next milestones

- Validate on PF1007 hardware: build/install, credentials storage, connectivity and touch latency.
- Add reusable pages/groups and HA entity picker.
- Add per-button icon and action choices and optional refresh/brightness controls.
- Add boot recovery tests after power loss.

## Diagnosing missing entity values

The status line now shows the number of loaded states and requested entities, for example `Connecté · 8/9 entités`. If it stays at `0/9`, confirm the configured entity IDs in the web editor, check Home Assistant user permissions, and capture non-secret logs:

```bash
adb logcat -c
adb shell am force-stop dev.maelab.haframe
adb shell am start -n dev.maelab.haframe/.MainActivity
sleep 8
adb logcat -d | grep -Ei 'haframe|WebSocket|AndroidRuntime' | tail -60
```

Never post the long-lived token or a raw WebSocket auth request. If the display stays empty but `N/M` reports loaded entities, check the labels/configuration and use the native Settings screen for local diagnostics.
