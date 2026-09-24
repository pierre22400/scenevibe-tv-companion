# SceneVibe TV Companion POC

**Purpose:** verify on a physical Android TV / Google TV that SceneVibe can keep
an independent, transparent commentary layer above an official streaming app
without controlling, modifying, capturing or replacing the video stream.

This repository is an installable technical POC, not the final SceneVibe TV
application.

## Current state

The current candidate build is **v0.3.0**.

- A TV-friendly activity checks the user-granted **Display over other apps**
  capability and starts/stops the overlay.
- A foreground service owns the overlay after the activity is left.
- A noninteractive TYPE_APPLICATION_OVERLAY window uses
  FLAG_NOT_FOCUSABLE | FLAG_NOT_TOUCHABLE, so the streaming app keeps remote
  focus.
- A bounded LAN HTTP server listens on TV port **8765** only while the overlay
  service runs.
- POST /commentary renders transient SceneVibe text and can optionally render a bounded JPEG/PNG image above it.
- GET /health reports service readiness.
- The newest commentary owns its expiry: when a new comment replaces an older
  one, the previous pending expiry is cancelled before the new duration starts.

The app has no capture, accessibility, microphone or media permission, no boot
receiver, no Cast receiver and no streaming-account integration. It does use
android.permission.INTERNET solely for the current LAN POC transport.

## Security boundary

The v0.3.x transport is intentionally minimal and is **not** the final
pairing/security design. It has no authentication and must be used only on a
trusted local network during development.

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
gradle :app:assembleDebug :app:lintDebug --stacktrace
~~~

The debug APK is:

~~~text
app/build/outputs/apk/debug/app-debug.apk
~~~

The **Android debug APK** GitHub Actions workflow runs the same contract tests,
Android build and lint on pushes to main, then uploads
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

v0.3.0 returns a JSON object with:

- type: scenevibe.health.v1
- status: ready
- version: 0.3.0
- media: inline-image

### Commentary

~~~http
POST /commentary
Content-Type: application/json
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

Example PowerShell request, replacing the IP with the TV's current LAN address:

~~~powershell
Invoke-RestMethod -Method Post -Uri "http://192.168.1.183:8765/commentary" `
  -ContentType "application/json" `
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
.\scripts\send-media-commentary.ps1 -TvIp "192.168.1.183" -ImagePath "C:\Users\DENIS\Downloads\maison.jpg" -Text "SceneVibe — image overlay test" -DurationMs 10000
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

Physical qualification of image rendering is still required before v0.3.0 is
declared qualified.

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
