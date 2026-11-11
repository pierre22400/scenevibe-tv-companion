#!/usr/bin/env bash
# Same public/private native suites, plus the actual configured Android shortcut path.
set -euo pipefail
bash .github/scripts/public-autonomous001b-native.sh
adb exec-out run-as com.scenevibe.tvcompanionpoc cat files/companion-mute002-p0-open.json > public001b-native/mute-p0-open.json
adb exec-out run-as com.scenevibe.tvcompanionpoc cat files/companion-mute002-private-proof.json > public001b-native/mute-private-proof.json
adb exec-out run-as com.scenevibe.tvcompanionpoc cat files/companion-mute002-public-proof.json > public001b-native/mute-public-proof.json
adb logcat -d -v brief -s SceneVibeMute002:I SceneVibeControls:I SceneVibeRuntime:I AndroidRuntime:E > public001b-native/mute-diagnostics.log
