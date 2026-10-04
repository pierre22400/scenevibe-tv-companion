# SCENEVIBE OS — WORK — M4 PHASE D — Video Compatibility Handlers

Date: 2026-10-04  
Repository: `pierre22400/scenevibe-tv-companion`  
Branch: `work/scenevibe-os-m4-tv-installation-001`  
PR: #14 — must remain **OPEN / DRAFT / unmerged**.

## 1. Mission

Implement **M4 Phase D only**: move the already-qualified Video runtime semantics behind two
statically registered compatibility handlers:

- manifested Video;
- legacy Video text.

Phase D must make Video parsing/cross-contract semantics handler-owned while preserving the
qualified current Video v1 behavior and all existing public/internal production call signatures.

Phase D is **not** the PackageInstaller orchestration phase, Cloud v1 adapter phase, generic
production cutover phase, generic reboot/diagnostics cleanup phase, Banner phase, or M5+.

The authoritative architecture is:

- `docs/scenevibe-os-m4-tv-installation-architecture.md`

The immediately preceding closure is:

- `docs/m4-phase-c-generic-durable-store-report.md`

Phase C closure HEAD:

- `531b9cb4db20497a6b1df6ac414f12f2b3bea615`

Frozen milestone bases remain:

- `BASE_TV_M4 = ecdf77bec9f93babf15239a63bf7f702fd7ca293`
- `BASE_CLOUD_M4 = 5011c91aac61a0cc6dcc74c256a15b7dee03d785`

Production revision authority remains **SHADOW**.

This work-order commit is documentation-only. Before implementation, verify that the branch HEAD
is exactly the commit containing this file and that its ancestry contains the Phase C closure HEAD
above. Do not reset to the Phase C SHA and discard this work order.

## 2. Read completely before editing

Read in full, from the exact branch HEAD:

1. `docs/scenevibe-os-m4-tv-installation-architecture.md`
2. this work order
3. `docs/m4-phase-c-generic-durable-store-report.md`
4. `docs/m4-phase-b-generic-model-capabilities-report.md`
5. `docs/m4-phase-a-characterization-report.md`
6. `app/src/main/java/com/scenevibe/tvcompanionpoc/CloudTrackRepository.java`
7. `app/src/main/java/com/scenevibe/tvcompanionpoc/VideoOverlayManifestBridge.java`
8. `app/src/main/java/com/scenevibe/tvcompanionpoc/TrackParser.java`
9. `app/src/main/java/com/scenevibe/tvcompanionpoc/OverlayManifestParser.java`
10. `app/src/main/java/com/scenevibe/tvcompanionpoc/OverlayService.java`
11. `app/src/main/java/com/scenevibe/tvcompanionpoc/SceneRuntimeController.java`
12. `app/src/main/java/com/scenevibe/tvcompanionpoc/MediaSyncedTrackScheduler.java`
13. all current types under
    `app/src/main/java/com/scenevibe/tvcompanionpoc/installation/`

GitHub is the source of truth.

## 3. Starting truth to preserve

At the Phase C closure:

- JVM: **290 PASS / 0 FAIL / 1 private SKIP**, 291 cases;
- Phase A bucket: 30 PASS;
- Phase B bucket: 39 PASS;
- Phase C bucket: 43 PASS;
- Python: **24 PASS / 0 FAIL / 0 SKIP**;
- final-head Android debug workflow: run `37217550274`, SUCCESS;
- final-head Android 15 standard-platform smoke: run `37217550277`, SUCCESS;
- PR #14: OPEN / DRAFT / mergeable / unmerged;
- TV `main` and Cloud `main` unchanged;
- current production runtime still uses the historical Cloud v1 path;
- generic `PackageInstaller` does not exist;
- no concrete production `InstallationHandler` exists yet;
- generic store exists but current callers still use its historical facade;
- no new Sony physical qualification is claimed.

The sole private SKIP remains:

`M1CloudInteropTest.originalColumboProjectionIsInstallable`

via opt-in `SCENEVIBE_M1_SONY_FIXTURES`.

Do not convert the missing private fixture into a false PASS.

## 4. Required Phase D architecture

Introduce exactly two executable static Video compatibility handlers:

1. a manifested Video handler for
   `TvCapabilities.CODEC_TRACK_OVERLAY`;
2. a legacy Video handler for
   `TvCapabilities.CODEC_TRACK`.

They implement the existing `InstallationHandler` contract.

They must be statically composed into one deterministic build-local registry intended for later
PackageInstaller use. The generic `InstallationHandlerRegistry` must remain product-neutral and
must not import Video classes. A Video-side composition/factory class is preferred.

No reflection, service discovery, dynamic class loading, downloaded plugins, WebView, URL-loaded
code or runtime registration.

The canonical handler identities must match the identities already persisted/inferred by Phase C:

- `InstallationStore.COMPAT_OVERLAY_HANDLER_ID`;
- `InstallationStore.COMPAT_TRACK_HANDLER_ID`.

Do not create a second incompatible handler-id vocabulary.

## 5. Package / dependency placement

Because the existing Video parser/runtime types are package-private in
`com.scenevibe.tvcompanionpoc`, the concrete Video handlers may live in that package.

The generic package
`com.scenevibe.tvcompanionpoc.installation`
must remain free of Video, Prime, scheduler, renderer, controller and Cloud transport imports.

Allowed direction:

```text
generic installation contracts
        ^
        |
Video compatibility handlers
        |
existing Video parser / bridge / runtime ports
```

Forbidden direction:

```text
generic installation package
        -> VideoOverlayManifestBridge / TrackParser / OverlayManifest /
           MediaSyncedTrackScheduler / SceneRuntimeController / Prime / CloudControlClient
```

## 6. Manifested Video handler — exact responsibilities

The manifested handler owns the semantic package shape:

- codec id: `scenevibe.runtime-track-overlay.v1`;
- exact artifact set: `runtime` + `manifest`;
- runtime artifact decoded as strict UTF-8 without replacement or normalization;
- manifest artifact decoded as strict UTF-8 without replacement or normalization;
- historical UTF-16 semantic bounds retained:
  - runtime <= 400,000 UTF-16 units;
  - manifest <= 800,000 UTF-16 units;
- runtime remains Cloud-compatible text-only:
  - comments array required;
  - each comment must be an object;
  - presence of a `media` field remains rejected for this compatibility shape;
- runtime parsed by the existing `TrackParser`;
- manifest parsed by the existing `OverlayManifestParser`;
- cross-artifact validation performed by the existing
  `VideoOverlayManifestBridge`;
- Video/media clock coherence retained;
- canonical bytes remain the exact bounded artifact bytes supplied in the request;
- PREPARE creates no store write, scheduler mutation, renderer mutation, ACK or network access.

The generic handler must not know `finalTrackId`, device credentials, Cloud URLs or transport
envelopes.

### 6.1 Capability enforcement

The handler must derive an `ExecutionRequirements` from the parsed package and validate it against
`TvCapabilities.current()`.

For the manifested generic handler, the executable M4 profile remains:

- rendering contract: `scenevibe.overlay-manifest.v1`;
- clock: MEDIA;
- pause behavior: FREEZE;
- remote asset acquisition: false;
- shared asset cache: false;
- timed scene count: exact bounded manifest scene count.

A parser-valid wall-clock manifest is not executable in M4.

A parser-valid manifested `continue` profile is not advertised by the Phase B capability matrix
and must return `UNSUPPORTED_CAPABILITY` on the **generic handler contract**.

Do not silently reinterpret an `assetRef` as requiring remote acquisition: current SceneVibe has
local asset references but no downloader. Remote acquisition remains false.

### 6.2 Transitional compatibility warning

The currently frozen Cloud v1 path predates the explicit Phase B capability profile. Before changing
any current production acceptance/rejection behavior, characterize any edge where the historical
repository/transport accepted a parser-valid package that the new generic capability model rejects
(for example a manifested `continue` profile).

If such an edge exists:

- the **generic handler** must follow the Phase B capability truth;
- the current production compatibility path must not be silently behavior-changed merely to make
  Phase D aesthetically uniform;
- preserve the current caller's frozen behavior through a narrow compatibility evaluation seam
  owned by the same Video handler if necessary;
- document the difference explicitly in the Phase D report;
- do not modify the Cloud wire contract in Phase D.

Qualified supported fixtures and all previously characterized cases must remain behavior-compatible.

## 7. Legacy Video handler — exact responsibilities

The legacy handler owns the no-manifest package shape:

- codec id: `scenevibe.runtime-track.v1`;
- exact artifact set: `runtime` only;
- strict UTF-8 decode;
- <= 400,000 UTF-16 units;
- same Cloud-compatible text-only check;
- parse through the existing `TrackParser`;
- no OverlayManifest parse;
- no `VideoOverlayManifestBridge`;
- derive requirements:
  - rendering contract: `scenevibe.track.v1`;
  - clock: MEDIA;
  - pause behavior:
    - FREEZE when `pauseFreezesDisplay == true`;
    - CONTINUE when `pauseFreezesDisplay == false`;
  - timed scene count: exact comment/event count;
  - remote asset acquisition: false;
  - shared asset cache: false;
- validate derived requirements through `TvCapabilities.current()`.

PREPARE remains memory-only and cannot persist, render, ACK or use the network.

## 8. Prepared state — narrow additive evolution allowed

Phase B deliberately made `PreparedInstallation` inert and scalar-only. The M4 architecture,
however, requires PREPARE to retain the already parsed/validated runtime state so ARM does not
independently reinterpret untrusted JSON.

Phase D may therefore make a **narrow additive evolution** of the generic contract:

- introduce a marker/typed prepared-state contract owned by `InstallationHandler` or the generic
  installation package;
- allow `PreparedInstallation` to retain one handler-owned trusted prepared-state instance;
- keep the existing Phase B constructor and behavior valid for old tests/callers;
- never accept an arbitrary raw `Object`;
- never retain a Context, Service, scheduler, renderer, network client, credential, secret or store
  inside prepared state;
- prepared state is memory-only and is never serialized by `InstallationStore`;
- `encodeForCache` still returns only the canonical bounded `InstallRequest`;
- no generic class may import the concrete Video prepared-state implementation.

A Video prepared state may contain the already parsed immutable/bounded:

- `ScheduledTrack`;
- optional `OverlayManifest`.

Do not reparse canonical JSON during ARM merely because the Phase B placeholder lacked this slot.

## 9. Static registry composition

Create one deterministic static Video registry/composition containing exactly the two handlers.

Requirements:

- exactly two entries;
- unique handler ids;
- unique codec ids;
- handler id matches the Phase C durable compatibility identity;
- codec matches the Phase B capability identity;
- no lazy discovery;
- no reflection;
- no handler construction during lookup;
- no network or Android Context required to obtain the registry;
- stable sorted registry metadata as already guaranteed by
  `InstallationHandlerRegistry`.

The existing generic `InstallationHandlerRegistry.empty()` may remain for compatibility/tests.
Do not silently make unrelated generic code auto-wire Video.

No production caller uses this registry as the installation orchestrator until a later phase.

## 10. ARM contract and Video runtime ports

Both handlers must implement an executable `arm(...)` contract for deterministic tests and future
PackageInstaller use.

Do not put Android `Looper`, WindowManager, Service or renderer classes into the generic
`InstallationHandler.RuntimePorts` type.

Define a Video-side runtime-port contract that extends `InstallationHandler.RuntimePorts` and
exposes only the bounded operations needed to preserve the current runtime semantics.

The exact method names are implementation choices, but the contract must be able to represent:

- owner-thread eligibility/check;
- loading the already prepared `ScheduledTrack`;
- arming/replacing the manifested `SceneRuntimeController` revision;
- disarming/unloading the manifested controller for legacy takeover;
- synchronously retiring the opposite visual owner before the new revision is considered active;
- selecting/recording the active revision only after successful activation.

No method may send an ACK or perform network I/O.

### 10.1 Manifested ARM

For a valid prepared manifested installation, ARM must ensure:

- it runs only through an accepted Video runtime-port implementation;
- owner-thread requirement is honored;
- previous legacy visual ownership is synchronously retired;
- scheduler receives the already prepared track;
- controller receives the already prepared manifest for the exact revision;
- the active revision is selected only after the activation steps succeed;
- success returns `ARMED`;
- any bad ports, wrong prepared state or activation failure returns `ARM_FAILED`;
- no second visual owner remains active after a failure.

### 10.2 Legacy ARM

For a valid prepared legacy installation, ARM must ensure:

- owner-thread requirement is honored;
- any manifested visual/controller ownership is synchronously retired/disarmed;
- scheduler receives the already prepared track;
- the exact legacy revision is selected only after successful activation;
- success returns `ARMED`;
- failure returns `ARM_FAILED`;
- no manifested scene can remain visible concurrently with the legacy owner.

Phase D does not authorize a real OverlayService cutover to these runtime ports. The contract must be
fully unit-testable with deterministic fakes.

## 11. encodeForCache / restoreFromCache

For both handlers:

`validate(request, capabilities)`

must be pure and return only a bounded `InstallationStatus`.

`prepare(request, capabilities)`

must create one immutable `PreparedInstallation` containing:

- exact revision;
- exact codec;
- exact canonical artifact bytes;
- exact handler id;
- derived requirements;
- only bounded non-content scalar metadata if useful;
- trusted handler-owned prepared state.

`encodeForCache(prepared)`

must:

- verify the prepared installation belongs to that handler/codec;
- return the exact canonical bounded request;
- perform no store write;
- preserve artifact bytes exactly.

`restoreFromCache(snapshot, capabilities)`

must:

- treat the snapshot as untrusted durable bytes;
- run the same semantic parsing and capability checks as live prepare;
- produce the same trusted prepared state;
- perform no store write and no ARM;
- fail closed on corruption/unsupported capability.

Do not trust handler id alone as semantic validation.

## 12. Move Video semantic ownership out of CloudTrackRepository

Phase D must remove direct ownership of Video parsing/cross-contract rules from
`CloudTrackRepository` as far as possible without introducing Phase E orchestration.

In particular, after Phase D:

- `CloudTrackRepository` must not call
  `VideoOverlayManifestBridge.validate(...)` directly;
- the manifested Video handler must be the owner of that bridge call;
- duplicated cross-contract logic outside the handler is forbidden;
- the repository's existing production method signatures must remain compatible;
- current revision/staleness rules, historical persistence sequence, scheduler/load ordering,
  restore behavior and bounded legacy results must remain characterized.

A narrow transitional adapter from the repository's existing String APIs into the handlers is
allowed.

The repository may continue, in Phase D, to own its historical revision/persistence orchestration
because Phase E — not Phase D — introduces the one generic PackageInstaller.

If the repository needs the parsed prepared track/manifest after persistence to preserve the current
scheduler/restore sequence, obtain them through a narrow Video-handler-owned typed accessor/facade;
do not reparse the raw JSON independently in the repository.

### 12.1 Preserve current bounded diagnostics

Current `CloudTrackRepository.InstallResult` distinguishes at least:

- `MANIFEST_INVALID`;
- `MANIFEST_INCONSISTENT`;
- `MANIFEST_CACHE_FAILED`;
- `NONE`.

Do not regress those current caller-visible bounded outcomes.

The manifested Video handler may therefore expose a Video-specific bounded evaluation result in
addition to the generic `InstallationHandler` methods, provided:

- it carries no payload/content;
- it is owned by the handler;
- generic code does not depend on `RuntimeDiagnostics.ManifestCode`;
- the generic handler contract still returns the closed `InstallationStatus` vocabulary.

## 13. Current production orchestration must not jump ahead

Phase D may refactor `CloudTrackRepository` to delegate Video semantic work to the handlers, but
must **not** introduce the Phase E/F architecture early.

Do not:

- add `PackageInstaller`;
- move revision/redelivery authority into a new orchestrator;
- make `CloudControlClient` construct `InstallRequest`;
- make `CloudControlClient` lookup handlers;
- replace `CloudControlClient.ManifestInstaller`;
- change ACK order or wire body;
- switch OverlayService reboot to generic store + registry restore;
- delete historical cache keys;
- make the generic store the current production commit path;
- route current Cloud assignments through the static registry as a generic installer;
- remove the current compatibility repository APIs.

The present production chain remains compatible while semantic ownership is extracted.

## 14. Mandatory handler rejection matrix

Add deterministic tests proving at minimum:

### Manifested handler

1. known valid manifested fixture validates and prepares;
2. exact `runtime` + `manifest` artifact names required;
3. missing/extra/unknown artifact rejected;
4. wrong codec rejected;
5. malformed strict UTF-8 rejected;
6. runtime UTF-16 bound retained;
7. manifest UTF-16 bound retained;
8. wrong runtime type rejected;
9. missing/invalid comments rejected;
10. any Cloud-text runtime comment carrying a `media` field rejected;
11. invalid media identity rejected as the current parser does;
12. malformed manifest JSON rejected;
13. non-Video product rejected;
14. sourceId/trackId mismatch rejected;
15. missing/extra/duplicate scene relation rejected;
16. exact timing drift rejected;
17. wall clock returns `UNSUPPORTED_CAPABILITY`;
18. manifested `continue` returns `UNSUPPORTED_CAPABILITY` on the generic handler contract;
19. valid MEDIA/FREEZE derives the exact Phase B execution requirements;
20. validation/prepare writes nothing and changes no scheduler/controller/renderer state;
21. encodeForCache preserves exact artifact bytes;
22. restoreFromCache revalidates and rebuilds trusted prepared state;
23. no raw content appears in errors/diagnostics.

### Legacy handler

24. known valid legacy fixture validates and prepares;
25. manifest/extra artifact rejected;
26. malformed strict UTF-8 rejected;
27. invalid runtime/comment/media rejected identically to the compatibility parser;
28. FREEZE derived when `pauseFreezesDisplay=true`;
29. CONTINUE derived when false/absent per the current parser behavior;
30. exact comment count becomes timedSceneCount;
31. encodeForCache preserves exact bytes;
32. restoreFromCache revalidates;
33. no OverlayManifest parser/bridge dependency in the legacy handler.

## 15. Mandatory ARM tests

With deterministic Video runtime-port fakes, prove:

1. manifested ARM uses the prepared state and does not reparse artifacts;
2. manifested ARM retires legacy ownership before selecting the new active revision;
3. scheduler receives exactly the prepared track;
4. controller receives exactly the prepared revision/manifest;
5. legacy ARM disarms manifested ownership before selecting legacy active revision;
6. wrong RuntimePorts type -> `ARM_FAILED`;
7. wrong handler/prepared-state pairing -> `ARM_FAILED`;
8. non-owner-thread port -> `ARM_FAILED`;
9. a failure before final selection never reports `ARMED`;
10. failure never invokes ACK/network logic;
11. at most one visual owner is active through replacement;
12. stale callback/generation protections in existing controller tests remain green.

Do not invent a second clock.

## 16. Differential / frozen-baseline proof

Retain and rerun all existing tests.

Add a differential matrix that compares the new handler-owned semantic decision with the qualified
historical behavior on the frozen supported Video profiles.

At minimum include:

- frozen manifested cache fixture;
- frozen legacy cache fixture;
- valid manifested M1 envelope projection;
- malformed runtime;
- runtime media field;
- malformed manifest;
- source mismatch;
- scene-set mismatch;
- timing mismatch;
- legacy no-manifest case.

For cases where the new generic capability contract deliberately rejects a parser-valid but
non-executable profile that the historical path may have accepted, record that as an explicit
capability tightening **without changing current production behavior in this phase**.

## 17. Import/boundary gates

Add/update automated Python boundary tests proving:

1. generic installation package still imports no Video/Prime/scheduler/controller/renderer/network
   classes;
2. manifested handler is the only new generic-handler path that references
   `VideoOverlayManifestBridge`;
3. legacy handler does not reference OverlayManifest parser/bridge;
4. handlers import no network API, CloudControlClient, credentials, FinalTrack/pipeline or
   Android View/WindowManager;
5. static registry composition is explicit and exactly two entries;
6. no reflection/dynamic loading/discovery;
7. no `PackageInstaller` exists yet;
8. current CloudControlClient does not construct `InstallRequest` or use the registry;
9. Phase C store/backend responsibilities remain unchanged;
10. protected manifest/signing/application/configuration blobs remain unchanged unless an exact
    Phase D test-accounting change is explicitly required.

## 18. Frozen fixtures and hashes

Preserve these exact files and SHA-256 values:

- M1 Cloud envelopes:
  `19ee6fc3e2eba7009c9d345c33a63ca5617621fac9c8f5618cb24ee2932d6eab`
- OverlayManifest 94-case corpus:
  `c78addf877b277e38f66c12274e4c8ed2ce12a503d883e27fd9950215cafbf30`
- manifested cache fixture:
  `76d109c4b69f3e707912449f87e3fa6b9081e4564a12c77c633ae7ed7563beb1`
- legacy cache fixture:
  `1676ed2033ec0e589509da7bdab3294ed8b5949153eeb2cfdb539aa23409cc8c`

Do not rewrite fixtures to make new tests pass.

## 19. Baseline and CI accounting

Phase D starts from:

- retained pre-Phase-A JVM: 178 PASS / 0 FAIL / 1 SKIP;
- Phase A: 30 PASS;
- Phase B: 39 PASS;
- Phase C: 43 PASS;
- full JVM: **290 PASS / 0 FAIL / 1 SKIP**;
- Python: **24 PASS / 0 FAIL / 0 SKIP**.

Add an explicit Phase D test bucket to the existing real-Gradle-XML summary flow. Do not collapse or
rename earlier buckets in a way that loses provenance.

After implementation and every localized correction, require on the exact pushed HEAD:

- all Python boundary tests PASS;
- full Gradle JVM suite PASS with only the known private SKIP;
- `lintDebug` PASS;
- `assembleDebug` PASS;
- LAN DEV compile PASS;
- Consumer Cloud-origin compile PASS;
- stable signing/certificate continuity PASS;
- Android 15 / API 35 standard-platform smoke SUCCESS.

Inspect exact-head workflow metadata, jobs, steps and relevant logs, not only a PR badge.

Stable certificate expected:
`f908bf564ed97ba67e02b1ebc89eb0239cf980752587f55eb9ec0419791a2e9c`.

Do not claim Sony physical qualification from JVM/CI/emulator evidence.

## 20. Protected non-regression surface

Preserve:

- Cloud v1 envelope fields;
- ACK body and order;
- `finalTrackId` transport binding;
- current revision/redelivery semantics;
- historical cache bytes and ACK semantics;
- scheduler media-time behavior;
- pause/seek/replay behavior;
- SceneRuntimeController generation guard;
- one-visual-owner guarantee;
- owner-thread window mutation;
- pairing / installation identity / Cloud credentials;
- disconnect/reset semantics;
- application id/package name;
- Android permissions;
- foreground-service type;
- signing chain;
- frozen fixtures;
- production revision authority **SHADOW**.

Do not remove existing Javadocs/docstrings.

## 21. Explicitly forbidden in Phase D

Do not implement:

- `PackageInstaller`;
- generic validate -> prepare -> commit -> arm orchestration authority;
- generic revision/stale/redelivery orchestration;
- Cloud v1 -> InstallRequest adapter;
- CloudControlClient generic-installer cutover;
- generic reboot/restore cutover;
- generic diagnostics cleanup/renaming;
- deletion of legacy/historical cache fields;
- Banner handler;
- Language handler;
- wall-clock runtime;
- SceneEvent / M5 scheduler;
- remote/CDN acquisition;
- M7 shared asset cache;
- WebView;
- dynamic plugins/code;
- new Android permissions;
- Cloud repository changes;
- Production cutover;
- PR merge.

No Phase E/F/G or M5+ work.

## 22. Documentation/code quality rules

Do not regress code.

Do not remove existing docstrings/Javadocs.

Every new non-trivial class, constructor, method and helper must have a concise pedagogical
Javadoc/docstring explaining its invariant and side effects.

For any new or modified Python file:

- preserve existing docstrings;
- include the pedagogical module banner docstring after imports when required by the project rule;
- every function, including utilities, must have a docstring.

Keep changes narrow. Do not reformat unrelated files.

## 23. Git / PR policy

Work only on:

`work/scenevibe-os-m4-tv-installation-001`

PR #14 remains DRAFT.

Use normal commits and normal push only.

Do not:

- merge;
- rebase;
- force-push;
- change TV `main`;
- change Cloud `main`;
- retarget the PR;
- mark the PR ready for review.

Recommended commit shape:

1. Video prepared-state support + manifested/legacy handlers + static registry;
2. CloudTrackRepository semantic delegation + Phase D tests/boundaries/CI accounting;
3. Phase D report.

A small deterministic self-audit correction may add another narrow commit.

## 24. WORK policy — implement, self-audit, fix localized defects

This is an implementation cycle.

After implementation:

1. self-audit the exact diff against the M4 architecture and this work order;
2. prove no parser/bridge semantic duplication remains where Phase D requires handler ownership;
3. inspect the prepared-state boundary for mutable/service/network leaks;
4. inspect ARM failure ordering for dual-owner hazards;
5. run all retained and new suites;
6. push normally;
7. inspect exact-head CI;
8. fix localized deterministic defects in the same cycle, strengthen regression tests, push and
   rerun the required gates.

STOP and report rather than guessing only if an issue:

- requires Phase E+ orchestration;
- requires a Cloud wire/product decision;
- requires changing the qualified scheduler/renderer semantics;
- makes the Phase B capability matrix inconsistent with an indispensable current production
  contract and cannot be preserved through the transitional compatibility seam;
- requires Sony physical qualification to decide correctness.

## 25. Physical qualification policy for Phase D

Do **not** claim a new Sony field qualification merely because handler tests and CI pass.

Phase D does not yet perform the final generic production cutover. The mandatory M4 Sony protocol
remains for the later integrated M4 runtime after the generic installation path is actually wired.

If this Phase D implementation unexpectedly changes real OverlayService/CloudControlClient
orchestration rather than only semantic delegation, treat that as scope expansion and stop unless
the change is strictly required and fully justified.

## 26. Required final report

Create:

`docs/m4-phase-d-video-compatibility-handlers-report.md`

The report must include:

- exact implementation start HEAD;
- final HEAD;
- commits and changed files;
- concrete handler class names and locations;
- exact handler ids / codec ids / artifact shapes;
- static registry composition;
- any additive PreparedInstallation/prepared-state contract change;
- manifested validation pipeline;
- legacy validation pipeline;
- strict UTF-8 and bounds behavior;
- exact capability derivation;
- treatment of wall/continue unsupported profiles;
- VideoOverlayManifestBridge ownership proof;
- CloudTrackRepository semantic-delegation delta;
- proof current production orchestration/ACK/wire is not advanced to Phase E/F;
- generic validate/prepare/encode/restore behavior;
- ARM runtime-port contract and ordering proof;
- differential baseline matrix;
- JVM counts split retained / A / B / C / D;
- Python counts split retained/new;
- fixture hashes;
- exact-head Android debug run id/result;
- exact-head Android 15 run id/result;
- lint/build/signature/application/permissions continuity;
- known limits;
- explicit confirmation no PackageInstaller, Cloud adapter, generic reboot cutover, Phase E/F/G or
  M5+ work;
- PR #14 state.

The final verdict must be exactly one of:

- `READY FOR M4 PHASE E`
- `NOT READY FOR M4 PHASE E`

Do not merge.

STOP after the report and verdict.
