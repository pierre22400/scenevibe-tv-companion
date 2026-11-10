#!/usr/bin/env bash
# Initial setup and framework shortcut only on a disposable emulator, never Sony.
set -euo pipefail
mkdir -p mute002-p0
scenevibe_mute_serial="$(adb get-serialno)"
[[ "$scenevibe_mute_serial" == emulator-* ]]
test "$(adb shell getprop ro.kernel.qemu | tr -d '\r')" = 1
# Always preserve bounded connection diagnostics, including a failed binding.
collect_mute002_p0() {
  adb logcat -d -v brief -s SceneVibeMute002:I AndroidRuntime:E AccessibilityManagerService:I > mute002-p0/shortcut.log || true
  adb shell dumpsys accessibility | grep -E 'User state|boundServices|enabledServices|bindingServices|UiAutomation|suppress|CompanionShortcutService|SystemAction|mIsAccessibilityEnabled' > mute002-p0/binding-state.txt || true
  adb shell dumpsys package com.scenevibe.tvcompanionpoc | grep -A 12 -B 2 CompanionShortcutService > mute002-p0/component.txt || true
  adb shell dumpsys package com.scenevibe.tvcompanionpoc | grep 'User 0:' >> mute002-p0/component.txt || true
}
trap collect_mute002_p0 EXIT
adb install -r app/build/outputs/apk/tvux002/app-tvux002.apk
adb install -r app/build/outputs/apk/androidTest/tvux002/app-tvux002-androidTest.apk
adb shell am instrument -w -r -e class com.scenevibe.tvcompanionpoc.CompanionShortcutConnectionAndroidTest com.scenevibe.tvcompanionpoc.test/androidx.test.runner.AndroidJUnitRunner | tr -d '\r' | tee mute002-p0/instrumentation.log
grep -q '^OK (1 test)$' mute002-p0/instrumentation.log
grep -q '^INSTRUMENTATION_CODE: -1$' mute002-p0/instrumentation.log
adb logcat -d -v brief -s SceneVibeMute002:I AndroidRuntime:E > mute002-p0/shortcut.log

# Admission and rendering use the retained owned cache/MediaCodec fixture, not a fabricated clock.
adb shell appops set com.scenevibe.tvcompanionpoc SYSTEM_ALERT_WINDOW allow
adb shell cmd notification allow_listener com.scenevibe.tvcompanionpoc/com.scenevibe.tvcompanionpoc.MediaSessionAccessService
adb shell am instrument -w -r -e class com.scenevibe.tvcompanionpoc.ManualTrackAndroidTest com.scenevibe.tvcompanionpoc.test/androidx.test.runner.AndroidJUnitRunner | tr -d '\r' | tee mute002-p0/admitted-opening.log
grep -q '^OK (3 tests)$' mute002-p0/admitted-opening.log
grep -q '^INSTRUMENTATION_CODE: -1$' mute002-p0/admitted-opening.log
adb exec-out run-as com.scenevibe.tvcompanionpoc cat files/companion-mute002-p0-open.json > mute002-p0/open-proof.json
