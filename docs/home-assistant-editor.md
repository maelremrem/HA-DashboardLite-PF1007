# Customize HA Frame entirely from Home Assistant (integration v0.8)

The **HA Frame PF1007** integration now has a built-in native Home Assistant configuration wizard. You no longer need the frame's temporary HTTP web editor (port 8765) to customize pages, cards, colors or CSS.

> The Android dashboard stays a light, native Canvas app. This update changes only the Home Assistant integration: the Android v0.7 APK remains compatible.

## Open the editor

1. In Home Assistant, open **Settings → Devices & services → HA Frame PF1007**.
2. Select the frame's entry and click **Configure**.
3. Choose **Title and themes**, **Custom CSS**, **Manage pages** or **Organize tiles**.
4. Every successful step updates the physical frame immediately over its authenticated LAN API (TCP 8766). Select **Finish** to close the wizard.

The editor reads the **current configuration from the frame** each time it opens. It does not overwrite your Home Assistant long-lived token or the device's display/night settings.

### Themes and custom CSS

Under **Title and themes**, rename the dashboard and pick **Original, Ocean, Amber or Light**. Select **Keep current CSS** to change only the title. Applying a preset **replaces the previous custom CSS**, so save a copy first if needed.

Under **Custom CSS**, paste or edit up to 12,000 characters of native Canvas CSS (or clear the field to restore the built-in styling). The frame validates the supported CSS subset; invalid CSS is rejected without saving the layout. See [CSS reference](css-theming.md).

Example:

```css
:root { --accent: #58d5e7; }
body { background-color: #0b1522; }
.clock { color: var(--accent); font-size: 46px; }
.metric, .control { border-radius: 16px; }
```

**Important:** The PF1007 does not run a browser; full browser CSS, transitions, flexbox, custom fonts and external stylesheets are unsupported.

### Pages

Open **Manage pages** to add, rename, move or remove a page. Each page has a selectable **2-, 3- or 4-column** layout and at most **3 rows**.

- Up to **4 pages** total.
- Up to **6 tiles** per two-column page, **9 tiles** per three-column page or **12 tiles** per four-column page.
- Removing a page also deletes its tiles; the wizard requires confirmation.
- To reduce the number of columns, first remove tiles until the new limit fits.

### Tiles

Open **Organize tiles**, then choose the page. Available actions:

- **Add tile:** choose Sensor, Action, Gauge, Thermostat, Light or Cover, add a title and search for an entity using Home Assistant's entity selector.
- **Edit tile:** change type/title/entity and the optional numeric min/max range.
- **Move tile:** move a tile one position up or down within its page. Repeat to reorder.
- **Remove tile:** explicit confirmation is required.

Thermostat tiles accept `climate.*`, light tiles accept `light.*`, cover tiles accept `cover.*`. Action tiles accept entities capable of triggering the supported Home Assistant commands. Blank tiles are allowed until assigned.

**The editor currently uses guided forms with up/down controls, not a live drag-and-drop preview.** The visual page on the PF1007 reflects changes immediately.

## Compatibility and rollback

The editing API already exists in Android **v0.7**:

- `GET /api/v1/config` reads `pages`, `dashboard_title`, `custom_css`.
- `POST /api/v1/config` writes the same keys.

The integration's **v0.8** options flow uses these endpoints. Your existing pages, theme, CSS, credentials and night mode remain on the frame. The original frame web editor (port 8765) remains usable.

For manual updates, replace `/config/custom_components/ha_frame` and restart Home Assistant. For Marketplace updates, install the new integration version and restart if prompted.

If the Configure flow reports "Cannot connect", confirm the frame is on Wi-Fi, the HA integration server is enabled and Home Assistant can reach TCP **8766**. The editor does not work if the frame is offline. API traffic currently uses local **HTTP**, so keep the port restricted to Home Assistant on your trusted network/VLAN.

## Testing

Run pure Python tests from the repo root:

```bash
python3 -m unittest discover -s tests -p 'test_dashboard_editor.py' -v
python3 -m compileall -q custom_components/ha_frame
```

The Home Assistant UI flow should also be verified on a live instance. If you encounter an issue after updating, include the exception and a screenshot of the Configure form, but **never share pairing codes or API tokens**.
