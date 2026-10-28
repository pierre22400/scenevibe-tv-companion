#!/usr/bin/env bash
# Qualify SceneVibe-owned MEDIA, local MP3 and Settings fixtures on a disposable
# Android TV emulator. This script has no platform-player or Cloud capability.
set -euo pipefail
mkdir -p ad-sync001-native
scenevibe_serial="$(adb get-serialno)"
[[ "$scenevibe_serial" == emulator-* ]]
test "$(adb shell getprop ro.kernel.qemu | tr -d '\r')" = 1
adb install -r app/build/outputs/apk/adsync001/app-adsync001.apk
adb install -r app/build/outputs/apk/androidTest/adsync001/app-adsync001-androidTest.apk
adb shell am instrument -w -r -e class com.scenevibe.tvcompanionpoc.AdSyncAndroidTest com.scenevibe.tvcompanionpoc.test/androidx.test.runner.AndroidJUnitRunner | tr -d '\r' | tee ad-sync001-native/instrumentation.log
grep -q '^OK (4 tests)' ad-sync001-native/instrumentation.log
grep -q '^INSTRUMENTATION_CODE: -1$' ad-sync001-native/instrumentation.log
adb logcat -d -v brief -s SceneVibeAdSync:I AndroidRuntime:E > ad-sync001-native/local-diagnostics.log
printf 'OWN_ANDROID_TV_FIXTURE\nNO_CLOUD_ORIGIN\nNO_PRIME_AD_OBSERVED\n' > ad-sync001-native/scope.txt
