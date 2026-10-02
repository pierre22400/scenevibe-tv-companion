# SceneVibe TV — Stable signing key (sideload / qualification)

This document is the exact **human** procedure for creating and operating the stable
signing key used by the release CI chain (`.github/workflows/android-release.yml`).

> **No key material lives in this repository.** The private key, keystore, and all
> passwords are chosen and stored by a human, off-repo. Kiro / CI never generate,
> invent, log, commit, display, or store the private key. The release workflow decodes
> the keystore from a GitHub secret into the runner's temp dir at build time and
> discards it with the runner.
>
> **No real password value appears anywhere in this document.** Every password below is
> a `<PLACEHOLDER>`; choose your own strong passwords and never share them.

---

## Key architecture — read this first

There are three *distinct* signing concepts. This cycle only configures the **first**
one. Do not conflate them, and do not invent Google Play policy.

1. **Our sideload / qualification signing key (what the four GitHub secrets are).**
   A self-managed stable RSA key stored in a `.jks` keystore. The release workflow uses
   it to `apksigner`-sign the Consumer Cloud Mode release APK so the same APK can be
   sideloaded and later upgraded in place (`adb install -r`) on a qualification device
   without `INSTALL_FAILED_UPDATE_INCOMPATIBLE`. **This is the only key configured here.**

2. **Google Play *upload* key.** A key you would register with Google Play to sign the
   artifact you upload to the Play Console. This is a **separate future decision** and is
   **not** configured in this cycle.

3. **Google Play *App Signing* key.** The key Google itself manages and uses to sign the
   APKs delivered to end users once Play App Signing is enrolled. Also a **separate
   future decision**, **not** configured here.

For now SceneVibe TV uses the self-managed stable key (concept 1) for sideload /
qualification APKs. Before the first public Play release, make an explicit continuity
choice: Google Play permits a developer to provide an existing app-signing key when
configuring Play App Signing. If preserving certificate continuity between these
sideloaded builds and Play-delivered installs matters, evaluate using this same stable
key as the Play **app-signing** key (while keeping a distinct upload key). Do not create
or switch Play keys implicitly; that remains a deliberate release decision.

---

## Prerequisites

- Windows with PowerShell.
- A JDK (for `keytool`) and Android `build-tools;35.0.0` (for `apksigner`) available.
  `apksigner` is at `%ANDROID_HOME%\build-tools\35.0.0\apksigner.bat`.

---

## (a) Generate the stable key ONCE (locally)

Run this exactly once, on a trusted machine. Choose your own strong passwords when
prompted; do not reuse passwords and do not paste them into tickets, PRs, chat, or logs.

```powershell
keytool -genkeypair `
  -alias scenevibe-stable `
  -keyalg RSA `
  -keysize 4096 `
  -validity 10000 `
  -keystore scenevibe-stable.jks `
  -storetype JKS `
  -dname "CN=SceneVibe TV, OU=Qualification, O=SceneVibe, C=FR"
```

- `-alias scenevibe-stable` is a **placeholder** alias — pick your own and remember it;
  it becomes the `SCENEVIBE_ANDROID_KEY_ALIAS` secret.
- `keytool` will prompt for the **keystore password** and the **key password**. Choose
  strong values (`<KEYSTORE_PASSWORD>` and `<KEY_PASSWORD>`). You may use the same value
  for both, but keep track of which is which — they map to two different secrets below.
- `-validity 10000` (days, ~27 years) keeps the key valid well beyond the qualification
  lifetime so in-place upgrades never break on expiry.

## (b) Store the keystore off-repo and back it up

- Move `scenevibe-stable.jks` to secure off-repo storage (encrypted vault / password
  manager attachment / offline encrypted backup).
- **Never** place the `.jks` in this repository, in a ticket, in a PR, in chat, or in any
  log. There is no `.jks` committed here and there must never be one.
- Keep at least one encrypted backup. If this key is lost you cannot ship an in-place
  upgrade to already-installed devices; you would have to uninstall + reinstall.

## (c) Produce Base64 for GitHub

GitHub secrets store text, so Base64-encode the keystore bytes:

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes('scenevibe-stable.jks')) `
  | Set-Content -NoNewline scenevibe-stable.jks.b64.txt
```

Open `scenevibe-stable.jks.b64.txt` and copy its entire contents for the next step.
Delete this `.b64.txt` file afterward; it contains the key and must not linger or be
committed.

## (d) Fill the four GitHub repository secrets

In the GitHub repo: **Settings > Secrets and variables > Actions > New repository
secret**. Create all four — the release workflow fails fast unless **all four** are set:

| Secret name                              | Value                                                        |
| ---------------------------------------- | ------------------------------------------------------------ |
| `SCENEVIBE_ANDROID_KEYSTORE_BASE64`      | the Base64 string from step (c)                              |
| `SCENEVIBE_ANDROID_KEYSTORE_PASSWORD`    | your `<KEYSTORE_PASSWORD>`                                    |
| `SCENEVIBE_ANDROID_KEY_ALIAS`            | your alias (e.g. `scenevibe-stable`)                         |
| `SCENEVIBE_ANDROID_KEY_PASSWORD`         | your `<KEY_PASSWORD>`                                         |

## (e) Keep the reference certificate SHA-256 fingerprint off-repo

This fingerprint is the **continuity anchor**: it is how you later prove a new APK is
signed by the *same* key.

```powershell
keytool -list -v -keystore scenevibe-stable.jks -alias scenevibe-stable
```

Copy the `SHA256:` certificate fingerprint from the output and store it with your
off-repo key backup (not in this repo). This is the value every future release APK's
certificate must match.

## (f) Verify a future APK is signed by the SAME key

Whenever CI (or you) produces a release APK, confirm it carries the stable key:

```powershell
& "$env:ANDROID_HOME\build-tools\35.0.0\apksigner.bat" verify --print-certs app.apk
```

Read the line:

```
Signer #1 certificate SHA-256 digest: <hex>
```

Compare `<hex>` to the reference fingerprint you stored in step (e). They must be
**identical**. The release workflow already emits this digest as `cert-sha256.txt` and
inside `release-metadata.json` (`certificateSha256`) in the
`scenevibe-tv-companion-release` artifact, so you can diff against the reference without
re-running `apksigner`.

---

## What the CI release workflow does with these secrets

`.github/workflows/android-release.yml` (manual `workflow_dispatch` only):

1. Fails immediately with a clear `::error::` if any of the four secrets is missing — a
   release is **never** produced unsigned.
2. Builds the Consumer Cloud Mode release variant and produces the unsigned,
   non-debuggable `app-release-unsigned.apk`.
3. Decodes the keystore from `SCENEVIBE_ANDROID_KEYSTORE_BASE64` into the runner temp
   dir, runs `zipalign` on the unsigned APK **before signing**, signs the aligned APK with
   `apksigner`, runs `apksigner verify --verbose --print-certs`, verifies final alignment
   again with `zipalign -c`, and discards the keystore in an `always()` step.
4. Uploads `scenevibe-tv-companion-release` (signed APK + verify output + APK SHA-256 +
   certificate SHA-256 + secret-free metadata). It does **not** publish to Play Store,
   push a tag, or create a GitHub release.
