#!/usr/bin/env bash
# Build locally on Linux or Windows WSL2. Needs: a JDK 17, and Gradle itself (there's no
# ./gradlew in this project — see README_APK_KOTLIN.md for why), and the Android SDK's
# command-line tools with platform 34 and build-tools 34.0.0 installed and on PATH
# (ANDROID_HOME set, sdkmanager run once to install them).
set -euo pipefail
cd "$(dirname "$0")"
command -v gradle >/dev/null || { echo "Install Gradle first (e.g. via sdkman: sdk install gradle 8.9)" >&2; exit 1; }
command -v sdkmanager >/dev/null || echo "Warning: sdkmanager not found on PATH — make sure ANDROID_HOME/cmdline-tools are set up." >&2
gradle assembleDebug --no-daemon
echo
echo "APK: $(ls app/build/outputs/apk/debug/*.apk)"
