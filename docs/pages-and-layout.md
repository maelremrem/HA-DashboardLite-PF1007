# Dynamic dashboard layout — HA Frame v0.6

The PF1007 app draws each dashboard page in native Android Canvas. The browser configuration page is used only for editing — never for dashboard rendering.

## Create and edit pages

1. On the PF1007: open **Réglages**, activate the temporary web server, and note its local URL and PIN.
2. Open the URL on another device on the same Wi-Fi, enter the PIN, and select **Nouvelle page** to add a page.
3. Give each page a name and select **2, 3 or 4 columns**.
4. Add sensor (`Capteur`) and action (`Commande`) cards in any order. Select their Home Assistant entity IDs using **Chercher**, or type the ID manually.
5. Reorder cards or pages with the arrow buttons. Delete unneeded cards or pages. Click **Enregistrer la configuration**.
6. The Android screen reloads its saved layout and reconnects to Home Assistant. Tap a page tab or swipe left/right to change pages.

## Screen capacity

Only **three rows** of cards are shown at once; this keeps the PF1007 touch targets reasonably large.

| Columns | Max cards per page |
| --- | ---: |
| 2 | 6 |
| 3 | 9 |
| 4 | 12 |

The app allows up to **four pages**, at least one. A page may contain no cards while you prepare it. Use one layout for each room, or mix sensor and action cards on a single page.

## Upgrade from v0.5

`FrameConfig.load()` automatically migrates the existing nine slots on first load if a `dashboard_layout_v2` JSON record does not exist. The first page is named **Maison**, with three columns, and contains the old three sensor and six action cards. The existing labels, entity assignments, dashboard title, CSS, Home Assistant URL and Android Keystore token are left untouched.

Once the web editor saves the layout, it stores the new JSON in the same app's private SharedPreferences. The old `label_N` / `entity_N` preferences are not deleted, but v0.6 uses the new layout. Update the APK with `adb install -r`; never use `adb uninstall` if you want to preserve app data.

## Performance and troubleshooting

- The WebSocket client subscribes to the **unique entity IDs across all pages** and batches redraws.
- Only the currently visible page is rendered.
- A command card shows a touch highlight and suppresses identical rapid double taps.
- An offline dashboard does not show stale states as current.
- The browser configuration server binds to the frame Wi-Fi interface and stops after 15 minutes, or when explicitly stopped.
- A layout containing more than three rows will be rejected. Change the column count or reduce the number of cards before saving.

To inspect runtime issues on Fedora:

```bash
adb logcat -c
adb shell am force-stop dev.maelab.haframe
adb shell am start -n dev.maelab.haframe/.MainActivity
adb logcat -d -s AndroidRuntime:E HAFrameConfig:W
adb shell dumpsys meminfo dev.maelab.haframe
```

Keep the Android 6 device isolated on a trusted IoT network. Never forward port 8765 to the Internet.
