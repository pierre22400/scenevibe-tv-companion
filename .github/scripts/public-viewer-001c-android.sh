#!/usr/bin/env bash
# Exécute le vrai service et Chromium ensemble sur l'émulateur de qualification.
set -euo pipefail
umask 077
mkdir -p viewer001c-evidence
test "$(adb shell getprop ro.kernel.qemu | tr -d '\r')" = 1
adb install -r app/build/outputs/apk/tvux002/app-tvux002.apk
adb install -r app/build/outputs/apk/androidTest/tvux002/app-tvux002-androidTest.apk
adb shell am instrument -w -r -e class com.scenevibe.tvcompanionpoc.HistoricalViewerHttpsAndroidTest \
    com.scenevibe.tvcompanionpoc.test/androidx.test.runner.AndroidJUnitRunner > viewer001c-evidence/instrumentation.log 2>&1 &
instrument=$!
trap 'kill "$instrument" 2>/dev/null || true' EXIT
node .github/scripts/public-viewer-001c-browser.mjs
wait "$instrument"
tr -d '\r' < viewer001c-evidence/instrumentation.log | grep -q '^OK (1 test)$'
