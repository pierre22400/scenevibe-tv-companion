#!/usr/bin/env bash
# Use a single owned Android TV image and standard forward package updates.
set -euo pipefail
umask 077

package=com.scenevibe.tvcompanionpoc
shortcut="$package/$package.CompanionShortcutService"
media="$package/$package.MediaSessionAccessService"
apk_dir=signed-apps
proof=emulator-proof
mkdir -p "$proof"
[[ "$(adb get-serialno)" =~ ^emulator-[0-9]+$ ]]
[[ "$(adb shell getprop ro.kernel.qemu | tr -d '\r')" == 1 ]]
[[ -z "$(adb shell pm path com.amazon.amazonvideo.livingroom | tr -d '\r')" ]]
(cd "$apk_dir" && sha256sum -c SHA256SUMS)
adb install "$apk_dir/scenevibe-companion-v32-reference.apk" | tee "$proof/install-v32.txt"
adb install "$RUNNER_TEMP/sony001a-tests-m5.apk"
adb install "$RUNNER_TEMP/sony001a-owned-player.apk"
adb shell input keyevent KEYCODE_WAKEUP
adb shell am start -W -n "$package/.MainActivity" > "$proof/initial-launch.txt"
adb shell input keyevent KEYCODE_HOME

# Initial setup is performed exactly once, before v32 is exercised.
adb shell appops set "$package" SYSTEM_ALERT_WINDOW allow
adb shell cmd notification allow_listener "$media"
adb shell settings put secure enabled_accessibility_services "$shortcut"
adb shell settings put secure accessibility_enabled 1
adb shell settings put secure accessibility_shortcut_target_service "$shortcut"
adb shell settings put secure accessibility_button_targets "$shortcut"
adb shell settings put secure accessibility_shortcut_dialog_shown 1

# Read grants, component target and package UID without changing their values.
snapshot() {
    {
        for setting in enabled_notification_listeners enabled_accessibility_services accessibility_enabled \
                accessibility_shortcut_target_service accessibility_button_targets accessibility_shortcut_dialog_shown; do
            printf '%s=' "$setting"
            adb shell settings get secure "$setting" | tr -d '\r'
        done
        adb shell appops get "$package" SYSTEM_ALERT_WINDOW | tr -d '\r' | sed -En 's/^(SYSTEM_ALERT_WINDOW: [^; ]+).*/\1/p'
        adb shell pm list packages -U "$package" | tr -d '\r' | grep -F "package:$package uid:"
    } > "$proof/grants-v$1.txt"
    grep -Fq 'SYSTEM_ALERT_WINDOW: allow' "$proof/grants-v$1.txt"
    grep -Fq "accessibility_shortcut_target_service=$shortcut" "$proof/grants-v$1.txt"
    grep -Fq "$media" "$proof/grants-v$1.txt"
    diff "$proof/grants-v32.txt" "$proof/grants-v$1.txt"
}

# Require successful assertions and Android instrumentation exit, not adb transport alone.
run_tests() {
    local class="$1" logfile="$2" version="$3" seed="$4"
    adb shell dumpsys accessibility | grep -F "$package" > "$proof/accessibility-before-$logfile.txt" || true
    adb shell am instrument -w -r -e class "$package.$class" -e expectedVersion "$version" \
        -e seed "$seed" -e retainShortcut true -e viewerTrackId track-cc472a2e896b3efecd5d607e517d8d70ab86345a \
        "$package.test/androidx.test.runner.AndroidJUnitRunner" | tr -d '\r' | tee "$proof/$logfile.txt"
    local instrument_exit=${PIPESTATUS[0]}
    adb shell dumpsys accessibility | grep -F "$package" > "$proof/accessibility-after-$logfile.txt" || true
    [[ "$instrument_exit" == 0 ]] &&
        grep -q '^OK (1 test)$' "$proof/$logfile.txt" &&
        grep -q '^INSTRUMENTATION_CODE: -1$' "$proof/$logfile.txt"
}

failures=0
snapshot 32
run_tests SonySignedContinuityTest private-v32 32 true || failures=$((failures + 1))
adb install -r "$apk_dir/scenevibe-companion-public-viewer-v34-m5.apk" | tee "$proof/update-v34.txt"
snapshot 34
run_tests SonySignedContinuityTest private-v34 34 false || failures=$((failures + 1))
run_tests SonySignedPublicTest public-v34 34 false || failures=$((failures + 1))
adb install -r "$apk_dir/scenevibe-companion-recovery-v35-m5.apk" | tee "$proof/recovery-v35.txt"
snapshot 35
run_tests SonySignedContinuityTest private-v35 35 false || failures=$((failures + 1))
if (( failures != 0 )); then
    printf 'SIGNED_UPGRADE_TEST_FAILURES=%s\n' "$failures" > "$proof/verdict.txt"
    exit 1
fi
printf '%s\n' SIGNED_V32_TO_V34_TO_V35_PRIVATE_RECOVERY_PASS > "$proof/verdict.txt"
