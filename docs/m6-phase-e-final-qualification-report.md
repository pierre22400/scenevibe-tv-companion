# M6 Phase E — real initial parity and final software assembly

**INITIAL PARITY: PASS, blocker cleared by real operator evidence.** Phase E software and Sony verdicts are recorded on the final immutable pair in the PR attestation after its exact-head gates. No overall Phase E PASS is implied by initial parity.

The operator used Cloud `a2f1dfde55ddb17cc795963fe0fffc54112effee`, tree `5f28b12b837abe00b852000d48fb84ad281e238f`, isolated Neon `m6-qualification`, Sony `a5e45f36-95af-41ab-841b-da596482436d`. These observations are supplied by the operator; WORK did not independently query the real Sony rows.

| Actual operation | Verdict | Writes | Reviewed evidence digest |
| --- | --- | --- | --- |
| Prepared Video inspect | eligible-to-seal; transactionReadOnly=true | 0 | `547564ef30a56379ac24809938e8cba3f6631ae76852db6ff552b80a81ceedd4` |
| Existing prepared publication seal | sealed; transactionReadOnly=false | 1 | same prepared digest |
| New strict post-seal ACK inspect | eligible | 0 | `6213edbc17c5687da7ec32b9f66116e023920827d38f9ebf9080489f3730f97e` |
| Historical OS ACK reconciliation | reconciled | 1 | same post-seal digest |
| Final read-only strict inspect | already-parity | 0 | same post-seal digest |

Publication `692b49d9-73b9-4b03-95d4-911f57b6521e`, revision `15`, assigned_at `2026-10-05 10:36:03.103+00`, legacy acknowledged_at `2026-10-05 10:36:16.059+00` remain exact. Actual seal time is `2026-10-07 20:26:26.36194+00`; storage codec digest is `891bcea1b0d9059b8d48454bc6dd6f7623d7c6d33cbd4c5b0b6286329a21125f`. The final validator checks active owner/TV, binding, timestamps, source/adapter, byte-exact deterministic READY package and all mirrored historical ACK fields before returning already-parity. The unchanged ACK evidence digest is correct: the digest excludes the OS ACK fields it reconciles, permitting a zero-write no-op on the same reviewed history.

No new Send, ACK, publication, revision, credential, pairing, identity, FinalTrack or binding was created. The two authorized writes are complete and **must not be repeated**. Historical ACK parity does not prove PACKAGE_V1 delivery or physical Sony behavior. Exact attribution of the historical ACK request remains unproven; it is not a requirement for the strictly observed final parity.

The complete sanitized operator sequence is retained in Cloud `docs/m6-phase-e-real-qualification-state.json`.

## Final runtime assembly

The named TV build `SCENEVIBE_M6_QUALIFICATION=m6-qualification` requires an explicit HTTPS origin and LAN DEV off. It selects PACKAGE_V1 on the existing Cloud client, the composite Video/Banner runtime ports, one store and one installer. The installer is created after the WALL driver and common ports exist. Only that qualification descriptor advertises executable WALL; `TvCapabilities.current()` remains false. The actual common ports now explicitly invalidate the opposing activation during Video/Banner replacement. Historical MEDIA models/scheduler, WALL models/scheduler, permissions and signing inputs remain unchanged.

The Cloud runtime opt-in requires the exact mode `package-v1`, scope `m6-qualification`, platform `VERCEL_ENV=preview`, the reviewed M6 branch and its existing branch-scoped DATABASE_URL. Every assignment writer route uses the same runtime selector, including historical Video Send/ACK/status and generic package Send/GET/ACK. No missing/malformed/partial variable activates M6; production keeps the unchanged SHADOW factory. Production-grade pepper, HTTPS-origin, disabled unsafe-principal and pool TLS checks remain required. The qualification pool has no migration or automatic bootstrap.

Read-only Vercel metadata confirms DATABASE_URL entry `LFj7DYrs6ABojHsJ` is scoped only to the M6 Preview branch, created/updated `1791386551113`. The seven observed branch deployments after that override all derive from M6-aware source. Older deployments retain their own immutable environment snapshots. The operator's isolated Neon branch assertion is accepted; metadata alone does not map a hidden endpoint to a Neon branch. No database secret was retrieved or changed.

## Software gates and remaining proof

The automatic gates retain the complete provenance chain E assembly → historical E preflight → D → C → B → M5 → M4. A new finite assembly inventory carries exact before/after whole-file inverses and independent pins; no historical JSON baseline is rewritten. Current behavior is exercised by new common-port/upgrade tests, and the historical assertions operate on exactly reconstructed older bytes.

The TV HTTP peer exports the actual executed JVM classpath. It can run the real client/adapter/installer/common ports against real Cloud HTTP handlers and disposable PostgreSQL for Video N → Banner N+1 → Video N+2, with proof generated from durable readback after ARM. Its loopback bridge establishes HTTP byte/authority behavior; it does not assert Android TLS, real Sony lifecycle or visible rendering. The final cross-repository gate must consume this actual peer artifact, never replace the final server with a fixture.

Software PASS requires complete exact-head Cloud contracts/typecheck/build, TV provenance/JVM/lint/native API31/API35/MEDIA differential/API35 smoke, the actual cross-repository HTTP proof and usable deployed qualification HTTPS. APK production is deferred until these are all PASS. The final PR attestation records actual run/job/artifact IDs, counts, pair HEAD/tree and any remaining blocker; green earlier HEADs cannot substitute.

Production stays SHADOW. PR #28/#16 stay OPEN/DRAFT/unmerged. WORK has performed no real Sony database write or M6 Send. Sony observations, upgrade and physical A→J remain NOT EXECUTED.

---

### Software closure and gated candidate

The immutable Cloud `8a57dbdd2487eb4373af6f3cb50950347ed07181` / TV peer `96286b3980a34240159e8b95e1acacede11ec212` code pair passes: Cloud 1,529 tests, zero failures, four inherited opt-in skips, typecheck and all builds; TV all four workflows, full JVM/lint and native gates. The actual downloaded peer ZIP and Cloud JUnit/transcript ZIP were SHA-256 checked. Real TCP HTTP exercised Video 1 → Banner 2 → Video 3 through the actual TV client/adapter/common ports/store/ARM/proof and actual Cloud handlers/PostgreSQL, with exact ACKs and final Video mirror parity. These revisions belong only to disposable test fixtures, never the Sony.

The actual Preview at `https://interface-scenevibe-3wxg-ny22b1hcp-archc-ode.vercel.app` is READY on the exact Cloud HEAD above. A direct verified HTTPS GET of the M6 route without a device token returns `401 UNAUTHORIZED`, no authentication wall and no Send. This proves route/TLS/auth construction, not a new authenticated Sony read or physical observation.

[Software input gate](m6-phase-e-software-gate.json) pins the Cloud runs, downloaded artifacts and every app/src byte from the actual peer. The final TV candidate job refuses source drift and waits for debug/JVM/lint plus API31/API35 differential/durability and API35 smoke PASS on its own exact HEAD before building any Sony candidate. It also rechecks the immutable HTTPS route and OPEN/DRAFT/unmerged PR16. The named build alone uses version `0.8.3-m6-phase-e` / code 13; the historical build identity stays unchanged.

The candidate is non-debuggable, PACKAGE_V1, WALL true, LAN DEV false, exact Preview origin, stable signer `f908bf564ed97ba67e02b1ebc89eb0239cf980752587f55eb9ec0419791a2e9c`. Its actual HEAD/tree, APK hash, software gate and signer are emitted by the gated job in `scenevibe-os-m6-phase-e-sony`, not guessed in this source report. The final exact pair/run attestation is recorded on both PRs after that job completes.

**Physical Sony: NOT EXECUTED. STOP at the physical boundary.** The sole next operator action is to record the current Sony Diagnostics baseline before upgrading. [Strict later A–J protocol](m6-phase-e-sony-operator-protocol.md). No first M6 Send, new Sony ACK or revision is performed by WORK; the historical revision remains 15 on the operator-qualified DB.

# Historical preflight reports — superseded state, retained for audit

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
