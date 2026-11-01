#!/usr/bin/env bash
# Only an owned emulator may be installed or driven. MediaProjection still uses system consent.
set -euo pipefail
serial=emulator-5554
test "$(adb -s "$serial" shell getprop ro.kernel.qemu | tr -d '\r')" = 1
probe_root="$(cd "$(dirname "$0")/.." && pwd)"
mkdir -p "$probe_root/proofs"
adb -s "$serial" install -r "$probe_root/app/build/outputs/apk/debug/app-debug.apk"
adb -s "$serial" install -r "$probe_root/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"
adb -s "$serial" shell am instrument -w -e class com.scenevibe.timebasecaptureg1.CaptureInstrumentTest \
  com.scenevibe.timebasecaptureg1.test/androidx.test.runner.AndroidJUnitRunner | tee "$probe_root/proofs/instrumentation.log"
adb -s "$serial" exec-out run-as com.scenevibe.timebasecaptureg1 cat files/g1.jsonl > "$probe_root/proofs/g1-owned-controls.jsonl"
adb -s "$serial" logcat -d -s AndroidRuntime > "$probe_root/proofs/android-errors.log"
grep -q 'OK (6 tests)' "$probe_root/proofs/instrumentation.log"
grep -q 'INSTRUMENTATION_CODE: -1' "$probe_root/proofs/instrumentation.log"
