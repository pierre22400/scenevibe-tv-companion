# SceneVibe OS M5 — final Sony physical closure

Date: 2026-10-06  
Repository: `pierre22400/scenevibe-tv-companion`  
Branch: `work/scenevibe-os-m5-scene-event-calendar-001`  
PR: #15  
Software HEAD physically qualified: `c9b0efd4acfaaae9ed7da13dcec505b2f653c548`  
BASE_TV_M5: `67b81045258b1692073c6927b956db4899c6ad1a`  
BASE_CLOUD_M5: `5011c91aac61a0cc6dcc74c256a15b7dee03d785`  
Production: **SHADOW**

## Decision

**M5 SONY PHYSICAL QUALIFICATION: PASS.**

The targeted real-device qualification defined by
`docs/m5-sony-targeted-qualification-protocol.md` has been executed on the Sony TV.
This closure records only the physical acceptance evidence. It does not alter the M5
software qualification history, does not erase prior failures, and does not perform a
production transition or merge.

The physically qualified software candidate remains the exact pre-closure software HEAD
`c9b0efd4acfaaae9ed7da13dcec505b2f653c548`. This closure commit is documentation-only.

## Exact candidate and delivery identity

The Sony qualification used the stable APK bound by the Phase D delivery manifest:

- Artifact ID: **11399919826**
- Raw APK: `scenevibe-tv-companion-cloud-qualification-stable.apk`
- Raw APK SHA-256: `43a9bb12ecc7712afdcf7ed5486d4eff1c1be1767308c8832d919ac0dca817d2`
- Signer certificate SHA-256:
  `f908bf564ed97ba67e02b1ebc89eb0239cf980752587f55eb9ec0419791a2e9c`
- Package: `com.scenevibe.tvcompanionpoc`
- versionCode / versionName: **12 / 0.8.2-tv-release-hardening**
- Upgrade mode: `adb install -r`; no uninstall, `pm clear`, Cloud reset, identity
  rotation or re-pairing.

## Physical evidence

### Upgrade continuity

The upgrade completed successfully without loss of SceneVibe identity or durable data.
Diagnostics exposed the expected temporal authority:

`scenevibe.media-calendar.v1`

The previously installed Video package was restored and executed without a new Send.

### Revision 5 execution

Revision **5** was restored and executed through the new MediaCalendar path.

The existing Unicode fixture rendered correctly:

`COMPOSÉ=é | DÉCOMPOSÉ=é | LIGATURE=œ | APOSTROPHE=’ | EMOJI=🙂`

Observed playback behavior was nominal:

- pause while the overlay was visible kept the overlay visible;
- resume allowed normal expiry;
- backward seek re-armed the event and allowed it to render again at the next due passage;
- forward seek did not produce a late render of an already skipped event;
- loss of Prime eligibility removed the SceneVibe visual;
- returning to eligibility did not resurrect a stale visual.

### Normal Cloud replacement 5 -> 6

A new valid package was delivered through the supported Cloud path.

The replacement completed normally from revision **5** to revision **6**. Diagnostics
reached **6 / 6 / 6 / 6** for the revision/ACK delivery indicators observed during the
live replacement, with:

- `Installation read failure: NONE`
- `Cloud error: NONE`

No double scene or stale revision visual was observed during the replacement.

### Full Sony reboot and durable restore

A genuine full Sony reboot was then performed with **no new Send**.

After reboot, revision **6** was still present and usable. Diagnostics reported:

- `Installation read failure: NONE`
- `Last startup restore: -`
- `Last assignment revision: 0`
- `Last successful ACK: 0`

The literal `-` value is preserved here exactly as observed. It is **not** rewritten as
`ARMED`.

This diagnostic particularity did not correspond to a functional restore failure:
revision 6 survived the reboot, no new Cloud delivery was required, no overlay appeared
unexpectedly after boot, and the expected revision-6 event subsequently rendered in Prime.

The zero process-local assignment and successful-ACK counters are consistent with the
absence of a new post-reboot Send. They are not used here to manufacture an internal restore
state that was not directly observed.

## Logcat reconciliation

The final filtered logcat did **not** expose explicit SceneVibe lines proving internal
`RESTORE`, `MediaCalendar`, `DUE` or revision transitions. This closure therefore does
not claim such proof from the log.

The log did show Android destroying the `SceneVibe SceneRenderer` window at the expected
time, consistent with the physically observed normal visual removal.

The final inspected log contained no SceneVibe occurrence of:

- `CORRUPT`
- `CACHE_FAILED`
- `GENERIC_INVALID`

No SceneVibe fatal crash or duplicate visual-owner symptom was identified. Unrelated
Sony/Android/Prime log noise is not attributed to SceneVibe.

The Prime session used for the final observations matched media identity ending in
`992e`, consistent with the qualified Prime path.

## Protocol reconciliation

The targeted M5 Sony protocol is considered satisfied by the combined direct observations:

- upgrade continuity without reset or re-pairing;
- new temporal engine visible in Diagnostics;
- existing revision restored without a Send;
- Unicode preservation;
- DUE/expiry behavior observed physically;
- pause/resume behavior;
- forward and backward seek behavior;
- eligibility loss/recovery behavior;
- supported Cloud replacement 5 -> 6;
- full reboot with durable revision survival;
- no post-reboot stale overlay;
- post-reboot rendering of revision 6;
- read failure NONE;
- final failure-signature log inspection.

The protocol requires reporting observations honestly rather than inferring unavailable
internal state. Accordingly, the post-reboot `Last startup restore: -` value remains a
documented diagnostic anomaly, not a hidden PASS condition and not evidence of `ARMED`.

## Final disposition

- M5 automated software qualification: **PASS** on the qualified software HEAD
- M5 targeted Sony physical qualification: **PASS**
- MediaCalendar Video cutover physical gate: **CLOSED**
- Production: **SHADOW**
- PR #15: **kept OPEN / DRAFT / unmerged by this closure**
- Merge action: **not performed**
- Next action: final PR audit on the documentation-only closure delta before any merge decision

The previous Phase D report and PR manifest remain the authoritative software evidence.
This document is the authoritative final physical acceptance record for M5.
