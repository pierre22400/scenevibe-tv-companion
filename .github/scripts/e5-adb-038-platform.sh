#!/usr/bin/env bash
# E5-038 software only: refuse every non-emulator serial; no Sony, native session or Cloud.
set -euo pipefail
SCENEVIBE_EMULATOR_SERIAL="${ANDROID_SERIAL:-emulator-5554}"
[[ "$SCENEVIBE_EMULATOR_SERIAL" =~ ^emulator-[0-9]+$ ]] || { echo "REFUSED_NON_EMULATOR"; exit 2; }
mkdir -p e5-adb-038-evidence
# Preserve the actual platform error even when an early assertion or runner launch fails.
trap 'adb -s "$SCENEVIBE_EMULATOR_SERIAL" logcat -d > e5-adb-038-evidence/full-logcat.txt 2>&1 || true' EXIT
adb -s "$SCENEVIBE_EMULATOR_SERIAL" install -r app/build/outputs/apk/debug/app-debug.apk
adb -s "$SCENEVIBE_EMULATOR_SERIAL" install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb -s "$SCENEVIBE_EMULATOR_SERIAL" shell appops set com.scenevibe.tvcompanionpoc SYSTEM_ALERT_WINDOW allow
adb -s "$SCENEVIBE_EMULATOR_SERIAL" shell pm list instrumentation > e5-adb-038-evidence/installed-runners.txt
grep -q E5AdbTriggerInstrumentation e5-adb-038-evidence/installed-runners.txt
grep -q E5AdbSenderInstrumentation e5-adb-038-evidence/installed-runners.txt
for runner in E5AdbTriggerInstrumentation E5AdbSenderInstrumentation; do
  timeout 90s adb -s "$SCENEVIBE_EMULATOR_SERIAL" shell am instrument -w \
    "com.scenevibe.tvcompanionpoc.test/com.scenevibe.tvcompanionpoc.$runner" \
    | tee "e5-adb-038-evidence/$runner.txt"
  grep -q 'INSTRUMENTATION_RESULT: failed=0' "e5-adb-038-evidence/$runner.txt"
done
grep -q 'INSTRUMENTATION_RESULT: passed=15' e5-adb-038-evidence/E5AdbTriggerInstrumentation.txt
grep -q 'INSTRUMENTATION_RESULT: passed=1' e5-adb-038-evidence/E5AdbSenderInstrumentation.txt
# Foreground witness is Android Settings, not a real streaming app or programme.
adb -s "$SCENEVIBE_EMULATOR_SERIAL" shell am start -a android.settings.SETTINGS
sleep 2
adb -s "$SCENEVIBE_EMULATOR_SERIAL" shell dumpsys activity activities > e5-adb-038-evidence/foreground-before.txt
adb -s "$SCENEVIBE_EMULATOR_SERIAL" logcat -c
adb -s "$SCENEVIBE_EMULATOR_SERIAL" shell am broadcast \
  -n com.scenevibe.tvcompanionpoc/.E5AdbTriggerReceiver \
  -a com.scenevibe.tvcompanionpoc.E5_ADB_VOD_INTERLUDE \
  --ez e5VodConfirmed true | tee e5-adb-038-evidence/shell-valid.txt
grep -q 'QUEUED_GUARDS_PENDING' e5-adb-038-evidence/shell-valid.txt
sleep 2
adb -s "$SCENEVIBE_EMULATOR_SERIAL" shell dumpsys activity activities > e5-adb-038-evidence/foreground-after.txt
python3 - <<'CHECK'
from pathlib import Path
import re

"""Require the same observed resumed Activity before and after the real ADB broadcast."""

root = Path('e5-adb-038-evidence')
before = re.findall(r'mResumedActivity: (.+)', (root / 'foreground-before.txt').read_text())
after = re.findall(r'mResumedActivity: (.+)', (root / 'foreground-after.txt').read_text())
assert before and before == after, (before, after)
CHECK
adb -s "$SCENEVIBE_EMULATOR_SERIAL" logcat -d -s SceneVibeE5Adb SceneVibeE5 AndroidRuntime ActivityManager \
  > e5-adb-038-evidence/shell-logcat.txt
grep -q REFUSED_TARGET_OR_VOD e5-adb-038-evidence/shell-logcat.txt
adb -s "$SCENEVIBE_EMULATOR_SERIAL" shell am broadcast \
  -n com.scenevibe.tvcompanionpoc/.E5AdbTriggerReceiver \
  -a com.scenevibe.tvcompanionpoc.E5_ADB_VOD_INTERLUDE \
  --ez e5VodConfirmed false | tee e5-adb-038-evidence/shell-malformed.txt
grep -q REFUSED_MALFORMED e5-adb-038-evidence/shell-malformed.txt
# OFF APK delivery must have no endpoint. Downgrade is on this disposable emulator only.
adb -s "$SCENEVIBE_EMULATOR_SERIAL" install -r -d e5-adb-038-off/app-debug.apk
adb -s "$SCENEVIBE_EMULATOR_SERIAL" shell am broadcast \
  -n com.scenevibe.tvcompanionpoc/.E5AdbTriggerReceiver \
  -a com.scenevibe.tvcompanionpoc.E5_ADB_VOD_INTERLUDE \
  --ez e5VodConfirmed true | tee e5-adb-038-evidence/shell-off.txt
! grep -q QUEUED_GUARDS_PENDING e5-adb-038-evidence/shell-off.txt
echo "E5-038 PLATFORM PASS; ADB DELIVERY REAL / NATIVE GUARDS SIMULATED OR REFUSED / NOT SONY"
