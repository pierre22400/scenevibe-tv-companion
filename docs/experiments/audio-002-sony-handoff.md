# SceneVibe TV Companion POC - KIRO AUDIO 002 - Sony physical-qualification handoff

**Status: PROTOCOL TO EXECUTE LATER. NO Sony step is executed in AUDIO 002.**
**Nature: operator protocol for a future physical mandate, modeled on `audio-focus-experiment-plan.md` and the operator-protocol style. This is a plan, not a results report.**

> **AUDIO 002 performed no Sony mutation, no install, no signed build, and no merge.** This document hands a concrete, future protocol to the next (physical) mandate, which runs **only under a separate owner GO**. Until that GO, none of the steps below are executed.
>
> Three categories, kept separate as in the other three deliverables:
>
> - **SOFTWARE PASS** - already obtained in this sandbox (JVM); software proof only.
> - **HISTORICAL PHYSICAL PASS A1** - pre-existing voice-coexistence result on the one Sony/Prime configuration; context, not re-earned here.
> - **NOT PHYSICALLY QUALIFIED (AUDIO 002)** - everything in this handoff; it becomes a verdict only after the protocol below is executed on hardware.

## 1. Device, scope, and preconditions

| Item | Value |
| --- | --- |
| Reference TV | Sony BRAVIA Android TV 12 |
| Third-party player | Native Prime Video (`com.amazon.amazonvideo.livingroom`) |
| Stable production package (untouched) | `com.scenevibe.tvcompanionpoc` |
| Stable signed line (untouched) | `versionCode=15`, M5 keystore / signature |
| Experimental package to install | `com.scenevibe.tvcompanionpoc.audioexp` (distinct, disposable debug signing, NOT M5-signed) |
| Reference audio path | Local voice comment WITHOUT any audio-focus request (`SceneVibeAudioRenderer` + `AndroidLocalAudioPort`) |
| Bundled cue (verify before install) | `scenevibe_cue.aac`, 7032 bytes, SHA-256 `96eb915315b01ebb624a5802440056cab30201f5c135aced55ee5276a6a381d7` |
| Authorisation | **Separate owner GO required.** Without it, no step runs. |

**Strict scope.** Any verdict is limited to the single Sony BRAVIA Android TV 12 / native Prime Video configuration. Nothing extends to other apps, models, or ad-interrupted sources.

**Allowed diagnostics only.** Observed states (PLAYING / PAUSED), audibility yes/no, picture progression yes/no, the fixture SHA-256, the experimental APK SHA-256, TV software version, Prime version. **Never** log media titles, session tokens, subtitles, or credentials.

**Prerequisites.**
- Separate owner GO obtained.
- An experimental APK assembled from this branch with `assembleAudioexp` on a machine that has the Android SDK (not built in AUDIO 002 - see the test report). Record its SHA-256 at install time.
- `adb` available to the operator; Prime Video launched and playing; a passive scan performed.
- The stable package, its pairing, and the M5 line remain untouched throughout.

## 2. R0 remote-safety GATE (before any media test)

Mandatory, carried from the spike physical closure and the audio-focus plan (incident 0.2.1). From a nominal Sony:

1. Record a remote baseline (Home, Back, D-pad, OK, volume) - all keys respond.
2. Install the experimental package only (see §3); open its diagnostic overlay with **no** command.
3. Confirm the overlay is **passive**: it sets `FLAG_NOT_FOCUSABLE` + `FLAG_NOT_TOUCHABLE`, contains no clickable button, and never calls `requestFocus()`; confirm **all** remote keys still respond and no focusable element intercepts the remote.
4. Hide the overlay and confirm it disappears and the remote still works **without any reboot**.

**GATE result.** If R0 fails, `force-stop` the experimental package only, log it, and **abort** the media tests. Scenarios A and the matrix start **only** if R0 is PASS. No reboot is to be performed as part of the protocol; R0 exists precisely to prevent the 0.2.1 remote-lockout condition.

## 3. Operator commands - isolated install alongside stable

Install the experimental DISTINCT package beside the stable one. Do **not** uninstall `com.scenevibe.tvcompanionpoc`, do **not** `pm clear`, do **not** reset the Sony/M5, do **not** redo pairing.

```bash
# 0) Verify the bundled cue before trusting the build.
sha256sum app/src/main/res/raw/scenevibe_cue.aac
# expect: 96eb915315b01ebb624a5802440056cab30201f5c135aced55ee5276a6a381d7

# 1) Record the experimental APK identity at install time (NOT built in AUDIO 002).
sha256sum app-audioexp-debug.apk

# 2) Install the EXPERIMENTAL package alongside stable (does not touch the stable package).
adb install -r app-audioexp-debug.apk

# 3) Confirm BOTH packages are present (stable preserved, experimental added).
adb shell pm list packages | grep scenevibe
# expect both:
#   com.scenevibe.tvcompanionpoc            (stable, untouched)
#   com.scenevibe.tvcompanionpoc.audioexp   (experimental)

# 4) Grant the EXPERIMENTAL package its OWN overlay + notification access only.
adb shell appops set com.scenevibe.tvcompanionpoc.audioexp SYSTEM_ALERT_WINDOW allow
#   Notification-listener access is granted via Settings > Apps > Special access >
#   Notification access, for com.scenevibe.tvcompanionpoc.audioexp ONLY.
#   Do NOT modify the stable package's permissions, pairing, or data.

# 5) Confirm Prime is PLAYING by a PASSIVE scan only (never emit transport).
adb shell dumpsys media_session | grep -i state
```

On a debug-signing-key conflict, uninstall **only** `com.scenevibe.tvcompanionpoc.audioexp`, never the stable package.

### 3.1. Optional PC DENIS PowerShell helper

If a PowerShell helper is used on the PC DENIS to drive the ADB steps above, it **must begin with the exact line below** (execution-policy scope limited to the current process, no persistent machine change):

```powershell
Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass
# audio-002 experimental install helper (DISTINCT package, alongside stable).
# Never requests, copies, or embeds any signing secret; never touches the M5 keystore.
$apk = "app-audioexp-debug.apk"
Write-Host ("APK SHA-256: " + (Get-FileHash $apk -Algorithm SHA256).Hash)
adb install -r $apk
adb shell appops set com.scenevibe.tvcompanionpoc.audioexp SYSTEM_ALERT_WINDOW allow
adb shell pm list packages | Select-String scenevibe
```

Never request, copy, or embed any signing secret in this or any helper.

## 4. Scenario A - coexistence without any audio-focus request

Reference path: `VoiceCoexistenceProbe` behaviour - no `AudioFocus`, no transport.

1. Confirm by passive scan (`MediaSessionProbe`, read-only) that Prime is PLAYING with a stable token. Never emit transport.
2. Start a short bounded local voice comment (the bundled cue), with **no** `AudioFocus` request and **no** PLAY/PAUSE/SEEK toward Prime.
3. Observe at the TV for the whole comment.
4. Let it finish naturally; never send PLAY or an automatic resume.
5. Re-scan the original session once after the local comment ends to confirm it is unchanged.

**PASS (coexistence) - all of:** the SceneVibe comment is actually audible; Prime's sound stays audible (even attenuated); the Prime picture keeps progressing; **Prime stays PLAYING with no PAUSE**; the original session is unchanged; SceneVibe emits no automatic resume.

**FAIL (coexistence):** Prime goes to **PAUSE** → FAIL **even if the voice is audible**; Prime sound cut/inaudible or picture frozen; session replaced/altered; residual SceneVibe sound after the end; or any transport command emitted.

This mirrors the **HISTORICAL PHYSICAL PASS A1** expectation but is **NOT PHYSICALLY QUALIFIED (AUDIO 002)** until executed here.

## 5. Synchronization matrix (physical steps)

Driven by the single media clock (passively observed Prime position); scheduler constants on `main`: `FORWARD_SEEK_THRESHOLD_MS=5000`, `BACKWARD_SEEK_THRESHOLD_MS=2000`, `MAX_LATE_MS=2000`.

| # | Situation | Physical step | PASS | FAIL |
| --- | --- | --- | --- | --- |
| 1 | Normal playback | Let the position reach a comment `startMs`. | Comment triggers at the right media instant, exactly once. | Wrong instant, duplicate, or missing when due. |
| 2 | Pause | Pause Prime via the remote (never via SceneVibe). | Comment stops per explicit **STOP_ON_PAUSE**; media window does not drain. | SceneVibe audio continues over a frozen picture; window drains during pause. |
| 3 | Resume | Resume Prime. | Behaviour consistent with media position; no wall-clock catch-up; a consumed comment is not replayed. | Replay of a consumed comment; artificial catch-up; desync. |
| 4 | Forward seek | Jump forward past an upcoming comment (delta > 5000 ms). | The now-stale comment is not started. | A stale comment is played. |
| 5 | Backward seek | Jump back before a comment already seen (delta < -2000 ms). | Controlled re-arm, no duplicates. | Duplicate/overlap, or a missing comment on replay. |
| 6 | Unrecognised media | Play media whose identity does not match the Track. | No audio comment (`onEligibility(false)`). | Any comment on unrecognised media. |
| 7 | Episode change | Switch to the next episode (new media identity). | Previous episode's comments stop; windows cleared; new Track re-armed. | A previous-episode comment persists. |
| 8 | AUDIO OFF | Toggle AUDIO OFF in the navigable UI during a comment. | Immediate SceneVibe stop+release, independent of the scheduler. | Latency, residual sound, or effect on Prime. |
| 9 | Eligibility loss | Make the session unavailable / force a mismatch during a comment. | Safe stop, no residual playback, no action on Prime. | Residual playback or action on Prime. |

STOP_ON_PAUSE is the default; `FREEZE_AND_RESUME` is only to be evaluated if Sony shows comfortable coexistence.

## 6. TEXT non-regression

During and after AUDIO execution, confirm the qualified TEXT path still works and is unchanged: TEXT comments still render on the single media clock, the TEXT ON/OFF toggle is independent of AUDIO, and turning AUDIO ON/OFF or changing SceneVibe volume has no effect on TEXT. (Software side: `SceneEvent`/`ScheduledTrack` are byte-for-byte unchanged and `TextEventPreservationTest` is a SOFTWARE PASS; the physical confirmation is part of this protocol.)

## 7. Prohibited on the audio path (reminder)

No Prime transport (no PAUSE/PLAY/SEEK from SceneVibe); no capture of the third-party player; no DRM bypass; no replacement of the original soundtrack; no ad mid-roll; no system/Prime volume mutation (no `setStreamVolume`, no global AudioManager change - SceneVibe volume is a local gain only); no Cloud contract change; no use of the M5 keystore and no change to the `versionCode=15` signed line; no automatic GitHub merge; no Sony install without the owner GO.

## 8. GO / STOP note

This is a **future** protocol executed only under a **separate owner GO**. **AUDIO 002 executes no Sony step.** The real behaviour on the Sony is the authority: do not assume an audio-focus policy guarantees mixing; if coexistence is impossible or unstable (for example Prime PAUSE), **stop this path and document the block** before any new architecture. The physical verdict (FINAL / PARTIAL / BLOCKED) is produced by that future mandate, not here. Until then AUDIO 002 remains **NOT PHYSICALLY QUALIFIED**, with only a **SOFTWARE PASS** earned and the **HISTORICAL PHYSICAL PASS A1** standing as pre-existing context.
