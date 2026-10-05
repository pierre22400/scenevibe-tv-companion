# SCENEVIBE OS — WORK — M4 PHASE G — Generic Restore, Diagnostics and M4 Software Closure

Date: 2026-10-05  
Repository: `pierre22400/scenevibe-tv-companion`  
Branch: `work/scenevibe-os-m4-tv-installation-001`  
PR: #14 — must remain **OPEN / DRAFT / unmerged**.

## 1. Mission

Implement **M4 Phase G only**: complete the TV-side M4 software architecture by replacing the
remaining historical startup/reboot and diagnostics truth with the generic installation store +
handler registry + PackageInstaller path, then remove or isolate obsolete live compatibility
orchestration.

Phase G must make the generic installation representation authoritative for:

- service/process recreation;
- boot-prepare restore;
- autostart "something to restore" detection;
- diagnostics;
- offline saved-content indication;
- exceptional reset when the service is not running.

The Phase F live Cloud assignment path remains the production live path and must not regress.

Phase G is the **last software implementation phase of M4**, but M4 is not physically complete
until the Sony protocol passes after this phase.

The required post-Phase-G state is:

```text
                    LIVE CLOUD ASSIGNMENT
Cloud v1 -> adapter -> PackageInstaller -> Video handler -> runtime ports -> ARMED -> ACK

                    PROCESS / BOOT RESTORE
InstallationStore.read()
        |
        +-- EMPTY   -> no runtime restore
        +-- CORRUPT -> fail closed, no clear/no ACK/no overwrite
        +-- SNAPSHOT
                 |
                 v
PackageInstaller same-revision restore
                 |
                 v
registered handler -> runtime ports -> ARMED
```

No Cloud network operation is required to restore the durable installation.

The authoritative architecture is:

- `docs/scenevibe-os-m4-tv-installation-architecture.md`

The immediately preceding closure is:

- `docs/m4-phase-f-cloud-v1-adapter-report.md`

Phase F final closure HEAD before this documentation commit:

- `eb0e0d823edc291bf1a39583ba70f098fac189e0`

Frozen milestone bases remain:

- `BASE_TV_M4 = ecdf77bec9f93babf15239a63bf7f702fd7ca293`
- `BASE_CLOUD_M4 = 5011c91aac61a0cc6dcc74c256a15b7dee03d785`

Production revision authority remains **SHADOW**.

This work-order commit is documentation-only. Before implementation, verify that branch HEAD is
exactly the commit containing this file and that its ancestry contains the Phase F closure HEAD.
Do not reset to the Phase F SHA and discard this work order.

## 2. Read completely before editing

Read in full, from the exact branch HEAD:

1. `docs/scenevibe-os-m4-tv-installation-architecture.md`
2. this work order
3. `docs/m4-phase-f-cloud-v1-adapter-report.md`
4. `docs/m4-phase-e-package-installer-orchestration-report.md`
5. `docs/m4-phase-d-video-compatibility-handlers-report.md`
6. `docs/m4-phase-c-generic-durable-store-report.md`
7. `docs/m4-phase-b-generic-model-capabilities-report.md`
8. `docs/m4-phase-a-characterization-report.md`
9. `app/src/main/java/com/scenevibe/tvcompanionpoc/OverlayService.java`
10. `app/src/main/java/com/scenevibe/tvcompanionpoc/BootReceiver.java`
11. `app/src/main/java/com/scenevibe/tvcompanionpoc/AutostartPolicy.java`
12. `app/src/main/java/com/scenevibe/tvcompanionpoc/RuntimeDiagnostics.java`
13. `app/src/main/java/com/scenevibe/tvcompanionpoc/DiagnosticsActivity.java`
14. `app/src/main/java/com/scenevibe/tvcompanionpoc/MainActivity.java`
15. `app/src/main/java/com/scenevibe/tvcompanionpoc/CloudControlClient.java`
16. `app/src/main/java/com/scenevibe/tvcompanionpoc/CloudTrackRepository.java`
17. all generic installation types
18. all Phase D Video handlers/runtime ports
19. all Phase F adapter/client/runtime/reset tests and boundary gates.

GitHub is the source of truth.

## 3. Independently audited Phase F starting truth

Phase F has been independently audited after Work.

Exact closure:

- Phase F work-order HEAD:
  `eb38e34086f22f81e5cc9ef917197658ce63ecbd`;
- adapter/client commit:
  `f77340df410b06aa050b5a7289577ff1daee1e1d`;
- service/reset composition commit:
  `ff5b8aad09c1a61b6740dd6410ec26b0e549b14a`;
- qualified software HEAD:
  `110608ff30fdf3a0b140dddaf72ace2fd1149eb9`;
- final Phase F report HEAD:
  `eb0e0d823edc291bf1a39583ba70f098fac189e0`.

PR #14 is OPEN / DRAFT / MERGEABLE / unmerged.

TV main remains:

`ecdf77bec9f93babf15239a63bf7f702fd7ca293`.

Cloud main remains:

`5011c91aac61a0cc6dcc74c256a15b7dee03d785`.

Exact final-head CI:

- Android debug workflow `37238480276`: SUCCESS;
- Android 15 standard-platform smoke `37238480293`: SUCCESS;
- Python: **58 PASS / 0 FAIL / 0 SKIP**;
- JVM: **626 PASS / 0 FAIL / 1 private SKIP**, 627 total;
- Phase F adds 138 JVM cases;
- stable signing certificate:
  `f908bf564ed97ba67e02b1ebc89eb0239cf980752587f55eb9ec0419791a2e9c`.

The sole private SKIP remains:

`M1CloudInteropTest.originalColumboProjectionIsInstallable`

via `SCENEVIBE_M1_SONY_FIXTURES`.

Independent audit found no deterministic Phase F defect requiring a correction.

## 4. Exact remaining pre-G mismatch

Phase F deliberately cut over **live Cloud installation only**.

At Phase F closure:

- live Cloud uses `InstallationStore + PackageInstaller + Video handlers`;
- a generic snapshot is authoritative after a newer live assignment;
- historical residue can remain physically present but is no longer authoritative;
- `OverlayService` startup still tries
  `CloudTrackRepository.restoreWithManifest/restore`;
- `BootReceiver` still detects cache through
  `new CloudTrackRepository(app).revision() > 0`;
- `RuntimeDiagnostics.capture` still uses `CloudTrackRepository`;
- `MainActivity.refreshCloud` still uses `CloudTrackRepository.revision()`;
- stopped-service Diagnostics Reset still calls `CloudTrackRepository.clear()`;
- old static service install/confirm/legacy helpers remain as dead compatibility code.

Because `InstallationStore.historicalValue()` intentionally yields no historical authority when a
generic snapshot exists, these historical views may report "nothing cached" even while a valid
generic installation is durable.

That is the exact Phase G target. Do not treat it as a Phase F regression.

## 5. Generic service-start restore — required design

Do **not** introduce a second restore orchestrator and do not duplicate handler lookup logic.

Use the already-qualified same-revision semantics of `PackageInstaller`.

Preferred production algorithm after the service has created:

- scheduler;
- SceneRuntimeController;
- InstallationStore;
- PackageInstaller;
- LiveVideoRuntimePorts;

is:

```text
ReadResult durable = installationStore.read()

EMPTY:
    do nothing; activeRevision remains 0

CORRUPT:
    fail closed;
    do not clear;
    do not arm;
    do not ACK;
    do not reinterpret as EMPTY

SNAPSHOT:
    PackageInstaller.install(
        durable.snapshot().canonical(),
        videoRuntimePorts
    )
```

The request passed above is inert. Because its revision equals the durable revision,
`PackageInstaller` must take its already-qualified same-revision path and restore the exact durable
snapshot through the stored handler id / codec binding.

The service must never independently parse the snapshot or infer a handler from artifact names.

### 5.1 No extra PackageInstaller API unless genuinely necessary

Prefer reusing `install(...)` as described above.

Do **not** add `restoreCurrent()`, a new restore coordinator or a second generic result vocabulary
merely for aesthetics.

If implementation demonstrates that a small PackageInstaller API addition is strictly required,
stop and justify it before changing the Phase E core contract.

### 5.2 Startup result behavior

For a durable snapshot:

- exact `ARMED` means the runtime is armed from durable bytes;
- any other result means no active revision;
- no store write;
- no ACK;
- no revision change;
- no Cloud Send;
- no rollback.

The runtime ports already perform bounded abort cleanup on ARM failure.

### 5.3 Ordering

Generic durable restore must complete before:

- the MediaSession probe can produce a due event against an unarmed runtime;
- the Cloud client begins polling and can process redelivery.

Preserve the current service's owner-thread serialization.

## 6. Historical cache compatibility on startup

Phase C's compatibility reader already maps historical cache state into a generic snapshot without
writing.

Phase G startup must therefore restore both frozen historical shapes through the same generic path:

- manifested historical cache revision 13 / ACK 13;
- legacy historical cache revision 14 / ACK 13.

Required:

- no migration write;
- no generic marker created merely by startup;
- exact runtime/manifest bytes preserved;
- exact revision/ACK preserved;
- correct Phase D handler selected;
- exact runtime arm behavior;
- no ACK;
- no Cloud required.

A later **newer** Cloud assignment may create the generic snapshot exactly as Phase F already does.

## 7. Generic snapshot startup

For a generic snapshot produced by Phase F:

- read it through `InstallationStore.read()`;
- restore through PackageInstaller same-revision;
- no CloudTrackRepository;
- no re-persist;
- no ACK;
- exact handler id/codec/revision retained;
- manifested package re-arms controller + scheduler;
- legacy package re-arms scheduler with manifested owner disarmed;
- activeRevision is selected only through LiveVideoRuntimePorts.

Process recreation must restore the pending durable revision even when its ACK is older because a
previous ARM/HTTP ACK failed.

Do not require `acknowledgedRevision == snapshot.revision` to restore.

## 8. Corruption / unsupported restore

If InstallationStore is CORRUPT:

- no runtime mutation;
- no cache clear;
- no fallback to historical residue;
- activeRevision remains 0;
- Cloud client later also fails closed per Phase F;
- diagnostics exposes bounded corrupt state;
- user can recover only through the explicit Reset flow.

If a durable snapshot is structurally valid but its handler/capability restore returns failure:

- do not mutate the durable revision;
- do not ACK;
- leave activeRevision 0;
- expose a bounded restore/install state;
- do not invent migration, fallback, or rollback.

A historical manifested MEDIA/CONTINUE snapshot may therefore be wire/history-compatible but not
executable under the canonical M4 capability truth. Document its exact observed restore outcome.

## 9. BootReceiver generic durable truth

Replace the historical:

`new CloudTrackRepository(app).revision() > 0`

test.

BootReceiver must determine whether a durable installation is present through
`InstallationStore.read()`.

Required interpretation for AutostartPolicy input:

- SNAPSHOT -> `hasValidCachedTrack/content = true`;
- EMPTY -> false;
- CORRUPT -> false.

Do not parse Video content in BootReceiver.

Do not arm in BootReceiver.

Do not access handlers in BootReceiver.

BootReceiver still starts only `OverlayService.ACTION_BOOT_PREPARE`.

The existing autostart policy remains:

- explicit opt-in required;
- overlay permission required;
- MediaSession access required;
- usable Cloud credential OR durable install present.

Do not add LOCKED_BOOT_COMPLETED/directBootAware.

## 10. Boot-prepare visual invariant

On ACTION_BOOT_PREPARE after a real reboot/process recreation:

- restore the exact durable installation;
- arm scheduler/controller only;
- no legacy card;
- no SceneRenderer scene;
- no POC badge in Consumer Mode;
- no comment displayed until a real eligible MediaSession event becomes due;
- stale callbacks from the previous process cannot exist/resurrect content;
- activeRevision is exact durable revision.

This must be covered by deterministic service/runtime tests as far as JVM boundaries allow.

Physical visibility after real boot remains for Sony qualification.

## 11. Generic diagnostics model

Make generic installation truth first-class and product-neutral.

`RuntimeDiagnostics.capture` must read `InstallationStore`, not
`CloudTrackRepository`.

Add bounded installation diagnostics sufficient to distinguish:

- EMPTY;
- READY / SNAPSHOT;
- CORRUPT.

For a valid snapshot expose only bounded non-secret metadata:

- installation/package present;
- revision;
- acknowledged revision;
- codec id;
- handler id.

Do not expose:

- artifact bytes;
- runtime JSON;
- manifest JSON;
- comment/scene text;
- raw exception;
- URL;
- credential;
- full FinalTrack.

### 11.1 Compatibility diagnostic fields

Preserve the existing fields while M4 closes:

- `cachedTrackPresent`;
- `cachedTrackId`;
- `cachedRevision`;
- `lastAcknowledgedRevision`.

Required compatibility behavior:

- `cachedTrackPresent` aliases generic snapshot presence;
- `cachedRevision` aliases generic installation revision;
- `lastAcknowledgedRevision` aliases generic acknowledged revision;
- `cachedTrackId` remains legacy/Video compatibility-only and may be null when generic durable
  state is authoritative; do not parse generic opaque artifacts merely to populate it.

Mark/document these as compatibility fields, not generic authority.

Do not remove them in Phase G.

## 12. Diagnostics screen

Update `DiagnosticsActivity` to show generic truth first with bounded labels such as:

- Installed package: yes / no / corrupt;
- Package codec;
- Package handler;
- Installed revision;
- Acknowledged revision.

Keep the old cache lines temporarily for compatibility, but label them clearly as legacy if useful.

The screen must remain read-only except for the existing explicit Reset action.

No secret may appear.

Do not display raw handler errors/artifacts.

## 13. MainActivity offline status

Replace the historical cache check in `MainActivity.refreshCloud`.

Offline saved-content indication must use the generic InstallationStore state.

Do not parse the package.

Recommended user-facing wording:

`Offline (using saved SceneVibe content)`

rather than "saved track", because M4 now has product-neutral durable packages.

CORRUPT is not valid saved content.

Do not expose revision/codec on the normal screen.

## 14. Stopped-service Reset

When Diagnostics Reset is invoked while the service is not running:

- rotate InstallationIdentity exactly as before;
- reset CloudDeviceCredentials exactly as before;
- clear installation state with `InstallationStore.clearAll()`;
- reset Cloud observations;
- do not construct/use CloudTrackRepository.

No concurrent Cloud client exists in this branch, so direct generic clear is valid.

When service is running, retain the Phase F coordinated asynchronous reset.

## 15. Compatibility cleanup

After generic startup/diagnostics/reset are wired:

### Required

- `OverlayService` live/startup code must not call:
  - `CloudTrackRepository.restore`;
  - `CloudTrackRepository.restoreWithManifest`;
  - `installManifestedRevision`;
  - `confirmManifestedRevisionArmed`;
  - `activateLegacyRevision`.

- `BootReceiver`, `RuntimeDiagnostics`, `DiagnosticsActivity`, and `MainActivity`
  must not construct/use `CloudTrackRepository`.

- no production behavior may infer current revision/ACK from historical keys once the generic
  store exists.

### Old static OverlayService helpers

The now-dead:

- `installManifestedRevision`;
- `confirmManifestedRevisionArmed`;
- `activateLegacyRevision`

should be removed from production if all retained behavior is already covered by handler/runtime
tests and frozen historical oracles.

Do not keep them merely to satisfy old tests. Update/move characterization tests to a test-side
oracle if necessary, while preserving their frozen assertions through inverse/provenance checks.

### CloudTrackRepository class

Do not delete it solely for aesthetic cleanup.

It may remain as an isolated historical characterization/compatibility type if removing it would
create unnecessary test churn.

However, after Phase G there must be **zero live production callers** of it.

If Work can safely move its remaining characterization need entirely into test source with exact
baseline proof, deleting the production class is allowed, but is not required.

No generic path may depend on it.

## 16. No historical key cleanup yet

Do not delete historical keys automatically.

A generic snapshot may coexist with old:

- revision;
- runtime;
- manifest;
- ackRevision

residue.

The generic marker/snapshot remains authoritative.

Reset may clear the whole installation file as already qualified.

Do not perform eager cleanup/migration on normal startup because:

- same-revision historical read must remain zero-write;
- M4 does not need destructive migration;
- physical upgrade evidence must remain easy to diagnose.

A later maintenance release may remove residue after field confidence.

## 17. Mandatory generic startup tests

Add deterministic tests proving at minimum:

1. EMPTY store -> no restore/arm/write/ACK;
2. CORRUPT store -> fail closed, no clear/fallback/arm;
3. generic manifested snapshot -> exact same-revision PackageInstaller restore -> ARMED;
4. generic legacy snapshot -> ARMED;
5. ACK may be lower than snapshot revision and restore still succeeds;
6. generic manifested restore performs zero commit and zero ACK write;
7. generic legacy restore performs zero commit and zero ACK write;
8. restored runtime ports receive the exact durable revision;
9. wrong/missing durable handler -> no active runtime;
10. unsupported durable capability -> no active runtime;
11. restore ARM failure -> durable package remains pending, no write;
12. subsequent process-local recreation can restore the same pending snapshot;
13. manifested and legacy restore keep maximum one visual owner;
14. stale generation cannot resurrect an old scene;
15. ARM remains not-visible until a due MediaSession event.

Use the real InstallationStore, PackageInstaller, Video registry and handlers where appropriate.

## 18. Mandatory historical startup tests

Using exact Phase A cache fixtures:

1. manifested 13 / ACK 13 restores via generic compatibility read;
2. legacy 14 / ACK 13 restores via generic compatibility read;
3. no write/migration;
4. generic marker remains absent;
5. exact bytes unchanged;
6. exact ACK unchanged;
7. correct handler selected;
8. no Cloud collaborator required;
9. corrupted historical state fails closed;
10. historical MEDIA/CONTINUE behavior is documented against the canonical capability model.

## 19. Boot/autostart tests

Retain every old AutostartPolicy assertion.

Add/adjust BootReceiver tests proving:

1. generic snapshot counts as durable content;
2. historical compatible snapshot counts as durable content;
3. empty state does not;
4. corrupt state does not;
5. usable Cloud credential still independently permits START;
6. missing overlay permission still blocks first;
7. missing MediaSession permission still blocks;
8. autostart disabled remains default/no start;
9. receiver never parses artifacts/handler;
10. receiver starts only ACTION_BOOT_PREPARE;
11. receiver never ACKs/writes/migrates installation state.

## 20. Diagnostics tests

Prove:

1. EMPTY generic store -> bounded empty state/revision 0/ACK 0;
2. generic manifested snapshot -> correct revision/codec/handler/ACK;
3. generic legacy snapshot -> correct revision/codec/handler/ACK;
4. historical manifested and legacy snapshots report generic compatibility truth;
5. CORRUPT -> bounded corrupt state and no content;
6. compatibility cachedTrackPresent/cachedRevision/lastAcknowledged aliases remain coherent;
7. cachedTrackId is never obtained by parsing generic opaque artifact content;
8. full artifact bytes/text never reach RuntimeDiagnostics;
9. secrets remain absent;
10. DiagnosticsActivity renders generic fields without raw content;
11. MainActivity offline status uses generic store truth;
12. stopped-service reset clears generic and historical installation state;
13. capture/read paths perform zero writes.

## 21. Live Cloud non-regression after startup cutover

Retain all Phase F tests and add integration cases proving:

1. service restored generic revision then receives same-revision Cloud redelivery -> zero install
   commit, ARMED, then ACK;
2. restored generic revision then receives newer revision -> one generic commit then ARM/ACK;
3. restored pending revision with older ACK retries through same-revision Cloud delivery;
4. corrupt durable state blocks both boot restore and Cloud overwrite;
5. Phase F MEDIA/CONTINUE tightening remains unchanged;
6. finalTrackId separation remains unchanged;
7. ACK remains after exact ARMED and server confirmation only;
8. reset after restored package clears durable/runtime state.

Do not modify Cloud v1 wire/server behavior.

## 22. Import / architecture boundary gates

Add a Phase G Python boundary gate proving:

1. OverlayService startup restore uses InstallationStore + PackageInstaller, not
   CloudTrackRepository;
2. BootReceiver contains no CloudTrackRepository;
3. RuntimeDiagnostics contains no CloudTrackRepository;
4. DiagnosticsActivity contains no CloudTrackRepository;
5. MainActivity contains no CloudTrackRepository;
6. CloudControlClient remains on the Phase F generic path and does not regress;
7. PackageInstaller remains the only generic revision/restore authority;
8. VideoOverlayManifestBridge.validate still has exactly one production caller:
   VideoManifestInstallationHandler;
9. generic installation package remains free of Cloud/Video/Android product dependencies except
   AndroidInstallationBackend;
10. no new reboot-specific parser/handler registry is introduced;
11. no normal startup migration/write exists;
12. old service static install/confirm/legacy helpers have no live production caller and are
    preferably removed;
13. CloudTrackRepository, if retained, has zero production callers;
14. no permission/application/signing/configuration change;
15. no M5/M6/M7 type or capability appears;
16. no Cloud repository change occurs.

Earlier Phase B–F boundary gates may gain only exact G inventory/accounting exceptions.

## 23. Frozen evidence

Preserve exact SHA-256 values:

- seven M1 Cloud envelopes:
  `19ee6fc3e2eba7009c9d345c33a63ca5617621fac9c8f5618cb24ee2932d6eab`;
- OverlayManifest 94-case corpus:
  `c78addf877b277e38f66c12274e4c8ed2ce12a503d883e27fd9950215cafbf30`;
- manifested historical cache:
  `76d109c4b69f3e707912449f87e3fa6b9081e4564a12c77c633ae7ed7563beb1`;
- legacy historical cache:
  `1676ed2033ec0e589509da7bdab3294ed8b5949153eeb2cfdb539aa23409cc8c`.

Do not rewrite fixtures.

## 24. Baseline and CI accounting

Phase G starts from:

| JVM bucket | PASS | FAIL | SKIP |
| --- | ---: | ---: | ---: |
| Retained pre-Phase-A baseline | 178 | 0 | 1 |
| Phase A | 30 | 0 | 0 |
| Phase B | 39 | 0 | 0 |
| Phase C | 43 | 0 | 0 |
| Phase D | 83 | 0 | 0 |
| Phase E | 115 | 0 | 0 |
| Phase F | 138 | 0 | 0 |
| **Current full JVM** | **626** | **0** | **1** |

Python: **58 PASS / 0 FAIL / 0 SKIP**.

Add an explicit Phase G bucket to actual Gradle-XML accounting.

After implementation and every localized correction, require on exact pushed HEAD:

- all Python boundary tests PASS;
- full Gradle JVM suite PASS with only the known private SKIP;
- `lintDebug` PASS;
- `assembleDebug` PASS;
- LAN DEV compile PASS;
- Consumer Cloud-origin compile PASS;
- stable signing/certificate continuity PASS;
- Android 15 / API 35 standard-platform smoke SUCCESS.

Inspect exact-head workflow metadata, jobs, steps and logs.

Record the exact stable-signed Cloud APK artifact id for the later Sony test.

Do not claim TV boot/overlay qualification from CI.

## 25. M4 Sony physical protocol document

As part of Phase G create:

`docs/m4-sony-physical-qualification-protocol.md`

This is a **manual protocol**, not a claimed result.

It must contain a concise numbered checklist for the existing Sony Android TV target.

The protocol must require at minimum:

1. record pre-upgrade:
   - current app version;
   - abbreviated installation id;
   - abbreviated Cloud device id;
   - permissions;
   - autostart state;
   - installed/cached revision;
   - acknowledged revision;
2. install the exact stable-signed Phase G APK with `adb install -r`, **no uninstall**;
3. confirm:
   - app data preserved;
   - installation identity preserved;
   - Cloud device identity/pairing preserved;
   - overlay permission preserved;
   - MediaSession access preserved;
4. without sending a new assignment, start/boot-prepare and verify the previously durable
   historical/generic package restores;
5. verify no stale card is shown immediately merely because restore occurred;
6. send a new manifested Video revision;
7. verify exact durable revision + ACK + last assignment + last successful ACK diagnostics;
8. while a scene is visible, replace with a newer revision;
9. verify exactly one visual owner and no old-scene resurrection;
10. perform same-revision redelivery;
11. pause / resume;
12. backward seek;
13. forward seek;
14. media eligibility loss / return;
15. enable/confirm autostart and perform a **hard TV reboot with no new Send**;
16. verify exact durable revision/handler restores after reboot;
17. verify no old overlay appears before an eligible due event;
18. post-reboot same-revision redelivery;
19. verify accented/decomposed French Unicode / ligature / apostrophe / emoji;
20. inspect logcat for:
    - AndroidRuntime/FATAL;
    - Looper/thread/window errors;
    - SceneVibe installer/restore exceptions;
    - duplicate visual-owner symptoms.

The protocol must provide places to record exact observed revision/ACK values and PASS/FAIL.

It must state that force-stop/emulator is **not** a substitute for hard reboot.

## 26. Phase G is not the physical qualification itself

Work cannot claim the Sony protocol passed unless actual physical evidence is supplied in this
cycle from the real TV.

Normally this Work cycle must finish with:

`READY FOR M4 SONY QUALIFICATION`

not `M4 COMPLETE`.

Do not mark PR ready and do not merge after automated Phase G qualification.

After Work stops, the user will run the documented physical protocol on the Sony TV. A separate
closure/audit can then record physical evidence and decide whether PR #14 is merge-ready.

## 27. Protected non-regression surface

Preserve:

- Cloud v1 assignment/ACK wire;
- finalTrackId binding;
- auth/activation/device proof;
- polling/backoff;
- PackageInstaller semantics;
- Phase D handler semantics;
- TvCapabilities;
- InstallationStore atomicity and ACK separation;
- Phase F live owner-thread path;
- scheduler media clock / seek / pause / replay;
- generation guard;
- exactly one visual owner;
- reset identity rotation;
- disconnect semantics;
- app id/package/version unless a separately justified release-version bump is explicitly required;
- permissions;
- foreground-service type;
- signing chain;
- frozen fixtures;
- Production **SHADOW**.

## 28. Explicitly forbidden in Phase G

Do not implement:

- Banner handler/product;
- Language handler;
- SceneEvent/M5 scheduler;
- wall-clock runtime;
- remote/CDN assets;
- M7 asset cache;
- public capabilities API;
- Cloud capability negotiation/targeting;
- partner/SDK/API contracts;
- new Android permissions;
- WebView;
- dynamic plugins/code;
- Cloud repository changes;
- release/production rollout;
- PR merge.

Do not delete historical keys automatically.

Do not claim Sony PASS from CI.

No M5+ work.

## 29. Documentation/code quality

Do not regress existing code.

Do not remove retained Javadocs/docstrings without replacing obsolete documentation with accurate
documentation.

Every new/modified non-trivial class, constructor, method and helper must have concise pedagogical
Javadoc/docstring explaining invariant and side effects.

For modified/new Python:

- preserve docstrings;
- module teaching banner after imports;
- every function/helper has a docstring.

Do not reformat unrelated files.

## 30. Git / PR policy

Work only on:

`work/scenevibe-os-m4-tv-installation-001`

PR #14 remains DRAFT.

Normal commits and normal push only.

Do not:

- merge;
- rebase;
- force-push;
- change TV main;
- change Cloud main;
- retarget PR;
- mark ready for review.

Recommended commit shape:

1. generic startup/boot restore + autostart truth;
2. generic diagnostics/MainActivity/reset + dead-path cleanup;
3. Phase G tests/boundaries/accounting + Sony protocol;
4. Phase G report.

Localized deterministic self-audit corrections may add narrow commits.

## 31. WORK policy

This is an implementation cycle.

After implementation:

1. self-audit exact diff against M4 architecture and this work order;
2. prove service startup has no CloudTrackRepository restore dependency;
3. prove BootReceiver/diagnostics normal UI use generic durable truth;
4. prove startup restore causes zero commit and zero ACK;
5. prove corrupt state is not cleared/fallback-restored;
6. prove historical cache startup remains zero-write;
7. prove Phase F live Cloud path is unchanged except exact composition needed by G;
8. prove reset clears both generic and historical state;
9. inspect dead compatibility helpers/callers;
10. run all retained/new tests;
11. push normally;
12. inspect exact-head CI;
13. fix localized deterministic defects and strengthen tests in the same cycle.

STOP rather than guessing if an issue:

- requires new product capability;
- requires Cloud wire/server change;
- requires changing PackageInstaller revision semantics;
- requires destructive cache migration;
- requires Sony physical evidence to decide implementation correctness;
- requires M5+.

## 32. Required Phase G report

Create:

`docs/m4-phase-g-generic-restore-diagnostics-report.md`

The report must include:

- exact implementation start HEAD;
- final HEAD;
- commits and changed files;
- generic startup restore algorithm;
- proof same PackageInstaller same-revision path is reused;
- EMPTY/CORRUPT/SNAPSHOT behavior;
- historical manifested/legacy startup results;
- generic manifested/legacy startup results;
- pending revision with older ACK restore result;
- boot/autostart generic-state behavior;
- armed-not-visible proof;
- RuntimeDiagnostics generic fields and compatibility aliases;
- DiagnosticsActivity changes;
- MainActivity offline-state change;
- stopped/running Reset paths;
- exact cleanup/removal or retained isolation of old helpers;
- proof CloudTrackRepository has zero live production caller if retained;
- proof Phase F live Cloud/ACK path remains qualified;
- JVM counts split retained / A / B / C / D / E / F / G;
- Python counts split retained/new;
- frozen hashes;
- exact-head Android debug run id/result;
- exact-head Android 15 run id/result;
- stable-signed Cloud APK artifact id;
- signing/application/permissions continuity;
- exact path to the Sony physical protocol;
- explicit statement that Sony physical qualification has NOT yet been performed;
- known limits;
- PR #14 state.

The final verdict must be exactly one of:

- `READY FOR M4 SONY QUALIFICATION`
- `NOT READY FOR M4 SONY QUALIFICATION`

Do not write `M4 COMPLETE`.

Do not merge.

STOP after the report and verdict.
