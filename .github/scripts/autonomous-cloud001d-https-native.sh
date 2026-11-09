#!/usr/bin/env bash
# Future explicit provider qualification: only the disposable emulator, no Sony.
# This script creates a synthetic public association on the dedicated 001-D Cloud.
# Do not run while provider writes remain rejected by automatic approval review.
set -euo pipefail
mkdir -p public001d-https
scenevibe_hosted_serial="$(adb get-serialno)"
[[ "$scenevibe_hosted_serial" == emulator-* ]]
test "$(adb shell getprop ro.kernel.qemu | tr -d '\r')" = 1
adb install -r app/build/outputs/apk/tvux002/app-tvux002.apk
adb install -r app/build/outputs/apk/androidTest/tvux002/app-tvux002-androidTest.apk
adb shell appops set com.scenevibe.tvcompanionpoc SYSTEM_ALERT_WINDOW allow
adb shell cmd notification allow_listener com.scenevibe.tvcompanionpoc/com.scenevibe.tvcompanionpoc.MediaSessionAccessService
adb shell am instrument -w -r -e class com.scenevibe.tvcompanionpoc.PublicHostedAndroidTest com.scenevibe.tvcompanionpoc.test/androidx.test.runner.AndroidJUnitRunner | tr -d '\r' | tee public001d-https/instrumentation.log
grep -q '^OK (1 test)$' public001d-https/instrumentation.log
grep -q '^INSTRUMENTATION_CODE: -1$' public001d-https/instrumentation.log
adb exec-out run-as com.scenevibe.tvcompanionpoc cat files/public001d-https-proof.json > public001d-https/proof.json
adb logcat -d -v brief -s SceneVibeRuntime:I SceneVibeMedia:I SceneVibeText009:I AndroidRuntime:E > public001d-https/diagnostics.log
