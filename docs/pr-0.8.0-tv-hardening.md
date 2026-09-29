# feat(tv): harden autonomous Cloud runtime for consumer use

> **This file is the PR body + physical qualification protocol for cycle 0.8**
> (`0.8.0-tv-hardening`). `0.8.0-tv-hardening` is **NOT yet physically
> qualified** — the APK has not been run on a TV. Everything below is compile-,
> lint- and unit-test-verified in CI only. Do **not** describe 0.8.0 as
> "physically qualified" anywhere.

## Summary

Cycle 0.8 turns the LAN pairing POC into an autonomous, Cloud-first consumer
runtime: install once, connect once with a six-digit code, then forget it. No IP,
port or pairing screen is shown; the PC can be powered off once an assignment is
cached; credentials are encrypted at rest; and the developer LAN prototype is
preserved intact behind the `SCENEVIBE_ENABLE_LAN_DEV` build flag.

## Revision range

- **Starting SHA:** `c37229809e22e41dc28bdb701d067bd1c9f76aa3`
  (`Merge physically qualified TV cloud client`).
- **Final code SHA:** `ad04176d05f6147226558038fc21d5aeffe429f4` (the last functional commit of the corrective cycle). This doc-pinning commit sits one above it as the branch tip; the exact branch HEAD and commit count at PR update are stated on the GitHub PR itself.
- **Commit count:** `16` commits from the starting SHA to the branch tip (`6` in this corrective cycle — 5 functional + this doc pin — on top of the prior `10`).
- **Branch:** `kiro/tv-product-hardening-001` → base `main`.

## Files changed (against the starting SHA)

Modified:

- `.github/workflows/android-debug.yml` — LAN DEV compile proof + secret-gated stable signing; branch trigger.
- `README.md` — Cycle 0.8 section linking the new docs.
- `app/build.gradle` — versionCode 9→10, versionName `0.7.1-cloud-media-bound`→`0.8.0-tv-hardening`, `ENABLE_LAN_DEV` buildConfigField.
- `app/src/main/AndroidManifest.xml` — `RECEIVE_BOOT_COMPLETED` + `BootReceiver` (BOOT_COMPLETED + MY_PACKAGE_REPLACED only); DiagnosticsActivity; `allowBackup=false` and specialUse FGS unchanged.
- `app/src/main/java/com/scenevibe/tvcompanionpoc/CloudControlClient.java` — `installationId` sourced from `InstallationIdentity`, off `PairingRuntime`.
- `app/src/main/java/com/scenevibe/tvcompanionpoc/CloudDeviceCredentials.java` — secrets routed through `SecretStore`; migration; disconnect vs reset.
- `app/src/main/java/com/scenevibe/tvcompanionpoc/CloudTrackRepository.java` — `clear()` for reset; `cachedTrackId()`; cache preserved on disconnect.
- `app/src/main/java/com/scenevibe/tvcompanionpoc/MainActivity.java` — simplified consumer UI; LAN widgets gated; autostart toggle; Diagnostics; Disconnect.
- `app/src/main/java/com/scenevibe/tvcompanionpoc/OverlayService.java` — `armed != visible` boot-prepare + lazy renderer; CommentaryServer gated behind `ENABLE_LAN_DEV`; diagnostics feed.
- `app/src/test/java/com/scenevibe/tvcompanionpoc/CloudDeviceCredentialsTest.java` — wiring adapted to inject fake `SecretStore`; lifecycle contract retained.
- `tests/test_poc_contract.py` — boundary set includes `RECEIVE_BOOT_COMPLETED`; BootReceiver-actions test; eligibility semantics updated.

Added:

- `app/src/main/java/com/scenevibe/tvcompanionpoc/AutostartPolicy.java`
- `app/src/main/java/com/scenevibe/tvcompanionpoc/AutostartPreference.java`
- `app/src/main/java/com/scenevibe/tvcompanionpoc/BootReceiver.java`
- `app/src/main/java/com/scenevibe/tvcompanionpoc/DiagnosticsActivity.java`
- `app/src/main/java/com/scenevibe/tvcompanionpoc/DiagnosticsStore.java`
- `app/src/main/java/com/scenevibe/tvcompanionpoc/InstallationIdentity.java`
- `app/src/main/java/com/scenevibe/tvcompanionpoc/RuntimeDiagnostics.java`
- `app/src/main/java/com/scenevibe/tvcompanionpoc/SecretStore.java`
- `app/src/test/java/com/scenevibe/tvcompanionpoc/AutostartPolicyTest.java`
- `app/src/test/java/com/scenevibe/tvcompanionpoc/CloudResetTest.java`
- `app/src/test/java/com/scenevibe/tvcompanionpoc/ConsumerModeTest.java`
- `app/src/test/java/com/scenevibe/tvcompanionpoc/DiagnosticsNoSecretTest.java`
- `app/src/test/java/com/scenevibe/tvcompanionpoc/InstallationIdentityTest.java`
- `app/src/test/java/com/scenevibe/tvcompanionpoc/OverlayArmedNotVisibleTest.java`
- `app/src/test/java/com/scenevibe/tvcompanionpoc/SecretStoreMigrationTest.java`
- `docs/tv-hardening-0.8.md`
- `docs/pr-0.8.0-tv-hardening.md` (this file)

### Corrective cycle 0.8 touches (Corrections 1-5 + doc corrections 7-9)

This corrective cycle changed behavior and tests without adding features. In
addition to the files above it touched:

- `OverlayService.java` / `OverlayRenderer.java`: Consumer Mode `armed != visible`
  made real on every entry path; renderer only on a real render; full hide on
  expiry and on BLOCKED/UNAVAILABLE (Correction 1).
- `CloudDeviceCredentials.java` / `SecretStore.java`: application-level coherent
  credential state (not filesystem-level atomicity): a best-effort
  encrypt/verify/commit-then-rollback activation persist, retryable
  `confirmClaimed`, durable verifiable secret removal, fail-closed migration
  (Correction 2). See "Credential state safety contract" below for the exact
  guarantee.
- `CloudControlClient.java` / `DiagnosticsActivity.java` / `RuntimeDiagnostics.java`
  / `DiagnosticsStore.java` / `BootReceiver.java` / `InstallationIdentity.java`:
  Reset serialized through the runtime execution boundary; read-only Diagnostics;
  honest bounded diagnostics for `lastBlockCode` and autostart decisions
  (Corrections 3 + 5).
- `.github/workflows/android-debug.yml`: `SIGNING_ENABLED` now requires all four
  stable-signing secrets non-empty (Correction 4).
- Tests extended: `SecretStoreMigrationTest`, `CloudResetTest`,
  `DiagnosticsNoSecretTest`, and the new `OverlayArmedNotVisibleTest`.

## Architecture per subsystem

- **Version / build flags** — `app/build.gradle` bumps to `0.8.0-tv-hardening`
  (versionCode 10) and adds `SCENEVIBE_ENABLE_LAN_DEV` → `BuildConfig.ENABLE_LAN_DEV`
  (default `false`), modeled on the existing `SCENEVIBE_CLOUD_ORIGIN` pattern.
  `applicationId com.scenevibe.tvcompanionpoc`, minSdk 26, targetSdk 35, compileSdk 35 unchanged.
- **Three identities** — `InstallationIdentity` owns the local `installationId`
  (random, app-private, never from IP/MAC/model). `cloudDeviceId` is the server
  UUID from the `201`. `deviceToken` is the durable secret. `CloudControlClient`
  now sources `installationId` from `InstallationIdentity` (no `PairingRuntime`
  dependency).
- **SecretStore / Keystore** — `deviceToken` + `activationSecret` are encrypted
  with `AES/GCM/NoPadding` using a non-exportable AndroidKeyStore AES-256 key with
  no user-auth requirement (so autostart can decrypt). Ciphertext+IV live in a
  dedicated `cloud_secret` prefs file. `EncryptedSharedPreferences` is not used.
  Fail-closed → `CREDENTIAL_UNAVAILABLE`. `allowBackup=false` preserved.
- **Legacy migration** — value-stable `installationId` migration from the legacy
  `PairingPolicy` `deviceId`; encrypt→persist→verify→then-delete for legacy
  plaintext secrets (never delete-first); fail-closed keeps the old value.
- **Credential state safety contract** — the credential write path does **not**
  claim absolute filesystem-level atomicity across the AndroidKeyStore-backed
  `SecretStore` and the multiple `SharedPreferences` records it touches; no such
  cross-store fsync transaction exists on Android. What it does guarantee, at the
  application level, is: a **successful** transition leaves a **coherent new
  credential state** (secrets encrypted-and-verified, non-secret batch committed,
  no old/new mix); any **detected** persistence or keystore uncertainty (a
  non-durable commit, a read-back mismatch, or a keystore/cipher failure) **fails
  closed** — the code rolls back to the previous tuple where it can and never
  operationalizes a mixed or uncertain credential tuple. Such a state is exposed
  as `CREDENTIAL_UNAVAILABLE`, and the store **never** falls back to reading legacy
  plaintext as an operational credential. The `SecretStore` fault-injection tests
  model a `commit` failure as leaving the previous non-secret values in place;
  that is the observable behavior of the injected fake, **not** a hardware-level
  atomicity claim about production `SharedPreferences`/Keystore.
- **Consumer cloud-only mode** — CommentaryServer / port 8765 / pairing UI /
  IPv4/port gated behind `ENABLE_LAN_DEV`; Cloud + MediaSession run ungated.
- **Autostart + boot** — opt-in `AutostartPreference` (default false);
  `BootReceiver` (BOOT_COMPLETED + MY_PACKAGE_REPLACED only, no
  LOCKED_BOOT_COMPLETED, no directBootAware); pure `AutostartPolicy.decide(...)`;
  `OverlayService` `armed != visible` boot-prepare with lazy renderer.
- **Diagnostics + Disconnect/Reset** — observational, bounded `RuntimeDiagnostics`
  / `DiagnosticsStore`; read-only `DiagnosticsActivity` off the normal path (no
  secrets, abbreviated ids). Disconnect keeps creds+cache; Reset (Diagnostics-only)
  wipes Cloud creds+cache but preserves `InstallationIdentity`; no auto-reset; no
  Factory Reset.
- **Simplified consumer UI** — permissions, Cloud status, autostart toggle,
  Start/Stop, Connect, Disconnect, Diagnostics; activation shows `Code: <6 digits>`
  + `Waiting for connection...`, connected shows `Connected`; no IP/port/route/
  revision/activationId/deviceToken.
- **CI** — keeps the Python boundary check, the "Cloud not configured"
  assembleDebug+lintDebug+testDebugUnitTest gate, and the Cloud qualification APK
  build+upload; adds a `SCENEVIBE_ENABLE_LAN_DEV=true` compile proof; adds
  secret-gated stable signing that no-ops without secrets.

## Exact test results (this corrective cycle, run locally with JDK 17 forced)

- **Python boundary suite** — `python3 -m unittest discover -s tests` → **Ran 12
  tests, OK**.
- **Full Android gate** — `gradle :app:assembleDebug :app:lintDebug
  :app:testDebugUnitTest -Dorg.gradle.java.home=…/java/17 --stacktrace` → **BUILD
  SUCCESSFUL**.
- **JUnit classes run via `testDebugUnitTest`** (14 classes, 95 tests, all green):
  `AutostartPolicyTest`, `CloudDeviceCredentialsTest`, `CloudProtocolTest`,
  `CloudResetTest`, `CloudTrackRepositoryTest`, `CommentaryServerAuthTest`,
  `ConsumerModeTest`, `DiagnosticsNoSecretTest`, `DisplayCountdownTest`,
  `InstallationIdentityTest`, `MediaSyncedTrackSchedulerIdentityTest`,
  `OverlayArmedNotVisibleTest`, `PairingPolicyTest`, `SecretStoreMigrationTest`.
- **Four build proofs:**
  1. Full gate (assembleDebug + lintDebug + testDebugUnitTest) — BUILD SUCCESSFUL.
  2. `SCENEVIBE_ENABLE_LAN_DEV=true` assembleDebug — BUILD SUCCESSFUL.
  3. `SCENEVIBE_CLOUD_ORIGIN=https://interface-scenevibe-3wxg.vercel.app`
     assembleDebug — BUILD SUCCESSFUL.
  4. Python boundary suite — 12 tests OK.

## Android limitations (Android 15 / targetSdk 35 FGS-from-boot audit)

Starting the `specialUse` foreground service from `BOOT_COMPLETED` is **supported**
under Android 15 / targetSdk 35 and is implemented correctly: `BootReceiver` calls
`Context.startForegroundService(...)` and `OverlayService` promptly calls
`startForeground(...)` with the declared `specialUse` type. `BOOT_COMPLETED` is an
exemption to the Android 12+ background-FGS-start restriction. Android 15 tightened
which FGS *types* may be started from `BOOT_COMPLETED` (dataSync, camera,
microphone and some media types are now disallowed / deferred), but `specialUse` is
**not** on that boot-disallowed-type list, so the overlay FGS remains startable from
boot. `foregroundServiceType` was deliberately **not** downgraded. Residual risk,
surfaced (not silently swallowed): if a future OEM/policy blocks the start, the
platform throws `ForegroundServiceStartNotAllowedException`; `BootReceiver` catches
only that start failure, logs a bounded diagnostic and stays disarmed (no retry
loop). The correct future fix is to keep `specialUse` and surface a user-facing
"autostart blocked by system" diagnostic, not to downgrade the type.

**Sandbox limitation:** there is no Android device/emulator here, so real boot
dispatch, the ~10 s `startForeground` window, the Android 15 per-type boot start
behavior, real AndroidKeyStore AES/GCM round-trip and GCM-tag-mismatch fail-closed,
and all on-device UI/D-pad rendering are **not** executed. They are validated by
pure-JVM tests against injectable boundaries plus successful compile + lint, and
require physical qualification.

## APK signing status

- The workflow builds and uploads the debug-signed Cloud qualification APK
  unconditionally.
- **Stable signing is prepared but NOT yet active**, because it is gated on four
  GitHub Actions secrets that a human must configure. When they are present, CI
  decodes the keystore into `RUNNER_TEMP`, signs the qualification APK with
  `apksigner`, uploads
  `scenevibe-tv-companion-cloud-qualification-stable`, and deletes the keystore.
  When absent, standard CI passes unchanged.
- **No key/keystore/password/Base64 is committed.**

### GitHub secrets a human must still configure (to activate stable signing)

- `SCENEVIBE_ANDROID_KEYSTORE_BASE64`
- `SCENEVIBE_ANDROID_KEYSTORE_PASSWORD`
- `SCENEVIBE_ANDROID_KEY_ALIAS`
- `SCENEVIBE_ANDROID_KEY_PASSWORD`

---

## Section 23 — Physical qualification protocol (run later; 8 tests)

**Reference Cloud harness:** the official merged harness at `interface-scenevibe`
`main` commit `67f6536600d386a4e6f818055803a5468f6e8c33`, canonical `/api/v1`
contract. Use FinalTrack **"Eaux troubles"**. On a successful ACK the expected
string is exactly **"TV ACK acknowledged — safe to turn off the PC"**.

Build the qualification APK with
`SCENEVIBE_CLOUD_ORIGIN=https://interface-scenevibe-3wxg.vercel.app` (stable-signed
once the four secrets are configured). The eight tests split across two distinct
qualifications, which must not be conflated:

- **(A) Real Sony BRAVIA product qualification** on the actual Sony device with a
  real streaming app (e.g. Prime Video): autostart, Cloud connect, offline cache,
  media identity, reset, and on-device UI. This exercises the product on the shipping
  TV. It does **not** assert the Sony device's OS version, and in particular does not
  claim the Sony BRAVIA runs Android 15.
- **(B) Android 15 / API 35 validation** on an Android 15 emulator or a separate
  Android 15 device: the platform-specific runtime behaviors that need API 35 to be
  meaningful, namely `FGS_BOOT_COMPLETED_RESTRICTIONS` (the `specialUse` FGS still
  starts from `BOOT_COMPLETED`), `BOOT_COMPLETED` and `MY_PACKAGE_REPLACED` receiver
  dispatch. See the FGS-from-boot audit above for why `specialUse` is not on Android
  15's boot-disallowed-type list.

Tests 2-8 below are the real Sony BRAVIA product qualification (A). Test 1 (update /
migration) and the FGS-from-boot behavior belong to the Android 15 / API 35
validation (B); run the boot-start and per-type checks on the A15 emulator or device.

1. **Update / migration with no uninstall (if signature compatible).** Signature
   compatibility is the precondition, and it is **not** guaranteed for the very first
   move to the stable key: the prior debug CI signature was an ephemeral debug cert,
   so a stable-signed 0.8.0 may fail to `adb install -r` over an installed 0.7.1 that
   carried the old debug signature. In that case the initial move to the stable key
   needs **one** uninstall of 0.7.1, then a clean install of 0.8.0; after that,
   subsequent 0.8.x updates install in place. To truly qualify a 0.7.1 -> 0.8.0
   update **without** uninstall, first install a reference 0.7.1 APK signed with the
   **same stable cert**, connect it, then `adb install -r` the stable-signed 0.8.0
   over it. Confirm the in-place update succeeds and the migrated `installationId`,
   `cloudDeviceId` and `deviceToken` are preserved (still connected, no re-pairing
   needed).
2. **Cloud connect with 6-digit code.** On a fresh install, Connect to SceneVibe
   Cloud; the TV shows `Code: <6 digits>` + `Waiting for connection...`. Claim the
   code via the merged harness, push FinalTrack "Eaux troubles"; the TV shows
   `Connected`, loads the assignment, and on ACK the harness reports
   **"TV ACK acknowledged — safe to turn off the PC"**.
3. **Autostart reboot.** Enable **Start SceneVibe with TV**, grant overlay + media
   access, then reboot the TV. Confirm the service auto-starts armed (no card, no
   badge, no stale comment) and renders commentary lazily only when media becomes
   eligible.
4. **Media identity.** Start the streaming app and confirm the overlay tracks the
   correct MediaSession (eligible), stays hidden when media is ineligible, and the
   scheduler follows the media clock (pause/resume, both seek directions).
5. **Offline.** With an assignment cached, power off / disconnect the PC and restart
   SceneVibe. Confirm the cached track is restored **before** any network request
   and commentary continues with the PC off.
6. **Opt-out.** Disable **Start SceneVibe with TV** and reboot; confirm nothing
   auto-starts (`AUTOSTART_DISABLED` diagnostic), and that missing overlay/media
   permission or nothing-to-restore yield the correct bounded diagnostic instead of
   a start.
7. **Diagnostics no-secret.** Open Diagnostics and confirm every field is present
   and correct, ids are shown **abbreviated only**, and **no** `deviceToken`,
   `activationSecret`, LAN token, full FinalTrack, comment text or raw network/
   credential stack trace is ever displayed.
8. **Reset Cloud preserves InstallationIdentity.** In Diagnostics run **Reset
   SceneVibe Cloud connection**; confirm `deviceToken`, `cloudDeviceId`,
   `activationId`, `activationSecret`, `userCode` and the cache are wiped and Cloud
   is disconnected, but the `installationId` is **unchanged** before and after
   reset. Confirm **Disconnect Cloud** (normal UI) instead keeps `deviceToken`,
   `cloudDeviceId`, `installationId` and the cache.

> Until all eight pass on a physical TV, `0.8.0-tv-hardening` is **NOT physically
> qualified**. Do not claim PC-off success before test 5 passes on real hardware.
