#!/usr/bin/env bash
# Initial setup and framework shortcut only on a disposable emulator, never Sony.
set -euo pipefail
mkdir -p mute002-p0
scenevibe_mute_serial="$(adb get-serialno)"
[[ "$scenevibe_mute_serial" == emulator-* ]]
test "$(adb shell getprop ro.kernel.qemu | tr -d '\r')" = 1
adb install -r app/build/outputs/apk/tvux002/app-tvux002.apk
adb install -r app/build/outputs/apk/androidTest/tvux002/app-tvux002-androidTest.apk
adb shell am instrument -w -r -e class com.scenevibe.tvcompanionpoc.CompanionShortcutConnectionAndroidTest com.scenevibe.tvcompanionpoc.test/androidx.test.runner.AndroidJUnitRunner | tr -d '\r' | tee mute002-p0/instrumentation.log
grep -q '^OK (1 test)$' mute002-p0/instrumentation.log
grep -q '^INSTRUMENTATION_CODE: -1$' mute002-p0/instrumentation.log
adb logcat -d -v brief -s SceneVibeMute002:I AndroidRuntime:E > mute002-p0/shortcut.log
