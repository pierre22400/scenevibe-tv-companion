# SceneVibe TV Companion POC - KIRO AUDIO 002B - Corrections report (C1-C5)

**Status: integrated-software AUDIO triggering demonstrated (SOFTWARE PASS). AUDIO 002B is NOT PHYSICALLY QUALIFIED and the experimental Android build is BLOCKED-ENV here.**
**Nature: targeted-fix report for the four AUDIO 002 review findings (C1-C4) plus the C5 non-regression guarantee. No audio engine was rebuilt. No Sony step was executed.**

> This report documents what was wrong, what changed, and the exact code references for each correction, grounded only in the real diff on branch `work/kiro-audio-002-local-integration-001` and the real (software-only) JVM test results. As in the AUDIO 002 deliverables, every claim is one of three kinds, kept strictly separate:
>
> - **SOFTWARE PASS**: proven by the Android-free JVM tests in this sandbox. Software proof only; never a physical Sony PASS.
> - **HISTORICAL PHYSICAL PASS A1**: a pre-existing fact from the earlier A1 spike on a physical Sony BRAVIA Android TV 12 / native Prime Video. Context, not re-earned here.
> - **NOT PHYSICALLY QUALIFIED (AUDIO 002B)**: everything that requires hardware and was not run here.

## 1. Scope and base

| Item | Value |
| --- | --- |
| Working branch | `work/kiro-audio-002-local-integration-001` |
| Pre-correction HEAD | `b3d3edc docs: add AUDIO 002 deliverable reports (software-only, not physically qualified)` |
| Correction commit (local, pre-push) | `394db81 fix: wire AUDIO path end-to-end (AUDIO 002B C1-C5)` |
| Files changed by the fix | `OverlayService.java`, `audio/DemoAudioTrack.java`, `audio/SceneVibeAudioRenderer.java`, plus two NEW test files (`audio/SchedulerAudioIntegrationTest.java`, `audio/AudioCorrectionsTest.java`) |
| Stable applicationId | `com.scenevibe.tvcompanionpoc` - UNTOUCHED |
| Experimental applicationId | `com.scenevibe.tvcompanionpoc.audioexp` (distinct, disposable debug signing, NOT M5-signed) |

`git diff b3d3edc --stat` lists exactly these five files and nothing else. Critically, it does NOT list `calendar/SceneEvent.java` or `ScheduledTrack.java` (see C5).

## 2. The AUDIO 002 defect in one sentence

The AUDIO path never fired end to end because the demo catalog was keyed to identifiers (`DemoAudioTrack.DEMO_TOKEN` + `demo-audio-1/2/3`) that the LIVE `MediaCalendarScheduler` never emits: on the real path the scheduler emits `onDue(token = "video-activation-N", eventId = <active ScheduledTrack SceneEvent id>)`, so `catalog.get(eventId)` always resolved null and `SceneVibeAudioRenderer` never started.

---

## C1 - Event identifier alignment (ROOT CAUSE)

### What was wrong

`OverlayService.ensureAudioRenderer()` preloaded `DemoAudioTrack.defaultEpisode().events()` (ids `demo-audio-1/2/3`) under `DemoAudioTrack.DEMO_TOKEN = "demo-audio-episode-a"`. But the live `MediaCalendarScheduler` wired in `OverlayService` emits:

- `token = "video-activation-N"`, minted in `LiveVideoRuntimePorts.loadPreparedVideo` as `"video-activation-" + (++nextActivation)`;
- `eventId = ScheduledTrack.Event.id` (= `SceneEvent.eventId`), built in `VideoPreparedState` from `track.comments`.

Neither the token nor the event ids matched the demo catalog, so in `SceneVibeAudioRenderer.onDue(...)` the lookup `catalog.get(eventId)` returned null and no comment ever started. The AUDIO engine was correct; it was simply never addressed with ids it could resolve.

### What changed

An explicit, testable mapping now builds the AUDIO catalog from the SAME event ids the scheduler actually emits, and binds it under the SAME activation token.

1. **Android-free mapping helper** - `audio/DemoAudioTrack.java`:
   - `public static List<AudioEvent> catalogForCalendar(MediaCalendar calendar)` and
   - `public static List<AudioEvent> catalogForEvents(List<SceneEvent> sceneEvents)`.

   These read the active calendar's TEXT `SceneEvent`s and emit one `AudioEvent` per event, keyed to the EXACT same `eventId` and reusing `startMs` verbatim, all referencing the single bundled cue `DemoAudioTrack.CUE_ASSET_REF` at `DemoAudioTrack.DEMO_GAIN = 0.8f`. A TEXT event with no window (`durationMs <= 0`) maps to a bounded `DEMO_WINDOW_MS = 6000 ms` audible window; a larger window is clamped to `MAX_AUDIO_WINDOW_MS = 60000 ms`; a `startMs` outside the `AudioEvent` bound is skipped. The helper only READS the TEXT ids - it never invents or mutates one.

2. **Live binding on the real token** - `OverlayService.java`:
   - The never-matching demo preload was removed from `ensureAudioRenderer()`.
   - A new `private void bindAudioCatalog(String token)` maps the ACTIVE activation's media calendar to AUDIO cues and registers them with `audioRenderer.setCatalog(token, DemoAudioTrack.catalogForCalendar(calendar))`, so the renderer's `catalogToken` equals the scheduler's activation token and its keys equal the exact event ids `onDue` carries.
   - `bindAudioCatalog(token)` is called from the `MediaCalendarScheduler.Sink.onEligibility(token, true)` fan-out, at the moment an activation becomes eligible.
   - Two minimal Android-free accessors on `LiveVideoRuntimePorts` expose the active session for the audio path WITHOUT touching the TEXT draw path: `String activeAudioToken()` (returns `active.token`) and `MediaCalendar activeAudioCalendar()` (returns `active.state.calendar`). Both are read-only and return null when no activation is active; a stale eligibility callback whose token is not the active token is ignored.

### Code references

- `app/src/main/java/com/scenevibe/tvcompanionpoc/audio/DemoAudioTrack.java` - `catalogForCalendar(MediaCalendar)`, `catalogForEvents(List<SceneEvent>)`, constants `CUE_ASSET_REF`, `DEMO_GAIN`, `DEMO_WINDOW_MS`, `MAX_AUDIO_WINDOW_MS`.
- `app/src/main/java/com/scenevibe/tvcompanionpoc/OverlayService.java` - `bindAudioCatalog(String token)`; the `onEligibility(token, eligible)` sink calling `bindAudioCatalog(token)` on `eligible`; `ensureAudioRenderer()` (demo preload removed); `LiveVideoRuntimePorts.activeAudioToken()` / `activeAudioCalendar()`.

### Result

An eligible media event now triggers `port.prepare` followed by `port.start` exactly once, through the REAL scheduler. This is the end-to-end proof in `SchedulerAudioIntegrationTest.normalPlaybackTriggersAudioAssetExactlyOnce` (quoted in §C4 and in the test report). **SOFTWARE PASS.**

---

## C2 - Pre-preparation (prePrepare) wiring decision

### What was wrong

`SceneVibeAudioRenderer.prePrepare(eventId)` existed and was unit-tested, but it was never driven by the live `OverlayService` lifecycle, and there was no explicit statement of whether it should be or how it would be invalidated.

### What changed (decision: justified tests-only latency hook)

`prePrepare` is intentionally NOT auto-wired into the live `OverlayService` lifecycle. The only scheduler edge that could trigger a look-ahead in-app is a per-position "about to be due" signal, which the passive `MediaCalendarScheduler` does NOT emit (it emits `onDue` exactly AT the media instant). Driving a look-ahead from the renderer would require a second clock / position counter, which the single-media-time-authority constraint forbids. Per the mandate's "only if a demonstrated benefit" clause, `prePrepare` therefore remains a spec / latency-optimization hook exercised by tests only. This decision is documented in the `prePrepare` javadoc.

Whichever path a future adapter takes, the hook is correctly INVALIDATED by the existing lifecycle edges:

- **Track change** - `setCatalog(...)` now releases a pre-prepared-not-started asset (`if (activeEventId != null && activePrepared && !activeStarted) teardown();`) so no stale prepared asset survives a catalog swap. An already-playing comment is left to the scheduler's `onEligibility(false)` stop (the episode-change path), not torn down mid-catalog-swap.
- **Eligibility loss** - `onEligibility(token, false)` tears down.
- **AUDIO disable** - `setAudioEnabled(false)` tears down.

No `Handler` / `Timer` / `Thread` / `postDelayed` / position counter was added; the renderer still owns no clock.

### Code references

- `app/src/main/java/com/scenevibe/tvcompanionpoc/audio/SceneVibeAudioRenderer.java` - the C2 invalidation block at the top of `setCatalog(String, Iterable<AudioEvent>)`; the expanded `prePrepare(String)` javadoc recording the tests-only decision and the three invalidation edges.
- Tests: `audio/AudioCorrectionsTest.java` - `prePrepareInvalidatedOnTrackChange`, `prePrepareInvalidatedOnEligibilityLoss`, `prePrepareInvalidatedOnAudioDisable`.

### Result

Pre-preparation invalidation on Track change, eligibility loss, and AUDIO disable is covered by tests; no second clock exists. **SOFTWARE PASS.**

---

## C3 - Session tokens (onDue / onExpire token scoping)

### What was wrong

`SceneVibeAudioRenderer.onDue(String token, String eventId)` and `onExpire(String token, String eventId)` IGNORED the incoming `token` and matched by `eventId` only. The renderer already stored `catalogToken` (set by `setCatalog` and `onEligibility(token, true)`) but never compared the incoming token to it. A due or expire bearing a token from an OLD activation but a live `eventId` could therefore start a comment, or interrupt one, in the NEW session.

### What changed

Both methods now early-return when `catalogToken != null && !catalogToken.equals(token)`:

- `onDue`: a stale-token due must neither start a new comment nor restart / disturb one already playing under the current token. Ignore it.
- `onExpire`: a stale-token expire must never interrupt / expire a comment active under the current token. Ignore it.

Behavior for the matching (live) token is unchanged: de-dup, concurrent-due arbitration, `STOP_ON_PAUSE`, and the existing expire-closes-active-window semantics all stand.

### Code references

- `app/src/main/java/com/scenevibe/tvcompanionpoc/audio/SceneVibeAudioRenderer.java` - the token guard at the top of `onDue(String, String)` and `onExpire(String, String)`.
- Tests: `audio/AudioCorrectionsTest.java` - `onDueIgnoresStaleTokenButHonorsCurrent`, `onExpireIgnoresStaleTokenButHonorsCurrent`; `audio/SchedulerAudioIntegrationTest.java` - `staleTokenDueDoesNotStartInNewSession`, `staleTokenExpireDoesNotInterruptLiveComment`. Each fails if the guard is removed.

### Result

A stale-activation due / expire with a live event id can neither start nor interrupt a comment in the new session, while the live-token due / expire still works. **SOFTWARE PASS.**

---

## C4 - Integrated scheduler to renderer test (seek / stale events)

### What was wrong

The AUDIO 002 renderer matrix exercised the renderer in isolation (calling `onDue` / `onExpire` directly). It did not exercise the REAL `MediaCalendarScheduler` to renderer wiring, so forward seek, backward seek, pause, resume, and episode change were never driven through the scheduler's own seek / expire / re-arm logic.

### What changed

A NEW JVM test `app/src/test/java/com/scenevibe/tvcompanionpoc/audio/SchedulerAudioIntegrationTest.java` constructs the REAL `calendar.MediaCalendarScheduler` with a `Sink` that mirrors the `OverlayService` fan-out: on `onEligibility(token, true)` it binds the C1 catalog (`renderer.setCatalog(token, DemoAudioTrack.catalogForCalendar(activeCalendar))`), then forwards `onEligibility / onPlayback / onDue / onExpire` to a `SceneVibeAudioRenderer` over a `RecordingLocalAudioPort`. Every transition is produced by the scheduler from `scheduler.load(calendar, token)` + `scheduler.onObservation(MediaObservation ...)` alone - the test never calls a renderer callback directly for the end-to-end cases.

Matrix covered (8 `@Test` methods), each written so it fails if the C1 alignment or C3 guard were reverted:

| Case | Test method | Assertion heart |
| --- | --- | --- |
| Normal playback trigger | `normalPlaybackTriggersAudioAssetExactlyOnce` | `prepareCount == 1`, `startCount == 1` at the comment's media instant |
| Forward seek (> 5000 ms) | `forwardSeekPastCommentStartsNoStaleComment` | `startCount == 0`; skipped comment never due |
| Backward seek (< -2000 ms) | `backwardSeekReArmsWithoutDuplicates` | re-arm plays once more; `startCount == 2`, no overlap |
| Pause / resume | `pauseStopsAndResumeDoesNotReplay` | STOP_ON_PAUSE stops; resume adds no start |
| Episode change | `episodeChangeStopsPreviousAndOnlyNewTokenPlays` | old binding dropped; only new token plays |
| No duplicates | `repeatedObservationsDoNotDuplicate` | repeated same-position observations keep `startCount == 1` |
| C3 stale due | `staleTokenDueDoesNotStartInNewSession` | stale-token due ignored; live-token due starts |
| C3 stale expire | `staleTokenExpireDoesNotInterruptLiveComment` | stale-token expire ignored; live-token expire closes |

### End-to-end proof (the heart of C1 / C4)

From `SchedulerAudioIntegrationTest.normalPlaybackTriggersAudioAssetExactlyOnce`, driven ONLY through the real scheduler:

```java
Harness h = new Harness();
h.load(calendarA(), TOKEN_A);
// First observation: becomes eligible (binds catalog via C1), position before any event.
h.observe(true, 0L, true);
assertEquals(0, h.port.startCount);
// Advance to E1's media instant: scheduler emits onDue(TOKEN_A, E1).
h.observe(true, 5_000L, true);

assertEquals(1, h.port.prepareCount);
assertEquals(1, h.port.startCount);
assertTrue(h.renderer.isStarted());
assertEquals(E1, h.renderer.activeEventId());
assertEquals(DemoAudioTrack.CUE_ASSET_REF, h.port.lastAssetRef);
```

The `Harness` sink is the exact OverlayService fan-out shape:

```java
@Override public void onEligibility(String token, boolean eligible) {
    // AUDIO 002B C1: bind the catalog to the REAL token + REAL event ids on the
    // transition to eligible, exactly like OverlayService.bindAudioCatalog(token).
    if (eligible && token != null && token.equals(activeToken) && activeCalendar != null) {
        renderer.setCatalog(token, DemoAudioTrack.catalogForCalendar(activeCalendar));
    }
    renderer.onEligibility(token, eligible);
}
@Override public void onDue(String token, String eventId) { renderer.onDue(token, eventId); }
```

Captured JUnit output for the full audio suite (see the test report for the exact commands):

```
JUnit version 4.13.2
..............................................................
Time: 0.027

OK (62 tests)
```

Before C1, the catalog was keyed to the demo ids `demo-audio-1/2/3`, so `onDue(TOKEN_A, "scene-event-alpha")` resolved null and `startCount` stayed 0; the assertion `assertEquals(1, h.port.startCount)` would fail. That is why this is the root-cause proof. **SOFTWARE PASS.**

---

## C5 - TEXT non-regression

### Guarantees held

- `app/src/main/java/com/scenevibe/tvcompanionpoc/calendar/SceneEvent.java` and `app/src/main/java/com/scenevibe/tvcompanionpoc/ScheduledTrack.java` are byte-for-byte unchanged: `git diff b3d3edc --stat` does NOT list them. No TEXT `eventId` is altered; the C1 mapping only READS TEXT ids.
- The overlay posture is untouched: it stays `FLAG_NOT_FOCUSABLE | FLAG_NOT_TOUCHABLE` with no `requestFocus()`. The `MainActivity` D-pad TEXT / AUDIO / volume controls were not modified.
- No `AudioFocus`, no Prime transport (PLAY / PAUSE / SEEK), and no global / system volume change were introduced. No `requestAudioFocus`, `AUDIOFOCUS`, `setStreamVolume`, `AudioManager` mutation, or `MediaController` transport verb was added anywhere by the fix. SceneVibe volume remains a LOCAL gain only.
- No second clock: no `Handler` / `Timer` / `Thread` / `postDelayed` / position counter was added; `tick()` still only increments a diagnostic sample count.
- The new C1 mapping helper lives in the audio package and is Android-free (it imports only `calendar.*`), so it stays JVM-testable.
- The existing `coreHasNoAudioFocusOrStreamVolumeCapability` reflection guard still passes; `AudioCorrectionsTest.demoCatalogHelperHasNoFocusOrStreamVolumeCapability` extends that guard over the new `DemoAudioTrack` static surface. `TextEventPreservationTest` is green.

### Code references

- `git diff b3d3edc --stat` (confirms the five-file surface, excluding SceneEvent / ScheduledTrack).
- Tests: `audio/TextEventPreservationTest.java` (3 `@Test`), `audio/AudioCorrectionsTest.demoCatalogHelperHasNoFocusOrStreamVolumeCapability`, and the pre-existing `SceneVibeAudioRendererTest.coreHasNoAudioFocusOrStreamVolumeCapability`.

### Result

TEXT behavior, TEXT event ids, the non-focusable / non-touchable overlay posture, and the no-focus / no-transport / no-global-volume prohibitions are all preserved. **SOFTWARE PASS.**

---

## 3. Software verification summary

- Compile: `javac -Xlint:all` under JDK 17.0.2 - clean, no warnings.
- Run: `java org.junit.runner.JUnitCore` over the 7 audio test classes - `OK (62 tests)` = 45 pre-existing + 17 new (`SchedulerAudioIntegrationTest` 8 `@Test`, `AudioCorrectionsTest` 9 `@Test`).
- The exact commands and totals are in `audio-002b-test-report.md`.

## 4. §7 exit verdict

**PARTIAL.**

- **Demonstrated (SOFTWARE PASS):** integrated-software AUDIO triggering through the REAL `MediaCalendarScheduler`, with the C1 event-id / token alignment, the C2 pre-preparation invalidation (tests-only hook, no second clock), the C3 token scoping, the C4 seek / pause / resume / episode-change matrix, and the C5 non-regression - all green under JDK 17.0.2 + JUnit 4.13.2.
- **Blocked (BLOCKED-ENV):** the experimental Android build and its on-device checks. The Android SDK is absent in this sandbox (`ANDROID_HOME` / `ANDROID_SDK_ROOT` unset, no `sdkmanager`), so Android compilation, Android (Robolectric) unit tests, lint, `assembleAudioexp`, the `applicationId = com.scenevibe.tvcompanionpoc.audioexp` check, and the no-M5-signature check could not be run and are NOT declared PASS. No APK was built and no APK SHA-256 exists or is asserted.

**This is NOT "READY FOR SONY QUALIFICATION."** Per the §7 criterion, READY requires BOTH integrated-software triggering (met) AND an experimental Android build PASS (blocked). The realistic ceiling here is PARTIAL.

### Resume procedure

1. On an SDK-equipped machine, re-run the FEAT-002 JVM verification (the exact `javac -Xlint:all` + `JUnitCore` commands in `audio-002b-test-report.md`) and confirm `OK (62 tests)`.
2. Run the PC DENIS Android-SDK steps in `audio-002b-pc-denis-procedure.md`: Android compile, Android unit tests, lint, `assembleAudioexp`, verify `applicationId = com.scenevibe.tvcompanionpoc.audioexp`, verify the APK is NOT M5-signed (disposable debug signing only).
3. Only if both pass may the verdict be revisited. The Sony physical qualification remains a separate, future protocol under a separate owner GO (`audio-002b-sony-handoff-addendum.md`).
