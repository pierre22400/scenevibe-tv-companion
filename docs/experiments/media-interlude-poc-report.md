# Media Interlude Capability Spike — Corrective Pass

> **Physical qualification closure — 6 October 2026.** Sony BRAVIA / Prime Video qualification is now **CLOSED / PASS for the Media Interlude capability** on candidate `0.1.4-media-interlude-poc-audio-adts`. The spoken fixture still says **“Bienvenue sur SceneVibe Audio”** and keeps the original AAC-LC access units; only the rejected M4A container was replaced by ADTS. The isolated AUDIO DUCK experiment remains a **semantic FAIL for Prime on this Sony** because Prime reacts to `AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK` by pausing rather than ducking. The controlled `PLAYING -> SceneVibe-owned PAUSE -> fullscreen local interlude -> fresh revalidation -> guarded PLAY -> PLAYING` path is physically validated end to end. Full physical evidence is recorded in section K.


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

**PHYSICAL SONY / PRIME: CLOSED / PASS — MEDIA INTERLUDE CAPABILITY VALIDATED.**
The physical campaign first exposed and corrected the opaque-Activity harness
blocker, then isolated a Sony/MediaTek M4A parser incompatibility, and finally
qualified candidate `0.1.4-media-interlude-poc-audio-adts`. SCAN and isolated
PAUSE are physically PASS. AUDIO DUCK is mechanically functional but semantically
FAIL for Prime on this Sony because Prime pauses on `MAY_DUCK` and does not
self-resume. The full controlled interlude path is physically PASS: SceneVibe
owns a confirmed PAUSE, renders the fullscreen local video, completes normally,
freshly revalidates the original session in PAUSED state, sends one guarded PLAY,
and observes Prime return to PLAYING. Section K records the exact observations
and terminal diagnostics.

- Repository: `pierre22400/scenevibe-tv-companion`.
- Branch: `experiment/scenevibe-media-interlude-poc-001`.
- Starting HEAD: `0d0f15dbd26732532f7f62e3897a88c72c61132c`.
- Experimental base: `c9b0efd4acfaaae9ed7da13dcec505b2f653c548`.
- Earlier core corrective checkpoint: `badc96e4d9bf840572cbb58dca4cc7d3d7634fb7`.
- Overlay-controls physical-candidate checkpoint: `13e4684c19d18711befb8d1f5999fb71469b76cc`.
- Final audio-container corrective checkpoint: `5c4cca5d0255b42d16e5c955d63b070e14dd09f9`.
- Qualified APK source/CI checkpoint: `69ef5c156ff0a180baffa1b4a667a8cbb831ff59`.
- This report closure is documentation-only after the qualified APK bytes; the
  final branch HEAD is supplied in the delivery.
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

| REQUIREMENT | IMPLEMENTED | AUTOMATED PASS | SONY / PRIME PHYSICAL RESULT |
| --- | --- | --- | --- |
| Scan is observation only | Yes, separate runtime action | Yes, scan-only test | **PASS** — Prime discovered PLAYING with advertised play/pause/play_pause |
| Audio duck contains no pause/video | Yes, isolated focus/cue path | Yes, grant/deny/deadline tests | **SEMANTIC FAIL on Prime/Sony** — cue audible on 0.1.4, but Prime pauses instead of ducking and does not self-resume |
| TEST PAUSE contains no cue/overlay/video/PLAY | Yes, pause-only mode | Yes, confirmed and timeout runtime tests | **PASS** — Prime PAUSED; no cue, no SceneVibe video, no automatic PLAY |
| Only PLAYING -> POC PAUSE -> observed PAUSED acquires ownership | Yes, explicit initial state and ownership | Yes, non-PLAYING matrix and dispatch race | **PASS on happy path** — sent=true, confirmed=true, ownership=true, initial=PLAYING |
| Fresh live session/token/package validation | Yes, scanner uses SessionCatalog | Yes, package/token/missing/equal-adapter tests | **PASS on full happy path** — original session present and revalidation succeeded |
| No resume after relevant-app/media change | Yes, relevance and identity checks | Yes, replacement/priority/competing/media tests | **NOT PHYSICALLY EXERCISED** in this closure |
| Latest live state must remain PAUSED | Yes, guard plus transport recheck | Yes, all non-PAUSED states rejected | **PASS** — latest state before resume = PAUSED |
| Full happy path issues one guarded PLAY and confirms PLAYING | Yes | Yes, real runtime/catalog seam | **PASS** — one PLAY sent and Prime observed PLAYING |
| Error/STOP never sends PLAY; teardown idempotent | Yes; no emergency-resume branch | Yes, error/STOP/late-callback/removal tests | **NOT PHYSICALLY EXERCISED** in this closure |
| Cue attributes precede preparation | Yes, new player then attributes/source/prepare/start | Yes, compiled source inspection and lint | **PASS mechanically** on 0.1.4; audio quality of fixture is poor but playback succeeds |
| Bounded content-free diagnostics | Yes, coarse fields and enum reasons | Yes, source inspection and compiled UI | **PASS** — terminal diagnostics correspond to observed full flow |
| Isolated package, no app dependency or prohibited mechanisms | Yes, retained architecture | Yes, manifest/build inspection and empty app diff | **PASS** — experimental package installed and exercised independently |
| Frozen boundary guards retained | Yes, unmodified | Yes, identical **expected 14-failure delta** | No physical gate required |

## F. Qualified APK and exact changed-file list

Final physically qualified candidate:

- Package: `com.scenevibe.tvcompanionpoc.mediaexperiment`.
- versionName: `0.1.4-media-interlude-poc-audio-adts`.
- versionCode: `5`.
- Final audio-container corrective commit: `5c4cca5d0255b42d16e5c955d63b070e14dd09f9`.
- GitHub Actions candidate commit: `69ef5c156ff0a180baffa1b4a667a8cbb831ff59`.
- GitHub Actions run: `37528995979` — **SUCCESS**.
- Gate: `:mediaexperiment:testDebugUnitTest :mediaexperiment:lintDebug :mediaexperiment:assembleDebug`.
- APK SHA-256: `00cc409e180dcbbb18b1a943cd00c6b95274261f0e14d01ac0251c7e0772db63`.
- The temporary build workflow was removed after artifact capture; its create/delete
  commits do not alter the final source tree or qualified APK bytes.
- Physical Sony/Prime qualification: **CLOSED / PASS for Media Interlude**.

Exactly **31 files** differ from the report's starting HEAD
`0d0f15dbd26732532f7f62e3897a88c72c61132c`. The final fixture transition is
explicit: `scenevibe_cue.m4a` is removed and `scenevibe_cue.aac` is added.
The production `app/` diff remains empty.

## G. Operator protocol — executed Sony / Prime qualification

This protocol was executed on 6 October 2026 against the isolated experimental
package on the Sony BRAVIA. The historical sequence included the 0.1.2 harness
candidate, the 0.1.3 materialized-M4A diagnostic candidate and the final 0.1.4
ADTS candidate. Exact results are in sections J and K.

1. Install the delivered experimental APK beside SceneVibe, keeping production
   pairing/data. Grant overlay and notification access to the **experimental**
   package only. Confirm version `0.1.2-media-interlude-poc-overlay-controls`.
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


## J. Sony physical harness finding and overlay-controls corrective (2026-10-06)

A first authorized Sony BRAVIA physical attempt exposed a **test-harness blocker**, not
a MediaSession scanner failure:

- Android `dumpsys media_session` showed Prime Video package
  `com.amazon.amazonvideo.livingroom` in native `PLAYING` state.
- SceneVibe's own sampler also observed the same Prime session as `PLAYING` with a
  progressing position before the POC Activity took foreground.
- The original `MediaExperimentActivity` was reported by the Sony system as
  `isTranslucent=false`. Roughly one second after it became foreground, SceneVibe's
  sampler changed to repeated `NO_ACTIVE_SESSIONS`.
- Therefore the earlier on-screen `NO_SESSION` result cannot be used as evidence
  against Prime MediaSession compatibility. The opaque diagnostic Activity itself
  invalidated the physical test condition.

Corrective harness change:

- `MediaExperimentActivity` is now a **translucent, short-lived permission launcher**.
- When both grants are present it starts `InterludeService`, requests the diagnostic
  panel, and immediately finishes so the native streaming Activity remains underneath.
- The five operator actions now live in `DiagnosticOverlayWindow`, a narrow,
  translucent, focusable `TYPE_APPLICATION_OVERLAY` window driven by the TV D-pad.
- The control overlay uses `FLAG_NOT_TOUCH_MODAL`; it does not enter the Activity
  stack and does not alter pause ownership or safe-resume logic.
- When the local fullscreen interlude video is first attached, the control panel is
  re-layered above it once so **EMERGENCY RESTORE / STOP** remains reachable.
- Production `:app` remains untouched by this corrective.

Build qualification for the corrected physical candidate:

- Code corrective commit: `13e4684c19d18711befb8d1f5999fb71469b76cc`.
- GitHub Actions run: `37524232444` — **SUCCESS**.
- Gate executed: `:mediaexperiment:testDebugUnitTest`, `:mediaexperiment:lintDebug`,
  `:mediaexperiment:assembleDebug`.
- Package: `com.scenevibe.tvcompanionpoc.mediaexperiment`.
- versionCode: `3`.
- versionName: `0.1.2-media-interlude-poc-overlay-controls`.
- APK SHA-256:
  `aab99a593e36b64f63123e485fdda8ba73dd13173b69c14dd176aa4e0cb17210`.

At this historical 0.1.2 checkpoint the correction was **ready for renewed physical qualification** but had not yet proved audible ducking, native PAUSE ownership, fullscreen interlude rendering or guarded resume. Those tests were subsequently executed; see section K for final results.


## Sony physical audio-duck corrective — 2026-10-06

Physical test on Sony BRAVIA with Prime Video foreground and the 0.1.2 overlay-controls candidate established:

- SCAN PASS: Prime session selected as `com.amazon.amazonvideo.livingroom`, state `PLAYING`, play/pause/play_pause actions available, mediaId present.
- TEST AUDIO DUCK: SceneVibe requested `USAGE_ASSISTANT / CONTENT_TYPE_SPEECH` with transient-may-duck focus; Android granted focus and Prime received `AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK`.
- Prime nevertheless transitioned to PAUSED without any SceneVibe PAUSE command. This is an application reaction to focus, not SceneVibe pause ownership.
- The local spoken fixture then failed before playback: Sony/MediaTek logged `MtkMP4Extractor parseMP4Chunk error(-1007)`, followed by `MediaPlayerNative error (1, -2147483648)` and `LOCAL_AUDIO_START_FAILED`.
- Runtime failed closed: focus was abandoned, SceneVibe did not claim pause ownership and did not dispatch PLAY.

Corrective candidate 0.1.3 changes only local fixture transport:

- `AndroidLocalAudioPort` copies the bundled `scenevibe_cue.m4a` byte-for-byte from `res/raw` to the app cache, then gives MediaPlayer a standalone file path.
- This avoids the Sony/MediaTek extractor path that receives an APK file descriptor plus resource offset.
- Audio-focus semantics, MediaSession scanning, pause ownership, safe-resume guard, overlay/video runtime and production `:app` remain unchanged.
- Version: `versionCode=4`, `versionName=0.1.3-media-interlude-poc-audio-materialized`.
- Corrective code commit: `76be69271328dc289df2cb75973bcbdc3b820856`.
- Version bump commit: `5e845983a6bfc5aa83539b21ee2a334cf4e172ad`.
- CI candidate commit: `3c0b49fac9de762f77a50893f168f047be96983b`.
- GitHub Actions run: `37527203668` — SUCCESS.
- Gate: `:mediaexperiment:testDebugUnitTest`, `:mediaexperiment:lintDebug`, `:mediaexperiment:assembleDebug` — SUCCESS.
- APK SHA-256: `5b1126b3e2b6c068f43ecf9b2308369317d979566b4e281b96d32176220d2d1c`.
- At this historical 0.1.3 checkpoint, AUDIO DUCK qualification was still pending. The subsequent 0.1.4 result is recorded in section K.


## Sony physical audio-duck follow-up — 0.1.3 result and 0.1.4 ADTS candidate — 2026-10-06

The 0.1.3 physical gate eliminated the APK-resource-offset hypothesis:

- `LOCAL_AUDIO_MATERIALIZED bytes=7722` proves the bundled cue was copied successfully to a standalone cache file before MediaPlayer preparation.
- Sony/MediaTek then parsed the standalone file and explicitly reported `Incompatible brand: M4A`.
- The extractor reached the AAC-LC audio track (mono, 22050 Hz) but failed on the MP4 chunk table with `Invalid chunk size: 4`, `parseMP4Chunk error(-1007)`, then `MediaPlayerNative error (1, -2147483648)`.
- `AndroidLocalAudioPort.playShortClip()` consequently failed at `MediaPlayer.prepare()`; runtime remained fail-closed and sent no SceneVibe transport PLAY.
- Prime's pause on focus remains an application response to `AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK`, not SceneVibe pause ownership.

Corrective candidate 0.1.4 preserves the exact AAC-LC access units and removes only the rejected MP4/M4A container:

- Source M4A: 41 AAC access units, 6745 bytes AAC payload.
- New fixture: 7032-byte AAC/ADTS stream, same AAC access units, no audio re-encoding.
- Resource renamed from `scenevibe_cue.m4a` to `scenevibe_cue.aac`; cache file follows the ADTS extension.
- Audio-focus semantics, MediaSession discovery, pause ownership, safe-resume guard, overlay/video runtime and production `:app` are unchanged.
- Corrective commit: `5c4cca5d0255b42d16e5c955d63b070e14dd09f9`.
- Version: `versionCode=5`, `versionName=0.1.4-media-interlude-poc-audio-adts`.
- GitHub Actions run: `37528995979` — SUCCESS.
- Gate: `:mediaexperiment:testDebugUnitTest`, `:mediaexperiment:lintDebug`, `:mediaexperiment:assembleDebug` — SUCCESS.
- APK SHA-256: `00cc409e180dcbbb18b1a943cd00c6b95274261f0e14d01ac0251c7e0772db63`.
- At candidate creation, physical AUDIO DUCK qualification was pending. It was subsequently executed: local cue playback PASS, desired ducking semantics FAIL for Prime/Sony; see section K.


## K. Final Sony BRAVIA / Prime Video physical qualification — CLOSED / PASS — 2026-10-06

### K.1 Environment and candidate

- Device: Sony BRAVIA, Android TV 12.
- Native provider under test: Prime Video package `com.amazon.amazonvideo.livingroom`.
- Experimental package: `com.scenevibe.tvcompanionpoc.mediaexperiment`.
- Candidate: `versionCode=5`, `versionName=0.1.4-media-interlude-poc-audio-adts`.
- Qualified APK SHA-256:
  `00cc409e180dcbbb18b1a943cd00c6b95274261f0e14d01ac0251c7e0772db63`.
- Build run: GitHub Actions `37528995979` — SUCCESS.
- Production SceneVibe application was not modified by this physical campaign.

### K.2 Trial history and findings

#### Trial 1 — original opaque diagnostic Activity

Before the POC Activity took foreground, Android and SceneVibe both observed Prime
as a healthy PLAYING MediaSession with advancing position. The original opaque
Activity then caused the session to disappear from the POC's active-session view.
This invalidated the physical test condition. The harness was corrected to use a
short-lived translucent Activity plus a focusable `TYPE_APPLICATION_OVERLAY`
control panel, leaving Prime underneath.

**Result:** harness defect identified and corrected; not a Prime MediaSession failure.

#### Trial 2 — SCAN MEDIA SESSION on corrected overlay harness

Observed on the panel:

- selected package: `com.amazon.amazonvideo.livingroom`
- playback state: `PLAYING`
- actions: `play=true pause=true play_pause=true`
- mediaId present: `true`

Prime remained visible and playing behind the narrow SceneVibe control panel.

**Result: PASS.**

#### Trial 3 — TEST AUDIO DUCK on 0.1.2

SceneVibe requested transient-may-duck focus with
`USAGE_ASSISTANT / CONTENT_TYPE_SPEECH`. Android granted focus and Prime received
`AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK`. Prime nevertheless paused its own playback.
SceneVibe had sent no PAUSE and therefore acquired no pause ownership.

The spoken cue failed to start. Sony/MediaTek reported MP4 extractor failure and
the runtime failed closed: focus was abandoned and no compensating PLAY was sent.

**Result:** focus path works; target ducking semantics FAIL; local cue playback FAIL.

#### Trial 4 — TEST AUDIO DUCK on 0.1.3 materialized M4A

The resource was successfully copied to a standalone cache file
(`LOCAL_AUDIO_MATERIALIZED bytes=7722`). Sony/MediaTek still rejected the
standalone file: `Incompatible brand: M4A`, then `Invalid chunk size: 4`,
`parseMP4Chunk error(-1007)`, `MediaPlayerNative error (1, -2147483648)`,
and `MediaPlayer.prepare()` failed.

This eliminated the APK file-descriptor/resource-offset hypothesis and isolated
the incompatibility to the M4A/MP4 container path on this device.

**Result:** diagnostic PASS; M4A container incompatible on this Sony/MediaTek path.

#### Trial 5 — TEST AUDIO DUCK on final 0.1.4 AAC/ADTS candidate

The exact original AAC-LC access units were remuxed from M4A into ADTS without
re-encoding the voice.

Physical observations:

- Prime freezes/pauses: **YES**
- spoken cue “Bienvenue sur SceneVibe Audio” audible: **YES**
- Prime sound stops with the paused video: **YES**
- Prime self-resumes when focus is abandoned: **NO**
- cue quality: audibly poor/robotic, acceptable only as a test fixture

The local-audio mechanism is therefore physically functional, but Prime does not
honour the desired semantic meaning of MAY_DUCK on this Sony. It chooses a full
pause and remains paused. Because SceneVibe did not send that PAUSE, the existing
safety model correctly refuses to claim ownership and does not send PLAY.

**Result: AUDIO DUCK = SEMANTIC FAIL for Prime/Sony; safety behavior = PASS.**

Product consequence: do not treat transient-may-duck focus as a portable
SceneVibe commentary-over-native-video mechanism. Any future provider capability
matrix must distinguish actual provider/device behavior from Android focus intent.

#### Trial 6 — TEST PAUSE

Prime was manually restored to PLAYING before the test.

Physical observations:

- Prime pauses: **YES**
- Prime remains paused: **YES**
- SceneVibe spoken cue: **NO**
- SceneVibe fullscreen video: **NO**
- automatic PLAY: **NO**

Terminal diagnostics:

- `pause command sent = true`
- `pause confirmed = true`
- `pause ownership acquired = true`
- `initial playback state = PLAYING`
- `resume denied reason = NONE`

**Result: PASS.** The isolated PAUSE test proves actual command dispatch,
subsequent PAUSED observation and explicit SceneVibe pause ownership without
audio-focus, cue, video or automatic resume.

#### Trial 7 — TEST FULL INTERLUDE

Prime was again manually restored to PLAYING before the independent full test.

Physical observations:

1. Prime paused: **YES**.
2. SceneVibe fullscreen local interlude appeared: **YES**.
3. The local interlude ended normally after approximately five seconds: **YES**.
4. Prime resumed automatically: **YES**.

Terminal diagnostics captured without starting a new SCAN/run:

- `state = STOPPED`
- `notification access = granted`
- selected package = `com.amazon.amazonvideo.livingroom`
- `playback state = PLAYING`
- actions = `play=true pause=true play_pause=true`
- `mediaId present = true`
- `audio focus = granted`
- `pause command sent = true`
- `pause confirmed = true`
- `pause timeout = false`
- `initial playback state = PLAYING`
- `pause ownership acquired = true`
- `original session present = true`
- `session revalidation attempted = true`
- `session revalidation succeeded = true`
- `relevant active package changed = false`
- `latest state before resume = PAUSED`
- `resume denied reason = NONE`
- `overlay attached = true`
- `local video completed = true`
- `local video error = false`
- `play command sent = true`
- `resume confirmed = true`
- `resume timeout = false`

These diagnostics are internally consistent with the observed physical sequence:
SceneVibe started from a live PLAYING Prime session, dispatched and confirmed its
own PAUSE, acquired ownership, completed the local interlude normally, found the
same original session still valid and PAUSED, passed the safe-resume guard,
dispatched PLAY once, and subsequently observed PLAYING.

**Result: TEST FULL INTERLUDE = PASS.**

### K.3 Final verdict

**MEDIA INTERLUDE CAPABILITY POC — SONY BRAVIA / PRIME VIDEO — CLOSED / PASS.**

Physically proven on this provider/device pair:

```text
Prime PLAYING
-> SceneVibe sends PAUSE
-> same session observed PAUSED
-> pause ownership acquired
-> fullscreen local SceneVibe video
-> normal local completion
-> local resources/focus released
-> original session freshly revalidated
-> latest state still PAUSED
-> safe-resume guard permits PLAY
-> SceneVibe sends PLAY
-> same native session observed PLAYING
```

This proves the controlled interlude mechanism on the qualified Sony/Prime pair.
It does **not** prove universal provider/device compatibility. In particular,
isolated AUDIO DUCK is explicitly not qualified as a usable Prime/Sony behavior.

### K.4 Physically unexercised safety cases

The automated suite covers these paths, but they were not required for this
physical closure and must not be represented as physically tested:

- relevant app/session/media changes during the interlude;
- pre-paused refusal as a separate physical attempt;
- emergency STOP during fullscreen local video;
- repeated STOP/idempotence on device;
- deliberate overlay/video failure;
- focus denial and confirmation timeouts.

They remain automated safety evidence, not Sony physical evidence.
