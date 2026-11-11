#!/usr/bin/env bash
# Seul l'émulateur de qualification reçoit l'APK et les manipulations de réglages.
set -euo pipefail
umask 077
mkdir -p public-viewer-native
test "$(adb shell getprop ro.kernel.qemu | tr -d '\r')" = 1
test -n "${SCENEVIBE_VIEWER_TRACK_ID:-}"
adb install -r app/build/outputs/apk/tvux002/app-tvux002.apk
adb install -r app/build/outputs/apk/androidTest/tvux002/app-tvux002-androidTest.apk
adb shell am instrument -w -r -e viewerTrackId "$SCENEVIBE_VIEWER_TRACK_ID" \
    -e class com.scenevibe.tvcompanionpoc.PublicHostedAndroidTest \
    com.scenevibe.tvcompanionpoc.test/androidx.test.runner.AndroidJUnitRunner | tr -d '\r' | tee public-viewer-native/instrumentation.log
grep -q '^OK (1 test)$' public-viewer-native/instrumentation.log
grep -q '^INSTRUMENTATION_CODE: -1$' public-viewer-native/instrumentation.log
adb exec-out run-as com.scenevibe.tvcompanionpoc cat files/public-viewer-native.json > public-viewer-native/cache-and-playback.json
proof_private="$(mktemp "$RUNNER_TEMP/public-viewer.XXXXXXXX")"
trap 'rm -f -- "$proof_private"' EXIT
adb exec-out run-as com.scenevibe.tvcompanionpoc cat files/public-viewer-boot-private.json > "$proof_private"

# Le redémarrage du noyau invité peut arrêter QEMU sur les images TV CI.
# Redémarre à froid la même image userdata, sans snapshot ni wipe.
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
"${ANDROID_SDK_ROOT:?}/emulator/emulator" -avd "$avd" -port "${serial#emulator-}" \
    -no-window -gpu swiftshader_indirect -noaudio -no-boot-anim -no-snapshot -memory 2048 -cores 2 \
    > "$RUNNER_TEMP/public-viewer-cold-boot.log" 2>&1 &
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
adb shell dumpsys activity services com.scenevibe.tvcompanionpoc/.PublicCompanionService | grep 'isForeground=true' > public-viewer-native/boot-service.txt
adb logcat -d -v brief -s SceneVibePublic:I | grep PUBLIC_RECEIVER_BOOT_START_REQUESTED > public-viewer-native/boot-receiver.txt

SCENEVIBE_VIEWER_BOOT_INPUT="$proof_private" node --input-type=module <<'JS'
import { readFile, writeFile } from 'node:fs/promises'
import { randomUUID } from 'node:crypto'
import assert from 'node:assert/strict'

const origin = 'https://scenevibe-public-viewer001-qual.vercel.app'
const { cookie, trackId, deviceId } = JSON.parse(await readFile(process.env.SCENEVIBE_VIEWER_BOOT_INPUT, 'utf8'))
assert.match(cookie, /^sv_autonomous_tv_[a-f0-9-]{36}=[A-Za-z0-9_-]{43}$/)
/** Reprend le navigateur associé pendant que l'écran du Companion reste fermé. */
async function command(action, body) {
  const response = await fetch(origin + '/api/autonomous/viewer-' + action, {
    method: 'POST', headers: { origin, cookie, 'content-type': 'application/json' },
    body: JSON.stringify(body), signal: AbortSignal.timeout(45000),
  })
  assert.equal(response.status, 200, 'POST_BOOT_REQUEST_REFUSED')
  return response.json()
}
const issued = await command('send', { trackId, deviceId, requestId: randomUUID() })
for (let i = 0; i < 60; i++) {
  const current = (await command('status', { trackId, deviceId })).command
  if (current?.commandId === issued.commandId && current.state === 'ready') {
    assert.equal(current.text, true)
    assert.equal(current.audio, false)
    await writeFile('public-viewer-native/post-boot.json', JSON.stringify({
      scope: 'OWN_ANDROID_TV_EMULATOR_REBOOT', activityLaunchedAfterBoot: false,
      associationReused: true, receivedAndArmed: true, text: true, audio: false,
    }, null, 2) + '\n')
    process.exit(0)
  }
  await new Promise(resolve => setTimeout(resolve, 3000))
}
throw new Error('POST_BOOT_CACHE_RECEIPT_TIMEOUT')
JS
