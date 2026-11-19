# HA Frame Studio — Home Assistant sidebar editor (integration v1.5.0)

The Home Assistant integration now installs a dedicated **HA Frame Studio** sidebar panel. You can configure all paired PF1007 frames directly from Home Assistant, without manually adding a Lovelace card or JavaScript resource.

The editor is a standalone full-page panel at **/ha-frame-studio**, with the same editing functionality as the original Lovelace card.

## Setup

1. Update the **HA Frame PF1007** integration to **v1.5.0** through HACS/Marketplace (or replace `custom_components/ha_frame` manually).
2. Restart Home Assistant, then hard-refresh the browser if the panel is not immediately visible.
3. Open **HA Frame Studio** from the left sidebar. Alternatively, visit `https://YOUR_HA_HOST/ha-frame-studio`.
4. Select a frame using the dropdown at the top. The panel lists all paired frames.

**Admin-only:** only Home Assistant administrators can see/open the panel or use its editor WebSocket commands. The panel never exposes frame tokens to the browser.

**No Android update for v1.5:** keep Android APK v1.1.1. Re-pairing is not needed.

## v1.5: history, tile size and style clipboard

- **Undo / Redo:** use the toolbar buttons or Ctrl/Cmd+Z and Ctrl/Cmd+Shift+Z / Ctrl/Cmd+Y. The history is limited to 50 recent edits per frame. Typing into a text field uses the browser's native text undo; rapid typing in Studio is grouped into fewer history steps.
- **Resize from preview:** select a tile, then use its four small ↔−/↔+/↕−/↕+ buttons to change width (1–page columns) or height (1–2 rows). The layout is checked using the same 3-row first-fit logic as Android; an impossible resize is refused without losing the previous size.
- **Copy / Paste style:** from the tile inspector, copy and paste the tile's color and corner-radius overrides without changing its type, entity, action, icon or dimensions. Clipboard content is held only in this Studio session.
- **Safe editing:** edits never trigger HA services. Undo/redo does not save to the device. After **Save**, the saved result is the new history baseline. Switching/reloading frames discards unsaved history after confirmation.
- **Compatibility:** v1.5 is a Home Assistant-only update. The existing Android 1.1.1 APK supports these controls without reinstallation.

## Visual editing

- **Frame picker:** switch between any PF1007 frame paired as its own Home Assistant integration entry.
- **Preview:** approximate simulation of Android's **1280×800 Canvas** layout with current entity states.
- **Tile selection:** click a tile to edit its type, label, entity, and range.
- **Reordering:** drag and drop tiles on desktop or use Before/After controls on touch.
- **Pages:** add, remove, rename, reorder; choose 2/3/4 columns and up to 3 rows.
- **Themes:** 14 presets, including Minuit, Forêt, Lavande, Rose, Graphite, Solaire, Arctique, Rétro, Synthwave and Sable, shown as a color-swatch gallery.
- **Design:** five card-corner treatments (standard, square, subtle, rounded, bubble) and three font-size presets (standard, compact, high readability).
- **Layouts:** six predefined page templates (Overview, Climate, Energy, Lights, Security, and 12-tile Grid), added as a new page or used to replace an existing one. Templates have **no entity IDs**; configure them before they can control devices.
- **Page cloning:** duplicate an existing page, preserving its entity assignments.
- **CSS:** edit the native Android Canvas CSS subset with supported style previews.
- **Copy from another frame:** choose a source and copy its layout into the editor.

Click **Save to this frame** to apply a layout. Unsaved modifications stay in the editor until you explicitly save. Each frame stores its own pages, tiles, theme and CSS. Switching away with unsaved changes prompts you to confirm. A revision check prevents overwriting edits made elsewhere.

**Safety:** clicking a tile only selects it; it never invokes Home Assistant services. The real Android controls still operate normally.

## Themes, designs and page templates

Click **Thèmes** in Studio to preview a palette. The current custom CSS is replaced **only after confirmation**, and all changes are unsaved until you explicitly save to the frame. The **Tile design** and **Typography** dropdowns append a reversible, native Canvas-compatible CSS block; existing custom CSS outside this generated block is preserved. **Standard** reverts those overrides to the selected theme's default styling.

Click **Dispositions** to add a prefilled page or replace the current page. Replacing a populated page requires confirmation and **removes its existing tiles and entity assignments**. Adding a new page requires fewer than four pages in the layout. Templates use valid 2/3/4-column grids and at most three rows; the Android renderer's geometry is not altered. **Duplicate page** in the Page inspector clones the current page and its associated entities.

The new presets in Studio and the Home Assistant options wizard work with the existing Android v0.9.0 renderer. New presets in the *on-device web configuration dropdown* require rebuilding/installing an APK with the updated web assets; this is not required for Studio.

## Advanced widgets, icons and video (Android APK 1.0.0 required)

Studio fixes the scaling of the 1280 × 800 preview, so CSS font sizes and tile spacing are proportional to the frame's Canvas renderer. Select a tile to change its **icon** (20 built-in native vectors or automatic), individual **background/text/accent colors**, **corner radius**, and its **width (1–4 columns)** and **height (1–2 rows)**. A deterministic first-fit layout algorithm blocks overlaps and keeps everything inside 3 rows. Wider and taller cards resize their touch areas on Android. Studio offers new Camera and Trend/Weather page templates.

- **Weather:** choose an entity `weather.example`. The tile shows condition, temperature and humidity when supplied.
- **Graph:** choose a numeric `sensor.example`. The app holds the last 24 *distinct value changes* in memory (not historical hours/days); history resets when the app reconnects.
- **Camera:** choose `camera.example` from Home Assistant. The frame requests `/api/camera_proxy_stream/camera.example?interval=1.0` with its existing Home Assistant token, with authenticated JPEG snapshot fallback. The stream displays roughly one frame per second, with **no audio**. A single camera stream is active at a time, and it is paused while the screen sleeps or a different page is selected. Camera availability and latency depend on the integration and hardware.
- Direct RTSP, HLS, WebRTC, arbitrary URLs, and multiple simultaneous video streams are **not supported** in this release. Add your network camera to Home Assistant first to use `camera.*`.

**Install both parts:** flash/install Android APK **v1.0.0** first (preserves pairing and settings via `adb install -r`), then update **HA Frame PF1007 v1.4.0** through HACS and restart HA. Older APKs remain supported for legacy tiles but cannot display advanced tiles.

The Studio browser preview of camera cards intentionally displays a camera placeholder; the MJPEG frames appear on the Android frame itself. Graph preview values only cover state updates while Studio is open. Test a camera with low resolution first.

## MDI icons from Home Assistant

Choose a tile and open **Icône Home Assistant**. You can type any recognized identifier such as `mdi:home`, `mdi:lightbulb`, or `mdi:weather-partly-cloudy`. An icon picker with built-in search is used if Home Assistant has loaded that component; a text field with suggestions is always available.

Leave the value empty to use the Home Assistant entity icon, or enter `none` to hide it. The preview uses Home Assistant's `ha-state-icon` when the entity is available. After clicking **Enregistrer sur ce cadre**, Studio resolves the icon's vector geometry and sends the safe path to Android. The stored path is a snapshot of the icon at save time; if an entity changes its icon dynamically, save the layout again to refresh the PF1007 icon.

**Requires Android APK v1.1.0** for new MDI selections. Existing layouts and the previous 20 built-in icons remain compatible. The on-device local web configuration editor preserves MDI data but new icon selections should be made in Studio.

The MDI icon library comes from Home Assistant / Pictogrammers; icon data follows the upstream Apache 2.0 license. Studio never sends custom icons to an external rendering service.

## Optional legacy Lovelace card

The original dashboard card remains available for existing dashboards:

```yaml
type: custom:ha-frame-visual-editor
```

In Lovelace resource storage mode, the integration automatically registers its JavaScript module. For YAML-managed resources, manually add:

```yaml
lovelace:
  resource_mode: yaml
  resources:
    - url: /ha-frame/ha-frame-editor.js?v=1.5.0
      type: module
```

**This extra Lovelace setup is not required for the sidebar panel.** The sidebar imports its editor code itself.

Optionally set a default frame when using the legacy card:

```yaml
type: custom:ha-frame-visual-editor
entry_id: REPLACE_WITH_HA_FRAME_CONFIG_ENTRY_ID
```

## Troubleshooting

- **Sidebar missing:** confirm integration v1.5.0 is installed and Home Assistant restarted. Open `/ha-frame-studio` directly. Check the user is an administrator.
- **Blank panel:** check `/ha-frame/ha-frame-studio-panel.js?v=1.5.0` and `/ha-frame/ha-frame-editor.js?v=1.5.0` are served by Home Assistant. Inspect browser console errors.
- **Legacy card not found:** verify its module resource under Settings → Dashboards → Resources. This does not affect the sidebar.
- **Frame offline:** confirm Wi-Fi and TCP port 8766. The editor accesses frames via Home Assistant's backend.
- **Conflict on save:** the layout changed outside Studio. Reload it before reapplying changes.
- **CSS mismatch:** the browser preview is approximate; only the native Canvas CSS subset is supported.
- **Multiple frames:** add each device separately through the HA Frame PF1007 integration.

The Home Assistant panel and card bundle are included with the integration; no separate plugin or frontend repository is required.
