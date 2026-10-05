# SCENEVIBE OS — WORK — M4 PHASE C — Generic Durable Store

Date: 2026-10-04  
Repository: `pierre22400/scenevibe-tv-companion`  
Branch: `work/scenevibe-os-m4-tv-installation-001`  
PR: #14 — must remain **OPEN / DRAFT / unmerged**.

## 1. Mission

Implement **M4 Phase C only**: extract durable installation persistence from the historical
`CloudTrackRepository` into a product-neutral `InstallationStore`, while preserving the
qualified Video v1 runtime behavior byte-for-byte and field-for-field.

Phase C is a persistence refactor and new generic storage capability. It is **not** the handler
phase, installer-orchestration phase, Cloud-adapter phase, reboot cutover phase, Banner phase,
or Production cutover.

The authoritative architecture is:

- `docs/scenevibe-os-m4-tv-installation-architecture.md`

The immediately preceding closure is:

- `docs/m4-phase-b-generic-model-capabilities-report.md`

Phase B closure HEAD:

- `2efc305d303f0abbfac708c168c76eb24ce4c491`

Frozen milestone bases remain:

- `BASE_TV_M4 = ecdf77bec9f93babf15239a63bf7f702fd7ca293`
- `BASE_CLOUD_M4 = 5011c91aac61a0cc6dcc74c256a15b7dee03d785`

Production revision authority remains **SHADOW**.

This work-order commit is documentation-only. Before implementation, verify that the branch HEAD
is exactly the commit containing this file and that its ancestry contains the Phase B closure HEAD
above. Do not reset to the Phase B SHA and thereby discard this work order.

## 2. Read completely before editing

Read in full, from the exact branch HEAD:

1. `docs/scenevibe-os-m4-tv-installation-architecture.md`
2. this work order
3. `docs/m4-phase-b-generic-model-capabilities-report.md`
4. `docs/m4-phase-a-characterization-report.md`
5. `app/src/main/java/com/scenevibe/tvcompanionpoc/CloudTrackRepository.java`
6. `app/src/test/java/com/scenevibe/tvcompanionpoc/M4PhaseAAtomicStoreTest.java`
7. `app/src/test/java/com/scenevibe/tvcompanionpoc/CloudTrackRepositoryTest.java`
8. `app/src/test/java/com/scenevibe/tvcompanionpoc/CloudManifestCacheTest.java`
9. the Phase B model classes under
   `app/src/main/java/com/scenevibe/tvcompanionpoc/installation/`

GitHub is the source of truth.

## 3. Required Phase C outcome

At the end of Phase C there must be one product-neutral durable store capable of representing a
bounded committed installation snapshot and its acknowledged revision without importing or
executing Video runtime behavior.

The store must own persistence mechanics and durable invariants. It must **not** own parsing,
scheduler loading, rendering, window mutation, network ACK transmission, transport, FinalTrack,
Prime-specific business rules, OverlayManifest semantic validation, or
`VideoOverlayManifestBridge`.

The historical `CloudTrackRepository` may remain as the current Video-shaped compatibility
repository during Phase C, but it must no longer be the architectural owner of the Android
SharedPreferences persistence mechanism. A narrow temporary compatibility adapter/facade is
allowed so all current callers keep their existing behavior while persistence mechanics are
moved underneath to the new store.

No production caller is switched to `InstallationHandler`, `PackageInstaller`, or the future
generic install pipeline in this phase.

## 4. Required generic store model

Introduce a small, bounded `InstallationStore` under
`com.scenevibe.tvcompanionpoc.installation`.

A separate immutable snapshot/value type may be introduced if it materially clarifies the
contract. Do not add abstraction layers merely for naming.

The durable generic snapshot must preserve, at minimum:

- exact positive installation revision;
- exact codec/package-kind id;
- exact static handler id needed for deterministic restore later;
- the complete bounded canonical artifact set as opaque bytes;
- enough format/version metadata to reject unknown or corrupt persisted representations;
- acknowledged revision stored separately from the installation snapshot.

The store must return defensive immutable/copying values. It must never expose a mutable internal
byte array.

The store must not retain:

- scheduler instances;
- renderer/window/service objects;
- Android Views;
- network clients;
- credentials or secrets;
- FinalTrack objects;
- arbitrary opaque Java objects;
- unbounded diagnostics or raw payload text in error messages.

A bounded closed result for store operations is permitted. Do not create a new unbounded error
surface.

## 5. Persistence backend and atomicity

The Android implementation may continue to use app-private `SharedPreferences`; M4 does not add a
database for abstraction purity.

The generic committed artifact set for one revision must be atomic from the store contract's point
of view:

- revision;
- codec id;
- handler id;
- artifact metadata;
- all canonical artifact bytes.

A failed commit must leave the previously durable generic snapshot intact.

The encoding format is an implementation choice, but it must be:

- explicitly versioned;
- deterministic;
- bounded before allocation/copy where practical;
- capable of exact byte round-trip;
- unambiguous;
- rejected if partially present, malformed, over-bound, unsupported, or internally inconsistent.

If Base64 or another textual encoding is used because SharedPreferences stores strings, prove exact
byte round-trip including non-ASCII UTF-8 payloads and account for encoding growth in bounds. Do
not normalize Unicode.

Do not use Java serialization, reflection, dynamic class loading, downloaded code, WebView, or
plugin discovery.

## 6. ACK durability semantics

The acknowledged revision remains durable state separate from the committed installation snapshot.

Required behavior:

- absent ACK state means `0`;
- a confirmed ACK may only be durably recorded for the exact current durable revision;
- a wrong/stale/future revision must not change the acknowledged revision;
- persistence failure must leave the previous acknowledged revision intact;
- acknowledged revision must never advance merely because COMMIT succeeded;
- store reads must preserve the exact historical `ackRevision` value when valid;
- no Phase C method sends an HTTP ACK.

Do not change the historical wire ACK body or `finalTrackId` compatibility binding.

## 7. Historical cache compatibility — read, do not invent

The qualified historical cache uses app-private preference file `cloud_track` with:

- `revision`;
- `runtime`;
- optional `manifest`;
- `ackRevision`.

Phase C must support a deterministic **compatibility read** of that state. Prefer a non-mutating
compatibility read over eager migration in this phase unless a strictly necessary implementation
constraint proves otherwise.

Compatibility read requirements:

1. preserve the exact historical revision;
2. preserve the exact valid acknowledged revision;
3. preserve the exact historical runtime/manifest String content, deriving canonical UTF-8 bytes
   without normalization, truncation, rewriting, or reserialization;
4. infer only the unambiguous legacy package shape from the historical key shape:
   - runtime present, manifest absent -> legacy Video package shape;
   - runtime present, manifest present -> manifested Video package shape;
5. perform no network operation;
6. perform no scheduler/renderer operation;
7. perform no revision increment;
8. leave installation identity, pairing and credentials untouched;
9. perform no write merely because a legacy state was read.

The store may use fixed internal ids already defined/qualified by Phase B. Do not create a public
wire contract from those ids.

### 7.1 Persistence corruption that must fail closed in Phase C

At the storage layer, fail closed for at least:

- non-numeric, zero, negative or overflowed revision;
- revision without runtime;
- runtime without revision;
- manifest without a complete historical base tuple;
- malformed/unsupported generic store version;
- incomplete generic metadata/artifact set;
- invalid artifact count/id/size;
- malformed textual encoding of generic artifact bytes;
- acknowledged revision that is malformed, negative or greater than the durable revision;
- coexistence states that are ambiguous according to the chosen versioning/precedence rules.

If a generic-format marker/snapshot exists but is corrupt, do **not** silently fall back to an older
legacy cache and thereby resurrect stale state.

Reading corruption must not load, arm, render or ACK anything.

### 7.2 Semantic validation deliberately remains outside Phase C

Phase C must **not** claim to prove Video runtime/manifest semantic coherence. In particular,
do not move into the store:

- `TrackParser`;
- `OverlayManifestParser`;
- `VideoOverlayManifestBridge`;
- Prime target/package checks;
- scene/runtime timing cross-contract validation.

Those responsibilities belong to the static Video handlers in Phase D.

Therefore Phase C's "corruption fail-closed" claim is specifically about the durable representation,
bounds, metadata and compatibility tuple. The existing production `CloudTrackRepository` semantic
validation remains unchanged for current callers.

## 8. Extraction from CloudTrackRepository

Refactor narrowly enough that Android persistence mechanics are owned by
`InstallationStore`, not duplicated indefinitely in `CloudTrackRepository`.

The preferred shape is:

- `InstallationStore` owns access to the existing app-private `cloud_track` preferences and
  the low-level atomic edit/commit mechanics;
- `CloudTrackRepository` retains its current Video parsing, manifested/legacy validation,
  scheduler loading and bounded compatibility results during Phase C;
- a thin compatibility adapter may expose the historical get/save/saveAck/clear behavior required
  by `CloudTrackRepository` while delegating the actual preference operations to the store/backend;
- existing test seams may be retained if needed to prevent unnecessary test churn.

Do not rewrite the runtime architecture in Phase C.

The existing current production behavior must remain:

- manifested install validates before commit, commits one complete historical tuple, then loads;
- legacy replacement removes stale manifest in the same commit;
- restore corruption never half-restores;
- same/stale revision behavior remains as characterized;
- ACK persistence remains after server confirmation only;
- reset/disconnect semantics remain unchanged.

If a minimal alternative extraction is cleaner, it is allowed only if the same ownership and
non-regression properties are demonstrably satisfied.

## 9. Import/dependency boundaries

Add an automated boundary proof that `InstallationStore` and any generic snapshot class import or
reference no:

- `MediaSyncedTrackScheduler`;
- `SceneRuntimeController`;
- renderer/window classes;
- `OverlayService`;
- `CloudControlClient`;
- `VideoOverlayManifestBridge`;
- `TrackParser`;
- `OverlayManifestParser`;
- FinalTrack/pipeline models;
- Prime-specific constants/business checks;
- networking APIs;
- reflection/dynamic loading APIs.

Android persistence types such as `Context` and `SharedPreferences` are allowed in the Android
backend. JDK collections, charset and bounded encoding helpers are allowed.

The store itself must perform no network access.

## 10. Mandatory tests

Retain every existing test. Add deterministic Phase C tests covering at minimum:

1. generic snapshot atomic commit and exact readback;
2. exact codec id + handler id + revision preservation;
3. exact artifact byte round-trip, including French accents, composed/decomposed Unicode,
   ligature, apostrophe and emoji;
4. failed generic commit leaves previous durable generic snapshot byte-identical;
5. acknowledged revision is separate and only advances for the exact current revision;
6. failed ACK persistence leaves the prior ACK unchanged;
7. generic corrupt/incomplete snapshot fails closed;
8. corrupt generic state never falls back to stale legacy state;
9. empty store is cleanly empty;
10. historical manifested fixture compatibility read preserves revision 13 / ACK 13 and exact
    runtime/manifest text;
11. historical legacy fixture compatibility read preserves revision 14 / ACK 13 and exact runtime
    text with no invented manifest;
12. malformed historical revision fails closed;
13. incomplete historical tuple fails closed;
14. malformed/out-of-range historical ACK fails closed according to the Phase C generic-read
    contract;
15. compatibility read performs zero writes;
16. compatibility read performs zero scheduler/renderer/network actions;
17. `CloudTrackRepository` current manifested install still makes one complete commit before load;
18. legacy replacement still removes manifest atomically;
19. all Phase A characterization remains green;
20. all Phase B model/capability tests remain green;
21. no current production caller switches to generic handlers/PackageInstaller;
22. no scheduler/renderer imports in the store;
23. application id, permissions, signing inputs and frozen fixtures are unchanged.

Use the existing Phase A cache fixtures, not invented replacements:

- `app/src/test/resources/m4/video-manifested-cache-v1.json`
  SHA-256 `76d109c4b69f3e707912449f87e3fa6b9081e4564a12c77c633ae7ed7563beb1`
- `app/src/test/resources/m4/video-legacy-cache-v1.json`
  SHA-256 `1676ed2033ec0e589509da7bdab3294ed8b5949153eeb2cfdb539aa23409cc8c`

Also preserve frozen M1 evidence:

- 7 Cloud envelopes:
  `19ee6fc3e2eba7009c9d345c33a63ca5617621fac9c8f5618cb24ee2932d6eab`
- 94-case OverlayManifest corpus:
  `c78addf877b277e38f66c12274e4c8ed2ce12a503d883e27fd9950215cafbf30`

## 11. Baseline and CI accounting

Phase C starts from the Phase B exact validated inventory:

- JVM: **247 PASS / 0 FAIL / 1 SKIP**, 248 cases;
- of those, Phase A: 30 PASS;
- Phase B: 39 PASS;
- Python: **19 PASS / 0 FAIL / 0 SKIP**.

The sole private SKIP remains:

- `M1CloudInteropTest.originalColumboProjectionIsInstallable`
- opt-in via `SCENEVIBE_M1_SONY_FIXTURES`.

Do not convert missing private fixtures into a false PASS.

Update the CI inventory/summary only as much as needed to add an explicit Phase C test bucket while
retaining the prior baseline, Phase A and Phase B accounting. The summary must read real Gradle
JUnit XML, as in the existing qualified flow.

After implementation and every localized fix, require on the exact pushed HEAD:

- all Python boundary tests PASS;
- full Gradle JVM suite PASS with only the known private SKIP;
- `lintDebug` PASS;
- `assembleDebug` PASS;
- LAN DEV compile PASS;
- Consumer Cloud-origin compile PASS;
- stable signing/certificate continuity PASS;
- Android 15 / API 35 standard-platform smoke SUCCESS.

Inspect the exact-head workflow metadata, jobs, steps and relevant logs. Do not infer success merely
from a PR badge.

Do not claim new Sony physical qualification from JVM, CI or Android 15 standard-image evidence.

## 12. Protected non-regression surface

Do not change unless strictly required for the narrow persistence extraction, and then prove the
exact behavior remains characterized:

- Cloud v1 envelope fields;
- ACK wire body;
- `finalTrackId` transport binding;
- scheduler timing semantics;
- SceneRuntimeController behavior;
- owner-thread window mutation;
- pairing/installation identity/credentials;
- disconnect/reset behavior;
- Android manifest permissions;
- application id/package name;
- signing chain;
- frozen M1 fixtures;
- Phase A cache fixtures;
- production revision authority.

Production remains **SHADOW**.

## 13. Explicitly forbidden in Phase C

Do not implement:

- manifested Video `InstallationHandler`;
- legacy Video `InstallationHandler`;
- `PackageInstaller`;
- generic validate -> prepare -> commit -> arm orchestration;
- Cloud v1 -> `InstallRequest` adapter;
- CloudControlClient cutover;
- generic reboot/restore cutover;
- generic diagnostics cleanup/renaming;
- deletion of old cache fields;
- Banner;
- wall-clock execution;
- SceneEvent/M5 scheduler generalization;
- remote/CDN asset acquisition or M7 asset cache;
- WebView;
- dynamic code/plugin loading;
- new Android permissions;
- Production cutover;
- Cloud repository modifications.

No Phase D/E/F/G or M5+ work.

## 14. Documentation/code quality rules

Do not regress existing code and do not remove existing docstrings/Javadocs.

Every new non-trivial class, constructor, method and helper must have a concise pedagogical
Javadoc/docstring explaining its invariant and side effects.

For any new or modified Python file:

- preserve existing docstrings;
- add a pedagogical module banner docstring after imports if the file does not already contain the
  required banner;
- every function, including utilities, must have a docstring.

Keep changes narrow. Do not reformat unrelated files.

## 15. Git / PR policy

Work only on:

`work/scenevibe-os-m4-tv-installation-001`

PR #14 remains DRAFT.

Use normal commits and normal push only.

Do not:

- merge;
- rebase;
- force-push;
- change TV `main`;
- modify the Cloud repository;
- retarget the PR;
- mark the PR ready for review.

Recommended commit shape:

1. generic durable store + legacy persistence extraction/compatibility reader;
2. Phase C tests + boundary/CI accounting;
3. Phase C report.

A localized correction discovered by self-audit may add another narrow commit.

## 16. WORK policy — implement, self-audit, fix localized defects

This is an implementation cycle, not an audit-only cycle.

After implementation:

1. self-audit the exact resulting diff against this work order and the M4 architecture;
2. inspect all newly introduced persistence paths for partial-write/fallback hazards;
3. run the complete relevant suites;
4. push;
5. inspect exact-head CI;
6. if a small deterministic defect is found, fix it in the same cycle, add/strengthen a regression
   test, push normally, rerun the affected/full required gates, and audit the final delta again.

Stop and report instead of guessing if a problem:

- requires Phase D+;
- requires a product decision;
- changes runtime semantics beyond persistence extraction;
- requires a Cloud contract change;
- requires Sony physical qualification to decide correctness;
- makes the historical cache shape genuinely ambiguous in a way not settled by the architecture.

## 17. Required final report

Create:

`docs/m4-phase-c-generic-durable-store-report.md`

The report must include:

- exact implementation start HEAD;
- final HEAD;
- commits and changed files;
- exact classes/types introduced;
- exact `CloudTrackRepository` extraction delta;
- generic snapshot durable schema/version and bounds;
- atomicity proof;
- ACK separation/preservation proof;
- historical manifested and legacy compatibility-read behavior;
- corruption/fail-closed matrix;
- exact statement of what semantic validation remains deferred to Phase D;
- import/dependency-boundary proof;
- proof that no production caller switched to handlers/PackageInstaller;
- JVM counts split into retained baseline / Phase A / Phase B / Phase C;
- Python counts split retained/new;
- fixture hashes;
- exact-head Android debug run id/result;
- exact-head Android 15 run id/result;
- lint/build/signature/application/permissions continuity;
- known limits;
- confirmation of no Phase D/E/F/G or M5+ work;
- PR #14 state.

The final verdict must be exactly one of:

- `READY FOR M4 PHASE D`
- `NOT READY FOR M4 PHASE D`

Do not merge.

STOP after the report and verdict.
