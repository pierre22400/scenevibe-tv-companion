# SceneVibe TV 0.8.2 - release-hardening qualification

Date: 2026-10-01  
Branch: `kiro/tv-release-hardening-0.8.2-001`  
Baseline (product head before this cycle): `0b6f1f8398b49d352b9d0046f1bb020d537098e7` ("Merge SceneVibe TV 0.8.1 recovery hardening")  
Qualification APK: `0.8.2-tv-release-hardening`, versionCode 12

## Purpose

Stabilize the release architecture of the SceneVibe TV Companion POC without changing any
qualified 0.8.1 runtime behavior. This cycle:

- introduces an explicit, non-debuggable **release** build variant (unsigned at build time;
  signed post-build by `apksigner` in CI);
- adds a dedicated, secret-gated **release** CI chain separate from the debug chain;
- documents the stable self-managed signing architecture and the signature-continuity protocol;
- audits the app against Android 15 / API 35 behavior changes (the app already targets 35);
- adds an API 35 emulator smoke test that honestly qualifies platform invariants only;
- bumps the version to `0.8.2-tv-release-hardening` (versionCode 12).

This document records, per honest status category, exactly what was qualified where. CI and
emulator results are never represented as physical qualification.

## Status categories

The results below are grouped under these exact labels:

- **AUTOMATED PASS** - qualified by automation (local sandbox gates and/or CI quality gates).
- **EMULATOR PASS** - qualified on an API 35 emulator (platform invariants only, not Android TV).
- **PHYSICAL PASS** - qualified on real hardware this cycle.
- **HUMAN ACTION REQUIRED** - a human/physical step that must still be performed.
- **NOT YET QUALIFIED** - not qualified by any path yet.

---

## AUTOMATED PASS

Locally qualified in the sandbox (JDK 17 at `/root/.local/share/mise/installs/java/17.0.2`,
Android SDK 35 at `/opt/android-sdk`). Gradle version is noted per gate because the sandbox
toolchain is version-sensitive (see LIMITATIONS).

| Gate | Result | Gradle version |
| --- | --- | --- |
| Python boundary tests (`python3 -m unittest discover -s tests`) | PASS - 12/12 | n/a (CPython 3) |
| `:app:assembleDebug` | PASS - produces `app/build/outputs/apk/debug/app-debug.apk` | 8.14.5 (PATH, via mise) |
| `:app:testDebugUnitTest` (JUnit 4.13.2) | PASS | 8.14.5 (PATH, via mise) |
| LAN DEV build (`SCENEVIBE_ENABLE_LAN_DEV=true`) | PASS - compiles | 8.14.5 |
| Cloud qualification build (`SCENEVIBE_CLOUD_ORIGIN=https://interface-scenevibe-3wxg.vercel.app`) | PASS - origin embedded in `BuildConfig.CLOUD_ORIGIN` | 8.14.5 |
| `:app:assembleRelease` | PASS - produces **unsigned, non-debuggable** `app/build/outputs/apk/release/app-release-unsigned.apk` | 8.9 |
| Release non-debuggable validation (`aapt dump badging`) | PASS - **no** `application-debuggable` flag present | 8.9 build-tools 35.0.0 |
| Lint (`:app:lintDebug`, `:app:lintVitalRelease`) | PASS | 8.9 |

Version bump re-verification (post-bump, this cycle):

- `aapt dump badging app-release-unsigned.apk` reports
  `package: name='com.scenevibe.tvcompanionpoc' versionCode='12' versionName='0.8.2-tv-release-hardening'`
  and still shows **no** `application-debuggable` flag.
- `MainActivity` renders `BuildConfig.VERSION_NAME` in its title ("SceneVibe\nTV Companion " +
  `BuildConfig.VERSION_NAME`); the new version string surfaces there automatically and both
  `assembleDebug` and `assembleRelease` compile cleanly, so the bump is confirmed to compile.

**Lint note.** Lint is qualified under **gradle 8.9** (the version CI uses). Under the sandbox's
default gradle 8.14.5 lint fails with an IntelliJ/UAST `MessageBusImpl` "Already disposed"
(`AlreadyDisposedException`) error inside `AndroidLintWorkAction`. This is a lint-runtime /
Gradle-version incompatibility of the sandbox toolchain, **not** a code defect, and no lint rule
was modified or suppressed. CI runs gradle 8.9, matching the version under which lint is green.
`assembleRelease` runs `lintVital`, so it is also built under gradle 8.9 for the same reason;
`assembleDebug` and `testDebugUnitTest` run fine under 8.14.5.

### Release build variant (FEAT-002)

`app/build.gradle` declares an explicit `release` buildType: `debuggable false`,
`minifyEnabled false`, `shrinkResources false`, `signingConfig null`. Minify and shrink are
intentionally OFF so the qualified 0.8.1 runtime behavior stays byte-for-byte unchanged except
for the debuggable flag and signing. No keystore path, store password, key alias, or key
password is referenced anywhere in version-controlled gradle. `assembleRelease` therefore emits
an **unsigned** APK; the real signature is applied by `apksigner` in CI.

### Release CI chain (FEAT-003) - quality gates portion

`.github/workflows/android-release.yml` is `workflow_dispatch`-only, builds Consumer Cloud Mode
(`SCENEVIBE_ENABLE_LAN_DEV=false`, `SCENEVIBE_CLOUD_ORIGIN=https://interface-scenevibe-3wxg.vercel.app`),
runs the Python boundary tests and `:app:testDebugUnitTest` first so a release never ships from a
red tree, then `:app:assembleRelease`, then validates non-debuggable via `aapt`. The workflow
YAML is well-formed (parsed with PyYAML). It contains **no** Play Store publish step, **no** auto
tag, and **no** auto GitHub release. The signing portion is CI-only / secret-gated (see
NOT YET QUALIFIED and HUMAN ACTION REQUIRED).

### Android 15 / API 35 audit (FEAT-004)

Outcome: **ZERO** app code or manifest changes required. The app already compiles and targets
compileSdk/targetSdk 35, and every surface affected by Android 15 behavior changes was already
compliant. Audit grounded in the official Android 15 behavior-changes documentation.

- **OverlayService `specialUse` FGS started from `BootReceiver`** - KEEP. Android 15 forbids
  `BOOT_COMPLETED` receivers from starting these FGS types: `dataSync`, `camera`,
  `mediaPlayback`, `phoneCall`, `mediaProjection`, `microphone`. OverlayService is
  `foregroundServiceType=specialUse`, which is **not** in that restricted list, so the
  boot-prepare `startForegroundService` path remains **permitted** on API 35. `BootReceiver`
  already wraps the start fail-closed, so even a future platform exception cannot crash boot.
- **SYSTEM_ALERT_WINDOW + background-FGS narrowing** - KEEP (no ordering change). The Android 15
  narrowing applies to **background** FGS starts under the SAW exemption (which require an
  already-visible overlay window first). This app never relies on that background-SAW path: the
  FGS is started either from an explicit **foreground** user Activity action in
  `MainActivity.startOverlay()` (guarded by `Settings.canDrawOverlays()` before
  `startForegroundService()`), or from the `BOOT_COMPLETED` exemption path in `BootReceiver`.
  `OverlayService.onStartCommand()` independently re-checks `Settings.canDrawOverlays()` and
  `stopSelf()`s if absent, and the overlay window is drawn lazily (only when a comment is due).
  Because no start depends on the background-SAW exemption, no "draw overlay before starting FGS"
  reordering is required.
- **BootReceiver** - KEEP. Listens only to `BOOT_COMPLETED` and `MY_PACKAGE_REPLACED`, is not
  `directBootAware` and not registered for `LOCKED_BOOT_COMPLETED`, `exported=true` (required to
  receive `BOOT_COMPLETED`), fail-closed body. Unaffected by Android 15.
- **NotificationListenerService / MediaSession access** - KEEP. No relevant Android 15 behavior
  change; the listener remains `exported=false`, permission-gated, and passive.
- **Exported components** - KEEP. `MainActivity exported=true` (leanback launcher),
  `DiagnosticsActivity`/`OverlayService`/`MediaSessionAccessService` `exported=false`,
  `BootReceiver exported=true` (required for `BOOT_COMPLETED`). All already satisfy the API 31+
  explicit-exported rule.
- **PendingIntent mutability** - KEEP. The only `PendingIntent` (notification contentIntent)
  already uses `FLAG_UPDATE_CURRENT | FLAG_IMMUTABLE`.
- **Other API-35 items** - N/A with reason. Edge-to-edge enforcement: this is a leanback TV POC,
  not an edge-to-edge content app, and TV has no status/navigation-bar inset model of concern.
  16 KB page size: affects apps with native (NDK/`.so`) libraries; this app is pure Java/AndroidX
  with no NDK code. DND / global-state and other media items: the app sends no transport controls
  and mutates no global interruption-filter state.

**Justification for zero changes:** for an app that already targets API 35, the correct audit
outcome can be zero code changes. Each affected surface was checked against the specific
Android 15 restriction and found already compliant; no cosmetic refactor was performed and no
interdicted file (`MediaSyncedTrackScheduler`, `MediaIdentityMatcher`, etc.) was touched.

---

## EMULATOR PASS

`.github/workflows/android-15-smoke.yml` runs on an **API 35 emulator** in CI
(`reactivecircus/android-emulator-runner@v2`, `api-level 35`) on `ubuntu-latest` with
`gradle 8.9`. YAML is well-formed (parsed with PyYAML). This is a CI gate; it was not run in the
sandbox (no KVM/AVD locally).

**Image / scope limitation (stated explicitly).** No officially usable Android **TV** (leanback)
system image exists for API 35, so the workflow boots a **standard** API 35 `google_apis`
`x86_64` image. The workflow comments and job name declare that this qualifies **Android 15
platform invariants only** and is explicitly **NOT Android TV** and **NOT** a physical
qualification. The script checks `pm list features` for `android.software.leanback` and emits a
HUMAN/PHYSICAL notice because that feature is absent on the standard image.

What the smoke workflow actually verifies in CI (job fails on a real failure):

- the Cloud-mode debug APK build artifact is present;
- `adb install -r` of the APK succeeds;
- the package is present (`pm list packages | grep com.scenevibe.tvcompanionpoc`);
- `am start -n com.scenevibe.tvcompanionpoc/.MainActivity` launches;
- `MainActivity` is present in the `dumpsys activity activities` stack;
- no `AndroidRuntime` / `FATAL EXCEPTION` attributed to the app in logcat;
- a weak install-persistence lower bound after `am force-stop`.

Items the smoke workflow marks **HUMAN/PHYSICAL REQUIRED** (never printed as PASS): Android TV
leanback runtime (standard image lacks leanback); the OverlayService `specialUse` FGS actually
started with "FGS alive" and the overlay window visible; the real `BOOT_COMPLETED` autostart
path; a true device reboot. A `note_human`/`fail`/`ok` helper set guarantees no "PASS" is printed
for a check that was not actually performed.

> No result in this section is a physical or Android TV qualification. The emulator qualifies
> Android 15 platform invariants on a standard (non-TV) image only.

---

## PHYSICAL PASS

**NONE performed in this cycle.** No new physical qualification was run for 0.8.2.

The prior physical record remains the **0.8.1 Sony BRAVIA (Android TV 12) recovery
qualification** documented in [`docs/tv-recovery-0.8.1.md`](tv-recovery-0.8.1.md). That record is
**unchanged** by this cycle. It qualified the recovery defect fix on real hardware; it did **not**
qualify Android 15 / API 35 runtime, the new release signing chain, or the signature-continuity
protocol, all of which remain HUMAN ACTION REQUIRED / NOT YET QUALIFIED below.

Nothing from the AUTOMATED PASS or EMULATOR PASS sections above counts as a physical
qualification.

---

## HUMAN ACTION REQUIRED

These steps are human/physical and were **not** performed by CI or by this cycle.

1. **Configure the four GitHub signing secrets** per [`docs/stable-signing.md`](stable-signing.md):
   `SCENEVIBE_ANDROID_KEYSTORE_BASE64`, `SCENEVIBE_ANDROID_KEYSTORE_PASSWORD`,
   `SCENEVIBE_ANDROID_KEY_ALIAS`, `SCENEVIBE_ANDROID_KEY_PASSWORD`. Until all four exist, the
   release workflow fails fast by design (see NOT YET QUALIFIED). Generate the stable key once
   locally with the keytool procedure in that doc, store the `.jks` off-repo with a backup, keep
   the reference certificate SHA-256 fingerprint off-repo as the continuity anchor, and never
   place any key, password, or keystore in the repo, a ticket, a PR, or a log.

2. **Run the signature-continuity protocol on the Sony** per
   [`docs/signature-continuity-protocol.md`](signature-continuity-protocol.md): install the first
   stable-signed APK, then upgrade with `adb install -r`, confirming the upgrade completes without
   `INSTALL_FAILED_UPDATE_INCOMPATIBLE`, and pre-compare the two APKs' signing certificates (same
   SHA-256 digest = same key = compatible upgrade). The exact commands are in NEXT PHYSICAL
   QUALIFICATION COMMANDS below.

3. **Physically qualify API 35 runtime on a real Android 15 device**: the OverlayService
   `specialUse` FGS actually started and alive, the overlay window actually visible (via the real
   D-pad "Start SceneVibe" user action), the real `BOOT_COMPLETED` autostart path (opt-in ON +
   overlay/MediaSession access granted + genuine reboot), and behavior across a true device
   reboot. These are the smoke-test items marked HUMAN/PHYSICAL REQUIRED.

4. **Android TV leanback runtime** must be qualified on real Android TV hardware; the standard
   API 35 emulator image cannot stand in for it.

Separate future decisions (not blockers for this cycle, not configured here): a Google Play
**upload key** and **Play App Signing**; stale historical TV cleanup via the account control
plane; production Clerk live-instance/domain cutover.

---

## NOT YET QUALIFIED

- **Signed release artifact.** Cannot be produced until the four GitHub signing secrets exist. The
  signing/verification path (keystore decode, `apksigner` sign, `apksigner verify --print-certs`,
  certificate-fingerprint extraction, signed-APK artifact upload) is **CI-only and secret-gated**
  and was **not** run in the sandbox because the four secrets are absent there. By design,
  `android-release.yml` **fails explicitly** (`::error::` + `exit 1`) before any build when the
  secrets are missing - a release is never produced unsigned.
- **Physical API 35 runtime.** No real Android 15 device run occurred this cycle (see
  HUMAN ACTION REQUIRED items 3 and 4).

---

## Signing architecture decision

- **This cycle:** a **self-managed stable signing key** for **sideload / qualification** APKs.
  The key lives entirely off-repo; CI holds it as the four GitHub repository secrets and
  `apksigner` applies the signature **post-build** to the unsigned, non-debuggable release APK.
  No `signingConfig` is embedded in gradle, so no key material is required at Gradle configure
  time and none is version-controlled.
- **Google Play upload key** and **Google Play App Signing** are a **separate future decision**
  and are **NOT configured** in this cycle. The self-managed key here is for sideload /
  qualification distribution, not a Play upload key. These three concepts (our
  sideload/qualification key vs a Play upload key vs Play App Signing) are kept distinct in
  [`docs/stable-signing.md`](stable-signing.md); this cycle commits only to the first.
- **Continuity** is anchored by the reference certificate SHA-256 fingerprint kept off-repo. The
  signature-continuity protocol in [`docs/signature-continuity-protocol.md`](signature-continuity-protocol.md)
  verifies that a future APK is signed by the same key before attempting an in-place upgrade.

## Limitations

- The sandbox gradle is 8.14.5 and lint (and therefore `lintVital` within `assembleRelease`)
  fails under it with the IntelliJ/UAST `MessageBusImpl` "Already disposed" error. Lint and
  `assembleRelease` are qualified under **gradle 8.9** (the CI version). This is a toolchain
  incompatibility, not a code defect; no lint rule was weakened or suppressed.
- No Android emulator/AVD and no KVM are available locally, so the API 35 emulator smoke test is
  **CI-only**; it was not booted in the sandbox.
- The four signing secrets are absent in the sandbox, so the signed-release production chain and
  `apksigner` signature verification are **CI-only and secret-gated** and were not exercised
  locally. No key, password, or keystore was generated, hardcoded, committed, or logged.
- The emulator smoke test runs on a standard (non-TV) API 35 image; it does not qualify Android TV
  leanback runtime.

## Next physical qualification commands

To be run by a human on the real device once a stable-signed APK exists. These are the
signature-continuity and verification commands (see
[`docs/signature-continuity-protocol.md`](signature-continuity-protocol.md)).

Compare the two APKs' signing certificates **before** upgrading (identical SHA-256 digest = same
key = compatible in-place upgrade):

```sh
apksigner verify --print-certs scenevibe-tv-companion-0.8.1-release.apk
apksigner verify --print-certs scenevibe-tv-companion-0.8.2-release.apk
# compare the "Signer #1 certificate SHA-256 digest:" lines - they must be identical
```

Install the first stable-signed APK, then upgrade in place with the next one:

```sh
adb install scenevibe-tv-companion-0.8.1-release.apk
adb install -r scenevibe-tv-companion-0.8.2-release.apk
# success criterion: the upgrade completes WITHOUT INSTALL_FAILED_UPDATE_INCOMPATIBLE
```

This upgrade/continuity test is a **HUMAN / PHYSICAL** step. It was not performed by CI or by this
cycle.

---

## Result

Release architecture hardened and version bumped to `0.8.2-tv-release-hardening` (versionCode 12).
All local automated gates pass after the bump; the API 35 smoke test and signed-release chain are
CI gates (the latter secret-gated and failing fast until secrets exist). **No new physical
qualification was performed this cycle**; the 0.8.1 Sony BRAVIA recovery record remains the prior
physical record and is unchanged. Physical API 35 runtime, Android TV leanback runtime, the signed
release artifact, and the signature-continuity protocol remain HUMAN ACTION REQUIRED / NOT YET
QUALIFIED as listed above.
