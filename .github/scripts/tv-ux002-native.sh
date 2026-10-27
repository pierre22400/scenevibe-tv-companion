#!/usr/bin/env bash
# Disposable Android TV only: real WindowManager, own foreground Activity and
# explicit normal launch handoff. No Sony, third-party application or Cloud operation.
set -euo pipefail
mkdir -p tv-ux002-native
scenevibe_serial="$(adb get-serialno)"
[[ "$scenevibe_serial" == emulator-* ]]
test "$(adb shell getprop ro.kernel.qemu | tr -d '\r')" = 1
adb shell wm size > tv-ux002-native/environment.txt
adb shell wm density >> tv-ux002-native/environment.txt
adb shell getprop ro.build.characteristics >> tv-ux002-native/environment.txt
adb install -r app/build/outputs/apk/tvux002/app-tvux002.apk
adb install -r app/build/outputs/apk/androidTest/tvux002/app-tvux002-androidTest.apk
adb shell appops set com.scenevibe.tvcompanionpoc SYSTEM_ALERT_WINDOW allow
adb shell am instrument -w -r -e class com.scenevibe.tvcompanionpoc.AdSyncAndroidTest,com.scenevibe.tvcompanionpoc.DeveloperOverlayAndroidTest com.scenevibe.tvcompanionpoc.test/androidx.test.runner.AndroidJUnitRunner | tr -d '\r' | tee tv-ux002-native/instrumentation.log
# Images contain only SceneVibe and the explicitly OWN foreground fixture, never protected content.
for image in prepared panel keypad; do
  if adb shell run-as com.scenevibe.tvcompanionpoc test -f "files/tvux002-$image.png"; then
    adb exec-out run-as com.scenevibe.tvcompanionpoc cat "files/tvux002-$image.png" > "tv-ux002-native/$image.png"
  fi
done
adb logcat -d -v brief -s SceneVibeAdSync:I SceneVibeMedia:I AndroidRuntime:E > tv-ux002-native/local-diagnostics.log
grep -q '^OK (12 tests)' tv-ux002-native/instrumentation.log
grep -q '^INSTRUMENTATION_CODE: -1$' tv-ux002-native/instrumentation.log
printf 'OWN_ANDROID_TV_FIXTURE\nREAL_TAB_SERVICE_AND_WINDOW_HANDOFF\nNO_CLOUD_OR_PRIME\nSONY_PRIME_HANDOFF_UNTESTED\n' > tv-ux002-native/scope.txt
