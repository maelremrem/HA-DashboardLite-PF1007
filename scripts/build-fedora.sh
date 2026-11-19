#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
VERSION="8.7"
GRADLE_DIR="$HOME/.cache/haframe/gradle-$VERSION"

# AGP 8.6.1 and Gradle 8.7 should run with JDK 17 or 21, not Fedora 44's Java 25.
if [[ -z "${JAVA_HOME:-}" && -x /usr/lib/jvm/temurin-17-jdk/bin/java ]]; then
  export JAVA_HOME=/usr/lib/jvm/temurin-17-jdk
  export PATH="$JAVA_HOME/bin:$PATH"
fi

if ! command -v java >/dev/null 2>&1; then
  echo 'Java 17 is required. Install temurin-17-jdk from Adoptium.' >&2
  exit 1
fi

JAVA_VERSION="$(java -version 2>&1 | sed -nE 's/.*version "([0-9]+).*/\1/p' | head -1)"
if [[ -z "$JAVA_VERSION" || "$JAVA_VERSION" -gt 21 || "$JAVA_VERSION" -lt 17 ]]; then
  echo "Unsupported Gradle runtime: $(java -version 2>&1 | head -1)" >&2
  echo 'Use JDK 17. On Fedora 44: install adoptium-temurin-java-repository and temurin-17-jdk.' >&2
  echo 'Then export JAVA_HOME=/usr/lib/jvm/temurin-17-jdk before building.' >&2
  exit 1
fi

echo "Building with $(java -version 2>&1 | head -1)"

if [[ -z "${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}" ]]; then
  echo 'Set ANDROID_HOME or ANDROID_SDK_ROOT to an Android SDK with platform 35.' >&2
  exit 1
fi

if [[ ! -x "$GRADLE_DIR/bin/gradle" ]]; then
  mkdir -p "$HOME/.cache/haframe"
  ZIP="$HOME/.cache/haframe/gradle-$VERSION-bin.zip"
  URL="https://services.gradle.org/distributions/gradle-$VERSION-bin.zip"
  curl --fail --location --retry 3 "$URL" --output "$ZIP"
  HASH="$(curl --fail --location --retry 3 "$URL.sha256" | awk '{print $1}')"
  echo "$HASH  $ZIP" | sha256sum --check
  unzip -q -o "$ZIP" -d "$HOME/.cache/haframe"
fi

"$GRADLE_DIR/bin/gradle" -p "$ROOT" --no-daemon assembleDebug
printf '\nAPK: %s\n' "$ROOT/app/build/outputs/apk/debug/app-debug.apk"
