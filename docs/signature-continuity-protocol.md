# SceneVibe TV — Signature continuity protocol (in-place upgrade test)

> **HUMAN / PHYSICAL step. NOT performed by CI and NOT performed by this cycle.**
> This document is the exact procedure a human runs later, on the physical Sony TV (or
> other API-35 qualification device), to prove that a new stable-signed release APK can
> upgrade an already-installed one **in place**. Nothing here is executed by Kiro, by the
> release workflow, or during this hardening cycle. It is written down now so the human
> run is unambiguous.

The goal: confirm that two release APKs signed by the stable key are **upgrade
compatible** — installing the second over the first does **not** fail with
`INSTALL_FAILED_UPDATE_INCOMPATIBLE` (the error Android raises when the signing
certificate changed).

---

## Why this matters

Android allows an in-place upgrade (`adb install -r`) only when the new APK is signed by
the **same** certificate as the installed one. If the stable signing key is swapped,
lost, or regenerated, upgrades break and the device must be uninstalled + reinstalled,
losing app state. The stable signing key (see `docs/stable-signing.md`) exists precisely
to keep this certificate identical across releases.

---

## Step 0 — Compare the signing certificates BEFORE touching the device

Do this first. If the certificates already differ, the on-device upgrade **will** fail;
there is no point installing.

For each APK (the currently-installed one and the next one), run:

```powershell
& "$env:ANDROID_HOME\build-tools\35.0.0\apksigner.bat" verify --print-certs <apk>
```

Read the line from each:

```
Signer #1 certificate SHA-256 digest: <hex>
```

- **Identical `<hex>` for both APKs** = same key = upgrade compatible. Proceed.
- **Different `<hex>`** = different key = the upgrade will fail. STOP and resolve the
  signing key discrepancy (see `docs/stable-signing.md`) before installing.

You can also compare against the off-repo **reference fingerprint** recorded in
`docs/stable-signing.md` step (e), and against `cert-sha256.txt` /
`release-metadata.json` (`certificateSha256`) in the `scenevibe-tv-companion-release` CI
artifact.

---

## Step 1 — Install the first stable release APK

With the device connected over `adb`:

```powershell
adb install scenevibe-tv-companion-release.apk
```

Expect `Success`. This establishes the baseline install signed by the stable key.

## Step 2 — Upgrade in place with the next stable release APK

Using a later build signed by the **same** stable key:

```powershell
adb install -r <next-stable>.apk
```

### Success criterion

- Output is `Success` and the app upgrades in place with its data preserved.
- **No** `INSTALL_FAILED_UPDATE_INCOMPATIBLE` and **no** signature-mismatch error.

If you see `INSTALL_FAILED_UPDATE_INCOMPATIBLE`, the two APKs were signed by different
keys — go back to Step 0, confirm the certificate digests, and fix the signing key before
retrying. Do **not** uninstall to force the install during a continuity test; uninstalling
masks the very incompatibility this protocol is meant to catch.

---

## What counts as a PASS

1. Step 0: both APKs show an **identical** `Signer #1 certificate SHA-256 digest`.
2. Step 1: first APK installs (`Success`).
3. Step 2: second APK installs with `-r` (`Success`, no
   `INSTALL_FAILED_UPDATE_INCOMPATIBLE`).

A CI or emulator result does **not** satisfy this protocol. This is a human/physical
qualification step and must be recorded as such — never label a CI run as "physically
qualified".
