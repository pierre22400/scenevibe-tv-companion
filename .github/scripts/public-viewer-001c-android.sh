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
instrument_status=0
wait "$instrument" || instrument_status=$?
tr -d '\r' < viewer001c-evidence/instrumentation.log > viewer001c-evidence/instrumentation-normalized.log
grep -Fxq 'OK (1 test)' viewer001c-evidence/instrumentation-normalized.log
grep -Fxq 'INSTRUMENTATION_CODE: -1' viewer001c-evidence/instrumentation-normalized.log
test -s viewer001c-evidence/test-a.json
test -s viewer001c-evidence/media.json
test -s viewer001c-evidence/mobile-second-track.json
# Le redémarrage peut couper le client adb après le verdict AndroidJUnitRunner.
case "$instrument_status" in
    0|255) printf 'AndroidJUnitRunner PASS, preuve post-redémarrage présente. adb=%s\n' "$instrument_status" ;;
    *) exit "$instrument_status" ;;
esac
