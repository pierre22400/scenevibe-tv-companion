# SCENEVIBE OS — M4 PHASE G — GENERIC RESTORE, DIAGNOSTICS AND SOFTWARE CLOSURE REPORT

Date: 2026-10-05. Repository: `pierre22400/scenevibe-tv-companion`.
Branch: `work/scenevibe-os-m4-tv-installation-001`. PR #14 stays **OPEN / DRAFT / unmerged**.
Production revision authority remains **SHADOW**. This cycle implements **Phase G only**.
**Sony physical qualification has NOT been performed.**

## 1. Git provenance, final HEAD convention and exact scope

| Reference | Exact SHA |
| --- | --- |
| Verified implementation start / full Phase G work order | `3a987f337a324060b67b78cc1e3f6c29233296f8` |
| Verified Phase F closure ancestor | `eb0e0d823edc291bf1a39583ba70f098fac189e0` |
| Startup/boot composition commit | `0cc8db3d9ae4218b02ab7e0c9be3afaf7858fdb9` |
| Generic diagnostics/offline/reset commit | `c911961f3aa57335a405e5184ec89ff5e2a7fd15` |
| Qualified software HEAD / tests, boundaries, accounting and protocol commit | `d482b19995c6416f1b44a18decba79f9c35f307a` |
| Qualified software tree | `b64049a3527980d7270077e72fb448638b64706c` |
| BASE_TV_M4 / unchanged TV main / unchanged PR base main | `ecdf77bec9f93babf15239a63bf7f702fd7ca293` |
| BASE_CLOUD_M4 / unchanged Cloud main | `5011c91aac61a0cc6dcc74c256a15b7dee03d785` |

The **final Phase G HEAD** is the documentation-only child of that qualified software HEAD.
Its exact SHA, tree, fourth commit, independently inspected final-head workflow/job/artifact ids
and **the final stable-signed APK artifact id to use for Sony** are recorded after publication in
the [PR #14 final Git closure manifest](https://github.com/pierre22400/scenevibe-tv-companion/pull/14).
This retains the Phase E/F closure convention: a content-addressed report cannot include its own
commit SHA. No future SHA or final-head CI result is invented. Work's final verdict is delivered
only after that documentation child also passes the complete exact-head qualification.

Four ordinary single-parent commits; **31 changed files from the verified start**. The final
child adds this report and makes a narrow protocol clarification separating network-isolated
local restore from later automatic Cloud polling. Every qualified software/test/configuration
blob remains identical. Each published tree was compared with its locally tested Git tree.

Publication uses the authenticated GitHub connector to create identical trees, ordinary commits
and **non-forced fast-forward** updates of the existing branch; the CLI has no push credential.
There is no rebase, history rewrite, force-push, merge, main change, PR retarget or Draft removal.
The Cloud repository is read-only. GitHub is the source of truth throughout.

The work order, authoritative architecture, reports A–F, generic types, Video helpers, service,
Cloud client/repository, boot, diagnostics, main screen and all F adapter/client/runtime/reset
tests and gates were read before editing. The initial baseline was independently executed:
JVM **626 PASS / 0 FAIL / 1 private SKIP**; Python **58 PASS / 0 FAIL / 0 SKIP**.
The start differs from Phase F closure only by the Phase G work order.

## 2. One generic startup authority and strict ordering

OverlayService retains the single F-owned AndroidInstallationBackend, InstallationStore,
PackageInstaller, static VideoInstallationHandlers registry, TvCapabilities and actual
LiveVideoRuntimePorts. After constructing fresh scheduler/controller/ports on Android main,
startup invokes its small package-visible `restoreInstalledPackage` composition seam:

```text
durable = installationStore.read()
EMPTY    -> no operation
CORRUPT  -> CACHE_FAILED; retain all bytes
SNAPSHOT -> packageInstaller.install(durable.snapshot().canonical(), videoRuntimePorts)
```

That inert request has exactly the durable revision. The **byte-unchanged Phase E installer**
takes its qualified same-revision path: read durable state, resolve its stored handler id and
exact codec binding, restore through that handler, then ARM through owner ports. It never
validates incoming replacement bytes, commits, migrates or acknowledges on this path.
There is no new PackageInstaller API, restore coordinator, registry, handler, parser or result
vocabulary. The service seam merely composes the required existing entry point.

Restore completes **before `mediaSessionProbe.start()` and before Cloud construction/start**.
The unchanged F Cloud client receives the exact same store/installer/ports. Startup needs no
Cloud Send, transport, credential or ACK collaborator. The active revision is selected only
by existing runtime ports after successful ARM, never inferred from a cache number by service code.

| Startup durable condition | Bounded result / fresh runtime | Durable writes / ACK |
| --- | --- | --- |
| EMPTY, including empty file with ACK 0 | No attempt, active revision 0; neutral nullable observation | 0 / 0 |
| CORRUPT marker/encoding, incoherent ACK, partial historical tuple or ordinary read failure | CACHE_FAILED, active 0; no clear, repair, historical fallback or reinterpretation as EMPTY | 0 / 0 |
| Supported manifested/legacy SNAPSHOT | ARMED from exact durable bytes and binding | 0 / 0 |
| Missing/unknown/mismatched durable handler or codec binding | CACHE_FAILED, active 0 | 0 / 0 |
| Invalid/unsupported durable semantic content | ARM_FAILED, active 0; exact pending package retained | 0 / 0 |
| Actual port refusal/ordinary exception, unavailable runtime or wrong owner | ARM_FAILED, active 0; existing runtime-only abort where authorized | 0 / 0 |

Only ordinary RuntimeException is bounded; VM errors are not broadly swallowed. Corruption never
triggers destructive cleanup. Startup logs only an existing bounded status or EMPTY; no artifact,
parser cause, credential, URL or raw error reaches its new observation.

## 3. Historical and generic restoration evidence

The G startup fixture composes the real InstallationStore, PackageInstaller, static Video
handlers/capabilities, scheduler, SceneRuntimeController and the **actual nested service ports**.
It reuses F's runtime fixture, substituting disk/native window callbacks/owner identity only.
It constructs no Cloud client, credentials or ACK callback and copies no handler/revision algorithm.

| Exact startup representation | Revision / durable ACK | Resolved codec and handler | Result |
| --- | --- | --- | --- |
| Frozen manifested historical cache | 13 / 13 | `scenevibe.runtime-track-overlay.v1` | ARMED; exact runtime and manifest, generic marker absent |
| Frozen legacy historical cache | 14 / 13 | `scenevibe.runtime-track.v1` | ARMED; runtime only, generic marker absent |
| Generic manifested over unchanged historical residue | 15 / 13 | `scenevibe.runtime-track-overlay.v1` | ARMED; exact authoritative snapshot, no historical rewrite |
| Generic legacy over unchanged historical residue | 15 / 13 | `scenevibe.runtime-track.v1` | ARMED; exact authoritative snapshot, no historical rewrite |

Every row proves **zero candidate commit, zero ACK write, zero clear/migration**, exact revision,
handler, artifact names and bytes, and exact ACK. Both frozen historical strings remain byte-exact.
Generic authority wins over obsolete residue; a corrupt present marker cannot restore valid
historical content underneath it. Generic marker remains absent after historical restore.

An ACK lower than the installed revision is explicitly valid for startup. Local restoration
does not manufacture Cloud confirmation. A separate case proves already-confirmed generic 15/15
also restores with zero ACK write. Recreated store/installer/media cores restore both historical
and generic representations without another Send or write. This is **process-local recreation**,
not a claimed TV reboot.

Historical parser-valid manifested **MEDIA/CONTINUE** now fails closed on startup through the
canonical Video capability model. The existing handler's `validate` reports UNSUPPORTED_CAPABILITY;
the unchanged installer's same-revision restore boundary maps that preparation refusal to
**ARM_FAILED**. WALL and invalid durable semantic content likewise remain unarmed. This does not
modify the handler or broaden capability support; bytes/revision/ACK stay intact, with no fallback,
rollback or migration. Diagnostics READY means structural snapshot presence, not executability.

## 4. Runtime visibility, failure recovery and generation proof

ARM itself shows no native scene/card. All four startup profiles remain invisible until the
unchanged scheduler receives an eligible passive media due event; then exactly one owner displays.
Consumer entry policies and boot-prepare in both Consumer/LAN modes remain byte-exact and invisible
on entry. No permanent Consumer badge or new clock/player control is introduced.

The 36 parameterized failure cases inject false/RuntimeException after every applicable real port
operation across both representations/profiles: legacy retirement, manifested retirement,
track load, manifest arm and selection. Existing bounded abort clears partial runtime ownership
without touching storage/ACK; a fresh process-local composition then restores the same pending
package with zero write. Non-owner and teardown-unavailable cases do not fake activation.

Replacement after restoration retires the visible opposite owner synchronously, selects only
after successful ARM and keeps maximum one visible owner. Old generation due/expiry callbacks
cannot resurrect the old scene or hide the new scene. Runtime ports, scheduler, controller,
renderers, media matcher and passive MediaSession semantics are unchanged.

## 5. Boot and autostart generic truth

BootReceiver reads `new InstallationStore(new AndroidInstallationBackend(app)).read()`.
Its narrow `decide` seam supplies snapshot presence to **unchanged AutostartPolicy**:
SNAPSHOT true; EMPTY/CORRUPT false. It reads metadata only, never canonical artifacts or handler
capabilities, and never ARM/writes/migrates/ACKs. Unsupported structurally valid content can permit
boot preparation; the service's existing installer then independently fails closed.

Every old policy assertion remains. New tests cover both generic and frozen historical profiles,
empty/corrupt content, usable credential as an independent START resource, disabled default opt-in,
overlay-first permission precedence and missing media access. A valid Cloud credential can permit
START even with corrupt durable content; local restore and Cloud overwrite still refuse corruption.

Receiver action whitelist remains BOOT_COMPLETED / MY_PACKAGE_REPLACED after unlock. It starts
only ACTION_BOOT_PREPARE via startForegroundService, never MainActivity or a direct-boot path.
Its failure logs now use fixed labels instead of propagating arbitrary exception causes.
Manifest, permissions and foreground-service type stay byte-exact.

## 6. Generic diagnostics, compatibility aliases and normal UI

RuntimeDiagnostics.capture uses one generic store read through `installationSnapshot`, while
preserving read-only credential peek and installation-id peek. Opening/refreshing Diagnostics
does not mint, migrate, encrypt, clear, commit or ACK any state. The read model retains no store,
snapshot, request, map, byte array, parsed Video object or raw cause; new fields are scalars/enums.

| First-class generic field | Bounded meaning |
| --- | --- |
| installationState | EMPTY / READY / CORRUPT; READY is structural SNAPSHOT only |
| installationPresent | True exactly for structural SNAPSHOT |
| installedRevision | Exact snapshot revision, otherwise 0 |
| acknowledgedRevision | Exact separate durable ACK for SNAPSHOT, otherwise unavailable/0 |
| packageCodecId / packageHandlerId | Exact bounded durable binding ids, otherwise null |
| lastStartupRestoreResult | Last startup attempt's existing InstallationStatus; neutral null for EMPTY/not attempted |

The last startup observation is historical, not current runtime selection or server confirmation.
Reset clears it. CORRUPT's neutral metadata does not mean durable data was erased or ACK reset.

Compatibility fields remain explicitly documented: `cachedTrackPresent` aliases generic presence,
`cachedRevision` aliases installedRevision and `lastAcknowledgedRevision` aliases acknowledgedRevision.
`cachedTrackId` remains a nullable Video compatibility field; capture leaves it null for **both**
generic and historical compatibility snapshots, avoiding any opaque artifact parsing for an id.
Existing builder seams/old test assertions remain available. No compatibility value becomes authority.

DiagnosticsActivity renders **Installed package: yes/no/corrupt**, codec, handler, installed revision,
ACK and last startup outcome before labeled compatibility cache lines. Existing identities remain
abbreviated; bounded manifest/scene outcomes also appear. It shows no artifact/text/URL/secret/error
body and stays read-only except for the already-explicit Reset button.

MainActivity.refreshCloud reads generic state without parsing. Its existing activation/connection
priority remains: activation code, offline, connected, not connected. Offline SNAPSHOT wording is
**“Offline (using saved SceneVibe content)”**; EMPTY/CORRUPT is “Offline”. Revision/codec/handler
remain absent from the normal consumer screen. Read and render tests prove zero writes.

## 7. Stopped/running exceptional Reset

The explicit stopped-service branch rotates the separate InstallationIdentity, resets existing
CloudDeviceCredentials, calls **InstallationStore.clearAll() once**, then resets Cloud observations.
There is no concurrent Cloud client in this branch and no historical repository construction.
Successful whole-file clear removes generic snapshot, historical residue and ACK atomically.
Installation/LAN pairing/credential storage remain distinct; actual owner tests prove deliberate
installation-id rotation, credential deletion, LAN pairing preservation and one whole-file clear.

Refused durable clear returns false, preserves readable cache/ACK, resets stale observations and
reports bounded NETWORK without claiming ready-to-pair success in the stopped screen. Identity
rotation failure stops subsequent credential/cache destruction and exposes no raw cause.

Running-service Reset retains the **byte-unchanged F client and actual service dispatch**: running=false,
asynchronous io rotation/credential wipe, awaited owner clearAll/runtime abort, spent executor,
cloudResetPending barrier and later fresh-client reconstruction. G integration proves reset after
restoration clears durable/runtime state on the existing owners. No reset is triggered automatically
by corruption, startup, offline or a 401.

## 8. Historical cleanup and protected live Cloud behavior

Removed from production: cloudTrackRepository field, historical startup restoreWithManifest/restore,
and all three dead static service helpers `installManifestedRevision`,
`confirmManifestedRevisionArmed`, `activateLegacyRevision`.
Their exact bodies and Javadocs move into **test-only M4PhaseGHistoricalService**. Only helper names
change in three old characterization sources; inverse hashes reconstruct each exact starting source,
preserving every old method/assertion. F protected-method hashes still qualify the oracle bodies;
new G suites exercise the actual replacement production seam.

CloudTrackRepository remains byte-exact as an isolated historical characterization type.
**It has zero other production callers**, including service, boot, diagnostics and main screen.
Its old parsing/clearing methods cannot govern any generic runtime path. No historical key deletion,
residue cleanup or eager migration occurs during startup/read; only explicit Reset may clear the file.

CloudControlClient, CloudV1InstallationAdapter, CloudProtocol, AssignmentMutationGate, identity,
credentials, generic core and all Video types remain byte-exact to the verified G start.
All 138 F tests remain executed with unchanged assertions. Twelve additional actual F HTTP/owner
integration cases cover generic restore followed by same revision, newer revision, pending ACK retry,
corruption refusal, MEDIA/CONTINUE refusal and coordinated reset, across both Video profiles.

Same revision re-arms exact durable bytes with zero candidate commit, ignoring changed incoming text.
Newer revision commits once, retires the restored owner, arms then ACKs. Corruption blocks both
restore and Cloud before GET/overwrite. MEDIA/CONTINUE stays wire-valid/local UNSUPPORTED with
zero mutation/ACK. Original **finalTrackId**, separate from trackId, remains the two-field ACK binding.
Exact ARMED and current/running rechecks precede HTTP ACK; existing validAck server confirmation
precedes markAcknowledged and the successful-ACK observation. Malformed/rejected confirmation keeps
the prior ACK until retry. Startup alone never performs any of those network/confirmation operations.

## 9. Provenance gates, actual execution counts and frozen evidence

The G inventory pins **62 production/resource/configuration blobs**, **81 retained test/fixture/
inventory blobs**, **13 authoritative documents** and **seven earlier boundary/accounting sources**.
Exactly six production and three helper-reference test exceptions are reversible to their whole
starting blobs. B–F predicates and assertions survive after reversing only exact G exceptions.
The old F store/constructor check stays unchanged; G reversals apply only where required.
All pure-core empty-classpath JDK compilation gates continue to execute.

Eighteen new Python cases prove startup authority/order/no writes, boot/diagnostic/read-only metadata,
normal status, both resets, zero repository callers, unchanged F/core/wire/platform/signing bytes,
one bridge caller, no new production definition/registry/capability, exact historical oracle,
retained source provenance, additive executed inventory, Python documentation and manual Sony scope.
Production inventory remains **52 Java files**; no new top-level production class is added.

The downloaded actual **Gradle XML-derived summary artifact `11327456582`** was validated against
every A–G suite name/count, all bucket totals, exact private SKIP and four frozen digests.
Counts are executed results, not annotation estimates. Local actual Java/JUnit and Python agree.

| JVM bucket | PASS | FAIL | SKIP | Total |
| --- | ---: | ---: | ---: | ---: |
| Retained pre-Phase-A | 178 | 0 | 1 | 179 |
| Phase A | 30 | 0 | 0 | 30 |
| Phase B | 39 | 0 | 0 | 39 |
| Phase C | 43 | 0 | 0 | 43 |
| Phase D | 83 | 0 | 0 | 83 |
| Phase E | 115 | 0 | 0 | 115 |
| Phase F | 138 | 0 | 0 | 138 |
| New Phase G | 131 | 0 | 0 | 131 |
| **Full Gradle JVM** | **757** | **0** | **1** | **758** |
| Python retained through F | 58 | 0 | 0 | 58 |
| New Phase G Python | 18 | 0 | 0 | 18 |
| **Full Python** | **76** | **0** | **0** | **76** |

Executed G suites: StartupTest 20; RestoreFailureTest 19; ArmFailureTest 36; StartupStateTest 6;
BootTest 6; DiagnosticsTest 12; DiagnosticsStateTest 8; ResetTest 12; CloudAfterRestoreTest 12.
Parameterized profiles/faults are counted by actual XML. The sole private SKIP remains
`M1CloudInteropTest.originalColumboProjectionIsInstallable`, requiring `SCENEVIBE_M1_SONY_FIXTURES`;
it is never reported as PASS. Seven envelopes and 94 corpus cases remain exercised in retained
suites and are not double-counted as additional JUnit executions.

| Frozen evidence | Unchanged SHA-256 |
| --- | --- |
| Seven M1 Cloud envelopes | `19ee6fc3e2eba7009c9d345c33a63ca5617621fac9c8f5618cb24ee2932d6eab` |
| OverlayManifest 94-case corpus | `c78addf877b277e38f66c12274e4c8ed2ce12a503d883e27fd9950215cafbf30` |
| Manifested historical cache | `76d109c4b69f3e707912449f87e3fa6b9081e4564a12c77c633ae7ed7563beb1` |
| Legacy historical cache | `1676ed2033ec0e589509da7bdab3294ed8b5949153eeb2cfdb539aa23409cc8c` |

## 10. Exact software-head Android qualification and final-head gate

Both run metadata records have `head_sha=d482b19995c6416f1b44a18decba79f9c35f307a`, trigger
pull_request, completed status and success conclusion. Every job/step and relevant log was inspected:

- [Android debug APK run 37268811696](https://github.com/pierre22400/scenevibe-tv-companion/actions/runs/37268811696),
  job `111631229879`: **SUCCESS**. Python 76/0/0, full Gradle JVM 757/0/1, XML inventory,
  lintDebug, assembleDebug, LAN DEV compile and Consumer Cloud-origin compile all actually ran.
  Stable keystore/sign/verify/upload/discard steps all executed successfully, none skipped.
- [Android 15 / API 35 run 37268811738](https://github.com/pierre22400/scenevibe-tv-companion/actions/runs/37268811738),
  job `111631230393`: **SUCCESS**. Six performed log PASS checks: Cloud-mode APK build, install,
  package presence, crash-free MainActivity launch, resumed/top activity and package presence
  after force-stop. All job steps completed successfully.

| Software-head artifact, independently bound by metadata to that exact SHA | ID |
| --- | ---: |
| Actual Gradle XML-derived summary | 11327456582 |
| Cloud debug APK | 11327610978 |
| Stable-signed Cloud APK | 11327695680 |

The final documentation HEAD is **independently requalified**, not inferred from its parent's
success. Its actual run/job/artifact ids and downloaded summary comparison are recorded in the
PR closure manifest linked in section 1. **Use that final-head stable artifact id for Sony**.
Archive metadata/digests are not misrepresented as raw APK hashes.

Performed apksigner certificate SHA-256 remains
`f908bf564ed97ba67e02b1ebc89eb0239cf980752587f55eb9ec0419791a2e9c`.
Unchanged application id `com.scenevibe.tvcompanionpoc`, versionCode 12,
versionName `0.8.2-tv-release-hardening`, minSdk 26, target/compileSdk 35, manifest permissions,
specialUse foreground service, signing chain, resources and build inputs are byte-pinned.
Workflow changes only two XML-accounting labels. No release/deploy workflow is dispatched.
Real Android Gradle/lint/build/signature/platform evidence comes from GitHub Actions; local JVM
verification compiles all actual sources against the official AGP mockable Android jar.

## 11. Exact changed-file inventory

Relative to the repository, production (six modified files):

```text
app/src/main/java/com/scenevibe/tvcompanionpoc/OverlayService.java
app/src/main/java/com/scenevibe/tvcompanionpoc/BootReceiver.java
app/src/main/java/com/scenevibe/tvcompanionpoc/DiagnosticsStore.java
app/src/main/java/com/scenevibe/tvcompanionpoc/RuntimeDiagnostics.java
app/src/main/java/com/scenevibe/tvcompanionpoc/DiagnosticsActivity.java
app/src/main/java/com/scenevibe/tvcompanionpoc/MainActivity.java
```

Tests/evidence/documentation (25 files; three old helper-name rewires, 11 new JVM sources,
seven gate/accounting modifications and four new gate/inventory/document artifacts):

```text
app/src/test/java/com/scenevibe/tvcompanionpoc/M4PhaseAFixtures.java
app/src/test/java/com/scenevibe/tvcompanionpoc/AssignmentMutationGateTest.java
app/src/test/java/com/scenevibe/tvcompanionpoc/ManifestInstallAckDecisionTest.java
app/src/test/java/com/scenevibe/tvcompanionpoc/M4PhaseGHistoricalService.java
app/src/test/java/com/scenevibe/tvcompanionpoc/M4PhaseGFixtures.java
app/src/test/java/com/scenevibe/tvcompanionpoc/M4PhaseGStartupTest.java
app/src/test/java/com/scenevibe/tvcompanionpoc/M4PhaseGStartupStateTest.java
app/src/test/java/com/scenevibe/tvcompanionpoc/M4PhaseGRestoreFailureTest.java
app/src/test/java/com/scenevibe/tvcompanionpoc/M4PhaseGArmFailureTest.java
app/src/test/java/com/scenevibe/tvcompanionpoc/M4PhaseGBootTest.java
app/src/test/java/com/scenevibe/tvcompanionpoc/M4PhaseGDiagnosticsTest.java
app/src/test/java/com/scenevibe/tvcompanionpoc/M4PhaseGDiagnosticsStateTest.java
app/src/test/java/com/scenevibe/tvcompanionpoc/M4PhaseGResetTest.java
app/src/test/java/com/scenevibe/tvcompanionpoc/M4PhaseGCloudAfterRestoreTest.java
tests/test_m4_phase_b_boundary.py
tests/test_m4_phase_c_boundary.py
tests/test_m4_phase_d_boundary.py
tests/test_m4_phase_e_boundary.py
tests/test_m4_phase_f_boundary.py
tests/test_m4_phase_g_boundary.py
.github/scripts/m4-phase-g-baseline.json
.github/scripts/m4-phase-a-test-summary.py
.github/workflows/android-debug.yml
docs/m4-sony-physical-qualification-protocol.md
docs/m4-phase-g-generic-restore-diagnostics-report.md
```

## 12. Self-audit corrections, manual Sony gate, limits and stop

Self-audit checked actual restore order, same-revision zero-write behavior, binding/corruption
refusals, every port failure, pending recovery, one visual owner/generation, metadata aliases,
read-only peeks/rendering, both resets, dead callers and complete unchanged F Cloud/ACK paths.
The new suites and exact inverse/hash gates pass. A localized gate correction removed an unnecessary
G test-patch reversal from F's unchanged store micro-check; all 76 Python cases then re-passed.
No production semantic correction or PackageInstaller API change was required.

The manual protocol was clarified during this same audit: **no new Send alone does not stop
automatic polling/redelivery**. Controlled network isolation (without Disconnect/Reset) separates
zero-ACK local restore from a subsequent legitimate Cloud confirmation or newer pre-existing
assignment. This documentation-only correction is included in the final child and requalified.

The requested document is
[`docs/m4-sony-physical-qualification-protocol.md`](m4-sony-physical-qualification-protocol.md).
It has 20 numbered checks with PASS/FAIL/NOT RUN and exact observed revision/ACK/evidence fields:
pre-upgrade version/ids/permissions/opt-in/cache; exact final stable APK via adb install -r without
uninstall/data clear; identity/pairing/permissions continuity; no-Send local historical/generic
restore and no stale card; new manifested Send and four diagnostics counters; visible newer
replacement/one owner; same-revision redelivery; pause/resume; backward/forward seek; eligibility
loss/return; autostart and genuine hard TV reboot without Send; exact handler/revision/ACK restore
and armed-not-visible; post-reboot redelivery; composed/decomposed French, ligature, apostrophe and
emoji; complete AndroidRuntime/FATAL/Looper/thread/window/installer/duplicate-owner logcat inspection.

**Sony physical qualification has NOT yet been performed or claimed.** Blank protocol results
remain NOT RUN. API 35 uses a standard Android image, not Android TV; force-stop/emulator is not
a hard reboot substitute. Native Sony windows/FGS, Prime MediaSession, TV/D-pad placement, genuine
boot and on-device upgrade/pairing continuity require the later physical evidence and separate audit.
JVM owner/disk boundaries do not certify power-loss/flash behavior. READY metadata and last startup
observation are deliberately not live visibility or Cloud acknowledgement guarantees.

No remaining localized deterministic Phase G software defect or blocker was found after corrections
and qualifications. No destructive migration/residue cleanup, new capability, Cloud wire/server
change, Banner/Language, wall-clock runtime, remote asset/CDN/cache, dynamic loading, permission,
M5/M6/M7 component, production cutover or merge is introduced. Both main branches remain at the
specified bases. Production stays **SHADOW**; PR #14 stays **OPEN / DRAFT / unmerged**.

**READY FOR M4 SONY QUALIFICATION**

Stop only after exact final-head qualification and its PR closure manifest. Physical qualification
and M5+ have not started; no complete-M4 physical acceptance is asserted.
