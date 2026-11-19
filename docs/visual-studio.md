# HA Frame Studio — visual editor in Home Assistant (integration v1.0)

Home Assistant now embeds a **Lovelace card** for visually editing PF1007 dashboards without opening the Android web server. The editor ships inside the existing HA Frame integration, with no separate frontend repository.

## First-time setup

1. Update the **HA Frame PF1007** integration to **v1.0.0** using Marketplace/HACS, then **restart Home Assistant**.
2. Open the Home Assistant dashboard you want to use for administration and choose **Edit dashboard → Add card**.
3. Search for **HA Frame · Studio**. If it is not available in the card picker, choose **Manual** and paste:

   ```yaml
   type: custom:ha-frame-visual-editor
   ```

4. Save the dashboard. The card automatically lists your paired PF1007 frames. Select the frame from the dropdown at the top.

**Access:** the editor is **administrator-only**, enforced by Home Assistant WebSocket commands. Do not place it on a shared dashboard intended for non-admin users. Editors cannot obtain the frame's bearer token or Home Assistant long-lived access token.

### If Home Assistant does not recognize the card

For Lovelace dashboards in **storage mode**, the integration automatically registers a module resource:

```text
/ha-frame/ha-frame-editor.js?v=1.0.0
```

If your dashboards use YAML-managed resources, add this module manually (only once):

```yaml
lovelace:
  resource_mode: yaml
  resources:
    - url: /ha-frame/ha-frame-editor.js?v=1.0.0
      type: module
```

Restart Home Assistant and hard-refresh the browser (Ctrl+Shift+R) after updating the integration. You can check the resource in **Settings → Dashboards → Resources**, if available.

## Visual editing

- **Frame picker:** switch between any PF1007 frame that you have paired as an independent integration config entry.
- **Preview:** display a responsive simulation of the Android **1280×800 Canvas** layout, populated with current HA entity states. The preview is approximate: Android's Canvas rendering, native font metrics and supported CSS are authoritative.
- **Tile click:** click a tile to edit its type, label, entity and gauge bounds in the inspector.
- **Reorder:** drag and drop tiles on desktop, or use **Before/After** controls on touch devices.
- **Pages:** add, remove, rename, reorder and choose 2/3/4 columns, with no more than 3 rows per page.
- **Appearance:** edit dashboard title; apply Original, Ocean, Amber or Light theme.
- **Custom CSS:** edit the same native CSS subset already supported by Android and see supported colors, radii and typography change in the preview.
- **Copy from another frame:** choose a source frame and click **Copy**. The source is only read; the target is updated only after you click **Save to this frame**.

Edits remain in your browser until **Save to this frame**. Every save uses the selected frame's existing authenticated integration endpoint. Each frame stores its own pages, tiles and CSS on the Android device. Switching a frame with unsaved changes prompts before discarding them. If the configuration was modified elsewhere after loading, a revision check blocks overwriting it; reload the frame to resolve the conflict.

**Safety:** clicking a tile in the editor *selects* it; it **never calls Home Assistant services or changes device states**. The application on the PF1007 continues to use its native touch controls as before. The card does not store layouts or credentials in the browser.

## Optional: pin a dashboard card to one frame

When adding the card manually, use its unique Home Assistant **config entry ID**:

```yaml
type: custom:ha-frame-visual-editor
entry_id: REPLACE_WITH_HA_FRAME_CONFIG_ENTRY_ID
```

The card will initially select that frame, while still allowing you to switch using the dropdown. You can add multiple Studio cards, each with its own default frame, to one Home Assistant dashboard.

## Version compatibility

Only the **Home Assistant integration v1.0.0** needs updating. The existing **Android APK v0.9.0** already has the `GET /api/v1/config` and `POST /api/v1/config` endpoints used by Studio. Re-pairing is not necessary.

The older **Configure** options wizard still works as a fallback. A future dedicated full-screen HA panel may improve ergonomics further, but this version is a Lovelace custom card embedded directly in your dashboard.

## Troubleshooting

- **Card not found:** verify `custom_components/ha_frame/frontend/ha-frame-editor.js` was installed, restart Home Assistant and check the resource URL.
- **Permission denied:** the logged-in HA user needs administrator permissions for the visual editor.
- **Frame offline:** check Wi-Fi and TCP port 8766. The card reads the frame through Home Assistant, not from the browser.
- **Conflict on save:** someone changed the layout in the native web editor or another browser session. Refresh this frame and reapply your edits.
- **CSS looks different on PF1007:** browser preview is not a pixel-perfect emulator. Unsupported Canvas CSS is rejected by Android.
- **Multiple frames:** each device must be paired once through HA Frame PF1007 → Add integration. Profiles are stored on each frame, not globally.

The current feature branch is `feature/v1.0-visual-multiframe-editor`. Build and Android changes are not needed for this frontend-only release.
