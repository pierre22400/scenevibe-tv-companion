#!/usr/bin/env bash
# Only a disposable emulator; keep permissions untouched after the actual signed update.
set -euo pipefail
mkdir -p mute002-upgrade
scenevibe_upgrade_serial="$(adb get-serialno)"
[[ "$scenevibe_upgrade_serial" == emulator-* ]]
test "$(adb shell getprop ro.kernel.qemu | tr -d '\r')" = 1
adb install baseline/tv-ux002-signed/scenevibe-real-track001-v31-stable.apk
adb install signed/tv-ux002-signed/companion-mute002-tests-m5.apk
# Establish v31's pre-existing state once, before the update under test.
adb shell appops set com.scenevibe.tvcompanionpoc SYSTEM_ALERT_WINDOW allow
adb shell cmd notification allow_listener com.scenevibe.tvcompanionpoc/com.scenevibe.tvcompanionpoc.MediaSessionAccessService
adb shell am instrument -w -r -e class com.scenevibe.tvcompanionpoc.CompanionUpgradeSeedTest com.scenevibe.tvcompanionpoc.test/androidx.test.runner.AndroidJUnitRunner | tr -d '\r' | tee mute002-upgrade/v31-seed.log
grep -q '^OK (1 test)$' mute002-upgrade/v31-seed.log
adb shell appops get com.scenevibe.tvcompanionpoc SYSTEM_ALERT_WINDOW > mute002-upgrade/overlay-before.txt
adb shell settings get secure enabled_notification_listeners > mute002-upgrade/listeners-before.txt
adb install -r signed/tv-ux002-signed/scenevibe-companion-mute-integration002-v32-signed.apk
# No overlay/MEDIA grant, reset or association is performed after the update.
adb shell appops get com.scenevibe.tvcompanionpoc SYSTEM_ALERT_WINDOW > mute002-upgrade/overlay-after.txt
adb shell settings get secure enabled_notification_listeners > mute002-upgrade/listeners-after.txt
diff mute002-upgrade/listeners-before.txt mute002-upgrade/listeners-after.txt
adb shell am instrument -w -r -e class com.scenevibe.tvcompanionpoc.CompanionUpgradeReadTest com.scenevibe.tvcompanionpoc.test/androidx.test.runner.AndroidJUnitRunner | tr -d '\r' | tee mute002-upgrade/v32-retained.log
grep -q '^OK (1 test)$' mute002-upgrade/v32-retained.log
grep -q '^INSTRUMENTATION_CODE: -1$' mute002-upgrade/v32-retained.log

# The owner explicitly accepts this initial accessibility setup; it runs only on this emulator.
adb shell am instrument -w -r -e class com.scenevibe.tvcompanionpoc.CompanionShortcutConnectionAndroidTest com.scenevibe.tvcompanionpoc.test/androidx.test.runner.AndroidJUnitRunner | tr -d '\r' | tee mute002-upgrade/signed-shortcut.log
grep -q '^OK (1 test)$' mute002-upgrade/signed-shortcut.log
grep -q '^INSTRUMENTATION_CODE: -1$' mute002-upgrade/signed-shortcut.log
