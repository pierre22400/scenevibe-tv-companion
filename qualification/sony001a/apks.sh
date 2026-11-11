#!/usr/bin/env bash
# Verify exact input bytes, compare compiled grants, and sign only on the isolated CI branch.
set -euo pipefail

build_tools="${ANDROID_HOME:?}/build-tools/34.0.0"
reference=reference-input/tv-ux002-signed/scenevibe-companion-mute-integration002-v32-signed.apk
mapfile -t inputs < <(find candidate-input -type f -name app-tvux002-unsigned.apk)
[[ "${#inputs[@]}" == 1 ]]
candidate="${inputs[0]}"
recovery=recovery-source/app/build/outputs/apk/tvux002/app-tvux002-unsigned.apk
output=sony-proof
mkdir -p "$output"
certificate=f908bf564ed97ba67e02b1ebc89eb0239cf980752587f55eb9ec0419791a2e9c

# Read a nondebuggable production manifest and normalize its uses-permissions.
inspect() {
    local apk="$1" version="$2" stage="$3"
    "$build_tools/aapt" dump badging "$apk" > "$output/$stage-badging.txt"
    grep -Fq "package: name='com.scenevibe.tvcompanionpoc' versionCode='$version'" "$output/$stage-badging.txt"
    if grep -q application-debuggable "$output/$stage-badging.txt"; then return 1; fi
    "$build_tools/aapt" dump permissions "$apk" | sed -n "s/^uses-permission[^:]*: name='\([^']*\)'.*/\1/p" | sort -u > "$output/$stage-permissions.txt"
    test -s "$output/$stage-permissions.txt"
    "$build_tools/aapt" dump xmltree "$apk" AndroidManifest.xml > "$output/$stage-manifest.txt"
    grep -Fq 'CompanionShortcutService' "$output/$stage-manifest.txt"
    grep -Fq 'android.permission.BIND_ACCESSIBILITY_SERVICE' "$output/$stage-manifest.txt"
}

# Confirm Android signature schemes and pin the single historical signer.
verify() {
    "$build_tools/apksigner" verify --verbose --print-certs "$1" > "$output/$2-signature.txt"
    grep -Fxq "Signer #1 certificate SHA-256 digest: $certificate" "$output/$2-signature.txt"
    grep -Fxq 'Number of signers: 1' "$output/$2-signature.txt"
    grep -Fxq 'Verified using v2 scheme (APK Signature Scheme v2): true' "$output/$2-signature.txt"
    grep -Fxq 'Verified using v3 scheme (APK Signature Scheme v3): true' "$output/$2-signature.txt"
}

# Preserve APK input bytes and sign a temporary aligned copy.
sign() {
    "$build_tools/zipalign" -f 4 "$1" "$sign_dir/aligned.apk"
    "$build_tools/apksigner" sign --ks "$sign_dir/m5.jks" --ks-pass env:SCENEVIBE_ANDROID_KEYSTORE_PASSWORD \
        --ks-key-alias "$SCENEVIBE_ANDROID_KEY_ALIAS" --key-pass env:SCENEVIBE_ANDROID_KEY_PASSWORD \
        --v2-signing-enabled true --v3-signing-enabled true --out "$2" "$sign_dir/aligned.apk"
}

if [[ "$1" == preflight ]]; then
    printf '%s  %s\n' d4e45b19dd6fd99e6ab6d7cdfa4dfd31e04bac8a50d43e366ba284ff3c5e612b "$reference" \
        7084c6353105982f35696dd35e39352416b6af57794bec21a7ec120cdba703a6 "$candidate" | sha256sum -c -
    inspect "$reference" 32 v32
    inspect "$candidate" 34 v34
    verify "$reference" v32
    if "$build_tools/apksigner" verify "$candidate" > /dev/null 2>&1; then exit 1; fi
    diff <(cat "$output/v32-permissions.txt" <(printf '%s\n' android.permission.RECEIVE_BOOT_COMPLETED) | sort -u) "$output/v34-permissions.txt"
    grep -Fq 'PublicViewerBootReceiver' "$output/v34-manifest.txt"
    printf '%s\n' 'v32 source 01da23263c0d042192d536c2ed497b6b34daecc1' \
        'v34 source b2b5a1c4339bca517e6a4997d943f456ddc0f4e2' \
        'v34 build b1ed6637e050348247e21b3526fe1dcc9101f546' \
        'v35 recovery 0c252fc9f2c57e2a12c367187d9ecf9a23570510' > "$output/sources.txt"
    exit 0
fi

[[ "$1" == sign ]]
inspect "$recovery" 35 v35
diff "$output/v32-permissions.txt" "$output/v35-permissions.txt"
diff "$output/v32-manifest.txt" "$output/v35-manifest.txt" > "$output/v32-v35-manifest-diff.txt" || [[ "$?" == 1 ]]
: "${SCENEVIBE_ANDROID_KEYSTORE_BASE64:?}" "${SCENEVIBE_ANDROID_KEYSTORE_PASSWORD:?}" "${SCENEVIBE_ANDROID_KEY_ALIAS:?}" "${SCENEVIBE_ANDROID_KEY_PASSWORD:?}"
umask 077
sign_dir="$(mktemp -d)"
trap 'rm -rf "$sign_dir"' EXIT
printf '%s' "$SCENEVIBE_ANDROID_KEYSTORE_BASE64" | base64 -d > "$sign_dir/m5.jks"
cp "$reference" "$output/scenevibe-companion-v32-reference.apk"
sign "$candidate" "$output/scenevibe-companion-public-viewer-v34-m5.apk"
sign "$recovery" "$output/scenevibe-companion-recovery-v35-m5.apk"
verify "$output/scenevibe-companion-public-viewer-v34-m5.apk" v34
verify "$output/scenevibe-companion-recovery-v35-m5.apk" v35
(cd "$output" && sha256sum -- ./*.apk > SHA256SUMS)
