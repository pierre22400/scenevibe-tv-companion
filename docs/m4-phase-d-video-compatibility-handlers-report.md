# SCENEVIBE OS — M4 PHASE D — VIDEO COMPATIBILITY HANDLERS REPORT

Date: 2026-10-04. Repository: `pierre22400/scenevibe-tv-companion`.
Branch: `work/scenevibe-os-m4-tv-installation-001`. PR #14 remains **OPEN / DRAFT / unmerged**.
Production revision authority remains **SHADOW**.

## 1. Git provenance and scope

GitHub was read before implementation and again before normal fast-forward publication:

| Reference | Exact SHA |
| --- | --- |
| Implementation start / documentation-only Phase D work order | `1a301ec039938df3f5c7fba7c2104acd3c8ca113` |
| Its parent / Phase C closure | `531b9cb4db20497a6b1df6ac414f12f2b3bea615` |
| Final software HEAD / Phase D implementation and qualification | `9211732daa4ce7b47d622446bd200959bdb4442e` |
| BASE_TV_M4 / unchanged TV main / PR base main | `ecdf77bec9f93babf15239a63bf7f702fd7ca293` |
| BASE_CLOUD_M4 / unchanged Cloud main | `5011c91aac61a0cc6dcc74c256a15b7dee03d785` |

The software commit is `feat(tv): add M4 Phase D static Video compatibility handlers`.
It changes exactly 23 files. This report is the sole file in a subsequent normal
fast-forward documentation commit, making 24 changed files for the Phase D cycle.
The exact final publication HEAD, report commit SHA and its independently inspected
CI runs are recorded after creation in the **PR #14 final Git closure manifest**.
That manifest avoids inventing an impossible self-referential SHA inside this report.

There is no merge, rebase, force-push, retargeting, Draft removal, TV main modification
or Cloud repository modification. Earlier reports and the authoritative architecture
and work order are preserved.

## 2. Implemented classes and exact static composition

All concrete Video types live in
`app/src/main/java/com/scenevibe/tvcompanionpoc/`, alongside the existing package-private
Video parsers. The generic `.installation` package imports none of them.

| Concrete handler | Durable handler id = codec id | Exact artifact set |
| --- | --- | --- |
| `VideoManifestInstallationHandler` | `scenevibe.runtime-track-overlay.v1` | `runtime` + `manifest` |
| `VideoLegacyInstallationHandler` | `scenevibe.runtime-track.v1` | `runtime` only |

Handler ids use the existing `InstallationStore.COMPAT_OVERLAY_HANDLER_ID` and
`COMPAT_TRACK_HANDLER_ID`; codecs use the existing `TvCapabilities` constants.
No second vocabulary is introduced.

`VideoInstallationHandlers` eagerly creates one instance of each concrete handler and
one registry with exactly those two entries. Lookup constructs nothing. Registry metadata
remains sorted and immutable. No Context, network, reflection, discovery, downloaded code
or runtime registration is involved. Generic `InstallationHandlerRegistry.empty()` is
unchanged. Current production callers use named Video semantic facades, never registry
lookup or registry-driven orchestration.

Other new Video types:

- `VideoRuntimePreparation`: exact artifact shape, strict decode and the shared qualified text parser.
- `VideoPreparedState`: immutable typed parser result and exact candidate/profile ownership binding.
- `VideoInstallationRuntimePorts`: bounded owner-thread activation contract for deterministic fakes
  and later composition; no production OverlayService adapter is added.

## 3. Narrow additive prepared-state contract

Only `InstallationHandler.java` and `PreparedInstallation.java` change in the generic package:

- An empty, documented `InstallationHandler.PreparedState` marker permits handler-owned,
  immutable, bounded, memory-only preparation.
- A six-argument constructor and `preparedState()` accessor are additive.
- The existing five-argument constructor and all its original scalar/capability checks remain;
  its state is null and it cannot masquerade as an executable Video preparation.
- No raw Object or concrete Video import enters generic code. The state is never serialized.

`VideoPreparedState` retains the qualified immutable `ScheduledTrack`, optional
`OverlayManifest`, immutable canonical request, handler id and derived requirements.
Cloud text preparation produces no Bitmap. No Context, Service, scheduler, renderer,
controller, client, credential, secret or store is retained.

ARM and encode require the exact concrete state, handler/codec, manifest presence, and
canonical request / requirements object identities established by PREPARE.
A borrowed state rebound to another revision, canonical request or independently supplied
requirements is rejected. Historical String evaluation returns an unbound typed result;
it cannot be armed or encoded as a generic prepared installation.

## 4. Validation, preparation, cache encoding and restoration

### Manifested generic path

1. Require the exact known codec and exactly `runtime` + `manifest`.
2. Strict UTF-8 decode with malformed/unmappable input set to REPORT.
3. Enforce <=400,000 runtime and <=800,000 manifest UTF-16 units.
4. Parse Cloud text-only runtime with the existing `TrackParser`; comments must be an array
   of objects and any `media` field is rejected, including null.
5. Parse the manifest with the existing `OverlayManifestParser`.
6. Derive requirements from the actual parsed manifest and check `TvCapabilities`.
7. Apply the existing `VideoOverlayManifestBridge` to the parsed pair.
8. Retain trusted immutable parse state and the exact canonical artifact bytes.

There is exactly one production call to `VideoOverlayManifestBridge.validate`, inside
the manifested handler's coherence method. Generic and historical manifested evaluation
share that method. Video product/source/media identity, comment-scene bijection and exact
timing decisions are inherited from the unchanged bridge, not duplicated.

### Legacy generic path

Require the exact legacy codec and `runtime` only; strict decode and <=400,000 UTF-16
units precede the same text-only parser. Derive requirements from the parsed track and
check capabilities. This handler and its shared runtime parser have no manifest-parser
or bridge dependency.

### Byte bounds and side effects

Before decode, UTF-8 byte length is bounded by three times the applicable UTF-16 ceiling;
after decode the historical String.length ceiling is checked. The existing immutable
InstallRequest artifact/package/count bounds remain unchanged. Bytes are neither replaced,
normalized nor truncated, including Unicode supplementary characters.

`validate` returns only a closed status. PREPARE is memory-only.
`encodeForCache` verifies ownership and returns the exact immutable canonical request;
it performs no write. `restoreFromCache` treats bytes as untrusted and repeats live semantic
and capability preparation, rebuilding trusted state without ARM or persistence.
No new lexical/parser semantics are claimed beyond the qualified parsers and these
explicit generic artifact/decode/profile checks.

Parser causes and arbitrary messages are discarded. Generic rejection exposes only the
closed InstallationStatus name; manifested historical rejection exposes only the existing
bounded ManifestCode. No new logs contain payloads, commentary, credentials or secrets.

## 5. Execution capabilities and explicit transitional difference

| Package profile | renderingContract | clock | pauseBehavior | timedSceneCount | remote acquisition / shared cache |
| --- | --- | --- | --- | --- | --- |
| Executable manifested Video | `scenevibe.overlay-manifest.v1` | MEDIA | FREEZE | Exact parsed manifest scene count | false / false |
| Legacy, pauseFreezesDisplay true | `scenevibe.track.v1` | MEDIA | FREEZE | Exact parsed comment count | false / false |
| Legacy, pauseFreezesDisplay false or absent | `scenevibe.track.v1` | MEDIA | CONTINUE | Exact parsed comment count | false / false |

A parser-valid wall manifest and a parser-valid manifested CONTINUE profile return
`UNSUPPORTED_CAPABILITY` on the generic handler contract. No wall-clock runtime exists.
A local assetRef does not advertise remote acquisition or a shared cache and introduces
no downloader, external URL or image qualification.

**Characterized before production edits:** the historical manifested String API accepts
MEDIA/CONTINUE, rejects WALL with MANIFEST_INVALID, and the historical legacy parser
derives CONTINUE when its pause flag is absent. Three characterization tests were executed
against the unmodified production code before extraction and retained afterward.

The manifested handler therefore exposes `prepareCompatibility(String,String)` for current
repository callers. It uses the same parsers and bridge without adding the future generic
capability gate. Historical MEDIA/CONTINUE acceptance and WALL diagnostic are preserved.
Install bounds and revision checks remain in the repository; historical restore acceptance
is preserved rather than silently tightened. No capability or wire contract is changed.

The runtime parser's existing internal integer coercions and the bridge's existing treatment
of a runtime pause flag versus manifest pause behavior are preserved and tested. This is
an internal repository compatibility proof, not a claim that malformed Cloud wire is accepted.

## 6. CloudTrackRepository delta and production continuity

The only modified existing Video production class is `CloudTrackRepository`:

- Manifested install evaluates through the named manifested semantic facade and obtains
  the prepared track before the original durable save and scheduler.load.
- Manifested restore obtains the handler-owned typed track/manifest, then follows the
  original scheduler/load/result sequence.
- Its private legacy String parser delegates to the named legacy semantic facade.
- Direct TrackParser, OverlayManifestParser and bridge calls are removed.
- Existing method signatures and InstallResult / RestoreResult vocabulary remain.

Positive revision, staleness, redelivery authority, durable historical tuple, save/load order,
cache-clear behavior, revision/acknowledged continuity and ACK persistence remain in their
existing owners. The repository's Context constructor, injectable legacy install/restore
segment and revision/diagnostic/ACK/clear segment are byte-pinned. The existing Storage seam
is unchanged. MANIFEST_INVALID, MANIFEST_INCONSISTENT, MANIFEST_CACHE_FAILED and NONE
remain caller-visible as before.

CloudControlClient, CloudProtocol, OverlayService, media scheduler, controller, both renderers,
identity, pairing, credentials, disconnect/reset callers, Phase C store/backend and capabilities
are unchanged. Existing validate -> durable install -> arm -> ACK tests remain green.
No new handler ARM call performs persistence or ACK.

This cycle does not wire generic InstallRequest into Cloud assignments, use the registry as
a production installer, commit through a new generic orchestration path, delete historical keys,
or switch reboot restoration. Exactly two concrete handlers now exist; PackageInstaller does not.

## 7. Executable ARM port contract and failure ordering

`VideoInstallationRuntimePorts` extends the existing empty generic RuntimePorts marker.
Its operations are owner eligibility, synchronous visual retirement, loading prepared track,
arming prepared revision/manifest, final active-revision selection and idempotent abort.
There is no ACK, network, Android window, Looper, new clock or service API.

| ARM target | Successful operation order, after typed-state and owner checks |
| --- | --- |
| Manifested | retire legacy -> retire previous manifested -> load exact prepared track -> arm exact revision/manifest -> select revision |
| Legacy | retire manifested -> retire previous legacy -> load exact prepared track -> select legacy revision |

Selection is reached only after prior operations succeed. Success returns ARMED.
Wrong ports/state/handler/codec, old inert state, rebound state, null inputs and non-owner calls
return ARM_FAILED. Failed owner checks authorize no mutation, including cleanup.
Each false-return or RuntimeException on an eligible owner triggers bounded abort and returns
ARM_FAILED; cleanup exceptions cannot escape or manufacture ARMED.

Sixteen deterministic ARM cases inject partial mutation, false returns and exceptions at every
activation stage. They observe at most one visual owner at each mutation, exact prepared object
identity, no reparse, exact manifest revision and selection last. A cleanup exception after
retirement still leaves at most one owner and ARM_FAILED. It is not a durable rollback claim:
a malicious/broken future port cannot be made transactional by this handler, and selection
metadata may remain the previous value when cleanup itself throws. Later concrete runtime
ports must honor synchronous retirement and abort semantics.

Existing controller generation/stale-callback and owner-thread renderer tests remain unchanged
and pass. No real OverlayService port adapter or runtime cutover is implemented in Phase D.

## 8. Independent differential oracle and matrix

`M4PhaseDHistoricalRepository` is a **test-only copy of the actual starting repository**,
not expectations reconstructed from the new handlers. Only the class/constructor names and
documentation change; executable lines, including old parsing/bridge logic, are preserved.
Python checks remove comments/blank lines and reverse that rename against the captured
authoritative source:

- Starting Git blob: `0e8994a927a107f7214bd5791e7042a8a3d642e0`.
- Executable-code SHA-256: `a1e3f6b81a710ead729aa17a740d89e71e8c17fc07567370b67a8554d5467de6`.

The oracle and delegated repository execute on equivalent fresh memories and real qualified
schedulers. Tests compare acceptance, bounded diagnostic, exact persisted strings, restore
outcome/revision, commit/clear counts, actual scheduler loads, revision and acknowledged revision.
Generic handler decisions are compared alongside the current production decisions.

| Differential case | Preserved current production outcome | Generic outcome |
| --- | --- | --- |
| Frozen manifested cache | Accepted, exact bytes and restore | VALIDATED |
| Frozen legacy cache / no manifest | Accepted, exact bytes and restore | VALIDATED |
| All seven actual frozen M1 envelope projections | Matches the independent historical decision | Matches supported profile decision |
| Malformed runtime / comment media / malformed manifest | Existing refusal and diagnostics | INVALID_PACKAGE |
| Source mismatch | MANIFEST_INVALID | INVALID_PACKAGE |
| Scene-set mismatch / 1 ms timing drift | MANIFEST_INCONSISTENT | INVALID_PACKAGE |
| Manifested MEDIA/CONTINUE | Accepted as before | UNSUPPORTED_CAPABILITY |
| Parser-valid WALL | MANIFEST_INVALID as before | UNSUPPORTED_CAPABILITY |
| Runtime pause false with manifest freeze | Accepted as before | VALIDATED |
| Internal runtime timing coercion | Historical parser/bridge result unchanged | Same semantic result |
| Invalid legacy text-only runtime | Refused as before | INVALID_PACKAGE |

Original fixture files are reused, not rewritten or independently recopied.

## 9. Executed tests and protected import gates

Before production changes: **290 JVM PASS / 0 FAIL / 1 SKIP** and **24 Python PASS**.
The three new edge characterizations then passed against the unchanged production implementation.
Final local full suites and the actual Gradle JUnit XML from GitHub agree:

| JVM bucket | PASS | FAIL | SKIP | Total |
| --- | ---: | ---: | ---: | ---: |
| Retained before Phase A | 178 | 0 | 1 | 179 |
| Phase A, unchanged | 30 | 0 | 0 | 30 |
| Phase B, unchanged | 39 | 0 | 0 | 39 |
| Phase C, unchanged | 43 | 0 | 0 | 43 |
| New Phase D | 83 | 0 | 0 | 83 |
| **Full suite** | **373** | **0** | **1** | **374** |

Phase D: compatibility edges 3; manifested handler 30; legacy handler 16; ARM 16;
registry 4; independent differential 14. Fixtures, seven envelopes and 94 corpus cases
are exercised within those suites and are not double-counted as extra JUnit tests.

Python: original POC 15 + retained Phase B 4 + retained Phase C 5 + new Phase D 9
= **33 PASS / 0 FAIL / 0 SKIP**. Existing test functions/docstrings remain.
Optional D inventory allows only the six explicit Video-side additions and two additive
generic contract edits; earlier C persistence/import checks remain active.

The sole private opt-in SKIP is
`M1CloudInteropTest.originalColumboProjectionIsInstallable`, requiring
`SCENEVIBE_M1_SONY_FIXTURES`. Its absent private content is not counted as PASS.

Automated gates cover generic import neutrality and independent compilation, sole bridge
ownership, legacy graphical independence, no network/Context/window/credential dependency,
exactly two eager registry entries and exactly two concrete Handler implementations,
no reflection/PackageInstaller, unchanged Cloud transport and store/backend boundaries,
typed state and old constructor continuity, and immutable fixture/test/runtime provenance.

The D inventory pins **46 production/configuration blobs and 46 prior test/resource/inventory
blobs**. All 92 were matched against the **published GitHub tree**, not only local files.
The three authorized repository orchestration fragments are additionally SHA-pinned.
All earlier Java tests and fixture/inventory files remain exact.

Localized self-audit defects fixed before publication: a local-asset rejection test had
accidentally retained Group-only children in an image primitive; the fixture construction
was corrected to the unchanged parser's exact image shape. The boundary classifier initially
counted a nested PreparedState implementation as a concrete Handler; the full-type match was
corrected without weakening the two-handler assertion. Rejection wrappers expose only fixed
codes and no parser cause. Final local and pushed CI suites include these corrections.

Existing Javadocs are retained; every new non-trivial class/constructor/method/helper is
documented. Modified Python functions retain/add docstrings and a post-import teaching banner.

## 10. Frozen fixtures

| Evidence | Exact unchanged SHA-256 |
| --- | --- |
| Seven M1 Cloud envelopes | `19ee6fc3e2eba7009c9d345c33a63ca5617621fac9c8f5618cb24ee2932d6eab` |
| 94-case OverlayManifest corpus | `c78addf877b277e38f66c12274e4c8ed2ce12a503d883e27fd9950215cafbf30` |
| Manifested cache 13/13 | `76d109c4b69f3e707912449f87e3fa6b9081e4564a12c77c633ae7ed7563beb1` |
| Legacy cache 14/13 | `1676ed2033ec0e589509da7bdab3294ed8b5949153eeb2cfdb539aa23409cc8c` |

## 11. Exact software-HEAD CI evidence

Both workflow metadata head SHAs are
`9211732daa4ce7b47d622446bd200959bdb4442e`; jobs, individual steps and relevant
logs are inspected rather than relying on a PR badge.

- [Android debug APK, run 37223485337](https://github.com/pierre22400/scenevibe-tv-companion/actions/runs/37223485337):
  **SUCCESS**, job `111498292932`. Boundary 33/33; full Gradle JVM 373/0/1 and
  actual-XML buckets 179/30/39/43/83; lintDebug; assembleDebug; LAN DEV compile;
  Consumer Cloud-origin compile; all stable-signing steps actually executed successfully.
- [Android 15 / API 35 standard-platform smoke, run 37223485439](https://github.com/pierre22400/scenevibe-tv-companion/actions/runs/37223485439):
  **SUCCESS**, job `111498293002`. All six performed checks are explicit PASS in the logs:
  Cloud-mode APK build, APK install, package presence, crash-free MainActivity launch,
  resumed/top activity and package presence after force-stop. Every job step completed successfully.

Stable certificate SHA-256 from the performed apksigner verification:
`f908bf564ed97ba67e02b1ebc89eb0239cf980752587f55eb9ec0419791a2e9c`.

Software-head artifacts: executed summary `11310623878`, Cloud debug APK `11310823371`,
stable-signed APK `11311107684`. Artifact metadata also binds them to the exact software SHA.
Archive digests are not presented as raw APK hashes. The manual release workflow was not dispatched.

Local verification uses the real Java compiler/JUnit and qualified parser/runtime sources;
actual Android Gradle, lint, variant builds and signing evidence comes from the pushed
GitHub Actions run. No local Gradle execution is invented.

Application/configuration continuity is proven by unchanged build and manifest blobs:
package/applicationId `com.scenevibe.tvcompanionpoc`; versionCode 12;
versionName `0.8.2-tv-release-hardening`; minSdk 26; targetSdk 35.
Permissions, foreground-service type and signature inputs are unchanged.

## 12. Exact changed-file inventory

Under `app/src/main/java/com/scenevibe/tvcompanionpoc/`:

- Added: `VideoRuntimePreparation.java`, `VideoInstallationRuntimePorts.java`,
  `VideoPreparedState.java`, `VideoManifestInstallationHandler.java`,
  `VideoLegacyInstallationHandler.java`, `VideoInstallationHandlers.java`.
- Modified: `CloudTrackRepository.java`, `installation/InstallationHandler.java`,
  `installation/PreparedInstallation.java`.

Under `app/src/test/java/com/scenevibe/tvcompanionpoc/`, added:

- `M4PhaseDCompatibilityEdgeTest.java`, `M4PhaseDHandlerFixtures.java`,
  `M4PhaseDHistoricalRepository.java`, `M4PhaseDManifestedHandlerTest.java`,
  `M4PhaseDLegacyHandlerTest.java`, `M4PhaseDRegistryTest.java`,
  `M4PhaseDArmTest.java`, `M4PhaseDDifferentialTest.java`.

Gates/accounting: added `.github/scripts/m4-phase-d-baseline.json` and
`tests/test_m4_phase_d_boundary.py`; modified `tests/test_m4_phase_b_boundary.py`,
`tests/test_m4_phase_c_boundary.py`, `.github/scripts/m4-phase-a-test-summary.py`
and only the two A/B/C/D test-accounting labels in `.github/workflows/android-debug.yml`.
Documentation: this report only. No unrelated reformatting or fixture modification.

## 13. Qualification limits and stop

No new Sony physical qualification is claimed. API 35 uses a standard Android image,
not Android TV; presence after force-stop does not qualify hard reboot.
The integrated M4 Sony protocol remains for the later actually wired runtime path.

There is no PackageInstaller, generic orchestration authority, generic Cloud v1 adapter,
CloudControlClient cutover, generic reboot/restore cutover, diagnostics cleanup, historical
cache deletion, Banner/Language handler, wall runtime, SceneEvent/M5 scheduler, asset acquisition,
shared cache, WebView, dynamic plugin, new permission, Cloud change or Production cutover.
No Phase E/F/G or M5+ development has begun.

Both software-head gates are complete and inspected. The report-only publication HEAD is
independently requalified; its exact SHA and final-head run metadata/jobs/log evidence belong
to the PR #14 closure manifest, recorded after those checks complete. PR #14 stays Draft.

**READY FOR M4 PHASE E.** No remaining Phase D blocker. Stop after this closure;
do not merge or begin Phase E in this cycle.
