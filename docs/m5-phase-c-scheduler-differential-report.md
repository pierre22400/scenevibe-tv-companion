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
separate. Test files are written exclusively in the disposable test package cache.

Finite provenance: C inverse returns exact accepted final B blobs; existing B,
pre-C corrective and Sony inverses then execute unchanged historical predicates.
No production inverse, wildcard, fixture change or behavioral relaxation is admitted.

Qualification pending. **NOT READY FOR M5 PHASE D** until the final HEAD passes all gates.
