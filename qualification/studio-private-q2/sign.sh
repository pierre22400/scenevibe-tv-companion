#!/usr/bin/env bash
# Signe les candidats Q2 et v41 avec la routine M5 historique, uniquement dans Actions.
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
baseline="$v38"
baseline_code=38
baseline_sha=003f1a74c420b909a0fa241ed1cc674b86c17ef00c5b67105c84e463e9945b81
baseline_run=43441020236
candidate="$v40"
candidate_code=40
experiment=studio-private-q2-v40
if [[ "${SCENEVIBE_SIGN_V41:-}" == true ]]; then
    baseline="signed-input/scenevibe-companion-studio-private-q2-v40-m5.apk"
    baseline_code=40
    baseline_sha=f48931b03c1e4fcbae89bde04620558cac303385392e78682773a894c4b56915
    baseline_run=43643847974
    candidate="$proof/scenevibe-companion-media-universal-v41-m5.apk"
    candidate_code=41
    experiment=media-universal-v41
fi
printf '%s  %s\n' "$baseline_sha" "$baseline" | sha256sum -c -
output="$proof"
verify "$baseline" "$baseline_code"
sign "$unsigned" "$candidate"
sign "$tests" "$RUNNER_TEMP/studio-q2-v40-tests-m5.apk"
verify "$candidate" "$candidate_code"
for version in "$baseline_code" "$candidate_code"; do
    awk -F "'" '/^uses-permission/ {print $2}' "$proof/v$version-badging.txt" | sort -u > "$proof/v$version-permissions.txt"
    test -s "$proof/v$version-permissions.txt"
done
diff "$proof/v$baseline_code-permissions.txt" "$proof/v$candidate_code-permissions.txt"
"$tools/aapt" dump xmltree "$candidate" AndroidManifest.xml > "$proof/v$candidate_code-manifest.txt"
grep -Eq "versionName=.{0,4}[^ ]*-$experiment" "$proof/v$candidate_code-badging.txt"
sha256sum "$unsigned" > "$proof/unsigned-sha256.txt"
(cd "$proof" && sha256sum -- ./*.apk > SHA256SUMS)
{
  printf 'android=%s\n' "$(git rev-parse HEAD)"
  printf 'variant=tvux002\nexp=%s\n' "$experiment"
  printf 'private_historical=https://scenevibe-autonomous001d-qual-preview.vercel.app\n'
  printf 'public_historical=https://scenevibe-public-viewer001-qual.vercel.app\n'
  printf 'studio_private=https://scenevibe-product-consolidation001q2-studi-git-5fd51d-archc-ode.vercel.app\n'
  printf 'v%s_actions_run=%s\n' "$baseline_code" "$baseline_run"
} > "$proof/provenance.txt"
