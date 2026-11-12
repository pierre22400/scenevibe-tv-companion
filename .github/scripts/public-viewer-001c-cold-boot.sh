#!/usr/bin/env bash
# Redémarre à froid la même image Android TV avec son userdata intact.
set -euo pipefail
test "$(adb shell getprop ro.kernel.qemu | tr -d '\r')" = 1
serial="$(adb get-serialno)"
avd="$(adb emu avd name | tr -d '\r' | head -n 1)"
[[ "$serial" =~ ^emulator-[0-9]+$ && "$avd" =~ ^[a-zA-Z0-9_-]+$ ]]
adb shell sync
adb emu kill
for iteration in $(seq 1 30); do
    if ! adb devices | awk '{print $1}' | grep -Fxq "$serial"; then break; fi
    sleep 1
done
! adb devices | awk '{print $1}' | grep -Fxq "$serial"
nohup "${ANDROID_SDK_ROOT:?}/emulator/emulator" -avd "$avd" -port "${serial#emulator-}" \
    -no-window -gpu swiftshader_indirect -noaudio -no-boot-anim -no-snapshot -memory 2048 -cores 2 \
    > "$RUNNER_TEMP/001c-cold-boot.log" 2>&1 < /dev/null &
timeout 150 adb wait-for-device
for iteration in $(seq 1 60); do
    if [[ "$(adb shell getprop sys.boot_completed | tr -d '\r')" == 1 ]]; then break; fi
    sleep 2
done
test "$(adb shell getprop sys.boot_completed | tr -d '\r')" = 1
for iteration in $(seq 1 45); do
    if adb shell dumpsys activity services com.scenevibe.tvcompanionpoc/.PublicCompanionService | grep -q 'isForeground=true'; then break; fi
    sleep 2
done
adb shell dumpsys activity services com.scenevibe.tvcompanionpoc/.PublicCompanionService | grep 'isForeground=true' > viewer001c-evidence/boot-service.txt
adb logcat -d -v brief -s SceneVibePublic:I | grep PUBLIC_RECEIVER_BOOT_START_REQUESTED > viewer001c-evidence/boot-receiver.txt
