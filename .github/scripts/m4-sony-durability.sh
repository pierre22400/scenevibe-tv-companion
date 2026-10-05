#!/usr/bin/env bash
set -euo pipefail

# Test-only native Android persistence/process gate. No physical Sony, network or reboot claim.
# Every scenario uses real SharedPreferences XML, then kills the entire target process before reload.
APP_ID="com.scenevibe.tvcompanionpoc"
COMPONENT="${APP_ID}.test/com.scenevibe.tvcompanionpoc.M4SonyDurabilityInstrumentation"
APK_ROOT="$RUNNER_TEMP/scenevibe-m4-durability-apks"
RESULTS="$RUNNER_TEMP/scenevibe-m4-durability-results"
mkdir -p "$RESULTS"

# Run a single instrumentation process and require both its explicit bounded PASS and framework completion.
invoke() {
  local build="$1" scenario="$2" phase="$3" log="$RESULTS/$1-$2-$3.txt"
  adb shell am instrument -w -e build "$build" -e scenario "$scenario" -e phase "$phase" "$COMPONENT" \
    | tr -d '\r' > "$log"
  if ! grep -q '^INSTRUMENTATION_RESULT: result=PASS$' "$log" \
      || ! grep -q '^INSTRUMENTATION_CODE: -1$' "$log"; then
    cat "$log"
    echo "FAIL: native durability $build / $scenario / $phase"
    exit 1
  fi
}

# Seed and reload in genuinely different Android processes; no Java/SharedPreferences/static view survives.
scenario() {
  local build="$1" name="$2" first second
  invoke "$build" "$name" seed
  first="$(sed -n 's/^INSTRUMENTATION_RESULT: pid=//p' "$RESULTS/$build-$name-seed.txt")"
  adb shell am force-stop "$APP_ID"
  if adb shell pidof "$APP_ID" > /dev/null; then
    echo "FAIL: target process survived force-stop"
    exit 1
  fi
  invoke "$build" "$name" reload
  second="$(sed -n 's/^INSTRUMENTATION_RESULT: pid=//p' "$RESULTS/$build-$name-reload.txt")"
  if [[ ! "$first" =~ ^[0-9]+$ || ! "$second" =~ ^[0-9]+$ || "$first" = "$second" ]]; then
    echo "FAIL: process identities are missing or unchanged"
    exit 1
  fi
  echo "PASS: native $build / $name / distinct process / exact disk / zero restore writes"
}

# Install disposable debug/test APKs only; the separate stable workflow produces the later physical candidate.
install_pair() {
  adb install -r "$APK_ROOT/$1-app.apk"
  adb install -r "$APK_ROOT/$1-test.apk"
}

SDK="$(adb shell getprop ro.build.version.sdk | tr -d '\r')"
if [ "$SDK" = "31" ]; then
  install_pair baseline
  for profile in manifested legacy; do
    for ack in confirmed pending; do
      scenario baseline "generic-$profile-$ack"
    done
  done
fi

install_pair fixed
for name in generic-manifested-confirmed generic-manifested-pending generic-legacy-confirmed \
    generic-legacy-pending historical-manifested historical-legacy invalid-generic \
    ack-ahead-without-generic empty maximum failed-commit; do
  scenario fixed "$name"
done

python3 .github/scripts/m4-sony-durability-summary.py "$RESULTS" "$SDK"
echo "PASS: Android native disk/process durability gate complete; Sony hard reboot remains physical"
