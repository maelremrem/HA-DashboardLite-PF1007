# CSS themes in HA Frame (native Canvas)

HA Frame does **not** embed a browser on the PF1007. In version 0.5, a small CSS interpreter maps supported properties to native Android Canvas drawing. It is not full browser CSS: no HTML layout, external fonts, backgrounds with images, JavaScript, `@import`, `@media`, or animations.

## Use

1. Open **RÉGLAGES** on the PF1007 and enable the local configuration server.
2. In your desktop browser, visit the displayed IP and enter the one-time PIN.
3. Open **Personnaliser avec du CSS** and select a preset, edit CSS, or import a local `.css` file.
4. Save. The Android parser validates the stylesheet and the frame immediately reloads the visual theme.
5. Export a `.css` file for backup or to share a theme. The HA token is never included in CSS exports.

## Selectors

| Selector | Native element |
| --- | --- |
| `:root` | CSS hex color variables, e.g. `--accent: #56d9ef` |
| `body` | Dashboard background |
| `.header` / `.subtitle` | Title and subtitle |
| `.clock` / `.date` | Time and date |
| `.status` / `.settings` / `.section` | Connection info, settings button, section headings |
| `.accent` | Indicator dot |
| `.metric` / `.metric-1` ... `.metric-3` | All sensors / one sensor |
| `.metric-label` / `.metric-value` | Sensor label and value |
| `.control` / `.control-1` ... `.control-6` | All actions / one action |
| `.control.on` / `.control.pressed` | Active / pressed action background |
| `.control-label` / `.control-state` | Action label / state |

Comma-separated selectors are allowed: `.metric, .control { border-radius: 20px; }`.

## Supported properties

| Property | Allowed values |
| --- | --- |
| `color`, `background-color` | `#RGB`, `#RRGGBB`, `var(--color)` |
| `border-radius` | `0px` to `72px` |
| `font-size` | `10px` to `72px` |
| `font-weight` | `normal`, `bold` |

Example:

```css
:root { --accent: #58d5e7; }
body { background-color: #071c2b; }
.header, .clock { color: #b9f0ff; }
.metric, .control { background-color: #113249; border-radius: 23px; }
.metric-1 { background-color: #153c4e; }
.control.on { background-color: #19575e; }
.control.pressed { background-color: #296b77; }
.accent { color: var(--accent); }
```

Themes can be at most 12,000 characters and 180 declarations. Unsupported CSS is rejected rather than silently ignored; no theme can trigger a native network request. Existing config and AES-GCM-encrypted Home Assistant token survive `adb install -r`. The browser configuration server is temporary (15 minutes) and HTTP-only on the trusted LAN; never expose its port to the Internet.

## Tests

```bash
./tests/test-css.sh
```

This tests the pure-Java CSS parser, JavaScript presets and slot reordering without needing an Android device. A real PF1007 build/test is still required.
