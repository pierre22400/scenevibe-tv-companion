# SceneVibe TV Companion POC

**Purpose:** test on a physical Android TV / Google TV whether an independent,
transparent Android window can remain visible while an official streaming app
plays video. This is an installable experiment, not a video player or the final
SceneVibe TV application.

## What the app does

- A TV remote friendly activity checks `Settings.canDrawOverlays()` and opens
  Android's overlay settings. It starts a top-right or bottom-right test badge
  and can stop it.
- A user-started foreground service owns the overlay for as long as Android
  permits it, including after leaving the activity. A persistent notification
  returns to the controls. Android may still kill the service; `START_STICKY`
  requests recovery and the last position is restored if a restart occurs.
- A separate renderer uses one `TYPE_APPLICATION_OVERLAY` window with
  `FLAG_NOT_FOCUSABLE | FLAG_NOT_TOUCHABLE`, overall alpha `0.75`, a small
  translucent badge, and a clock. It never intercepts remote keys. The badge
  changes only its position when a second Start button is pressed.

The app has **no internet, capture, accessibility, microphone or media
permission**, no boot receiver, no Cast, no streaming account integration and
no code in another app. Android's permission page is user controlled. It does
not grant a permission silently.

## Open source review before implementation

Reviewed the default branches on 24 September 2026:

| Project | License and implementation | POC decision |
| --- | --- | --- |
| [TvOverlay](https://github.com/gugutab/TvOverlay) | No `LICENSE` file in the repository. It contains APKs, JSON examples and Home Assistant/Postman samples, but no Android source code. Its README describes `SYSTEM_ALERT_WINDOW`, an ADB `appops` fallback on some TVs, positioning and REST/MQTT control. Foreground service, `WindowManager` flags and remote focus cannot be verified from source. | No code copied. The permission troubleshooting and small-corner display inform the test procedure. REST/MQTT is unnecessary here. |
| [TVCompanion](https://github.com/avnishkirnalli/TVCompanion) | MIT; Java Android TV host plus Android controller. A foreground `CompanionService` serves TCP and advertises via `NsdManager`; a separate `ScreenStreamingService` uses MediaProjection. It declares `SYSTEM_ALERT_WINDOW` but the reviewed host service does not implement the small visual overlay required here. The TV manifest exposes a Leanback launcher. | No code copied. Separating the service from the activity and NSD/TCP is a possible future direction; screen capture, boot start and network code are explicitly excluded. |
| [PipTV](https://github.com/juniorbarrigana/PipTV) | MIT; Kotlin, Compose, Media3, WebView and an `AccessibilityService`. Its PiP window uses `TYPE_ACCESSIBILITY_OVERLAY`, `WindowManager`, key interception and focus recovery; its manifest also declares `SYSTEM_ALERT_WINDOW`. The README documents TV remote handling. | No code copied. Its focus handling serves an interactive player; this badge must not take focus, so it uses `TYPE_APPLICATION_OVERLAY` and no accessibility service. |

No third-party source is included, so these licenses introduce no code
redistribution obligation in this repository. The implementation uses Android
framework APIs directly. A future phone-to-TV connection may study
TVCompanion's NSD discovery and TCP acknowledgement, but none exists here.

## Build and automated checks

Requires JDK 17, Android SDK Platform 35, Build Tools 35.x and Gradle **8.9**.
The Android Gradle plugin is pinned to **8.7.3**. There is no Gradle wrapper
binary in this minimal repository. With `ANDROID_HOME` or `ANDROID_SDK_ROOT`
set and `gradle` 8.9 available:

```sh
python3 -m unittest discover -s tests
gradle :app:assembleDebug :app:lintDebug --stacktrace
```

The debug APK is `app/build/outputs/apk/debug/app-debug.apk`. The
`Android debug APK` GitHub Actions workflow runs the same checks and uploads
that file as the `scenevibe-tv-companion-poc-debug` artifact. A successful
build only proves packaging and static checks; it cannot prove the overlay
works on Prime Video or that a TV exposes the permission setting.

Target: Android API **26–35** (Android 8.0+; later releases need device tests).
`TYPE_APPLICATION_OVERLAY` and notification channels exist from API 26.
The foreground service declares the `specialUse` type and permission required
when targeting Android 14+, with its narrow experimental use stated in the
manifest. This sideloaded POC makes no Play Store acceptance claim. Android TV
and Google TV share the Android APIs used here; launcher, settings, background
management and app-specific overlay restrictions can vary by model and OS.
No Sony-only APIs are used.

## Install and grant the permission on a TV

Connect ADB to the TV as documented for its developer settings, then:

```sh
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.scenevibe.tvcompanionpoc/.MainActivity
```

On the TV, select **Open overlay permission settings**, locate SceneVibe TV
Companion POC and allow **Display over other apps**. Return to the app: its
status must read **granted**. Android 11+ may show a general list rather than
the package page. If a TV does not expose the setting, use this **development
fallback only** after deciding to grant the permission:

```sh
adb shell appops set com.scenevibe.tvcompanionpoc SYSTEM_ALERT_WINDOW allow
adb shell appops get com.scenevibe.tvcompanionpoc SYSTEM_ALERT_WINDOW
```

Reopen the app and check its status; the `appops` result alone is not proof
that `Settings.canDrawOverlays()` returns true on this particular TV. If the
service cannot start, inspect logcat. To revoke the development grant, use
`adb shell appops set com.scenevibe.tvcompanionpoc SYSTEM_ALERT_WINDOW default`
or the TV settings. Do not use ADB as the intended consumer flow.

## Physical qualification protocol

1. Note TV model, Android/Google TV version, firmware, Prime Video version and
   whether permission was granted in Settings or by ADB.
2. In SceneVibe, start **top right**. Confirm the clock advances. Press Home,
   navigate with the remote, and check the badge remains visible.
3. Open official **Prime Video**, start a normal video and watch for at least
   two minutes. Record whether the badge is visible over the *video image*
   (not merely over menus), whether video and audio remain normal, whether
   Play/Pause and D-pad still control Prime Video, and any flicker/blanking.
4. Reopen SceneVibe and switch to **bottom right**. Repeat on the same video;
   this distinguishes corner policies and subtitle obstruction. Select
   **Stop overlay** and confirm the badge disappears from Home and Prime Video.
5. Repeat steps 2–4 separately with official **Netflix** and **Disney+**,
   recording results per app. No result for one app establishes another.

Record a pass **only** if the badge remains visible over the playing video
while sound, picture and remote controls work normally. A service heartbeat
does not prove that Android composited the badge over protected video. If
menus work but the video hides the badge, record that precise failure. If
permission or foreground service setup fails, record that as a separate
blocker. This repository has no physical TV result yet.

## Diagnosis and cleanup

```sh
adb logcat -s SceneVibePoc:D AndroidRuntime:E
adb shell appops get com.scenevibe.tvcompanionpoc SYSTEM_ALERT_WINDOW
adb shell dumpsys window
adb uninstall com.scenevibe.tvcompanionpoc
```

Logs cover service creation/destruction, foreground start, permission loss,
WindowManager add/update/remove, attach/detach, errors and a heartbeat every
30 seconds. Android does not notify this app when another application or the
compositor merely *conceals* a still-attached overlay: observation of the TV
image is essential. The service does not force itself above system UI or
another app's secure/special surfaces. Some manufacturers do not expose the
overlay permission UI, and Android may remove a foreground service under
resource pressure. Those limitations remain to be tested on the Sony Bravia.

