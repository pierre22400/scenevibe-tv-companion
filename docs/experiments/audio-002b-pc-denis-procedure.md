# SceneVibe TV Companion POC - KIRO AUDIO 002B - PC DENIS Android-SDK qualification procedure

**Status: PROCEDURE TO EXECUTE LATER on a Windows machine ("PC DENIS") that has the Android SDK. It was NOT executed in this sandbox (SDK absent -> BLOCKED-ENV). It builds a DEBUG-signed experimental APK only.**
**Nature: reproducible local build / qualification steps for the Android-SDK verifications that are BLOCKED-ENV in the sandbox. No signing secret and no M5 keystore are ever requested, copied, or embedded.**

> This procedure covers only the LOCAL experimental qualification: Android compile, Android unit tests, lint, `assembleAudioexp`, the `applicationId` check, and the no-M5-signature check. It does NOT install on the Sony and does NOT run any signed / release build. The Sony physical steps are a separate, future protocol under a separate owner GO (`audio-002b-sony-handoff-addendum.md`).

## 1. Preconditions on PC DENIS

| Item | Value / requirement |
| --- | --- |
| OS | Windows (PowerShell available) |
| JDK | JDK 17 (same major as the sandbox JVM run; `JAVA_HOME` set) |
| Android SDK | `platforms;android-35` and `build-tools;34.0.0` installed; `ANDROID_HOME` / `ANDROID_SDK_ROOT` set |
| SDK command-line tools | `aapt` / `aapt2`, `apkanalyzer`, `apksigner` on `PATH` (from `build-tools` / `cmdline-tools`) |
| Repo | this branch `work/kiro-audio-002-local-integration-001` checked out at commit `394db81` (or later as reported by the orchestrator) |
| Gradle | the project build is driven by Gradle; if no `gradlew` wrapper is committed, use a locally installed Gradle 8.14.x |
| Signing | DISPOSABLE default debug signing ONLY. Do NOT reference, copy, or embed the M5 release keystore or any signing secret. |

The experimental variant is the `audioexp` build type in `app/build.gradle` (`initWith debug`, `applicationIdSuffix ".audioexp"`, `versionNameSuffix "-audio002-exp"`, `debuggable true`, `signingConfig signingConfigs.debug`). Its Gradle tasks are named with the `Audioexp` build-type segment (for example `assembleAudioexp`).

## 2. Verify the bundled cue before trusting the build

```powershell
Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass
# audio-002b: verify the bundled cue identity before any Android build.
# Never requests, copies, or embeds any signing secret; never touches the M5 keystore.
$cue = "app\src\main\res\raw\scenevibe_cue.aac"
$hash = (Get-FileHash $cue -Algorithm SHA256).Hash
Write-Host ("cue SHA-256: " + $hash)
Write-Host ("cue bytes:   " + (Get-Item $cue).Length)
# expect SHA-256 96EB915315B01EBB624A5802440056CAB30201F5C135ACED55EE5276A6A381D7
# expect bytes   7032
```

## 3. JVM re-confirmation (optional but recommended)

Re-run the FEAT-002 Android-free JVM suite on PC DENIS (adjust the classpath to the local JUnit 4.13.2 + hamcrest-core 1.3 jars). Expect the same result as the sandbox: a clean `javac -Xlint:all` compile and `OK (62 tests)` from `org.junit.runner.JUnitCore` over the seven audio test classes. The exact command list is in `audio-002b-test-report.md` §2.

## 4. Android-SDK qualification steps

Run from the repository root. These are the steps that are BLOCKED-ENV in the sandbox.

```powershell
Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass
# audio-002b: Android-SDK local qualification of the EXPERIMENTAL audioexp variant.
# DEBUG signing only. Never requests, copies, or embeds any signing secret; never
# touches the M5 release keystore; never runs a signed/release build; never installs
# on the Sony.
$ErrorActionPreference = "Stop"

# Pick the Gradle entry point: prefer a committed wrapper, else a local gradle.
$gradle = if (Test-Path ".\gradlew.bat") { ".\gradlew.bat" } else { "gradle" }

# 4.1 Android compilation of the experimental variant.
& $gradle :app:compileAudioexpDebugJavaWithJavac

# 4.2 Android unit tests (Robolectric) for the variant.
& $gradle :app:testAudioexpDebugUnitTest

# 4.3 Lint on the variant.
& $gradle :app:lintAudioexpDebug

# 4.4 Assemble the experimental APK (DEBUG signing only).
& $gradle :app:assembleAudioexpDebug

# 4.5 Locate the produced APK and record its identity.
$apk = Get-ChildItem -Recurse app\build\outputs\apk\audioexp |
       Where-Object { $_.Name -like "*.apk" } | Select-Object -First 1
Write-Host ("APK: " + $apk.FullName)
Write-Host ("APK SHA-256: " + (Get-FileHash $apk.FullName -Algorithm SHA256).Hash)
```

Record the produced APK SHA-256 from the output of step 4.5. (It does NOT exist until the build above actually succeeds; it is never fabricated in advance.)

## 5. applicationId verification

The experimental APK MUST carry `applicationId = com.scenevibe.tvcompanionpoc.audioexp` (distinct from the stable `com.scenevibe.tvcompanionpoc`).

```powershell
Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass
# audio-002b: confirm the experimental applicationId. Never touches the M5 keystore.
$apk = (Get-ChildItem -Recurse app\build\outputs\apk\audioexp |
        Where-Object { $_.Name -like "*.apk" } | Select-Object -First 1).FullName

# Preferred: apkanalyzer (from the Android SDK command-line tools).
apkanalyzer manifest application-id $apk
# expect exactly: com.scenevibe.tvcompanionpoc.audioexp

# Alternative: aapt badging.
aapt dump badging $apk | Select-String "package: name="
# expect: package: name='com.scenevibe.tvcompanionpoc.audioexp' ...
```

PASS only if the id is exactly `com.scenevibe.tvcompanionpoc.audioexp`. If it reads `com.scenevibe.tvcompanionpoc` (no `.audioexp` suffix), the wrong build type was assembled - FAIL and stop.

## 6. No-M5-signature verification

The experimental APK MUST be signed with the disposable DEBUG key only, NEVER with the M5 release keystore.

```powershell
Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass
# audio-002b: confirm the APK is DEBUG-signed, NOT M5-signed. This only READS the
# certificate embedded in the already-built APK; it never references, loads, copies,
# or embeds the M5 keystore or any signing secret.
$apk = (Get-ChildItem -Recurse app\build\outputs\apk\audioexp |
        Where-Object { $_.Name -like "*.apk" } | Select-Object -First 1).FullName

apksigner verify --print-certs $apk
# Expect the Android debug certificate, whose subject is:
#   CN=Android Debug, O=Android, C=US
# PASS: the signer is the Android debug cert (disposable debug signing).
# FAIL: any certificate matching the production/M5 release identity, or any prompt
#       asking for the M5 keystore or a signing secret -> abort, do NOT proceed.
```

PASS only if the printed signer certificate is the Android debug certificate (`CN=Android Debug, O=Android, C=US`). Any match to the production / M5 release signer is a FAIL. Never supply the M5 keystore to any step.

## 7. Recording the results

For each step record PASS / FAIL plus the raw tool line:

| Step | Expectation | Record |
| --- | --- | --- |
| 2 cue SHA-256 | `96eb9153...a381d7`, 7032 bytes | hash + byte count |
| 3 JVM suite (optional) | `OK (62 tests)` | run output |
| 4.1 Android compile | success | task result |
| 4.2 Android unit tests | success | task result |
| 4.3 lint | success (no new errors) | task result |
| 4.4 assembleAudioexp | APK produced | path |
| 4.5 APK SHA-256 | recorded at build time | hash |
| 5 applicationId | `com.scenevibe.tvcompanionpoc.audioexp` | tool line |
| 6 signer | `CN=Android Debug, O=Android, C=US` (NOT M5) | cert line |

If every step is PASS, the AUDIO 002B §7 verdict may be revisited together with the integrated-software SOFTWARE PASS. Even then, physical Sony qualification remains a separate, future protocol under a separate owner GO (`audio-002b-sony-handoff-addendum.md`); this procedure does NOT install anything on the Sony.

## 8. Prohibitions (reminder)

No signed / release build; no use of the M5 keystore or any signing secret; no bump of the stable `versionCode` or `applicationId`; no install on the Sony from this procedure; no Prime transport, no `AudioFocus`, no global / system volume change (these are software prohibitions carried from the mandate and remain in force on-device); no GitHub merge of PR #17 / #27 / #28 / #29. Every `.ps1` or inline PowerShell block in this procedure begins with the exact line `Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass`.
