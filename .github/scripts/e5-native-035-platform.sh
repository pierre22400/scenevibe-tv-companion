#!/usr/bin/env bash
# E5-NATIVE-035 software emulator only. Never connect to an IP, use -d, or touch a Sony.
# All native session commands in the custom instrumentation are explicitly simulated.
set -euo pipefail
SCENEVIBE_EMULATOR_SERIAL="${ANDROID_SERIAL:-emulator-5554}"
if [[ ! "$SCENEVIBE_EMULATOR_SERIAL" =~ ^emulator-[0-9]+$ ]]; then
  echo "Refused non-emulator serial"
  exit 2
fi
mkdir -p e5-platform-results
adb -s "$SCENEVIBE_EMULATOR_SERIAL" shell getprop ro.build.version.sdk > e5-platform-results/api.txt
adb -s "$SCENEVIBE_EMULATOR_SERIAL" install -r app/build/outputs/apk/debug/app-debug.apk
adb -s "$SCENEVIBE_EMULATOR_SERIAL" install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb -s "$SCENEVIBE_EMULATOR_SERIAL" shell appops set com.scenevibe.tvcompanionpoc SYSTEM_ALERT_WINDOW allow
adb -s "$SCENEVIBE_EMULATOR_SERIAL" logcat -c
instrument_status=0
timeout 180s adb -s "$SCENEVIBE_EMULATOR_SERIAL" shell am instrument -w \
  com.scenevibe.tvcompanionpoc.test/com.scenevibe.tvcompanionpoc.E5NativeInstrumentation \
  | tee e5-platform-results/instrumentation.txt || instrument_status=$?
adb -s "$SCENEVIBE_EMULATOR_SERIAL" logcat -d -s E5_PLATFORM SceneVibeE5 AndroidRuntime MediaPlayer MediaExtractor \
  > e5-platform-results/logcat.txt
if [[ "$instrument_status" != 0 ]]; then
  echo "Instrumentation failed or exceeded its finite budget: $instrument_status"
  exit "$instrument_status"
fi
if ! grep -q 'INSTRUMENTATION_RESULT: failed=0' e5-platform-results/instrumentation.txt \
  || ! grep -q 'INSTRUMENTATION_RESULT: passed=5' e5-platform-results/instrumentation.txt; then
  echo "Platform assertions failed or missing: no synthetic PASS"
  exit 1
fi
echo "SOFTWARE PLATFORM PASS — native session SIMULATED; SONY NOT EXECUTED"
