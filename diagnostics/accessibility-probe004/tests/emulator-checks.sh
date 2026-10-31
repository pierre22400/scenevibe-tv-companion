#!/usr/bin/env bash
# CI-only owned emulator script. The qemu/serial guard deliberately rejects every physical TV.
set -euo pipefail
serial=emulator-5554
test "$(adb -s "$serial" shell getprop ro.kernel.qemu | tr -d '\r')" = 1
probe_root="$(cd "$(dirname "$0")/.." && pwd)"
proof="$probe_root/proofs"
mkdir -p "$proof"
trap 'adb -s "$serial" logcat -d -s AndroidRuntime AccessibilityManagerService > "$proof/emulator-service-errors.log" 2>/dev/null || true' EXIT
adb -s "$serial" install -r "$probe_root/app/build/outputs/apk/debug/app-debug.apk"
adb -s "$serial" install -r "$probe_root/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"
adb -s "$serial" shell settings put secure enabled_accessibility_services com.scenevibe.accessibilityprobe004/.ProbeService
adb -s "$serial" shell settings put secure accessibility_enabled 1
failure=0
for test_class in AutonomousProbeTest AndroidProbeTest RestartSeedTest; do
  adb -s "$serial" shell am instrument -w -e disableAnalytics true -e class "com.scenevibe.accessibilityprobe004.$test_class" \
    com.scenevibe.accessibilityprobe004.test/androidx.test.runner.AndroidJUnitRunner | tee "$proof/$test_class.log"
  if [ "$test_class" = AndroidProbeTest ]; then count=8; elif [ "$test_class" = AutonomousProbeTest ]; then count=7; else count=1; fi
  if ! test "$(grep -c "OK ($count test" "$proof/$test_class.log")" = 1; then failure=1; fi
done
for evidence in positive-event-only positive-periodic positive-owner-buttons; do
  adb -s "$serial" exec-out run-as com.scenevibe.accessibilityprobe004 cat "files/$evidence.jsonl" > "$proof/$evidence.jsonl" || failure=1
done
adb -s "$serial" shell am force-stop com.scenevibe.accessibilityprobe004
adb -s "$serial" shell am start -n com.scenevibe.accessibilityprobe004/.ProbeActivity
adb -s "$serial" shell am instrument -w -e disableAnalytics true -e class com.scenevibe.accessibilityprobe004.RestartTest \
  com.scenevibe.accessibilityprobe004.test/androidx.test.runner.AndroidJUnitRunner | tee "$proof/RestartTest.log"
test "$(grep -c 'OK (1 test' "$proof/RestartTest.log")" = 1

test "$failure" = 0
