# SceneVibe TV Companion POC

> **STATUS — ACTIVE ANDROID TV REPOSITORY.** Together with `pierre22400/interface-scenevibe`, this is part of the default active SceneVibe working set. `main` is the physically qualified v0.6 LAN baseline. PR #7 is the active outbound-cloud candidate, but it must be aligned to the canonical `/api/v1` cloud protocol merged in the monorepo before it can be merged here.


**Purpose:** verify on a physical Android TV / Google TV that SceneVibe can keep
an independent, transparent commentary layer above an official streaming app
without controlling, modifying, capturing or replacing the video stream.

This repository is an installable technical POC, not the final SceneVibe TV
application.

## Current state

The current candidate build is **v0.6.0** (paired authenticated LAN POC).

- A TV-friendly activity checks the user-granted **Display over other apps**
  capability and starts/stops the overlay.
- A foreground service owns the overlay after the activity is left.
- A noninteractive TYPE_APPLICATION_OVERLAY window uses
  FLAG_NOT_FOCUSABLE | FLAG_NOT_TOUCHABLE, so the streaming app keeps remote
  focus.
- A bounded LAN HTTP server listens on TV port **8765** only while the overlay
  service runs.
- POST /commentary requires a paired Bearer token and renders bounded text/image.
- GET /health reports service readiness.
- A passive MediaSession probe can sample active session playback state/position once per second after the user explicitly grants Android notification-listener access.
- POST /track requires the same token and loads a bounded runtime track produced from FinalTrack 1.1; the TV schedules its comments from the selected streaming app's MediaSession clock.
- The user opens a 120-second six-digit pairing window on the TV; POST /pair exchanges that code for a revocable 256-bit random token. Reset pairing invalidates the token immediately.
- A runtime track can opt in to `pauseFreezesDisplay`: its currently visible commentary retains the remaining display time while the matching MediaSession reports pause.
- The newest commentary owns its expiry: when a new comment replaces an older
  one, the previous pending expiry is cancelled before the new duration starts.

The app has no capture, accessibility, microphone or privileged media-control
permission, no boot receiver, no Cast receiver and no streaming-account
integration. It does use android.permission.INTERNET solely for the current LAN
POC transport.

v0.4.0 also declares a NotificationListenerService solely because Android allows
an enabled notification listener to call MediaSessionManager.getActiveSessions()
for sessions published by other apps. The user must explicitly grant that
special access in Android settings. The SceneVibe listener does not implement
notification-content callbacks and the probe never sends playback controls.

## Security boundary

v0.6.0 authenticates mutating LAN requests with a paired Bearer token. Pairing
is initiated on the TV, limited to five incorrect codes and expires after 120
seconds. The token is persisted in private application storage; Reset pairing
revokes it. **Transport is still plain HTTP without TLS.** A local network
eavesdropper or active MITM can capture the code or token. Use a trusted LAN;
this POC does not solve hostile Wi-Fi, interception or client identity.
See [the versioned LAN protocol](docs/lan-pairing-protocol-v1.md).

The Companion does not contact, inspect, capture or modify the streaming
application or its video.

## Open-source review before implementation

Reviewed the default branches on 24 September 2026:

| Project | License and implementation | POC decision |
| --- | --- | --- |
| [TvOverlay](https://github.com/gugutab/TvOverlay) | No LICENSE file in the repository. Its README describes SYSTEM_ALERT_WINDOW, an ADB appops fallback, positioning and REST/MQTT control. | No code copied. Permission troubleshooting and corner positioning informed the test protocol. |
| [TVCompanion](https://github.com/avnishkirnalli/TVCompanion) | MIT; Android TV host/controller architecture with foreground service and network transport. | No code copied. Service/activity separation was a useful architectural reference. Screen capture and boot-start behavior were excluded. |
| [PipTV](https://github.com/juniorbarrigana/PipTV) | MIT; Android TV UI with accessibility-overlay behavior for an interactive player. | No code copied. SceneVibe deliberately uses a noninteractive application overlay and no accessibility service. |

No third-party source is included. The implementation uses Android framework
APIs directly.

## Build and automated checks

Requires JDK 17, Android SDK Platform 35, Build Tools 35.x and Gradle **8.9**.
The Android Gradle plugin is pinned to **8.7.3**.

~~~sh
python3 -m unittest discover -s tests
gradle :app:assembleDebug :app:lintDebug :app:testDebugUnitTest --stacktrace
~~~

The debug APK is:

~~~text
app/build/outputs/apk/debug/app-debug.apk
~~~

The **Android debug APK** GitHub Actions workflow runs the same contract tests,
Android build, unit tests and lint on pushes to the work branch / PR, then uploads
scenevibe-tv-companion-poc-debug.

A green build proves packaging and static checks only. Physical qualification
is recorded separately below.

Target: Android API **26–35**. No Sony-only API is used.

### Debug APK signing note

GitHub-hosted runners create an ephemeral debug keystore unless a stable signing
configuration is supplied. Therefore a debug APK from a later workflow run may
not update a previously installed debug APK with adb install -r.

For this disposable POC, if Android reports
INSTALL_FAILED_UPDATE_INCOMPATIBLE, uninstall the existing package and then
install the new APK:

~~~sh
adb uninstall com.scenevibe.tvcompanionpoc
adb install app-debug.apk
~~~

This also clears the **Display over other apps** grant, so grant it again after
reinstallation.

## Install and grant overlay permission

Connect ADB to the TV, then:

~~~sh
adb install app-debug.apk
adb shell am start -n com.scenevibe.tvcompanionpoc/.MainActivity
~~~

On the TV, select **Open overlay permission settings**, locate SceneVibe TV
Companion POC and allow **Display over other apps**. Return to the app and
confirm:

~~~text
Display over other apps: granted
~~~

If a development TV does not expose the permission screen, an ADB fallback is
available after explicitly deciding to grant it:

~~~sh
adb shell appops set com.scenevibe.tvcompanionpoc SYSTEM_ALERT_WINDOW allow
adb shell appops get com.scenevibe.tvcompanionpoc SYSTEM_ALERT_WINDOW
~~~

Do not use ADB as the intended consumer permission flow.

## Dynamic commentary protocol

While the user-started overlay service is running, the TV listens on TCP port
**8765**.

### Health

~~~http
GET /health
~~~

v0.6.0 returns a JSON object with:

- type: scenevibe.health.v1
- status: ready
- version: 0.6.0
- protocolVersion: "1", pairingRequired: true, paired: true/false
- media: inline-image
- synchronization: media-session-clock

### Commentary

~~~http
POST /commentary
Content-Type: application/json
Authorization: Bearer <token>
~~~

Body contract:

- type must be scenevibe.commentary.v1
- id: non-empty, maximum 128 characters
- text: non-empty, maximum 1000 characters
- durationMs: optional, default 10000, allowed range 1000–60000
- request body maximum: 3 MiB
- optional media.kind: image
- optional media.mimeType: image/jpeg or image/png
- optional media.dataBase64: inline image bytes, maximum 2 MiB after Base64 decoding
- decoded images are downsampled for rendering to a maximum working envelope of 1280×720

Example PowerShell request, replacing the IP and token:

~~~powershell
Invoke-RestMethod -Method Post -Uri "http://192.168.1.183:8765/commentary" `
  -ContentType "application/json" `
  -Headers @{ Authorization = "Bearer $token" } `
  -Body '{"type":"scenevibe.commentary.v1","id":"physical-001","text":"Dynamic SceneVibe commentary from the PC.","durationMs":10000}'
~~~

Expected acknowledgement:

~~~json
{"type":"scenevibe.commentary.ack.v1","id":"physical-001","status":"rendered","mediaRendered":false}
~~~

### Image commentary

v0.3.0 adds an optional inline image payload to the same commentary contract.
The TV never reads a Windows path and does not fetch an arbitrary remote image:
the sender reads the local asset, Base64-encodes it and sends the bounded bytes
with the commentary.

The repository includes a PowerShell sender:

~~~powershell
.\scripts\send-media-commentary.ps1 -TvIp "192.168.1.183" -Token $token -ImagePath "C:\images\example.jpg" -Text "SceneVibe — image overlay test" -DurationMs 10000
~~~

A successful media ACK contains mediaRendered: true.

This transport shape is deliberately separate from the canonical FinalTrack
schema. The physical media-rendering POC should be qualified first; a durable
FinalTrack media-reference contract can then be versioned without embedding
machine-specific paths such as C:\\... in canonical tracks.

Stopping the overlay service also closes port 8765. Starting the overlay again
restarts the commentary server.

## Physical qualification — Sony Bravia, 24 September 2026

### v0.1.0 overlay architecture

After the user granted **Display over other apps**, the overlay remained visible
over playing video in:

- Prime Video
- Netflix
- Disney+
- Canal+
- YouTube

Prime Video was exercised for at least five minutes with normal picture, sound
and remote control. Top-right and bottom-right placement, cross-app persistence,
explicit Stop, full Android reboot behavior, force-stop and recovery were also
tested successfully.

A full reboot stopped the running overlay, as expected because this POC has no
boot receiver, while the Android overlay permission remained granted.

### v0.2.0 dynamic transport

The LAN transport was then qualified physically on the same Sony Bravia.

Observed passes:

1. A PowerShell POST /commentary from the PC produced visible dynamic
   commentary above a playing Prime Video image and returned the correlated
   scenevibe.commentary.ack.v1 with status: rendered.
2. Three successive commentary messages were all received and rendered.
   Perceived timing during replacement was slightly imprecise; this observation
   led to the v0.2.1 exact-expiry correction.
3. With Prime Video remaining in the foreground and the Companion activity in
   the background, a new LAN commentary still rendered and returned its ACK.
4. After switching from Prime Video to Netflix, the overlay and LAN transport
   remained active and a new commentary rendered successfully above Netflix.
5. **Stop overlay** removed the badge and closed the HTTP server. A subsequent
   GET /health failed to connect, which is the expected stopped state.
6. Starting the overlay again restored normal overlay and server operation
   without rebooting the TV.

These results qualify the tested architecture on that physical Sony Bravia.
They do not establish identical behavior on every Android TV / Google TV model,
firmware or streaming application.

## v0.2.1 correction

Physical v0.2.0 testing showed that replacement comments all rendered correctly,
but their visible durations could feel imprecise.

v0.2.1 changes the renderer so each commentary owns one explicit expiry
callback. Before a newer commentary is displayed, the previous pending expiry
callback is removed. The new comment then receives its full requested
durationMs, and only its expiry restores the normal status badge.

A contract test pins this behavior by requiring cancellation of the previous
expiry and scheduling of the new one.

### Physical v0.2.1 timer validation

The correction was then verified on the same physical Sony Bravia.

A single commentary requested for 5000 ms was logged as displayed at
22:12:44.856 and expired at 22:12:49.859, for an observed renderer duration of
5003 ms.

A replacement sequence was then exercised with 12 s, 12 s and 10 s comments.
The renderer logged three commentary displays but only one final expiry. The
third commentary was displayed at 22:13:30.219 and expired at 22:13:40.221,
for an observed duration of 10002 ms. The earlier pending expiries did not
interrupt the replacement comment.

The same sequence was also observed visually on the TV and behaved normally.
This physically validates the v0.2.1 latest-commentary expiry correction on the
tested Sony Bravia.

## v0.3.0 rich overlay candidate

v0.3.0 preserves the qualified text/timer behavior and extends the overlay card
with an optional image region. The sender may provide a JPEG or PNG together
with a normal commentary. The image and text share the same duration and the
same latest-commentary expiry ownership.

The transport is bounded before bitmap allocation: the HTTP body is capped,
decoded image bytes are capped at 2 MiB, the declared MIME type must match the
decoded image, and large dimensions are downsampled before rendering. No new
Android permission is added.

### Physical v0.3.0 image validation

The rich overlay path was then tested physically on the same Sony Bravia with a
local JPEG supplied by the PC sender. The Companion returned
`mediaRendered: true`, and the image was visibly rendered in the SceneVibe
overlay together with its commentary text above the active TV application.

This validates the first end-to-end rich-media path:

`PC asset -> bounded LAN commentary payload -> Android TV Companion -> image + text overlay`.

The result qualifies JPEG image rendering for this POC on the tested Sony
Bravia. It does not yet define the durable canonical FinalTrack media-reference
contract.

## FinalTrack 1.1 mixed playback sender

The repository also includes a development sender for the separately validated
FinalTrack 1.1 media-reference candidate.

The canonical track contains only an `assetRef`. Machine-local paths remain in
an external asset map used by the sender.

Example asset map:

~~~json
{
  "asset-house-001": "C:\\Users\\DENIS\\Downloads\\maison.jpg"
}
~~~

The included mixed fixture contains a text-only comment, an image-backed
comment, then another text-only comment.

Run it from the repository root:

~~~powershell
.\scripts\play-finaltrack-media.ps1 `
  -TvIp "192.168.1.183" `
  -TrackPath ".\examples\finaltrack-media-tv-poc.json" `
  -AssetMapPath "$env:TEMP\scenevibe-asset-map.json" `
  -Token $token
~~~

The sender resolves the referenced asset locally, enforces the 2 MiB POC limit,
encodes the bytes only for the LAN transport payload, and never writes the local
path into FinalTrack.

### Physical FinalTrack 1.1 mixed playback validation

The sender was then exercised against the physical Sony Bravia with a three-event
FinalTrack 1.1 sequence:

1. text-only commentary;
2. an image-backed commentary referencing `asset-house-001`;
3. text-only commentary.

The TV visibly rendered the complete sequence. The correlated ACKs reported
`mediaRendered: false` for the two text-only events and
`mediaRendered: true` for the image-backed event, which is the expected
transport state. The sender resolved the local JPEG through the external asset
map; the canonical FinalTrack contained only the asset reference.

This physically validates the mixed FinalTrack 1.1 playback path on the tested
Sony Bravia:

`FinalTrack 1.1 -> assetRef resolution -> bounded LAN payload -> TV rich overlay`.

## v0.4.0 passive MediaSession synchronization probe

This cycle tests whether Android TV itself exposes a useful playback clock from
official streaming applications.

Android requires either privileged MEDIA_CONTENT_CONTROL permission or an
enabled NotificationListenerService before an ordinary app can query active
sessions from other packages. The POC uses the user-granted notification-listener
route and does not request privileged media-control permission.

In the TV activity:

1. choose **Open Apps settings for MediaSession access**;
2. navigate with the remote to **Special app access -> Notification access -> SceneVibe**;
3. explicitly grant access to SceneVibe;
4. return and confirm **MediaSession access: granted**;
5. start the overlay;
6. open a streaming application and play a title.

The indirect Apps-settings route is intentional. On the physically tested Sony
Bravia, launching the notification-access activity directly produced an initial
D-pad focus trap, while entering through the normal Apps settings hierarchy
preserved remote navigation.

The probe samples once per second and writes only to the `SceneVibeMedia`
logcat tag. It records package, playback state, published position, an estimated
current position, speed, age of the last position update, duration and basic
published title/subtitle metadata.

The estimated position is derived only while the published state is PLAYING,
FAST_FORWARDING or REWINDING:

`estimated = publishedPosition + updateAge × playbackSpeed`.

The probe is intentionally passive. It does not call transport controls, dispatch
media buttons, seek, pause, resume, inspect notifications, capture the screen or
read subtitle content.

Physical qualification should test at least:

- normal playback: estimated position advances near real time;
- pause: state becomes PAUSED and position stops advancing;
- resume: state returns to PLAYING;
- forward seek: published/estimated position jumps forward;
- backward seek: published/estimated position jumps backward;
- application switch: identify whether a new active session is exposed.

Prime Video is the first target. Netflix, Disney+ and YouTube should then be
checked because session publication is controlled by each streaming app.

Use:

~~~sh
adb logcat -c
adb logcat -v time -s SceneVibeMedia:I
~~~

This test is successful only if a streaming application's published MediaSession
provides a sufficiently accurate and responsive clock. A successful Android API
call by itself is not enough.

## Physical v0.4.0 MediaSession qualification — Sony Bravia, 25 September 2026

The passive MediaSession probe was exercised physically before introducing TV-side
track scheduling.

On the tested Sony Bravia, Prime Video exposed package
`com.amazon.amazonvideo.livingroom` with a usable playback clock. During normal
playback the published position advanced approximately one second per second
(for example 263480 ms, 264493 ms, 265498 ms, 266503 ms). Pause was then held
for more than ten seconds and the position remained fixed at 366667 ms while
the state remained PAUSED. Resume returned to PLAYING and normal progression.
A forward seek of roughly two minutes moved the published clock from about
376348 ms to about 501056 ms, after which normal progression resumed.

Netflix exposed package `com.netflix.ninja` with the same basic behavior.
Normal playback advanced from 14560 ms to 18683 ms over successive samples.
During pause, the position remained fixed at 41691 ms while updateAge continued
to increase.

These observations physically qualify the passive playback clock on Prime Video
for play, pause, resume and forward seek, and on Netflix for play and pause, on
this Sony Bravia.

Additional physical checks on the same TV showed:

- Canal+ (`com.canal.android.canal`) exposed a normal millisecond playback clock
  advancing approximately one second per second, with a published duration.
- YouTube TV (`com.google.android.youtube.tv`) exposed the same usable clock
  behavior and also published title/duration metadata.
- Disney+ (`com.disney.disneyplus`) exposed an active PLAYING MediaSession,
  title, episode subtitle and duration, but the published playback position in
  the observed sample was not a usable Android millisecond clock: values such as
  7, 14, 18, 10 and 24 were both far too small and non-monotonic. Disney+ is
  therefore session-visible but is NOT qualified as a synchronization clock by
  this test.

Current physical status is therefore: Prime qualified for play/pause/resume/seek;
Netflix qualified for play/pause; Canal+ and YouTube qualified for normal
playback clock progression; Disney+ MediaSession discovery/metadata works but its
published position is not yet usable for SceneVibe scheduling.

A follow-up physical investigation isolated the Sony notification-access issue.
The Android TV `NotificationAccessActivity` was correctly in the foreground and
showed SceneVibe, but the initial focus remained on the outer two-panel scroll
container instead of entering the enabled preference list. Remote D-pad, injected
D-pad and TAB input could not move that focus. A single development-only pointer
tap on the SceneVibe preference row moved interaction into the list; normal D-pad
navigation then worked for both the Interface and SceneVibe rows, and the user
could grant SceneVibe through the visible Android consent flow. SceneVibe then
reported `MediaSession access: granted`.

This is therefore recorded as a focus/navigation defect observed on the tested
Sony Bravia firmware, not as a failure of the Android notification-listener
permission mechanism. A follow-up test showed that entering Notification access
manually through **Settings -> Apps -> Special app access** preserves normal
D-pad navigation, while launching the notification-access activity directly
does not. The Companion now opens the top-level Apps settings and guides the user
through that working TV-native path. ADB remains development-only. Full evidence
and public platform references are recorded in
[docs/sony-notification-access-focus-2026-09-25.md](docs/sony-notification-access-focus-2026-09-25.md).

## v0.5.0 MediaSession-synced FinalTrack scheduler candidate

v0.5.0 connects the two previously separate POC paths:

`FinalTrack 1.1 -> sender-side assetRef resolution -> POST /track -> passive MediaSession clock -> TV scheduler -> rich overlay`.

The canonical FinalTrack remains unchanged. Machine-local paths are still held
only in the external asset map. The new PowerShell loader converts each
`schedule.idealStartSec` into a bounded runtime `startMs`, resolves optional
image assets, and sends a `scenevibe.track.v1` payload to the TV. No stopwatch
is used by the sender.

The runtime track requires an explicit Android package name so the scheduler
does not accidentally follow an unrelated active MediaSession. The Prime Video
default is `com.amazon.amazonvideo.livingroom`.

Load the existing FinalTrack 1.1 mixed fixture with:

~~~powershell
.\scripts\load-finaltrack-mediasession.ps1 `
  -TvIp "192.168.1.183" `
  -TrackPath ".\examples\finaltrack-media-tv-poc.json" `
  -AssetMapPath ".\examples\asset-map.json"
~~~

The TV returns `scenevibe.track.ack.v1` with status `loaded`. Scheduling then
depends only on the streaming application's published playback position.

The candidate scheduler uses an explicit deterministic seek policy:

- during PAUSED, BUFFERING or NONE states, it never renders a new comment;
- during normal PLAYING progression, a due comment is rendered from the TV clock;
- a forward jump greater than 5 seconds is treated as a seek and crossed
  comments are marked consumed instead of burst-rendered;
- a backward jump greater than 2 seconds re-arms comments at or after the new
  position so they may replay when the viewer watches that section again;
- a comment more than 2 seconds late is skipped rather than rendered out of
  context.

The scheduler remains passive: it reads snapshots only and contains no transport
control, media-button dispatch, screen capture, subtitle capture or streaming
application API integration.

### Physical v0.5.0 qualification — Sony Bravia + Prime Video, 25 September 2026

The complete MediaSession-synced FinalTrack path was then exercised physically
on the same Sony Bravia with Prime Video.

The TV health endpoint reported `version: 0.5.0` and
`synchronization: media-session-clock`. The mixed FinalTrack 1.1 fixture loaded
through `POST /track` with `status: loaded`, target package
`com.amazon.amazonvideo.livingroom`, and three runtime comments.

Observed passes:

1. During normal playback the three fixture events rendered at their expected
   media positions: text at approximately 0 s, image + text at approximately
   8 s, and text at approximately 18 s.
2. Pausing Prime froze scheduling: no new due comment was triggered while the
   MediaSession state remained paused, and scheduling resumed with playback.
3. A backward seek re-armed later comments, allowing them to be replayed
   repeatedly by revisiting their media positions.
4. A forward seek from before the 8 s event to beyond the 18 s event did not
   burst-render crossed comments. They were consumed according to the v0.5 seek
   policy.

This physically qualifies the complete path on the tested device:

`FinalTrack 1.1 -> assetRef resolution -> POST /track -> Prime MediaSession clock -> TV scheduler -> rich overlay`.

One limitation remains: if a commentary card is already visible when Prime is
paused, its renderer expiry timer continues and the card disappears after its
requested display duration. Scheduler triggering is pause-aware, but the current
renderer therefore does not yet implement the FinalTrack
`pauseFreezesDisplay: true` policy for an already-visible card.

The full test record is in
[docs/physical-qualification-v0.5-2026-09-25.md](docs/physical-qualification-v0.5-2026-09-25.md).

## v0.6.0 pairing and physical qualification

The TV activity now shows **Not paired / Pairing open / Paired**, current LAN
IPv4 when available, **Start pairing** and **Reset pairing**. First grant overlay
permission and start the overlay service. Start pairing displays a six-digit
code for 120 seconds; the fifth wrong code closes the window. The sender uses
the code once and keeps the returned token in memory. A later pairing replaces
the prior token, and Reset pairing revokes it immediately. Stopping the overlay
closes port 8765 but does not erase the token; uninstalling clears it.

~~~powershell
$tvIp = "192.168.1.183" # Replace with the address displayed on the TV
Copy-Item ".\examples\asset-map.example.json" "$env:TEMP\scenevibe-asset-map.json"
notepad "$env:TEMP\scenevibe-asset-map.json" # Set path to a real JPEG/PNG
$token = .\scripts\pair-tv.ps1 -TvIp $tvIp -Code "123456" # Replace with TV code
Invoke-RestMethod -Uri "http://${tvIp}:8765/health"
.\scripts\load-finaltrack-mediasession.ps1 -TvIp $tvIp -Token $token `
  -TrackPath ".\examples\finaltrack-media-tv-poc.json" `
  -AssetMapPath "$env:TEMP\scenevibe-asset-map.json"
~~~

The sample asset map is only a template: bind its image reference to a real
local JPEG/PNG before loading. Direct commentary sender:

~~~powershell
.\scripts\send-media-commentary.ps1 -TvIp $tvIp -Token $token `
  -ImagePath "C:\images\example.jpg"
~~~

### Physical v0.6.0 qualification — Sony Bravia + Prime Video, 25 September 2026

The v0.6 APK was exercised on the physical Sony Bravia after a clean reinstall.
The Sony-safe Settings -> Apps -> Special app access -> Notification access
route remained navigable with the remote and MediaSession access reported
granted.

Observed v0.6 passes:

1. Start pairing displayed a six-digit code. A LAN POST to `/pair` returned
   `scenevibe.pair.ack.v1`, status `paired`, a deviceId and a 43-character
   Base64URL token; the TV state changed to paired.
2. POST `/commentary` without a Bearer token returned HTTP 401 and rendered
   nothing on the TV.
3. A valid authenticated commentary returned
   `scenevibe.commentary.ack.v1`, status `rendered`, and was visibly rendered.
4. An authenticated two-comment `scenevibe.track.v1` fixture loaded with
   `status: loaded`, target package `com.amazon.amazonvideo.livingroom`.
5. With `pauseFreezesDisplay: true`, a card with a nominal 10-second lifetime
   was paused while visible. It remained visible for more than 15 seconds of
   paused wall time. After playback resumed, it remained for its unconsumed
   visible time and then disappeared normally.

This closes the v0.5 already-visible-card pause limitation on the tested Sony
Bravia with Prime Video.

The physical run did **not** exercise Reset pairing / old-token revocation,
five invalid pairing attempts, pairing-window expiry, token replacement, v0.6
image rendering, v0.6 forward/backward seek regression checks, or Netflix /
Disney+ v0.6 playback. Those paths must not be described as physically
qualified by this run.

The complete v0.6 physical record is in
[docs/physical-qualification-v0.6-2026-09-25.md](docs/physical-qualification-v0.6-2026-09-25.md).

Automated checks cover the broader token gate, protocol, build, lint and timer
arithmetic. The v0.5 physical record remains the evidence for its previously
qualified mixed-media and seek behavior.

The full endpoint and error contract for the future "Send to TV" client is
in [docs/lan-pairing-protocol-v1.md](docs/lan-pairing-protocol-v1.md).

## Diagnosis and cleanup

~~~sh
adb logcat -s SceneVibePoc:D AndroidRuntime:E
adb shell appops get com.scenevibe.tvcompanionpoc SYSTEM_ALERT_WINDOW
adb shell dumpsys window
adb uninstall com.scenevibe.tvcompanionpoc
~~~

Logs cover service creation/destruction, foreground start, permission loss,
WindowManager add/update/remove, attach/detach, commentary display/expiry,
errors and the 30-second heartbeat.

Android does not notify the app merely because another application or compositor
conceals a still-attached overlay, so physical observation remains part of the
qualification procedure.

## Outbound SceneVibe Cloud candidate (physical qualification pending)

This branch adds a **separate** HTTPS cloud route alongside the v0.6 LAN pairing/receiver. Build the Android APK with `SCENEVIBE_CLOUD_ORIGIN=https://<your-domain>` (an exact HTTPS host on port 443); without it, the v0.6 LAN app builds and the UI reports **Cloud: Not configured**. Configure PostgreSQL and the server using the monorepo's `docs/tv-cloud-control-plane-v1.md`; no domain or paid cloud instance is included.

Start the overlay, then select **Connect to SceneVibe Cloud** on TV. A six-digit code appears on the TV after a successful outbound activation request. Enter that code in Chrome's **Connect TV** section. The TV detects the claim by conditional HTTPS polling while its foreground overlay service runs. The device credential remains in app-private Android storage and is distinct from the LAN bearer token and Chrome controller token. **Disconnect cloud** stops polling locally but retains the private device proof for later TV-initiated reconnection; cached commentary survives. It does not revoke the server token. Account-level revocation remains future work.

Each cloud revision uses the same track parser as LAN; the TV commits the validated text-only runtime JSON and revision before loading its MediaSession scheduler and sending a correlated ACK. At service restart, it restores the cached track before any network request, even with the desktop offline. This does not qualify the physical Sony/Prime Video behavior. Test: queue a real Prime track in Chrome, observe Loaded on TV, close Chrome and power off/disconnect the PC, verify normal playback and commentary, then stop/restart SceneVibe while PC remains off, test pause/resume and both seek directions. The TV never controls Prime Video. There is no phone, boot receiver or cloud playback timer in this candidate.
