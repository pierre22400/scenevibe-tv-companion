# Media Interlude Capability Spike — Corrective Pass

> **Post-qualification fixture update — 6 October 2026.** The isolated ducking cue was replaced at source commit `75dcefbfafe7b23c7cd16d48ae85ba676c8ff2d3` by a locally generated French spoken fixture saying **“Bienvenue sur SceneVibe Audio”**. The file remains `mediaexperiment/src/main/res/raw/scenevibe_cue.m4a`, AAC-LC mono, 22.05 kHz, ~1.81 s. The interlude video fixture is unchanged. This fixture-only change does not alter pause/session/guard logic. The APK SHA-256 recorded later in this report belongs to the pre-replacement APK and MUST NOT be used for the physical test; rebuild `:mediaexperiment:assembleDebug` from this branch before installation.


## A. Status and scope

This remains an **ISOLATED EXPERIMENTAL CAPABILITY SPIKE**, **NOT A NUMBERED
POST-M6 MILESTONE** and **NOT PRODUCTION CODE**. Under the canonical SceneVibe
roadmap, its purpose is to feed the provider/device capability matrix for the
future **Generic Multimedia Track Model** and to constrain what **SceneVibe Studio**
may safely expose to creators for AUDIO/DUCK and VIDEO/PAUSE. It does not create a
special Track type and does not authorize player-control production code.

The four pause/resume blockers and the local-cue AudioAttributes issue are
corrected. Automated decision, wiring, compilation, packaging and lint gates
passed as described below. The frozen Python boundary suite retains its
**14 expected experimental failures**, with no new failing test.

**PHYSICAL SONY / PRIME: NOT TESTED.** No device or emulator was used and no APK
was installed on the Sony. Audio-focus grants and JVM command counters are not
physical evidence of ducking, Prime pause/play or fullscreen rendering.

- Repository: `pierre22400/scenevibe-tv-companion`.
- Branch: `experiment/scenevibe-media-interlude-poc-001`.
- Starting HEAD: `0d0f15dbd26732532f7f62e3897a88c72c61132c`.
- Experimental base: `c9b0efd4acfaaae9ed7da13dcec505b2f653c548`.
- Corrective code / APK source checkpoint: `badc96e4d9bf840572cbb58dca4cc7d3d7634fb7`.
- This report is a documentation-only commit after that checkpoint; the final
  branch HEAD is supplied in the delivery. APK source bytes are unchanged by it.
- No merge PR was opened and nothing was merged. Only this experimental branch
  is pushed after qualification. `main`, PR #15 and PR #16 are not modified.

Roadmap reference: the canonical post-M6 plan now uses unnumbered workstreams
(controlled assets, Generic Multimedia Track Model, Public Track Platform,
SceneVibe Studio, Creator/Identity/Publication) until M6 reconciliation and
physical capability evidence justify final milestone numbering.

## B. Isolation and retained architecture

The installable module remains `:mediaexperiment`, with the distinct applicationId
`com.scenevibe.tvcompanionpoc.mediaexperiment`. It has no `:app` dependency and no
shared pairing, Installation ID, Cloud device ID, preferences, cache or database.
It retains MediaSession-only transport, local `TYPE_APPLICATION_OVERLAY` video,
a `mediaPlayback` foreground service and an Android-free Java decision core.

The corrective diff changes **only `mediaexperiment/` and this existing report**.
`settings.gradle` has not changed during the corrective pass: its existing
`include(":mediaexperiment")` is the experimental delta inherited from the spike.
No production `app/`, Cloud, durable installation, ACK, scheduler, FinalTrack,
M6 WALL code, `.github/`, boundary test or frozen baseline is modified.

Measured check:

```bash
git diff c9b0efd4acfaaae9ed7da13dcec505b2f653c548 -- app/
```

Result: **empty**. The corrective diff against the starting HEAD is also empty
for `app/`, `tests/`, `.github/`, root build configuration and `settings.gradle`.

Static source/manifest/build inspection found no operational use of an
AccessibilityService, simulated remote/input, `adb input`, coordinate injection,
UI scraping, `setStreamVolume`, Cloud client, FinalTrack ingestion,
PackageInstaller, BootReceiver or boot/autostart registration. The experimental
manifest has no INTERNET or RECEIVE_BOOT_COMPLETED permission. References to
forbidden mechanisms in explanatory comments are not implementations.

## C. Corrected implementation

### Independent operator flows

`InterludeService` delegates its separate actions to the Android-free
`InterludeRuntime`, which is exercised by the runtime/catalog tests. No full-test
entry point calls the pause-only action.

| Action | Sequence and effect |
| --- | --- |
| SCAN MEDIA SESSION | Query and record coarse session/action facts only. No focus, cue, transport or video. |
| TEST AUDIO DUCK | Request transient-may-duck focus; if granted, play the local cue only. Stop cue and abandon focus after a bounded two seconds. No PAUSE or video. |
| TEST PAUSE | Require a live PLAYING target advertising PAUSE/PLAY_PAUSE, send PAUSE, await a subsequent PAUSED observation within four seconds. Report confirmed/timeout and end. No focus request, cue, overlay, local video or automatic PLAY. |
| TEST FULL INTERLUDE | Require live PLAYING, request focus for the bundled video's audio, recheck PLAYING, send PAUSE, confirm same-session PAUSED, attach/play fullscreen local video, finish normally, remove local resources, freshly validate the original live session, guard one PLAY, await PLAYING within four seconds. No duck-test cue. |
| EMERGENCY RESTORE / STOP | Stop local media, remove overlay and abandon focus. Idempotent; always NO PLAY. The operator resumes native media manually. |

The full flow retains a pre-pause focus gate because `interlude.mp4` contains an
AAC audio stream alongside H.264 video. A denied focus request causes no native
PAUSE and no local video. The player state is freshly checked **after** focus,
so a native player that independently pauses in response to focus cannot have
that pause claimed. The isolated PAUSE test does not take this focus path.

The service polls once per second on the main thread. A new operator action
cancels earlier sampler callbacks and tears down the previous attempt without
PLAY. The duck deadline belongs to its own machine, so an earlier cue cannot
later abandon focus belonging to a new interlude.

### Explicit pause ownership

`PauseOwnership` records the initial playback state, PAUSE sent, subsequent
PAUSED confirmed, and normal completion. `ownsPause()` requires:

```text
observed PLAYING immediately before dispatch
-> this POC dispatched PAUSE
-> a subsequent same-session/media observation confirmed PAUSED
```

`SessionCatalog.pause()` also performs a fresh active-session/token/PLAYING/action
check immediately at transport dispatch. Sent is never treated as confirmed.
Already PAUSED, BUFFERING, STOPPED, NONE, ERROR, CONNECTING, skipping/seeking or
unknown initial state cannot acquire ownership or launch the interlude.

### Live session-token revalidation

The Android scanner wraps controllers from
`MediaSessionManager.getActiveSessions(ownNotificationListenerComponent)` in a
thin `SessionController` adapter. Each `PlaybackSnapshot` carries the actual
`MediaController.getSessionToken()` as an **opaque Object**. The captured
`SessionTarget` retains that token separately from package and media metadata.
Tokens are compared by equality; their contents are never logged or persisted.

`SessionCatalog` is the Android-free selection/revalidation seam used by the
**real scanner**, the runtime observations and the transport port. It queries
the source afresh rather than holding and sampling a stale controller.

Before guarded PLAY it requires the original token to be present exactly once,
the package to match, sufficient media identity to remain consistent, and no
competing relevant session. The platform priority owner at index zero and any
other playing/preparing/seeking session are treated conservatively: a changed
priority owner or competing active playback denies resume, including a paused
replacement owner. Multiple PLAYING sessions at selection, missing tokens,
duplicate token entries, failed queries or lost access also fail closed.

A different controller **adapter instance** with an equal original token is
allowed; the same package/media with a **different token** is denied. A lower
priority inactive session alone does not replace the original relevant owner.
Relevance is inferred from observable MediaSession priority/state, not from
foreground-app inspection. An app switch producing no observable session/state
change cannot be proved by this seam; this POC adds no UI scraping to infer it.

During the video, each poll can invalidate the session and immediately tear down.
Normal completion first removes local video/overlay/audio and abandons focus,
then performs a new query. `SafeResumeGuard` requires all ownership facts, normal
completion, successful original-token/package/media/relevance revalidation and
a latest live **STATE_PAUSED**. `SessionCatalog.play()` rechecks again at actual
dispatch to catch disappearance/change between guard evaluation and transport.
The fresh recheck cannot make external app changes atomic with Android transport;
uncertainty detected before dispatch always denies PLAY.

No cached snapshot alone authorizes PLAY. No emergency/error branch authorizes
resume; the old `emergencyCleanup` semantic branch was removed. Failure of local
resource teardown also denies normal resume. Late callbacks after STOP are
ignored, and player completion/error/prepared callbacks are tied to the owned
MediaPlayer lifetime. Synchronous local-video errors cannot overwrite STOPPED
with VIDEO_PLAYING.

### Audio duck implementation

`AndroidLocalAudioPort` now uses `new MediaPlayer()`, configures
`USAGE_ASSISTANT` / `CONTENT_TYPE_SPEECH`, sets the bundled raw-resource data
source, and only then calls `prepare()` followed by `start()`. It no longer uses
an already-prepared `MediaPlayer.create()` followed by late attributes.
Partially prepared players are retained for reliable cleanup on failure.

`AndroidAudioFocusPort` still requests `AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK` with
`USAGE_ASSISTANT` / `CONTENT_TYPE_SPEECH`. It does not manipulate Prime or stream
volume. A granted request does not prove that Prime actually ducked.

### Bounded diagnostics

The operator UI includes initial playback state, pause ownership acquired,
original session identity present, revalidation attempted/succeeded, relevant
active package changed, latest state before resume and an enum-only denial
reason, alongside the earlier sent/confirmed/timeout flags. No title, subtitle,
media content, token contents or credentials are logged. Media metadata stays
internal to the transient comparison. `localVideoStarted` records the requested
local playback path; it is not proof of physically visible rendering.

## D. Automated qualification

Toolchain: **Gradle 8.9**, **Temurin JDK 17.0.20.1+1**, Android SDK platform 35,
Build Tools 34.0.0 and 35.0.0 installed. Local dependency transport settings are
outside the repository. No build-file changes or lint suppressions were used to
force the gate green.

The full local gate was:

```bash
gradle --no-daemon --console=plain --stacktrace \
  :app:assembleDebug :app:lintDebug :app:testDebugUnitTest \
  :mediaexperiment:lintDebug \
  :mediaexperiment:testDebugUnitTest \
  :mediaexperiment:assembleDebug
```

Result: **BUILD SUCCESSFUL** (96 tasks executed on the full gate).

| Gate | Measured result |
| --- | --- |
| `:app:assembleDebug` | PASS |
| `:app:lintDebug` | PASS; 0 errors/fatal, 24 warnings on unchanged production sources |
| `:app:testDebugUnitTest` | 1,051 discovered: **1,050 passed, 1 skipped**, 0 failures/errors |
| `:mediaexperiment:lintDebug` | PASS; 0 errors/fatal, 7 warnings |
| `:mediaexperiment:testDebugUnitTest` | **63 passed**, 0 failures/errors/skipped |
| `:mediaexperiment:assembleDebug` | PASS |
| APK inspection/signature | Distinct package/version verified; APK v2 signature verifies |
| `git diff --check` | PASS |
| Production diff against experimental base | Empty |
| Python boundary suite | **118 tests: 104 passed, 14 expected failures**, same failing tests before/after |

The seven experimental lint warnings are one VectorRaster, one DataExtractionRules
and five SetTextI18n warnings. They do not concern pause ownership, session identity,
MediaPlayer preparation or prohibited control mechanisms. They are reported rather
than suppressed.

JVM breakdown:

| Test class | Passed |
| --- | ---: |
| `InterludeStateMachineTest` | 9 |
| `SafeResumeGuardTest` | 12 |
| `InterludeRuntimeTest` | 32 |
| `SessionCatalogTest` | 10 |
| **Total** | **63** |

The useful original nine machine tests and valid guard scenarios are retained,
with explicit live-session inputs. The obsolete emergency-resume-positive test
is replaced by the pre-existing-pause ownership rejection. Runtime/catalog
coverage changes the actual active-controller inventory source used by Android,
including replacement/missing sessions and a dispatch-time race; it does not
merely inject a different package into a stale-controller core test.

The runtime tests cover every required case: denied focus, isolated duck/pause,
PAUSE sent versus confirmed, full/pause-only timeout, all non-PLAYING initial
states, one guarded PLAY on normal completion, package/token replacement,
original disappearance, another relevant app, media change, every non-PAUSED
latest state, overlay/video error, repeated STOP, and unsupported/unconfirmed
pause. Extra checks cover query/access failure, priority-owner replacement,
focus-induced pause, synchronous video error, overlay-removal failure, stale
cue deadline and controller disappearance between guard and dispatch.

## E. Requirement audit

**AUTOMATED PASS below means logic/wiring/static/build evidence only. It does not
mean a physical TV behavior passed.**

| REQUIREMENT | IMPLEMENTED | AUTOMATED PASS | PHYSICAL TEST REQUIRED |
| --- | --- | --- | --- |
| Scan is observation only | Yes, separate runtime action | Yes, scan-only test | Yes, actual Prime discovery |
| Audio duck contains no pause/video | Yes, isolated focus/cue path | Yes, grant/deny/deadline tests | Yes, audible duck/restoration |
| TEST PAUSE contains no cue/overlay/video/PLAY | Yes, pause-only mode | Yes, confirmed and timeout runtime tests | Yes, observed Prime PAUSED and absence of interlude |
| Only PLAYING -> POC PAUSE -> observed PAUSED acquires ownership | Yes, explicit initial state and ownership | Yes, non-PLAYING matrix and dispatch race | Yes, real state reporting and pre-paused refusal |
| Fresh live session/token/package validation | Yes, scanner uses SessionCatalog | Yes, package/token/missing/equal-adapter tests | Yes, platform session changes |
| No resume after relevant-app/media change | Yes, relevance and identity checks | Yes, replacement/priority/competing/media tests | Yes, native app-switch behavior |
| Latest live state must remain PAUSED | Yes, guard plus transport recheck | Yes, all non-PAUSED states rejected | Yes, genuine Prime state |
| Full happy path issues one guarded PLAY and confirms PLAYING | Yes | Yes, real runtime/catalog seam | Yes, Prime pause/video/resume |
| Error/STOP never sends PLAY; teardown idempotent | Yes; no emergency-resume branch | Yes, error/STOP/late-callback/removal tests | Yes, real surface/focus teardown |
| Cue attributes precede preparation | Yes, new player then attributes/source/prepare/start | Yes, compiled source inspection and lint | Yes, audio behavior |
| Bounded content-free diagnostics | Yes, coarse fields and enum reasons | Yes, source inspection and compiled UI | Yes, readability and diagnostic correspondence |
| Isolated package, no app dependency or prohibited mechanisms | Yes, retained architecture | Yes, manifest/build inspection and empty app diff | Yes, later side-by-side installation |
| Frozen boundary guards retained | Yes, unmodified | Yes, identical **expected 14-failure delta** | No |

## F. APK and exact changed-file list

- Code checkpoint: `badc96e4d9bf840572cbb58dca4cc7d3d7634fb7`.
- Package: `com.scenevibe.tvcompanionpoc.mediaexperiment`.
- versionName: `0.1.1-media-interlude-poc-corrective`.
- versionCode: `2`.
- Build APK path: `mediaexperiment/build/outputs/apk/debug/mediaexperiment-debug.apk`.
- APK size: **80,457 bytes**.
- APK SHA-256: `aed354daa66ada3c3cb97820a9e2904b4e5aaa359e7162ee54162064e4c15a35`.
- APK signature: debug-signed; verified with `apksigner verify --verbose`.

This digest identifies this delivered APK. A different debug keystore or rebuild
can produce a different APK digest; it is not a reproducible release-hash claim.
The final documentation-only commit does not change APK source bytes.

Exactly **26 files** change from the starting HEAD (25 code/build/test files
and this existing report):

```text
docs/experiments/media-interlude-poc-report.md
mediaexperiment/build.gradle
mediaexperiment/src/main/java/com/scenevibe/tvcompanionpoc/mediaexperiment/AndroidLocalAudioPort.java
mediaexperiment/src/main/java/com/scenevibe/tvcompanionpoc/mediaexperiment/AndroidMediaControlPort.java
mediaexperiment/src/main/java/com/scenevibe/tvcompanionpoc/mediaexperiment/AndroidOverlayVideoPort.java
mediaexperiment/src/main/java/com/scenevibe/tvcompanionpoc/mediaexperiment/InterludeService.java
mediaexperiment/src/main/java/com/scenevibe/tvcompanionpoc/mediaexperiment/MediaExperimentActivity.java
mediaexperiment/src/main/java/com/scenevibe/tvcompanionpoc/mediaexperiment/MediaSessionScanner.java
mediaexperiment/src/main/java/com/scenevibe/tvcompanionpoc/mediaexperiment/core/ActiveSessionSource.java
mediaexperiment/src/main/java/com/scenevibe/tvcompanionpoc/mediaexperiment/core/DeniedReason.java
mediaexperiment/src/main/java/com/scenevibe/tvcompanionpoc/mediaexperiment/core/Diagnostics.java
mediaexperiment/src/main/java/com/scenevibe/tvcompanionpoc/mediaexperiment/core/InterludeRuntime.java
mediaexperiment/src/main/java/com/scenevibe/tvcompanionpoc/mediaexperiment/core/InterludeStateMachine.java
mediaexperiment/src/main/java/com/scenevibe/tvcompanionpoc/mediaexperiment/core/PauseOwnership.java
mediaexperiment/src/main/java/com/scenevibe/tvcompanionpoc/mediaexperiment/core/PlaybackSnapshot.java
mediaexperiment/src/main/java/com/scenevibe/tvcompanionpoc/mediaexperiment/core/SafeResumeGuard.java
mediaexperiment/src/main/java/com/scenevibe/tvcompanionpoc/mediaexperiment/core/SessionCatalog.java
mediaexperiment/src/main/java/com/scenevibe/tvcompanionpoc/mediaexperiment/core/SessionController.java
mediaexperiment/src/main/java/com/scenevibe/tvcompanionpoc/mediaexperiment/core/SessionPort.java
mediaexperiment/src/main/java/com/scenevibe/tvcompanionpoc/mediaexperiment/core/SessionRevalidation.java
mediaexperiment/src/main/java/com/scenevibe/tvcompanionpoc/mediaexperiment/core/SessionTarget.java
mediaexperiment/src/test/java/com/scenevibe/tvcompanionpoc/mediaexperiment/InterludeRuntimeTest.java
mediaexperiment/src/test/java/com/scenevibe/tvcompanionpoc/mediaexperiment/InterludeStateMachineTest.java
mediaexperiment/src/test/java/com/scenevibe/tvcompanionpoc/mediaexperiment/SafeResumeGuardTest.java
mediaexperiment/src/test/java/com/scenevibe/tvcompanionpoc/mediaexperiment/SessionCatalogTest.java
mediaexperiment/src/test/java/com/scenevibe/tvcompanionpoc/mediaexperiment/TestFakes.java
```

## G. Short operator protocol — later authorized Sony / Prime qualification

**Do not run or install as part of this corrective work. Physical qualification
requires separate authorization. The following is the protocol for that later
session, not a record of a test already performed.**

1. Install the delivered experimental APK beside SceneVibe, keeping production
   pairing/data. Grant overlay and notification access to the **experimental**
   package only. Confirm version `0.1.1-media-interlude-poc-corrective`.
2. Start Prime native playback. Run **SCAN MEDIA SESSION** and verify the relevant
   package, PLAYING and advertised PAUSE support. If opening the diagnostic app
   has already paused Prime, record that limitation and restore native playback;
   never treat an initial PAUSED state as POC ownership.
3. Run **TEST AUDIO DUCK**. Listen for the cue and any reduction/restoration of
   Prime audio. Record audible behavior separately from focus GRANTED. Verify
   no PAUSE, fullscreen video or PLAY was dispatched.
4. Re-establish native PLAYING, then run **TEST PAUSE**. Require sent=true followed
   by confirmed=true and ownership=true within four seconds. Confirm that there
   is no cue or video and no automatic resume. Timeout is UNSUPPORTED / NOT
   CONFIRMED. **Manually resume Prime and verify PLAYING before the next step.**
5. Run **TEST FULL INTERLUDE**. Observe genuine PAUSED before the fullscreen local
   video. On normal completion record live revalidation, latest PAUSED, one PLAY
   sent and a genuine later PLAYING confirmation. If any gate is denied, the
   expected behavior is teardown and no POC PLAY.
6. Check refusal while Prime was manually pre-paused. In separate attempts check
   STOP during the video, and a relevant app/session/media change when feasible.
   STOP must remove the overlay and leave native resume to the operator. Repeat
   STOP and verify no additional effects or accidental PLAY.
7. Record only coarse diagnostics, transitions, visible/audible behavior, APK
   digest and TV/Prime versions. Do not log tokens, title/subtitle, content or
   credentials. Cases not actually exercised remain **NOT TESTED**.

## H. Exact expected Python boundary delta

Both the starting tree and the final corrective tree were tested with:

```bash
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s tests
```

Each reports **118 tests, 14 failures**: **10** inherited `settings.gradle`
module-registration byte-pin failures plus **4** report-file inventory failures.
There are **104 passing tests** and no additional corrective failure. The ordered
failing-test list is identical before and after. The former statement claiming
10 failures for the final tree has been removed: 10 is only one component of the
same final **14-failure** delta.

The base `settings.gradle` SHA-256 pin begins `1560b65...`; the experimental module
registration produces `384d9c8...`. The existing report lies outside the frozen
`docs/` inventories. Neither the shared registration nor the inventory/baseline
expectations were changed by this corrective pass.

### Group 1: the `settings.gradle` module-include pin (10 failures)

Every one of these cites only `settings.gradle`, with the identical digest mismatch
`1560b65... != 384d9c8...`:

1. `test_m4_phase_b_boundary.M4PhaseBBoundaryTest.test_qualified_runtime_wire_and_phase_a_sources_remain_byte_exact`
2. `test_m4_phase_c_boundary.M4PhaseCBoundaryTest.test_production_inventory_and_handlers_remain_in_exact_phase_c_scope`
3. `test_m4_phase_d_boundary.M4PhaseDBoundaryTest.test_qualified_runtime_store_wire_and_configs_remain_byte_exact`
4. `test_m4_phase_e_boundary.M4PhaseEBoundaryTest.test_all_old_production_handlers_store_and_configs_remain_byte_exact`
5. `test_m4_phase_f_boundary.M4PhaseFBoundaryTest.test_installer_handlers_wire_runtime_platform_and_signing_remain_byte_exact`
6. `test_m4_phase_g_boundary.M4PhaseGBoundaryTest.test_cloud_installer_handlers_runtime_and_build_inputs_are_byte_exact`
7. `test_m4_sony_corrective_boundary.M4SonyCorrectiveBoundaryTest.test_every_retained_blob_and_corrective_inverse_is_exact`
8. `test_m5_phase_b_boundary.M5PhaseBBoundaryTest.test_all_retained_bytes_and_reversible_provenance_are_exact`
9. `test_m5_phase_c_boundary.M5PhaseCBoundaryTest.test_every_retained_blob_including_models_oracle_owner_and_workflows`
10. `test_m5_phase_d_boundary.M5PhaseDBoundaryTest.test_every_retained_blob_reconstructs_exact_c`

### Group 2: the new docs inventory entry for this report (4 failures)

Each of these fails only because this report is a **new, unlisted file under `docs/`**
(the frozen inventories predate it); none touches any production or M5 byte:

1. `test_m4_sony_corrective_boundary.M4SonyCorrectiveBoundaryTest.test_retained_java_tests_fixtures_and_prior_inventories_have_no_exception`
2. `test_m5_phase_b_boundary.M5PhaseBBoundaryTest.test_no_unlisted_production_test_config_or_document_file`
3. `test_m5_phase_c_boundary.M5PhaseCBoundaryTest.test_no_unlisted_file_in_any_retained_scope`
4. `test_m5_phase_d_boundary.M5PhaseDBoundaryTest.test_exact_file_inventory`

The exact named failures above remain expected on this isolated experimental
branch. No release guard, frozen M4/M5 baseline or production byte was edited to
make them green. No physical Sony qualification result follows from this suite.

## I. Primary Android references

- [MediaPlayer — AudioAttributes must precede preparation](https://developer.android.com/reference/android/media/MediaPlayer).
- [MediaSessionManager — active sessions and priority order](https://developer.android.com/reference/android/media/session/MediaSessionManager).
- [MediaController — session token and transport controls](https://developer.android.com/reference/android/media/session/MediaController).
- [MediaSession.Token](https://developer.android.com/reference/android/media/session/MediaSession.Token).
- [Audio focus](https://developer.android.com/guide/topics/media-apps/audio-focus).

Implementation policy about a competing/changed-priority owner is a conservative
POC inference from the observable active-session list, not an Android guarantee
of foreground-app identity.
