#!/usr/bin/env bash
set -euo pipefail
# Execute all 82 frozen corpus sequences against both real engines in the same Android VM.
RESULTS="$RUNNER_TEMP/scenevibe-m5-differential-results"
mkdir -p "$RESULTS"
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w com.scenevibe.tvcompanionpoc.test/com.scenevibe.tvcompanionpoc.M5MediaDifferentialInstrumentation | tr -d '\r' > "$RESULTS/instrumentation.txt"
adb logcat -d -s M5DifferentialTest > "$RESULTS/test-diagnostic.txt"
for expected in 'result=PASS' 'compared=82' 'hashmap=4' 'divergences=0'; do
  grep -qx "INSTRUMENTATION_RESULT: $expected" "$RESULTS/instrumentation.txt"
done
grep -qx 'INSTRUMENTATION_CODE: -1' "$RESULTS/instrumentation.txt"
adb exec-out run-as com.scenevibe.tvcompanionpoc cat cache/m5-phase-c-android.json > "$RESULTS/m5-phase-c-android.json"
python3 .github/scripts/m5-media-differential-summary.py "$RESULTS/m5-phase-c-android.json"
