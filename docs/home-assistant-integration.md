# HA Frame PF1007 — Home Assistant custom integration (v0.7)

## Prerequisites

- HA Frame v0.7 APK running in the foreground on PF1007, connected to Wi-Fi.
- Home Assistant can directly reach the frame's LAN IPv4 address on port **8766**.
- Custom integration support: copy `custom_components/ha_frame` to your Home Assistant config directory.
- Protect the local network. **The integration API uses HTTP, not HTTPS**; it is disabled by default and should not be exposed through Cloudflare, port forwarding or the public Internet.

## Install

1. Copy the `custom_components/ha_frame` directory into `/config/custom_components/ha_frame` (Home Assistant config location) and restart Home Assistant.
2. On the PF1007, open **Settings → ACTIVER L'INTÉGRATION HA**. Tap **GÉNÉRER UN CODE DE JUMELAGE**. A six-digit code appears for 2 minutes (max 5 failed attempts).
3. In HA: **Settings → Devices & services → Add integration → HA Frame PF1007**. Enter the PF1007 IP address (optionally `:8766`) and the pairing code.
4. Home Assistant stores the separate device API bearer token in its config entry. The frame keeps it encrypted using Android Keystore. The existing **Home Assistant Long-Lived Access Token on the frame is never shared** with the integration.
5. HA Frame creates a device with these entities:
   - Switches: screen enabled, night mode, automatic brightness, night time fallback.
   - Numbers: manual brightness, automatic min/max, threshold in lux.
   - Times: start/end of the night window.
   - Text: HA illuminance `entity_id` to use for lux readings, and dashboard title.
   - Sensors: measured lux, applied screen brightness and the frame UUID (Device ID).
   - Binary sensors: screen dimmed and physical light sensor present.
   - Button: wake the display temporarily.
   - **Notify:** send a message to the PF1007 from an automation.

## Send a notification

Use the **Send a notification message** action in the automation editor. Choose the `notify` entity created by HA Frame. Example (replace the entity ID with the one on your device):

```yaml
action: notify.send_message
target:
  entity_id: notify.ha_frame_pf1007_notifications
data:
  title: "Impression terminée"
  message: "La X1C a terminé son impression."
```

The PF1007 shows a native popup for 12 seconds and temporarily leaves night-blackout mode. An ordinary Android notification is also posted. The display does not become physically powered off.

## Configure the display from Home Assistant

The switch, number, time and text entities expose night settings and brightness without opening the frame's web editor. The screen can be awakened with the button entity. All values can also be set by HA automations.

For advanced configuration, HA Frame registers `ha_frame.set_dashboard`. It accepts `device_id` (the frame UUID available as a Home Assistant Device ID sensor and through its API), optional `dashboard_title`, `custom_css`, and `pages` as a JSON array. For example:

```yaml
action: ha_frame.set_dashboard
data:
  device_id: "REPLACE_WITH_FRAME_UUID"
  dashboard_title: "Maison"
  pages: >-
    [{"title":"Maison","columns":3,"cards":[
      {"type":"gauge","label":"Humidité","entity_id":"sensor.humidite","min":0,"max":100},
      {"type":"thermostat","label":"Salon","entity_id":"climate.salon"},
      {"type":"light","label":"Éclairage","entity_id":"light.salon"},
      {"type":"cover","label":"Volets","entity_id":"cover.salon"}
    ]}]
```

The action uses the same JSON layout as the onboard editor. It does not expose the HA LLA token. Visual drag/drop page editing is **still available only in the optional local web editor (port 8765)**; Home Assistant configuration uses native entities and the `set_dashboard` action instead.

## Night mode details

- Night mode becomes active **only inside the configured time window** AND when illuminance falls below the configured lux threshold.
- Values are read from a configured HA sensor first (`sensor.*`, state numeric), then from a physical Android `TYPE_LIGHT` sensor. The active source and lux value are reported to HA.
- If no usable sensor exists, night mode stays inactive unless **Night time fallback** is enabled.
- When active, the Android Canvas draws black and requests 1% window brightness. This does not guarantee that the panel backlight is electrically off. No root/sysfs writes are performed.
- A tap on the black screen or the HA wake button temporarily wakes the screen for 2 minutes. Notifications wake it for at least 20 seconds.
- Automatic brightness is mapped logarithmically from 0–2000 lx to the configured auto brightness range. Without lux, manual brightness is used. This is intentionally an app/window brightness change rather than a global Android setting.
- The PF1007's real light sensor support has not been verified; run `adb shell dumpsys sensorservice` to inspect available sensors.

## Troubleshooting

- `Cannot connect`: verify Wi-Fi IP, port 8766, integration toggle, pairing code age and VLAN/firewall rules.
- No notifications while Android kills the application: keep HA Frame running as the foreground launcher. The API server is in the app process, not a persistent background Android service.
- Unknown/night lux: configure an HA illuminance entity ID and verify it has numeric state. The PF1007 does not necessarily contain a light sensor.
- Security: use an IoT VLAN and restrict API access to Home Assistant. Treat the old Android 6 firmware as untrusted, and avoid public Internet access.
