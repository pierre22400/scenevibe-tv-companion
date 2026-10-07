# M6 Phase E — final qualification report

Date: 2026-10-07. Verdict: **M6 PHASE E: BLOCKED**.

The stop occurs at the Cloud qualification isolation / writer-drainage preflight,
before a live M6 Send. The Phase E runtime assembly, software qualification,
Sony candidate APK and physical qualification are **not complete**. Neither
`M6 PHASE E SOFTWARE GATE: PASS` nor `READY FOR SONY PHYSICAL QUALIFICATION`
is claimed.

## Exact starting authorities

| Repository / authority | HEAD | Tree |
| --- | --- | --- |
| Qualified TV Phase D, PR #16 | `d311c05ecd4c0b73c5e23afa6280e76230f4d0f3` | `a6d09b55da2982c59d3ff245be1ed47dc968b40d` |
| TV main / merged M5 | `17cbe36ae99ac7f48aaf861e0d1feac702a9e521` | unchanged |
| Qualified Cloud Phase D, PR #28 | `a16db39c2d09655b7a9c47aaaa2c6ee46a6f806c` | `d15381658dccda5192f69bd4f05af3be4c7406b5` |
| Cloud documentary main | `60c3c0b6ed116ca425d115f60aedd859b7eaa367` | `e7b773f339b28fa6aee5b4cf79a5bb878bc442cf` |
| Reconciled Cloud operational base | `c5a3c8a3aac06bfeea0a730d4a30603a3feb60c3` | `6a1f035496cf4bdbad41daa8deea696e6f5162b6` |

Cloud reconciliation preserves Phase D as first parent and documentary main as
second parent. Exactly the seven PR #27 documents enter. No executable, test,
workflow, dependency or SQL blob changes. On that exact reconciled HEAD,
[contracts / PostgreSQL CI](https://github.com/pierre22400/interface-scenevibe/actions/runs/37631371852)
and [Preview CI](https://github.com/pierre22400/interface-scenevibe/actions/runs/37631372131)
are SUCCESS: 1,426 tests PASS, four existing opt-in skips, full typecheck and builds.

## Reproducible blocker

The canonical Vercel project is `prj_yVUvNHVCNjx1kpJlA9ymPRd5i1Ka`, team
`team_U2fjwOYJ6t1W6hxsMiKY73fG`. Read-only environment metadata shows one
unscoped Preview `DATABASE_URL`, created and last updated at
`1790517879704`, with no `gitBranch` override and no custom environment.
No named, isolated M6 qualification database or writer-drainage attestation is
configured. Secret values were neither decrypted nor printed.

An observed pre-M6 Preview remains READY: deployment
`dpl_QVikQxs9dtcFQ1vzT6x8fdnPqcc4`, URL
`https://interface-scenevibe-3wxg-i4x9u7nv1-archc-ode.vercel.app`, source HEAD
`d9eabae89377bd3cb72e1b51524fd989fbccc473`.
Its exact `packages/cloud/src/postgres/repositories.ts` blob is
`355e0f1f51cbbc5753cf24db9bb59bba7a03f201`. The legacy `upsert` takes the
device lock, reads `tv_assignments`, allocates the legacy revision and writes
the mirror; it contains no `assertLegacyWritable` or M6 authority guard.
Replacing the newest deployment cannot change this immutable old binary.

This proves that safe drainage has **not been established**. It does not claim
a live old-writer Send or a production database collision was executed.
The direct old-Preview probe was blocked by Vercel Authentication; this is not
evidence of application-level M6 rejection. The currently reconciled Preview
responds HTTP 503 `CLOUD_UNAVAILABLE` to the generic package GET, as required by
the unchanged Phase D production factory.

Reproduce without writes: inspect Vercel deployment and environment metadata;
read the pinned old repository blob; compare its allocator to Phase D
`assertLegacyWritable`; GET the reconciled package route without credentials.
Do not perform an old-binary Send against the shared Preview database to prove
the risk.

## TV state and retained provenance

TV application, permissions, Gradle inputs, fixtures, Java tests and workflows
remain byte-identical to qualified Phase D. The Android constructor remains
`VIDEO_V1`; `supportsWallClockExecution()` remains false. The single existing
store, installer, poller, owner and M5 MEDIA scheduler are retained.

Only this report, a new finite Phase E documentation baseline and its Python
provenance gate are added. Two Phase D provenance files receive explicit
Phase E inverses and literal added-path integration. Historical JSON baselines
are not rewritten. Unknown paths, source mutations, pin changes and broken
inverses remain rejected. E reconstructs exact D before D -> C -> B -> M5 -> M4.

The report-containing HEAD and tree, final exact-head CI runs and actual artifact
IDs/digests are attested in the PR #16 description after those CI finish, avoiding
a circular claim that this report contains the SHA of its own commit. Green
documentation / retention CI does not constitute the Phase E software gate.

## Safe resumption prerequisites

Use a named, isolated PostgreSQL qualification environment whose writers all
understand the M6 marker, or provide complete drainage of every pre-M6 writer
that can use the chosen database. Preserve the Sony installation/device identity,
credential, revision, ACK, permissions and autostart preference. Inspect initial
parity before activation; `PARITY_REQUIRED`, `OS_MIRROR_STALE` or incoherent
bootstrap must stop without repair. No new credential or reset is a substitute
for that proof.

Once this prerequisite is established, resume the requested PACKAGE_V1 live
composition, truthful capability, real HTTP cross-repository golden, exact-head
Cloud/TV gates, stable-signed APK and operator protocol A -> J. These tasks are
pending, not software or Sony evidence.

## Stop state

Production remains **SHADOW**. TV PR #16 and Cloud PR #28 remain
**OPEN / DRAFT / unmerged**. No live M6 Send, public cutover, writer deletion,
database mutation, reset, re-pairing, post-M6 implementation or Sony PASS.
No Phase E APK is produced or renamed from a Phase D artifact. Upgrade and
physical initial state remain unobserved. Sony qualification must start only
after the Phase E software gate and exact candidate have been completed.
