# M5 Phase C — generic scheduler and exact differential

Start HEAD: `46000bf11961572e0cc6003dc1b46094eeb9d846`.
BASE_TV_M5: `67b81045258b1692073c6927b956db4899c6ad1a`.
BASE_CLOUD_M5: `5011c91aac61a0cc6dcc74c256a15b7dee03d785`.

Production remains SHADOW. The two new production sources are NON-LIVE.
Every existing production source, all three B values, frozen sources, corpus,
expected traces, B journal/comparator and owner corrective remain byte-identical.
PR #15 remains OPEN / DRAFT / unmerged. No Phase D, physical Sony, merge or ready.

The candidate preserves immediate legacy mutation/callback order, exact thresholds
(lateness <=2000, forward >5000, backward <-2000), HashSet consumption and raw
HashMap expiration. One due per observation, zero/negative duration no window,
same-input due/expire, pause, recovery and old-binding invalidation are preserved.
The core is passive, silent, serialized and JDK-only. The external adapter copies
the unchanged matcher's result and probe-selected position; null stays a no-op.
Captured callback tokens are checked separately from unchanged B input-label metadata.

Local JVM: 82/82 real frozen/candidate traces equal, 78 deterministic goldens unchanged,
four raw HashMap pairs equal in the same VM. Additional 21/21: 11 direct pure scheduler,
4 adapter, 4 frozen sink-exception differentials with following inputs, 2 temporary
compiled source mutations rejected. These are not substitutes for final Android CI.

Android test-only harness shares the exact journal/projection/recording sources,
executes all 82 sequences against the byte-pinned unchanged legacy and real candidate
inside each API31/API35 VM, and exports all raw pairs. M4 native durability remains
separate. Test files are written exclusively in disposable target cache, outside preferences/installation state.

Finite provenance: C inverse returns exact accepted final B blobs; existing B,
pre-C corrective and Sony inverses then execute unchanged historical predicates.
No production inverse, wildcard, fixture change or behavioral relaxation is admitted.

Initial implementation checkpoint (historical): **NOT READY FOR M5 PHASE D** before qualification. The completed qualification and documentary sealing rule are below.

## Initial CI attempt — preserved

Commit `29cf388cd372833a2f6b43895cb24641cd2d5a03`:
- debug run `37432516203`, job `112166457122`: Python108 PASS, then
  compileDebugUnitTestJavaWithJavac FAIL in the newly added negative test only.
  The AGP compilation image lacks Files.readString/writeString; use the existing
  supported readAllBytes/write APIs with explicit UTF-8, no semantic test change.
- new Android differential run `37432516268`: both API jobs FAIL at instrumentation.
  API31 artifact `11397417304` contained only result=FAIL, no trace. No differential
  acceptance is inferred. A bounded stage/exception diagnostic is added to the
  test runner; export now uses the target process cache and matching run-as UID.
- smoke run `37432516214`: SUCCESS.

Only new C test/instrumentation files are adjusted. Existing production, legacy,
matcher, probe, controller, goldens and historical test predicates stay unchanged.
These failed results remain distinct from all subsequent qualification.

## Public boundary

| API | Contract |
| --- | --- |
| `MediaCalendarScheduler(Sink)` | synchronous sink, no task or clock |
| `synchronized load(MediaCalendar, String)` | emit old-token INELIGIBLE, then replace/reset |
| `synchronized clear()` | old-token INELIGIBLE, then remove binding/reset |
| `synchronized onUnavailable()` | lose eligibility, clear windows, preserve consumed |
| `synchronized onObservation(MediaObservation)` | null no-op; exact legacy temporal branch order |
| `Sink.onEligibility(String, boolean)` | producing activation token and eligibility |
| `Sink.onPlayback(String, boolean, boolean)` | token, playing, freeze policy; repetitions retained |
| `Sink.onDue(String, String)` | token and exact consumed/windowed event ID |
| `Sink.onExpire(String, String)` | token and exact already-removed event ID |
| package-private `VideoMediaObservationAdapter.observe(ScheduledTrack, Snapshot)` | null or immutable observation; unchanged matcher; estimated>=0 else raw; PLAYING only |

No token participates in installation revision, ACK, stale or same-revision decisions.
Token tests cover nullable bindings, A-to-B load, clear, and reentrant callbacks.
The callback of an enclosing A transition remains labelled A after a nested load B.
No generation binding, delayed dispatcher or controller integration is introduced.

## Negative sensitivity and failure boundaries

Each control reads the actual candidate source, changes exactly one unique context
in a new host temporary directory, compiles only that scheduler with the JDK,
and isolates scheduler/Sink classes while sharing the unmodified value classes.
The genuine candidate must first equal the frozen oracle on the same fixture.
The compiled mutant must then fail the unchanged raw comparator.

| Mutation | Frozen B fixture | Required observed result |
| --- | --- | --- |
| `deltaMs > FORWARD_SEEK_THRESHOLD_MS` to `>=` | `forward-5000-state-3` | comparator rejects mutant |
| remove `return` immediately following `onDue` | `stable-equal-starts` | comparator rejects multiple-DUE mutant |

Four separate parameterized tests throw a precise test-only RuntimeException once
at ELIGIBLE, PLAYBACK, DUE or EXPIRE in both the isolated frozen legacy and real
candidate. Subsequent repeated positions expose already-mutated eligibility, anchor,
consumed IDs and partially drained windows. Exact trace equality is asserted;
production never catches or retries the exception. Two equal-start positive windows
exercise the remove-before-expire frontier.

## Finite source inventory

| Path | Change |
| --- | --- |
| `.github/scripts/m4-phase-a-test-summary.py` | finite path admission / actual executed accounting |
| `.github/scripts/m5-media-differential-summary.py` | new test/provenance/report |
| `.github/scripts/m5-media-differential.init.gradle` | new test/provenance/report |
| `.github/scripts/m5-media-differential.sh` | new test/provenance/report |
| `.github/scripts/m5-phase-c-baseline.json` | new test/provenance/report |
| `.github/workflows/android-media-differential.yml` | new test/provenance/report |
| `app/src/androidTest/java/com/scenevibe/tvcompanionpoc/M5MediaDifferentialInstrumentation.java` | new test/provenance/report |
| `app/src/main/java/com/scenevibe/tvcompanionpoc/VideoMediaObservationAdapter.java` | new NON-LIVE production |
| `app/src/main/java/com/scenevibe/tvcompanionpoc/calendar/MediaCalendarScheduler.java` | new NON-LIVE production |
| `app/src/test/java/com/scenevibe/tvcompanionpoc/M5CDifferentialHarness.java` | new test/provenance/report |
| `app/src/test/java/com/scenevibe/tvcompanionpoc/M5CandidateDifferentialTest.java` | new test/provenance/report |
| `app/src/test/java/com/scenevibe/tvcompanionpoc/M5NegativeSensitivityTest.java` | new test/provenance/report |
| `app/src/test/java/com/scenevibe/tvcompanionpoc/M5SchedulerDirectTest.java` | new test/provenance/report |
| `app/src/test/java/com/scenevibe/tvcompanionpoc/M5SinkFailureDifferentialTest.java` | new test/provenance/report |
| `app/src/test/java/com/scenevibe/tvcompanionpoc/M5VideoObservationAdapterTest.java` | new test/provenance/report |
| `docs/m5-phase-c-scheduler-differential-report.md` | new test/provenance/report |
| `tests/m5_phase_b_provenance.py` | finite path admission / actual executed accounting |
| `tests/m5_phase_c_provenance.py` | new test/provenance/report |
| `tests/test_m4_phase_b_boundary.py` | finite path admission / actual executed accounting |
| `tests/test_m4_phase_c_boundary.py` | finite path admission / actual executed accounting |
| `tests/test_m4_phase_d_boundary.py` | finite path admission / actual executed accounting |
| `tests/test_m4_phase_e_boundary.py` | finite path admission / actual executed accounting |
| `tests/test_m4_phase_f_boundary.py` | finite path admission / actual executed accounting |
| `tests/test_m4_phase_g_boundary.py` | finite path admission / actual executed accounting |
| `tests/test_m4_sony_corrective_boundary.py` | finite path admission / actual executed accounting |
| `tests/test_m5_phase_b_boundary.py` | finite path admission / actual executed accounting |
| `tests/test_m5_phase_c_boundary.py` | new test/provenance/report |

All 252 start-tree blobs remain pinned. Ten existing files have uniquely reversible
path-admission/accounting patches; there is no production, fixture, baseline-B or
owner-gate inverse. Both the extended retained B compilation gate and the independent
C gate compile all four core classes plus nested Sink with empty classpath/sourcepath.

**NO PRODUCTION CUTOVER**. **MEDIA SYNCHED TRACK SCHEDULER STILL LIVE AUTHORITY**.
**NO WALL**. **NO CLOUD/DURABLE/ACK CHANGE**.
**NO SONY PHYSICAL TEST REQUIRED/CLAIMED**. Phase D is not started.

## Qualified code commit

`1f2710aae516f0b758b69498bf7ae9fe321ba52d` retains the production additions
byte-for-byte from `29cf388cd372833a2f6b43895cb24641cd2d5a03`.

| Gate | Executed result | Run / artifact |
| --- | --- | --- |
| Python complete | 108 PASS / 0 FAIL / 0 SKIP | debug 37433131411 |
| JVM complete | 1006 PASS / 0 FAIL / 1 historical SKIP; 1007 total | summary 11397972145, downloaded/read |
| M5 B retained | 127 PASS / 0 FAIL / 0 SKIP | executed summary |
| M5 C added | 103 PASS / 0 FAIL / 0 SKIP | 82 differential + 11 direct + 4 adapter + 4 sink failures + 2 mutants |
| JDK-only core | PASS in both B-extended and C gates; four classes plus Sink | actual empty-classpath/sourcepath compilation |
| assembleDebug / lintDebug / testDebugUnitTest | SUCCESS | debug 37433131411, job 112168454441 |
| LAN DEV compile / Cloud qualification compile | SUCCESS / SUCCESS | same debug run, executed steps |
| Stable signing / apksigner verify | SUCCESS | stable artifact 11397997143, downloaded/read |
| API35 platform smoke | SUCCESS | 37433131326, job 112168453943; standard Android, not Android TV |
| Android C API31 | 82 compared / four raw HashMap / zero divergence | 37433131431, job 112168454467, artifact 11397314330 |
| Android C API35 | 82 compared / four raw HashMap / zero divergence | same run, job 112168454082, artifact 11397458135 |
| M4 native API35 | 11 corrected scenarios PASS / 22 distinct-process invocations | 37433131351, artifact 11397672794 downloaded/read |
| M4 native API31 initial attempt | FAIL at process-disappearance check after successful seed | job 112168454321, artifact 11397618076 downloaded/read |

JVM environment: OpenJDK 64-Bit Server VM 17.0.20.1. Both native differential
environments: Dalvik 2.1.0, API31 and API35. The native validator was reexecuted
locally on both downloaded raw artifacts. The four environment-dependent cases are
`hashmap-collision-pair`, `hashmap-collision-tree-and-resize`,
`hashmap-unique-key-resize`, `multiple-active-windows`. All four oracle/candidate
raw pairs are equal in each VM; no cross-platform canonical order is asserted.
The only JVM skip is `M1CloudInteropTest.originalColumboProjectionIsInstallable`.

Stable signer SHA-256, verified by CI apksigner and the downloaded APK certificate:
`f908bf564ed97ba67e02b1ebc89eb0239cf980752587f55eb9ec0419791a2e9c`.
Code-commit APK: 230202 bytes; SHA-256
`5da1fa79eb6a1a7e03610b017e6c618cbd8c4d29c72cbf7459447c157d72b37b`.

The API31 native failure is not an owner-gate failure: all corrected owner JVM
cases passed. The raw seed succeeded with checkpoint COMPLETE and PID3203,
but the shell's immediate post-force-stop pidof check found the process. Four
pre-fix controls had passed. No reload was accepted for the first fixed scenario,
and no 11-scenario API31 PASS is inferred from that failed attempt. One unchanged
job retry was requested; the original gate, timeouts and predicates stay exact.
The previous code commit's native API31/API35 workflow 37432516217 succeeded,
but that historical success cannot replace this code commit's qualification.

## Non-scheduler fixture accounting

The ten unchanged B model-rejection fixtures remain executed in B (not eligible
for scheduler construction); they are not hidden differential skips:

- `duplicate-exact-id`
- `empty-calendar`
- `257-event-calendar`
- `id-129-ascii`
- `id-130-utf16-emoji`
- `id-empty`
- `id-null`
- `start-negative`
- `start-over-max`
- `duration-over-max`

Every other frozen case is one of the 82 scheduler comparisons on JVM and on each Android API.

## Completed native retry and closure

One unchanged retry succeeded: native run `37433131351`, API31 job
`112169984164`, artifact `11398485692` downloaded and read. The unchanged native
summary validator was reexecuted on its 30 raw seed/reload invocations: 11 corrected
scenarios PASS, four historical pre-fix corruption controls PASS, distinct PIDs,
exact disk and zero restore writes. API35 remains 11 corrected PASS / 22 invocations.
This does not diagnose the exact emulator cause of the preserved first force-stop
failure, and no gate timeout, process check or assertion was changed.

All four workflows for code HEAD `1f2710aae516f0b758b69498bf7ae9fe321ba52d`
are SUCCESS: debug `37433131411`, smoke `37433131326`, M4 native
`37433131351`, M5 differential `37433131431`.

| C commit | Purpose |
| --- | --- |
| `29cf388cd372833a2f6b43895cb24641cd2d5a03` | scheduler/adapter non-live, differential, direct/failure/token/mutation tests, finite provenance, Android runner |
| `1f2710aae516f0b758b69498bf7ae9fe321ba52d` | test-only AGP byte IO, native export/diagnostic, explicit test inventory and preserved failures |
| final documentary commit, exact SHA sealed in PR #15 | this completed report only; every production/test/workflow/baseline byte remains identical to the qualified code HEAD |

The report cannot embed the SHA of its own producing commit. The exact final HEAD,
its parent, tree, four freshly rerun workflow IDs, job IDs, artifacts and results
are sealed externally in the [PR #15 manifest](https://github.com/pierre22400/scenevibe-tv-companion/pull/15).
The final verdict below takes effect only when that manifest confirms all gates on
the documentary HEAD; code-commit success alone never substitutes for final-HEAD CI.

**READY FOR M5 PHASE D** — subject to the exact final documentary seal above.

Recommendation after that seal, without starting it:

**M5 PHASE D — VIDEO CUTOVER TO GENERIC MEDIA CORE + SINGLE LIVE TEMPORAL AUTHORITY + CONTROLLER EVENT-ID / GENERATION BINDING + TARGETED SONY PHYSICAL QUALIFICATION**

PR #15 remains OPEN / DRAFT / unmerged. Production SHADOW.
NO PRODUCTION CUTOVER. MEDIA SYNCHED TRACK SCHEDULER STILL LIVE AUTHORITY.
NO WALL. NO CLOUD/DURABLE/ACK CHANGE. NO SONY PHYSICAL TEST REQUIRED/CLAIMED.
Phase D not started. STOP.
