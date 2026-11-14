#!/usr/bin/env bash
# Banc Android TV jetable pour Studio Q2-D, avec installation et contrôle du certificat M5.
set -euo pipefail
package=com.scenevibe.tvcompanionpoc
[[ "$(adb get-serialno)" =~ ^emulator-[0-9]+$ ]]
[[ "$(adb shell getprop ro.kernel.qemu | tr -d '\r')" == 1 ]]
tools="${ANDROID_HOME:?}/build-tools/34.0.0"
apk="$RUNNER_TEMP/q2d-android/scenevibe-companion-studio-private-q2-v40-m5.apk"
"$tools/apksigner" verify --verbose --print-certs "$apk" > "$SCENEVIBE_STUDIO_EVIDENCE/android-certificate.txt"
grep -Fxq 'Signer #1 certificate SHA-256 digest: f908bf564ed97ba67e02b1ebc89eb0239cf980752587f55eb9ec0419791a2e9c'     "$SCENEVIBE_STUDIO_EVIDENCE/android-certificate.txt"
adb install "$apk"
adb install "$RUNNER_TEMP/q2d-test/scenevibe-q2d-tests-m5.apk"
adb install "$RUNNER_TEMP/q2d-test/scenevibe-q2d-owned-player.apk"
adb shell appops set "$package" SYSTEM_ALERT_WINDOW allow
adb shell cmd notification allow_listener "$package/$package.MediaSessionAccessService"
adb shell input keyevent KEYCODE_WAKEUP
timeout --foreground 12m node qualification/studio-private-q2/https.mjs
