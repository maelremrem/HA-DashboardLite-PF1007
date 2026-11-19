# HA Frame Studio v2.1 feature bundle — setup and test plan

This branch targets **HA integration 2.1.0** and **Android APK 2.1.0** on a rooted PF1007 with Android **6.0.1 / API 23**. Upgrade both components; earlier APKs cannot render the new widget and slideshow schema. Do not uninstall the existing APK; install in place with the same signing certificate.

## Included functionality

**Studio v1.5 foundation**
- Per-frame undo/redo history, 50 edits, keyboard shortcuts
- Tile size controls in preview; reversible style clipboard
- Explicit save, revision check, save baseline and multi-frame load isolation

**Widgets (v1.6)**
- Calendar: next event title and start attributes from an HA calendar entity
- To-do: remaining task count from a todo entity; the entire task list is *not* transferred
- Bambu Lab: generic HA printer state with optional `progress` / `remaining_time` attributes, without hard-coded entity IDs
- Energy and air-quality sensor cards, presence cards, circular gauge
- History 1h/24h/7d from authenticated HA history REST API (at most 256 KiB response, up to 48 numeric points, refresh every five minutes)
- Existing graph, weather and camera cards remain available

**Context and interaction (v1.7–1.8)**
- Allow-listed conditions on entity states or HH:MM–HH:MM time windows
- Optional conditional background/visibility; hidden tiles reserve their original grid space
- Automatic page selection on a matching rule, with 2-minute manual override
- Tap/long-press action presets; light/cover drag slider sends one action on release
- Tap camera tile to open it fullscreen; tap to close
- HA notifications continue to use the existing authenticated notification service

**Photos (v1.9)**
- Local JPG/PNG photos from `Android/data/dev.maelab.haframe/files/Pictures/HAFrame/` on the frame (individual image ≤6 MB)
- Or one authenticated HA `camera.*` or `image.*` source
- Idle activation, display interval, clock overlay and return on touch/notification
- Photo decoding happens off the UI thread and is downsampled to the 1280×800 display
- **Limitations:** instant transitions, not animated dissolves; HA media albums / remote arbitrary URLs are not supported

**Profiles and management (v2.0)**
- Profiles stored privately in HA storage: 20 named profiles, latest 8 previous layouts per frame
- Export/import JSON, selective pages/theme copy between frames
- Authenticated read-only screenshot (640×400 JPEG, maximum one every five seconds), memory/Wi-Fi/battery diagnostics, short privacy-safe application event log
- Explicit command to *reload dashboard application view*, not root/system reboot

**Updates and safety (v2.1)**
- Installed APK's signing certificate SHA-256 shown in diagnostics
- APK staged locally at `Android/data/dev.maelab.haframe/files/updates/candidate.apk` is checked for package name, higher version, same certificate and SHA-256
- Install requires confirmation in Android Settings > Secure APK update. There is **no remote silent/automatic OTA**; unsigned/mismatched APKs are rejected
- Keep your last working APK as rollback. Android usually prevents package downgrades without removing data; test recovery with ADB before relying on it

## Build and install

On Fedora, in the checked-out branch (after the changes are merged, use `main`):

```bash
git pull --ff-only
bash scripts/build-fedora.sh
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am force-stop dev.maelab.haframe
adb shell am start -n dev.maelab.haframe/.MainActivity
```

For HA, update custom_components/ha_frame to 2.1.0 through HACS or manual copy, restart HA, then hard-refresh the browser and open **HA Frame Studio**.

**The debug APK is build-signed**. In-place updates only work when the new APK is signed by the same certificate as the installed APK. If `adb install -r` fails with `INSTALL_FAILED_UPDATE_INCOMPATIBLE`, do not uninstall until existing settings and pairing data are backed up.

## Final manual test checklist

1. Start app, connect to HA, observe a numeric sensor and check no AndroidRuntime crash in `adb logcat`.
2. In Studio, create and save a themed layout, undo/redo and resize a tile, then reload it on the frame.
3. Check a calendar, todo, Bambu progress, energy, air-quality, presence, circular gauge and a historical graph. Missing attributes should show fallback states rather than crashes.
4. Test a numeric threshold and overnight page rule, conditional colors/hiding and manual page override.
5. Test sliders and long-press actions on a non-critical test entity; camera fullscreen exit.
6. Copy two photos into the app-specific folder and enable the slideshow; test idle, notification wake, touch exit and low-memory behavior.
7. Save a named profile, change a tile, load profile and restore an old revision. Confirm other frames remain unchanged until explicitly saved.
8. Request screenshot, logs and diagnostics from Studio; verify no tokens are displayed and reload requires confirmation.
9. Stage a same-signed APK (higher version) and an incompatible APK to verify acceptance/refusal. **Do not test installation/reboot unattended**.
10. Check 24h stability, memory use, Wi-Fi reconnections and temperature on the physical PF1007.

If startup fails, collect `adb logcat -d -v time -s AndroidRuntime:E` and avoid clearing app data.

## Known limitations and follow-up

The current implementation is a broad feature bundle intended for final on-device validation, not a claim of production-hardening. Full HA todo items, remote photo album sync, animated slideshow transitions, scheduled notification orchestration, complete rollback automation and silent over-the-air updates remain outside this release. Physical-device testing, performance tuning and accessibility review are still needed before a public stable release.
