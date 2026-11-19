#!/usr/bin/env bash
set -euo pipefail

echo '=== DEVICE ==='
adb shell getprop ro.build.version.release
adb shell wm size

echo '=== APPLICATION ==='
adb shell pm list packages -3 | grep -E 'haframe|nexuslauncher' || true

echo '=== MEMORY ==='
adb shell dumpsys meminfo dev.maelab.haframe | tail -40

echo '=== CRASHES ==='
adb logcat -d -v brief | grep -E 'FATAL EXCEPTION|dev.maelab.haframe|AndroidRuntime' | tail -40 || true
