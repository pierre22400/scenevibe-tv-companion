# SceneVibe TV Companion — Cycle 0.8 (`0.8.0-tv-hardening`)

> **Status.** This document describes the `0.8.0-tv-hardening` consumer-hardening
> cycle. The build is compile-, lint- and unit-test-verified in CI. It has **not**
> been physically qualified on a TV yet; the physical qualification protocol lives
> in [`docs/pr-0.8.0-tv-hardening.md`](pr-0.8.0-tv-hardening.md).

Cycle 0.8 turns the LAN pairing POC into an **autonomous, Cloud-first consumer
runtime** that a non-technical user can install once, connect once with a
six-digit code, and then forget: no IP address, no port, no pairing screen, no PC
required to keep watching. The developer LAN prototype is preserved intact but
hidden behind a build flag.

## The three identities (kept strictly distinct)

Cycle 0.8 keeps three separate identities that must never be conflated:

| Identity | What it is | Where it lives | Secret? |
| --- | --- | --- | --- |
| **installationId** | The stable *local* installation id of SceneVibe on this TV. Random, app-private, independent of the LAN, never derived from IP/MAC/TV model. | `installation` app-private prefs, via `InstallationIdentity`. | No (non-secret). |
| **cloudDeviceId** | The server-assigned device UUID (`deviceId`) returned by the Cloud on activation (`201`). | `cloud_identity` app-private prefs. | No (an identifier, shown only abbreviated in Diagnostics). |
| **deviceToken** | The durable bearer proof the TV uses on assignment/ACK. | Encrypted via `SecretStore` (AndroidKeyStore). | **Yes.** Never logged, never shown. |

## Consumer Cloud Mode (default)

The default consumer build (`SCENEVIBE_ENABLE_LAN_DEV` unset/false) runs
**Cloud-only**:

- No LAN HTTP server is constructed and TV port **8765** is never opened.
- The normal screen shows only: app version, **Display over other apps**
  (Granted/Not granted), **Media access** (Granted/Not granted), **SceneVibe
  Cloud** status, the **Start SceneVibe with TV** autostart toggle, and the
  buttons **Start SceneVibe / Stop SceneVibe / Connect to SceneVibe Cloud /
  Disconnect Cloud / Diagnostics**.
- No IP, port, HTTP route, revision, `activationId`, or `deviceToken` is ever
  displayed.

### Why no IP is required

The TV talks **outbound** to the SceneVibe Cloud over HTTPS. It initiates every
request itself (activation, assignment polling, ACK), so nothing on the local
network ever needs the TV's address. The desktop/web side hands out work through
the Cloud, not by connecting to the TV. This is why the consumer never sees or
types an IP address or port, and why the PC can be powered off once an assignment
has been delivered and cached.

### Connecting with a 6-digit code (activation)

The canonical `/api/v1` flow (base `/api/v1`):

1. **Activation** — `POST /device-activations` with `{"installationId":"…"}`
   (reactivation also sends the current `{"deviceToken":"…"}`). A `201` returns
   `{activationId, deviceId (=cloudDeviceId), userCode (6 digits), deviceToken,
   activationSecret, expiresAt}`. The durable `deviceToken` and `cloudDeviceId`
   are persisted **immediately**; `userCode` is shown on the TV as
   `Code: <6 digits>` with `Waiting for connection...`.
2. **Activation status** — `GET /device-activations/:activationId` with
   `Bearer <activationSecret>` returns `{"status":"open|claimed|expired"}`.
   `claimed` shows `Connected`; `expired` clears only the temporary activation
   state and keeps `deviceToken` + `cloudDeviceId`.
3. **Assignment** — `GET /devices/:cloudDeviceId/assignment?afterRevision=N` with
   `Bearer <deviceToken>` returns the canonical envelope. `204` (nothing newer)
   and `404` (authenticated, no current assignment) are both **normal** and never
   treated as offline; `401` means the credential was rejected.
4. **Acknowledgement** — `POST /devices/:cloudDeviceId/ack` with
   `Bearer <deviceToken>` and body `{"revision":<n>, "finalTrackId":"…"}`.

Assignment polling uses a bounded exponential backoff (15 s … 120 s). Everything
is **fail-closed**: corrupt or invalid input clears the affected state, never
crashes, and never leaves a stale card on screen.

### Offline cache

Each Cloud revision is committed through the *same* track parser as LAN
(text-only for Cloud). At service (re)start the TV **restores the cached track
before any network request**, so commentary keeps working with the PC off and no
connectivity. `CloudTrackRepository` holds the last validated runtime track,
`trackId`, and `revision`. Disconnect Cloud keeps this cache; only an explicit
Reset Cloud clears it.

## InstallationIdentity, cloudDeviceId, deviceToken and legacy migration

- **InstallationIdentity** (`InstallationIdentity.java`) owns the local
  `installationId`. On first read it (1) reuses an existing new value, else
  (2) migrates the **exact** legacy `PairingPolicy` `deviceId` (key `deviceId` in
  the `pairing` prefs) so a 0.7.1 install keeps its id, else (3) mints a strong
  random id (Base64url of 16 `SecureRandom` bytes, matching the legacy format),
  then persists it. `CloudControlClient` now sources `installationId` from here,
  not from `PairingRuntime`.
- **cloudDeviceId** is the server UUID from the `201`; it is an identifier, kept
  in plaintext app-private prefs and shown only abbreviated in Diagnostics.
- **deviceToken** (and `activationSecret`) are the secrets — see Keystore below.

### Legacy credential migration (fail-safe)

For a 0.7.1 install that stored a plaintext `deviceToken`/`activationSecret`, the
migration is **encrypt → persist → verify → only then delete** (never
delete-first): detect the legacy plaintext, encrypt via `SecretStore`, persist
ciphertext+IV, read it back and confirm it equals the original, and only then
remove the plaintext. On any Keystore problem it **fails closed**: the old value
is kept, there is no plaintext fallback, no new TV identity is minted, no identity
is auto-erased, and the state becomes observable as `CREDENTIAL_UNAVAILABLE`.

## Keystore / SecretStore

`SecretStore` is the encryption boundary for the two secret credentials
(`deviceToken`, `activationSecret`):

- Production `AndroidKeyStoreSecretStore` uses `Cipher "AES/GCM/NoPadding"` with an
  AES-256 `KeyGenParameterSpec` key generated **inside AndroidKeyStore that never
  leaves it**. There is **no** biometric / user-authentication requirement, so the
  unattended autostart path can still decrypt after a reboot.
- Ciphertext + per-record IV are stored Base64 in a dedicated app-private prefs
  file (`cloud_secret`), separate from the plaintext `cloud_identity` prefs.
- **`EncryptedSharedPreferences` is deliberately not used.**
- Any Keystore/cipher failure **fails closed** (never returns plaintext, never
  silently regenerates) and surfaces the `CREDENTIAL_UNAVAILABLE` signal.
- The non-secret fields (`cloudDeviceId`, `installationId`, `activationId`,
  `userCode`, flags, revisions) may remain in plaintext app-private prefs.
- `allowBackup=false` is preserved, so secrets are not swept into cloud backup.

### Credential state safety contract

The credential write path is designed for a **coherent** credential state, not for
absolute filesystem-level atomicity. Android offers **no** cross-store transaction
that fsyncs the AndroidKeyStore-backed `SecretStore` and the several
`SharedPreferences` records together, so this cycle does **not** claim one. The
actual contract is:

- A **successful** transition leaves a coherent new credential state: both secrets
  are encrypted and verified, the non-secret batch is committed, and no old/new
  mix remains.
- Any **detected** persistence or keystore uncertainty — a non-durable `commit`, a
  read-back mismatch, or a keystore/cipher failure — **fails closed**: the code
  rolls back to the previous tuple where it can and **never operationalizes a mixed
  or uncertain credential tuple**.
- Such a state is exposed as `CREDENTIAL_UNAVAILABLE`, and the store **never** falls
  back to reading legacy plaintext as an operational credential.

The `SecretStore` fault-injection tests model a `commit` failure as leaving the
previous non-secret values in place. That is the observable behavior of the
injected in-memory fake used to prove the fail-closed/rollback logic — it is **not**
a hardware-level atomicity guarantee about production `SharedPreferences` +
AndroidKeyStore.

## Autostart and reboot behavior

- **Start SceneVibe with TV** is an explicit opt-in preference
  (`AutostartPreference`), default **false**. Nothing starts automatically until
  the user enables it.
- `BootReceiver` listens to **only** `BOOT_COMPLETED` and `MY_PACKAGE_REPLACED`
  (declared with `RECEIVE_BOOT_COMPLETED`). There is **no**
  `LOCKED_BOOT_COMPLETED`, **no** `directBootAware`, and no Device Protected
  Storage. It never launches `MainActivity`, never loops, never auto-requests a
  permission, and never crashes.
- On boot the receiver consults the pure `AutostartPolicy.decide(...)`, which
  returns `START` **only** when autostart is enabled **and** overlay permission is
  granted **and** media access is granted **and** there is at least one useful
  resource (a usable Cloud credential **or** a valid cached track). Otherwise it
  records a bounded diagnostic: `AUTOSTART_DISABLED`,
  `AUTOSTART_BLOCKED_OVERLAY_PERMISSION`, `AUTOSTART_BLOCKED_MEDIA_PERMISSION`, or
  `AUTOSTART_NOTHING_TO_RESTORE`.
- **`armed != visible`.** When started at boot (`ACTION_BOOT_PREPARE`),
  `OverlayService` primes the cache restore, track scheduler, MediaSession probe
  and Cloud client **without drawing any overlay**: no card, no badge, no stale
  comment. The renderer is created **lazily**, only when a comment is actually due
  (media eligible + a due comment), and the tracked commentary is hidden
  immediately when media becomes ineligible. User-initiated Start still shows the
  overlay as before.

### Android 15 / targetSdk 35 FGS-from-boot note

Starting the `specialUse` foreground service from `BOOT_COMPLETED` is supported
under Android 15 / targetSdk 35 and is implemented the correct way: the receiver
calls `Context.startForegroundService(...)` and the service promptly calls
`startForeground(...)` with the declared `specialUse` type. Android 15 tightened
which FGS *types* may be started from `BOOT_COMPLETED` (dataSync, camera,
microphone and some media types are now disallowed), but `specialUse` is **not**
on that boot-disallowed list, so the overlay FGS remains startable from boot. The
`foregroundServiceType` was deliberately **not** downgraded. If a future
OEM/policy blocks the start, the platform throws
`ForegroundServiceStartNotAllowedException`; `BootReceiver` catches only that
start failure, logs a bounded diagnostic, and stays disarmed (no retry loop, no
silent hiding). This boot/FGS runtime behavior needs confirmation on Android 15 /
API 35 (an Android 15 emulator or an Android 15 device); it is compile-/lint-verified
only here. Note this Android 15 / API 35 validation is distinct from the real Sony
BRAVIA product qualification: the Sony device exercises the product end to end
(autostart, Cloud, cache, media identity, reset, UI), but its OS version is not
assumed to be Android 15, so the API 35 boot-start and per-type behaviors are
validated separately on an Android 15 emulator or device.

## Diagnostics (observational, bounded, no secrets)

A **Diagnostics** screen is reachable from `MainActivity` but off the normal
parcours (`DiagnosticsActivity`, `exported=false`, not a launcher entry,
read-only, D-pad navigable). It shows: app version; service running/stopped;
autostart enabled/disabled; Cloud connected/offline/activation-pending/
disconnected; **abbreviated** `installationId`; **abbreviated** `cloudDeviceId`;
cached track yes/no; cached `trackId`; cached revision; last acknowledged
revision; MediaSession permission; last observed media app; media identity
eligible/blocked/unavailable; last block code; last successful Cloud connection;
last assignment revision received; last successful ACK; last Cloud error code.

Diagnostics is **observational only** (it never drives business logic) and
**bounded** (no unbounded history). It never exposes `deviceToken`,
`activationSecret`, a LAN token, a pepper, `DATABASE_URL`, Vercel secrets, the
full FinalTrack, comment text, or raw stack traces containing network/credential
values. `installationId` and `cloudDeviceId` are shown abbreviated only. Cloud
errors surface as bounded codes (`NETWORK`, `UNAUTHORIZED`, `PROTOCOL`,
`CREDENTIAL_UNAVAILABLE`).

## Disconnect Cloud vs Reset Cloud

- **Disconnect Cloud** (available in the normal UI): stops local Cloud use and
  clears the activation temporaries, but **keeps** `deviceToken`, `cloudDeviceId`,
  `InstallationIdentity`, and the FinalTrack cache. It does not revoke the server
  token. The TV can reconnect later without a new code.
- **Reset SceneVibe Cloud connection** (exceptional, available **only** in
  Diagnostics): flips the Cloud client to stopped synchronously, then performs the wipe
  asynchronously. Work already executing on the single Cloud executor is allowed to finish
  first; the queued wipe then removes any credential/cache/scheduler state written by that
  work before reset completion. If the primary executor is already shut down, the wipe is
  dispatched to a separate daemon fallback worker — it is never executed inline on the
  Android caller thread. The wipe removes the `SecretStore` secrets (`deviceToken`,
  `activationSecret`) plus `cloud_identity` (`cloudDeviceId`, `activationId`,
  `userCode`) and the Cloud/runtime cache, and sets Cloud state to disconnected.
  **Reset never deletes `InstallationIdentity`.** There is **no** auto-reset on
  network/timeout/`401`/error, and **no** Factory Reset this cycle.

## Stable APK signing

The Cloud qualification APK can be signed with a **stable** key so that, once two
builds share that same stable certificate, a later build updates an installed one
without an uninstall. The first move onto the stable key is the exception: a build
that was previously installed with the ephemeral debug CI signature is not signature
compatible with the stable-signed build, so that initial transition may need **one**
uninstall. To qualify a true in-place 0.7.1 -> 0.8.0 update without uninstall,
install a reference 0.7.1 signed with the **same stable cert** first and then
`adb install -r` the stable-signed 0.8.0 over it. Signing is **secret-gated** in CI
and reads material **only** from GitHub Actions secrets — nothing is committed:

- `SCENEVIBE_ANDROID_KEYSTORE_BASE64`
- `SCENEVIBE_ANDROID_KEYSTORE_PASSWORD`
- `SCENEVIBE_ANDROID_KEY_ALIAS`
- `SCENEVIBE_ANDROID_KEY_PASSWORD`

When these four secrets are present, CI decodes the keystore into `RUNNER_TEMP`,
signs the Cloud qualification APK with `apksigner`, verifies the certificate,
uploads the signed APK
(`scenevibe-tv-companion-cloud-qualification-stable`), then deletes the keystore
so the runner discards it. **When the secrets are absent, standard CI is
unchanged**: the debug-signed qualification APK is still built, tested and
uploaded, and stable signing is simply **not active**. No private key, keystore,
password, or Base64 material is ever committed to the repository.

## Build variables

| Variable | Type | Default | Effect |
| --- | --- | --- | --- |
| `SCENEVIBE_CLOUD_ORIGIN` | build-time env → `BuildConfig.CLOUD_ORIGIN` | empty | Exact HTTPS origin (port 443) of the SceneVibe Cloud. Empty = Cloud **Not configured**. |
| `SCENEVIBE_ENABLE_LAN_DEV` | build-time env → `BuildConfig.ENABLE_LAN_DEV` | `false` | `true` restores the developer LAN prototype surface (CommentaryServer on port 8765, pairing UI, IPv4/port display). Default consumer builds are Cloud-only. |

## LAN DEV Mode (developer only)

Building with `SCENEVIBE_ENABLE_LAN_DEV=true` restores the full v0.6 LAN
prototype **for developers**: the bounded LAN HTTP server on port **8765**,
`POST /commentary`, `POST /track`, `GET /health`, the six-digit LAN pairing
window and bearer token, and the `MainActivity` pairing status / IPv4 / port
display. This surface is compiled out of consumer builds and the Cloud runtime
has **no** functional dependency on the LAN pairing subsystem.
