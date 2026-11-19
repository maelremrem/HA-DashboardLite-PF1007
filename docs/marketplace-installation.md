# Installing HA Frame from GitHub

This repository contains **two separate parts**:

- The native Android app under `app/`, built and installed on the PF1007 over ADB.
- The Home Assistant integration under `custom_components/ha_frame/`, installed on your Home Assistant server.

The Home Assistant Marketplace installs **only the integration**, not the Android APK.

## Marketplace (Home Assistant 2026.11+)

Requirements:

1. The repository must be **public** on GitHub. The Marketplace cannot access private repositories, even with its GitHub connection.
2. `hacs.json` and `custom_components/ha_frame/manifest.json` must be on the repository's **default branch** (or available in a compatible release).
3. The Android **v0.7+** app must already be installed on the frame, connected to Wi-Fi, and have its integration API enabled.

At the time this document was added, v0.7 is still on the `feature/v0.7-cards-night-notify-ha-integration` branch. Do **not** expect the Marketplace to install it from `main` until that branch is merged and the repository is made public.

After those requirements are met:

1. In Home Assistant, open **Settings → Marketplace**.
2. Select **Add from link**, connect GitHub if prompted, and paste:
   `https://github.com/maelremrem/HA-DashboardLite-PF1007`
3. Choose **Integration** if prompted, add the repository, open it, then select **Install**.
4. Open **Settings → Devices & services → Add integration** and choose **HA Frame PF1007** (restart Home Assistant if the Marketplace prompts you to).
5. On the frame, enable **HA integration** and generate a new two-minute pairing PIN.
6. Enter the frame's LAN IP address and PIN in the Home Assistant setup form.

Once installed, Marketplace updates the integration from GitHub; updating the Android APK remains a separate ADB build/install process.

**Security:** Making a repository public exposes its committed source and history. Review that it contains no passwords, Home Assistant tokens, Wi-Fi credentials, or private configuration before changing visibility. The `local.properties` and `*.apk` files are excluded by `.gitignore`, but review old commits too.

## Private repository: install from a Git checkout

If you want to **keep the repository private**, use a trusted Home Assistant host/container with `git` and a working read-only SSH deploy key for this repository. The Marketplace cannot install the private repository.

For an environment where `/config` is Home Assistant's config folder:

```bash
cd /config
git clone -b feature/v0.7-cards-night-notify-ha-integration \
  git@github.com:maelremrem/HA-DashboardLite-PF1007.git ha-frame-source
mkdir -p /config/custom_components
cp -a ha-frame-source/custom_components/ha_frame /config/custom_components/
```

Restart Home Assistant and then follow the pairing steps in [Home Assistant integration](home-assistant-integration.md).

To update from the same branch:

```bash
cd /config/ha-frame-source
git pull --ff-only
cp -a custom_components/ha_frame /config/custom_components/
```

Restart Home Assistant after updating the component code. Adjust `/config` if your installation uses a different configuration directory. Avoid storing deploy keys or tokens inside the Git repository.

## Fedora: build and install Android v0.7

The repository's default branch is v0.6 until the v0.7 pull request is merged:

```bash
cd ~/PF1007-2/HA-DashboardLite-PF1007
git fetch origin
git switch -c v0.7-test --track origin/feature/v0.7-cards-night-notify-ha-integration

export JAVA_HOME=/usr/lib/jvm/temurin-17-jdk
export ANDROID_HOME="$HOME/Android/Sdk"
bash scripts/build-fedora.sh
adb devices -l
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n dev.maelab.haframe/.MainActivity
```

Use `bash scripts/build-fedora.sh` if a checkout reports `Permission denied`: some older commits stored the script without the executable bit. **Do not run the build with sudo.** If you prefer the `./` syntax, run `chmod +x scripts/build-fedora.sh` locally first.

## Troubleshooting

- `adb: no devices/emulators found`: ensure the USB cable and ADB mode are active, then run `adb kill-server && adb start-server && adb devices -l`.
- `Permission denied` on the build script: run it explicitly with `bash` instead of `sudo`.
- Marketplace cannot add the repository: check **public visibility**, `hacs.json` and `custom_components/ha_frame/` on the **default branch**.
- HA Frame cannot pair: enable its API on the frame, check the displayed IP, TCP port **8766**, and the short-lived PIN; permit traffic from Home Assistant to the frame across VLANs.

The integration API is local HTTP, so restrict access to your trusted LAN/IoT network; do not expose port 8766 to the Internet.
