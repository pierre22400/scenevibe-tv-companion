# M4 Sony physical qualification — manual protocol

Status: **NOT PERFORMED**. This is a checklist for the existing Sony Android TV target,
not a result. Production remains **SHADOW**; PR #14 stays OPEN / DRAFT / unmerged.
Automated JVM and API 35 results do not qualify physical TV boot, Prime MediaSession,
native overlay windows, D-pad placement or upgrade continuity.

Use the exact **final Phase G HEAD** and its **stable-signed Cloud APK artifact** from the
[PR #14 final Git closure manifest](https://github.com/pierre22400/scenevibe-tv-companion/pull/14),
linked by the [Phase G report](m4-phase-g-generic-restore-diagnostics-report.md).
Record the commit, Actions run, artifact id, downloaded APK SHA-256 and certificate before installing.
Do not substitute a debug-signed APK or an APK from another commit.

| Session evidence | Observed value |
| --- | --- |
| Operator / date / timezone | ____ |
| Sony model / firmware / Android version / ADB serial | ____ |
| Final Phase G HEAD / debug run / stable APK artifact id | ____ / ____ / ____ |
| Downloaded raw APK SHA-256 | ____ |
| Observed signer certificate SHA-256 | ____ |
| Expected certificate SHA-256 | `f908bf564ed97ba67e02b1ebc89eb0239cf980752587f55eb9ec0419791a2e9c` |
| Package / expected version | `com.scenevibe.tvcompanionpoc` / code 12, `0.8.2-tv-release-hardening` |
| Existing paired Cloud device (abbreviated only) | ____ |
| Evidence directory / logcat file | ____ / ____ |

Every numbered line requires **PASS / FAIL / NOT RUN**, exact observed revision and ACK where
applicable, and an evidence reference. Blank fields are NOT RUN, never PASS. Capture Diagnostics
screens and bounded observations; do not include credentials, full identities or raw Cloud bodies.
No uninstall, data clear, Reset Cloud or re-pairing is permitted during the upgrade/continuity checks.
Record any failure before stopping that check; preserve state for the later audit.

1. **Before upgrade**, open Diagnostics and record current app version, abbreviated installation id
   and Cloud device id, overlay permission, MediaSession/notification access, autostart opt-in,
   installed/cached revision and acknowledged revision. Record existing package shape if known;
   retain the historical cache in place. Result: ____; revision/ACK: ____ / ____; evidence: ____.

2. Verify the exact final-head stable artifact and signer above, then upgrade the existing package
   using `adb -s <sony-serial> install -r <verified-phase-g-stable.apk>` (`adb install -r`),
   **without uninstall or clearing app data**. Preserve the command output and observed app version.
   Result: ____; revision/ACK after install: ____ / ____; evidence: ____.

3. Compare data, abbreviated installation id, Cloud device id and pairing with step 1. Confirm
   overlay permission, MediaSession access and autostart state remain preserved. Do not repair a
   changed identity by pairing again and count it as continuity. Result: ____;
   ids/permissions/opt-in comparison: ____; revision/ACK: ____ / ____; evidence: ____.

4. **Without a new Cloud Send**, enter Start SceneVibe or the existing boot-prepare path and verify
   restoration of the previously durable historical/generic package. Diagnostics must show the
   same revision, ACK, codec and handler and `Last startup restore: ARMED`. MEDIA/CONTINUE manifested
   or other unsupported durable data must fail closed; record that refusal rather than silently
   changing capabilities, clearing or replacing its cache. Result: ____;
   revision/ACK/codec/handler/startup outcome: ____ / ____ / ____ / ____ / ____; evidence: ____.

5. Before an eligible due media event, confirm restore alone displays **no stale card, scene or
   permanent Consumer badge**. Record both immediate startup and entry into Prime before the first
   due event. Result: ____; revision/ACK: ____ / ____; visual observation/evidence: ____.

6. Send one new manifested Video MEDIA/FREEZE revision through the existing Cloud v1 flow to this
   paired Sony. Use the qualified Prime media identity and record the expected revision and known
   due media positions. Verify its scene at an eligible due event. Result: ____;
   sent/installed revision and ACK: ____ / ____ / ____; evidence: ____.

7. In Diagnostics compare **Installed revision**, **Acknowledged revision**, **Last assignment
   revision** and **Last successful ACK** against the exact sent revision. A local ARMED result
   alone is not server ACK. Record the prior ACK during a pending interval if observable, then the
   confirmed values. Result: ____; installed/ACK/assignment/successful ACK: ____ / ____ / ____ / ____;
   evidence: ____.

8. While a manifested scene is visibly active, send a **newer** manifested revision. Record old
   and new revisions, visible replacement timing and confirmed ACK; do not restart the service
   to hide a replacement defect. Result: ____; old/new revision/ACK: ____ / ____ / ____; evidence: ____.

9. Observe the handoff and later due/expiry events: exactly **one visual owner**, immediate removal
   of the old scene and no old-scene resurrection or late old expiry hiding the new scene.
   Result: ____; revision/ACK: ____ / ____; visual-owner evidence: ____.

10. Use the existing authorized redelivery mechanism to deliver the **same revision**, without a
    new Send/increment. Record how redelivery was induced and prove the revision/handler/content
    stay unchanged and ACK confirms only after successful re-ARM. If no mechanism is available,
    record NOT RUN and arrange that test before physical acceptance; do not edit private data.
    Result: ____; revision/ACK before/after: ____ / ____ → ____ / ____; evidence: ____.

11. Pause while the new scene is visible, then resume. Verify the qualified media-time FREEZE
    behavior and no added wall clock, stale scene or duplicate owner. Result: ____;
    media positions/revision/ACK: ____ / ____ / ____; evidence: ____.

12. Seek **backward** across a known due event. Verify the existing replay/seek policy, eligible
    identity and one owner; record positions and observed scene. Result: ____;
    positions/revision/ACK: ____ / ____ / ____; evidence: ____.

13. Seek **forward** beyond a scene window. Verify the existing forward-seek/expiry policy without
    stale resurrection or duplicate owner. Result: ____;
    positions/revision/ACK: ____ / ____ / ____; evidence: ____.

14. Lose then recover media eligibility (leave the qualified Prime session or select non-matching
    media, then return). Verify immediate visual retirement on loss and display only after an
    eligible due event on return. Result: ____; media/block codes/revision/ACK: ____ / ____ / ____ / ____;
    evidence: ____.

15. Enable/confirm autostart, record installed revision/ACK, then perform a **hard TV reboot with
    no new Send**: a genuine full Sony restart/power cycle, not standby, app restart or force-stop.
    Record the exact procedure and boot completion evidence. A force-stop/emulator is **not** a
    substitute for hard reboot. Result: ____; pre-reboot revision/ACK/opt-in: ____ / ____ / ____;
    reboot procedure/evidence: ____.

16. After the hard reboot, **without Send or re-pairing**, confirm autostart decision START, restored
    exact durable revision, codec, handler, ACK and startup ARMED. Recompare both abbreviated ids
    and permissions with step 1. Process-local assignment/successful-ACK counters may be zero
    before redelivery; durable ACK must persist. Result: ____;
    revision/ACK/codec/handler/startup/autostart: ____ / ____ / ____ / ____ / ____ / ____; evidence: ____.

17. Verify no old card/scene/badge appears at boot or before an eligible due event. Then produce
    an eligible due event and verify the restored package displays with exactly one owner.
    Result: ____; revision/ACK: ____ / ____; before/after visual evidence: ____.

18. Perform **post-reboot same-revision redelivery**, retaining the exact revision and handler.
    Compare durable ACK and the process-local assignment/successful-ACK counters after confirmed
    delivery. Result: ____; revision/ACK/assignment/successful ACK: ____ / ____ / ____ / ____;
    redelivery method/evidence: ____.

19. Verify **Unicode** visually and in approved bounded fixture evidence: composed accented French,
    decomposed `e` + combining acute accent, ligature `œ`, apostrophe `’` and emoji `🙂`.
    Use known exact fixture text through the qualified Video path; verify preservation after
    replacement/redelivery and hard reboot, with no normalization/replacement character.
    If a new Unicode Send is needed, repeat steps 15–18 for that revision. Result: ____;
    expected fixture/reference/revision/ACK: ____ / ____ / ____ / ____; evidence: ____.

20. Inspect the complete upgrade/startup/replacement/redelivery/pause/seek/eligibility/reboot logcat
    evidence for AndroidRuntime / FATAL, Looper/thread/window failures, SceneVibe installer/restore
    exceptions and duplicate visual-owner symptoms. Use `adb -s <sony-serial> logcat -d -v threadtime`
    into the evidence file; retain reboot-session evidence as available. Record time ranges and
    bounded findings without publishing secrets. Result: ____; revision/ACK: ____ / ____;
    log/evidence/findings: ____.

| Physical qualification closure — to be completed by the operator and separate audit | Value |
| --- | --- |
| All 20 steps completed with evidence? | ____ |
| PASS / FAIL / NOT RUN steps | ____ / ____ / ____ |
| Final observed revision / durable ACK / codec / handler | ____ / ____ / ____ / ____ |
| Deviations / unresolved failures / missing physical evidence | ____ |
| Operator result and date | ____ |
| Separate Chat audit reference and decision | ____ |

**No physical PASS is asserted by this document.** API 35 standard-image smoke and JVM recreation
cannot replace this Sony protocol. Work Phase G authorizes preparation for this manual gate;
the later physical evidence and separate audit determine acceptance. No merge, production cutover
or M5+ action is authorized here.
