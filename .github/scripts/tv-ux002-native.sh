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
# Fail fast on the new idle-service path before the retained seven-minute suite.
# This is the same owned Android case, not another provider or physical campaign.
adb shell am instrument -w -r -e class 'com.scenevibe.tvcompanionpoc.MultiPlatformObservationAndroidTest#idleServicePhoneCampaignsDoNotActivateTrack' com.scenevibe.tvcompanionpoc.test/androidx.test.runner.AndroidJUnitRunner | tr -d '\r' | tee tv-ux002-native/idle-observation-smoke.log
grep -q '^OK (1 test)' tv-ux002-native/idle-observation-smoke.log
adb shell am instrument -w -r -e class com.scenevibe.tvcompanionpoc.AdSyncAndroidTest,com.scenevibe.tvcompanionpoc.AdDetectionAndroidTest,com.scenevibe.tvcompanionpoc.MultiPlatformObservationAndroidTest,com.scenevibe.tvcompanionpoc.DeveloperOverlayAndroidTest,com.scenevibe.tvcompanionpoc.RecoveryCacheAndroidTest com.scenevibe.tvcompanionpoc.test/androidx.test.runner.AndroidJUnitRunner | tr -d '\r' | tee tv-ux002-native/instrumentation.log
# Images contain only SceneVibe and the explicitly OWN foreground fixture, never protected content.
for image in prepared panel keypad monitor on-focus off-focus; do
  if adb shell run-as com.scenevibe.tvcompanionpoc test -f "files/tvux002-$image.png"; then
    adb exec-out run-as com.scenevibe.tvcompanionpoc cat "files/tvux002-$image.png" > "tv-ux002-native/$image.png"
  fi
done
for proof in input service runtime003 runtime004-replay runtime004-toggle runtime004-fifteen runtime004-port runtime004-calibration ad-detection ad-detection-service ad002-text ad002-audio ad002-upcoming ad002-restart multi-netflix multi-disney_plus multi-idle-service multi-prime; do
  if adb shell run-as com.scenevibe.tvcompanionpoc test -f "files/tvux002-$proof-proof.json"; then
    adb exec-out run-as com.scenevibe.tvcompanionpoc cat "files/tvux002-$proof-proof.json" > "tv-ux002-native/$proof-proof.json"
  fi
done
adb logcat -d -v brief -s SceneVibeControls:I SceneVibeAdSync:I SceneVibeMedia:I SceneVibeText009:I SceneVibeCache:I SceneVibeRuntime:I SceneVibeAdProbe:I SceneVibeAdGuard:I SceneVibeMultiAd:I AndroidRuntime:E > tv-ux002-native/local-diagnostics.log
grep -q '^OK (49 tests)' tv-ux002-native/instrumentation.log
grep -q '^INSTRUMENTATION_CODE: -1$' tv-ux002-native/instrumentation.log
# Each phase must pass separately; force-stop is restricted to the disposable emulator app above.
adb shell am instrument -w -r -e class com.scenevibe.tvcompanionpoc.CloudBridge003eRestartSeedTest com.scenevibe.tvcompanionpoc.test/androidx.test.runner.AndroidJUnitRunner | tr -d '\r' | tee tv-ux002-native/restart-seed.log
grep -q '^OK (1 test)' tv-ux002-native/restart-seed.log
adb shell am force-stop com.scenevibe.tvcompanionpoc
adb shell am instrument -w -r -e class com.scenevibe.tvcompanionpoc.CloudBridge003eRestartReadTest com.scenevibe.tvcompanionpoc.test/androidx.test.runner.AndroidJUnitRunner | tr -d '\r' | tee tv-ux002-native/restart-read.log
grep -q '^OK (1 test)' tv-ux002-native/restart-read.log
adb exec-out run-as com.scenevibe.tvcompanionpoc cat files/cloud003e-restart-proof.json > tv-ux002-native/restart-proof.json
printf 'OWN_ANDROID_TV_FIXTURE\nREAL_TAB_SERVICE_AND_WINDOW_HANDOFF
PASSIVE_ZERO_KEYS_REACH_OWN_VIEWER\nEXPLICIT_NOTIFICATION_FOCUS_CANDIDATE\nPAIRED_LOCAL_PHONE_COMMANDS_NOT_SONY_DPAD\nACTUAL_SERVICE_ADSYNC_RECOVERY_19_EVENTS\nDURABLE_CACHE_DIFFERENT_PROCESS\nPASSIVE_DEFAULT_EXPLICIT_FOCUS_RISK\nOWN_PUBLIC_METADATA_AD_START_END_CANDIDATE_ONLY\nOBSERVATION_DISABLED_BY_DEFAULT_NO_COMMENT_GATE\nNO_CLOUD_OR_PRIME\nSONY_PRIME_HANDOFF_UNTESTED\n' > tv-ux002-native/scope.txt
