# SCENEVIBE OS — M4 PHASE E — PACKAGE INSTALLER ORCHESTRATION REPORT

Date: 2026-10-04. Repository: `pierre22400/scenevibe-tv-companion`.
Branch: `work/scenevibe-os-m4-tv-installation-001`. PR #14 remains **OPEN / DRAFT / unmerged**.
Production revision authority remains **SHADOW**. Implementation is **Phase E only**.

## 1. Git provenance, publication and exact scope

| Reference | Exact SHA |
| --- | --- |
| Implementation start / complete Phase E work order | `bfaf24c6d4a5ac49f256b8e15c49f4d9e34f565b` |
| Verified Phase D closure ancestor | `e58a4d81c0aa0c5b6eda0126cd55ff86449107f7` |
| First published Phase E commit | `c9a52f728f02d857b783ce1f58af7bc9dca6e266` |
| Qualified final software HEAD | `3d9145669842f0d92f3bbc188b72b78ae8d9541e` |
| Qualified software tree | `d5d1ed4a928b8b9b110db2da2d44e49cf50e6a51` |
| BASE_TV_M4 / unchanged TV main / PR base main | `ecdf77bec9f93babf15239a63bf7f702fd7ca293` |
| BASE_CLOUD_M4 / unchanged Cloud main | `5011c91aac61a0cc6dcc74c256a15b7dee03d785` |

The final publication HEAD is the **report-only child of the qualified software HEAD**.
Its actual SHA, report commit and independently inspected final-head CI run/job ids are
recorded in the [PR #14 final Git closure manifest](https://github.com/pierre22400/scenevibe-tv-companion/pull/14).
A content-addressed report cannot contain its own commit SHA; no future SHA or CI result is invented.
The final Work verdict is delivered only after both workflows pass on that report-only HEAD.

Three single-parent Phase E commits, 17 changed files in total:

1. `c9a52f728f02d857b783ce1f58af7bc9dca6e266` — generic PackageInstaller and 73 core JVM cases, five files.
2. `3d9145669842f0d92f3bbc188b72b78ae8d9541e` — 42 real-Video/historical JVM cases, nine Python gates,
   protected inventories and actual-XML accounting, 11 files.
3. The final report commit — this file only; exact identity is in the closure manifest.

The command-line Git client had no push credential. Publication used the authenticated GitHub
connector to create the identical Git trees, ordinary single-parent commits and a **non-forced
fast-forward** of the existing branch. The two published trees were compared with the locally
tested trees, with no source delta. No published history was rewritten. There was no rebase,
force-push, merge, main change, PR retarget or Draft removal. The Cloud repository was read-only.

The full architecture, work order, A/B/C/D reports and all required existing types/callers were
read before editing. Initial baseline was re-executed: JVM 373 PASS / 0 FAIL / 1 private SKIP;
Python 33 PASS / 0 FAIL / 0 SKIP. GitHub is the source of truth throughout.

## 2. Exact public API and dependency boundary

The only new production type is
`com.scenevibe.tvcompanionpoc.installation.PackageInstaller`:

```java
public PackageInstaller(
    InstallationStore store,
    InstallationHandlerRegistry registry,
    TvCapabilities capabilities
)

public synchronized InstallationStatus install(
    InstallRequest request,
    InstallationHandler.RuntimePorts ports
)
```

Construction rejects null dependencies with a fixed label and performs no read/write/activation.
There are exactly three retained dependencies: store, static registry and capabilities.
Ports are supplied per call and are never retained. The only imports are `java.util.Arrays`
and `java.util.Map`. A real empty-external-classpath/sourcepath JDK compilation covers the
installer and all pure existing installation types, excluding the sole Android backend.

No Video, Prime, FinalTrack, Cloud envelope/finalTrackId, service, scheduler, renderer, controller,
Android, network, credential, diagnostics, reflection, discovery, dynamic loading or WebView
dependency enters the installer. Its API has no ACK, boot or public generic restore operation.
All internal helpers and new non-trivial classes/methods have pedagogical Javadocs.
Modified/new Python retains existing docstrings, documents every function, and has a teaching
banner after imports. No unrelated production reformatting or docstring removal occurred.

## 3. Complete revision decision table

The first operation is the real store's coherent read. Incoming bytes are not parsed for
revision authority. The registry does not construct/discover/invoke handlers on lookup.

| Durable state / incoming revision | Result and authorized operations | Writes / confirmation |
| --- | --- | --- |
| CORRUPT, including corrupt generic marker, future/orphan ACK or read exception | CACHE_FAILED; no handler invocation, fallback, clearing or repair | Zero |
| EMPTY + null request | INVALID_PACKAGE | Zero |
| EMPTY + valid positive request | Complete validate/prepare/encode/commit/readback/restore/arm pipeline | One candidate commit; no confirmation |
| `incoming < durable` | STALE before handler lookup/validation, including malformed bytes or unknown codec | Zero |
| `incoming == durable` | Resolve durable handler id/codec; restore and arm the exact durable package; ignore incoming codec/artifacts | Zero |
| `incoming > durable` | Complete new-package pipeline | At most one candidate commit; no confirmation |
| Same revision with absent/mismatched durable registry binding | CACHE_FAILED; no handler fallback or guessed artifact shape | Zero |
| Same revision with failed restore/ARM | ARM_FAILED; exact durable bytes/revision/ACK retained | Zero |
| Newer successful commit, failed exact readback | CACHE_FAILED; no restore/ARM, clearing or second commit | One attempted candidate commit; no confirmation |
| Newer successful commit/readback, failed restore/ARM | ARM_FAILED; newer revision remains durable and pending, old ACK retained | One candidate commit; no rollback or confirmation |
| Successful durable restore and local ARM | Exactly ARMED | No confirmation write |

Only local ARM success returns ARMED. The installer never increments or decrements a revision.
The final immutable InstallRequest already enforces positive revisions, bounded codec ids,
defensive opaque artifacts, counts and byte ceilings. Its content need not be parsed for STALE.

## 4. Exact newer-revision lifecycle and commit/readback proof

The executed core trace is:

```text
read -> validate -> prepare -> encode -> commit -> read -> restore -> arm
```

1. `registry.findCodec(request.codecId())` selects an existing static Entry. Missing codec is
   INVALID_PACKAGE without write or ARM.
2. `handler.validate(request, capabilities)` must return VALIDATED to continue. Ordinary
   rejection codes are returned unchanged. Null/exception or premature PREPARED/ARMED claims
   are INVALID_PACKAGE, preventing a handler from manufacturing installer success.
3. `handler.prepare(...)` must yield PREPARED with the exact request revision, selected codec,
   trusted Entry handler id and capability-valid canonical shape/requirements. Null/foreign
   metadata or exception fails before any write. PREPARED status and valid profiles are also
   structurally protected by the existing final PreparedInstallation constructor.
4. `handler.encodeForCache(prepared)` may supply canonical bytes, but cannot change revision,
   codec or executable artifact-count/profile validity. The new snapshot takes its handler id
   exclusively from the registry Entry. Encoding failure is INVALID_PACKAGE and writes nothing.
5. `store.commit(candidate)` is called **once**: COMMITTED continues; INVALID_SNAPSHOT maps to
   INVALID_PACKAGE; CACHE_FAILED/ordinary exception maps to CACHE_FAILED. The real Phase C
   store/backend remain unchanged, including failed-publication protection and separate ACK.
6. The store is re-read. It must yield SNAPSHOT at the exact candidate revision, codec and handler
   id. Lookup by durable handler id must resolve the same static Entry. Artifact names and every
   canonical byte are additionally compared against the encoded candidate: matching metadata
   cannot hide different persisted bytes. Empty/corrupt/exception/wrong metadata, binding, names
   or bytes is CACHE_FAILED with zero restore/ARM. No caller-copy fallback exists.
7. `durableHandler.restoreFromCache(durable.canonical(), capabilities)` rebuilds trusted state
   using the persisted bytes. The installer checks exact revision/codec/handler, PREPARED,
   capability validity and exact canonical artifact bytes again.
8. `durableHandler.arm(restored, ports)` receives **that restored PreparedInstallation**.
   Only ARMED is success; null/non-ARMED/ordinary exception is ARM_FAILED.

Object-identity tests prove the ARM input is the restoration result, is not the earlier
pre-commit prepared value, and that restore receives a separately decoded durable request.
A test encoder intentionally canonicalizes different bytes: those encoded bytes are committed,
read back, restored and armed, while the incoming/prepared caller bytes never become authority.

The installer does not inspect opaque handler-owned PreparedState. Its semantic/ownership
validation remains in the statically registered handler. No product validation is duplicated.

## 5. Durable resolution and same-revision authority

Resolution is only:

```text
snapshot.handlerId -> registry.findHandler -> exact handler-id + codec-id binding -> entry.handler
```

There is no codec fallback for unknown durable handler id, no artifact-name inference, no
historical-key guess, reflection, registration or discovery. Phase C alone performs the bounded
unambiguous historical representation read.

Same revision never validates/prepares/encodes the incoming request and never commits. Its core
trace is `read -> restore -> arm`. Tests send invalid incoming JSON, non-UTF-8 inert bytes,
unknown codec and the other registered Video shape; the exact durable package/handler still
wins. Fresh restoration is mandatory even if prior activation was successful. Failed redelivery
does not erase or rewrite either durable metadata or confirmed ACK.

## 6. Handler/storage failure and exception matrix

| Boundary fault | Bounded result | Candidate writes / runtime ARM |
| --- | --- | --- |
| Missing incoming codec registration | INVALID_PACKAGE | 0 / 0 |
| VALIDATE rejection, including UNSUPPORTED_CAPABILITY | Exact closed rejection | 0 / 0 |
| VALIDATE null / RuntimeException / premature PREPARED or ARMED | INVALID_PACKAGE | 0 / 0 |
| PREPARE null / RuntimeException / wrong revision, codec, handler / unsupported requirements | INVALID_PACKAGE | 0 / 0 |
| ENCODE null / RuntimeException / changed revision, codec or artifact profile | INVALID_PACKAGE | 0 / 0 |
| Actual store INVALID_SNAPSHOT | INVALID_PACKAGE | No successful candidate publication / 0 |
| COMMIT false / RuntimeException | CACHE_FAILED; prior tuple byte-identical | One attempt / 0 |
| READBACK corrupt, empty, exception, wrong revision/id/codec/binding/artifact names/bytes | CACHE_FAILED | One commit / 0 |
| RESTORE null / RuntimeException / wrong revision, codec, handler or canonical bytes | ARM_FAILED | One new commit, or 0 on redelivery / 0 |
| ARM null / every non-ARMED status / RuntimeException / absent ports | ARM_FAILED | No extra commit; confirmation unchanged |

Only RuntimeException is caught; VM-fatal errors are not broadly swallowed. No arbitrary
exception text, causes, content or secrets are returned/logged by PackageInstaller.
Failures inside real Video ports retain the Phase D bounded cleanup contract.

## 7. ACK isolation and no rollback

The installer contains no `markAcknowledged`, ACK callback, ACK flag, finalTrackId, transport,
credentials or network import. Success tests observe zero backend `ackRevision` writes; new
empty installs have ACK zero rather than their revision. Replacement retains the prior confirmed
ACK. Every generic and real-Video fault checks the same invariant. The explicit confirmations
used to seed tests occur outside PackageInstaller and are separately counted/reset.

After commit plus failed restore/ARM, the newly encoded revision remains durable; there is no
second commit, previous-package re-commit, revision decrement or invented rollback. A recreated
installer later recovers that exact pending revision through same-revision redelivery, using
different/unknown incoming shape with **zero additional writes**, while ACK stays at the prior
confirmed revision. ARMED means local durable restoration and runtime activation only.

## 8. Real-Video and historical compatibility results

Integration composes the real InstallationStore, PackageInstaller, VideoInstallationHandlers
registry, TvCapabilities, MediaSyncedTrackScheduler and SceneRuntimeController. Disk and native
visual windows alone are deterministic boundaries; there is no copied Video parser/handler,
Cloud client, credential owner, network collaborator or ACK callback.

| Integration evidence | Result |
| --- | --- |
| Frozen manifested package through full pipeline | ARMED, one commit, exact runtime+manifest bytes, manifested handler, armed-not-visible until real scheduler due event |
| Frozen legacy package through full pipeline | ARMED, one commit, runtime only, legacy handler/media visual owner |
| Visible manifested -> newer legacy replacement | Opposite owner synchronously retired, one durable identity, no manifest in new snapshot, maximum one visual owner |
| Visible legacy -> newer manifested replacement | Legacy retired before manifested selection/show; maximum one visual owner |
| Visible manifested -> newer manifested replacement | Actual controller generation invalidates old due/expiry callbacks |
| Parser-valid manifested MEDIA/CONTINUE | UNSUPPORTED_CAPABILITY, zero commit/live mutation |
| Parser-valid WALL, alongside a visible qualified revision | UNSUPPORTED_CAPABILITY; prior bytes, generation and scene unchanged |
| Supported profile with Video timing/source cross-contract mismatch | INVALID_PACKAGE before commit |
| Same-revision manifested and legacy with changed incoming bytes | Durable parsed text restored; zero rewrite or ACK change |
| Same-revision incoming opposite Video codec | Durable manifested handler remains authority |
| 18 real handler activation refusals/exceptions across both profiles | ARM_FAILED, new revision durable, old ACK retained, no second write, bounded abort and maximum one visual owner |
| Non-owner Video ports | ARM_FAILED, committed candidate pending, no runtime mutation |
| Failed Video ARM followed by process-local recreation/redelivery | Exact pending package restored and ARMED without another commit |
| UTF-8 accents, ligature, apostrophe, combining accent and emoji | Exact canonical bytes plus runtime/manifest parsed text preserved |

Frozen historical compatibility is read-only:

| Exact Phase A fixture | Durable handler | After same-revision ARM | Writes / generic marker |
| --- | --- | --- | --- |
| `video-manifested-cache-v1.json`, revision 13 / ACK 13 | `scenevibe.runtime-track-overlay.v1` | 13 / 13; exact original runtime + manifest Strings/UTF-8 bytes | 0 / absent |
| `video-legacy-cache-v1.json`, revision 14 / ACK 13 | `scenevibe.runtime-track.v1` | 14 / 13; exact original runtime, no invented manifest | 0 / absent |

Both fixtures also pass recreated-installer redelivery. Runtime ARM and historical semantic
failures preserve all existing keys/revision/ACK; invalid historical JSON is not migrated into
validity. Historical keys remain byte-identical and are not deleted/eagerly migrated. Installation
identity, pairing and credential owners are not dependencies of this composition and remain
byte-pinned in production. Process-local recreation here is not a physical reboot claim.

## 9. Protected production and scope proof

The E inventory pins **60 production/resource/configuration blobs**, **55 retained test/fixture/
inventory blobs** and **nine authoritative existing document blobs**. All **124** identities were
independently matched against the published GitHub software tree; none drifted.

CloudControlClient, OverlayService, BootReceiver, CloudTrackRepository, every Phase D handler,
prepared state/runtime port and every Phase C store/backend/codec remain byte-exact to the start.
Production source inventory admits exactly one new Java definition. PackageInstaller has no
other production reference or caller. There is no Phase F adapter, service port or Phase G
restore coordinator. Historical String compatibility, including the characterized manifested
MEDIA/CONTINUE acceptance, remains in the untouched production path.

Earlier B/C/D gates gain only their exact inventoried PackageInstaller exception. An additional
E gate reverses those specific additions and compares each original Python predicate/file and
debug workflow against the starting Git blob. The summary script adds only the E executed bucket;
all earlier inventories, suite counts, allowed private SKIP and frozen fixture hashes remain.
The workflow changes only two accounting step labels.

Cloud v1 wire/ACK body/order/finalTrackId binding, current historical persistence/redelivery/
restore orchestration, scheduler media time/pause/seek/replay, generation guard, visual ownership,
window owner threading, identity/pairing/credentials, reset/disconnect, application and signing
inputs remain unchanged. No current Cloud caller constructs InstallRequest or invokes the new
installer. Production remains SHADOW.

## 10. Executed qualification counts and frozen hashes

The actual Gradle XML-derived summary was downloaded from software-head artifact `11313179292`
and inspected, including the suite inventory, allowed SKIP and fixture digests. Counts are not
estimated from source annotations. The complete local JUnit/Python execution agrees with CI.

| JVM bucket | PASS | FAIL | SKIP | Total |
| --- | ---: | ---: | ---: | ---: |
| Retained before Phase A | 178 | 0 | 1 | 179 |
| Phase A | 30 | 0 | 0 | 30 |
| Phase B | 39 | 0 | 0 | 39 |
| Phase C | 43 | 0 | 0 | 43 |
| Phase D | 83 | 0 | 0 | 83 |
| Phase E | 115 | 0 | 0 | 115 |
| **Complete suite** | **488** | **0** | **1** | **489** |

E suites: PackageInstallerTest 27; PackageInstallerFailureTest 36;
PackageInstallerReadbackTest 10; M4PhaseEVideoInstallerTest 16;
M4PhaseEVideoFailureTest 18; M4PhaseEHistoricalInstallerTest 8.

Python: original 15 + retained B 4 + retained C 5 + retained D 9 + new E 9 =
**42 PASS / 0 FAIL / 0 SKIP**. E covers API/dependencies, real JDK-only compilation,
forbidden imports/dynamic APIs, ACK/fatal-error boundaries, exact old production/configuration,
frozen tests/documents, single-type/no-caller inventory, exact earlier gate additions and docstrings.

The sole JVM private SKIP remains
`M1CloudInteropTest.originalColumboProjectionIsInstallable`, requiring
`SCENEVIBE_M1_SONY_FIXTURES`. It is not counted as PASS. The seven M1 envelopes and 94-case
manifest corpus are executed within retained suites, not added again to JUnit totals.

| Frozen evidence | Unchanged SHA-256 |
| --- | --- |
| Seven M1 Cloud envelopes | `19ee6fc3e2eba7009c9d345c33a63ca5617621fac9c8f5618cb24ee2932d6eab` |
| OverlayManifest 94-case corpus | `c78addf877b277e38f66c12274e4c8ed2ce12a503d883e27fd9950215cafbf30` |
| Manifested historical cache | `76d109c4b69f3e707912449f87e3fa6b9081e4564a12c77c633ae7ed7563beb1` |
| Legacy historical cache | `1676ed2033ec0e589509da7bdab3294ed8b5949153eeb2cfdb539aa23409cc8c` |

## 11. Exact software-head Android CI and continuity

Both workflow metadata records have `head_sha=3d9145669842f0d92f3bbc188b72b78ae8d9541e`,
trigger `pull_request`, completed status and success conclusion. Jobs, every relevant step
and actual logs were inspected.

- [Android debug APK, run 37231110399](https://github.com/pierre22400/scenevibe-tv-companion/actions/runs/37231110399),
  job `111520739938`: **SUCCESS**. Python 42/0/0; Gradle JVM and XML inventory 488/0/1;
  `lintDebug`, `assembleDebug`, LAN DEV and Consumer Cloud-origin builds all executed successfully.
  Every stable-signing step, including certificate verification, executed successfully rather than skipped.
- [Android 15 / API 35 platform smoke, run 37231110369](https://github.com/pierre22400/scenevibe-tv-companion/actions/runs/37231110369),
  job `111520739875`: **SUCCESS**. Six explicit performed PASS checks: Cloud-mode APK build,
  APK install, package presence, crash-free MainActivity launch, resumed/top activity and
  package presence after force-stop. All job steps completed successfully.

Software-head artifacts: Gradle XML-derived summary `11313179292`, Cloud debug APK
`11312734907`, stable-signed APK `11312844746`. Their metadata independently binds them
to the exact software SHA. Archive digests are not misrepresented as raw APK hashes.

Stable certificate SHA-256, read from performed apksigner verification:
`f908bf564ed97ba67e02b1ebc89eb0239cf980752587f55eb9ec0419791a2e9c`.

Unchanged build/manifest inputs retain application/package `com.scenevibe.tvcompanionpoc`,
versionCode 12, versionName `0.8.2-tv-release-hardening`, minSdk 26, targetSdk 35,
the same permissions, foreground-service type and signing chain. The manual release
workflow was not dispatched. Real Gradle/lint/build/signature evidence comes from GitHub
Actions; local verification uses actual Java/JUnit and the official AGP mockable Android jar.

The report-only final HEAD is independently requalified before closure. Its exact run/job ids,
results, final summary artifact and final SHA are in the PR #14 closure manifest. This avoids
a recursive report-commit/CI update while preserving exact-head evidence for the final verdict.

## 12. Exact changed-file inventory

Under `app/src/main/java/com/scenevibe/tvcompanionpoc/installation/`, added:

- `PackageInstaller.java` — the sole production delta.

Under `app/src/test/java/com/scenevibe/tvcompanionpoc/installation/`, added:

- `M4PhaseEInstallerFixtures.java`;
- `PackageInstallerTest.java`;
- `PackageInstallerFailureTest.java`;
- `PackageInstallerReadbackTest.java`.

Under `app/src/test/java/com/scenevibe/tvcompanionpoc/`, added:

- `M4PhaseEVideoFixtures.java`;
- `M4PhaseEVideoInstallerTest.java`;
- `M4PhaseEVideoFailureTest.java`;
- `M4PhaseEHistoricalInstallerTest.java`.

Gates/accounting:

- added `.github/scripts/m4-phase-e-baseline.json` and `tests/test_m4_phase_e_boundary.py`;
- narrowly modified `tests/test_m4_phase_b_boundary.py`, `tests/test_m4_phase_c_boundary.py`,
  `tests/test_m4_phase_d_boundary.py`, `.github/scripts/m4-phase-a-test-summary.py`;
- changed only two accounting labels in `.github/workflows/android-debug.yml`.

Documentation: added this report only. Earlier sources, fixtures, inventories, reports,
architecture and work orders are unchanged.

## 13. Self-audit, limits and stop

The exact diff was audited for every write path before VALIDATE/PREPARE success, stale early
exit, incoming-byte authority on redelivery, trusted Entry identities, post-commit readback,
restored-object ARM, exception/null handling, ACK isolation and no rollback. Extra exact-byte
checks and premature-success/null guards were included before publication and qualified.
There is no remaining deterministic Phase E defect or blocker requiring a Phase D semantic,
Cloud wire, durable-store contract or product-policy change. No old production correction was needed.

Calls are synchronous and serialized per installer. The later caller must serialize access
to the shared runtime/store and honor owner-thread ports; Phase E does not claim a global
transaction across independent installers, implement a cross-process lock, or make a broken
runtime port transactional. Storage readback proves the representation observed under the
existing Phase C consistency contract, not physical flash behavior during a power loss.

No new Sony physical qualification is claimed. API 35 is a standard Android image, not Android
TV; force-stop is not hard reboot. Real Android windows, physical reboot, pairing continuity
during upgrade and integrated M4 execution retain their later Sony gate. Isolated Phase E
does not authorize a runtime cutover or establish complete M4 physical qualification.

No Phase F/G or M5+ work: no Cloud v1 adapter/cutover, OverlayService ports, generic boot/restore
cutover, diagnostics cleanup, historical-key deletion/migration, Banner/Language, wall clock,
SceneEvent scheduler, remote assets/CDN/shared cache, dynamic code, new permission, Cloud change,
Production cutover or PR merge. PR #14 remains OPEN / DRAFT / unmerged.

**READY FOR M4 PHASE F**

Stop after final-head qualification and the closure manifest. Phase F has not started.
