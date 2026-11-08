#!/usr/bin/env bash
# Uniquement l'émulateur Android TV jetable ; aucune installation Sony.
set -euo pipefail
mkdir -p public001b-native
scenevibe_public_serial="$(adb get-serialno)"
[[ "$scenevibe_public_serial" == emulator-* ]]
test "$(adb shell getprop ro.kernel.qemu | tr -d '\r')" = 1
adb install -r app/build/outputs/apk/tvux002/app-tvux002.apk
adb install -r app/build/outputs/apk/androidTest/tvux002/app-tvux002-androidTest.apk
adb shell appops set com.scenevibe.tvcompanionpoc SYSTEM_ALERT_WINDOW allow
adb shell cmd notification allow_listener com.scenevibe.tvcompanionpoc/com.scenevibe.tvcompanionpoc.MediaSessionAccessService
adb shell am instrument -w -r -e class com.scenevibe.tvcompanionpoc.PublicAutonomousAndroidTest com.scenevibe.tvcompanionpoc.test/androidx.test.runner.AndroidJUnitRunner | tr -d '\r' | tee public001b-native/public-tests.log
grep -q '^OK (10 tests)$' public001b-native/public-tests.log
grep -q '^INSTRUMENTATION_CODE: -1$' public001b-native/public-tests.log
adb exec-out run-as com.scenevibe.tvcompanionpoc cat files/public001b-native-proof.json > public001b-native/service-proof.json
# Le parcours privé utilise toujours ses propres références et son ancien consentement.
adb shell am instrument -w -r -e class com.scenevibe.tvcompanionpoc.ManualTrackAndroidTest com.scenevibe.tvcompanionpoc.test/androidx.test.runner.AndroidJUnitRunner | tr -d '\r' | tee public001b-native/private-retained.log
grep -q '^OK (3 tests)$' public001b-native/private-retained.log
adb shell am instrument -w -r -e class com.scenevibe.tvcompanionpoc.PublicRestartSeedTest com.scenevibe.tvcompanionpoc.test/androidx.test.runner.AndroidJUnitRunner | tr -d '\r' | tee public001b-native/restart-seed.log
grep -q '^OK (1 test)$' public001b-native/restart-seed.log
adb shell am force-stop com.scenevibe.tvcompanionpoc
adb shell am instrument -w -r -e class com.scenevibe.tvcompanionpoc.PublicRestartReadTest com.scenevibe.tvcompanionpoc.test/androidx.test.runner.AndroidJUnitRunner | tr -d '\r' | tee public001b-native/restart-process.log
grep -q '^OK (1 test)$' public001b-native/restart-process.log
adb reboot
adb wait-for-device
timeout 180 bash -c 'until [[ "$(adb shell getprop sys.boot_completed | tr -d "\r")" == 1 ]]; do sleep 2; done'
adb shell input keyevent 82
adb shell am instrument -w -r -e class com.scenevibe.tvcompanionpoc.PublicRestartReadTest com.scenevibe.tvcompanionpoc.test/androidx.test.runner.AndroidJUnitRunner | tr -d '\r' | tee public001b-native/restart-environment.log
grep -q '^OK (1 test)$' public001b-native/restart-environment.log
adb exec-out run-as com.scenevibe.tvcompanionpoc cat files/public001b-restart-proof.json > public001b-native/restart-proof.json
adb logcat -d -v brief -s SceneVibeRuntime:I SceneVibeMedia:I SceneVibeText009:I AndroidRuntime:E > public001b-native/diagnostics.log
