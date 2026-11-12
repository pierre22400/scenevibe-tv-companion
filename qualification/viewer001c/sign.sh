#!/usr/bin/env bash
# Compile artifacts are checked against the exact v34 input and historical signing identity.
set -euo pipefail
umask 077
tools="${ANDROID_HOME:?}/build-tools/34.0.0"
output=m5-proof
mkdir -p "$output"
v34=signed-input/scenevibe-companion-public-viewer-v34-m5.apk
printf '%s  %s\n' ec03ac6eed6271351b4331df3556996092d91355f4bb3185710ce5b92f5be4cd "$v34" | sha256sum -c -
certificate=f908bf564ed97ba67e02b1ebc89eb0239cf980752587f55eb9ec0419791a2e9c
tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT
printf '%s' "${SCENEVIBE_ANDROID_KEYSTORE_BASE64:?}" | base64 -d > "$tmp/m5.jks"

# Check the release package, Android signature schemes and the single pinned M5 certificate.
verify() {
    local file="$1" version="$2"
    "$tools/aapt" dump badging "$file" > "$output/v$version-badging.txt"
    grep -Fq "package: name='com.scenevibe.tvcompanionpoc' versionCode='$version'" "$output/v$version-badging.txt"
    ! grep -q 'application-debuggable' "$output/v$version-badging.txt"
    "$tools/apksigner" verify --verbose --print-certs "$file" > "$output/v$version-signature.txt"
    grep -Fxq "Signer #1 certificate SHA-256 digest: $certificate" "$output/v$version-signature.txt"
    grep -Fxq 'Number of signers: 1' "$output/v$version-signature.txt"
    grep -Fxq 'Verified using v2 scheme (APK Signature Scheme v2): true' "$output/v$version-signature.txt"
    grep -Fxq 'Verified using v3 scheme (APK Signature Scheme v3): true' "$output/v$version-signature.txt"
}

# Align an unsigned input and sign it with the already provisioned historical key.
sign() {
    local file="$1" destination="$2"
    "$tools/zipalign" -f 4 "$file" "$tmp/aligned.apk"
    "$tools/apksigner" sign --ks "$tmp/m5.jks" --ks-pass env:SCENEVIBE_ANDROID_KEYSTORE_PASSWORD \
        --ks-key-alias "$SCENEVIBE_ANDROID_KEY_ALIAS" --key-pass env:SCENEVIBE_ANDROID_KEY_PASSWORD \
        --v2-signing-enabled true --v3-signing-enabled true --out "$destination" "$tmp/aligned.apk"
}

verify "$v34" 34
sign app/build/outputs/apk/tvux002/app-tvux002-unsigned.apk "$output/scenevibe-companion-viewer001c-v36-m5.apk"
sign recovery-source/app/build/outputs/apk/tvux002/app-tvux002-unsigned.apk "$output/scenevibe-companion-recovery-v37-m5.apk"
sign app/build/outputs/apk/androidTest/tvux002/app-tvux002-androidTest.apk "$RUNNER_TEMP/viewer001c-tests-m5.apk"
verify "$output/scenevibe-companion-viewer001c-v36-m5.apk" 36
verify "$output/scenevibe-companion-recovery-v37-m5.apk" 37
(cd "$output" && sha256sum -- ./*.apk > SHA256SUMS)
printf 'candidat %s\nrécupération %s\nqualification HTTPS %s\n' "$(git rev-parse HEAD)" "$(git -C recovery-source rev-parse HEAD)" "${QUALIFICATION_RUN:?}" > "$output/provenance.txt"
