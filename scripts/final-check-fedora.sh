#!/usr/bin/env bash
set -euo pipefail

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$PROJECT_ROOT"
REPORT_DIR="$PROJECT_ROOT/build/final-check/$(date +%Y%m%d-%H%M%S)"
mkdir -p "$REPORT_DIR"

if ! command -v adb >/dev/null; then
  echo "ADB is missing. Install Android platform-tools first." >&2
  exit 1
fi
DEVICE_COUNT="$(adb devices | awk 'NR>1 && $2=="device" {count++} END {print count+0}')"
if [[ "$DEVICE_COUNT" != 1 ]]; then
  echo "Connect exactly one authorized PF1007 over ADB (found: $DEVICE_COUNT)." >&2
  adb devices -l
  exit 1
fi

{
  echo "=== DEVICE ==="
  adb shell getprop ro.product.model
  adb shell getprop ro.build.version.release
  adb shell getprop ro.build.version.sdk
  echo "=== VERSION / SIGNING ==="
  adb shell dumpsys package dev.maelab.haframe | grep -E 'versionName|versionCode' | head -5 || true
  echo "=== STORAGE ==="
  adb shell df -h /sdcard || true
} | tee "$REPORT_DIR/device.txt"

if [[ "${1:-}" != "--no-build" ]]; then
  bash scripts/build-fedora.sh 2>&1 | tee "$REPORT_DIR/build.log"
fi

APK="app/build/outputs/apk/debug/app-debug.apk"
if [[ ! -s "$APK" ]]; then
  echo "APK not found: $APK" >&2
  exit 1
fi

echo "Installing in place (existing settings and pairing are preserved)..."
if ! adb install -r "$APK" 2>&1 | tee "$REPORT_DIR/install.log"; then
  echo "Installation failed. Do NOT uninstall the old APK or clear app data." >&2
  exit 1
fi

adb shell am force-stop dev.maelab.haframe
adb logcat -c
adb shell am start -n dev.maelab.haframe/.MainActivity 2>&1 | tee "$REPORT_DIR/launch.txt"
sleep 10

adb logcat -d -v time -s AndroidRuntime:E > "$REPORT_DIR/android-crashes.log" || true
adb logcat -d -v time -s HAFrameApi:W HAFrame:E > "$REPORT_DIR/haframe-warnings.log" || true
adb shell dumpsys meminfo dev.maelab.haframe > "$REPORT_DIR/memory.txt" || true

if grep -Eq 'FATAL EXCEPTION|Process: dev.maelab.haframe|java.lang.NoClassDefFoundError' "$REPORT_DIR/android-crashes.log"; then
  echo "STARTUP FAILED. Attach $REPORT_DIR/android-crashes.log to the GitHub issue."
  exit 2
fi

if ! adb shell dumpsys activity activities | grep -q 'dev.maelab.haframe'; then
  echo "Application not visible. Check $REPORT_DIR/launch.txt and Android logs."
  exit 3
fi

echo "Startup smoke test passed."
echo "Report: $REPORT_DIR"
echo "Next: open HA Frame Studio, add a photo, a calendar and a conditional MDI tile."
