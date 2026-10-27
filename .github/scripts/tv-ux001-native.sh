#!/usr/bin/env bash
# Disposable Android TV only: real WindowManager, own foreground Activity and
# notification capability. No Sony, third-party application or Cloud operation.
set -euo pipefail
mkdir -p tv-ux001-native
scenevibe_serial="$(adb get-serialno)"
[[ "$scenevibe_serial" == emulator-* ]]
test "$(adb shell getprop ro.kernel.qemu | tr -d '\r')" = 1
adb install -r app/build/outputs/apk/tvux001/app-tvux001.apk
adb install -r app/build/outputs/apk/androidTest/tvux001/app-tvux001-androidTest.apk
adb shell appops set com.scenevibe.tvcompanionpoc SYSTEM_ALERT_WINDOW allow
adb shell am instrument -w -r -e class com.scenevibe.tvcompanionpoc.AdSyncAndroidTest,com.scenevibe.tvcompanionpoc.TvUxAndroidTest com.scenevibe.tvcompanionpoc.test/androidx.test.runner.AndroidJUnitRunner | tr -d '\r' | tee tv-ux001-native/instrumentation.log
adb logcat -d -v brief -s SceneVibeAdSync:I AndroidRuntime:E > tv-ux001-native/local-diagnostics.log
grep -q '^OK (9 tests)' tv-ux001-native/instrumentation.log
grep -q '^INSTRUMENTATION_CODE: -1$' tv-ux001-native/instrumentation.log
printf 'OWN_ANDROID_TV_FIXTURE\nREAL_WINDOW_AND_NOTIFICATION_CAPABILITY\nNO_CLOUD_OR_PRIME\nSONY_REMOTE_NOTIFICATION_ENTRY_UNPROVEN\n' > tv-ux001-native/scope.txt
