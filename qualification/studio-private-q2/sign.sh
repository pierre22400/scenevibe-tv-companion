#!/usr/bin/env bash
# Signe le candidat v40 avec la routine M5 historique, uniquement dans Actions.
set -euo pipefail
umask 077
tools="${ANDROID_HOME:?}/build-tools/34.0.0"
proof=m5-proof
v38=signed-input/scenevibe-companion-product-consolidation-v38-m5.apk
v40="$proof/scenevibe-companion-studio-private-q2-v40-m5.apk"
unsigned=app/build/outputs/apk/tvux002/app-tvux002-unsigned.apk
tests=app/build/outputs/apk/androidTest/tvux002/app-tvux002-androidTest.apk
mkdir -p "$proof"
certificate=f908bf564ed97ba67e02b1ebc89eb0239cf980752587f55eb9ec0419791a2e9c
tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT
printf '%s' "${SCENEVIBE_ANDROID_KEYSTORE_BASE64:?}" | base64 -d > "$tmp/m5.jks"

# Charge les deux routines historiques sans les opérations v34/v36/v37.
sed -n '/^verify()/,/^}/p; /^sign()/,/^}/p' qualification/viewer001c/sign.sh > "$tmp/m5-functions.sh"
source "$tmp/m5-functions.sh"
if [[ "$#" -eq 1 && "$1" == tests ]]; then
    signed_tests="$RUNNER_TEMP/studio-q2-v40-tests-m5.apk"
    sign "$tests" "$signed_tests"
    "$tools/apksigner" verify --print-certs "$signed_tests" | grep -Fx "Signer #1 certificate SHA-256 digest: $certificate"
    exit 0
fi
test "$#" -eq 0
printf '%s  %s\n' 003f1a74c420b909a0fa241ed1cc674b86c17ef00c5b67105c84e463e9945b81 "$v38" | sha256sum -c -
output="$proof"
verify "$v38" 38
sign "$unsigned" "$v40"
sign "$tests" "$RUNNER_TEMP/studio-q2-v40-tests-m5.apk"
verify "$v40" 40
for version in 38 40; do
    awk -F "'" '/^uses-permission/ {print $2}' "$proof/v$version-badging.txt" | sort -u > "$proof/v$version-permissions.txt"
    test -s "$proof/v$version-permissions.txt"
done
diff "$proof/v38-permissions.txt" "$proof/v40-permissions.txt"
"$tools/aapt" dump xmltree "$v40" AndroidManifest.xml > "$proof/v40-manifest.txt"
grep -Eq 'versionName=.{0,4}[^ ]*-studio-private-q2-v40' "$proof/v40-badging.txt"
sha256sum "$unsigned" > "$proof/unsigned-sha256.txt"
(cd "$proof" && sha256sum -- ./*.apk > SHA256SUMS)
{
  printf 'android=%s\n' "$(git rev-parse HEAD)"
  printf 'variant=tvux002\nexp=studio-private-q2-v40\n'
  printf 'private_historical=https://scenevibe-autonomous001d-qual-preview.vercel.app\n'
  printf 'public_historical=https://scenevibe-public-viewer001-qual.vercel.app\n'
  printf 'studio_private=https://scenevibe-product-consolidation001q2-studi-git-5fd51d-archc-ode.vercel.app\n'
  printf 'v38_actions_run=43441020236\n'
} > "$proof/provenance.txt"
