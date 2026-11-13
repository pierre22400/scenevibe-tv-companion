#!/usr/bin/env bash
# Relit le cache et la possession de test après veille, fin de processus et boot.
# La qualification utilise uniquement le transport contrôlé et l'émulateur jetable.
set -euo pipefail
evidence=product-consolidation001-restart
mkdir -p "$evidence"
serial="$(adb get-serialno)"
avd="$(adb emu avd name | tr -d '\r' | head -n 1)"
[[ "$serial" =~ ^emulator-[0-9]+$ && "$avd" =~ ^[a-zA-Z0-9_-]+$ ]]
test "$(adb shell getprop ro.kernel.qemu | tr -d '\r')" = 1
package=com.scenevibe.tvcompanionpoc
adb install -r app/build/outputs/apk/tvux002/app-tvux002.apk
adb install -r app/build/outputs/apk/androidTest/tvux002/app-tvux002-androidTest.apk

# Exige les deux preuves du runner et ne journalise que les résultats de test.
run_case() {
  adb shell am instrument -w -r -e class "$package.$1" "$package.test/androidx.test.runner.AndroidJUnitRunner" \
    | tr -d '\r' | tee "$evidence/$2.log"
  grep -Fx 'OK (1 test)' "$evidence/$2.log"
  grep -Fx 'INSTRUMENTATION_CODE: -1' "$evidence/$2.log"
}

run_case PublicRestartSeedTest seed
adb shell dumpsys power > "$evidence/power-before.txt"
adb shell input keyevent KEYCODE_SLEEP
timeout 30 bash -c 'until adb shell dumpsys power | grep -q "mWakefulness=Asleep"; do sleep 1; done'
adb shell dumpsys power > "$evidence/power-asleep.txt"
adb shell input keyevent KEYCODE_WAKEUP
timeout 30 bash -c 'until adb shell dumpsys power | grep -q "mWakefulness=Awake"; do sleep 1; done'
adb shell dumpsys power > "$evidence/power-awake.txt"
run_case PublicRestartReadTest after-wake
adb exec-out run-as "$package" cat files/public001b-restart-proof.json > "$evidence/after-wake.json"

adb shell am force-stop "$package"
run_case PublicRestartReadTest after-process
adb exec-out run-as "$package" cat files/public001b-restart-proof.json > "$evidence/after-process.json"

adb shell cat /proc/sys/kernel/random/boot_id | tr -d '\r' > "$evidence/boot-before.txt"
adb shell sync
adb emu kill
timeout 45 bash -c 'while adb devices | awk "{print \$1}" | grep -Fxq "$1"; do sleep 1; done' _ "$serial"
nohup "${ANDROID_SDK_ROOT:?}/emulator/emulator" -avd "$avd" -port "${serial#emulator-}" \
  -no-window -gpu swiftshader_indirect -noaudio -no-boot-anim -no-snapshot -memory 2048 -cores 2 \
  > "$evidence/cold-boot.log" 2>&1 < /dev/null &
timeout 150 adb wait-for-device
timeout 180 bash -c 'until [[ "$(adb shell getprop sys.boot_completed | tr -d "\r")" == 1 ]]; do sleep 2; done'
adb shell cat /proc/sys/kernel/random/boot_id | tr -d '\r' > "$evidence/boot-after.txt"
test -s "$evidence/boot-before.txt"
test -s "$evidence/boot-after.txt"
! cmp -s "$evidence/boot-before.txt" "$evidence/boot-after.txt"
run_case PublicRestartReadTest after-cold-boot
adb exec-out run-as "$package" cat files/public001b-restart-proof.json > "$evidence/after-cold-boot.json"
