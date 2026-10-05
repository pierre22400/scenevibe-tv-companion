# SCENEVIBE OS — WORK — M4 PHASE E — PackageInstaller Orchestration

Date: 2026-10-04  
Repository: `pierre22400/scenevibe-tv-companion`  
Branch: `work/scenevibe-os-m4-tv-installation-001`  
PR: #14 — must remain **OPEN / DRAFT / unmerged**.

## 1. Mission

Implement **M4 Phase E only**: introduce one product-neutral `PackageInstaller` that owns the
generic local installation lifecycle:

```text
validate -> prepare -> commit -> arm
```

and the generic revision/redelivery rules.

Phase E must compose the already-qualified Phase B model/capabilities, Phase C durable store and
Phase D static Video handlers without changing the current production Cloud v1 call path.

ACK remains completely outside the installer.

The authoritative architecture is:

- `docs/scenevibe-os-m4-tv-installation-architecture.md`

The immediately preceding closure is:

- `docs/m4-phase-d-video-compatibility-handlers-report.md`

Phase D final closure HEAD before this documentation commit:

- `e58a4d81c0aa0c5b6eda0126cd55ff86449107f7`

Frozen milestone bases remain:

- `BASE_TV_M4 = ecdf77bec9f93babf15239a63bf7f702fd7ca293`
- `BASE_CLOUD_M4 = 5011c91aac61a0cc6dcc74c256a15b7dee03d785`

Production revision authority remains **SHADOW**.

This work-order commit is documentation-only. Before implementation, verify that the branch HEAD
is exactly the commit containing this file and that its ancestry contains the Phase D closure HEAD
above. Do not reset to the Phase D SHA and discard this work order.

## 2. Read completely before editing

Read in full, from the exact branch HEAD:

1. `docs/scenevibe-os-m4-tv-installation-architecture.md`
2. this work order
3. `docs/m4-phase-d-video-compatibility-handlers-report.md`
4. `docs/m4-phase-c-generic-durable-store-report.md`
5. `docs/m4-phase-b-generic-model-capabilities-report.md`
6. `docs/m4-phase-a-characterization-report.md`
7. all current types under
   `app/src/main/java/com/scenevibe/tvcompanionpoc/installation/`
8. `app/src/main/java/com/scenevibe/tvcompanionpoc/VideoInstallationHandlers.java`
9. `app/src/main/java/com/scenevibe/tvcompanionpoc/VideoManifestInstallationHandler.java`
10. `app/src/main/java/com/scenevibe/tvcompanionpoc/VideoLegacyInstallationHandler.java`
11. `app/src/main/java/com/scenevibe/tvcompanionpoc/VideoInstallationRuntimePorts.java`
12. `app/src/main/java/com/scenevibe/tvcompanionpoc/VideoPreparedState.java`
13. `app/src/main/java/com/scenevibe/tvcompanionpoc/CloudTrackRepository.java`
14. `app/src/main/java/com/scenevibe/tvcompanionpoc/CloudControlClient.java`
15. `app/src/main/java/com/scenevibe/tvcompanionpoc/OverlayService.java`

GitHub is the source of truth.

## 3. Independently audited Phase D starting truth

Phase D has been independently audited after the Work cycle.

Exact closure:

- final Phase D HEAD: `e58a4d81c0aa0c5b6eda0126cd55ff86449107f7`;
- implementation commit: `9211732daa4ce7b47d622446bd200959bdb4442e`;
- report commit: `e58a4d81c0aa0c5b6eda0126cd55ff86449107f7`;
- PR #14: OPEN / DRAFT / MERGEABLE / unmerged;
- TV main remains `ecdf77bec9f93babf15239a63bf7f702fd7ca293`;
- Cloud main remains `5011c91aac61a0cc6dcc74c256a15b7dee03d785`;
- final-head Android debug workflow `37223946457`: SUCCESS;
- final-head Android 15 platform smoke `37223946526`: SUCCESS.

Executed final-head counts:

- Python: **33 PASS / 0 FAIL / 0 SKIP**;
- JVM: **373 PASS / 0 FAIL / 1 private SKIP**, 374 total;
- Phase D adds 83 JVM cases.

The sole private SKIP remains:

`M1CloudInteropTest.originalColumboProjectionIsInstallable`

via `SCENEVIBE_M1_SONY_FIXTURES`.

The stable signing certificate remains:

`f908bf564ed97ba67e02b1ebc89eb0239cf980752587f55eb9ec0419791a2e9c`.

No Phase D corrective commit is required before Phase E.

## 4. Required Phase E architecture

Introduce one generic class:

`com.scenevibe.tvcompanionpoc.installation.PackageInstaller`

It owns only local installation orchestration and revision policy.

Its dependencies are:

- `InstallationStore`;
- `InstallationHandlerRegistry`;
- `TvCapabilities`;
- caller-supplied `InstallationHandler.RuntimePorts`.

It must not import or know:

- Video classes;
- Prime;
- FinalTrack;
- Cloud v1 envelope fields;
- `finalTrackId`;
- `CloudControlClient`;
- `OverlayService`;
- scheduler/controller/renderer concrete classes;
- Android Context / Service / Handler / Looper / View / WindowManager;
- network APIs;
- credentials;
- diagnostics payload content.

No reflection, discovery, dynamic loading, WebView or downloaded executable code.

Phase E does **not** wire this installer into production.

## 5. Public/local orchestration contract

Exact names may be adjusted if there is a strong Java reason, but the semantics must remain small
and explicit.

Preferred shape:

```java
PackageInstaller(
    InstallationStore store,
    InstallationHandlerRegistry registry,
    TvCapabilities capabilities
)

InstallationStatus install(
    InstallRequest request,
    InstallationHandler.RuntimePorts ports
)
```

The installer returns only the existing bounded `InstallationStatus` vocabulary.

Do not add raw exception messages, payloads, URLs, secrets or content-bearing diagnostics.

Do not add an ACK method.

Do not add a network client.

Do not add a public generic reboot/boot API in Phase E; generic boot/restore cutover belongs to
Phase G. Internal durable re-read/restore required to ARM an installation or same-revision
redelivery is mandatory and is not considered the Phase G cutover.

## 6. Exact revision authority

The installer owns the generic revision rules.

First read the durable store under its existing consistency contract.

### 6.1 Corrupt durable state

If `InstallationStore.read()` is `CORRUPT`:

- fail closed;
- do not validate/prepare/commit/arm the incoming request;
- do not clear or rewrite the store;
- do not ACK;
- return a bounded storage failure result.

Use `CACHE_FAILED` unless the existing type system proves a strictly better already-defined
mapping. Do not invent a new status merely for this case.

### 6.2 Empty durable state

If store state is `EMPTY`:

- a valid incoming request follows the complete
  validate -> prepare -> commit -> re-read -> restore -> arm flow.

### 6.3 Stale revision

If:

`request.revision() < durableRevision`

return `STALE`.

A stale request must cause:

- zero handler validation;
- zero preparation;
- zero commit;
- zero ARM;
- zero ACK;
- zero mutation.

Do not parse stale payloads merely to produce a prettier error.

### 6.4 Same-revision redelivery

If:

`request.revision() == durableRevision`

the **durable installation is authoritative**.

Required behavior:

- do not persist the incoming request;
- do not reinterpret incoming artifact bytes as the active package;
- do not lower or replace the durable revision;
- resolve the handler from the durable snapshot metadata;
- re-run that handler's `restoreFromCache(...)` on the exact durable canonical bytes;
- ARM that restored durable package through the supplied runtime ports;
- return `ARMED` only after that exact durable package is armed;
- on restore/ARM failure, return the appropriate bounded failure;
- ACK remains outside.

This intentionally preserves the already-characterized rule that same-revision redelivery can
contain different incoming content without rewriting the cache: the exact durable copy is the
authority.

The incoming codec/artifacts therefore must not be allowed to replace the durable shape merely
because the revision matches.

### 6.5 Newer revision

If:

`request.revision() > durableRevision`

execute the full Phase E pipeline.

## 7. Full newer-revision pipeline

For a newer request, perform these stages in order.

### 7.1 Resolve static handler

Resolve the request codec through the static registry.

Unknown/unregistered codec:

- no write;
- no ARM;
- return `INVALID_PACKAGE`.

Lookup must not construct or discover a handler.

### 7.2 VALIDATE

Call:

`handler.validate(request, capabilities)`

If result is not `VALIDATED`:

- return that bounded result;
- no PREPARE;
- no commit;
- no ARM.

This includes `INVALID_PACKAGE` and `UNSUPPORTED_CAPABILITY`.

### 7.3 PREPARE

Call:

`handler.prepare(request, capabilities)`.

The prepared result must be coherent with the selected registry entry:

- exact incoming revision;
- exact selected codec;
- exact selected handler id;
- status `PREPARED`;
- capability-valid requirements.

A null, foreign, rebound or inconsistent prepared result fails closed before persistence.

If prepare throws/rejects after validate succeeded:

- no commit;
- no ARM;
- return `INVALID_PACKAGE` or the closed status carried by the trusted Phase D rejection type
  if available without coupling the generic installer to Video.

The generic installer must not import a Video-specific exception.

### 7.4 Encode canonical cache candidate

Call:

`handler.encodeForCache(prepared)`.

The returned canonical request may define the handler's canonical artifact bytes, but it must
preserve:

- exact revision;
- exact codec selected by the registry;
- bounded `InstallRequest` invariants.

The installer must never accept an encoder that changes revision or codec.

Construct the durable `InstallationSnapshot` using the **registry entry's trusted handler id**,
not an untrusted incoming string.

An encode failure causes no write and returns a bounded invalid-package failure.

### 7.5 COMMIT

Call `InstallationStore.commit(snapshot)`.

Map outcomes without ambiguity:

- `COMMITTED` -> continue;
- `INVALID_SNAPSHOT` -> `INVALID_PACKAGE`;
- `CACHE_FAILED` -> `CACHE_FAILED`.

COMMIT must not:

- ARM;
- update ACK;
- send network traffic.

A failed commit leaves the previous durable revision intact, as already guaranteed by Phase C.

### 7.6 Re-read the committed representation

After a successful commit, do **not** ARM the earlier caller/prepared copy directly.

Read the store again.

The re-read must prove:

- state is `SNAPSHOT`;
- revision equals the requested/committed revision;
- codec equals the selected codec;
- handler id equals the selected registry handler id;
- registry lookup by durable handler id resolves the same codec/handler binding.

If the committed representation cannot be read back exactly:

- do not ARM;
- return `CACHE_FAILED`;
- do not ACK.

This is a durability/readback failure, not permission to trust the pre-commit in-memory copy.

### 7.7 Restore from the durable bytes

Call the durable handler's:

`restoreFromCache(durableSnapshot.canonical(), capabilities)`.

This must rebuild trusted prepared state from the persisted bytes.

Verify the restored prepared value is coherent with:

- exact durable revision;
- exact durable codec;
- exact durable handler id;
- `PREPARED` status.

Do not re-use the pre-commit prepared-state object for ARM.

If restore fails:

- the newly committed revision remains durable;
- ACK remains unchanged;
- do not roll back to the older revision;
- do not invent another commit;
- return `ARM_FAILED` unless the failure is an actual store/readback failure already classified
  as `CACHE_FAILED`.

This matches the characterized M4 rule: commit success followed by arm failure leaves the new
revision durable and pending.

### 7.8 ARM

Call:

`durableHandler.arm(restoredPrepared, ports)`.

Only exact `ARMED` is success.

Any other result/exception:

- no ACK;
- newly committed durable revision remains;
- acknowledged revision remains the previous confirmed value;
- return `ARM_FAILED` unless the handler already returned a more specific closed pre-arm status
  that is semantically valid at this point.

The installer must not call `InstallationStore.markAcknowledged`.

## 8. Durable handler resolution rules

Introduce a small private helper if useful.

For a durable `InstallationSnapshot`:

1. lookup by `handlerId`;
2. require an entry exists;
3. require `entry.handlerId() == snapshot.handlerId()`;
4. require `entry.codecId() == snapshot.codecId()`;
5. use only `entry.handler()`.

Never infer a handler from artifact names during generic restore.

Never guess from historical key presence inside PackageInstaller. Phase C already converts historical
state into an unambiguous `InstallationSnapshot`.

No reflection or fallback.

## 9. Historical-cache compatibility in Phase E

Phase E must prove that PackageInstaller can consume the Phase C compatibility read without
rewriting it.

For frozen historical caches:

- manifested fixture revision 13 / ACK 13 maps to the manifested handler;
- legacy fixture revision 14 / ACK 13 maps to the legacy handler.

A same-revision install request must:

- perform zero store writes;
- restore/prepare from the durable historical snapshot;
- ARM the durable handler;
- preserve exact historical revision and ACK;
- leave the legacy keys byte-identical.

Do not eagerly migrate historical state in Phase E.

Migration/cutover policy remains deferred.

## 10. ACK invariant

This phase must strengthen, not weaken, the ACK boundary.

PackageInstaller must have:

- no HTTP/network import;
- no `finalTrackId`;
- no Cloud credentials;
- no `markAcknowledged` call;
- no ACK callback;
- no Boolean named or documented as "ack eligible" that can be mistaken for server confirmation.

`ARMED` means only:

- durable exact revision is present;
- durable bytes were restored through the registered handler;
- local runtime ARM succeeded.

The later Cloud adapter may decide to send the historical ACK only after receiving `ARMED`.

The server-confirmed ACK is still persisted later by the transport/compatibility layer, not here.

## 11. Handler failure/exception discipline

PackageInstaller is a trust boundary around statically registered handlers.

It must fail closed if a handler:

- throws during validate;
- returns null status;
- claims `PREPARED` incoherently;
- throws during prepare;
- returns null prepared value;
- changes revision/codec in encode;
- throws during encode;
- fails restore;
- returns a foreign restored handler/codec/revision;
- throws during arm;
- returns null/non-ARMED from arm.

Do not expose the handler exception text.

Do not catch VM-fatal errors broadly; ordinary runtime exceptions are sufficient.

## 12. No rollback invention

Do not introduce a rollback policy in Phase E.

If a newer revision commits successfully and later restore/ARM fails:

- keep the newer revision durable;
- keep the old acknowledged revision;
- return failure;
- allow later redelivery/process recreation to attempt recovery from the durable new package.

This is the frozen Phase A semantics.

Do not re-commit the previous revision.

Do not silently decrement revision.

## 13. Production cutover explicitly forbidden

The generic PackageInstaller may be production-quality and fully executable in tests, but Phase E
must not wire it into the current production route.

Do not modify current production orchestration in:

- `CloudControlClient`;
- `OverlayService`;
- `BootReceiver`;
- current `CloudControlClient.ManifestInstaller` seam.

Do not make current Cloud v1 code construct `InstallRequest`.

Do not replace `CloudTrackRepository` current install/restore orchestration.

Do not make boot restore call PackageInstaller.

Those belong to Phase F/G.

Phase E integration tests may compose:

- real `InstallationStore`;
- real `PackageInstaller`;
- real Phase D Video registry;
- deterministic test backends/runtime ports.

That is not a production cutover.

## 14. Mandatory core PackageInstaller tests

Add deterministic tests with generic fake handlers/ports covering at minimum:

1. empty store + valid newer request -> exact validate/prepare/encode/commit/readback/restore/arm order;
2. success returns exactly `ARMED`;
3. success performs no ACK write;
4. unknown codec -> `INVALID_PACKAGE`, zero write/arm;
5. validate `INVALID_PACKAGE` -> zero prepare/write/arm;
6. validate `UNSUPPORTED_CAPABILITY` -> zero prepare/write/arm;
7. validate exception/null -> fail closed, zero write/arm;
8. prepare failure/exception/null -> zero commit/arm;
9. prepared revision mismatch -> zero commit;
10. prepared codec mismatch -> zero commit;
11. prepared handler-id mismatch -> zero commit;
12. encode exception/null -> zero commit;
13. encoder changed revision -> zero commit;
14. encoder changed codec -> zero commit;
15. store `INVALID_SNAPSHOT` -> `INVALID_PACKAGE`;
16. store commit failure -> `CACHE_FAILED`, prior durable snapshot unchanged;
17. successful commit never changes acknowledged revision;
18. post-commit corrupt/empty/wrong revision readback -> `CACHE_FAILED`, zero ARM;
19. post-commit handler-id/codec mismatch -> fail closed, zero ARM;
20. restoreFromCache is called on durable bytes, not the pre-commit request object;
21. restore failure leaves newly committed revision durable and ACK unchanged;
22. restored prepared revision/codec/handler mismatch -> fail closed;
23. arm false/non-ARMED/exception -> `ARM_FAILED`, ACK unchanged;
24. stale revision -> `STALE` before handler lookup/validation;
25. stale payload can be malformed without being parsed;
26. same revision -> zero commit;
27. same revision ignores different incoming bytes and restores exact durable bytes;
28. same revision resolves the durable handler, not the incoming codec;
29. same revision restore/arm success -> `ARMED`;
30. same revision ARM failure -> `ARM_FAILED`, no write;
31. corrupt durable state -> `CACHE_FAILED`, no handler invocation;
32. orphan/future ACK corruption remains fail-closed through the store read;
33. installer never calls `markAcknowledged`;
34. handler registry lookup constructs nothing;
35. no second commit occurs merely to ARM.

## 15. Mandatory real-Video integration tests

Compose the real:

- `InstallationStore`;
- `PackageInstaller`;
- `VideoInstallationHandlers.registry()`;
- `TvCapabilities.current()`;
- deterministic Video runtime ports.

Prove at minimum:

1. manifested frozen package installs through the generic pipeline and returns `ARMED`;
2. legacy frozen package installs through the generic pipeline and returns `ARMED`;
3. manifested -> newer legacy replacement produces one durable handler identity and one visual owner;
4. legacy -> newer manifested replacement likewise;
5. generic manifested `MEDIA/CONTINUE` is rejected before commit with
   `UNSUPPORTED_CAPABILITY`;
6. generic wall profile is rejected before commit with `UNSUPPORTED_CAPABILITY`;
7. invalid Video cross-contract data is `INVALID_PACKAGE` on the supported executable profile;
8. same-revision manifested redelivery re-arms the durable manifested copy without rewrite;
9. same-revision legacy redelivery re-arms the durable legacy copy without rewrite;
10. same-revision incoming bytes intentionally changed do not replace durable bytes;
11. failed Video ARM after successful commit leaves the new snapshot durable and prior ACK unchanged;
12. exact Unicode bytes survive commit/readback/restore;
13. no network or ACK collaborator exists in the composition.

Do not use a copied Video parser or copied handler logic in these integration tests.

## 16. Historical compatibility integration tests

Using the exact frozen Phase A fixtures:

- `video-manifested-cache-v1.json`;
- `video-legacy-cache-v1.json`;

prove PackageInstaller same-revision handling:

- reads them through `InstallationStore.read()`;
- resolves the Phase D handler identity;
- performs no commit/migration;
- arms from the exact durable bytes;
- preserves revision;
- preserves ACK;
- preserves runtime/manifest text bytes;
- leaves identity/pairing/credentials out of scope and untouched.

## 17. Import / architecture boundary gates

Add a Phase E Python boundary gate proving at minimum:

1. `PackageInstaller.java` is in the generic installation package;
2. PackageInstaller has no Android imports;
3. PackageInstaller has no Video/Prime/FinalTrack/Cloud/renderer/scheduler/controller references;
4. PackageInstaller has no network/reflection/dynamic loading;
5. PackageInstaller does not call `markAcknowledged`;
6. PackageInstaller does not contain `finalTrackId`;
7. production `CloudControlClient.java` remains byte-exact Phase D;
8. production `OverlayService.java` remains byte-exact Phase D;
9. production `BootReceiver.java` remains byte-exact Phase D;
10. `CloudTrackRepository.java` remains byte-exact Phase D unless a strictly necessary
    non-production-compatible compile adjustment is proven; no orchestration move is authorized;
11. Phase D handler files remain byte-exact unless a localized Phase E compatibility correction is
    strictly necessary and separately justified;
12. Phase C store/backend durable behavior remains protected;
13. exactly one new production orchestration type is introduced unless a tiny generic immutable
    result/helper is genuinely necessary;
14. no Phase F adapter or Phase G restore coordinator exists;
15. application/signing/permission/configuration blobs remain unchanged.

Do not weaken earlier Phase B/C/D gates merely to admit PackageInstaller. Add the exact authorized
Phase E production file to their source inventories where needed and pin all other protected blobs.

## 18. Frozen fixtures and hashes

Preserve:

- M1 Cloud envelopes:
  `19ee6fc3e2eba7009c9d345c33a63ca5617621fac9c8f5618cb24ee2932d6eab`
- OverlayManifest 94-case corpus:
  `c78addf877b277e38f66c12274e4c8ed2ce12a503d883e27fd9950215cafbf30`
- manifested cache fixture:
  `76d109c4b69f3e707912449f87e3fa6b9081e4564a12c77c633ae7ed7563beb1`
- legacy cache fixture:
  `1676ed2033ec0e589509da7bdab3294ed8b5949153eeb2cfdb539aa23409cc8c`

Do not rewrite fixtures to fit the installer.

## 19. Baseline and CI accounting

Phase E starts from the independently verified exact counts:

| JVM bucket | PASS | FAIL | SKIP |
| --- | ---: | ---: | ---: |
| Retained pre-Phase-A baseline | 178 | 0 | 1 |
| Phase A | 30 | 0 | 0 |
| Phase B | 39 | 0 | 0 |
| Phase C | 43 | 0 | 0 |
| Phase D | 83 | 0 | 0 |
| **Current full JVM** | **373** | **0** | **1** |

Python: **33 PASS / 0 FAIL / 0 SKIP**.

Add an explicit Phase E JVM/Python bucket to the existing real-Gradle-XML accounting without losing
the previous inventories.

After implementation and every localized correction, require on the exact pushed HEAD:

- all Python boundary tests PASS;
- full Gradle JVM suite PASS with only the known private SKIP;
- `lintDebug` PASS;
- `assembleDebug` PASS;
- LAN DEV compile PASS;
- Consumer Cloud-origin compile PASS;
- stable signing/certificate continuity PASS;
- Android 15 / API 35 standard-platform smoke SUCCESS.

Inspect exact-head workflow metadata, jobs, relevant steps and logs.

Do not infer test counts from annotations or source; read the real JUnit XML summary.

Do not claim Sony physical qualification from CI/emulator evidence.

## 20. Required non-regression

Preserve current production behavior exactly:

- Cloud v1 envelope fields;
- ACK body/order;
- `finalTrackId` binding;
- current `CloudTrackRepository` historical persistence path;
- current redelivery/revision behavior;
- current manifested/legacy acceptance behavior, including the characterized compatibility edge;
- scheduler media-time semantics;
- pause/seek/replay;
- SceneRuntimeController generation guard;
- one visual owner;
- owner-thread window mutation;
- identity/pairing/credentials;
- disconnect/reset;
- application id/package name;
- permissions;
- foreground-service type;
- signing chain;
- Production **SHADOW**.

The new PackageInstaller is not yet the production authority.

## 21. Explicitly forbidden in Phase E

Do not implement:

- Cloud v1 compatibility adapter;
- `CloudControlClient` PackageInstaller cutover;
- `OverlayService` PackageInstaller adapter;
- generic boot/reboot restore cutover;
- generic diagnostics terminology cleanup;
- deletion/migration of historical cache keys;
- server/Cloud capability negotiation;
- Banner handler;
- Language handler;
- wall-clock runtime;
- SceneEvent/M5 scheduler;
- remote assets/CDN;
- M7 asset cache;
- WebView;
- dynamic plugins/code;
- new Android permissions;
- Cloud repository changes;
- Production cutover;
- PR merge.

No Phase F/G or M5+ implementation.

## 22. Documentation/code quality rules

Do not regress existing code.

Do not remove existing Javadocs/docstrings.

Every new non-trivial class, constructor, method and helper must have a concise pedagogical
Javadoc/docstring explaining its invariant and side effects.

For any new or modified Python file:

- preserve existing docstrings;
- retain/add the pedagogical module banner after imports according to the project rule;
- every function, including utilities, must have a docstring.

Do not reformat unrelated files.

## 23. Git / PR policy

Work only on:

`work/scenevibe-os-m4-tv-installation-001`

PR #14 remains DRAFT.

Normal commits and normal push only.

Do not:

- merge;
- rebase;
- force-push;
- change TV `main`;
- change Cloud `main`;
- retarget the PR;
- mark the PR ready for review.

Recommended commit shape:

1. generic PackageInstaller + unit tests;
2. real Video/historical integration + boundary/CI accounting;
3. Phase E report.

A localized deterministic self-audit correction may add another narrow commit.

## 24. WORK policy — implement, self-audit, fix localized defects

This is an implementation cycle.

After implementation:

1. self-audit the exact diff against this work order and M4 architecture;
2. inspect every path that can write before VALIDATE/PREPARE success;
3. inspect same-revision handling for accidental re-persist or incoming-byte trust;
4. inspect post-COMMIT handling to prove ARM uses the durable re-read/restored state;
5. inspect ACK isolation;
6. inspect handler exception/null behavior;
7. run all retained/new tests;
8. push normally;
9. inspect exact-head CI;
10. fix localized deterministic defects in the same cycle and strengthen regression coverage.

STOP and report rather than guessing if an issue:

- requires Phase F Cloud adapter decisions;
- requires modifying the current Cloud wire;
- requires changing Phase D Video semantics;
- requires a rollback product policy not established by the architecture;
- requires Sony physical qualification to decide correctness;
- materially changes the durable store contract.

## 25. Physical qualification policy

No new Sony field qualification is required merely for the isolated PackageInstaller class because
Phase E still does not wire the production runtime.

Do not claim a physical PASS.

The mandatory final M4 Sony protocol remains after the generic path is actually integrated in later
phases.

If implementation starts modifying real OverlayService/CloudControlClient/boot orchestration, stop:
that is Phase F/G scope unless a narrow compile-only reason is proven.

## 26. Required Phase E report

Create:

`docs/m4-phase-e-package-installer-orchestration-report.md`

The report must include:

- exact implementation start HEAD;
- final HEAD;
- commits and changed files;
- exact PackageInstaller API;
- dependency/import boundary proof;
- complete revision decision table;
- exact validate/prepare/encode/commit/readback/restore/arm order;
- handler resolution rules;
- commit/readback proof;
- proof ARM uses restored durable prepared state, not pre-commit state;
- stale behavior;
- same-revision behavior;
- newer-revision behavior;
- corrupt-store behavior;
- handler failure/exception matrix;
- ACK isolation proof;
- no-rollback proof after commit+arm failure;
- historical manifested/legacy compatibility results;
- real Phase D Video integration results;
- proof no production caller is switched;
- JVM counts split retained / A / B / C / D / E;
- Python counts split retained/new;
- frozen hashes;
- exact-head Android debug run id/result;
- exact-head Android 15 run id/result;
- lint/build/signing/application/permissions continuity;
- known limits;
- explicit confirmation no Phase F/G or M5+ work;
- PR #14 state.

The final verdict must be exactly one of:

- `READY FOR M4 PHASE F`
- `NOT READY FOR M4 PHASE F`

Do not merge.

STOP after the report and verdict.
