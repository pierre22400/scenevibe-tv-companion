# SceneVibe OS M4 — final Sony physical closure

Date: 2026-10-05  
Repository: `pierre22400/scenevibe-tv-companion`  
Branch: `work/scenevibe-os-m4-tv-installation-001`  
PR: #14  
Corrective software/artifact HEAD physically qualified: `94b2ad47640151cf592c27e720cea4a8fbd8cd7f`  
BASE_TV_M4: `ecdf77bec9f93babf15239a63bf7f702fd7ca293`  
BASE_CLOUD_M4: `5011c91aac61a0cc6dcc74c256a15b7dee03d785`  
Production: **SHADOW**

## Decision

**M4 SONY PHYSICAL QUALIFICATION: PASS.**

This closure supersedes the earlier PARTIAL acceptance conclusion only for the remaining
physical Sony gate. It does not erase the original hard-reboot failure on the faulty
Phase G APK; that failure remains historical evidence. The corrective APK has now
passed the missing real-device persistence/startup/Unicode cycle.

No Phase H is created. No M5 work, production transition or Cloud change is claimed here.

## Exact corrective candidate

- Stable artifact id: **11346757578**
- Raw APK: `scenevibe-tv-companion-cloud-qualification-stable.apk`
- Raw APK SHA-256: `c4b1ce2bc266e1a191dee97be8cde46cd96bcab02b94a6bdbf0ffab07846b135`
- Archive SHA-256: `e6ac96d3febd615768fcdef99d14b6dc7972dff1a637db538644e7bea3480465`
- Signer certificate SHA-256: `f908bf564ed97ba67e02b1ebc89eb0239cf980752587f55eb9ec0419791a2e9c`
- Package: `com.scenevibe.tvcompanionpoc`
- versionCode / versionName: **12 / 0.8.2-tv-release-hardening**

## Final physical evidence

The final clean Sony cycle used revision **5** and the Unicode fixture:

`COMPOSÉ=é | DÉCOMPOSÉ=é | LIGATURE=œ | APOSTROPHE=’ | EMOJI=🙂`

The decomposed accent is U+0065 followed by U+0301.

Before reboot the clean package was delivered and acknowledged at revision 5. The
Unicode fixture was visually preserved through the qualified Prime Video path.

A genuine full Sony reboot was then performed with **no manual Send**. The successful
boot log establishes the local restoration sequence:

1. Android `BOOT_COMPLETED` starts SceneVibe in `BOOT_PREPARE`.
2. `OverlayService` is created.
3. SceneVibe logs
   `TRACK_LOADED trackId=finaltrack-7ec6402382a602cbbf498551 ... comments=5`.
4. SceneVibe immediately logs `Installation startup restore=ARMED`.
5. The passive MediaSession probe starts and reports `NO_ACTIVE_SESSIONS`.
6. SceneVibe logs `Overlay armed (no overlay shown)`.

This proves that the durable track was restored locally before an eligible Prime
MediaSession existed and that restore alone did not display a stale overlay.

Post-reboot Diagnostics recorded:

- Installed revision: **5**
- Acknowledged revision: **5**
- Last assignment revision: **0**
- Last successful ACK: **0**
- Installation read failure: **NONE**
- Last startup restore: **ARMED**

The zero process-local assignment/successful-ACK counters, together with durable
revision/ACK 5 and the boot ordering above, demonstrate that the restored package did
not depend on a new post-reboot Cloud delivery.

When Prime subsequently became eligible, the MediaSession identity was exactly
`amzn1.dv.gti.baecadcc-c3ef-42a3-bce0-c241adaa992e`. At approximately 32.535 s no
comment was due. At approximately 33.542 s SceneVibe emitted `COMMENT_DUE` for the
comment scheduled at 32.988 s. The physical observation matched this ordering: no
SceneVibe visual before the due event, then the expected Unicode comment appeared.
The comment expired normally after its 8 s window. Backward seek re-armed/replayed it
according to the existing policy.

The complete successful-cycle log was inspected for the corrective failure signatures.
No SceneVibe `CORRUPT`, `CACHE_FAILED` or `GENERIC_INVALID` result was found; no
SceneVibe fatal exception/crash or duplicate visual-owner symptom was identified.
Unrelated Sony/Android component errors are not attributed to SceneVibe.

## 20-check protocol reconciliation

The previously missing physical gates are now satisfied by this final cycle:
startup before first due event, hard-reboot local restoration ordering, exact durable
revision/ACK survival, read-failure NONE, startup ARMED, no stale visual before event,
post-reboot eligible rendering, complete Unicode matrix, and successful-cycle logcat
inspection.

Checks 10 and 18, same-revision redelivery, remain **NOT RUN by protocol design** because
the existing supported UI has no same-revision trigger and Send increments the revision.
The protocol explicitly requires NOT RUN rather than inventing a product button/API or
editing private state. These accepted NOT RUN entries are not product failures and do
not block this physical closure.

## Final disposition

- Corrective hard-reboot persistence defect: **PASS on real Sony**
- Final Sony Unicode + hard-reboot cycle: **PASS**
- Sony physical qualification: **PASS**
- M4 physical gate: **CLOSED**
- Production: **SHADOW**
- PR #14: **kept OPEN / DRAFT / unmerged by this evidence-freeze commit**
- Merge action: **not performed by this closure**
- Phase H: **does not exist**

The earlier PARTIAL audit remains useful historical evidence but is superseded by this
document for the final M4 physical acceptance decision.
