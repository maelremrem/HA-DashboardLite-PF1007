# PF1007 — ADB access from Fedora

This guide describes the **PF1007 / PF1007L AiMOR** unit tested with Android **6.0.1**, build `astar_m86_8723cs-eng 6.0.1 MOB30R 20260529 test-keys.1`. Other revisions may behave differently. Read-only inspection comes first; do not blindly apply root/system commands to a different device.

## 1. Connect the frame

On Fedora, install the Android Platform Tools and connect a **data-capable USB cable**:

```bash
sudo dnf install -y android-tools usbutils
adb version
lsusb
adb devices -l
```

If ADB is disabled and the frame still has the stock AiMOR interface, on **some** builds use **Settings → About → repeatedly tap Model Number → Open test app**. The factory test menu password observed for this firmware family is `YHK88888888`; choose its ADB-enabled option (often labelled `IS CLOSED` → `is open`). This menu is vendor-specific and may be absent on other firmware.

**Do not enable ADB over Wi-Fi.** The tested build provides a root ADB shell over USB, which should not be exposed on a network.

## 2. Confirm the actual firmware

```bash
adb devices -l
adb shell id
adb shell getprop ro.build.version.release
adb shell getprop ro.build.display.id
adb shell getprop persist.sys.usb.config
adb shell getprop ro.product.cpu.abi
adb shell wm size
adb shell wm density
```

On the tested second frame, the outputs included:

```text
uid=0(root)
6.0.1
astar_m86_8723cs-eng 6.0.1 MOB30R 20260529 test-keys.1
mass_storage,adb
armeabi-v7a
Physical size: 1280x800
Physical density: 160
```

If `adb shell id` does **not** report `uid=0(root)`, do not perform the system modification steps in the [debloat guide](debloat-security.md).

## 3. Open Android settings without a Home key

```bash
adb shell input keyevent KEYCODE_HOME
adb shell input keyevent KEYCODE_BACK
adb shell am start -a android.settings.SETTINGS
adb shell am start -a android.settings.WIFI_SETTINGS
adb shell am start -a android.settings.HOME_SETTINGS
```

The frame has no physical Home button; simulated key events and `am start` let you reach Android Settings.

## 4. Configure and check Wi-Fi

Open the Wi-Fi settings using ADB and **type the password on the frame** instead of putting it in a terminal script:

```bash
adb shell svc wifi enable
adb shell am start -a android.settings.WIFI_SETTINGS
adb shell ip addr show wlan0
adb shell date
```

For a powered-on display, these Android 6 settings may help; confirm the result on this firmware:

```bash
adb shell settings put global wifi_sleep_policy 2
adb shell settings put global auto_time 1
adb shell settings put global auto_time_zone 1
adb shell settings put system screen_off_timeout 2147483647
```

## 5. Keep recovery access

```bash
adb reboot
adb wait-for-device
adb shell id
adb shell pm list packages -d
adb shell am start -a android.settings.HOME_SETTINGS
```

For later application development, use the [Fedora build setup](fedora-build.md). For manufacturer app removal and `su` hardening, use the [debloat/security guide](debloat-security.md).
