# SCENEVIBE OS — M4 PHASE B — Generic Model & Capabilities — WORK ORDER

Date: 2026-10-04  
Repository: `pierre22400/scenevibe-tv-companion`  
Branch: `work/scenevibe-os-m4-tv-installation-001`

## 1. Source of truth

The authoritative M4 architecture remains:

`docs/scenevibe-os-m4-tv-installation-architecture.md`

Phase A characterization is complete and recorded in:

`docs/m4-phase-a-characterization-report.md`

Phase B must be implemented strictly as defined in section **15 — Phase B — generic model and capabilities** of the architecture document.

Do not reconstruct the architecture from assumptions. Read the current branch and the real production code before making changes.

## 2. Git truth expected before starting

Expected Phase A HEAD before this work-order commit:

`a3aaf94bfe0b7bc439460e10f5102fa4339c602c`

Frozen bases:

- `BASE_TV_M4 = ecdf77bec9f93babf15239a63bf7f702fd7ca293`
- `BASE_CLOUD_M4 = 5011c91aac61a0cc6dcc74c256a15b7dee03d785`

PR:

- TV PR #14
- must remain OPEN / DRAFT / non-merged
- branch `work/scenevibe-os-m4-tv-installation-001`

Production Cloud revision authority remains **SHADOW**.

STOP if the current branch does not descend from the expected Phase A state or if main/base truth has materially diverged.

## 3. Mission — PHASE B ONLY

Implement only the generic immutable TV installation model and local capabilities layer.

Introduce the responsibilities described by the architecture for:

- `TvCapabilities`
- `InstallRequest`
- `PreparedInstallation`
- bounded installation result/status codes
- `InstallationHandler`
- a **static build-time InstallationHandler registry**

No current production caller is switched to the new model in Phase B.

The existing Video v1 install/cache/arm/ACK path must continue to run exactly as before.

## 4. Hard scope boundary

Phase B MUST NOT implement Phase C, D, E, F or G.

Therefore do not:

- extract `InstallationStore` from `CloudTrackRepository`
- migrate the durable cache
- switch restore to the generic model
- move Video behavior into manifested/legacy handlers
- route `CloudControlClient` through the new installer
- introduce `PackageInstaller` orchestration
- move revision/redelivery authority
- change ACK ordering or wire
- refactor scheduler ownership
- change renderer/window ownership
- start generic diagnostics migration

Also forbidden:

- M5 SceneEvent/media-calendar work
- M6 Banner execution
- M7 remote asset/cache/download work
- M8+ partner/Connect/Language work
- dynamic class loading
- reflection-based plugin discovery
- WebView
- remote executable content
- network access from installation handlers
- production cutover

## 5. Required architectural properties

### 5.1 TvCapabilities

Create a deterministic immutable local descriptor representing **actual executable capability**, not parser capability.

At M4 Phase B the canonical truth must express at least:

- supported installation codec/package-kind identifiers
- supported rendering contracts
- supported clock modes
- supported pause behaviors
- maximum package/artifact sizes
- relevant already-qualified manifest limits
- remote asset acquisition support
- wall-clock execution support

Required current truth:

- media-clock execution: supported
- wall-clock execution: NOT supported
- native OverlayManifest rendering: supported
- legacy `scenevibe.track.v1`: supported as current compatibility capability
- remote asset acquisition: NOT supported
- M7 asset cache: NOT advertised

Parsing `clock.mode=wall` must never cause wall-clock execution to be advertised.

Capabilities must be bounded, deterministic and safe to expose in local diagnostics/tests without secrets or content.

### 5.2 InstallRequest

Introduce a product-neutral immutable handoff model.

It may contain only bounded installation data such as:

- revision
- codec/package-kind id
- immutable artifact bytes/strings
- bounded compatibility metadata required to select/prepare a handler

It must not contain or import:

- FinalTrack
- Pass0/Pass1/Pass2
- Prime catalog/subtitle extraction
- LLM types
- Cloud database/publication internals
- account ownership
- Banner/Language business semantics

Do not change the current Cloud v1 envelope or callers yet.

### 5.3 PreparedInstallation

Introduce an immutable PREPARE output model able to carry, at minimum:

- revision
- selected handler id
- parsed/validated runtime object references or immutable prepared values
- canonical bounded bytes intended for future persistence
- local execution/capability decision
- no secret
- no network client

Phase B does not persist or arm it.

### 5.4 Installation result codes

Introduce a bounded result/status vocabulary suitable for the future installer.

The architecture allows outcomes such as:

- `ARMED`
- `STALE`
- `UNSUPPORTED_CAPABILITY`
- `INVALID_PACKAGE`
- `CACHE_FAILED`
- `ARM_FAILED`

Phase B may define the minimal stable vocabulary required for the model/tests, but it must be bounded and must not carry:

- raw JSON
- content
- URLs
- stack traces
- credentials/secrets

Do not wire these results into current transport yet.

### 5.5 InstallationHandler

Introduce the product-neutral static handler contract described by M4.

Conceptual responsibilities:

```text
validate(request, capabilities)
prepare(request, capabilities)
encodeForCache(prepared)
restoreFromCache(snapshot, capabilities)
arm(preparedOrRestored, runtimePorts)
```

Phase B defines the contract and static registration mechanism only.

Do not move the current Video manifested/legacy implementation behind these handlers yet; that is Phase D.

The generic handler interface must not import FinalTrack/Prime/business pipeline types.

### 5.6 Static registry

Implement a hard-coded/build-time registry abstraction with:

- deterministic lookup by handler/codec/package-kind id
- rejection of unknown ids
- no reflection
- no URL/plugin loading
- no dynamic class loading
- no downloaded handlers

The registry must be testable independently.

Do not create a plugin marketplace or generalized extension framework.

## 6. Non-regression invariants

Phase B is an additive model layer. Existing runtime behavior must remain byte/behavior compatible.

The complete Phase A baseline remains mandatory:

- Python boundary: 15 PASS
- existing JVM baseline preserved
- Phase A characterization tests preserved
- current total before Phase B: 208 PASS / 1 private SKIP
- 7 M1 Cloud envelopes unchanged
- 94-case corpus unchanged
- signing chain unchanged
- package/application id unchanged
- permission set unchanged
- owner-thread Android window rule unchanged
- current Video ACK body unchanged
- identity/pairing/credentials behavior unchanged
- current cache keys/format unchanged
- current scheduler behavior unchanged

Do not delete or weaken Phase A tests.

## 7. Phase B tests required

Add focused tests proving at minimum:

1. the new generic model imports no FinalTrack / pipeline / Prime-specific business model
2. `TvCapabilities` advertises media clock and rejects/does not advertise wall clock
3. remote asset acquisition is false/not advertised
4. M7 asset cache capability is absent/not advertised
5. native OverlayManifest rendering capability is present
6. legacy track compatibility capability is represented
7. unknown codec/package-kind lookup is rejected
8. static registry is deterministic and immutable from callers
9. `InstallRequest` enforces bounds / rejects invalid revision or oversized bounded fields where applicable
10. `PreparedInstallation` is immutable and contains no network/secrets ownership
11. bounded result codes cannot carry arbitrary content
12. no production caller references the new generic install path yet
13. no current cache/write/ACK/renderer side effect is introduced by constructing/validating these models
14. all Phase A characterization tests still pass unchanged
15. Python architectural boundary remains green
16. 7-envelope and 94-corpus fixture hashes remain unchanged

Prefer compile-time/import-boundary tests where they provide stronger evidence than comments.

## 8. Package placement and dependency direction

Inspect the actual Java package layout before choosing exact paths/names.

Keep the generic model in a package that can remain independent of:

- Cloud transport details
- Video bridge
- Prime/media identity
- scheduler implementation
- Android window/rendering implementation

Video/transport may depend on the generic model later.

The generic model must not depend back on Video/transport.

Do not move existing production classes solely to obtain naming symmetry in Phase B.

## 9. Implementation style

Keep the change small and explicit.

Prefer:

- final/immutable value objects
- enums or closed bounded code sets
- defensive copies for byte arrays/collections
- explicit size/count bounds
- deterministic ordering
- build-time registration
- package-private constructors/factories where useful to protect invariants

Avoid:

- generic frameworks
- reflection
- service loaders if they create dynamic discovery semantics
- DI frameworks
- abstractions for hypothetical future producers not required by M4
- premature persistence/runtime ports beyond what the Phase B model contract needs

## 10. CI / validation

Before push:

- run the new Phase B tests
- run the complete JVM suite
- run Python boundary tests
- run lint/build gates available in the repo
- verify M1 envelope fixture hash unchanged
- verify 94-case corpus hash remains
  `c78addf877b277e38f66c12274e4c8ed2ce12a503d883e27fd9950215cafbf30`

After push, inspect the exact HEAD GitHub checks:

- Android debug APK
- Android 15 / API 35 smoke

Both must be SUCCESS on the exact Phase B HEAD.

Do not claim physical Sony qualification from CI/emulator/JVM evidence.

## 11. Git / PR policy

Work only on:

`work/scenevibe-os-m4-tv-installation-001`

PR #14 remains DRAFT.

Normal commits and normal push only.

Do not:

- merge
- rebase
- force-push
- change TV main
- modify Cloud repo
- merge the old M2 qualification branch

Use narrow commits. A recommended shape is:

1. generic model + capabilities + registry
2. Phase B tests/boundary gates
3. Phase B report

Exact commit count may differ if the implementation naturally groups differently.

## 12. WORK policy — implement + self-audit + fix localized defects

This cycle is not audit-only.

Implement Phase B, then self-audit the exact resulting HEAD.

If you find a localized defect whose cause and minimal correction are clear:

- fix it yourself in this same cycle
- add/durden the regression test
- push the correction
- re-run relevant CI
- re-check only the affected delta

Do not stop with a separate "micro-fix needed" cycle for a small deterministic defect.

STOP and report instead only if the issue is:

- architectural/ambiguous
- materially expands Phase B scope
- requires Phase C+
- requires a product decision
- requires Sony physical qualification
- risks changing current runtime behavior

## 13. Required final report

Create/update:

`docs/m4-phase-b-generic-model-capabilities-report.md`

The report must include:

- start HEAD
- final HEAD
- commits
- changed files
- exact package/classes introduced
- dependency/import-boundary proof
- exact capability matrix
- registry semantics
- validation/bounds
- proof that no current caller switched
- Phase A regression counts
- new Phase B test counts
- Python counts
- fixture/corpus hashes
- Android debug run id/result
- Android 15 run id/result
- signing/application/permissions continuity
- known limits
- confirmation no Phase C/D/E/F/G or M5+ work
- PR #14 state

Final verdict must be exactly one of:

- `READY FOR M4 PHASE C`
- `NOT READY FOR M4 PHASE C`

Do not merge.

STOP after the report and verdict.
