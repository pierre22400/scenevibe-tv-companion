# SceneVibe TV Companion POC - KIRO AUDIO 002 - Test report

**Status: SOFTWARE PASS (JVM) only. AUDIO 002 is NOT PHYSICALLY QUALIFIED.**
**Nature: enumeration of the tests actually run, the environment used, and the limits. No Sony step was executed.**

> A JVM (or Robolectric, or emulator) pass is **SOFTWARE PROOF ONLY**. It is **never** a physical Sony PASS. This report keeps three categories strictly separate:
>
> - **SOFTWARE PASS**: what the Android-free JVM tests proved in this sandbox.
> - **HISTORICAL PHYSICAL PASS A1**: a pre-existing physical fact from the A1 spike, listed here as context, not re-earned in AUDIO 002.
> - **NOT PHYSICALLY QUALIFIED (AUDIO 002)**: everything that requires hardware and was not run here.

## 1. Base and environment

| Item | Value |
| --- | --- |
| Base HEAD | `origin/main = fdc5f6d5f17a19ef775422bf0b6e63454cc0fbbb` |
| Branch | `work/kiro-audio-002-local-integration-001` |
| Commits under test | `0ae8bc3` (CORE), `ffe610e` (Android integration) |
| JDK used for the software run | OpenJDK **17.0.2** (via `mise where java@17.0.2`) |
| Test framework | JUnit **4.13.2** + hamcrest-core **1.3** |
| Gradle on PATH | 8.14.5 (requires a writable `-g` / `user.home` to initialise native services) |
| Android SDK | **MISSING**: `build-tools;34.0.0` and `platforms;android-35` are not installed and cannot be downloaded under the sandbox network mode |

### 1.1. Which verifications were software-run vs deferred

- **Software-run (this sandbox):** the Android-free CORE (`AudioEvent`, `LocalAudioPort`, `SceneVibeAudioRenderer`, `DemoAudioTrack`) plus `calendar/SceneEvent` were compiled with `javac -Xlint:all` (lint-clean) and the five JVM test classes were executed with `java org.junit.runner.JUnitCore`.
- **Deferred to physical qualification (BLOCKED-ENV here):** the full Gradle unit-test task `:app:testDebugUnitTest` (Robolectric), APK assembly (`assembleAudioexp`), any instrumented test, and all on-device behaviour. `./gradlew :app:testDebugUnitTest` reaches task-dependency resolution (so `app/build.gradle`, including the new `audioexp` build type, parses and configures without DSL error) and then fails with *"Failed to install the following Android SDK packages as some licences have not been accepted: build-tools;34.0.0, platforms;android-35"*. This is the documented **SOFTWARE-ENV LIMIT**: the single missing component is the Android SDK platform/build-tools, not Gradle, not the JDK.

## 2. Tests run and results - SOFTWARE PASS

Command (fallback path, since the Gradle Android path is BLOCKED-ENV):

```
javac -d /tmp/out -cp junit-4.13.2.jar:hamcrest-core-1.3.jar <CORE + test sources>
java  -cp /tmp/out:junit-4.13.2.jar:hamcrest-core-1.3.jar org.junit.runner.JUnitCore \
      com.scenevibe.tvcompanionpoc.audio.AudioEventTest \
      com.scenevibe.tvcompanionpoc.audio.SceneVibeAudioRendererTest \
      com.scenevibe.tvcompanionpoc.audio.TextEventPreservationTest \
      com.scenevibe.tvcompanionpoc.audio.DemoAudioTrackTest \
      com.scenevibe.tvcompanionpoc.audio.AudioControlWiringTest
```

**Result: `OK (45 tests)`, exit 0.** All five Android-free test classes passed. This is a **SOFTWARE PASS** and nothing more.

| Test class | Focus | Rows / checks |
| --- | --- | --- |
| `AudioEventTest` | Additive AUDIO value: exactly the mandated fields, bounded validation (identifier/asset/temporal/gain bounds, null type/policy, factory defaults). | boundary matrix |
| `SceneVibeAudioRendererTest` | The full synchronization matrix of the renderer CORE (see §3). | ~25 tests |
| `TextEventPreservationTest` | `SceneEvent` still exactly `{eventId, startMs, durationMs}` with identical validation and error message; `AudioEvent` is a distinct additive type. | TEXT non-regression (software) |
| `DemoAudioTrackTest` | Demo fixture bounds/immutability + end-to-end drive of the renderer (prepare-before-start, episode-change eligibility loss stops+releases). | fixture + e2e |
| `AudioControlWiringTest` | Control semantics `OverlayService` relies on: AUDIO OFF immediate stop+release, AUDIO OFF suppresses due, AUDIO ON after OFF plays a new due (no retroactive replay), gain forwarded to local port only, safe reconcile order. | UI control logic |

### 2.1. Static guards (software, from the diff)

- `grep -rn 'import android' app/src/main/java/com/scenevibe/tvcompanionpoc/audio/{SceneVibeAudioRenderer,LocalAudioPort,AudioEvent,DemoAudioTrack}.java` → no match (CORE stays Android-free).
- `grep -rn 'AUDIOFOCUS|requestAudioFocus|setStreamVolume|postDelayed|new Handler|new Timer|new Thread' app/src/main/java/com/scenevibe/tvcompanionpoc/audio/` → no match (no focus, no system volume, no second clock in the audio package).
- `grep -rn 'setStreamVolume|AUDIOFOCUS|requestAudioFocus' .../audio/AndroidLocalAudioPort.java` → no match.
- `git diff --stat fdc5f6d -- .../calendar/SceneEvent.java .../ScheduledTrack.java` → empty (TEXT event classes byte-for-byte unchanged).
- `grep -rn 'applicationId' app/build.gradle` → stable `com.scenevibe.tvcompanionpoc` and `versionCode 12` unchanged; experimental id via `applicationIdSuffix '.audioexp'` only.

## 3. Synchronization matrix coverage - SOFTWARE only

Each row of the AUDIO 002 mandate matrix is covered by a dedicated JVM test driven only by the scheduler callbacks (`onDue`/`onExpire`/`onPlayback`/`onEligibility`), so the single media-time authority is respected. The constants cited are the verified `main` scheduler constants `FORWARD_SEEK_THRESHOLD_MS=5000`, `BACKWARD_SEEK_THRESHOLD_MS=2000`, `MAX_LATE_MS=2000`; the renderer does not re-implement seek/expire - the scheduler consumes/re-arms.

| # | Matrix situation | JVM test(s) | SOFTWARE result |
| --- | --- | --- | --- |
| 1 | Normal playback (due → one trigger) | `dueDuringPlaybackPreparesThenStartsLocalResource`, `prepareOrderingAlwaysPrecedesStart`, `sceneVibeGainIsAppliedToTheLocalPortOnly` | PASS |
| 2 | Pause (STOP_ON_PAUSE) | `pauseStopsInProgressCommentPerStopOnPausePolicy` | PASS |
| 3 | Resume (no replay of a consumed phrase) | `resumeDoesNotReplayConsumedPhrase` | PASS |
| 4 | Forward seek (no stale comment) | `forwardSeekStartsNoStaleComment` | PASS |
| 5 | Backward seek (controlled re-arm, no duplicates) | `backwardSeekReArmsWithoutDuplicates` | PASS |
| 6 | Unrecognised media / eligibility loss | `eligibilityLossStopsAndReleases` | PASS |
| 7 | Episode change (previous audio stops) | `episodeChangeStopsPreviousAudioImmediately`, `DemoAudioTrackTest` e2e | PASS |
| 8 | AUDIO OFF during playback (immediate stop) | `audioOffDuringPlaybackStopsImmediately`, `AudioControlWiringTest` | PASS |
| 9 | No-focus guarantee | `coreHasNoAudioFocusOrStreamVolumeCapability` | PASS |
| - | No duplicates on repeated due / concurrent due | `repeatedDueDoesNotRestart`, `concurrentSecondDueIsIgnoredWhileOneActive` | PASS |
| - | Pre-preparation (`prepare != start`) + timing hooks | `prePrepareReadiesWithoutSound`, `timingHooksExposePrepAndStartSamples` | PASS |
| - | Decode error fail-safe (truthful terminal state, never coexistence) | `decodeErrorFailsSafeWithTruthfulTerminalState` | PASS |
| - | Gain bounds / null port / idempotent teardown / unknown event / null catalog entry | `setGainIsBoundedAndForwardedToPort`, `constructorRejectsNullPort`, `redundantTeardownIsSafe`, `dueForUnknownEventIsIgnored`, `catalogSkipsNullEntries`, `dueWhilePausedStartsNothing` | PASS |

All of the above are **SOFTWARE PASS**. They prove the CORE decision logic; they prove **nothing** about on-device audibility, mixing, or sync precision.

## 4. Limits - what a software pass does NOT prove

A green JVM run proves the renderer's reactive logic. It does **not** prove, and this report does **not** claim:

- that a SceneVibe comment is actually audible on the Sony;
- that Prime's own sound stays audible and that Prime stays PLAYING (no PAUSE) while SceneVibe plays;
- any **physical** synchronization precision (the JVM tests count sample indices, not wall-clock milliseconds on hardware - see `audio-002-synchronization-analysis.md`);
- that `MediaPlayer` prepares/plays the cue on the device, or that the overlay stays passive on hardware.

All of these are **NOT PHYSICALLY QUALIFIED (AUDIO 002)** and are deferred to the physical protocol in `audio-002-sony-handoff.md`.

## 5. APK status

**No APK was produced in-sandbox.** The Android SDK (`build-tools;34.0.0` + `platforms;android-35`) is not installed and cannot be downloaded under the sandbox network mode, so neither the stable nor the experimental `audioexp` variant could be assembled here. **There is therefore no AUDIO 002 APK SHA-256 to report, and none is fabricated.** The experimental `audioexp` build type (distinct `applicationId com.scenevibe.tvcompanionpoc.audioexp`, disposable debug signing, NOT M5-signed) is left ready for the operator/orchestrator to assemble with `assembleAudioexp` on a machine that has the Android SDK.

The **only** SHA-256 asserted by AUDIO 002 is the bundled cue:
`app/src/main/res/raw/scenevibe_cue.aac`, 7032 bytes, **SHA-256 `96eb915315b01ebb624a5802440056cab30201f5c135aced55ee5276a6a381d7`**.

## 6. Historical physical fact (A1) - context only, not re-earned here

- **HISTORICAL PHYSICAL PASS A1**: on the specific Sony BRAVIA Android TV 12 with native Prime Video, the A1 spike observed that a local voice comment played **without any audio-focus request** was audible while Prime's sound stayed audible, with no PAUSE. The A1 experimental APK recorded SHA-256 `a2578a12d21bcec963f1120bae78ce7bd2859f6385dcf7f6cc2f939c8170d41b`. The AAC/ADTS container was PASS and M4A/MP4 was FAIL on that OEM extractor.

This A1 result is a **pre-existing fact**, strictly limited to that one Sony / Prime configuration. It was **not re-earned** in AUDIO 002, and it does not qualify AUDIO 002.

## 7. Verdict

- **SOFTWARE PASS**: `OK (45 tests)` on JDK 17.0.2 + JUnit 4.13.2, lint-clean. Software proof only.
- **HISTORICAL PHYSICAL PASS A1**: pre-existing, out of scope to re-earn.
- **AUDIO 002 is NOT PHYSICALLY QUALIFIED.** No APK built here, no Sony step executed, no on-device coexistence or sync precision measured.
