#!/usr/bin/env bash
# Standalone pure-Java software checks; no Android, player, network or production secret.
set -euo pipefail
probe_root="$(cd "$(dirname "$0")/.." && pwd)"
classes="$(mktemp -d)"
trap 'rm -rf "$classes"' EXIT
sources="$probe_root/app/src/main/java/com/scenevibe/accessibilityprobe004"
java com.sun.tools.javac.Main -d "$classes" \
  "$sources/ProbeLimits.java" "$sources/ProbeProfile.java" "$sources/ProbeClock.java" \
  "$sources/SafeRow.java" "$sources/TextProjection.java" "$sources/JournalStore.java" \
  "$sources/ObservationCadence.java" "$sources/SurveyNode.java" "$sources/NodeFacts.java" "$sources/SurveyEngine.java" \
  "$sources/ProbeController.java" "$probe_root/tests/ProbeCoreChecks.java"
java -cp "$classes" com.scenevibe.accessibilityprobe004.ProbeCoreChecks
