#!/usr/bin/env bash
# Qualification hors réseau de la continuité M5 sur le même userdata d'émulateur.
set -euo pipefail
package=com.scenevibe.tvcompanionpoc
proof=m5-proof
v34=signed-input/scenevibe-companion-public-viewer-v34-m5.apk
v38=signed-input/scenevibe-companion-product-consolidation-v38-m5.apk
v40="$proof/scenevibe-companion-studio-private-q2-v40-m5.apk"
[[ "$(adb get-serialno)" =~ ^emulator-[0-9]+$ ]]
[[ "$(adb shell getprop ro.kernel.qemu | tr -d '\r')" == 1 ]]
adb shell svc wifi disable
adb shell cmd connectivity airplane-mode enable
for host in scenevibe-autonomous001d-qual-preview.vercel.app scenevibe-public-viewer001-qual.vercel.app; do
    if timeout 8 adb shell ping -c 1 -W 1 "$host" 2>&1 | grep -q '^PING '; then exit 1; fi
done
adb install "$v34" | tee "$proof/install-v34.txt"
adb install "$RUNNER_TEMP/studio-q2-v40-tests-m5.apk"
adb install "$RUNNER_TEMP/studio-q2-owned-player.apk"
adb shell input keyevent KEYCODE_WAKEUP
adb shell am start -W -n "$package/.MainActivity" > "$proof/initial-launch-v34.txt"
adb shell input keyevent KEYCODE_HOME
shortcut="$package/$package.CompanionShortcutService"
adb shell appops set "$package" SYSTEM_ALERT_WINDOW allow
adb shell cmd notification allow_listener "$package/$package.MediaSessionAccessService"
adb shell settings put secure enabled_accessibility_services "$shortcut"
adb shell settings put secure accessibility_enabled 1
adb shell settings put secure accessibility_shortcut_target_service "$shortcut"
adb shell settings put secure accessibility_button_targets "$shortcut"
adb shell settings put secure accessibility_shortcut_dialog_shown 1

# Le test natif v34 historique crée seulement les fixtures privées de cet émulateur.
qualify() {
    local version="$1" seed="$2" output="$3"
    {
        adb shell settings get secure enabled_notification_listeners
        adb shell settings get secure accessibility_shortcut_target_service
        adb shell appops get "$package" SYSTEM_ALERT_WINDOW | sed -En 's/^(SYSTEM_ALERT_WINDOW: [^; ]+).*/\1/p'
        adb shell pm list packages -U "$package"
    } | tr -d '\r' > "$proof/grants-$output.txt"
    diff "$proof/grants-v34.txt" "$proof/grants-$output.txt"
    for attempt in $(seq 1 35); do
        local pid
        pid="$(adb shell pidof "$package" | tr -d '\r' | awk '{print $1}' || true)"
        if [[ -n "$pid" ]] && adb logcat -d --pid="$pid" -s SceneVibeMute002:I |
            grep 'SHORTCUT_CONNECTED' > "$proof/shortcut-$output.txt"; then break; fi
        sleep 1
    done
    test -s "$proof/shortcut-$output.txt"
    timeout --foreground 180s adb shell am instrument -w -r -e class "$package.HistoricalM5ContinuityTest" \
        -e expectedVersion "$version" -e seed "$seed" -e retainShortcut true \
        "$package.test/androidx.test.runner.AndroidJUnitRunner" | tr -d '\r' | tee "$proof/$output.txt"
    grep -Fxq 'OK (1 test)' "$proof/$output.txt"
    grep -Fxq 'INSTRUMENTATION_CODE: -1' "$proof/$output.txt"
}

# Les générations et les pannes ci-dessous appartiennent au seul AVD jetable.
qualify_cache() {
    local class="$1" testcase="$2"
    timeout --foreground 210s adb shell am instrument -w -r -e class "$package.$class#$testcase" \
        -e disposableQ2 true "$package.test/androidx.test.runner.AndroidJUnitRunner" \
        | tr -d '\r' | tee "$proof/cache-$testcase.txt"
    grep -Fxq 'OK (1 test)' "$proof/cache-$testcase.txt"
}

# Redémarre l'AVD en recréant processus et services sur le même espace utilisateur.
restart_android() {
    adb reboot
    timeout 150 adb wait-for-device
    for attempt in $(seq 1 90); do
        [[ "$(adb shell getprop sys.boot_completed | tr -d '\r')" == 1 ]] && break
        sleep 2
    done
    [[ "$(adb shell getprop sys.boot_completed | tr -d '\r')" == 1 ]]
    adb shell input keyevent KEYCODE_WAKEUP
}

qualify 34 true v34
adb install -r "$v38" | tee "$proof/upgrade-v38.txt"
qualify 38 false v38
if [[ "${SCENEVIBE_SIGN_V41:-}" == true ]]; then
    v40=signed-input/scenevibe-companion-studio-private-q2-v40-m5.apk
fi
adb install -r "$v40" | tee "$proof/upgrade-v40.txt"
qualify 40 false v40
candidate_version=40
upgrade_verdict=SIGNED_V38_TO_V40_AND_HISTORICAL_CACHES_PASS
if [[ "${SCENEVIBE_SIGN_V41:-}" == true ]]; then
    v41="$proof/scenevibe-companion-media-universal-v41-m5.apk"
    if [[ "${SCENEVIBE_SIGN_BROWSER_V42:-}" == true ]]; then
        v41=signed-input/scenevibe-companion-media-universal-v41-m5.apk
    fi
    adb install -r "$v41" | tee "$proof/upgrade-v41.txt"
    qualify 41 false v41
    candidate_version=41
    upgrade_verdict=SIGNED_V40_TO_V41_AND_HISTORICAL_CACHES_PASS
fi
if [[ "${SCENEVIBE_SIGN_BROWSER_V42:-}" == true ]]; then
    adb install -r "$proof/scenevibe-companion-qr-browser-v42-m5.apk" | tee "$proof/upgrade-v42.txt"
    qualify 42 false v42
    candidate_version=42
    upgrade_verdict=SIGNED_V41_TO_V42_AND_HISTORICAL_CACHES_PASS
fi
restart_android
qualify "$candidate_version" false "v$candidate_version-after-reboot"
printf '%s\n' "$upgrade_verdict" > "$proof/upgrade-verdict.txt"

if [[ "${SCENEVIBE_SIGN_V41:-}" == true ]]; then
    # La fixture publique possède le même plafond global et remplace l'ancien cache du banc.
    qualify_cache PublicRestartSeedTest persistPublicCacheAndAssociationBeforeProcessRestart
    restart_android
    qualify_cache PublicRestartReadTest restoreWithoutCodeAfterProcessAndAndroidEnvironmentRestart
fi
qualify_cache MultimediaCacheLifecycleAndroidTest oneCachePerDomainAcrossGenerations
qualify_cache MultimediaCacheLifecycleAndroidTest publicReplacementsAndDiskFailure
qualify_cache MultimediaCacheLifecycleAndroidTest privateRevisionsAndDiskFailure
if [[ "${SCENEVIBE_SIGN_V41:-}" == true ]]; then
    qualify_cache MediaUniversalAndroidTest privateQ2AndSessionContinuity
    qualify_cache MediaUniversalAndroidTest publicSelectionAndDeferredReplacement
fi
if [[ "${SCENEVIBE_SIGN_BROWSER_V42:-}" == true ]]; then
    qualify_cache MediaUniversalAndroidTest secondBrowserKeepsExistingDeviceCredential
fi
adb logcat -d -s SceneVibeCacheQ2:I SceneVibeCacheQ3:I > "$proof/cache-domains.txt"
