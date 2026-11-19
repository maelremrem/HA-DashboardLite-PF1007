# PF1007 — Debloat, backups and security audit

**Applies to the verified second PF1007 only:** Android 6.0.1, `astar_m86_8723cs-eng 6.0.1 MOB30R 20260529 test-keys.1`, root ADB over USB. These are the steps actually performed and checked after a reboot. Other firmware versions may require a different recovery procedure.

> Disable packages rather than deleting system APKs. Keep a working alternative launcher and working USB ADB before touching AiMOR. Android 6 is no longer maintained, SELinux is disabled on this firmware, and the root/debug build remains a security risk even after cleanup. Put the frame on an isolated IoT network and allow only the Home Assistant endpoint it needs.

## 1. Back up first (Fedora)

```bash
mkdir -p ~/PF1007-2/backup ~/PF1007-2/audit
adb shell id
adb shell pm list packages -f > ~/PF1007-2/backup/packages.txt
adb shell getprop > ~/PF1007-2/backup/device-properties.txt
adb pull /system/priv-app/launcher_aimor ~/PF1007-2/backup/
adb pull /system/priv-app/frame_ota ~/PF1007-2/backup/
adb pull /system/priv-app/DeviceTest ~/PF1007-2/backup/
adb pull /system/xbin/su ~/PF1007-2/backup/su-original
ls -lah ~/PF1007-2/backup
```

These files are **not** a full firmware backup. Keep them locally on Fedora, **not in this Git repository**. In the original session an initial `cd ~/PF1007-2/backup` failed and `su-original` was downloaded to the current `audit` directory instead; the commands above create the directories explicitly.

For extra safety before changing `/system`, also consider a compressed image of the system partition (needs local free space):

```bash
adb exec-out 'cat /dev/block/by-name/system' | gzip -1 > ~/PF1007-2/backup/system-before-changes.img.gz
gzip -t ~/PF1007-2/backup/system-before-changes.img.gz
```

Restoring a partition image is a separate recovery operation, not covered here. Do not flash a backup from a different frame.

## 2. Install a fallback launcher

On the tested frame, **Rootless Pixel Launcher 3.9.1** (`amirz.rootless.nexuslauncher`) was used. Obtain the APK from a trusted source: https://f-droid.org/packages/amirz.rootless.nexuslauncher/.

```bash
adb install rootless.apk
adb shell am start -a android.intent.action.MAIN \
  -c android.intent.category.HOME \
  -p amirz.rootless.nexuslauncher
adb shell am start -a android.settings.HOME_SETTINGS
```

Select Rootless as the default Home app **on the frame**, and verify that both Home and Android Settings open. Do not remove it during HA Frame testing.

## 3. Debloat — reversible package disable

The following **six packages were disabled on the second frame and remained disabled after reboot**:

```bash
adb shell pm disable com.efercro.os.aimor
adb shell pm disable com.yhk.qeota
adb shell pm disable com.DeviceTest
adb shell pm disable com.android.gl2jni
adb shell pm disable com.svox.pico
adb shell pm disable jp.co.omronsoft.openwnn
adb shell pm list packages -d
```

| Package | Purpose | Notes |
|---|---|---|
| `com.efercro.os.aimor` | Vendor launcher / functionality | Disable **only after** Rootless is confirmed working |
| `com.yhk.qeota` | Vendor OTA updates | No automatic vendor updates afterwards |
| `com.DeviceTest` | Factory diagnostics | May be needed again for diagnosis |
| `com.android.gl2jni` | Bundled graphics demo | Unneeded for HA Frame |
| `com.svox.pico` | Text-to-speech | Optional; was not needed |
| `jp.co.omronsoft.openwnn` | Alternative keyboard | Keep `com.android.inputmethod.latin` enabled |

**Do not disable** `com.android.settings`, `com.android.documentsui`, `com.android.providers.media`, `com.android.externalstorage`, `com.android.webview`, Android framework, or the main keyboard.

Undo a disable with `adb shell pm enable <package>`. Example:

```bash
adb shell pm enable com.efercro.os.aimor
adb shell am start -a android.settings.HOME_SETTINGS
```

## 4. Read-only security audit

```bash
adb shell ps
adb shell netstat -an
adb shell 'netstat -an | grep :7806'
adb shell 'netstat -an | grep :5555'
adb shell 'ps | grep -Ei "aimor|qeota|DeviceTest|gl2jni"'
adb shell getprop | grep -E 'init.svc|service.adb|persist.sys.usb'
adb shell getprop ro.debuggable
adb shell getprop ro.secure
adb shell getenforce
adb shell ls -l /system/xbin/su
```

Observed before hardening:

```text
/system/xbin/su: -rwsr-sr-x root shell
ro.debuggable: 1
ro.secure: 0
SELinux: Disabled
```

Observed snapshot: no AiMOR/OTA/DeviceTest/gl2jni processes, no `7806` or `5555` network listener, only `127.0.0.1:5037` TCP listening. Other sockets such as `/dev/socket/netd` are local UNIX sockets, not remotely accessible TCP ports. This is **not** proof of a clean or uncompromised firmware. The system services `sayeye`, `perfprofd` and `console` were still present; their integrity was not independently checked.

## 5. Restrict the vendor `su` binary — verified procedure

The stock `/system/xbin/su` had setuid/setgid bits. On this exact root-ADB build the following modification was performed successfully, leaving USB ADB root intact:

```bash
# Confirm backup exists before continuing
ls -l ~/PF1007-2/backup/su-original
adb remount
adb shell chmod 0700 /system/xbin/su
adb shell ls -l /system/xbin/su
adb shell id
adb shell sync
adb shell mount -o remount,ro /system
adb shell mount | grep ' /system '
```

Expected:

```text
-rwx------ root shell ... su
uid=0(root) ...
/dev/block/by-name/system /system ext4 ro,...
```

On the verified unit these changes survived a reboot. The change only restricts this **one** executable; it does not secure every vendor service, prevent all privilege escalation, or fix disabled SELinux. Avoid `chmod` of other system binaries without a tested recovery plan.

## 6. Validate after reboot

```bash
adb reboot
adb wait-for-device
adb shell id
adb shell ls -l /system/xbin/su
adb shell mount | grep ' /system '
adb shell pm list packages -d
adb shell 'netstat -an | grep -E ":5555|:7806"'
```

Validated on the second frame:

- `uid=0(root)` over USB ADB;
- `su` mode `0700` (`-rwx------`);
- `/system` mounted `ext4 ro`;
- six vendor/optional packages still disabled;
- no `5555` or `7806` listener visible at test time.

For Home Assistant access, avoid giving HA Frame an admin account or actions controlling critical locks/alarms without added authorization. See the main README for current authentication and web editor limitations.
