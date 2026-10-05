# M4 Phase G — Sony hard-reboot corrective cycle

Verdict: **READY FOR M4 SONY RE-QUALIFICATION**, subject to the exact final Git closure
and its independently inspected final-head qualifications in [PR #14](https://github.com/pierre22400/scenevibe-tv-companion/pull/14).
This is a localized correction of Phase G. Production remains **SHADOW**;
PR #14 remains **OPEN / DRAFT / unmerged**. No M4 completion, Phase H, M5+,
Cloud server change, deployment or corrective physical Sony PASS is asserted.

## Immutable scope and Git closure

| Reference | Exact value |
| --- | --- |
| Verified corrective start / faulty final G HEAD | `824339de562c0de8542f4c1b2a22266832c8abef` |
| Starting tree | `30a958558383e09e7ce0721cb669926b628ce2ef` |
| Original G software-qualified HEAD | `d482b19995c6416f1b44a18decba79f9c35f307a` |
| Phase F closure ancestor | `eb0e0d823edc291bf1a39583ba70f098fac189e0` |
| Corrective software HEAD, all qualifications below actually executed | `ba714e3d9b18376f6df7e426a49133ef0ab5c07b` |
| Corrective software tree | `96c6441063d47e0db03156f1660a9115cd07ab25` |
| BASE_TV_M4 / unchanged TV main / unchanged PR base | `ecdf77bec9f93babf15239a63bf7f702fd7ca293` |
| BASE_CLOUD_M4 / unchanged Cloud main | `5011c91aac61a0cc6dcc74c256a15b7dee03d785` |
| **Exact final report-child HEAD, final-head runs/jobs/artifacts and final raw APK hash** | **[PR #14 final Git closure manifest](https://github.com/pierre22400/scenevibe-tv-companion/pull/14)** |

The final row follows the established E/F/G content-addressed closure convention:
a report cannot contain its own commit hash or CI/artifact ids created afterwards.
The final manifest records those exact values after this documentation-only child is
published and all three workflows are inspected again on that **new final HEAD**.
The software-head artifact below is separate evidence; use the final-head stable
artifact identified in that manifest for Sony. Do not infer qualification of a child
from its parent, even though its production/test/configuration bytes are identical.

Before editing, GitHub branch, PR and local HEAD independently matched the specified
start; the worktree was clean, PR OPEN/DRAFT/unmerged and both main refs unchanged.
The architecture, G work order, original G report, Sony protocol and C/D/E/F reports
were read in full. The exact original G diff and actual store/backend/codec/installer,
handlers, service/boot, owner/ACK, diagnostics and preference access paths were inspected.
The supplemental Sony hard-reboot corrective work order controls this cycle; the
[authoritative M4 architecture](scenevibe-os-m4-tv-installation-architecture.md) remains unchanged.

Publication uses ordinary single-parent commits and non-forced fast-forward branch
updates, with each GitHub tree matched to the locally reviewed Git tree. No merge,
rebase, force-push, main change, PR retarget, Draft removal or Cloud write is involved.

## Authoritative physical evidence: initial session PARTIAL / FAIL

The faulty stable APK was verified by the physical operator:
`scenevibe-tv-companion-cloud-qualification-stable.apk`, raw SHA-256
`3aa97275855b85dd2ccecda395bb5c2c0724c9ccd4713576c523c127c4f9a6e2`,
exactly the final G artifact bound to HEAD `824339de562c0de8542f4c1b2a22266832c8abef`.
Its identity is accepted; this is a defect in that build, not an unverified APK theory.

Before the blocker, the operator observed PASS for `adb install -r`/data continuity,
installation id `A44Utwpq`, Cloud device id `a5e45f36`, GRANTED/GRANTED,
historical restore after upgrade, new Cloud 14/14/14/14, visible replacement
15/15/15/15, normal visuals/one owner, pause/resume, backward/forward seek,
Prime eligibility loss hiding overlays and return restoring eligible comments.
These partial passes remain evidence of the first session, not new corrective passes.
Same-revision physical redelivery was **NOT RUN**: the existing UI has no supported
trigger and a new Send increments the revision. No button, product flow or API was added.

A genuine hard Sony reboot, with TV networking temporarily unavailable to isolate
local restoration from Cloud, failed on confirmed revision 15:

| Physical post-reboot observation | Value |
| --- | --- |
| App version | `0.8.2-tv-release-hardening` |
| Overlay service / Cloud | running / offline |
| Installation id / Cloud device id | preserved / preserved |
| Permissions | GRANTED / GRANTED |
| Installed package / handler | CORRUPT / unavailable |
| Installed / cached / acknowledged / assignment / successful-ACK diagnostic revisions | 0 / 0 / 0 / 0 / 0 |
| Last cloud error / Last startup restore | NONE / CACHE_FAILED |

Those zeros are bounded unavailable observations, not proof that stored data or ACK
was cleared. The log shows unlocked user before BOOT_PREPARE, BOOT_COMPLETED allowing
the FGS, BootReceiver dispatching ACTION_BOOT_PREPARE, live service/foreground notification,
then startup CACHE_FAILED, then MediaSession probe. There was no observed SceneVibe
crash. Sony DLNA/other system FATALs belong to other components and are not attributed
here. The original [G software report](m4-phase-g-generic-restore-diagnostics-report.md)
is byte-identical historical evidence; its original readiness verdict does not turn
this later physical FAIL into a PASS.

## Demonstrated software root cause

The generic codec writes a strict versioned logical string ending in LF. Successful
SharedPreferences `commit()` writes Android XML, but immediate backend readback reads
the in-process preference map. In the AOSP Android 12 indented XML writer:

- [XmlUtils.writeMapXml/writeValueXml](https://github.com/aosp-mirror/platform_frameworks_base/blob/android-12.0.0_r1/core/java/com/android/internal/util/XmlUtils.java)
  enables indentation and writes a preference String with serializer `text()`.
- [Xml.newFastSerializer](https://github.com/aosp-mirror/platform_frameworks_base/blob/android-12.0.0_r1/core/java/android/util/Xml.java)
  selects FastXmlSerializer.
- [FastXmlSerializer.text(String)/endTag](https://github.com/aosp-mirror/platform_frameworks_base/blob/android-12.0.0_r1/core/java/com/android/internal/util/FastXmlSerializer.java)
  escapes LF as an XML entity, but sets `mLineStart` from the original string's final LF.
  `endTag` consequently appends indentation before `</string>`. At map/string nesting,
  four spaces become **part of the stored String**. XML readback retains them.

Thus the memory map can read exact generic 15 and confirm ACK 15, while a new process
loads generic 15 plus four trailing spaces. The **present** generic marker correctly
remains authoritative; the unchanged strict codec rejects trailing content, giving
CORRUPT/CACHE_FAILED. Historical 13 and raw ACK 15 still exist, but no missing-marker
fallback or ACK-ahead hypothesis is needed to reproduce this failure.

A local disk-reload proof ran the actual backend/store and the exact AOSP serializer
(the local copy changes only its package and removes unavailable annotation imports;
it is not a committed production dependency). The identical positive invariant failed
before the fix and passed after:

| Actual local proof | Before correction | After correction |
| --- | --- | --- |
| Immediate store revision / ACK | 15 / 15, SNAPSHOT | 15 / 15, SNAPSHOT |
| Generic marker after XML reload | present | present |
| Logical encoded string after reload | original + four spaces | exact original |
| Fresh store state / historical residue / raw ACK | CORRUPT / 13 / 15 | SNAPSHOT / 13 / 15 |
| Positive durable-reload assertion / exit | FAIL / 1 | PASS / 0 |

This is not presented as a native process test. The new **real Android API 31** gate
independently executes the identical test-only fixture against the exact archived
pre-fix production tree `824339d…`, using real private SharedPreferences, actual handlers,
installer and service restore ports. In four manifested/legacy × confirmed/pending
controls, immediate installation succeeds, then fresh process reads a present generic
string with exactly four appended spaces, CORRUPT and startup CACHE_FAILED/no ARM.
The same fixture against the corrected tree reads exact bytes and restores ARMED.
All controls and corrected runs actually completed; downloaded results contain the
explicit baseline-corruption checkpoint and distinct seed/reload PIDs.

**Physical attribution limit:** no raw Sony preference XML was supplied, so the exact
bytes on that Sony are not claimed to have been forensically observed. The software
cause is demonstrated before/after on actual Android 12 and is consistent with the
physical symptoms. The new bounded diagnostic and the real Sony rerun must establish
its remaining on-device outcome. CI is not physical hard-reboot acceptance.

## Hypotheses and ownership audit

| Hypothesis | Deterministic conclusion / limit |
| --- | --- |
| Generic absent → historical 13 with ACK 15 | Not the reproduced cause: generic is present with exact XML-added padding. Absence with historical 13/ACK 15 remains CORRUPT in native/JVM tests. Actual Sony raw key presence was not supplied. |
| Generic invalid after disk reload | Demonstrated precisely: four extra spaces, strict decode rejection, no truncation or missing key required. |
| Locked credential-encrypted storage / Direct Boot | Unsupported by supplied unlocked-before-prepare log. Existing receiver/manifest remain unchanged; no LOCKED_BOOT_COMPLETED, directBootAware or permission change. |
| SceneVibe service/autostart crash | Contrary to supplied service/probe ordering and live service. No application crash evidence; unrelated Sony FATALs excluded. |
| Old owner clears/writes generic outside the store | Full access audit found one actual Android preference owner. The historical repository has no other production callers. Authorized writes flow through the synchronized store/backend; resets are explicit. |
| ACK-ahead tolerated, historical fallback, automatic cleanup | Rejected as a correction. All stay fail-closed and no residue/ACK is silently removed or rewritten. |
| False commit / process-local failed-view mask | Existing mask and single-batch recovery remain. Native unwritable-directory commit actually fails, installer refuses ARM/ACK, prior disk bytes survive fresh process and restore zero-write. |
| Package size / codec bounds | The unchanged 3,000,000-byte ceiling round-trips exact bytes through real Android XML/new processes on APIs 31 and 35. No lower ceiling or new storage system. |
| Arbitrary vendor I/O faults or power-loss atomicity | Not ruled out for unobserved Sony bytes; no emulator/process test claims power-loss or final device qualification. |

Cloud assignment still follows the unchanged v1 adapter → installer → synchronized
store/backend one-batch commit → logical readback → successful ARM/owner guard → server
ACK → separate exact revision ACK commit. Startup still reads the durable store and
uses the existing same-revision install/restore before probe/Cloud. Generic present
never consults stale historical authority. `cloud_track` is the exact same private file;
identity, credentials, installation id and pairing files remain separate. The preference
monitor, F owner gate, exact finalTrackId binding, revision rules and one visual owner
are unchanged. No new production preference writer, listener, service or process exists.

## Minimal production correction and strictness

Only four production files change:

| File | Responsibility and effects |
| --- | --- |
| `installation/AndroidInstallationBackend.java` | Generic writes use `scenevibe.os.android-preference.v1:` + unchanged logical codec string + `!`. The physical XML text now ends with a non-LF character. Exact known/bounded envelope reads return the original logical string. Historical/raw generic reads remain untouched. Same key/file/editor/commit/monitor; 34-character fixed overhead. |
| `installation/InstallationStore.java` | Adds a fixed observational ReadFailure to the same EMPTY/SNAPSHOT/CORRUPT result; discriminates decode, historical, ACK and backend read stages. Corrupt still means null snapshot/zero unavailable ACK. Backend causes/messages are discarded; VM errors still propagate. No changed commit, revision, ACK, restore or reset authority. |
| `RuntimeDiagnostics.java` | Projects the fixed enum from one durable read; immutable scalar metadata only, with unchanged compatibility aliases. No bytes, parser, exception, secret, URL or mutation. |
| `DiagnosticsActivity.java` | Adds `Installation read failure` enum text to the existing diagnostics renderer. No action, product recovery or reset change. |

The envelope rejects missing terminals, empty/oversized payloads and malformed content;
unknown/raw versions still meet the strict unchanged codec. No trim, trailing-space
acceptance, codec rewrite, destructive migration, background repair or reset exists.
Valid old raw generic snapshots remain readable without a write. Old **corrupted**
generic snapshots, including LF-plus-spaces, stay CORRUPT. Failed publication captures
and restages the **physical** prior envelope exactly once; later ACK/recovery cannot
nest its wrapper, publish a failed candidate or advance an unconfirmed ACK.

`PackageInstaller`, codec, static registry, exact handlers, artifact/revision semantics,
ACK separation, Cloud client/adapter/protocol/HTTP/wire/auth, owner rule, renderer,
scheduler/controller/seek/pause/replay, identity/credentials, package/version, manifest,
permissions/FGS, resources, build/configuration and signing chain are byte-exact to start.
Historical revision/runtime/manifest/ACK residue is retained. Restore has no mutation
and an ARMED result alone is still invisible. The new enum is never a business authority.

## Added tests and preserved boundaries

`M4SonyPreferenceTransportTest` adds 10 actual JVM cases for logical/physical envelope
boundaries, valid raw no-write compatibility, old padding rejection, malformed/type
faults, maximum bytes, failed commit/ACK restaging, explicit reset failure and VM errors.
`M4SonyReadFailureTest` adds nine real store/projection/renderer fault/read cases,
checking closed results, no content/exception leakage and no diagnostic mutations.
These preference substitutes **do not qualify Android XML or process death**.

The separate test-only Instrumentation uses platform APIs already in the project,
with no AndroidX dependency or production build/manifest change. A Gradle init script
configures only androidTest sources, frozen fixture assets and its test runner. The
shell runs seed then reload separated by complete target `am force-stop`, verifies
process absence and different PIDs; no Java object, shared preference map or static
failed view survives. Expectations/digests are stored in separate synced test files.

For each API, 11 completed native scenarios cover manifested/legacy generic 15 with
ACK 15 and ACK 13, both coherent historical 13/13 profiles, invalid generic with valid
residue, absent generic with historical 13/ACK 15, empty, maximum package and actual
failed disk commit. Correct generic cases verify exact revision/ACK/codec/handler,
all artifact bytes, unchanged historical residue and actual service same-revision
ARMED with no visibility. Every reload checks zero backend commits, ACK writes and
clears plus byte-identical actual XML before/after restore. Maximum is a storage-only
opaque package, not a claim of an unknown handler's ARM. Native failure injection
changes only the disposable fixture directory mode and restores it in `finally`.

The API 31 job also builds the exact old Git archive with these same test sources,
then performs four negative controls. The API 35 job intentionally does not duplicate
that old build; its conditional step is skipped, **not a skipped test or suppressed FAIL**.
Native totals: **22 corrective scenario PASS + four reproduced pre-fix corruption
controls = 26 scenarios / 52 instrumentation invocations**, with different process ids
within every seed/reload pair. They are not added to the Gradle JVM case count.

All retained Java tests, fixtures, original A–G inventories and historical reports
are unchanged. Earlier Python provenance checks receive only a shared, uniquely
reversible corrective view for full-blob/inverse comparisons. Semantic predicates,
compiler inputs and business tests continue to inspect/execute actual current sources.
The new 10-case boundary suite pins all 191 retained starting blobs, all four production
hunks and the finite boundary/accounting/protocol edits to exact start identities.
It admits no new production type or broader exception. The full executed suite map,
private SKIP policy and frozen hashes remain mandatory; the new summary bucket is additive.

## Actual software-head qualification

All run metadata independently identifies branch `work/scenevibe-os-m4-tv-installation-001`,
`head_sha=ba714e3d9b18376f6df7e426a49133ef0ab5c07b`, pull_request, completed/success, attempt 1.
Jobs, all qualification steps, logs and actual artifact metadata/downloaded bytes were inspected.

| Workflow | Run | Successful job(s) / performed result |
| --- | ---: | --- |
| [Debug / all retained qualifications](https://github.com/pierre22400/scenevibe-tv-companion/actions/runs/37311428285) | 37311428285 | 111767522307 — boundaries, full Gradle JVM, XML summary/frozen fixtures, lintDebug, assembleDebug, LAN DEV, Consumer Cloud origin, stable signing/cert verification/upload/discard all actually ran |
| [Android 15 / API 35 standard-image smoke](https://github.com/pierre22400/scenevibe-tv-companion/actions/runs/37311428316) | 37311428316 | 111767522457 — six performed PASS checks: Cloud APK build/install/package, crash-free launch, resumed MainActivity, package after force-stop |
| [Native disk/process durability, APIs 31/35](https://github.com/pierre22400/scenevibe-tv-companion/actions/runs/37311428439) | 37311428439 | 111767523210 (31), 111767522958 (35) — 11 corrected scenarios per API and four actual API 31 old-build controls |

The standard smoke remains a standard Android image, not Android TV. Its force-stop
package check alone is not a boot/durable-restore qualification. The new native gate
covers the missing disk/process lifecycle separately; neither claims Sony hard reboot.

| Executed bucket | PASS | FAIL | SKIP | Total |
| --- | ---: | ---: | ---: | ---: |
| Retained pre-A | 178 | 0 | 1 | 179 |
| Phase A / B / C / D / E / F / G | 30 / 39 / 43 / 83 / 115 / 138 / 131 | 0 | 0 | 579 |
| Corrective JVM | 19 | 0 | 0 | 19 |
| **Full Gradle JVM** | **776** | **0** | **1** | **777** |
| Retained Python through G | 76 | 0 | 0 | 76 |
| Corrective Python | 10 | 0 | 0 | 10 |
| **Full Python** | **86** | **0** | **0** | **86** |

The sole private assumption SKIP remains
`M1CloudInteropTest.originalColumboProjectionIsInstallable`, requiring
`SCENEVIBE_M1_SONY_FIXTURES`; it is never counted as PASS. Local full actual JUnit
execution independently matched 776/0/1 and Python 86/0/0. Start baseline was 757/0/1
and Python 76/0/0. Downloaded actual Gradle XML-derived JSON was validated against the
entire A–G + corrective suite map, all bucket totals, exact SKIP and frozen hashes.
Both downloaded native artifacts were independently re-summarized from every actual
seed/reload output, framework completion/checkpoint and PID; their JSON matched exactly.
Example actual software-head confirmed-15 PIDs: API 31 3130→3268; API 35 2431→2596.

The original G final debug attempt 1 had the already disclosed retained F owner-gate
scheduling sensitivity (InterruptedException vs bounded stopped-client CloudException).
No F test/client/gate/fixture changes, retries, suppression or new SKIP were needed in
this corrective software-head run; attempt 1 passed. The previous failure remains
historical evidence in the original closure/audit. During local baseline work, one
concurrent JVM compiler hit a JDK PerfMemory SIGBUS; sequential unchanged Python rerun
passed all 76 cases. It was a local tool incident, not an application crash. No test
expectation changed for it. One newly added Python source-boundary list initially
omitted its diagnostics renderer; it was corrected locally to include the actual
allowed observer. All retained assertions and final checks passed unchanged.

## Downloaded signed software candidate and frozen evidence

| Software-head artifact | ID / exact evidence |
| --- | --- |
| Gradle summary | 11345963278, `scenevibe-m4-phase-a-test-summary` |
| Stable Cloud APK | 11346316037, `scenevibe-tv-companion-cloud-qualification-stable` |
| API 31 native evidence | 11346665660, `scenevibe-m4-native-durability-api-31` |
| API 35 native evidence | 11346431038, `scenevibe-m4-native-durability-api-35` |
| Raw stable APK filename / size | `scenevibe-tv-companion-cloud-qualification-stable.apk` / 226048 bytes |
| Raw software APK SHA-256 | `c4b1ce2bc266e1a191dee97be8cde46cd96bcab02b94a6bdbf0ffab07846b135` |
| Actual stable ZIP SHA-256, matches artifact metadata | `b27de83c40d49cb2b62a93d0f4f526f2f5956933803676256805a1e38761e02d` |
| apksigner-verified signer certificate SHA-256 | `f908bf564ed97ba67e02b1ebc89eb0239cf980752587f55eb9ec0419791a2e9c` |
| Application id / versionCode / versionName | `com.scenevibe.tvcompanionpoc` / 12 / `0.8.2-tv-release-hardening` |
| minSdk / compileSdk / targetSdk | 26 / 35 / 35 |

Metadata binds all these artifacts to the software HEAD. The raw APK was independently
downloaded, unzipped and hashed, with the embedded certificate also independently
extracted and matching the expected fingerprint. Actual DEX contains the new transport,
fixed fault enum and diagnostics label; it excludes test-only instrumentation. ZIP hash,
raw APK hash and signer fingerprint are distinct evidence. The final-head candidate,
new artifact id and hashes are recorded separately in the final PR closure manifest.

| Retained fixture | Unchanged SHA-256 |
| --- | --- |
| Seven M1 Cloud envelopes | `19ee6fc3e2eba7009c9d345c33a63ca5617621fac9c8f5618cb24ee2932d6eab` |
| OverlayManifest 94-case corpus | `c78addf877b277e38f66c12274e4c8ed2ce12a503d883e27fd9950215cafbf30` |
| Historical manifested cache | `76d109c4b69f3e707912449f87e3fa6b9081e4564a12c77c633ae7ed7563beb1` |
| Historical legacy cache | `1676ed2033ec0e589509da7bdab3294ed8b5949153eeb2cfdb539aa23409cc8c` |

## Complete changed-file inventory and self-audit

The software commit changes 22 files; this report adds only its documentation child,
for 23 files from corrective start. All other retained blobs are pinned to start.

- `app/src/main/java/com/scenevibe/tvcompanionpoc/installation/AndroidInstallationBackend.java`
- `app/src/main/java/com/scenevibe/tvcompanionpoc/installation/InstallationStore.java`
- `app/src/main/java/com/scenevibe/tvcompanionpoc/RuntimeDiagnostics.java`
- `app/src/main/java/com/scenevibe/tvcompanionpoc/DiagnosticsActivity.java`
- `app/src/test/java/com/scenevibe/tvcompanionpoc/installation/M4SonyPreferenceTransportTest.java`
- `app/src/test/java/com/scenevibe/tvcompanionpoc/M4SonyReadFailureTest.java`
- `app/src/androidTest/java/com/scenevibe/tvcompanionpoc/M4SonyDurabilityInstrumentation.java`
- `.github/workflows/android-installation-durability.yml`
- `.github/scripts/m4-sony-durability.init.gradle`
- `.github/scripts/m4-sony-durability.sh`
- `.github/scripts/m4-sony-durability-summary.py`
- `.github/scripts/m4-phase-g-sony-corrective-baseline.json`
- `.github/scripts/m4-phase-a-test-summary.py`
- `tests/sony_corrective_provenance.py`
- `tests/test_m4_sony_corrective_boundary.py`
- `tests/test_m4_phase_b_boundary.py`
- `tests/test_m4_phase_c_boundary.py`
- `tests/test_m4_phase_d_boundary.py`
- `tests/test_m4_phase_e_boundary.py`
- `tests/test_m4_phase_f_boundary.py`
- `tests/test_m4_phase_g_boundary.py`
- `docs/m4-sony-physical-qualification-protocol.md`
- `docs/m4-phase-g-sony-hard-reboot-corrective-report.md`

The exact diff was audited for false durable success, physical/logical double wrapping,
weak validation/fallback, ACK/revision changes, destructive cleanup, multiple owners,
Cloud/protocol/identity/signing/platform drift, bounded diagnostic leaks and native
qualification inflation. Existing documentation/Javadocs/docstrings remain retained;
all new nontrivial methods/helpers explain responsibilities/invariants/effects, Python
has post-import teaching banners and every function has a docstring. No broad cleanup
or production refactor was required. The shell log checks use ubiquitous grep rather
than adding a runner dependency; new tests' checks were fixed before publication.

## Sony re-qualification to perform

Use the updated [20-check physical protocol](m4-sony-physical-qualification-protocol.md)
and the final-head stable artifact from the manifest. Upgrade with `adb install -r`,
first record unchanged identity/pairing/permissions **before any operator reset** and
capture the fixed read-failure enum on the existing cache. Already-corrupt bytes may
still yield GENERIC_INVALID/CACHE_FAILED and must not be treated as a correction PASS.
If that cache blocks installation, preserve evidence, then only the operator's existing
explicit Reset Cloud/re-pair flow may prepare a clean reference. Record intentional
identity rotation separately; it cannot count as upgrade continuity. No reset/re-pair
was performed in Work and no automatic recovery was added.

Send a new clean revision N and newer N+1 with exact confirmed ACKs, repeat the previous
visual/pause/seek/eligibility checks, isolate TV network, then perform a genuine full
Sony restart/power cycle. Require non-corrupt installed package, exact revision/ACK/
codec/handler, `Installation read failure: NONE`, startup ARMED and no old overlay
before an eligible due event. Preserve pre/post logs showing local restore before
probe/Cloud. Reboot CORRUPT/CACHE_FAILED or changed durable ACK/handler is FAIL; never
mask it with Send/reset/re-pair. Restore network afterwards and distinguish any Cloud
work from the offline local result. Keep same-revision redelivery NOT RUN if the
existing UI has no supported trigger; do not invent one. A separate physical evidence
audit decides final acceptance. Production remains SHADOW and the PR remains Draft.

**READY FOR M4 SONY RE-QUALIFICATION**
