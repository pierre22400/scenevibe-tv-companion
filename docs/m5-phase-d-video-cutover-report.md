# M5 Phase D — Video cutover

Starting HEAD: f99f662b4dd1c2e2285a1272b6abced9e89862b0.
Production: SHADOW. PR #15 remains OPEN / DRAFT / unmerged.

AUTOMATED QUALIFICATION PASS. PHYSICAL SONY PENDING.

The sole live service-owned MediaCalendarScheduler replaces the historical scheduler.
VideoPreparedState projects already parsed temporal values once and retains an immutable
payload index outside the core. Binding canonical preparation reuses both object identities.
Pending activation becomes active only at final revision selection. A fresh local monotonic
token identifies each activation; manifested ARM captures the controller generation once.
Callbacks never read currentGeneration to retag. Synchronous old-binding eligibility
invalidation during load/clear preserves diagnostic-before-controller/native ordering.
All other inactive, pending and stale callbacks are ignored before native mutation.
Abort attempts every cleanup, clears every binding and selects revision zero.

The LAN development parser's optional bitmap payload remains outside generic text-only
Cloud ingress through a dedicated memory-only prepared projection and revision-free LAN
selection. DiagnosticsActivity is the only additional production file: it exposes the fixed
observational label `Temporal engine: scenevibe.media-calendar.v1` without changing gates.
Cloud, store, snapshot, codecs, handler IDs, historical scheduler and C scheduler/adapter
semantics remain byte-identical. Restore completes before probe and Cloud polling.

D provenance uniquely reverses finite hunks to complete C blobs before the existing C→B→M4
chain. Historical B/C non-live assertions inspect those reconstructed stage bytes; actual D
structural and executable tests require one live calendar engine and no live old scheduler.
M4 F runtime tests now execute the actual router. Existing controller tests change only
Event→ID API arguments; their business assertions and counts are retained. Native durability
fixtures reflect the real six-argument M4 or eight-argument D adapter API, so the same
baseline/corrected storage assertions execute against both APKs without copied persistence.

Local initial retained JVM: 1006 PASS / 0 FAIL / 1 historical SKIP. First new D fixture
run rejected a hardcoded test device ID (40 fixture setup failures); corrected by using the
existing envelope's actual deviceId. No production Cloud rule or owner assertion changed.

Downloaded evidence and operator binding are recorded below.

## Software qualification and exact evidence

**AUTOMATED QUALIFICATION PASS — PHYSICAL SONY PENDING.**
No physical Sony/Prime playback, upgrade or reboot was executed in this WORK.

Software implementation commit: `5b8635b1a481a69146763a2abdc1a0b8b87c2f7c`.
Tree: `01969a277bf48914e6b5646ca512ea438b6fc5fe`.
The subsequent closure changes only the two new D documents and strengthens the new D suite's
stale/pending PLAYBACK checks to inspect actual controller flags in addition to native traces.
Production and every retained test remain byte-identical to the software commit. The final branch HEAD,
its four rerun workflows and final artifact IDs are recorded in the
[PR #15 Phase D delivery manifest](https://github.com/pierre22400/scenevibe-tv-companion/pull/15).
Delivery requires those final checks to pass and the final APK to match the binary digest below.
This distinguishes the reproducible implementation evidence from the later documentation HEAD.

| Executed gate | Evidence at software commit | Result |
| --- | --- | --- |
| Python, including pure empty-classpath JDK compile | debug job `112186383636` | 118 PASS, 0 FAIL, 0 SKIP |
| JVM complete inventory | summary artifact `11399882946` | 1050 PASS, 0 FAIL, 1 historical SKIP, 1051 total |
| M5 B / C / D buckets | actual Gradle JUnit XML summary | 127 / 103 / 44 executed, all PASS |
| Frozen oracle/corpus/negative sensitivity | retained B/C suites and whole-blob gates | unchanged; 82 pairs, 4 raw HashMap cases, 0 divergences |
| assembleDebug, lintDebug, testDebugUnitTest | [debug run 37438574056](https://github.com/pierre22400/scenevibe-tv-companion/actions/runs/37438574056) | SUCCESS, first attempt |
| LAN DEV, Cloud mode, stable signing | same debug run, successful individual steps | SUCCESS |
| Android API 35 platform smoke | [run 37438574107](https://github.com/pierre22400/scenevibe-tv-companion/actions/runs/37438574107), job `112186384099` | SUCCESS, first attempt; standard Android image, not Android TV |
| Native differential API 31 / 35 | [run 37438574446](https://github.com/pierre22400/scenevibe-tv-companion/actions/runs/37438574446), jobs `112186385203` / `112186385075` | SUCCESS, first attempt; each 82 / 4 raw HashMap / 0 divergences |
| Native disk/process durability API 31 / 35 | [run 37438574168](https://github.com/pierre22400/scenevibe-tv-companion/actions/runs/37438574168), jobs `112186384396` / `112186384194` | SUCCESS, first attempt |

The sole retained skip is `M1CloudInteropTest.originalColumboProjectionIsInstallable`,
the existing opt-in historical integration case. No new case is ignored or skipped.
The 44 D cases are 22 invariants executed for each real manifested/legacy handler shape.
Retained M4 F owner/Cloud/ARM failure suites also execute the new actual router, preserving
all durability-before-ARM-before-ACK, cancellation, lifetime, same/stale revision and abort assertions.

Native API 31: 11 corrected scenarios plus 4 pinned pre-fix corruption controls,
30 seed/reload invocations, each pair in distinct OS processes. Native API 35:
11 corrected scenarios, 22 distinct-process invocations. Each raw text inventory was
redownloaded and revalidated by the unchanged native validator. Generic manifested/legacy
confirmed/pending snapshots, historical residue, empty/corrupt/boundary/failed-commit cases
retain the exact artifact bytes, codec/handler IDs, ACKs and zero restore writes.
The API 31 negative control remains the Git archive of
`824339de562c0de8542f4c1b2a22266832c8abef`; it reproduces the pre-fix XML corruption.

| Downloaded raw artifact | ID | ZIP SHA-256 verified against GitHub digest |
| --- | --- | --- |
| Executed JVM summary | `11399882946` | `a519d4d60da67502321127bee23cc13ae666fc9eb58cb421d27b2af81df85fb9` |
| Differential API 31 | `11400920313` | `71ebcc8869e97397f6426dacebfe50655b209ed098f94d5678816557b8433dc3` |
| Differential API 35 | `11399943302` | `c6a71f51c195fdd9aad03155ed06f3ff183f941d9f8091934c52ccdcebf575ed` |
| Durability API 31 | `11400940470` | `9035f859fd7db30f6a2b5ef8def9f4488a92959e2c8e594d8fdf13fe1d5e32dd` |
| Durability API 35 | `11399883175` | `f5aef85d01c1440de45c31ade19b5ebd64723fde78d9403e1462e0ffe9ed97d8` |

Both differential Android environments are `Dalvik 2.1.0`. Their four environment-dependent
HashMap cases retain the raw callback order from that same VM; nothing is sorted or normalized.
The API 35 smoke proves install/package/activity/no-fatal platform invariants only.
It does not prove Sony reboot, notification access, native TV windows or Prime visibility.

## APK operator binding

Qualification artifact at the software commit: `11399967597`,
`scenevibe-tv-companion-cloud-qualification-stable`, raw ZIP member and delivered filename:
`scenevibe-tv-companion-cloud-qualification-stable.apk` (234298 bytes).
Its verified archive SHA-256 is
`4f5e300698ea8adb4f83c59aa2c2539fd35acede2870a9e386763b9660336331`.
Final-HEAD artifact identity is the PR delivery manifest; the operator uses the exact same APK digest:

`43a9bb12ecc7712afdcf7ed5486d4eff1c1be1767308c8832d919ac0dca817d2`

CI `apksigner verify --print-certs` succeeded. The signer certificate decoded independently
from the downloaded APK has SHA-256:

`f908bf564ed97ba67e02b1ebc89eb0239cf980752587f55eb9ec0419791a2e9c`

Actual compiled AndroidManifest.xml: package `com.scenevibe.tvcompanionpoc`, versionCode `12`,
versionName `0.8.2-tv-release-hardening`, minSdk `26`, targetSdk `35`, compileSdk `35`.
These values come from the APK, not an inferred build filename. The build config matches them.
This is an upgrade of the existing signed app: `adb install -r`, no uninstall, pm clear,
Cloud reset, identity rotation or new pairing.

## Final audit and scope

Five existing production files change. All C core/value/adapter bytes, old scheduler,
identity matcher, probe, renderers/countdown, CloudControlClient/wire/ACK, registry/handler IDs,
store/backend/snapshot/transport and build/signing inputs remain exact starting blobs.
`CloudTrackRepository` remains the isolated historical characterization definition, with
no live production caller (the retained M4 G gate checks this). It creates no scheduler.
There is zero production instantiation of the historical scheduler and exactly one
production `new MediaCalendarScheduler`, owned by OverlayService. No renderer owns another.
No feature flag, wall clock, shadow scheduler, dynamic production API or player control is added.

The prepared state's immutable calendar/index are shared by canonical binding, never encoded.
`loadPreparedVideo` stores a fresh pending activation, loads its exact calendar and emits only
the qualified core's old-binding eligibility invalidation. `armPreparedManifest` replaces the
manifest then captures generation once. `selectActiveRevision` alone promotes pending after
all checks. Callback paths use `binding.generation`, never `currentGeneration()`.
Tokens are local monotonic activations and do not depend on revisions, ACKs or identity.
Same-revision restore therefore receives a different token without a durable write.

Retirement invalidates active/pending before native removal can reenter. Aborting tries
controller unload, scene removal, legacy removal and core clear even when native cleanup throws.
If an eligibility callback interrupts the qualified core's clear before its state reset,
the adapter retries clear with callbacks already invalidated; the C algorithm remains untouched.
Both tokens and captured generations are discarded, selection becomes zero, and injected stale
callbacks cannot show, hide or update B. Tests inspect the actual cleared generic calendar too.

Manifested DUE/EXPIRE carry only ID and captured generation into the clock-free regie.
All original null/unknown/stale/eligibility/preflight/hide-before-show/one-visible/matching-expiry
business assertions remain. Legacy DUE retrieves the original exact payload object, and
PLAYBACK repetitions reach its unchanged freeze-aware renderer. Legacy EXPIRE remains ignored.
Eligibility mirrors diagnostics first, then controller, then native removal; recovery alone
forces no visibility. Unavailable records the existing bounded code before the distinct core entry.

Startup retains the exact M4 restore method and same installer authority, before probe/Cloud.
BOOT_PREPARE and all consumer entries remain ARM-only. The Sony protocol targets only these
changed runtime surfaces and continuity; no artificial legacy route or redelivery is invented.

## Development failures retained

The first D-only local run had 40 setup refusals because the new fixture supplied a hardcoded
Cloud deviceId inconsistent with its existing envelope. The fixture now uses that envelope's
actual deviceId; production validation and all assertions were retained. Early Python staging
also caught the not-yet-created D gate file and two existing literal wiring checks. The D file
was created, and the service kept the original playback spelling and explicit false eligibility
forwarding; no old gate was relaxed to accept a missing renderer route. Final local and CI runs
are green. All four software-commit workflows passed on their first attempts; no D CI job retry
or exception-acceptance expansion was used. B/C historical qualification failures remain in
[the unchanged B report](m5-phase-b-models-oracle-report.md) and
[the unchanged C report](m5-phase-c-scheduler-differential-report.md).

Targeted physical protocol: [m5-sony-targeted-qualification-protocol.md](m5-sony-targeted-qualification-protocol.md).
Final delivery verdict after final-HEAD automated checks: **READY FOR M5 SONY PHYSICAL QUALIFICATION**.
PR #15 remains OPEN / DRAFT / unmerged. Production remains SHADOW. STOP before physical execution.
