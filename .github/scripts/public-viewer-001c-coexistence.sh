#!/usr/bin/env bash
# Banc jetable. Exige la preuve native ainsi que le verdict AndroidJUnitRunner.
set -euo pipefail
mkdir -p viewer001c-coexistence
test "$(adb shell getprop ro.kernel.qemu | tr -d '\r')" = 1
adb install -r app/build/outputs/apk/tvux002/app-tvux002.apk
adb install -r app/build/outputs/apk/androidTest/tvux002/app-tvux002-androidTest.apk
adb shell am instrument -w -r -e class com.scenevibe.tvcompanionpoc.HistoricalMediaCoexistenceTest \
  com.scenevibe.tvcompanionpoc.test/androidx.test.runner.AndroidJUnitRunner | tr -d '\r' | tee viewer001c-coexistence/instrumentation.log
grep -Fx 'OK (1 test)' viewer001c-coexistence/instrumentation.log
grep -Fx 'INSTRUMENTATION_CODE: -1' viewer001c-coexistence/instrumentation.log
adb exec-out run-as com.scenevibe.tvcompanionpoc cat files/001c-coexistence.json > viewer001c-coexistence/coexistence.json
