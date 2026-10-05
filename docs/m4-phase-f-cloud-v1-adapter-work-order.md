# SCENEVIBE OS — WORK — M4 PHASE F — Cloud v1 Adapter and Live Assignment Cutover

Date: 2026-10-04  
Repository: `pierre22400/scenevibe-tv-companion`  
Branch: `work/scenevibe-os-m4-tv-installation-001`  
PR: #14 — must remain **OPEN / DRAFT / unmerged**.

## 1. Mission

Implement **M4 Phase F only**: cut the existing Cloud v1 live-assignment path over to the
generic Phase E installation pipeline while preserving the qualified Cloud wire/auth/ACK contract.

The Phase F production flow must become:

```text
Cloud HTTPS / scenevibe.cloud.assignment.v1
        |
        v
Cloud v1 compatibility adapter
        |
        v
InstallRequest
        |
        v
AssignmentMutationGate / Android owner thread
        |
        v
PackageInstaller
        |
        v
registered Video handler + Video runtime ports
        |
        v
ARMED
        |
        v
Cloud ACK using original finalTrackId
        |
        v
server-confirmed InstallationStore.markAcknowledged(revision)
```

Phase F must remove the live Cloud assignment path's independent manifested/legacy installation
orchestration. The generic `PackageInstaller` becomes the sole revision/install authority for
new Cloud v1 assignments and redelivery.

ACK remains outside `PackageInstaller`.

Phase F is **not** the reboot/boot restore, diagnostics terminology cleanup, historical-key cleanup,
Sony final qualification, Banner, wall-clock or M5+ phase.

The authoritative architecture is:

- `docs/scenevibe-os-m4-tv-installation-architecture.md`

The immediately preceding closure is:

- `docs/m4-phase-e-package-installer-orchestration-report.md`

Phase E final closure HEAD before this documentation commit:

- `f4b2007245b7ecc39f8cabb1e8d36945d0650d4e`

Frozen milestone bases remain:

- `BASE_TV_M4 = ecdf77bec9f93babf15239a63bf7f702fd7ca293`
- `BASE_CLOUD_M4 = 5011c91aac61a0cc6dcc74c256a15b7dee03d785`

Production revision authority remains **SHADOW**.

This work-order commit is documentation-only. Before implementation, verify that the branch HEAD
is exactly the commit containing this file and that its ancestry contains the Phase E closure HEAD
above. Do not reset to the Phase E SHA and discard this work order.

## 2. Read completely before editing

Read in full, from the exact branch HEAD:

1. `docs/scenevibe-os-m4-tv-installation-architecture.md`
2. this work order
3. `docs/m4-phase-e-package-installer-orchestration-report.md`
4. `docs/m4-phase-d-video-compatibility-handlers-report.md`
5. `docs/m4-phase-c-generic-durable-store-report.md`
6. `docs/m4-phase-b-generic-model-capabilities-report.md`
7. `docs/m4-phase-a-characterization-report.md`
8. `app/src/main/java/com/scenevibe/tvcompanionpoc/CloudControlClient.java`
9. `app/src/main/java/com/scenevibe/tvcompanionpoc/CloudProtocol.java`
10. `app/src/main/java/com/scenevibe/tvcompanionpoc/AssignmentMutationGate.java`
11. `app/src/main/java/com/scenevibe/tvcompanionpoc/OverlayService.java`
12. `app/src/main/java/com/scenevibe/tvcompanionpoc/CloudTrackRepository.java`
13. all current Phase D Video handler/runtime-port classes
14. all current generic installation classes, especially:
    - `PackageInstaller`
    - `InstallationStore`
    - `InstallRequest`
    - `InstallationHandlerRegistry`
    - `InstallationStatus`
    - `TvCapabilities`

GitHub is the source of truth.

## 3. Independently audited Phase E starting truth

Phase E has been independently audited after the Work cycle.

Exact closure:

- Phase E work-order HEAD: `bfaf24c6d4a5ac49f256b8e15c49f4d9e34f565b`;
- generic installer commit: `c9a52f728f02d857b783ce1f58af7bc9dca6e266`;
- integration/boundary commit: `3d9145669842f0d92f3bbc188b72b78ae8d9541e`;
- final report HEAD: `f4b2007245b7ecc39f8cabb1e8d36945d0650d4e`;
- PR #14: OPEN / DRAFT / MERGEABLE / unmerged;
- TV main remains `ecdf77bec9f93babf15239a63bf7f702fd7ca293`;
- Cloud main remains `5011c91aac61a0cc6dcc74c256a15b7dee03d785`.

Exact final-head CI:

- Android debug workflow `37231602832`: SUCCESS;
- Android 15 standard-platform smoke `37231602848`: SUCCESS;
- Python: **42 PASS / 0 FAIL / 0 SKIP**;
- JVM: **488 PASS / 0 FAIL / 1 private SKIP**, 489 total;
- Phase E adds 115 JVM cases;
- stable signing certificate:
  `f908bf564ed97ba67e02b1ebc89eb0239cf980752587f55eb9ec0419791a2e9c`.

The sole private SKIP remains:

`M1CloudInteropTest.originalColumboProjectionIsInstallable`

via `SCENEVIBE_M1_SONY_FIXTURES`.

No Phase E corrective commit is required before Phase F.

## 4. Known compatibility edge — explicit Phase F policy

Phases D/E intentionally characterized one historical edge:

- the old Cloud v1 manifested path accepted parser-valid `MEDIA/CONTINUE`;
- the canonical generic executable capability model supports manifested `MEDIA/FREEZE` only;
- the generic handler therefore returns `UNSUPPORTED_CAPABILITY` for manifested
  `MEDIA/CONTINUE`.

Phase F is the first authorized live Cloud cutover to the canonical generic capability truth.

Therefore:

- the Cloud v1 **wire envelope remains valid/compatible**;
- a parser/wire-valid manifested `MEDIA/CONTINUE` assignment may pass the Cloud v1 adapter;
- `PackageInstaller` must return `UNSUPPORTED_CAPABILITY`;
- the prior durable/runtime revision remains unchanged;
- no ACK is sent;
- do **not** misclassify this as malformed transport/PROTOCOL;
- document this as the already-known local capability tightening, not an accidental regression.

Do not weaken `TvCapabilities` or the Phase D manifested handler merely to keep the old accidental
acceptance.

All frozen/qualified supported Sony profiles remain expected to be MEDIA/FREEZE and must remain
byte/wire compatible.

## 5. Required Cloud v1 compatibility adapter

Introduce a dedicated static/bounded adapter in the Video/Cloud compatibility package, preferred
name:

`CloudV1InstallationAdapter`

It is not generic installation core.

It owns only the historical Cloud v1 envelope-to-installation mapping:

- `scenevibe.cloud.assignment.v1`;
- `deviceId` binding;
- revision extraction;
- `finalTrackId`;
- `trackId`;
- Prime/video target checks required by the frozen v1 contract;
- runtimeTrack presence/shape needed by v1;
- optional OverlayManifest presence/shape needed by v1;
- mapping to one of the two existing static codec ids;
- deterministic UTF-8 artifact bytes using the same JSON serialization form used by the current
  production path.

The adapter must output an immutable bounded value such as:

- canonical `InstallRequest`;
- exact `finalTrackId` needed for the ACK;
- revision if convenient.

The adapter must not output or retain:

- device token;
- activation secret;
- HTTP client;
- Context/Service;
- scheduler/renderer/controller;
- PackageInstaller;
- arbitrary exception text.

The generic `InstallRequest` must never contain `finalTrackId`.

### 5.1 Preserve CloudProtocol wire truth

Do not duplicate the frozen Cloud envelope rules by hand if they can remain delegated to
`CloudProtocol`.

A narrow refactor of `CloudProtocol.validAssignment` is allowed if necessary, but preserve all
currently qualified envelope acceptance/rejection semantics and the seven frozen M1 envelopes.

The Cloud v1 adapter must not call `VideoOverlayManifestBridge` directly.

If current bounded Manifest diagnostics require semantic preflight, delegate through the existing
Video handler-owned compatibility seam; do not duplicate bridge logic.

## 6. CloudControlClient target responsibilities

After Phase F, `CloudControlClient` should own:

- HTTPS transport;
- activation/auth lifecycle;
- polling/backoff;
- bounded transport diagnostics;
- Cloud v1 envelope adaptation;
- dispatch onto `AssignmentMutationGate`;
- invocation of `PackageInstaller`;
- ACK HTTP request after local `ARMED`;
- persistence of the server-confirmed ACK through `InstallationStore.markAcknowledged`;
- reset/account lifecycle coordination.

It must no longer own or directly invoke:

- `MediaSyncedTrackScheduler`;
- manifested-vs-legacy revision branching;
- `CloudTrackRepository.install/restore`;
- `ManifestInstaller.install`;
- `ManifestInstaller.confirmArmed`;
- `ManifestInstaller.activateLegacy`;
- `VideoOverlayManifestBridge`;
- OverlayManifest parsing;
- scheduler.load;
- SceneRuntimeController;
- renderer/window classes.

The network client may depend on generic:

- `InstallationStore`;
- `PackageInstaller`;
- `InstallationHandler.RuntimePorts`;
- `InstallationStatus`;

and on the Cloud v1 compatibility adapter.

## 7. Owner-thread invariant

The existing `AssignmentMutationGate` remains the mandatory owner-thread boundary.

For one valid Cloud assignment:

1. network fetch/parse stays on Cloud io;
2. adapter creates the inert bounded `InstallRequest` off the UI thread;
3. `mutations.call(...)` transfers installation to the Android owner thread;
4. after dispatch, re-check that this Cloud client is still the current/running instance;
5. call exactly one `PackageInstaller.install(request, runtimePorts)`;
6. synchronously wait for the result;
7. return to Cloud io;
8. ACK only when result is exactly `ARMED`.

Posting work without waiting is never sufficient to ACK.

If reset/stop invalidates the client before the queued owner mutation executes:

- do not install;
- do not arm;
- do not ACK.

Preserve the existing interrupted-wait/cancel behavior of `AssignmentMutationGate`.

## 8. Real OverlayService Video runtime ports

Wire the already-qualified Phase D `VideoInstallationRuntimePorts` to the existing live
`OverlayService` runtime.

Prefer one narrow private/static adapter or inner object rather than spreading handler-specific
logic through CloudControlClient.

The production runtime ports must implement the Phase D semantics using the existing objects:

### 8.1 isOwnerThread

Return true only on the Android window owner/main thread used by existing overlay mutations.

### 8.2 retireLegacyVisualOwner

Synchronously dismiss any current legacy `OverlayRenderer` visual so it cannot overlap the next
manifested owner.

Do not create a new renderer.

### 8.3 retireManifestedVisualOwner

Synchronously:

- unload the existing `SceneRuntimeController`;
- invalidate its generation;
- dismiss any current SceneRenderer visual immediately.

Do not touch durable storage.

### 8.4 loadPreparedTrack

Load exactly the already prepared `ScheduledTrack` into the existing
`MediaSyncedTrackScheduler`.

No reparse.

### 8.5 armPreparedManifest

For manifested packages only:

- require existing controller/runtime availability;
- load/replace exactly the prepared revision + manifest;
- no persistence;
- no ACK.

### 8.6 selectActiveRevision

Set the service's active visual revision only after all previous handler ARM operations succeeded.

For manifested selection, require the controller to hold the exact revision.

For legacy selection, require the manifested controller to be disarmed.

### 8.7 abortActivation

Idempotently retire partial visual/runtime ownership and clear the service active selection, while
leaving the newly committed durable PackageInstaller revision pending.

Do not rollback storage.

No runtime-port method may send network traffic or persist an ACK.

## 9. PackageInstaller composition in OverlayService

Production composition must create/use exactly one generic installation stack for the live service:

- one `AndroidInstallationBackend`;
- one `InstallationStore`;
- one `PackageInstaller`;
- `VideoInstallationHandlers.registry()`;
- `TvCapabilities.current()`;
- one live Video runtime-ports adapter.

Do not create one PackageInstaller per assignment.

All live assignment installation calls must flow through this one service-owned composition and the
owner-thread gate.

This also closes the Phase E multi-instance concurrency seam: production runtime and store
installation are serialized by one owner-thread composition.

## 10. Generic durable revision / ACK source in CloudControlClient

After Phase F generic commits, the historical `CloudTrackRepository.revision()` /
`acknowledged()` view is not authoritative.

The Cloud client must obtain:

- current durable revision;
- acknowledged revision;

from `InstallationStore.read()`.

Required behavior:

- EMPTY -> revision 0 / ACK 0;
- SNAPSHOT -> exact snapshot revision + exact acknowledged revision;
- CORRUPT -> fail closed; do not fetch/install/overwrite/ACK as if empty.

A corrupt durable installation must never be converted into `afterRevision=0` and overwritten by a
new assignment.

Use only bounded diagnostics on corruption.

## 11. Same-revision and stale Cloud behavior

The Cloud adapter/client must not reimplement the generic revision policy.

It may supply current durable revision to the frozen v1 envelope validator when required, but after
a valid adapted assignment:

- `PackageInstaller` alone decides STALE / same / newer;
- no second revision branch in CloudControlClient;
- no direct same-revision restore logic in the client/service.

If PackageInstaller returns:

- `ARMED` -> ACK may proceed;
- `STALE` -> no ACK for that response and no mutation;
- `UNSUPPORTED_CAPABILITY` -> no ACK;
- `INVALID_PACKAGE` -> no ACK;
- `CACHE_FAILED` -> no ACK;
- `ARM_FAILED` -> no ACK.

Do not treat any result except exact `ARMED` as ACK-eligible.

## 12. ACK contract — preserve byte/field compatibility

The ACK request remains exactly:

```json
{
  "revision": <revision>,
  "finalTrackId": "<the assignment finalTrackId>"
}
```

Do not replace `finalTrackId` with `trackId`.

Do not add fields.

Sequence:

1. adapted valid assignment;
2. owner-thread PackageInstaller result == ARMED;
3. POST historical ACK body;
4. validate the existing server ACK response with `CloudProtocol.validAck`;
5. only after server confirmation call
   `InstallationStore.markAcknowledged(revision)`;
6. only after that publish `lastSuccessfulAckRevision`.

If HTTP ACK fails or confirmation is malformed:

- local durable/armed revision remains pending;
- acknowledged revision remains the previous value;
- later polling/redelivery may re-arm same revision and retry ACK.

If local ACK persistence fails after server confirmation:

- report bounded failure;
- do not manufacture local confirmation;
- next poll may retry.

PackageInstaller itself remains completely ACK-free.

## 13. Reset semantics

Preserve the current exceptional Reset Cloud behavior:

- running=false synchronously;
- rotate installation identity;
- clear Cloud credentials;
- clear the entire installed-package durable state and acknowledged revision;
- clear the in-memory scheduler/runtime visual ownership on the Android owner thread;
- reset bounded Cloud observations;
- shut down the spent Cloud executor;
- allow later reconstruction of a fresh client;
- do not block the Android main thread.

Prefer an additive generic `InstallationStore.clearAll()` if a generic clear API is required.

If added:

- it must atomically clear the same app-private installation preference file;
- it must be bounded/non-networked;
- existing `clearHistorical()` must remain compatible, preferably delegating to the same primitive;
- add direct Phase C/E regression tests;
- do not rename/delete historical APIs yet.

Do not keep `CloudTrackRepository` in the new live assignment path merely as a reset shortcut.

A narrow service-owned runtime reset callback/port is allowed if needed so CloudControlClient does
not regain scheduler/renderer knowledge.

## 14. Transitional Phase F limitations — deliberate

Phase F cuts over **live Cloud assignment installation**, but Phase G still owns:

- service-start / boot / hard-reboot generic restore;
- final diagnostics migration from "cached track" to installation/package truth;
- compatibility display of old diagnostics fields against generic snapshots;
- removal/cleanup of obsolete historical restore/install helpers and seams;
- old-key cleanup/migration policy.

Therefore Phase F must not claim reboot continuity through the generic path.

The existing boot/diagnostics code may remain temporarily historical in this branch, but:

- do not use it for new live assignment authority;
- document the temporary mismatch;
- Phase F must remain DRAFT/unmerged;
- Phase G is mandatory before M4 completion/merge.

No Sony final qualification is claimed in Phase F.

## 15. Bounded diagnostics compatibility

Preserve current Cloud error codes and do not expose content.

For live v1 assignments:

- malformed/auth/wire invalid -> existing bounded Cloud PROTOCOL/UNAUTHORIZED behavior;
- generic unsupported capability -> local install refusal, not malformed transport;
- generic cache failure -> bounded local installation failure; do not expose raw store exception;
- ARM failure -> bounded local installation failure;
- successful ACK -> clear prior Cloud error as before.

Preserve existing `RuntimeDiagnostics.ManifestCode` behavior as closely as possible without
duplicating Video bridge rules.

For manifested Cloud v1 compatibility diagnostics, a narrow handler-owned compatibility evaluation
is allowed so:

- `MANIFEST_INVALID`;
- `MANIFEST_INCONSISTENT`;
- `MANIFEST_CACHE_FAILED`;
- `NONE`

do not silently collapse if current tests depend on them.

The adapter/client/service must not call `VideoOverlayManifestBridge` directly; the Phase D
manifested handler remains the sole bridge owner.

Do not start Phase G terminology redesign.

## 16. Mandatory Cloud adapter tests

Add deterministic tests proving at minimum:

1. each of the seven frozen M1 assignment envelopes adapts with the same acceptance decision;
2. exact revision preserved;
3. exact `finalTrackId` preserved outside InstallRequest;
4. exact `trackId`/runtime binding preserved;
5. no-manifest -> `scenevibe.runtime-track.v1`;
6. manifested -> `scenevibe.runtime-track-overlay.v1`;
7. exact UTF-8 bytes use the same current JSON serialization as production;
8. wrong type/device/revision/trackId/targetPackage/media identity remains rejected;
9. malformed manifest envelope remains rejected as before;
10. adapter has no network/runtime/store/credentials dependency;
11. adapter never calls VideoOverlayManifestBridge directly;
12. adapter carries no secret.

## 17. Mandatory CloudControlClient / ACK-order tests

Retain all existing tests and add/replace deterministic tests proving:

1. valid assignment adapts off-owner-thread then invokes PackageInstaller once on owner thread;
2. owner-thread completion is awaited before ACK;
3. PackageInstaller ARMED -> exact historical ACK body;
4. every non-ARMED InstallationStatus -> zero ACK HTTP request;
5. PackageInstaller exception -> zero ACK;
6. server ACK rejection -> acknowledged revision unchanged;
7. malformed ACK response -> acknowledged revision unchanged;
8. server-confirmed ACK -> exact `InstallationStore.markAcknowledged(revision)`;
9. local ACK persistence failure remains unconfirmed locally;
10. same-revision redelivery uses PackageInstaller and zero install commit;
11. stale response cannot overwrite durable state;
12. generic store corruption prevents fetch/install overwrite;
13. query `afterRevision` uses exact generic acknowledged revision;
14. current client stopped before queued owner mutation -> no install/ACK;
15. interrupted owner wait -> queued work cancelled/no late install;
16. `finalTrackId`, never `trackId`, is ACKed;
17. no scheduler/renderer/controller/bridge call exists in CloudControlClient;
18. no old ManifestInstaller manifested/legacy branching remains.

## 18. Mandatory real service/runtime integration tests

Using deterministic Android-free seams where possible, prove:

1. real manifested v1 assignment -> adapter -> PackageInstaller -> real Video handler -> runtime ports -> ARMED;
2. real legacy v1 assignment -> same generic pipeline;
3. manifested -> newer legacy replacement preserves one visual owner;
4. legacy -> newer manifested replacement preserves one visual owner;
5. same-revision redelivery re-arms exact durable copy with no rewrite;
6. failed ARM commits pending revision but yields zero ACK eligibility;
7. later redelivery can recover pending revision and then become ACK-eligible;
8. old generation callback cannot resurrect previous manifested scene;
9. pause/resume/seek scheduler semantics remain unchanged in retained suites;
10. owner-thread guard rejects off-owner direct ARM;
11. runtime ports perform no store/network/ACK access.

## 19. Compatibility-edge tests

Explicitly test the already-characterized edge:

### Manifested MEDIA/CONTINUE

- CloudProtocol / v1 adapter still recognizes the assignment as wire-compatible if all v1 fields
  are valid;
- PackageInstaller returns `UNSUPPORTED_CAPABILITY`;
- zero candidate commit;
- zero runtime mutation;
- zero ACK;
- prior revision/ACK remain exact;
- diagnostics do not report the envelope as malformed transport.

### Manifested WALL

Preserve current v1 structural rejection/capability behavior according to the frozen
CloudProtocol/handler boundary. No wall-clock runtime may arm.

Document exact observed classification in the Phase F report.

## 20. Historical cache live-redelivery tests

With the frozen historical caches:

- manifested revision 13 / ACK 13;
- legacy revision 14 / ACK 13;

prove a valid same-revision Cloud v1 redelivery:

- reads generic compatibility view through InstallationStore;
- PackageInstaller resolves the durable handler;
- zero cache migration/write before ACK;
- ARMED precedes network ACK;
- server-confirmed ACK persists exact revision;
- historical runtime/manifest strings stay byte-identical;
- generic marker remains absent for same-revision historical redelivery.

For a newer assignment over historical state:

- one generic snapshot commit becomes authoritative;
- old historical residue may remain physically present;
- generic marker/snapshot wins thereafter;
- ACK remains separate and advances only after server confirmation.

Do not delete residue in Phase F.

## 21. Reset regression tests

If generic clear support is added, prove:

1. generic snapshot + ACK are removed by Reset;
2. historical snapshot + ACK are removed by Reset;
3. no unrelated preference/identity/credential store is cleared by InstallationStore itself;
4. identity rotation and credential reset stay in the existing Cloud reset flow;
5. runtime visual/scheduler clear executes on owner thread;
6. reset remains asynchronous/non-blocking on main;
7. spent Cloud client is reconstructable exactly as before.

## 22. Import / architecture boundary gates

Add a Phase F Python boundary gate proving:

1. generic installation package still contains no Cloud/Video/Android dependency beyond the already
   qualified generic types;
2. Cloud v1 adapter is outside the generic installation package;
3. Cloud v1 adapter contains no network, Context, Service, credentials, scheduler, renderer or
   controller;
4. CloudControlClient contains no `MediaSyncedTrackScheduler` field/reference after cutover;
5. CloudControlClient contains no `CloudTrackRepository` live-install dependency;
6. CloudControlClient contains no `ManifestInstaller` type/seam;
7. CloudControlClient contains no direct manifested/legacy install branching;
8. CloudControlClient contains no `VideoOverlayManifestBridge`, `TrackParser`,
   `OverlayManifestParser`, `SceneRuntimeController`, `OverlayRenderer` or
   `SceneRenderer` reference;
9. PackageInstaller remains byte-exact Phase E unless a micro-fix is strictly necessary;
10. Phase D handlers remain sole product semantic owners;
11. VideoOverlayManifestBridge.validate has exactly one production caller:
    `VideoManifestInstallationHandler`;
12. OverlayService is the only Android owner of live Video runtime ports;
13. no Phase G restore coordinator/diagnostics cleanup type appears;
14. no new permission/application/signing/config change appears;
15. no Cloud repository change occurs.

Earlier Phase B/C/D/E gates may gain only the exact Phase F authorized inventory exceptions.

## 23. Frozen wire/runtime evidence

Preserve exact fixture hashes:

- seven M1 Cloud envelopes:
  `19ee6fc3e2eba7009c9d345c33a63ca5617621fac9c8f5618cb24ee2932d6eab`;
- OverlayManifest 94-case corpus:
  `c78addf877b277e38f66c12274e4c8ed2ce12a503d883e27fd9950215cafbf30`;
- manifested historical cache:
  `76d109c4b69f3e707912449f87e3fa6b9081e4564a12c77c633ae7ed7563beb1`;
- legacy historical cache:
  `1676ed2033ec0e589509da7bdab3294ed8b5949153eeb2cfdb539aa23409cc8c`.

Do not rewrite fixtures.

Preserve Cloud v1 ACK fields and endpoint path exactly.

## 24. Baseline and CI accounting

Phase F starts from the independently verified exact counts:

| JVM bucket | PASS | FAIL | SKIP |
| --- | ---: | ---: | ---: |
| Retained pre-Phase-A baseline | 178 | 0 | 1 |
| Phase A | 30 | 0 | 0 |
| Phase B | 39 | 0 | 0 |
| Phase C | 43 | 0 | 0 |
| Phase D | 83 | 0 | 0 |
| Phase E | 115 | 0 | 0 |
| **Current full JVM** | **488** | **0** | **1** |

Python: **42 PASS / 0 FAIL / 0 SKIP**.

Add an explicit Phase F test bucket to the actual Gradle-XML accounting.

After implementation and every localized correction, require on exact pushed HEAD:

- all Python boundary tests PASS;
- full Gradle JVM suite PASS with only the known private SKIP;
- `lintDebug` PASS;
- `assembleDebug` PASS;
- LAN DEV compile PASS;
- Consumer Cloud-origin compile PASS;
- stable signing/certificate continuity PASS;
- Android 15 / API 35 standard-platform smoke SUCCESS.

Inspect exact-head workflow/job/step/log evidence.

Do not claim Android TV/Sony qualification from standard Android CI.

## 25. Protected non-regression surface

Preserve:

- activation/auth HTTP contract;
- Cloud v1 assignment endpoint/path;
- assignment envelope fields;
- deviceId proof/binding;
- `finalTrackId` ACK binding;
- ACK endpoint/body/response validation;
- retry/backoff timing;
- credential lifecycle;
- reset identity rotation;
- offline behavior;
- scheduler media-time/pause/seek/replay semantics;
- SceneRuntimeController generation guard;
- exactly-one-visual-owner invariant;
- owner-thread Android window mutation;
- disconnect behavior;
- application id/package;
- permissions;
- foreground-service type;
- signing chain;
- frozen fixtures;
- Production **SHADOW**.

## 26. Explicitly forbidden in Phase F

Do not implement:

- generic boot/reboot restore cutover;
- BootReceiver generic package restoration;
- final diagnostics installation/package terminology migration;
- deletion of old diagnostics fields;
- deletion/migration cleanup of historical cache residue;
- public capabilities API;
- Cloud-side capability negotiation/targeting;
- Banner handler;
- Language handler;
- wall-clock runtime;
- SceneEvent/M5 scheduler;
- remote assets/CDN;
- shared asset cache;
- WebView;
- dynamic plugins/code;
- new Android permissions;
- Cloud repository modifications;
- Sony final M4 physical qualification claim;
- PR merge;
- Production cutover beyond this draft branch.

No Phase G implementation and no M5+ work.

## 27. Documentation/code quality rules

Do not regress existing code.

Do not remove existing Javadocs/docstrings.

Every new non-trivial class, constructor, method and helper must have a concise pedagogical
Javadoc/docstring explaining invariant and side effects.

For any new or modified Python file:

- preserve existing docstrings;
- keep/add the pedagogical module banner after imports;
- every function, including utilities, must have a docstring.

Do not reformat unrelated files.

## 28. Git / PR policy

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
- mark the PR ready.

Recommended commit shape:

1. Cloud v1 adapter + generic CloudControlClient cutover;
2. OverlayService runtime-port composition + reset compatibility;
3. Phase F tests/boundaries/accounting;
4. Phase F report.

Localized deterministic self-audit corrections may add narrow commits.

## 29. WORK policy — implement, self-audit, fix localized defects

This is an implementation cycle.

After implementation:

1. self-audit the exact diff against the architecture and this work order;
2. prove there is exactly one live revision/install authority;
3. prove no direct old manifested/legacy branch remains in CloudControlClient;
4. inspect every ACK path and prove exact ARMED-before-ACK ordering;
5. inspect finalTrackId/trackId separation;
6. inspect owner-thread dispatch and stopped-client race;
7. inspect generic store corruption handling before fetch/overwrite;
8. inspect reset after generic snapshot creation;
9. run all retained/new suites;
10. push normally;
11. inspect exact-head CI;
12. fix localized deterministic defects in the same cycle and add regression coverage.

STOP and report rather than guessing if an issue:

- requires Phase G reboot/diagnostics decisions;
- requires changing the Cloud server contract;
- requires weakening TvCapabilities to support a profile not executable by M4;
- requires changing scheduler/media semantics;
- requires Sony physical evidence to decide correctness;
- requires Cloud repository modification.

## 30. Physical qualification policy

Do not claim final Sony M4 qualification in Phase F.

Phase F changes the live Cloud installation path, so automated owner-thread and real-runtime
integration evidence is mandatory, but the architecture assigns the complete physical Sony protocol
to the integrated M4 state after Phase G.

The later Sony gate must exercise upgraded APK, cache compatibility, new send, replacement,
same-revision redelivery, seek/pause/eligibility, hard reboot, post-reboot redelivery and Unicode.

Phase F report must explicitly say physical qualification is still pending.

## 31. Required Phase F report

Create:

`docs/m4-phase-f-cloud-v1-adapter-report.md`

The report must include:

- exact implementation start HEAD;
- final HEAD;
- commits and changed files;
- exact adapter class/API;
- exact Cloud v1 -> codec/artifact mapping;
- finalTrackId isolation proof;
- owner-thread call chain;
- exact CloudControlClient dependency delta;
- proof old ManifestInstaller/legacy branching is gone;
- production PackageInstaller composition;
- real Video runtime-port adapter behavior;
- durable revision/ACK read source;
- complete ACK ordering proof;
- server ACK failure/local ACK persistence failure behavior;
- same/stale/new revision behavior through Cloud;
- known MEDIA/CONTINUE capability-tightening result;
- bounded diagnostics compatibility;
- reset behavior against generic and historical snapshots;
- historical same-revision/newer migration behavior;
- proof Phase G reboot/diagnostics work is not started;
- JVM counts split retained / A / B / C / D / E / F;
- Python counts split retained/new;
- frozen hashes;
- exact-head Android debug run id/result;
- exact-head Android 15 run id/result;
- lint/build/signing/application/permissions continuity;
- known limits;
- explicit statement that Sony physical qualification remains pending;
- PR #14 state.

The final verdict must be exactly one of:

- `READY FOR M4 PHASE G`
- `NOT READY FOR M4 PHASE G`

Do not merge.

STOP after the report and verdict.
