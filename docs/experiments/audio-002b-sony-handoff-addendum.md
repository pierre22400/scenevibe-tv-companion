# SceneVibe TV Companion POC - KIRO AUDIO 002B - Sony physical-qualification handoff ADDENDUM

**Status: PROTOCOL TO EXECUTE LATER. NO Sony step is executed in AUDIO 002B.**
**Nature: an addendum to `audio-002-sony-handoff.md` recording what the 002B corrections change for the future physical qualification. It is a plan, not a results report. It runs ONLY under a separate owner GO.**

> This addendum does NOT replace `audio-002-sony-handoff.md`. The base handoff remains the authoritative operator protocol: its R0 remote-safety GATE (§2), the install-alongside-stable DISTINCT-package steps (§3), the Scenario A coexistence test (§4), the synchronization matrix (§5), the TEXT non-regression check (§6), the prohibited-on-the-audio-path list (§7), and the GO / STOP note (§8) all stand UNCHANGED and MUST be followed. This addendum only records the 002B deltas and re-states the gate and prohibitions so nothing is lost.
>
> The three-way distinction is preserved:
>
> - **SOFTWARE PASS** - obtained in the sandbox (JVM): now including integrated-software AUDIO triggering through the REAL scheduler. Software proof only.
> - **HISTORICAL PHYSICAL PASS A1** - the pre-existing voice-coexistence result on the one Sony / Prime configuration; context, not re-earned here.
> - **NOT PHYSICALLY QUALIFIED (AUDIO 002B)** - everything in this handoff; it becomes a verdict only after the protocol is executed on hardware under a separate owner GO.

## 1. What AUDIO 002B changes for the physical protocol

AUDIO 002 delivered the audio engine and a software matrix but the AUDIO path did not actually fire end to end on the integrated app, because the demo catalog was keyed to identifiers the live scheduler never emits. AUDIO 002B fixes that in integrated software (SOFTWARE PASS). For the future physical operator this means two things change, and both make the matrix in `audio-002-sony-handoff.md` §5 more directly observable:

1. **AUDIO now triggers end to end via the REAL scheduler event ids (C1 / C4).** The audio catalog is bound to the SAME activation token (`video-activation-N`) and the SAME `SceneEvent` event ids the `MediaCalendarScheduler` emits on `onDue`, via an explicit, testable mapping (`DemoAudioTrack.catalogForCalendar(...)`, bound in `OverlayService.bindAudioCatalog(token)` on `onEligibility(token, true)`). So in §5 row 1 ("Normal playback"), the operator should now EXPECT a comment to actually trigger at a comment `startMs` on the integrated experimental build, where in AUDIO 002 nothing would have fired. The exit criterion of the matrix becomes testable on hardware.

2. **Session-token safety (C3).** `SceneVibeAudioRenderer.onDue` / `onExpire` now ignore any due / expire bearing a token that is not the current catalog token. For the operator this means a late or reordered callback from a PREVIOUS activation (for example right after an episode change, §5 row 7) cannot start a comment in the new session nor interrupt a comment active in it. Observed behavior should be strictly scoped to the current activation.

Nothing else in the physical protocol changes. In particular the C2 decision (pre-preparation stays a tests-only latency hook, no second clock) has NO on-device behavioral effect: there is no new look-ahead edge to observe.

## 2. Preconditions delta

Replace the AUDIO 002 build note in `audio-002-sony-handoff.md` §1 ("An experimental APK assembled ... not built in AUDIO 002") with: the experimental APK is assembled by the PC DENIS procedure in `audio-002b-pc-denis-procedure.md` on an Android-SDK machine (`assembleAudioexp`, DISTINCT `applicationId com.scenevibe.tvcompanionpoc.audioexp`, DISPOSABLE debug signing, NOT M5-signed). Its SHA-256 is recorded at build time by that procedure and again at install time here. It was NOT built in AUDIO 002B (sandbox has no SDK -> BLOCKED-ENV).

The bundled cue to verify before install is unchanged: `scenevibe_cue.aac`, **7032 bytes**, SHA-256 **`96eb915315b01ebb624a5802440056cab30201f5c135aced55ee5276a6a381d7`**.

## 3. Preserved gates and prohibitions (unchanged, re-stated)

These are carried verbatim from `audio-002-sony-handoff.md` and remain MANDATORY:

- **R0 remote-safety GATE (base §2).** Before any media test: record a remote baseline, install the experimental package only, confirm the overlay is passive (`FLAG_NOT_FOCUSABLE | FLAG_NOT_TOUCHABLE`, no clickable element, never `requestFocus()`), confirm all remote keys still respond, hide the overlay and confirm the remote still works without any reboot. If R0 fails, `force-stop` the experimental package only and ABORT the media tests. Scenario A and the matrix start ONLY if R0 is PASS.
- **Install alongside stable, DISTINCT package (base §3).** Install `com.scenevibe.tvcompanionpoc.audioexp` beside the stable `com.scenevibe.tvcompanionpoc`. Do NOT uninstall the stable package, do NOT `pm clear`, do NOT reset the Sony / M5, do NOT redo pairing. On a debug-signing-key conflict, uninstall ONLY the experimental package.
- **Prohibited on the audio path (base §7).** No Prime transport (no PAUSE / PLAY / SEEK from SceneVibe); no capture of the third-party player; no DRM bypass; no replacement of the original soundtrack; no `AudioFocus` request; no system / Prime volume mutation (no `setStreamVolume`, no global `AudioManager` change - SceneVibe volume is a LOCAL gain only); no Cloud contract change; no use of the M5 keystore and no change to the signed stable line; no automatic GitHub merge; no Sony install without the owner GO.
- **Allowed diagnostics only.** Observed states (PLAYING / PAUSED), audibility yes/no, picture progression yes/no, the fixture SHA-256, the experimental APK SHA-256, TV software version, Prime version. Never log media titles, session tokens, subtitles, or credentials.

## 4. PowerShell first-line rule (unchanged)

Any PowerShell helper used on the PC DENIS to drive the ADB install steps (base §3.1) or the Android-SDK build / verification steps (`audio-002b-pc-denis-procedure.md`) MUST begin with the exact line:

```powershell
Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass
```

Such a helper never requests, copies, or embeds any signing secret and never touches the M5 keystore. The install helper installs the DISTINCT experimental package alongside stable; the build helper assembles the DEBUG-signed `audioexp` variant and reads (never writes) its signer certificate.

## 5. GO / STOP note (unchanged)

This is a FUTURE protocol executed only under a SEPARATE OWNER GO. **AUDIO 002B executes no Sony step.** The real behavior on the Sony is the authority: do not assume an audio policy guarantees mixing; if coexistence is impossible or unstable (for example Prime PAUSE), STOP this path and document the block before any new architecture. The physical verdict (FINAL / PARTIAL / BLOCKED) is produced by that future mandate, not here.

Until then AUDIO 002B remains **NOT PHYSICALLY QUALIFIED**, with a **SOFTWARE PASS** earned (now including integrated-software AUDIO triggering through the real scheduler) and the **HISTORICAL PHYSICAL PASS A1** standing as pre-existing context. The experimental Android build itself is **BLOCKED-ENV** in the sandbox and is to be run via `audio-002b-pc-denis-procedure.md` on an SDK-equipped machine before the physical protocol begins.
