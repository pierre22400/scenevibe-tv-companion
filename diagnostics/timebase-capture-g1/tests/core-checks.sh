#!/usr/bin/env bash
# Pure Java tests in a temporary directory; no Android device, repository mutation or network.
set -euo pipefail
probe_root="$(cd "$(dirname "$0")/.." && pwd)"
classes="$(mktemp -d)"
trap 'rm -rf "$classes"' EXIT
sources="$probe_root/app/src/main/java/com/scenevibe/timebasecaptureg1"
java com.sun.tools.javac.Main -d "$classes" "$sources/CapturePolicy.java" "$sources/PixelProbe.java" "$probe_root/tests/CoreChecks.java"
java -cp "$classes" CoreChecks
