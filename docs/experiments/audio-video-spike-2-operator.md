# SceneVibe OS — Audio/Video capability Spike 2.0

STATUS: SOFTWARE IMPLEMENTATION / BUILD AND PHYSICAL SONY QUALIFICATION PENDING.
ISOLATED: only experimental mediaexperiment module. NOT PRODUCTION / NOT M6.

## Physical baseline
Authoritative Spike 1.0 report: docs/experiments/media-interlude-poc-report.md
at 96d1de5b555d52a88eda74fb45a6895d8599b316. On 2026-10-06,
Sony BRAVIA Android TV 12 with native Prime Video demonstrated controlled
PAUSE -> fullscreen local video with audio -> same-session guarded PLAY -> PLAYING.
AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK caused Prime to PAUSE rather than duck;
Prime did not self-resume. Sony/MediaTek rejected M4A, while AAC/ADTS passed.
Do not reclassify the failed ducking experiment as audio mixing success.

## New tests and fail-closed conditions
1. VOICE MP3 10S / NO FOCUS: observe a freshly PLAYING Prime MediaSession
   with stable token, start 10-second local MP3 speech, issue NO AudioFocus and
   NO transport PAUSE/PLAY, rescan the original token once a second. Stop on
   native PAUSE, replacement session, changed media, source error or 13s timeout.
   This is deliberately diagnostic only, not a production AudioFocus policy.
2. VIDEO INTERLUDE MP4 10S: preflight real local MP4 with both video/audio and
   9.5–10.5s duration before any native PAUSE; reuse the existing v1
   focus/confirmed PAUSE/guarded-resume sequence with a distinct external file.
   The existing TEST FULL INTERLUDE remains on its original bundled asset.
3. EMERGENCY STOP kills local voice/video and never sends native PLAY.
4. Existing DUCK / TEST PAUSE / FULL INTERLUDE retain the historical semantics.

The new Android-free VoiceCoexistenceProbe intentionally lacks AudioFocusPort
and MediaControlPort dependencies. The state COMPLETED_NEEDS_PHYSICAL_PROOF
means only that software saw stable PLAYING until local completion. It is
NOT proof the audio was audible or the image continuously advanced.

## Resources and licences
Prepare via mediaexperiment/fixtures/prepare-spike-2-fixtures.ps1:
- Speech MP3 10 seconds, cut from Samplelib's publicly available 1min MP3
  English speech (samplelib.com/sample-mp3.html). Samplelib advertises its
  downloads as without licence restrictions.
- Genuine moving Big Buck Bunny H.264 MP4 10s 720p from test-videos.co.uk,
  retaining video and adding an AAC 440Hz diagnostic tone. Attribution:
  Big Buck Bunny © Blender Foundation / CC BY.
- Assets are NOT bundled in GitHub: record the SHA256 output and transfer via
  adb into app-specific external files for this experimental package.
- The app has NO INTERNET permission, cloud client or shared production cache.

Install the experimental APK first and launch it once to initialize its
app-specific external-files directory. Then, from Windows with FFmpeg and ADB:
    powershell.exe -ExecutionPolicy Bypass -File .\mediaexperiment\fixtures\prepare-spike-2-fixtures.ps1 -PushToTv

If adb push fails due to OEM scoped storage, STOP and diagnose; never use
shared SceneVibe production directories or broaden permissions as a workaround.

## Qualification protocol (only operator can perform on Sony)
Install the isolated 0.2.0 experimental APK beside production, grant its own
overlay and notification access, launch native Prime PLAYING, SCAN first.
Run VOICE: verify real native picture progression, BOTH native Prime audio and
spoken fixture audibility, absence of Prime PAUSE and unchanged original session.
A PAUSED state is a FAIL even if speech can be heard. Do not auto-resume.
Run VIDEO: confirm an actual moving 10-second clip with AAC tone,
observed PAUSE ownership and guarded return to Prime PLAYING.
Test emergency STOP independently (never auto-PLAY). Repeat legacy FULL
INTERLUDE to detect regressions. Record observed states, coarse diagnostics,
fixture hashes, APK hash and TV software version, but never media titles, session
tokens, subtitle payloads or credentials.

## Software gates
Gradle 8.9 / JDK 17 / Android SDK 35:
  gradle :mediaexperiment:testDebugUnitTest :mediaexperiment:lintDebug :mediaexperiment:assembleDebug

Expected experimental branch's older frozen Python boundary inventory has
14 inherited failures from module inclusion and report inventory. Do not edit
any app/, tests/, settings.gradle or frozen historical assertions for this spike.
CI green is software-only. Do NOT merge, enable production or claim Sony PASS
before the separate physical qualification report is completed.
