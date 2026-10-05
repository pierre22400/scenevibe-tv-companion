# SceneVibe OS — M4 TV Installation / Cache / Capabilities Architecture

Date: 2026-10-04  
Status: architecture-first development declaration; **no M4 implementation is included in this document**.

---

## 1. Milestone definition

The authoritative SceneVibe OS roadmap defines M4 as:

> generic TV installation / cache / capabilities; legacy isolated;  
> validate → prepare → commit → arm → ACK; continuity of identity / cache / reboot.

M4 is therefore a **TV-runtime generalization milestone**. It does not add Banner
execution, a generic media calendar, remote assets, partner APIs, or a production
Cloud cutover.

The milestone exists so the Companion TV stops treating the Video v1 assignment
shape as the runtime architecture itself. Video remains a supported producer, but
the terminal obtains one generic installation pipeline that later products can
reuse.

---

## 2. Frozen bases

The bases for this M4 cycle are:

- `BASE_CLOUD_M4 = 5011c91aac61a0cc6dcc74c256a15b7dee03d785`
- `BASE_TV_M4 = ecdf77bec9f93babf15239a63bf7f702fd7ca293`

The Cloud base includes the completed M3 closure and the separately qualified
UTF-8-safe FinalTrack import helper. The TV base is the qualified 0.10A
OverlayManifest runtime integration.

Production revision authority remains `SHADOW`. M4 does not authorize or
perform a Cloud cutover.

---

## 3. Evidence carried forward

The following behavior is already physically qualified and is a hard
non-regression baseline:

- assignment replacement on the Sony;
- monotone revision progression;
- ACK only after the assignment becomes durable and armed;
- no mixed or double visual revision;
- redelivery;
- hard-reboot restoration;
- post-reboot redelivery;
- seek / pause / replay continuity;
- corrected UTF-8 French rendering;
- final observed Sony diagnostics:
  `13 / 13 / 13 / 13 / NONE`.

M4 may reorganize internal ownership, but it must not weaken any of those
observed guarantees.

---

## 4. Current coupling that M4 must remove

At `BASE_TV_M4`, the required behavior exists, but the installation path is
still materially Video-shaped.

### 4.1 `CloudProtocol`

`CloudProtocol.validAssignment` validates the historical
`scenevibe.cloud.assignment.v1` envelope and directly understands:

- `finalTrackId`;
- `scenevibe.track.v1`;
- the Prime Video Android package;
- `prime_video`;
- the optional Video OverlayManifest;
- the requirement that that manifest is `product=video`, `clock=media`.

This remains a compatibility boundary, not the future OS installation core.

### 4.2 `CloudTrackRepository`

The durable store currently owns:

- `revision`;
- `runtime`;
- optional `manifest`;
- `ackRevision`.

It also directly parses `ScheduledTrack`, parses `OverlayManifest`, invokes
`VideoOverlayManifestBridge`, and loads `MediaSyncedTrackScheduler`.

The persistence semantics are useful and must be preserved, but the repository
is not generic because storage, Video validation, scheduler activation and
legacy compatibility are combined in one class.

### 4.3 `CloudControlClient.ManifestInstaller`

The Cloud client already has a seam preventing graphics logic from leaking into
transport, but the seam is still named and shaped around manifested Video
assignments:

- `install(revision, runtimeJson, manifestJson)`;
- `confirmArmed(revision)`;
- `activateLegacy(revision)`.

M4 replaces this with one product-neutral installation port.

### 4.4 `OverlayService`

`OverlayService` currently coordinates:

- durable manifested installation;
- restore;
- scheduler load;
- SceneRuntimeController arm;
- legacy renderer handoff;
- active revision.

The behavior is correct, but the orchestration belongs in a generic
installation runtime so the service does not remain the de facto package
installer.

---

## 5. Architectural rule

The M4 TV core must not know:

- FinalTrack;
- Pass0 / Pass1 / Pass2;
- Prime catalog or subtitle extraction;
- LLMs;
- account ownership;
- Cloud publication database internals;
- Banner business semantics;
- Language business semantics.

The generic TV installation core may know only:

- a device-local revision;
- an installation package kind / codec selected from a **static registry**;
- bounded execution requirements derived from the received artifact;
- terminal capabilities;
- durable package bytes / metadata;
- an armable runtime handler;
- ACK eligibility.

No dynamic class loading, plugin download, WebView or executable remote content
is introduced.

---

## 6. Target pipeline

The single M4 installation pipeline is:

```text
transport envelope
    |
    v
compatibility adapter
    |
    v
InstallRequest
    |
    v
VALIDATE
    |
    v
PREPARE
    |
    v
COMMIT
    |
    v
ARM
    |
    v
ACK permitted
```

Every stage has one meaning.

### 6.1 VALIDATE

Pure, side-effect-free validation.

It must prove:

- envelope/adaptor input is structurally valid;
- revision is valid and not older than the durable candidate;
- package kind / codec is statically known;
- payload sizes and counts are bounded;
- artifact-internal invariants hold;
- cross-artifact invariants hold;
- the package requires only declared TV capabilities.

VALIDATE performs no persistence, scheduler mutation, renderer mutation, ACK,
network access, or asset download.

### 6.2 PREPARE

Pure or memory-only construction of a bounded `PreparedInstallation`.

PREPARE may:

- parse immutable runtime objects;
- parse an OverlayManifest;
- calculate the local execution profile needed to arm the package;
- bind the selected static runtime handler;
- perform preflight checks that do not mutate durable/live state.

PREPARE must not:

- update SharedPreferences;
- change the active revision;
- show or hide an overlay;
- load a scheduler;
- send an ACK.

The prepared object must contain everything COMMIT and ARM need so those stages
do not re-interpret untrusted JSON independently.

### 6.3 COMMIT

COMMIT is the **durability boundary**.

It persists, atomically for one revision:

- revision;
- package kind / codec identity;
- bounded canonical artifact bytes needed for restore;
- installation metadata required to select the same handler after reboot.

A commit must never produce:

- runtime from revision N with manifest from revision N-1;
- package metadata from one handler with bytes from another;
- a revision that cannot be deterministically re-read.

The existing one-commit SharedPreferences guarantee may remain as the first
implementation if the package stays within the qualified bounds. M4 does not
introduce a database merely for abstraction purity.

ACK is still forbidden after COMMIT alone.

### 6.4 ARM

ARM turns the just-committed durable installation into the active runtime state.

It must:

- reconstruct/read the committed representation rather than trust an unrelated
  caller copy;
- activate exactly one handler for the revision;
- load the existing media scheduler when the handler requires it;
- arm `SceneRuntimeController` when the handler requires it;
- synchronously retire the previous visual owner before the new owner is
  considered active;
- set the active revision only after successful activation.

ARM must preserve the owner-thread rule for Android window mutation.

If ARM fails, ACK is forbidden. A failed arm must never cause a second visual
path to remain active.

### 6.5 ACK

The network layer may send the historical Video ACK only after the generic
installer returns an `ARMED` result for the exact revision.

The current v1 ACK body remains:

```json
{
  "revision": 13,
  "finalTrackId": "..."
}
```

for the Video compatibility route. `finalTrackId` is a compatibility binding
owned by the Video v1 adapter / transport, not by the generic installer.

After the server confirms the ACK, the durable acknowledged revision is updated
as today.

---

## 7. Generic TV model

M4 should introduce a small immutable internal model. Exact Java names may be
adjusted during implementation, but responsibilities must not be merged back
together.

### 7.1 `TvCapabilities`

A deterministic local descriptor of what this APK can actually execute.

At M4, it should describe at least:

- supported installation codecs / package kinds;
- supported rendering contracts;
- supported clock modes;
- supported pause behaviors;
- maximum package/artifact byte sizes;
- relevant manifest limits already enforced by the parser;
- whether remote asset acquisition exists;
- whether wall-clock execution exists.

The descriptor represents **real executable capability**, not parser
capability. Therefore, at M4:

- media-clock execution: supported;
- wall-clock execution: **not supported**;
- native OverlayManifest rendering: supported;
- legacy `scenevibe.track.v1`: supported through the compatibility handler;
- remote asset acquisition: **not supported**;
- M7 asset cache capability: **not advertised**.

The fact that `OverlayManifestParser` can parse `clock.mode=wall` does not
make wall-clock execution a capability.

### 7.2 `InstallRequest`

Product-neutral handoff from a compatibility adapter to the installer.

It should contain only bounded installation data, for example:

- revision;
- codec/package-kind id;
- immutable artifact bytes or strings;
- compatibility metadata needed by the selected handler.

It must not contain a FinalTrack object.

### 7.3 `PreparedInstallation`

Output of PREPARE.

It must be immutable and contain:

- revision;
- selected handler id;
- parsed/validated runtime objects;
- canonical bytes to persist;
- bounded local requirements/capability decision;
- no secret;
- no network client.

### 7.4 `InstallationHandler`

A statically registered handler for one supported installable runtime shape.

Conceptual operations:

```text
validate(request, capabilities)
prepare(request, capabilities)
encodeForCache(prepared)
restoreFromCache(snapshot, capabilities)
arm(preparedOrRestored, runtimePorts)
```

The registry is hard-coded at build time. No reflection, URL loading, APK
extensions, JavaScript or plugin marketplace exists.

### 7.5 `InstallationStore`

Owns atomic durable state only.

It must not parse FinalTrack, inspect Prime, invoke a scheduler, render a View or
send an ACK.

### 7.6 `PackageInstaller`

The one orchestration authority for:

```text
validate -> prepare -> commit -> arm
```

It returns a bounded result such as:

- `ARMED`;
- `STALE`;
- `UNSUPPORTED_CAPABILITY`;
- `INVALID_PACKAGE`;
- `CACHE_FAILED`;
- `ARM_FAILED`.

Those codes are bounded. No content, secrets, stack traces, URLs or raw JSON are
exposed in diagnostics.

---

## 8. Static handlers at M4

M4 needs only enough handlers to preserve the current qualified behavior.

### 8.1 Video manifested handler

Owns the existing Video-specific bridge:

- parse `scenevibe.track.v1`;
- parse OverlayManifest;
- run `VideoOverlayManifestBridge`;
- require Video/media coherence;
- prepare `ScheduledTrack` + `OverlayManifest`;
- arm scheduler + SceneRuntimeController.

The **generic installer must not call `VideoOverlayManifestBridge` directly**.
Only this compatibility handler may do so.

### 8.2 Legacy Video text handler

Preserves the historical no-manifest path:

- parse `scenevibe.track.v1`;
- prepare ScheduledTrack;
- commit through the generic store;
- arm the existing scheduler / legacy visual owner;
- explicitly disarm any previously manifested revision.

This handler exists for compatibility. It is isolated so future generic product
paths do not inherit legacy semantics.

No Banner handler is added in M4.

---

## 9. Compatibility adapter boundary

The historical Cloud v1 envelope stays supported.

A dedicated adapter should convert:

```text
scenevibe.cloud.assignment.v1
    -> VideoV1 installation request
    -> generic PackageInstaller
```

The adapter owns:

- `finalTrackId`;
- `trackId`;
- Prime/video checks required by the frozen v1 contract;
- whether the assignment maps to manifested Video or legacy Video;
- ACK compatibility binding.

The generic installer never sees `finalTrackId`.

This preserves byte/wire compatibility while making the TV runtime internally
generic.

---

## 10. Cache model and reboot continuity

M4 must preserve these durable invariants:

1. one current durable installation revision per device;
2. all artifacts for that revision are committed atomically;
3. acknowledged revision is stored separately and never advanced before server
   confirmation;
4. a reboot can identify the stored handler without guessing from historical
   keys;
5. restore runs through the same handler validation/capability checks used by a
   live installation;
6. corrupt or unsupported durable state fails closed;
7. no cache migration may silently invent a new assignment revision.

### 10.1 Migration of existing cache

Existing qualified installs use keys:

- `revision`;
- `runtime`;
- optional `manifest`;
- `ackRevision`.

M4 must support an explicit, idempotent migration or compatibility read of this
state.

The migration must:

- preserve the exact revision;
- preserve the exact acknowledged revision;
- preserve runtime/manifest bytes;
- infer only the handler that is unambiguous from the existing qualified shape;
- perform no Cloud Send;
- perform no revision increment;
- keep installation identity and device credentials untouched.

A legacy cache without a manifest maps only to the legacy Video handler. A cache
with a coherent manifest maps only to the manifested Video handler. Any
ambiguous/corrupt combination fails closed.

---

## 11. Redelivery and stale revisions

The currently qualified semantics remain:

- `revision < durableRevision`: reject as stale;
- `revision == durableRevision`: no re-persist; re-confirm / restore the exact
  durable installation is armable, then ACK may be repeated;
- `revision > durableRevision`: full validate → prepare → commit → arm before
  ACK.

The generic installer owns those rules so they are not separately reimplemented
for every product.

---

## 12. Capabilities are local truth in M4

M4 introduces the canonical TV capability model and enforces it locally.

M4 does **not** yet require:

- a public capabilities API;
- partner-visible capabilities;
- Cloud-side device targeting by capability;
- automatic transcoding/fallback;
- capability negotiation with third parties.

Those may be added later only when a real second producer requires them.

The M4 implementation may expose bounded capabilities in local diagnostics or
tests, but must not leak credentials or content.

---

## 13. Separation from later milestones

### M5 — not in M4

Do not replace or generalize `MediaSyncedTrackScheduler` into SceneEvent here.
M4 may depend on the existing scheduler through a port. M5 owns the generic
SceneEvent / media-calendar core.

### M6 — not in M4

Do not execute Banner, do not invent the wall-clock anchor, and do not fake a
Banner as a FinalTrack or Video assignment.

### M7 — not in M4

Do not add HTTP/CDN asset download, shared asset cache, quotas, hashes or image
prefetch. M7 owns controlled assets.

### M8+ — not in M4

No Banner product UX, MediaContext generalization, partner contract, Connect,
SDK/API or Language shortcut.

---

## 14. Android / security invariants

M4 must preserve:

- application id / package name;
- signing chain;
- current permission set;
- foreground-service type;
- no player transport controls;
- passive MediaSession observation only;
- no WebView;
- no dynamic code loading;
- no downloaded executable plugins;
- no network access from SceneRenderer / installation handlers;
- UI/window mutations on the Android owner thread;
- secrets never written into diagnostics or package cache;
- reset semantics and installation identity rotation unchanged unless a separate
  explicitly qualified requirement says otherwise.

---

## 15. Proposed implementation phases

No phase after A should start until the preceding phase is green.

### Phase A — characterization

Before refactoring:

- freeze current Java/Python test counts;
- add characterization tests for the existing
  `validate -> durable install -> arm -> ACK` behavior;
- add reboot/restore and same-revision redelivery characterization;
- add cache fixture(s) representing the current qualified revision shape;
- prove current Video v1 envelope behavior byte/field compatible.

No production behavior change.

### Phase B — generic model and capabilities

Introduce:

- `TvCapabilities`;
- `InstallRequest`;
- `PreparedInstallation`;
- bounded install result codes;
- static `InstallationHandler` registry.

No current caller is switched yet.

### Phase C — generic durable store

Extract persistence from `CloudTrackRepository` into `InstallationStore`.

Prove:

- atomic artifact set;
- exact revision preservation;
- exact ACK revision preservation;
- legacy cache migration/compatibility read;
- corruption fail-closed;
- no scheduler / renderer imports in the store.

### Phase D — Video compatibility handlers

Move existing behavior behind:

- manifested Video handler;
- legacy Video handler.

Keep `VideoOverlayManifestBridge` only in the Video handler.

Prove outputs and rejection cases are equivalent to the frozen baseline.

### Phase E — PackageInstaller orchestration

Make one orchestrator own:

```text
validate -> prepare -> commit -> arm
```

Move revision/redelivery rules into that orchestrator.

ACK must remain outside and strictly after an `ARMED` result.

### Phase F — Cloud v1 adapter

Refactor `CloudControlClient` so it:

- validates transport/auth envelope through the v1 compatibility boundary;
- creates the install request;
- calls the generic installer on the owner thread;
- ACKs only after `ARMED`;
- contains no scheduler, renderer or Video bridge logic.

### Phase G — reboot / diagnostics / cleanup

Restore through the generic store + handler registry.

Update diagnostics from “cached track” terminology toward installation/package
terminology while preserving old fields until compatibility is proven.

Do not delete old paths until regression evidence demonstrates identical
behavior.

---

## 16. Required automated gates

At minimum M4 must add/retain tests proving:

1. generic installer imports no FinalTrack / pipeline / Prime-specific business
   model;
2. static capability registry rejects an unsupported clock/contract;
3. no wall-clock package can arm in M4;
4. no remote-asset requirement is advertised as supported;
5. validate failure writes nothing;
6. prepare failure writes nothing;
7. commit is atomic for all artifacts of one revision;
8. commit failure leaves prior durable revision intact;
9. arm failure never permits ACK;
10. same-revision redelivery does not rewrite the cache and must re-confirm arm;
11. stale revision cannot replace durable state;
12. reboot restores the exact handler + revision;
13. legacy cache migration preserves revision and acknowledged revision exactly;
14. manifested Video cross-contract failure remains fail-closed;
15. legacy Video remains behavior-compatible;
16. only one visual owner is active during replacement;
17. stale callbacks from a replaced revision remain neutralized;
18. Unicode text round-trips unchanged through cache/restore;
19. diagnostics remain bounded and secret-free;
20. package/application/signing/permission boundaries remain unchanged.

All existing Python boundary tests, JVM tests, lint, debug build, Cloud-origin
build and release-signing continuity gates remain mandatory.

---

## 17. Physical Sony qualification for M4

M4 changes the TV installation/cache architecture, so a Sony gate is mandatory.

Use the same real target and preserve the existing installation / pairing where
possible.

Minimum field protocol:

1. upgrade the APK with `adb install -r`, no uninstall;
2. verify installation identity, Cloud device identity and pairing continuity;
3. verify the previously cached qualified revision restores through the M4
   migration/compatibility path;
4. send a new manifested Video revision;
5. verify coherent cache / ACK / assignment / successful-ACK counters;
6. while a scene is visible, replace it with another revision;
7. verify exactly one visual owner, no old-scene resurrection;
8. same-revision redelivery;
9. pause / resume;
10. backward seek / forward seek;
11. eligibility loss / return;
12. hard reboot without a new Send;
13. verify exact revision + package restoration;
14. post-reboot redelivery;
15. verify accented French Unicode remains correct;
16. inspect logcat for SceneVibe AndroidRuntime, Looper, thread/window or
    installer exceptions.

A successful field gate must record exact revisions and the final diagnostics.

---

## 18. Definition of done

M4 is COMPLETE only when all of the following are true:

- one generic TV installation pipeline owns
  `validate -> prepare -> commit -> arm`;
- ACK is impossible before successful arm;
- a canonical `TvCapabilities` model expresses what the APK can really execute;
- unsupported capabilities fail before durable/live mutation;
- Video v1 is an adapter/handler, not the installation core;
- legacy no-manifest behavior is isolated behind its own compatibility handler;
- cache persistence is generic and handler-identifiable after reboot;
- old qualified cache migrates/restores without revision or identity loss;
- current Cloud v1 wire remains compatible;
- all automated gates are green;
- the Sony M4 physical protocol passes;
- production remains `SHADOW` unless a separate later decision explicitly
  authorizes cutover;
- no M5/M6/M7 scope has been pulled forward.

---

## 19. Immediate next action

The next commit after this architecture document must be **Phase A
characterization only**.

Do not begin the generic refactor before the baseline tests and current cache
fixtures exist. The purpose is to make every later extraction mechanically
provable against the already-qualified Sony behavior.
