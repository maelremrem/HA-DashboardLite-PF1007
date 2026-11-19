# HA Frame Studio — Home Assistant sidebar editor (integration v1.1.0)

The Home Assistant integration now installs a dedicated **HA Frame Studio** sidebar panel. You can configure all paired PF1007 frames directly from Home Assistant, without manually adding a Lovelace card or JavaScript resource.

The editor is a standalone full-page panel at **/ha-frame-studio**, with the same editing functionality as the original Lovelace card.

## Setup

1. Update the **HA Frame PF1007** integration to **v1.1.0** through HACS/Marketplace (or replace `custom_components/ha_frame` manually).
2. Restart Home Assistant, then hard-refresh the browser if the panel is not immediately visible.
3. Open **HA Frame Studio** from the left sidebar. Alternatively, visit `https://YOUR_HA_HOST/ha-frame-studio`.
4. Select a frame using the dropdown at the top. The panel lists all paired frames.

**Admin-only:** only Home Assistant administrators can see/open the panel or use its editor WebSocket commands. The panel never exposes frame tokens to the browser.

**No Android update:** the existing Android APK v0.9.0 supports the same authenticated config endpoints. Re-pairing is not needed.

## Visual editing

- **Frame picker:** switch between any PF1007 frame paired as its own Home Assistant integration entry.
- **Preview:** approximate simulation of Android's **1280×800 Canvas** layout with current entity states.
- **Tile selection:** click a tile to edit its type, label, entity, and range.
- **Reordering:** drag and drop tiles on desktop or use Before/After controls on touch.
- **Pages:** add, remove, rename, reorder; choose 2/3/4 columns and up to 3 rows.
- **Themes:** Original, Ocean, Amber, or Light; dashboard title.
- **CSS:** edit the native Android Canvas CSS subset with supported style previews.
- **Copy from another frame:** choose a source and copy its layout into the editor.

Click **Save to this frame** to apply a layout. Unsaved modifications stay in the editor until you explicitly save. Each frame stores its own pages, tiles, theme and CSS. Switching away with unsaved changes prompts you to confirm. A revision check prevents overwriting edits made elsewhere.

**Safety:** clicking a tile only selects it; it never invokes Home Assistant services. The real Android controls still operate normally.

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
    - url: /ha-frame/ha-frame-editor.js?v=1.1.0
      type: module
```

**This extra Lovelace setup is not required for the sidebar panel.** The sidebar imports its editor code itself.

Optionally set a default frame when using the legacy card:

```yaml
type: custom:ha-frame-visual-editor
entry_id: REPLACE_WITH_HA_FRAME_CONFIG_ENTRY_ID
```

## Troubleshooting

- **Sidebar missing:** confirm integration v1.1.0 is installed and Home Assistant restarted. Open `/ha-frame-studio` directly. Check the user is an administrator.
- **Blank panel:** check `/ha-frame/ha-frame-studio-panel.js?v=1.1.0` and `/ha-frame/ha-frame-editor.js?v=1.1.0` are served by Home Assistant. Inspect browser console errors.
- **Legacy card not found:** verify its module resource under Settings → Dashboards → Resources. This does not affect the sidebar.
- **Frame offline:** confirm Wi-Fi and TCP port 8766. The editor accesses frames via Home Assistant's backend.
- **Conflict on save:** the layout changed outside Studio. Reload it before reapplying changes.
- **CSS mismatch:** the browser preview is approximate; only the native Canvas CSS subset is supported.
- **Multiple frames:** add each device separately through the HA Frame PF1007 integration.

The Home Assistant panel and card bundle are included with the integration; no separate plugin or frontend repository is required.
