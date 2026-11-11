#!/usr/bin/env bash
# Build the owned MEDIA fixture with an ephemeral key, independently of M5.
set -euo pipefail
tools="${ANDROID_HOME:?}/build-tools/34.0.0"
platform="$ANDROID_HOME/platforms/android-35/android.jar"
source_dir=qualification/sony001a/player
fixture_tmp="$(mktemp -d)"
trap 'rm -rf "$fixture_tmp"' EXIT
mkdir -p "$fixture_tmp/classes" "$fixture_tmp/dex"
javac -encoding UTF-8 --release 8 -classpath "$platform" -d "$fixture_tmp/classes" "$source_dir/OwnedPlayer.java"
"$tools/d8" --min-api 31 --lib "$platform" --output "$fixture_tmp/dex" \
    "$fixture_tmp/classes/com/amazon/amazonvideo/livingroom/OwnedPlayer.class"
"$tools/aapt" package -f -M "$source_dir/AndroidManifest.xml" -I "$platform" -F "$fixture_tmp/fixture.apk"
(cd "$fixture_tmp/dex" && "$tools/aapt" add "$fixture_tmp/fixture.apk" classes.dex)
"$tools/zipalign" -f 4 "$fixture_tmp/fixture.apk" "$fixture_tmp/aligned.apk"
keytool -genkeypair -storetype PKCS12 -keystore "$fixture_tmp/fixture.p12" -storepass fixture-only \
    -keypass fixture-only -alias fixture -dname 'CN=Owned Emulator MEDIA' -keyalg RSA -validity 30 > /dev/null 2>&1
"$tools/apksigner" sign --ks "$fixture_tmp/fixture.p12" --ks-pass pass:fixture-only \
    --out "$1" "$fixture_tmp/aligned.apk"
