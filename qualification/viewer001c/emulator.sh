#!/usr/bin/env bash
# Validate standard forward updates in one emulator without any write to the reference origin.
set -euo pipefail
package=com.scenevibe.tvcompanionpoc
shortcut="$package/$package.CompanionShortcutService"
media="$package/$package.MediaSessionAccessService"
proof=m5-proof
[[ "$(adb get-serialno)" =~ ^emulator-[0-9]+$ ]]
[[ "$(adb shell getprop ro.kernel.qemu | tr -d '\r')" == 1 ]]
adb shell svc wifi disable
adb shell svc data disable
adb shell cmd connectivity airplane-mode enable

# The dedicated emulator has a reserved TEST-NET DNS. Abort if either reference host resolves.
check_offline() {
    local host
    for host in scenevibe-autonomous001d-qual-preview.vercel.app scenevibe-public-viewer001-qual.vercel.app; do
        if timeout 8 adb shell ping -c 1 -W 1 "$host" 2>&1 | grep -q '^PING '; then
            return 1
        fi
    done
}

# Compare stored Android permissions and verify the runtime with the same app process data.
qualify() {
    local version="$1" seed="$2" label="$3"
    check_offline
    {
        adb shell settings get secure enabled_notification_listeners
        adb shell settings get secure accessibility_shortcut_target_service
        adb shell appops get "$package" SYSTEM_ALERT_WINDOW | sed -En 's/^(SYSTEM_ALERT_WINDOW: [^; ]+).*/\1/p'
        adb shell pm list packages -U "$package"
    } | tr -d '\r' > "$proof/grants-$label.txt"
    diff "$proof/grants-v34.txt" "$proof/grants-$label.txt"
    timeout --foreground 180s adb shell am instrument -w -r -e class "$package.HistoricalM5ContinuityTest" \
        -e expectedVersion "$version" -e seed "$seed" -e retainShortcut true \
        "$package.test/androidx.test.runner.AndroidJUnitRunner" | tr -d '\r' | tee "$proof/$label.txt"
    grep -Fxq 'OK (1 test)' "$proof/$label.txt"
    grep -Fxq 'INSTRUMENTATION_CODE: -1' "$proof/$label.txt"
}

check_offline
adb install signed-input/scenevibe-companion-public-viewer-v34-m5.apk | tee "$proof/install-v34.txt"
adb install "$RUNNER_TEMP/viewer001c-tests-m5.apk"
adb install "$RUNNER_TEMP/viewer001c-owned-player.apk"
adb shell input keyevent KEYCODE_WAKEUP
adb shell appops set "$package" SYSTEM_ALERT_WINDOW allow
adb shell cmd notification allow_listener "$media"
adb shell settings put secure enabled_accessibility_services "$shortcut"
adb shell settings put secure accessibility_enabled 1
adb shell settings put secure accessibility_shortcut_target_service "$shortcut"
adb shell settings put secure accessibility_button_targets "$shortcut"
adb shell settings put secure accessibility_shortcut_dialog_shown 1
qualify 34 true v34
adb install -r "$proof/scenevibe-companion-viewer001c-v36-m5.apk" | tee "$proof/update-v36.txt"
qualify 36 false v36
adb reboot
adb wait-for-device
for attempt in $(seq 1 90); do
    [[ "$(adb shell getprop sys.boot_completed | tr -d '\r')" == 1 ]] && break
    sleep 2
done
[[ "$(adb shell getprop sys.boot_completed | tr -d '\r')" == 1 ]]
adb shell input keyevent KEYCODE_WAKEUP
qualify 36 false v36-after-boot
adb install -r "$proof/scenevibe-companion-recovery-v37-m5.apk" | tee "$proof/update-v37.txt"
qualify 37 false v37
printf '%s\n' SIGNED_V34_TO_V36_TO_V37_PASS > "$proof/verdict.txt"
