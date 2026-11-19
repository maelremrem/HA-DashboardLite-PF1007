# HA Frame Studio / PF1007 — roadmap

Original baseline: **Home Assistant integration 1.4.0** + **Android APK 1.1.1** (Android 6.0.1, API 23, 1280×800).
The release numbers below are **planned**, not guarantees. Ship in order, with separate Home Assistant and Android versions as needed.

Priority: **P0** = protect existing data / essential foundation; **P1** = main functionality; **P2** = nice-to-have.
Each phase needs a green CI run, UI review in Home Assistant, a test with a physical PF1007, and a documented rollback before merging.

| Phase | Priority | Scope | Deliverables | Android APK required? |
| --- | --- | --- | --- | --- |
| **1.5 — Studio workflow** | **P0** | Editing safety and speed | Undo/redo (bounded, isolated per frame), keyboard shortcuts, direct tile span controls in preview, copy/paste per-tile appearance, clearer save/discard state | **No** — HA integration only |
| **1.6 — Rich widgets** | **P1** | Reliable HA-powered information widgets | Calendar (next events), task lists, Bambu Lab status/progress, energy, air quality, presence, circular gauge, improved weather and **time-based** history graph | **Yes** for new native Canvas widgets; HA prepares compact datasets |
| **1.7 — Rules & context** | **P1** | Smart tiles and automatic navigation | State thresholds and conditional color/icon/visibility; page selection by time, HA entity, presence; HA-triggered overlays/alerts, priority and expiry | **Yes**, with validated rules and safe fallback |
| **1.8 — Touch interaction** | **P1** | Native control surfaces | Configurable long press, validated buttons, light dimmer slider, thermostat target, cover position slider, camera full-screen view and back navigation | **Yes**, preserve action authorization and avoid preview actions |
| **1.9 — Photos & screensaver** | **P1** | Native photo frame mode | Local slideshow, HA-provided photo sources, clock overlay, quiet hours, transitions and wake-on-alert/touch | **Yes**, memory-bounded decoder/cache and offline fallback |
| **2.0 — Fleet & recovery** | **P0** | Multi-frame maintenance and backups | Named configuration profiles, version history, export/import + restore, selective sync across frames, read-only screenshot/diagnostics/logs, authenticated remote restart | **Yes** for device screenshot/restart; HA-only for profile tools |
| **2.1 — Updates & hardening** | **P2** | Secure remote lifecycle | Opt-in signed APK updates with explicit confirmation and rollback strategy, health checks, network retry tuning, performance/battery/thermal guardrails | **Yes**, feasibility of silent installs must be verified on rooted PF1007 |

## v1.5 delivery contract — first implementation

- **History:** max 50 independent edits, per selected frame; undo/redo must not call HA services or save remotely. A new edit after undo discards the redo branch. Reload/frame switch discards the local history; successful Save becomes the new baseline.
- **Save state:** explicit Save remains the only remote mutation. An edit undone back to the saved baseline must not show as dirty; buttons reflect availability. Current revision-based conflict detection stays in place.
- **Tile sizing:** change width (1–page columns) and height (1–2 rows) from the visual preview, with Android-equivalent first-fit collision checks and safe rollback. Existing drag-to-reorder and inspector span selectors remain usable.
- **Styling:** copy/paste the selected tile’s native Canvas style locally, without altering type, icon, entity or action. Clipboard stays in the current Studio session.
- **Keyboard:** Ctrl/Cmd+Z (undo), Ctrl/Cmd+Shift+Z or Ctrl/Cmd+Y (redo) while Studio has focus, without hijacking native undo in text inputs.
- **Compatibility:** no new config schema fields, firmware changes, authentication permissions or network endpoints. Existing APK 1.1.1 works unchanged.
- **Verification:** automated history/shortcut/resize/clipboard tests plus current Python, JS and Android CI. Validate physically before marking done.

## v1.6 acceptance / architecture notes

- Prefer Home Assistant sensor/calendar/todo APIs and server-side aggregation; Android receives **bounded, sampled, read-only** data only for visible cards.
- Avoid creating hard-coded Bambu integrations: discover existing HA entities, validate required fields, and provide graceful missing-data states.
- Histories need timestamps and defined windows (e.g. 1 h / 24 h / 7 days). The existing in-memory 24-value graph **does not** qualify as a time-based history chart.
- Use lazy updates, 1 visible camera stream maximum, no autoplay audio, and a simple non-animated fallback if the device is overloaded.

## v1.7–2.1 safeguards

- Conditional rules use an allow-listed, declarative format (no eval or arbitrary JS/CSS injection).
- **No action** should run from a Studio preview. A remote reboot/update requires an explicit admin action, confirmation, authenticated device communication and a visible status/result.
- Photo sources must have explicit authorization and bounded file/thumbnail sizes; never embed HA access tokens in Studio pages.
- Profile synchronization is opt-in per frame; preserve each frame’s separate configuration, revisions and device identity.
- A rooted PF1007 may support privileged installation, but signed APK distribution, recovery and rollback must be designed before enabling remote updates.

## Implementation status (v2.1 feature branch)

**v1.5: merged into main**, including the Studio workflow and its tests.

**v1.6–v2.1: implemented as one experimental integration + APK 2.1.0 feature bundle** on `feature/v2-roadmap-native-dashboard` (PR #12). The core data paths, UI controls and native renderers are present; automated tests compile and check contracts. Device-level acceptance is intentionally postponed to the final test phase.

The following original roadmap items are **partial or intentionally deferred**:

- Rich calendar/todo/Bambu data: consumes HA entity state/attributes; a full server-side multi-event/calendar/todo-item aggregator isn't included.
- Dynamic icons on rule changes: conditional backgrounds and hiding work; dynamic icon switching is not included.
- Photo transitions: basic image switching; animation and HA media library album synchronization not included.
- Remote management: refresh/reload of the app dashboard, **not an Android system reboot**.
- OTA: local signed APK verification and on-device installation confirmation, **not unattended network download, remote silent installation or automatic rollback**.
- HA notifications use the existing notify endpoint; additional image attachments and a priority-based notification queue not included.

This is an **experimental release candidate until tested physically on Android 6 PF1007**. Do not rely on unattended updates. See [v2 test guide](v2-setup.md).

## Milestones and review

1. **v1.5 merged into main.** The v2.1 bundle is in draft PR #12; test on the physical PF1007 before a stable release.
2. Before each later release, confirm widget data contracts and memory/performance budget on Android 6.
3. Document Home Assistant ↔ APK minimum compatible versions, upgrade path and safe fallback.
4. Keep CI green and validate at least one physical device after each Android-facing change.
