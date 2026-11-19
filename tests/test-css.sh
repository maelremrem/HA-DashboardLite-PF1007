#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
TMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TMP_DIR"' EXIT
javac -d "$TMP_DIR" app/src/main/java/dev/maelab/haframe/CanvasTheme.java tests/CanvasThemeTest.java tests/ThemePresetsRunner.java
java -cp "$TMP_DIR" dev.maelab.haframe.CanvasThemeTest
node tests/theme-presets.test.cjs "$TMP_DIR/presets.json"
java -cp "$TMP_DIR" dev.maelab.haframe.ThemePresetsRunner "$TMP_DIR/presets.json"
node tests/web-config.test.cjs
