# SceneVibe TV Companion POC - KIRO AUDIO 002B - Test and Android-build report

**Status: SOFTWARE PASS (JVM) `OK (62 tests)`. The experimental Android build is BLOCKED-ENV (no SDK). AUDIO 002B is NOT PHYSICALLY QUALIFIED.**
**Nature: enumeration of the tests actually run, the environment used, and the Android qualification steps that are blocked here with a reproducible PC DENIS procedure. No Sony step was executed; no APK was built; no APK SHA-256 is asserted.**

> A JVM (or Robolectric, or emulator) pass is **SOFTWARE PROOF ONLY**. It is **never** a physical Sony PASS. This report keeps three categories strictly separate:
>
> - **SOFTWARE PASS**: what the Android-free JVM tests proved in this sandbox.
> - **HISTORICAL PHYSICAL PASS A1**: a pre-existing physical fact from the A1 spike, listed as context, not re-earned in AUDIO 002B.
> - **NOT PHYSICALLY QUALIFIED (AUDIO 002B)**: everything that requires hardware or the Android SDK and was not run here.

## 1. Base and environment

| Item | Value |
| --- | --- |
| Pre-correction HEAD | `b3d3edc docs: add AUDIO 002 deliverable reports (software-only, not physically qualified)` |
| Branch | `work/kiro-audio-002-local-integration-001` |
| Correction commit under test (local, pre-push) | `394db81 fix: wire AUDIO path end-to-end (AUDIO 002B C1-C5)` |
| JDK used for the software run | OpenJDK **17.0.2** (pinned explicitly; the shimmed `java` on PATH reports 25) |
| Test framework | JUnit **4.13.2** + hamcrest-core **1.3** |
| Android SDK | **MISSING**: `ANDROID_HOME` / `ANDROID_SDK_ROOT` unset, no `sdkmanager`; `build-tools;34.0.0` and `platforms;android-35` cannot be downloaded under the sandbox network mode |

### 1.1. Which verifications were software-run vs blocked

- **Software-run (this sandbox):** the Android-free CORE (`audio/AudioEvent`, `audio/LocalAudioPort`, `audio/SceneVibeAudioRenderer`, `audio/DemoAudioTrack`) plus `calendar/{SceneEvent, MediaCalendar, MediaObservation, MediaCalendarScheduler}` were compiled with `javac -Xlint:all` (clean) and the seven audio JVM test classes were executed with `java org.junit.runner.JUnitCore`.
- **BLOCKED-ENV (deferred to an SDK-equipped machine):** Android compilation, Android (Robolectric) unit tests, lint, `assembleAudioexp`, the `applicationId` check, and the no-M5-signature check. `OverlayService.java`, `AndroidLocalAudioPort.java`, and `MainActivity.java` are Android-coupled and cannot be `javac`-compiled standalone in-sandbox; the C1 / C2 wiring in `OverlayService` is minimal, well-commented, and validated INDIRECTLY by the Android-free integration test (see §3). No Gradle Android task was run and none is declared PASS.

## 2. Tests run and results - SOFTWARE PASS

Reproducible single-invocation commands (the sandbox `/tmp` is per-command ephemeral, so compile and run happen in one shell):

```sh
JAVA17=~/.local/share/mise/installs/java/17.0.2
GLIB=/inner_container/opt/toolchains/.local/share/mise/installs/gradle/8.14.5/gradle-8.14.5/lib

$JAVA17/bin/javac -Xlint:all -d /tmp/audio-build \
  -cp "$GLIB/junit-4.13.2.jar:$GLIB/hamcrest-core-1.3.jar" \
  app/src/main/java/com/scenevibe/tvcompanionpoc/calendar/SceneEvent.java \
  app/src/main/java/com/scenevibe/tvcompanionpoc/calendar/MediaCalendar.java \
  app/src/main/java/com/scenevibe/tvcompanionpoc/calendar/MediaObservation.java \
  app/src/main/java/com/scenevibe/tvcompanionpoc/calendar/MediaCalendarScheduler.java \
  app/src/main/java/com/scenevibe/tvcompanionpoc/audio/AudioEvent.java \
  app/src/main/java/com/scenevibe/tvcompanionpoc/audio/LocalAudioPort.java \
  app/src/main/java/com/scenevibe/tvcompanionpoc/audio/SceneVibeAudioRenderer.java \
  app/src/main/java/com/scenevibe/tvcompanionpoc/audio/DemoAudioTrack.java \
  app/src/test/java/com/scenevibe/tvcompanionpoc/audio/*.java

$JAVA17/bin/java -cp "/tmp/audio-build:$GLIB/junit-4.13.2.jar:$GLIB/hamcrest-core-1.3.jar" \
  org.junit.runner.JUnitCore \
  com.scenevibe.tvcompanionpoc.audio.AudioEventTest \
  com.scenevibe.tvcompanionpoc.audio.SceneVibeAudioRendererTest \
  com.scenevibe.tvcompanionpoc.audio.TextEventPreservationTest \
  com.scenevibe.tvcompanionpoc.audio.DemoAudioTrackTest \
  com.scenevibe.tvcompanionpoc.audio.AudioControlWiringTest \
  com.scenevibe.tvcompanionpoc.audio.AudioCorrectionsTest \
  com.scenevibe.tvcompanionpoc.audio.SchedulerAudioIntegrationTest
```

The `javac -Xlint:all` compile is clean (exit 0, no warnings). The JUnit run output:

```
JUnit version 4.13.2
..............................................................
Time: 0.027

OK (62 tests)
```

**Result: `OK (62 tests)`, exit 0.** This is a **SOFTWARE PASS** and nothing more.

### 2.1. The 62 total: 45 pre-existing + 17 new

| Test class | `@Test` methods | Status |
| --- | --- | --- |
| `AudioEventTest` | 8 | pre-existing |
| `SceneVibeAudioRendererTest` | 24 | pre-existing |
| `TextEventPreservationTest` | 3 | pre-existing |
| `DemoAudioTrackTest` | 5 | pre-existing |
| `AudioControlWiringTest` | 5 | pre-existing |
| `AudioCorrectionsTest` | 9 | **NEW (AUDIO 002B)** |
| `SchedulerAudioIntegrationTest` | 8 | **NEW (AUDIO 002B)** |
| **Total** | **62** | **45 + 17** |

(`RecordingLocalAudioPort.java` is a JVM fake port, not a test class.)

### 2.2. New-test coverage map (AUDIO 002B)

| Correction | New test(s) | What it proves |
| --- | --- | --- |
| C1 | `AudioCorrectionsTest.mappingKeysAudioCuesToRealEventIds`, `mappedCatalogResolvesRealIdDue`, `mappingHandlesNullAndEmptyDefensively` | catalog keyed to the EXACT real event ids; a real-id `onDue` starts; defensive on null/empty |
| C1 / C4 (end-to-end) | `SchedulerAudioIntegrationTest.normalPlaybackTriggersAudioAssetExactlyOnce` | an eligible media event drives `port.prepare` + `port.start` once through the REAL scheduler |
| C2 | `AudioCorrectionsTest.prePrepareInvalidatedOnTrackChange / OnEligibilityLoss / OnAudioDisable` | pre-prepared-not-started asset released on each invalidation edge |
| C3 | `AudioCorrectionsTest.onDueIgnoresStaleTokenButHonorsCurrent`, `onExpireIgnoresStaleTokenButHonorsCurrent`; `SchedulerAudioIntegrationTest.staleTokenDueDoesNotStartInNewSession`, `staleTokenExpireDoesNotInterruptLiveComment` | a stale-token due/expire is ignored; the live-token one works |
| C4 | `SchedulerAudioIntegrationTest.forwardSeekPastCommentStartsNoStaleComment`, `backwardSeekReArmsWithoutDuplicates`, `pauseStopsAndResumeDoesNotReplay`, `episodeChangeStopsPreviousAndOnlyNewTokenPlays`, `repeatedObservationsDoNotDuplicate` | the full seek / pause / resume / episode / no-dup matrix through the REAL scheduler |
| C5 | `AudioCorrectionsTest.demoCatalogHelperHasNoFocusOrStreamVolumeCapability` + the pre-existing `TextEventPreservationTest` and `coreHasNoAudioFocusOrStreamVolumeCapability` | no new focus / stream-volume capability; TEXT event shape preserved |

### 2.3. End-to-end trigger proof (the heart of C1 / C4)

Captured from `SchedulerAudioIntegrationTest.normalPlaybackTriggersAudioAssetExactlyOnce`, driven ONLY by `scheduler.load(...)` + `scheduler.onObservation(...)` through a `Sink` that mirrors the `OverlayService` fan-out:

```java
h.load(calendarA(), TOKEN_A);
h.observe(true, 0L, true);                 // becomes eligible -> C1 binds the catalog
assertEquals(0, h.port.startCount);
h.observe(true, 5_000L, true);             // scheduler emits onDue(TOKEN_A, E1)
assertEquals(1, h.port.prepareCount);
assertEquals(1, h.port.startCount);
assertTrue(h.renderer.isStarted());
assertEquals(E1, h.renderer.activeEventId());
assertEquals(DemoAudioTrack.CUE_ASSET_REF, h.port.lastAssetRef);
```

Before the C1 fix the catalog was keyed to `demo-audio-1/2/3`, so `onDue(TOKEN_A, "scene-event-alpha")` resolved null and `startCount` stayed 0 - the assertion would fail. This is the root-cause proof that an eligible media event now actually triggers an AUDIO asset end to end. **SOFTWARE PASS.**

## 3. Android qualification steps - BLOCKED-ENV

The following AUDIO 002B steps require the Android SDK, which is absent in this sandbox. They are **BLOCKED-ENV**, were NOT run, and are NOT declared PASS:

| Step | Command (for an SDK-equipped machine) | Status here |
| --- | --- | --- |
| Android compilation | `./gradlew :app:compileAudioexpDebugJavaWithJavac` | **BLOCKED-ENV** |
| Android unit tests (Robolectric) | `./gradlew :app:testAudioexpDebugUnitTest` | **BLOCKED-ENV** |
| Lint | `./gradlew :app:lintAudioexpDebug` | **BLOCKED-ENV** |
| Assemble experimental variant | `./gradlew :app:assembleAudioexpDebug` | **BLOCKED-ENV** |
| applicationId verification (`com.scenevibe.tvcompanionpoc.audioexp`) | `aapt dump badging <apk>` / `apkanalyzer manifest application-id <apk>` | **BLOCKED-ENV** |
| No-M5-signature verification | `apksigner verify --print-certs <apk>` (expect a disposable debug cert, NOT M5) | **BLOCKED-ENV** |

The reproducible PC DENIS procedure for these steps is in **`audio-002b-pc-denis-procedure.md`**.

## 4. APK and SHA-256 status

**No APK was produced in-sandbox.** The Android SDK is not installed and cannot be downloaded under the sandbox network mode, so neither the stable nor the experimental `audioexp` variant could be assembled here. **There is therefore no AUDIO 002B APK SHA-256 to report, and none is fabricated.**

The **only** binary SHA-256 asserted by AUDIO 002B is the bundled cue, verified this session:

- `app/src/main/res/raw/scenevibe_cue.aac`
- **7032 bytes**
- **SHA-256 `96eb915315b01ebb624a5802440056cab30201f5c135aced55ee5276a6a381d7`**

The experimental `audioexp` build type (distinct `applicationId com.scenevibe.tvcompanionpoc.audioexp`, disposable debug signing, NOT M5-signed) is left ready for assembly with `assembleAudioexp` on a machine that has the Android SDK.

## 5. Limits - what a software pass does NOT prove

A green JVM run proves the renderer and the integrated scheduler-to-renderer decision logic. It does **not** prove, and this report does **not** claim:

- that a SceneVibe comment is actually audible on the Sony;
- that Prime's own sound stays audible and that Prime stays PLAYING (no PAUSE) while SceneVibe plays;
- any **physical** synchronization precision (the JVM tests count observation steps, not wall-clock milliseconds on hardware - see `audio-002-synchronization-analysis.md`);
- that `MediaPlayer` prepares / plays the cue on the device, or that the overlay stays passive on hardware;
- that the experimental APK assembles, carries `applicationId com.scenevibe.tvcompanionpoc.audioexp`, and is NOT M5-signed (this is BLOCKED-ENV, pending the PC DENIS run).

All of these are **NOT PHYSICALLY QUALIFIED (AUDIO 002B)** and are deferred to the PC DENIS procedure and the Sony protocol in `audio-002b-sony-handoff-addendum.md`.

## 6. Historical physical fact (A1) - context only, not re-earned here

- **HISTORICAL PHYSICAL PASS A1**: on the specific Sony BRAVIA Android TV 12 with native Prime Video, the A1 spike observed that a local voice comment played **without any audio-focus request** was audible while Prime's sound stayed audible, with no PAUSE. The AAC/ADTS container was PASS and M4A/MP4 was FAIL on that OEM extractor.

This A1 result is a **pre-existing fact**, strictly limited to that one Sony / Prime configuration. It was **not re-earned** in AUDIO 002B and does not qualify AUDIO 002B.

## 7. Verdict

- **SOFTWARE PASS**: `OK (62 tests)` (45 pre-existing + 17 new) on JDK 17.0.2 + JUnit 4.13.2, clean `-Xlint:all`. Integrated-software AUDIO triggering through the REAL `MediaCalendarScheduler` is demonstrated. Software proof only.
- **HISTORICAL PHYSICAL PASS A1**: pre-existing, out of scope to re-earn.
- **Android experimental build**: **BLOCKED-ENV** (no SDK). No APK built, no APK SHA-256 asserted.
- **Overall exit verdict: PARTIAL.** Integrated-software AUDIO triggering demonstrated; the experimental Android build is blocked on the missing SDK. This is **NOT "READY FOR SONY QUALIFICATION."**

### Resume procedure

1. On an SDK-equipped machine, re-run the §2 JVM commands and confirm `OK (62 tests)`.
2. Run the PC DENIS Android-SDK steps (`audio-002b-pc-denis-procedure.md`): Android compile, Android unit tests, lint, `assembleAudioexp`, verify `applicationId = com.scenevibe.tvcompanionpoc.audioexp`, verify the APK is NOT M5-signed.
3. Only if both pass may the verdict be revisited. Physical Sony qualification remains a separate, future protocol under a separate owner GO (`audio-002b-sony-handoff-addendum.md`).

### GitHub HEAD / PR state (deliverable #6)

The local pre-push HEAD of this branch is `394db81`. The coder does NOT push or merge: the orchestrator pushes the branch and reports the final GitHub HEAD and the state of PR #29 after the push. PR #29 (head `work/kiro-audio-002-local-integration-001`, base `main`) remains Draft and MUST NOT be merged.
