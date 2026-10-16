"""Verify this presentation delta without changing any historical baseline or assertion.

The frozen M4/M5 Python suites intentionally pin their original whole-tree inventory.
This gate separately verifies every protected base byte, retains all historical tests,
and checks the only two explicitly modified tracked files before qualifying new UI code.
"""

from pathlib import Path
import hashlib
import json
import re
import subprocess
import sys
import xml.etree.ElementTree as ET
import zipfile

ROOT = Path(__file__).resolve().parents[2]
BASE = "17cbe36ae99ac7f48aaf861e0d1feac702a9e521"
JAVA = "app/src/main/java/com/scenevibe/tvcompanionpoc/"
MODIFIED = {"app/build.gradle", JAVA + "MainActivity.java"}


def git(*args):
    """Read the immutable Git baseline; no remote operation or index mutation is performed."""
    return subprocess.check_output(["git", "-C", str(ROOT), *args])


def digest(data):
    """Return the SHA-256 of retained source or resource bytes."""
    return hashlib.sha256(data).hexdigest()


def method(source, signature):
    """Extract one unchanged legacy Java body for exact preservation checks."""
    start = source.index(signature)
    opening = source.index("{", start)
    depth = 1
    for end in range(opening + 1, len(source)):
        depth += (source[end] == "{") - (source[end] == "}")
        if depth == 0:
            return source[start:end + 1]
    raise AssertionError("Unbalanced source: " + signature)


def luminance(hex_color):
    """Compute WCAG relative luminance from the actual 8-bit sRGB resource."""
    channels = [int(hex_color[i:i + 2], 16) / 255 for i in (1, 3, 5)]
    linear = [v / 12.92 if v <= .04045 else ((v + .055) / 1.055) ** 2.4 for v in channels]
    return .2126 * linear[0] + .7152 * linear[1] + .0722 * linear[2]


def contrast(foreground, background):
    """Measure the ratio for two real token colors rather than guessing from their names."""
    a, b = sorted([luminance(foreground), luminance(background)])
    return (b + .05) / (a + .05)


def verify():
    """Audit protected code, UI boundaries, typography assets and release fixture exclusion."""
    assert git("rev-parse", "HEAD").decode().strip() == BASE
    files = git("ls-tree", "-r", "--name-only", BASE).decode().splitlines()
    protected = {}
    for name in files:
        original = git("show", BASE + ":" + name)
        current = (ROOT / name).read_bytes()
        if name not in MODIFIED:
            assert current == original, "Protected baseline changed: " + name
            protected[name] = digest(current)
    path = JAVA + "MainActivity.java"
    original = git("show", BASE + ":" + path).decode()
    current = (ROOT / path).read_text()
    signatures = ["private void startPairing()", "private void resetPairing()", "private void refreshPairing()",
                  "private void refreshCloud()", "static String cloudStatus(", "private void connectCloud()",
                  "private void disconnectCloud()", "private void openDiagnostics()", "private String lanIpv4()",
                  "private void toggleAutostart()", "private String autostartLabel()", "private Button addButton(",
                  "private void openPermissionSettings()", "private void openMediaSessionAccessSettings()",
                  "private boolean startOverlay(", "private void stopOverlay()", "private int dp("]
    for signature in signatures:
        assert method(original, signature) == method(current, signature), "Legacy action changed: " + signature
    old_controls = original[original.index("        LinearLayout controls"):original.index("        ScrollView scroll")]
    new_controls = current[current.index("        LinearLayout controls = new LinearLayout"):current.index("        return controls;")]
    assert old_controls == new_controls, "Legacy controls or listeners were removed"
    old_docstrings = re.findall(r"/\*\*.*?\*/", original, re.S)
    assert all(comment in current for comment in old_docstrings), "An existing docstring was removed"
    assert "presentation.remember()" in method(current, "protected void onPause()")
    assert "stopService" not in method(current, "protected void onPause()")
    gradle = (ROOT / "app/build.gradle").read_text()
    baseline_gradle = git("show", BASE + ":app/build.gradle").decode()
    for signature in ["applicationId", "minSdk", "targetSdk", "versionCode", "versionName", "signingConfig", "CLOUD_ORIGIN", "ENABLE_LAN_DEV"]:
        pattern = r"^.*" + re.escape(signature) + r".*$"
        assert re.findall(pattern, gradle, re.M) == re.findall(pattern, baseline_gradle, re.M), signature
    presentation = ROOT / JAVA / "presentation"
    banned = ["new CloudControlClient", "new PackageInstaller", "new MediaCalendarScheduler", "new SceneRenderer",
              "new WebView", "HttpURLConnection", "SecretStore", "startForegroundService", "deviceToken(", "postDelayed("]
    for java in presentation.glob("*.java"):
        executable = re.sub(r"/\*.*?\*/|//[^\n]*", "", java.read_text(), flags=re.S)
        for token in banned:
            assert token not in executable, str(java) + ": " + token
    demo = (ROOT / "app/src/debug/java/com/scenevibe/tvcompanionpoc/presentation/TvSourceFactory.java").read_text()
    for token in ["postDelayed", "Timer(", "Thread(", "HttpURLConnection", "deviceToken", "startService"]:
        assert token not in demo, "Development simulation crossed a boundary: " + token
    colors = {el.attrib["name"]: el.text for el in ET.parse(ROOT / "app/src/main/res/values/scenevibe_colors.xml").getroot()}
    pairs = [("foreground", "background"), ("foreground", "card"), ("muted_foreground", "background"),
             ("muted_foreground", "muted"), ("primary", "background"), ("primary_foreground", "primary"),
             ("secondary_foreground", "secondary"), ("sidebar_foreground", "sidebar"),
             ("sidebar_accent_foreground", "sidebar_accent")]
    ratios = {}
    for fg, bg in pairs:
        ratio = contrast(colors["scenevibe_" + fg], colors["scenevibe_" + bg])
        assert ratio >= 4.5, "Text contrast below 4.5: " + fg + "/" + bg
        ratios[fg + "/" + bg] = round(ratio, 3)
    provenance = ROOT / "app/src/main/assets/scenevibe/provenance"
    assets = json.loads((provenance / "assets-manifest.json").read_text())
    for asset in assets:
        assert digest((ROOT / "app/src/main" / asset["resource"]).read_bytes()) == asset["sha256"]
        if "sourceSha256" in asset:
            original = provenance / Path(asset["source"]).name
            assert digest(original.read_bytes()) == asset["sourceSha256"]
    assert "SIL OPEN FONT LICENSE Version 1.1" in (provenance / "inter-OFL.txt").read_text()
    assert "SIL OPEN FONT LICENSE Version 1.1" in (provenance / "spacegrotesk-OFL.txt").read_text()
    apks = {}
    for variant, filename in [("debug", "app-debug.apk"), ("release", "app-release-unsigned.apk")]:
        apk = ROOT / "app/build/outputs/apk" / variant / filename
        with zipfile.ZipFile(apk) as archive:
            has_demo = "assets/tvfirst002/demo-offers.json" in archive.namelist()
            assert has_demo == (variant == "debug"), "Fixture packaging mismatch: " + variant
            has_demo_class = any(b"TvSourceFactory$LocalDemo;" in archive.read(name)
                                 for name in archive.namelist() if re.fullmatch(r"classes\d*\.dex", name))
            assert has_demo_class == (variant == "debug"), "Demo class packaging mismatch: " + variant
            if variant == "release":
                assert not any(n.startswith("META-INF/") and n.endswith((".RSA", ".DSA", ".EC")) for n in archive.namelist())
        apks[variant] = {"sha256": digest(apk.read_bytes()), "localDemoCorpusPresent": has_demo,
                         "localDemoClassPresent": has_demo_class,
                         "deliveredAsPhysicalCandidate": False}
    return {"base": BASE, "protectedFiles": len(protected), "legacyMethodsRetainedExactly": len(signatures),
            "allExistingDocstringsRetained": True, "contrastRatios": ratios, "assetsChecked": len(assets),
            "apkPackaging": apks, "protectedSha256": protected, "verdict": "PASS"}


if __name__ == "__main__":
    result = verify()
    output = Path(sys.argv[1]) if len(sys.argv) > 1 else ROOT / "build/tvfirst002/delivery-integrity.json"
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n")
    print(json.dumps({k: v for k, v in result.items() if k != "protectedSha256"}, ensure_ascii=False, indent=2))
