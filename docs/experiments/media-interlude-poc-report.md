# Media Interlude Capability Spike Report

## A. Overview

This document reports an **experimental capability spike**, not production code. It is
**NOT M9** and **NOT** part of any shipping SceneVibe release. The spike exists only to
gather engineering evidence for three capability questions, using an isolated,
side-by-side Android TV app:

1. Can the companion request **audio ducking** (transient, may-duck) while another app
   (for example Prime Video) is the active media owner?
2. Can the companion **pause and later resume** the active media session through the
   `MediaController` transport, confirmed by observed `PlaybackState` transitions rather
   than by firing-and-hoping?
3. Can the companion present a **fullscreen local video interlude** over the paused
   content and then **safely resume** only when it still owns the same media identity?

The spike answers these questions at the level of **logic, packaging, and static checks
only**. The real audio, real Prime transport, and real on-TV overlay behavior are
explicitly **NOT** verified here (see section E).

## B. Isolation

Isolation is the core requirement of this spike. The experimental app is a **separate
Gradle module** (`:mediaexperiment`) with a **distinct applicationId**
`com.scenevibe.tvcompanionpoc.mediaexperiment`, so it installs **side-by-side** with the
production `:app` and shares **none** of the following with it:

- SharedPreferences
- Installation ID
- Cloud device ID / Cloud client
- Pairing state
- Cache
- Database
- Signing-identity assumptions
- BootReceiver / autostart

The experimental module contains **no** Cloud client, **no** SceneVibe assignment logic,
**no** FinalTrack ingestion, and **no** `PackageInstaller` usage. It declares **no**
`INTERNET` and **no** `RECEIVE_BOOT_COMPLETED` permission and ships **no** BootReceiver.
It does **not** depend on `project(":app")`; it only **mirrors** `:app`'s toolchain
(compileSdk 35 / minSdk 26 / targetSdk 35 / Java 17 / `testOptions.unitTests.returnDefaultValues`).

The **only** change made to any shared or root file is the single mandatory one-line
module registration in `settings.gradle` (`include(":mediaexperiment")`) plus the
gitignored `local.properties`. **No file under `app/` was modified** (verified by
`git diff --stat` against the pre-spike commit: empty).

## C. Implemented

The following was built in the isolated `:mediaexperiment` module:

- **Isolated Android TV application module** `:mediaexperiment` with its own
  `build.gradle`, manifest, resources, and assets. No dependency on `:app`.
- **Manifest** declaring:
  - `SYSTEM_ALERT_WINDOW` (for the fullscreen local interlude overlay).
  - `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_MEDIA_PLAYBACK` (typed permission that
    matches `foregroundServiceType="mediaPlayback"`, as required by targetSdk 35).
  - A `mediaPlayback` **foreground service**.
  - Its **own** `NotificationListenerService` (`ExperimentMediaAccessService`) pointing at
    its **own** `ComponentName`, with an isolated notification-access check (equivalent of
    `:app`'s `NotificationAccess`, not shared).
  - A `LEANBACK_LAUNCHER` diagnostic Activity (`MediaExperimentActivity`).
- **AudioFocusPort** requesting `AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK` with
  `USAGE_ASSISTANT` / `CONTENT_TYPE_SPEECH`.
- **MediaControlPort** issuing transport `pause()` / `play()` through a `MediaController`
  (MediaSession transport controls only; **no** accessibility, **no** simulated remote
  buttons, **no** `adb input`, **no** coordinate injection, **no** UI scraping).
- **OverlayVideoPort**: fullscreen `TYPE_APPLICATION_OVERLAY` window with a `TextureView` +
  `MediaPlayer` playing a bundled local `assets/interlude.mp4`. Unlike `:app`'s passive
  commentary overlay, this one is interactive and `MATCH_PARENT`, so it does **not** use
  `FLAG_NOT_TOUCHABLE`.
- **LocalAudioPort** playing a bundled short cue (`res/raw/scenevibe_cue.m4a`).
- **Android-free core logic** (plain Java, no Android framework references, JUnit-testable
  under `returnDefaultValues`):
  - `InterludeStateMachine` enforcing only legal transitions, routing unexpected events to
    a cleanup/STOPPED state rather than crashing, with a bounded enum-only transition log.
  - `SafeResumeGuard` (fail-closed) that permits a resume `PLAY` only when the same media
    identity is still owned and the pause was actually confirmed.
  - **Intentional fail-safe narrowing:** although `SafeResumeGuard` can also authorize a
    resume during explicit emergency cleanup, the state machine never drives that branch at
    runtime. Every emergency or error teardown (`STOP`, overlay/video failure, out-of-order
    events) tears down and leaves the other app paused WITHOUT issuing `PLAY`; only the
    normal interlude-completed path attempts a guarded resume. This is deliberately more
    conservative than the spec, which permits resume during emergency cleanup, because an
    automatic `PLAY` while the POC's own overlay or playback is failing is the riskier
    behavior. The emergency-resume branch therefore exists in the guard (and is covered by a
    unit test) but is not reachable at runtime by design.
- **TV diagnostic UI** (`MediaExperimentActivity`), programmatic D-pad layout, with five
  operator actions plus stop: **SCAN MEDIA SESSION**, **TEST AUDIO DUCK**, **TEST PAUSE**,
  **TEST FULL INTERLUDE**, **EMERGENCY RESTORE**, **STOP**, and bounded on-screen
  diagnostics.

## D. Automated Pass

All automated gates below were run **locally in this sandbox** and passed. They prove
**logic correctness, packaging, and static checks ONLY**. They prove **nothing** about
real audio, real Prime transport, or real on-TV rendering.

### Toolchain note

- Local builds used **Gradle 8.14.5** at
  `/root/.local/share/mise/installs/gradle/8.14/gradle-8.14.5/bin/gradle`, with
  **JDK 17** (`/opt/toolchains/.local/share/mise/installs/java/17.0.2`) and
  `ANDROID_HOME=/opt/android-sdk`.
- **CI uses Gradle 8.9**, which is **not installed in this sandbox** (there is no 8.9
  binary here). The 8.14.5 toolchain assembles, lints, and tests cleanly; it only emits
  harmless Gradle 9 deprecation warnings. This toolchain difference is called out here for
  transparency; the CI gate should be re-run on Gradle 8.9 to confirm parity.

### Commands and results

Gate command (all four targets green):

```
JAVA_HOME=/opt/toolchains/.local/share/mise/installs/java/17.0.2 \
ANDROID_HOME=/opt/android-sdk \
/root/.local/share/mise/installs/gradle/8.14/gradle-8.14.5/bin/gradle \
  :app:assembleDebug \
  :mediaexperiment:testDebugUnitTest \
  :mediaexperiment:lintDebug \
  :mediaexperiment:assembleDebug --console=plain
```

Result: **BUILD SUCCESSFUL**.

- `:app:assembleDebug`: **green** (regression anchor; `:app` deliberately untouched).
- `:mediaexperiment:lintDebug`: **green** (no lint errors; no isolation-relevant
  suppressions added).
- `:mediaexperiment:assembleDebug`: **green**; produced the debug APK (see section F).
- `:mediaexperiment:testDebugUnitTest`: **green**, **17 tests, 0 failures, 0 errors**
  across the Android-free core:
  - `InterludeStateMachineTest`: **9 tests, 0 failures**. These are the nine required
    scenarios:
    1. `happyPath_confirmedOwned_playsExactlyOnce`
    2. `focusDenied_noPauseNoAudio_zeroPlay`
    3. `pauseTimeout_notConfirmed_doesNotContinue_zeroPlay`
    4. `sessionReplacement_differentPackage_guardFails_zeroPlay`
    5. `mediaIdentityChange_samePackageNewId_guardFails_zeroPlay`
    6. `overlayFailure_emergencyCleanup_zeroPlay`
    7. `repeatedStop_isIdempotent`
    8. `noAccidentalPlay_afterUnconfirmedPause`
    9. `pauseSentIsNotPauseConfirmed`
  - `SafeResumeGuardTest`: **8 tests, 0 failures**:
    `allConditionsHold_true`, `pauseNotSent_false`, `pauseSentButNotConfirmed_false`,
    `mediaIdentityChanged_false`, `appSwitchedToDifferentPackage_false`,
    `noObservationAvailable_false`, `interludeNotEndedNormallyAndNotEmergency_false`,
    `emergencyCleanupAllowsResumeWhenOtherwiseOwned_true`.

Python boundary suite:

```
python3 -m unittest discover -s tests
```

Result: **Ran 118 tests, 10 failures** (all solely the `settings.gradle` module-include
byte pin; see section H). Everything else in the boundary suite passes.

**Explicit scope limit:** the above proves state-machine and guard **logic**, APK
**packaging**, and **static analysis** only. No automated gate here exercises real audio
focus, a real `MediaController` against Prime Video, or real overlay rendering on a TV.

## E. Physical Sony / Prime NOT YET TESTED

The following are **physical behaviors** and are **NOT verified** by anything in this
sandbox. No emulator was available and no physical device was used, so **no JVM or
emulator claim is made** about any of them:

- **Real audio ducking** while Prime Video is actively playing. NOT TESTED.
- **Real Prime Video MediaSession pause/play confirmation** (observing a true
  `PlaybackState` transition to PAUSED and back on real content). NOT TESTED.
- **Real fullscreen interlude** rendered over genuinely paused Prime content on a real TV,
  and **real safe resume** afterward. NOT TESTED.

The **Sony BRAVIA is reserved for M5** and **MUST NOT be used for this spike**. Do **NOT**
install the experimental APK on the Sony for this work. Physical validation is deferred to
a later, deliberately scheduled on-hardware session following the operator protocol in
section G.

## F. Deliverables

- **Branch:** `experiment/scenevibe-media-interlude-poc-001`
- **HEAD at build time (code commit):** `75bb58d64342e00c1138148c8a42508d934c46d2`
  (`75bb58d`). This report is committed on top of that code commit, so the branch tip
  moves forward by exactly this docs commit; the built APK below corresponds to the
  `75bb58d` code state.
- **Package id:** `com.scenevibe.tvcompanionpoc.mediaexperiment`
- **versionName:** `0.1.0-media-interlude-poc` (versionCode `1`)
- **APK path:** `mediaexperiment/build/outputs/apk/debug/mediaexperiment-debug.apk`
- **APK SHA-256:**
  `48fd2e4ce41c8e92acd288bc3379b321d6dd55af0be3817d684837f27a306462`

**APK digest caveat:** this is a **debug** build signed with the ephemeral Android debug
keystore. The SHA-256 is therefore **informational and non-reproducible** across machines
and rebuilds (the debug signature and build timestamps differ per environment). Treat the
digest as a record of the artifact produced in this sandbox, not as a reproducible release
hash.

## G. Operator Protocol (for later physical testing)

To be run **by a human on real hardware** in a future, deliberately scheduled session (not
on the Sony reserved for M5):

1. Install the experimental APK **beside** the production SceneVibe app (distinct
   applicationId means they coexist; do not uninstall SceneVibe).
2. In Android TV settings, grant the **experimental** app:
   - **Display over other apps** (Settings > Apps > Special app access > Display over other
     apps), and
   - **Notification access** (Settings > Apps > Special app access > Notification access),
   pointing at the **experimental** app only, not SceneVibe.
3. Open **Prime Video** and start playback of any title; let it play.
4. Launch the experimental diagnostic app and run the actions in order, recording the
   observed `PlaybackState` transitions at each step:
   1. **SCAN MEDIA SESSION**: confirm the active Prime session is discovered.
   2. **TEST AUDIO DUCK**: confirm Prime audio ducks (quiets) while the cue plays, then
      restores.
   3. **TEST PAUSE**: confirm the session reports **PAUSED** (observe the transition, do
      not assume).
   4. **TEST FULL INTERLUDE**: confirm the fullscreen local interlude renders over the
      paused content.
   5. **Verify safe resume**: confirm playback resumes only if the same media identity is
      still owned.
   6. **EMERGENCY RESTORE / STOP**: confirm the overlay is torn down and no accidental
      PLAY is issued.
5. Record every observed `PlaybackState` transition and any divergence from the expected
   state-machine sequence.

Android documentation references:
[MediaController](https://developer.android.com/reference/android/media/session/MediaController),
[MediaSession](https://developer.android.com/reference/android/media/session/MediaSession),
[Audio focus](https://developer.android.com/guide/topics/media-apps/audio-focus),
[Display over other apps](https://developer.android.com/reference/android/provider/Settings#ACTION_MANAGE_OVERLAY_PERMISSION).

## H. Known Boundary-Test Delta (documented, not hidden)

Adding an installable isolated module **requires** registering it in the repo's single
tracked `settings.gradle`. That one mandatory line:

```
include(":mediaexperiment")
```

changes the byte content of `settings.gradle` from the pinned hash `1560b65...` to
`384d9c8...`. Several M4 and M5 **release provenance guards** pin `settings.gradle`
**byte-for-byte**, so on this experimental branch exactly **10** of their assertions fail
**solely on the `settings.gradle` byte hash and nothing else**.

In addition, `docs/` is itself within the boundary guards' **file inventory** scope, so
adding this report file (`docs/experiments/media-interlude-poc-report.md`) trips **4**
further inventory assertions that each flag the new, unlisted docs file. That brings the
**actual final total to 14 failures** when the python suite is run **after this report file
exists**. The breakdown is reported honestly below; both groups are expected and benign on
an experimental branch, and neither reflects any change to production/M5 bytes.

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

Why this is acceptable and what was deliberately **not** done:

- **(a)** This is the expected, unavoidable consequence of adding an installable isolated
  module on an experimental branch: the module must be registered, and registration edits
  the one pinned shared file.
- **(b)** The M4/M5 **frozen baselines were deliberately NOT altered**
  (`.github/scripts/m4-*-baseline.json`, `.github/scripts/m5-*-baseline.json`), the
  boundary tests were **not edited**, and `settings.gradle` was **not** manipulated to hide
  the module. Editing a release guard to force it green would be worse than an honest,
  documented delta.
- **(c)** The **M5 physical qualification is unaffected**: no `:app` / production /
  M5-scheduler / FinalTrack / Cloud byte changed. Everything the guards actually inventory
  (`app/src/`, `tests/`, `.github/`, and the frozen production/oracle bytes) is unchanged,
  and the `mediaexperiment/` module is entirely **outside** their inventory scope.

**Measured final result (after this report file exists):**
`python3 -m unittest discover -s tests` reports **118 tests, 14 failures** = **10**
`settings.gradle` byte-pin failures (Group 1) **+ 4** docs-inventory failures caused by this
new report file itself (Group 2). These numbers are the real, as-measured result and were
**not** worked around: the M4/M5 baselines, boundary tests, and `:app`/production bytes were
all deliberately left untouched, so M5 physical qualification is unaffected.
