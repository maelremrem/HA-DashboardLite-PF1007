# CSS theming (native Android Canvas)

HA Frame **does not use a browser/WebView** on the PF1007. Version 0.5 adds a deliberately small CSS interpreter that applies supported visual properties to the software-rendered Canvas dashboard. It is **not full CSS**: HTML selectors, positioning, animations, imported fonts, images, `@media`, `@import`, and arbitrary external resources do not work.

## Using the editor

1. Open **RÉGLAGES** on the PF1007 and enable the temporary web configuration server.
2. Open the displayed local URL from a trusted computer and enter the one-time PIN.
3. Open **Personnaliser avec du CSS**, choose a preset or edit the CSS textarea. You can import/export a local `.css` file.
4. Click **Enregistrer la configuration**. Invalid selectors/properties/values are rejected with an error. Valid changes are applied to the frame immediately.

The selected theme is stored in the same Android private preferences as other HA Frame settings. It survives `adb install -r` and app restarts. The long-lived Home Assistant token remains in Android Keystore and is never exported with CSS.

## Available selectors

| Selector | Element |
| --- | --- |
| `:root` | Color variables in the form `--name: #RRGGBB;` |
| `body` | Whole-screen background |
| `.header` | Dashboard title |
| `.subtitle` | Subtitle |
| `.clock`, `.date` | Clock and date |
| `.status` | Home Assistant connection text |
| `.settings` | Settings button |
| `.section` | CAPTEURS / COMMANDES headings |
| `.accent` | Active-dot accent color |
| `.metric` | All three sensor cards |
| `.metric-1`, `.metric-2`, `.metric-3` | Individual sensor card |
| `.metric-label`, `.metric-value` | Sensor label/value |
| `.control` | All six action cards |
| `.control-1` to `.control-6` | Individual action card |
| `.control.on`, `.control.pressed` | Active and pressed action card backgrounds |
| `.control-label`, `.control-state` | Action card title/state |

Use a comma for groups, e.g. `.metric, .control { border-radius: 24px; }`.

## Supported properties

| Property | Values | Notes |
| --- | --- | --- |
| `color` | `#RGB`, `#RRGGBB`, `var(--name)` | Text / indicator foreground color |
| `background-color` | `#RGB`, `#RRGGBB`, `var(--name)` | Screen or card backgrounds |
| `border-radius` | `0px`–`72px` | Rounded native cards |
| `font-size` | `10px`–`72px` | Canvas text size; font size can exceed available card space |
| `font-weight` | `normal`, `bold` | Text weight |

Color variables are declared in `:root` and are limited to hex colors. Custom property names use lowercase letters, digits, and hyphens.

### Example

```css
:root { --accent: #53d6ef; }
body { background-color: #071c2b; }
.header, .clock { color: #b9f0ff; }
.metric, .control { background-color: #113249; border-radius: 23px; }
.metric-1 { background-color: #143b43; }
.metric-value { font-size: 38px; font-weight: bold; }
.control.on { background-color: #19575e; }
.control.pressed { background-color: #296b77; }
.accent { color: var(--accent); }
```

## Performance and safety

CSS is parsed and validated **on configuration save / load**, not by a WebView. The renderer still uses simple Android Canvas operations, with no downloaded themes or background asset fetching. Stylesheets are limited to 12,000 characters and 180 declarations. Unrecognized selectors and properties fail validation rather than silently doing nothing.

No CSS file can run JavaScript or send network requests through the native renderer. Avoid exposing the on-demand HTTP configuration server outside a trusted LAN. The config server always stops after 15 minutes.

## Running tests

The CSS parser is plain Java and can be tested on a development PC:

```bash
mkdir -p /tmp/haframe-css-tests
javac -d /tmp/haframe-css-tests \
  app/src/main/java/dev/maelab/haframe/CanvasTheme.java \
  tests/CanvasThemeTest.java \
  tests/ThemePresetsRunner.java
java -cp /tmp/haframe-css-tests dev.maelab.haframe.CanvasThemeTest
node tests/theme-presets.test.cjs /tmp/haframe-css-presets.json
java -cp /tmp/haframe-css-tests dev.maelab.haframe.ThemePresetsRunner /tmp/haframe-css-presets.json
```

## V0.6: Dynamic pages and card styles

These additional selectors are supported by the native Canvas renderer:

| Selector | Effect |
| --- | --- |
| `.card` | Common background color and corner radius on all cards |
| `.page-tab` | Page tabs (background, color, corner radius, font-size) |
| `.page-tab.active` | Active page tab (overrides `.page-tab`) |
| `.metric-1` through `.metric-12` | Per-page metric ordinal (in display order) |
| `.control-1` through `.control-12` | Per-page command ordinal (in display order) |

Ordinal selectors are counted **separately within the active page**. If you rearrange cards, their ordinals can change. Prefer `.metric` or `.control` for consistent styling across pages.

```css
:root { --accent: #67dbba; }
.card { border-radius: 14px; }
.page-tab { background-color: #263244; color: #b7c6d7; }
.page-tab.active { background-color: #213d3c; color: var(--accent); }
.control { background-color: #1b2432; }
.control.on { background-color: #213d3c; }
```

The CSS subset remains intentionally strict: no layout rules, selectors beyond the documented set, remote assets, external imports, JavaScript or animations. **Columns and card order are configured in the web editor**, not with CSS.
