# PF1007 diagnostics in Home Assistant (v0.9)

The HA Frame **device page → Diagnostic** exposes read-only telemetry from the Android frame:

| Entity | Meaning |
| --- | --- |
| App version | Version installed on the frame, read from the Android package manager (not the HA integration version) |
| Android version | Operating system version |
| Device uptime | Seconds elapsed since the last Android boot, including time spent in sleep |
| Wi-Fi connected | Wi-Fi association status |
| Wi-Fi SSID | Access point network name (when accessible to Android) |
| Wi-Fi IP address | IPv4 address reported by Android's Wi-Fi interface |
| Wi-Fi signal strength | RSSI in dBm; a value closer to zero usually means stronger signal |
| Wi-Fi signal quality | Approximate 0–100% signal estimate from RSSI, not Internet quality |
| Wi-Fi link speed | Reported link rate in Mbit/s; **not** a throughput/speed test |
| Wi-Fi frequency | Connection frequency in MHz |

Device diagnostics are polled by Home Assistant every **30 seconds**, using the existing authenticated `GET /api/v1/status` endpoint on TCP 8766. No separate pairing, cloud connectivity, or public access is required.

## Update both components

The Android **v0.9.0 APK** adds these telemetry fields. The Home Assistant integration **v0.9.0** exposes them as diagnostic entities. Installing only the integration leaves the new sensors unavailable until the APK is updated.

**Fedora** (from your existing clone):

```bash
cd ~/PF1007-2/HA-DashboardLite-PF1007
git switch main
git pull --ff-only

export JAVA_HOME=/usr/lib/jvm/temurin-17-jdk
export ANDROID_HOME="$HOME/Android/Sdk"
bash scripts/build-fedora.sh

adb devices -l
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am force-stop dev.maelab.haframe
adb shell am start -n dev.maelab.haframe/.MainActivity
```

In Home Assistant, update **HA Frame PF1007** to **0.9.0** through Marketplace/HACS, restart Home Assistant if requested, then open **Settings → Devices & services → HA Frame PF1007 → device → Diagnostic**.

The stored frame configuration, pairing credential, dashboard layout and Home Assistant token are not changed. A new pairing should not be required.

## Missing Wi-Fi values

On Android 6 and other vendor firmware, SSID and radio metadata availability depends on device permissions and Wi-Fi driver support. When information cannot be read, the corresponding sensor reports unknown instead of an invented value. The manifest includes only `ACCESS_WIFI_STATE`, which is not a runtime permission.

When the frame loses Wi-Fi completely, Home Assistant cannot reach its LAN API; all coordinator-driven entities may become unavailable rather than displaying `Wi-Fi disconnected`. The Wi-Fi connected diagnostic reflects a state **only while the integration can poll the frame**.

For quick local checks:

```bash
adb shell dumpsys wifi | head -80
adb shell cat /proc/uptime
adb shell dumpsys package dev.maelab.haframe | grep versionName
```

Do not publish private SSID or LAN topology information in public issue logs. The integration API uses local plain HTTP; allow only the Home Assistant host to contact port 8766.
