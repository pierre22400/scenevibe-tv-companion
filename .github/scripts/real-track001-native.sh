#!/usr/bin/env bash
# Réutilise les 51 cas historiques et les deux redémarrages, sans retirer d'assertion.
# Le script appelé refuse tout appareil qui n'est pas notre émulateur jetable.
set -euo pipefail
bash .github/scripts/tv-ux002-native.sh
adb shell am instrument -w -r -e class com.scenevibe.tvcompanionpoc.ManualTrackAndroidTest com.scenevibe.tvcompanionpoc.test/androidx.test.runner.AndroidJUnitRunner | tr -d '\r' | tee tv-ux002-native/manual-track001.log
grep -q '^OK (2 tests)' tv-ux002-native/manual-track001.log
grep -q '^INSTRUMENTATION_CODE: -1$' tv-ux002-native/manual-track001.log
