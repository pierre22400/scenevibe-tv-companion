#!/usr/bin/env bash
set -u

APP_ID="com.scenevibe.tvcompanionpoc"
APK="app/build/outputs/apk/debug/app-debug.apk"
FAILED=0

note_human() {
  echo "::notice title=HUMAN/PHYSICAL REQUIRED::$1"
  echo "HUMAN/PHYSICAL REQUIRED: $1"
}

fail() {
  echo "::error::$1"
  echo "FAIL: $1"
  FAILED=1
}

ok() {
  echo "PASS: $1"
}

# GitHub-hosted Android emulators occasionally transition through "offline" even
# after android-emulator-runner has completed its boot wait. Treat that runner
# transport race as infrastructure noise, but fail closed if the device cannot
# become usable again within a bounded retry window.
wait_for_adb() {
  local label="$1"
  local attempt state
  for attempt in $(seq 1 12); do
    state="$(adb get-state 2>/dev/null || true)"
    if [ "$state" = "device" ] && adb shell true >/dev/null 2>&1; then
      echo "ADB ready for ${label} (attempt ${attempt}/12)"
      return 0
    fi
    echo "ADB not ready for ${label}: state=${state:-unavailable} (attempt ${attempt}/12)"
    adb reconnect >/dev/null 2>&1 || true
    sleep 5
  done

  echo "ADB still unavailable; restarting host adb server once."
  adb kill-server >/dev/null 2>&1 || true
  adb start-server >/dev/null 2>&1 || true
  adb wait-for-device >/dev/null 2>&1 || true
  for attempt in $(seq 1 6); do
    state="$(adb get-state 2>/dev/null || true)"
    if [ "$state" = "device" ] && adb shell true >/dev/null 2>&1; then
      echo "ADB recovered for ${label} after server restart (attempt ${attempt}/6)"
      return 0
    fi
    sleep 5
  done

  fail "emulator adb transport did not recover for ${label}"
  return 1
}

echo "===== Android 15 (API 35) platform smoke — scope: platform invariants only ====="
echo "----- device under test -----"
wait_for_adb "initial device probe" || true
adb shell getprop ro.build.version.sdk
adb shell getprop ro.build.version.release
adb shell getprop ro.product.cpu.abi

LEANBACK="$(adb shell pm list features | grep -c 'android.software.leanback' || true)"
if [ "$LEANBACK" = "0" ]; then
  note_human "Running on a STANDARD Android 15 image (no android.software.leanback). Android-TV leanback launcher, D-pad focus and TV overlay rendering are NOT qualified here; qualify on physical Android TV hardware."
else
  echo "Device reports leanback feature (unexpected for the standard image)."
fi

echo "----- 1) APK present -----"
if [ -f "$APK" ]; then
  ok "Cloud-mode debug APK was built at $APK"
else
  fail "APK not found at $APK"
fi

echo "----- 2) adb install -----"
wait_for_adb "APK install" || true
if adb install -r "$APK"; then
  ok "APK installed"
else
  fail "adb install failed"
fi

echo "----- 3) package present (pm list packages) -----"
wait_for_adb "package verification" || true
if adb shell pm list packages | tr -d '\r' | grep -q "package:${APP_ID}"; then
  ok "package ${APP_ID} present after install"
else
  fail "package ${APP_ID} not present after install"
fi

echo "----- 4) clear logcat, then launch MainActivity -----"
wait_for_adb "MainActivity launch" || true
adb logcat -c || true
adb shell am start -n "${APP_ID}/.MainActivity"
sleep 8
wait_for_adb "post-launch verification" || true

echo "----- 5) no immediate crash: scan logcat for AndroidRuntime FATAL EXCEPTION -----"
LOG="$(adb logcat -d || true)"
echo "$LOG" | grep -E 'FATAL EXCEPTION|AndroidRuntime' | grep -i "${APP_ID}" || true
if echo "$LOG" | grep -E 'FATAL EXCEPTION' | grep -qi "${APP_ID}"; then
  fail "AndroidRuntime FATAL EXCEPTION detected for ${APP_ID} after launch"
elif echo "$LOG" | grep -q 'FATAL EXCEPTION'; then
  note_human "A FATAL EXCEPTION appeared in logcat but not clearly attributed to ${APP_ID}; inspect the step log to confirm it is unrelated."
  ok "no FATAL EXCEPTION attributed to ${APP_ID}"
else
  ok "no FATAL EXCEPTION / AndroidRuntime crash after MainActivity launch"
fi

echo "----- 6) MainActivity resumed (dumpsys activity activities) -----"
ACT="$(adb shell dumpsys activity activities || true)"
if echo "$ACT" | grep -q "${APP_ID}/.MainActivity"; then
  if echo "$ACT" | grep -E 'mResumedActivity|ResumedActivity|topResumedActivity' | grep -q "${APP_ID}/.MainActivity"; then
    ok "MainActivity is the resumed/top activity"
  else
    note_human "MainActivity is present in the activity stack but the resumed-activity line could not be confirmed headlessly; confirm foreground resume on physical hardware."
  fi
else
  fail "MainActivity not found in the activity stack after launch"
fi

echo "----- 7) foreground service (OverlayService, specialUse FGS) -----"
wait_for_adb "foreground-service invariant checks" || true
adb shell appops set "${APP_ID}" SYSTEM_ALERT_WINDOW allow || true
echo "appops SYSTEM_ALERT_WINDOW state:"
adb shell appops get "${APP_ID}" SYSTEM_ALERT_WINDOW || true
note_human "OverlayService (specialUse foreground service) start + 'FGS alive' + overlay-window-visible require a real user action (D-pad 'Start SceneVibe') and a visible overlay surface. These are NOT honestly observable in this headless CI emulator and are HUMAN/PHYSICAL REQUIRED on Android TV hardware."

echo "----- 8) boot / autostart path (BootReceiver -> specialUse FGS) -----"
wait_for_adb "force-stop persistence check" || true
note_human "BOOT_COMPLETED autostart (BootReceiver arming the specialUse FGS in boot-prepare mode) requires the autostart opt-in ON, overlay + MediaSession access granted, and a genuine device reboot. This is NOT honestly qualifiable headlessly and is HUMAN/PHYSICAL REQUIRED on Android TV hardware."
adb shell am force-stop "${APP_ID}" || true
if adb shell pm list packages | tr -d '\r' | grep -q "package:${APP_ID}"; then
  ok "package ${APP_ID} still present after force-stop (install persistence lower bound; NOT a boot-path qualification)"
else
  fail "package ${APP_ID} missing after force-stop"
fi

echo "----- summary -----"
echo "Qualified here (Android 15 / API 35 platform invariants): APK build+install, package presence, MainActivity launch, no AndroidRuntime fatal, activity-stack presence."
echo "HUMAN/PHYSICAL REQUIRED (not qualified here): Android TV leanback runtime, overlay FGS alive + visible, real BOOT_COMPLETED autostart, true device reboot."

if [ "$FAILED" -ne 0 ]; then
  echo "::error::Android 15 platform smoke test FAILED (see FAIL lines above)."
  exit 1
fi

echo "Android 15 (API 35) platform smoke test: all performed checks passed; unqualifiable items marked HUMAN/PHYSICAL REQUIRED."
