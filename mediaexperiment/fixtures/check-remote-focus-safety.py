#!/usr/bin/env python3
"""Static regression gate: the experimental on-screen diagnostics must not own TV focus."""
from pathlib import Path
import re
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
java = ROOT / "src" / "main" / "java" / "com" / "scenevibe" / "tvcompanionpoc" / "mediaexperiment"
panel = (java / "DiagnosticOverlayWindow.java").read_text(encoding="utf-8")
service = (java / "InterludeService.java").read_text(encoding="utf-8")
activity = (java / "MediaExperimentActivity.java").read_text(encoding="utf-8")
operator = (java / "core" / "OperatorAction.java").read_text(encoding="utf-8")
manifest = ET.parse(ROOT / "src" / "main" / "AndroidManifest.xml").getroot()
android = "{http://schemas.android.com/apk/res/android}"

for flag in ("FLAG_NOT_FOCUSABLE", "FLAG_NOT_TOUCHABLE"):
    assert "WindowManager.LayoutParams." + flag in panel, f"REMOTE_FOCUS_UNSAFE_MISSING_{flag}"

for forbidden in ("requestFocus(", "setOnClickListener(", "addButton(", "new Button(", "FLAG_ALT_FOCUSABLE_IM"):
    assert forbidden not in panel, f"REMOTE_FOCUS_UNSAFE_CONTROL: {forbidden}"

assert "FLAG_NOT_FOCUSABLE" in panel and "windows.addView(existing, params)" in panel
assert "OperatorAction.parse(" in service
assert "OPERATOR_SETTLE_MS = 1500L" in service
assert "handler.postDelayed(pendingOperator, OPERATOR_SETTLE_MS)" in service
assert "cancelPendingOperator();" in service
assert "OperatorAction.parse(" in activity
assert "ApplicationInfo.FLAG_DEBUGGABLE" in activity
assert 'NONE("")' in operator and 'STOP("stop")' in operator and 'HIDE("hide")' in operator
assert 'android.permission.INTERNET' not in ET.tostring(manifest, encoding="unicode")
service_nodes = [
    node for node in manifest.findall("./application/service")
    if node.get(android + "name") == ".InterludeService"
]
assert len(service_nodes) == 1 and service_nodes[0].get(android + "exported") == "false", "PRIVATE_SERVICE_REQUIRED"
print("REMOTE_FOCUS_STATIC_GUARD_PASS")
