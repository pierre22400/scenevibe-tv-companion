# M5 Phase D — Video cutover

Starting HEAD: f99f662b4dd1c2e2285a1272b6abced9e89862b0.
Production: SHADOW. PR #15 remains OPEN / DRAFT / unmerged.

AUTOMATED QUALIFICATION PENDING. PHYSICAL SONY PENDING.

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

Final evidence and deliverable metadata are recorded below after execution.
