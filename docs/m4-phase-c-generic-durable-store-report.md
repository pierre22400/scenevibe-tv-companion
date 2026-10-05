# SCENEVIBE OS — M4 PHASE C — Generic Durable Store Closure

Date: 2026-10-04. Repository: `pierre22400/scenevibe-tv-companion`.

**Verdict: READY FOR M4 PHASE D.** Phase C implements durable representation and a narrow
historical persistence extraction. Phase D has not started. Production remains **SHADOW**.

## Git provenance and scope

| Reference | Exact value |
| --- | --- |
| Branch / PR | `work/scenevibe-os-m4-tv-installation-001` / #14 |
| Implementation start HEAD | `cc7ae52d4e48f8267bf8b569bd4de768cba8eb6a` |
| Its parent: Phase B closure | `2efc305d303f0abbfac708c168c76eb24ce4c491` |
| Qualified software HEAD | `29532ddf66ea8bc7d163c9102698971c0961dde7` |
| Qualified software tree | `de6d7d72dd1188f24576763b2e581793f8b15b23` |
| TV main / PR base / BASE_TV_M4 | `ecdf77bec9f93babf15239a63bf7f702fd7ca293` |
| Cloud main / BASE_CLOUD_M4 | `5011c91aac61a0cc6dcc74c256a15b7dee03d785` |

GitHub was read before editing and before publication. The start commit changes only the Phase C
work order; its Phase B ancestry is retained. The software commit is a normal single-parent
fast-forward. It contains the store, extraction, tests and CI accounting, with 13 changed files.
This report is the sole change in the following documentation commit, making 14 files for the cycle.

The final branch HEAD is that documentation commit. Its exact SHA, both final-head workflow run
IDs and their inspected conclusions are recorded in the updated [PR #14](https://github.com/pierre22400/scenevibe-tv-companion/pull/14)
closure manifest after publication. A report cannot contain its own content-addressed commit SHA;
no future SHA or workflow result is invented here. The software-head CI evidence below was already
completed and inspected before this report was committed; the final report-only HEAD must pass
both workflows again before the WORK final verdict is delivered.

PR #14 remains **OPEN / DRAFT / unmerged**, based on `main`. Neither repository's `main` is changed.
The Cloud repository is read-only throughout this cycle. No rebase, force-push, retarget or merge.

## Exact files and types

All Java paths below are relative to `app/src/main/java/com/scenevibe/tvcompanionpoc/`.

| File | Change and responsibility |
| --- | --- |
| `installation/InstallationSnapshot.java` | New immutable revision/codec/handler/opaque-artifact value, using the qualified defensive `InstallRequest` |
| `installation/InstallationSnapshotCodec.java` | New package-private bounded deterministic encoding/decoding; no product parser |
| `installation/InstallationStore.java` | New pure core, persistence-only `Backend`, closed `ReadState`/`CommitState`, immutable `ReadResult`, separate ACK and temporary historical facade |
| `installation/AndroidInstallationBackend.java` | New sole Android preference owner; private immutable `PriorView` and weak-identity `FailedView` protect failed publication |
| `CloudTrackRepository.java` | Constructor/import/banner extraction only; existing `Storage` seam delegates to the store's historical facade |

The other changed files are:

- `app/src/test/java/com/scenevibe/tvcompanionpoc/installation/InstallationStoreTest.java` — new, 22 cases;
- `app/src/test/java/com/scenevibe/tvcompanionpoc/M4PhaseCCompatibilityTest.java` — new, 12 cases;
- `app/src/test/java/com/scenevibe/tvcompanionpoc/installation/AndroidInstallationBackendTest.java` — new, 9 cases;
- `tests/test_m4_phase_c_boundary.py` — new, 5 architecture/scope cases;
- `tests/test_m4_phase_b_boundary.py` — narrowly authorizes the exact Phase C extraction while retaining its four cases;
- `.github/scripts/m4-phase-c-baseline.json` — frozen source/blob inventory and explicit Phase C counts;
- `.github/scripts/m4-phase-a-test-summary.py` — adds the Phase C bucket from actual Gradle JUnit XML;
- `.github/workflows/android-debug.yml` — two test-step labels updated to include Phase C;
- `docs/m4-phase-c-generic-durable-store-report.md` — this report.

All existing Java tests, original Python contract tests, Javadocs and frozen A/B inventories are
retained. New classes, constructors, methods and helpers carry invariant/side-effect documentation.

## Historical repository extraction

The Android constructor still opens only app-private `cloud_track`, now through
`AndroidInstallationBackend`. Its anonymous `Storage` delegates `get`, `save`, `saveAck` and `clear`
to the temporary historical facade. Direct SharedPreferences/editor mechanics leave the repository.

Everything from the injectable constructor through the end of `CloudTrackRepository` is byte-exact
to the starting code (SHA-256 `a295118d79d2f920f60ca714ce2bb51159f7bceaecbc9003ad9ccd477e9c1669`).
The existing `Storage` interface/Javadoc is also pinned
(`8c41c2fce888465f3a4349e353b6986a10fbf4241202a7addce5ab1919c2ce26`).
Thus parsing, manifested/legacy validation, revision decisions, load/restore results, arm ordering,
confirmed-ACK handling and current reset/disconnect callers remain in their qualified locations.

Normal historical writes still use one batch for revision/runtime/optional manifest; a legacy write
removes the previous manifest in that same batch. ACK is a separate write. The facade adds only
durable guards: bounded complete positive-revision writes, exact-positive-revision ACK, and refusal
to read/write historical residue when any generic marker exists. Current production never creates
that generic marker and is not switched to generic read, commit, restore or handler execution.

## Durable format and bounds

One `installationSnapshot` string atomically contains the complete generic candidate. ACK remains
in the separate historical `ackRevision` key. The generic format is internal, not a Cloud wire schema.

| Ordered field | Representation / bound |
| --- | --- |
| Version | Exact `scenevibe.os.installation-store.v1` |
| Revision | Canonical positive decimal Java `long`, at most 19 digits |
| Codec id / static handler id | Each safe ASCII `[A-Za-z0-9._:-]{1,128}` |
| Artifact count | 1 or 2, checked before traversal/allocation |
| Each artifact | Sorted unique safe id, then canonical padded standard Base64 |
| Each decoded artifact | 1–2,400,000 bytes |
| Complete decoded package | At most 3,000,000 bytes |
| Each encoded artifact | At most 3,200,000 characters |
| Complete encoding | At most 4,001,024 characters, required final newline, no trailing fields |
| Historical runtime / manifest | At most 400,000 / 800,000 UTF-16 units before UTF-8 allocation |

Every field ends in LF; safe ids cannot contain the delimiter. Artifact ordering is deterministic.
Three million raw bytes require at most 4,000,004 Base64 characters across two artifacts; the
worst-case bounded metadata adds 575 characters, below the outer limit. Decoding checks the outer
length, each field length, count, per-artifact decoded size and accumulated size before the relevant
substring/decode/copy. It uses a bounded cursor, not an unbounded split or recursive traversal.

Strict Base64 re-encoding rejects noncanonical padding/trailing bits, invalid characters and
whitespace. Generic bytes are fully opaque and need not be text. Historical strings round-trip
through UTF-8 without normalization, truncation or JSON reserialization; unpaired surrogates are
rejected rather than silently replaced. French accents, composed/decomposed forms, ligature,
apostrophe, emoji, all byte values and maximum aggregate size have deterministic tests.

## Atomicity and ACK proof

All store instances for one Android preference object share its identity monitor. A generic commit
uses one Editor/commit for the complete encoded snapshot, preserves historical residue and never
advances ACK. Returned artifacts are defensive copies; mutable scheduler/renderer/client objects
are absent from the store.

Android SharedPreferences can publish new in-memory values before `commit()` returns false.
The backend captures the five known keys, retains a shared prior readable view on false/exception,
and exposes that view even through a newly constructed backend for the same preference object.
There is no rollback editor/write. A later successful partial operation restages the prior view and
applies the requested change in the same batch, preventing a failed candidate or ACK from leaking
into a subsequent durable write. Weak identity bindings avoid retaining a Context lifecycle or
calling foreign `hashCode`/`equals` methods.

Nine backend tests exercise private-file selection, one-editor commits, failed writes that actually
publish fake memory, backend recreation, recovery/restaging, failed ACK, thrown commit, corrupt
typed storage, explicit clear and fixed-key rejection. These are controlled deterministic faults,
not a claim of physical flash/power-loss qualification.

`markAcknowledged` requires a complete current durable candidate and exactly its positive revision.
Wrong, stale, future or absent-candidate ACK is refused. Absent ACK means zero; malformed, negative,
overflow or ACK greater than the durable revision makes a generic read CORRUPT. Failed ACK storage
preserves the previous durable ACK. A candidate below already confirmed ACK is refused. COMMITTED
means durability only: it does not mean armed, rendered or ACK-eligible, and no method sends HTTP.
Revision replacement/idempotence orchestration stays in the existing repository and future installer.

## Compatibility and fail-closed matrix

| State | Phase C result / evidence |
| --- | --- |
| No snapshot, no historical tuple, no positive ACK | EMPTY, no mutation |
| Frozen manifested cache | SNAPSHOT, revision 13 / ACK 13; exact UTF-8 runtime + manifest, fixed qualified shape/handler ids |
| Frozen legacy cache | SNAPSHOT, revision 14 / ACK 13; exact runtime, no invented manifest |
| Historical absent ACK | Zero, no synthesized confirmation |
| Invalid/zero/negative/overflow revision | CORRUPT, no candidate or action |
| Partial historical tuple, empty or oversized artifact | CORRUPT |
| Malformed/future/orphan positive ACK | CORRUPT |
| Unsupported version, missing/trailing generic field | CORRUPT |
| Invalid id/count/order/duplicate, Base64 or size | CORRUPT |
| Present generic marker plus old historical residue | Generic representation is authoritative; no historical fallback or deletion |
| Corrupt/empty/wrong-type generic marker plus old cache | CORRUPT; historical facade cannot resurrect, overwrite or ACK old residue |
| Opaque artifacts with invalid Video JSON/coherence | Storage representation can be valid; current repository still rejects semantic corruption |

Both historical fixture reads perform **zero writes**. Compatibility tests read alongside an actually
restored, visible qualified runtime and prove unchanged cache/scheduler/generation/visible state.
The store retains no scheduler/renderer/network dependency. Generic corruption read returns only a
closed state and cannot load, arm, display or ACK anything. Explicit current cache reset still targets
only `cloud_track`; identity, pairing and credentials remain in their unchanged separate owners.

## Boundaries and deferred semantics

The five new Python boundary cases compile the eight Phase B types plus three pure Phase C types
with an empty external classpath/sourcepath; Android is available only to the backend. They reject
parser, Video/Prime, scheduler, controller, renderer/window, service, networking, reflection/dynamic
loading and transport references; pin the repository's full business tail; and check the exact
production file set. There is no concrete `InstallationHandler` or `PackageInstaller`, no Cloud
adapter and no production call to generic install/restore. GitHub tree inspection independently
confirmed all 82 pinned prior blobs, including 39 protected production Java files, unchanged.

TrackParser, OverlayManifestParser, VideoOverlayManifestBridge, Prime target/package checks and
runtime/scene timing coherence remain outside this store. Existing current-caller semantic validation
is unchanged. Static handler validation belongs to Phase D, which is not implemented. Codec/handler
ids here are durable metadata, not registration, negotiation or a capability framework.

## Executed tests and exact-head CI

Before editing: JVM **247 PASS / 0 FAIL / 1 SKIP** (248); Python **19 PASS / 0 FAIL / 0 SKIP**.
After all localized fixes, both the full local JVM runner and real GitHub Gradle suite report:

| JVM bucket | PASS | FAIL | SKIP | Total |
| --- | ---: | ---: | ---: | ---: |
| Retained baseline before Phase A | 178 | 0 | 1 | 179 |
| Phase A | 30 | 0 | 0 | 30 |
| Phase B | 39 | 0 | 0 | 39 |
| Phase C: store / compatibility / Android backend | 22 + 12 + 9 | 0 | 0 | 43 |
| **Full suite** | **290** | **0** | **1** | **291** |

Python: retained original 15 + Phase B 4 + Phase C 5 = **24 PASS / 0 FAIL / 0 SKIP**.
The sole JVM SKIP remains `M1CloudInteropTest.originalColumboProjectionIsInstallable`, requiring
private opt-in `SCENEVIBE_M1_SONY_FIXTURES`. It is not reported as PASS. The nested seven envelopes
and 94 corpus cases run within existing tests and are not double-counted as extra JUnit cases.

| Workflow on exact software HEAD `29532ddf66ea8bc7d163c9102698971c0961dde7` | Inspected result |
| --- | --- |
| [Android debug run 37216725173](https://github.com/pierre22400/scenevibe-tv-companion/actions/runs/37216725173), job 111478623224 | SUCCESS; 24 Python cases; actual Gradle XML 290/0/1; lintDebug; assembleDebug; LAN DEV compile; Consumer Cloud-origin compile; stable signing all SUCCESS |
| [Android 15 run 37216725108](https://github.com/pierre22400/scenevibe-tv-companion/actions/runs/37216725108), job 111478622757 | SUCCESS; APK build/install/presence, launch without fatal crash, resumed activity, presence after force-stop: six PASS |

Run metadata, exact `head_sha`, jobs, every relevant step and logs were read, not inferred from a
badge. Debug artifacts: summary `11308572753`, stable APK `11308547831`, debug APK `11308488248`.
The stable signer SHA-256 read from apksigner is
`f908bf564ed97ba67e02b1ebc89eb0239cf980752587f55eb9ec0419791a2e9c`.
The workflow summary reads real Gradle JUnit XML with frozen suite counts and the sole allowed SKIP.

## Frozen evidence and continuity

| Frozen evidence | Unchanged SHA-256 |
| --- | --- |
| Seven M1 Cloud envelopes | `19ee6fc3e2eba7009c9d345c33a63ca5617621fac9c8f5618cb24ee2932d6eab` |
| 94-case OverlayManifest corpus | `c78addf877b277e38f66c12274e4c8ed2ce12a503d883e27fd9950215cafbf30` |
| Manifested cache fixture, revision 13 / ACK 13 | `76d109c4b69f3e707912449f87e3fa6b9081e4564a12c77c633ae7ed7563beb1` |
| Legacy cache fixture, revision 14 / ACK 13 | `1676ed2033ec0e589509da7bdab3294ed8b5949153eeb2cfdb539aa23409cc8c` |

CloudProtocol/envelopes/ACK body/finalTrackId binding, scheduler timing, SceneRuntimeController,
renderer ownership/thread handling, reboot callers, identity/pairing/credentials and all existing
test sources are unchanged. Phase A cases retain validation → durable install → arm → ACK,
failed install/arm refusal, redelivery/stale replacement, cache/reboot, single visual owner and stale
callback suppression. Phase B's 39 model/registry/capability tests remain green.

Manifest and signing inputs are unchanged: application id `com.scenevibe.tvcompanionpoc`, versionCode
12, versionName `0.8.2-tv-release-hardening`, minSdk 26, targetSdk 35, same permissions and certificate.
Production revision authority remains SHADOW. No Cloud change, asset, Banner, wall clock, M5+,
generic diagnostics refactor, handler, installer, adapter or generic restore cutover is present.

## Self-audit corrections and remaining limits

Localized defects were corrected before the single software commit: weak-map foreign equality calls
were replaced by weak identity lookup; historical facade access was blocked under a generic marker;
and raw historical writes received bounded-complete-tuple guards. Regression tests cover them;
all retained suites were rerun unchanged and passed. Typed-preference failures expose only fixed
labels. No secrets, comment content or complete payloads are logged.

No new Sony physical test was executed or claimed. API 35 uses a standard Android emulator,
**not Android TV**, and force-stop is **not a hard-reboot qualification**. Historical reboot/restore
behavior remains characterized; generic reboot cutover and its physical gates belong to later M4
phases. Real-device flash/power-loss fault behavior is not inferred from controlled JVM fault models.
The manual release workflow was not dispatched; the required debug/build/stable-signing workflow
did execute. Final-head gates and exact final SHA are recorded in PR #14 after this report-only commit.

There is no remaining Phase C blocker. The next authorized increment is Phase D's static Video
handlers, to be launched in a separate cycle. No Phase D/E/F/G or M5+ work occurs here.

**READY FOR M4 PHASE D**
