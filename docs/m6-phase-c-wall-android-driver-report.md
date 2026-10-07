# M6 Phase C — WALL Android Driver + Common Runtime Owner + Restore Qualification

## Scope and starting point

Phase C owns the TV-local WALL (banner wall-clock) runtime integration only. It does
not own the Cloud Banner resolver, Cloud civil-time rules, Banner publication/assignment,
the Send/GET/ACK Banner wire, DB migration, control-plane cutover, remote assets, the
asset cache, Audio/Video Banner, Media Interlude, PAUSE/PLAY, the Generic Multimedia Track
Model, SceneVibe Studio, the Public Track Platform, Creator/Identity, production cutover or
Sony physical qualification. Those remain Phase D and later.

- Exact starting HEAD: `2d2c22ac2926531ce728575ab7b87a4b5a66fd99` (the qualified pure WALL
  models/scheduler commit, Phase B closed).
- Branch: `work/scenevibe-os-m6-banner-wall-architecture-001`.
- Frozen Phase B provenance baseline (`.github/scripts/m6-phase-b-baseline.json`,
  278 starting blobs, canonical sha256 `13e36b17…`) is left byte-unchanged.

## Delta (added / modified paths)

### New production Java (common owner, Android WALL driver, local Banner handler)
- `OverlayRuntimePorts.java`, `BannerInstallationRuntimePorts.java`,
  `LiveBannerRuntimePorts.java`, `BannerInstallationHandler.java`,
  `BannerOverlayManifestBridge.java`, `BannerPreparedState.java`,
  `BannerProfileParser.java`, `OverlayInstallationHandlers.java`.
- `WallClockDriver.java`, `WallClockSource.java`, `WallWaitScheduler.java`,
  `WallDriverDiagnostics.java`, `AndroidWallClockSource.java`,
  `AndroidWallWaitScheduler.java`, `AndroidWallSignalReceiver.java`.

### New JVM test suites
- `M6BannerOwnerTest.java`, `M6WallDriverTest.java`, `M6BannerHandlerTest.java`.

### Modified existing production Java (eight files, each pinned with a whole-file inverse)
- `OverlayService.java`, `AutostartPolicy.java`, `BootReceiver.java`,
  `DiagnosticsActivity.java`, `DiagnosticsStore.java`, `RuntimeDiagnostics.java`,
  `installation/InstallationHandlerRegistry.java`, `installation/TvCapabilities.java`.

### New provenance, report and planner artifacts
- `.github/scripts/m6-phase-c-baseline.json`, `tests/m6_phase_c_provenance.py`,
  `tests/test_m6_phase_c_boundary.py`, this report, and the committed planner tree
  under `.agents/tasks/task-m6-phase-c-wall-android-driver-common-owner/`.

### Inherited gates extended (admission-with-exact-inverse, never weakened)
The eleven inherited whole-repository boundary gates
(`tests/test_m4_phase_{b,c,d,e,f,g}_boundary.py`,
`tests/test_m4_sony_corrective_boundary.py`,
`tests/test_m5_phase_{b,c,d}_boundary.py`, `tests/test_m6_phase_b_boundary.py`) were
extended so their exact-inventory sets admit the Phase C additions and their byte-exact
production checks delegate the eight edited files to the Phase C gate. The former
Phase-B-only WALL-isolation assertion now inspects the exact pre-Phase-C (frozen) bytes
rather than the post-Phase-C runtime, so OverlayService may legitimately wire WALL while
the assertion remains exact. Every one of these edits is admitted in
`m6-phase-c-baseline.json` with a full before/after hash and a unique whole-file inverse
that reconstructs the exact pre-Phase-C byte. No test is deleted, no skip or wildcard is
added, and no earlier baseline is rewritten.

## WALL Android driver constants actually tested

`WallClockDriver` (outside the pure `wall/` package, so the purity gate still holds):

- `READ_WINDOW_MS = 50`
- `MAX_IMMEDIATE_RETRIES = 2`
- `DRIFT_THRESHOLD_MS = 1000`
- `WALL_MAX_WAIT_MS = 1000`

Every timer callback rereads a fresh civil-time sample paired with an elapsed-realtime
anchor before deciding DUE; a non-monotonic or wider-than-window read fails closed after at
most two immediate retries; an absolute drift at or beyond the threshold re-anchors and
re-evaluates. The single bounded wait is a relative `min(nextBoundary - now, WALL_MAX_WAIT_MS)`
delay, never a zero-delay loop. The scheduler, anchor, wallGeneration, timer ticket and
consumed cursor are all non-durable; restore creates a fresh temporal context.

## Common-owner extraction

The real principal runtime owner mechanic (pending/active/retiring activations, a fresh
unique token per ARM, generation captured once after `replaceRevision`, invalidate-before-retire,
`matching(token)` honoring only the active activation, `selectActiveRevision` as the sole
promotion step, bounded `abortActivation`) is reused under a single common owner exposing
static Video and Banner ports (`OverlayRuntimePorts` composing `LiveVideoRuntimePorts` and
`LiveBannerRuntimePorts`). No competing LiveBanner owner is introduced; Video and Banner are
alternating kinds, never concurrent installations.

## Banner handler / profile / codec

`BannerInstallationHandler` validates/prepares/encodes/restores/arms with zero persistence,
runtime, render or ACK side effects before commit, reusing the existing installer/store path.
The Banner codec/handler id is `scenevibe.banner-wall-overlay.v1`
(`TvCapabilities.CODEC_BANNER_WALL_OVERLAY`); the strict inert profile body type is
`scenevibe.banner.wall-package.v1`. The static registry stays codec-gated and keeps at most
three current shape slots (the two Video handlers plus the one Banner handler).

## Autostart decision

Banner-aware autostart adds a bounded decision for a KNOWN Banner codec/handler snapshot
(opt-in + overlay + durable present) WITHOUT requiring a MediaSession grant; the boot
receiver still reads only metadata and starts only the boot-prepare action.

## Diagnostics taxonomy

Observational WALL codes only, never gating selection/clock/ACK and never carrying
content/token/credential/eventId/URL/payload: `NONE`, `WALL_CALENDAR_LOADED`,
`WALL_ANCHORED`, `WALL_EVENT_DUE`, `WALL_EVENT_EXPIRED`, `WALL_EVENT_SUPERSEDED`,
`WALL_CLOCK_REEVALUATED`, `WALL_CALENDAR_CLEARED`, `WALL_HORIZON_EXHAUSTED`,
`WALL_DISPLAY_SUSPENDED`, `WALL_CLOCK_INVALID`, `WALL_DEADLINE_FAILED`.

## Video / MEDIA non-regression

- `app/.../calendar/MediaCalendarScheduler.java` is byte-unchanged
  (`817447e570b8e1ee6ecd1e9eaeaa4eb1f8eb5c19`).
- The pure `wall/{WallEvent,WallCalendar,WallCalendarScheduler}.java` are byte-unchanged
  and the Android WALL driver lives outside that package, so the Phase B purity gate still
  passes.
- `TvCapabilities` Video codec/contract/version constant values are unchanged;
  `supportsWallClockExecution()` still advertises `false` (no production WALL capability flip).
- No new Android permission is added to `AndroidManifest.xml`.

## Local qualification evidence actually executed on this HEAD

- Host Python provenance/boundary gate (always reproducible):
  `cd tests && PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s . -p "test_*.py"`
  runs GREEN with the new Phase C gates and all inherited gates (now admitting Phase C),
  0 FAIL / 0 SKIP.
- Pure-JDK WALL contract: `M6WallContract` executes 68/68 (`PASS=68 FAIL=0 SKIP=0`) under the
  Phase B boundary suite, which compiles and runs it with an empty classpath/sourcepath.
- Standalone JUnit (junit-4.13.2 + hamcrest-core-1.3 + org.json, android content/graphics
  stubs for transitive classes): `M6BannerOwnerTest` 15/15, `M6WallDriverTest` 23/23,
  `M6BannerHandlerTest` 23/23, all OK.

### Android Gradle gate (CI-only this session)

No Android SDK is available in this session (`ANDROID_HOME`/`ANDROID_SDK_ROOT` empty, no
`sdkmanager`), so the full `:app:testDebugUnitTest` / `:app:lintDebug` / `:app:assembleDebug`
were NOT run here and no post-Phase-C Android count is fabricated. The inherited CI figure is
1119 total / 1118 PASS / 1 historical SKIP
(`M1CloudInteropTest.originalColumboProjectionIsInstallable`) / 0 FAIL. WALL now adds its JVM
suites (`M6BannerOwnerTest` 15, `M6WallDriverTest` 23, `M6BannerHandlerTest` 23), so the final
CI `:app:testDebugUnitTest` total will exceed 1119; the exact post-Phase-C Android total is a
CI gate and is deliberately not asserted here.

## Phase C stop condition

- PR #16 remains OPEN / DRAFT / unmerged.
- Production remains SHADOW.
- No Phase D work has started; no Cloud Banner cutover; no Sony physical qualification.
- Authorized verdict: `M6 PHASE C: PASS` / `READY FOR M6 PHASE D` (pending the CI Android gate).
