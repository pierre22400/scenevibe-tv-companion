# SCENEVIBE OS — M4 PHASE F — CLOUD V1 ADAPTER AND LIVE CUTOVER REPORT

Date: 2026-10-04. Repository: `pierre22400/scenevibe-tv-companion`.
Branch: `work/scenevibe-os-m4-tv-installation-001`. PR #14 remains **OPEN / DRAFT / unmerged**.
Production revision authority remains **SHADOW**. Implementation is **Phase F only**.

## 1. Verified Git provenance and final publication identity

| Reference | Exact SHA |
| --- | --- |
| Implementation start / full Phase F work order | `eb38e34086f22f81e5cc9ef917197658ce63ecbd` |
| Verified Phase E closure ancestor | `f4b2007245b7ecc39f8cabb1e8d36945d0650d4e` |
| Adapter/client commit | `f77340df410b06aa050b5a7289577ff1daee1e1d` |
| Service/reset composition commit | `ff5b8aad09c1a61b6740dd6410ec26b0e549b14a` |
| Qualified software HEAD / tests and accounting commit | `110608ff30fdf3a0b140dddaf72ace2fd1149eb9` |
| Qualified software tree | `4dfd83ba135ec3f29871aadc8925c7f4fc533cd4` |
| BASE_TV_M4 / unchanged TV main / PR base main | `ecdf77bec9f93babf15239a63bf7f702fd7ca293` |
| BASE_CLOUD_M4 / unchanged Cloud main | `5011c91aac61a0cc6dcc74c256a15b7dee03d785` |

The final Phase F HEAD is the **report-only child of the qualified software HEAD**.
Its actual SHA, tree, fourth commit and independently inspected final-head workflow/job/artifact ids
are recorded in the [PR #14 final Git closure manifest](https://github.com/pierre22400/scenevibe-tv-companion/pull/14).
This follows the Phase E closure convention: a content-addressed report cannot contain its own
commit SHA. No future SHA or final-head CI result is fabricated. The final Work verdict is delivered
only after the report commit itself passes both workflows, preserving every qualified software blob.

Four ordinary single-parent commits: adapter/client, service/reset, tests/boundaries/accounting,
then this report only. There are **35 changed files from the verified start**: 34 software/evidence
files and one report. Each published software tree was compared with the corresponding local Git
tree. Publication used the authenticated GitHub connector and **non-forced fast-forward** updates
of the existing branch; the CLI has no push credential. No published history rewrite, rebase,
force-push, main change, Cloud change, PR retarget, Draft removal or merge occurred.

GitHub references, PR state and both main branches were checked before publication. The full work
order, authoritative architecture, reports A–E, all generic installation classes, all Phase D Video
types and the required existing callers were read. The starting suites were independently executed:
JVM **488 PASS / 0 FAIL / 1 private SKIP**; Python **42 PASS / 0 FAIL / 0 SKIP**.

## 2. Adapter API, exact mapping and finalTrackId isolation

The only new top-level production class is the package-private static compatibility utility
`com.scenevibe.tvcompanionpoc.CloudV1InstallationAdapter`, outside `installation`:

```java
static Assignment adapt(JSONObject envelope, String cloudDeviceId)
// Assignment contains only final InstallRequest request and final String finalTrackId.
InstallRequest Assignment.request()
String Assignment.finalTrackId()

static RuntimeDiagnostics.ManifestCode manifestCode(
    InstallRequest request, InstallationStatus result)
```

| Wire input | Generic codec | Exact artifacts |
| --- | --- | --- |
| v1 assignment without overlayManifest | `scenevibe.runtime-track.v1` | `runtime` = runtimeTrack.toString() encoded as UTF-8 |
| v1 assignment with overlayManifest | `scenevibe.runtime-track-overlay.v1` | Same `runtime`; `manifest` = overlayManifest.toString() encoded as UTF-8 |

The adapter delegates wire type/device/revision/trackId/Prime media/target/platform/manifest checks
to the **byte-unchanged `CloudProtocol.validAssignment`**. Its validator floor is zero, preserving
wire validation while leaving stale/same/new policy entirely to PackageInstaller. Current
JSONObject coercions are preserved: revision uses optLong and finalTrackId uses the same optString
as the former client. All seven frozen envelopes retain their acceptance decisions; three map to
the manifested codec and four to the legacy codec. No new JSON canonicalizer or normalization is used.

The output is bounded by existing InstallRequest invariants and a bounded ACK identifier. It
retains no JSONObject, device token, activation secret, Context, Service, HTTP client or runtime
owner. Artifact access is defensive. `finalTrackId` exists only beside the inert request: it is
absent from InstallRequest, snapshots, PackageInstaller and runtime ports. Distinct finalTrackId
and trackId are tested; ACK uses the former, while runtime/manifest binding retains the latter.
Exact French, decomposed accents, ligature, apostrophe and emoji serialization survive adaptation,
durable installation and process-local store reconstruction.

The adapter never calls VideoOverlayManifestBridge. Its narrow observational manifestCode method
delegates invalid-package classification to the unchanged manifested handler's compatibility seam.
It never gates installation, migrates a cache or authorizes ACK, and does not preflight incoming
bytes on a successful same-revision redelivery.

## 3. One live authority and the owner-thread call chain

Cloud io fetches and parses HTTPS bytes, reads the generic durable view and adapts the envelope.
`AssignmentMutationGate.call` posts one synchronous local operation to Android's main/window owner.
Inside the dispatched operation it rechecks **running and current service client**, then calls the
service's one PackageInstaller. Cloud io waits for completion before considering ACK.

CloudControlClient retains transport, activation/auth, polling/backoff, credentials, the generic
store, generic InstallationOperation bound to `installer::install`, generic RuntimePorts, the
existing owner gate, a service lifetime predicate and a runtime-only reset callback. It loses:

- every MediaSyncedTrackScheduler reference;
- every CloudTrackRepository reference;
- the ManifestInstaller type, field, constructors and install/confirmArmed/activateLegacy seam;
- independent manifested/legacy and same/stale/new revision branches;
- direct parsers, bridge, controller, renderers and windows.

The injected InstallationOperation permits deterministic non-ARMED/exception tests; production
binds exactly one method reference to the service-owned PackageInstaller. Stale, same, newer,
corrupt, unsupported and failed installation decisions come from the already-qualified pipeline.
PackageInstaller remains **byte-exact Phase E**, ACK-free and product/platform/network-free.

OverlayService constructs exactly one AndroidInstallationBackend, one InstallationStore, one
PackageInstaller with the existing static `VideoInstallationHandlers.registry()` and
`TvCapabilities.current()`, and one `LiveVideoRuntimePorts`. The historical startup facade shares
that same store through one additive constructor; it is absent from the new Cloud live path.

The Cloud client reference is **volatile**: owner-thread replacement/reset publication is visible
to Cloud io's pre-HTTP and pre-persistence lifetime rechecks. Stopped/replaced queued work performs
no install or ACK. Interrupting the original owner wait cancels the queued FutureTask, so it cannot
install after the owner queue is released. Replacement after ARM prevents HTTP ACK; replacement
during the response prevents local ACK confirmation. This composition closes Phase E's live
multi-instance seam by serializing live installation and runtime ownership through one service gate.

## 4. Actual service Video runtime ports

The package-visible nested `OverlayService.LiveVideoRuntimePorts` is the **actual production adapter
exercised by the new JVM integration suites**, with real handlers, scheduler and controller.
Only native window callbacks, private disk, HTTPS bytes and owner identity are substituted.
No handler/parser/revision algorithm is copied into the fixture.

| Port | Existing production behavior |
| --- | --- |
| isOwnerThread | Actual main Looper identity; every mutation checks it |
| retireLegacyVisualOwner | Synchronously dismiss the existing OverlayRenderer; create no renderer |
| retireManifestedVisualOwner | Unload/invalidate SceneRuntimeController, then immediately dismiss the existing SceneRenderer |
| loadPreparedTrack | Load the exact trusted ScheduledTrack into the existing scheduler, without reparsing |
| armPreparedManifest | Replace the exact trusted revision/manifest on the existing controller and verify its active revision |
| selectActiveRevision | Final operation only: exact active manifested revision, or legacy with no active manifest |
| abortActivation | Owner-only idempotent best-effort cleanup of controller, both windows, scheduler and selected revision 0 |

There is no store, network, ACK, JSON parser or bridge in these ports. Partial ARM refusals and
ordinary exceptions trigger runtime-only cleanup; a newly committed revision remains durable and
pending, with no rollback. Selection cannot precede all successful handler ARM operations.
Visible manifested→legacy and legacy→manifested handoffs maintain at most one visual owner. Old
generation callbacks cannot resurrect or hide the new manifested scene. ARM itself remains
armed-not-visible; actual due events still come from the unchanged media-time scheduler.

## 5. Durable view, revision policy and complete ACK ordering

CloudControlClient uses **InstallationStore.read()**, including its qualified historical
compatibility view. EMPTY yields revision/ACK 0; SNAPSHOT preserves the exact durable revision and
acknowledged revision. CORRUPT fails before GET, adaptation, installation, overwrite or ACK. It
never falls back to afterRevision=0. The GET query uses the generic **acknowledgedRevision**, even
when obsolete historical revision/runtime/manifest residue coexists with a generic marker.

| Valid Cloud delivery | Local result and writes | ACK consequence |
| --- | --- | --- |
| Stale revision | Installer STALE, zero mutation | Zero HTTP ACK |
| Same revision, including changed incoming codec/bytes | Restore/re-arm exact durable package, zero installation commit | HTTP ACK only after ARMED |
| New supported revision | Validate→prepare→encode→one commit→exact readback→restore→ARM | HTTP ACK only after ARMED |
| New unsupported or invalid package | Bounded refusal before candidate commit/runtime mutation | Zero HTTP ACK |
| Candidate commit/readback failure | CACHE_FAILED; preserve qualified store semantics | Zero HTTP ACK |
| Failed ARM after successful commit | New durable revision remains pending, old ACK remains | Zero HTTP ACK; redelivery can recover |
| Every other InstallationStatus, null or thrown operation | No ACK eligibility | Zero HTTP ACK |

The only HTTP ACK path is:

1. off-owner wire adaptation;
2. one owner-thread PackageInstaller operation, synchronously awaited;
3. result **exactly InstallationStatus.ARMED**;
4. current/running client recheck;
5. POST to the unchanged `/api/v1/devices/<cloudDeviceId>/ack` endpoint with exactly two fields:
   `revision` and the original `finalTrackId`;
6. HTTP 200 and unchanged `CloudProtocol.validAck(response, cloudDeviceId, revision)`;
7. second current/running client recheck;
8. `InstallationStore.markAcknowledged(revision)` must succeed;
9. publish lastSuccessfulAckRevision and clear the prior bounded Cloud error.

HTTP rejection or malformed confirmation leaves the local armed/durable revision pending and the
previous ACK exact. Later redelivery re-arms the durable package without a candidate rewrite and
retries ACK. Local ACK persistence failure after server confirmation is reported without
manufacturing local confirmation; the next poll may retry. Candidate commit and server-confirmed
ACK remain separate writes. The unchanged store rejects confirmation if the current durable
revision no longer matches. ARMED alone is never counted as server acknowledgement.

## 6. Compatibility edges and bounded diagnostics

**Manifested MEDIA/CONTINUE:** the frozen wire validator and adapter accept a structurally valid
v1 envelope. The real generic handler/installer returns **UNSUPPORTED_CAPABILITY**, before any
candidate commit, runtime mutation or ACK. The prior durable/runtime revision and ACK stay exact.
CloudErrorCode is the existing coarse **NETWORK** local-refusal fallback, **not PROTOCOL**;
ManifestCode is NONE. This is the expressly authorized Phase F executable-capability tightening,
not an altered wire contract. TvCapabilities and the Phase D handlers remain unchanged.

**Manifested WALL:** unchanged CloudProtocol rejects the v1 envelope structurally. Actual Cloud
polling reports **PROTOCOL** and invokes no installer/commit/runtime/ACK. No wall-clock runtime arms.

Malformed v1/device binding remains PROTOCOL; authenticated 401 remains UNAUTHORIZED; 204 and
authenticated 404 remain normal empty polls. Existing transport TIMEOUT and backoff behavior is
unchanged. Generic corruption/cache/ARM/unsupported refusal uses bounded local-failure labels
with the existing coarse NETWORK code; no new diagnostics enum/terminology redesign is introduced.
Manifested cache failure retains MANIFEST_CACHE_FAILED. Handler-owned invalidity and timing
cross-contract failure retain MANIFEST_INVALID and MANIFEST_INCONSISTENT. Successful ACK clears
the previous Cloud error. No raw body, artifact, credential, URL, parser/store exception or cause
is exposed by the new adapter/installation/reset diagnostics.

Activation, device-proof/auth, canonical HTTPS transport, disconnect, poll/backoff and activation
status methods are source-body pinned. CloudProtocol, credentials/SecretStore/InstallationIdentity,
AssignmentMutationGate, scheduler, controller and renderer classes are byte-pinned. Disconnect
retains the qualified credential lifecycle and suspends connected polling; it is not Reset.

## 7. Historical live redelivery and exceptional Reset

Both frozen historical shapes are exercised through the **actual new client and installer**:
manifested revision 13 / ACK 13 and legacy revision 14 / ACK 13. Same-revision delivery resolves
the durable handler, restores exact historical runtime/manifest bytes and arms before HTTP ACK.
At the network boundary every original key/byte remains exact, there is no candidate/cache write
or generic marker, and the prior ACK is unchanged. Only server-confirmed ACK is subsequently
persisted. Rejected ACK preserves every original key. Unicode is retained exactly.

A newer delivery over historical state performs one generic snapshot commit. Old runtime,
manifest and historical revision residue stays physically present; the generic marker/snapshot
becomes authoritative for later fetch/redelivery. ACK stays old until server confirmation.
Phase F performs no residue cleanup or eager migration.

`InstallationStore.clearAll()` is the only generic store API addition. It atomically clears the
same installation preference file, including generic snapshot/marker, historical residue and ACK,
under the existing backend monitor. It returns false on ordinary commit refusal/exception and
does no network/runtime/identity/credential work. Existing `clearHistorical()` retains its void
signature and delegates to the same primitive. Direct tests cover generic and historical clear,
atomic refusal, thrown backend failure and actual independent Cloud credentials remaining intact.
All other store/repository methods and the old injectable repository seam are inverse-hash exact.

Reset sets running=false synchronously and returns while io is blocked. The queued io wipe rotates
the installation identity, clears Cloud credentials, then awaits an owner-gate call that clears
the whole installation file and the actual runtime ports. Cloud observations reset after success;
the spent io executor shuts down without waiting on the caller. Existing asynchronous fallback
and bounded watchdog remain. Failed durable clear is bounded NETWORK, keeps readable durable
state/ACK and still clears credentials/runtime; it never claims fabricated empty state.

The service drops/publishes the old client and prevents reconstruction with cloudResetPending
until the asynchronous completion callback returns to main. Later explicit entry can construct
a fresh client over the service's same stack. Both generic/historical and manifested/legacy reset
representations are tested. No identity/credential file is cleared by InstallationStore itself.

## 8. Retained evidence, self-audit and exact changed files

The Phase F baseline inventory pins **61 production/resource/configuration blobs**, **63 prior
test/fixture/inventory blobs**, **11 authoritative documents**, and six earlier boundary/accounting
sources. Only CloudControlClient, OverlayService and the exact store/constructor micro-edits are
old production exceptions. PackageInstaller and all Phase D semantic/runtime helper classes stay
byte-exact. Production inventory is exactly the prior 51 Java files plus the adapter.

Earlier B/C/D/E gates keep every predicate and assertion. Exact inventoried F exceptions and
accounting labels are reversible to each starting Git blob; C/D still compare the original
repository behavior after reversing only the additive constructor. All prior inventories and
four fixtures remain unchanged. Sixteen new Python gates independently balance these exceptions.
Pure model/store/installer compilation with a JDK and empty external classpath still executes.

Four historical client-characterization sources now reference a test-only
`M4PhaseFHistoricalCloudClient`: an exact copy of the starting client changed **only in class name**.
Their old test methods/assertions remain byte-exact after reversing the name and one shared test
HTTPS registration. This oracle preserves old characterization without retaining its seam in
production. The six CloudResetAsync cases still run the **new production client** through a
test-only generic reset fixture; reversing only constructor references restores their exact
starting source. Every other old test is byte-exact. New F suites test the actual replacement
client/installer/ports rather than treating the historical oracle as cutover evidence.

Self-audit inspected every live installation/ACK path, finalTrackId isolation, post-dispatch and
pre-ACK lifetime guards, corrupt-before-fetch behavior, pending-revision recovery, both visual
handoffs, reset and retained source/fixture boundaries. Local deterministic corrections fixed
test expectations for the seven mixed envelopes and qualified Disconnect behavior, isolated
global diagnostics between fixtures, corrected the retry-stage historical ACK assertion, and
published the service's current client with volatile visibility. No contract, capability,
scheduler or Phase G decision was guessed. Final local reruns and exact-head CI are green.

Changed files from the verified start, with paths relative to the repository:

```text
app/src/main/java/com/scenevibe/tvcompanionpoc/CloudV1InstallationAdapter.java
app/src/main/java/com/scenevibe/tvcompanionpoc/CloudControlClient.java
app/src/main/java/com/scenevibe/tvcompanionpoc/OverlayService.java
app/src/main/java/com/scenevibe/tvcompanionpoc/CloudTrackRepository.java
app/src/main/java/com/scenevibe/tvcompanionpoc/installation/InstallationStore.java
app/src/test/java/com/scenevibe/tvcompanionpoc/AssignmentMutationGateTest.java
app/src/test/java/com/scenevibe/tvcompanionpoc/CloudResetAsyncTest.java
app/src/test/java/com/scenevibe/tvcompanionpoc/M4PhaseAFixtures.java
app/src/test/java/com/scenevibe/tvcompanionpoc/M4PhaseATransportTest.java
app/src/test/java/com/scenevibe/tvcompanionpoc/ManifestInstallAckDecisionTest.java
app/src/test/java/com/scenevibe/tvcompanionpoc/M4PhaseFAdapterRejectionTest.java
app/src/test/java/com/scenevibe/tvcompanionpoc/M4PhaseFAdapterTest.java
app/src/test/java/com/scenevibe/tvcompanionpoc/M4PhaseFCapabilityDiagnosticsTest.java
app/src/test/java/com/scenevibe/tvcompanionpoc/M4PhaseFClearStoreTest.java
app/src/test/java/com/scenevibe/tvcompanionpoc/M4PhaseFCloudClientTest.java
app/src/test/java/com/scenevibe/tvcompanionpoc/M4PhaseFCorruptionTest.java
app/src/test/java/com/scenevibe/tvcompanionpoc/M4PhaseFFixtures.java
app/src/test/java/com/scenevibe/tvcompanionpoc/M4PhaseFHistoricalCloudClient.java
app/src/test/java/com/scenevibe/tvcompanionpoc/M4PhaseFHistoricalRedeliveryTest.java
app/src/test/java/com/scenevibe/tvcompanionpoc/M4PhaseFHttps.java
app/src/test/java/com/scenevibe/tvcompanionpoc/M4PhaseFNonArmedTest.java
app/src/test/java/com/scenevibe/tvcompanionpoc/M4PhaseFOwnerGateTest.java
app/src/test/java/com/scenevibe/tvcompanionpoc/M4PhaseFResetFixture.java
app/src/test/java/com/scenevibe/tvcompanionpoc/M4PhaseFResetTest.java
app/src/test/java/com/scenevibe/tvcompanionpoc/M4PhaseFRuntimeFailureTest.java
app/src/test/java/com/scenevibe/tvcompanionpoc/M4PhaseFRuntimeTest.java
tests/test_m4_phase_b_boundary.py
tests/test_m4_phase_c_boundary.py
tests/test_m4_phase_d_boundary.py
tests/test_m4_phase_e_boundary.py
tests/test_m4_phase_f_boundary.py
.github/scripts/m4-phase-f-baseline.json
.github/scripts/m4-phase-a-test-summary.py
.github/workflows/android-debug.yml
docs/m4-phase-f-cloud-v1-adapter-report.md
```

## 9. Executed JVM/Python counts and frozen hashes

| JVM bucket | PASS | FAIL | SKIP | Total |
| --- | ---: | ---: | ---: | ---: |
| Retained before Phase A | 178 | 0 | 1 | 179 |
| Phase A | 30 | 0 | 0 | 30 |
| Phase B | 39 | 0 | 0 | 39 |
| Phase C | 43 | 0 | 0 | 43 |
| Phase D | 83 | 0 | 0 | 83 |
| Phase E | 115 | 0 | 0 | 115 |
| New Phase F | 138 | 0 | 0 | 138 |
| **Full Gradle JVM** | **626** | **0** | **1** | **627** |
| Python retained through E | 42 | 0 | 0 | 42 |
| New Phase F Python | 16 | 0 | 0 | 16 |
| **Full Python** | **58** | **0** | **0** | **58** |

The 12 new JVM suites execute: adapter 7, adapter rejection 20, client 30, non-ARMED 9,
capability/diagnostics 5, corrupt store 6, owner races 6, actual runtime ports 9, runtime-stage
faults 18, historical redelivery 6, direct clear regressions 10 and asynchronous reset 12.
Parameterized cases are counted from **actual Gradle JUnit XML**, not annotations. The downloaded
`scenevibe-m4-phase-a-test-summary` artifact was checked against the complete frozen A–F suite
inventory, all bucket counts, the exact skip and every fixture digest. Local actual JUnit execution
of all production/test sources and Python agree with CI.

The sole SKIP remains `M1CloudInteropTest.originalColumboProjectionIsInstallable`, requiring
private opt-in `SCENEVIBE_M1_SONY_FIXTURES`. It is never reported as PASS.

| Frozen evidence | Unchanged SHA-256 |
| --- | --- |
| Seven M1 Cloud envelopes | `19ee6fc3e2eba7009c9d345c33a63ca5617621fac9c8f5618cb24ee2932d6eab` |
| 94-case OverlayManifest corpus | `c78addf877b277e38f66c12274e4c8ed2ce12a503d883e27fd9950215cafbf30` |
| Manifested historical cache 13/13 | `76d109c4b69f3e707912449f87e3fa6b9081e4564a12c77c633ae7ed7563beb1` |
| Legacy historical cache 14/13 | `1676ed2033ec0e589509da7bdab3294ed8b5949153eeb2cfdb539aa23409cc8c` |

## 10. Exact software-head Android CI and final-head closure gate

Both workflow metadata head_sha values are exactly
`110608ff30fdf3a0b140dddaf72ace2fd1149eb9`. Jobs, every step and relevant logs were inspected:

- [Android debug APK run 37237970946](https://github.com/pierre22400/scenevibe-tv-companion/actions/runs/37237970946),
  job `111540798813`: **SUCCESS**. Python 58/0/0, actual Gradle JVM 626/0/1, XML accounting,
  `lintDebug`, `assembleDebug`, LAN DEV compile, Consumer Cloud-origin compile and all stable
  signing/certificate/upload/discard steps actually executed successfully.
- [Android 15 / API 35 run 37237970902](https://github.com/pierre22400/scenevibe-tv-companion/actions/runs/37237970902),
  job `111540798475`: **SUCCESS**. Six explicit performed PASS checks: Cloud-mode APK build,
  install, package presence, crash-free MainActivity launch, resumed/top activity and package
  presence after force-stop. Every job step completed successfully.

Software-head artifacts, each bound by independent metadata to the exact software HEAD:

| Artifact | ID |
| --- | ---: |
| Actual Gradle XML-derived summary | 11316232936 |
| Cloud debug APK | 11316492263 |
| Stable-signed Cloud APK | 11316078468 |

Performed apksigner verification yields the unchanged stable certificate SHA-256:
`f908bf564ed97ba67e02b1ebc89eb0239cf980752587f55eb9ec0419791a2e9c`.
Application id `com.scenevibe.tvcompanionpoc`, versionCode 12, versionName
`0.8.2-tv-release-hardening`, minSdk 26 and target/compileSdk 35 remain unchanged. Manifest,
permissions, specialUse foreground-service type, signing inputs, build configuration and resource
blobs are pinned. The only workflow delta adds the F label to the existing XML accounting steps.
No release/deploy workflow is dispatched; no production change is made.

The report-only final HEAD is **independently requalified**, not inferred from parent success.
Its actual run/job ids, logs, downloaded Gradle summary, artifacts and signing certificate are
recorded after publication in the PR closure manifest linked in section 1. Work stops only after
that exact final-head evidence and final PR/base/main checks are complete.

## 11. Deliberate limits, untouched Phase G and verdict

Service-start/boot restore and diagnostics still use their **historical view**. Against a generic
snapshot the obsolete cache truth can show no historical track even while the new installation
is durable. This known temporary mismatch is explicitly left to Phase G. No generic boot/restore
coordinator, BootReceiver cutover, hard-reboot continuity claim, diagnostics terminology cleanup,
old-key deletion or migration policy is implemented. The old service static install/confirm/legacy
helpers remain source-body exact for retained characterization, but have no live production caller.
They are deferred cleanup, not an independent Cloud authority.

No Cloud repository/server contract/capability negotiation changes, public capabilities API,
Banner/Language handler, wall-clock runtime, M5 scheduler, remote assets/CDN/cache, WebView,
dynamic loading or new permission appear. Production remains **SHADOW**; PR #14 remains
**OPEN / DRAFT / unmerged**, and Phase G has **not started**. Phase G is mandatory before M4 completion.

**Sony final physical qualification remains pending. No new Sony physical qualification is claimed.**
The API 35 workflow uses a standard Android image. It cannot qualify Android TV/D-pad placement,
real TV overlay/FGS behavior, Prime MediaSession, genuine boot or hard reboot. Force-stop is only
an install-persistence lower bound. The later integrated Sony gate still must exercise upgraded
APK, cache compatibility, new send, replacement, same-revision redelivery, seek/pause/eligibility,
hard reboot, post-reboot redelivery and Unicode.

No remaining deterministic Phase F defect or blocker was found after the localized corrections
and qualifications. Native window failure/power-loss and future boot/diagnostics behavior are not
manufactured guarantees. This cycle implements and qualifies only the authorized live cutover.

**READY FOR M4 PHASE G**

Stop after this report and the exact final-head PR closure. Do not merge or begin Phase G.
