#!/usr/bin/env python3
"""AUDIO 003 CI evidence: refuse source drift, missing tests or an unsafe APK.

Uses only the checked-out sources, Gradle XML results and installed Android tools.
It never installs an APK, accesses stable signing material or calls a Cloud API.
"""
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import xml.etree.ElementTree as ET

BASE = "04c1b8c4efc886ea1df0ec546c0e587d500a4fda"
M5 = "f908bf564ed97ba67e02b1ebc89eb0239cf980752587f55eb9ec0419791a2e9c"
OUT = Path("build/audio003")
OUT.mkdir(parents=True, exist_ok=True)


def command(*args):
    """Run a read-only verification command; any nonzero exit fails qualification."""
    return subprocess.check_output(args, text=True, stderr=subprocess.STDOUT)


def save(name, value):
    """Persist machine-readable evidence alongside the uploaded CI reports."""
    (OUT / name).write_text(json.dumps(value, indent=2) + "\n")


def source():
    """Keep AUDIO 002B functional bytes unchanged and reject active focus/transport APIs."""
    allowed = {"app/build.gradle", ".github/workflows/android-debug.yml",
               ".github/scripts/audio-003-verify.py"}
    changed = command("git", "diff", "--name-only", BASE, "HEAD").splitlines()
    assert set(changed) <= allowed, changed
    forbidden = r"\b(requestAudioFocus|AudioFocusRequest|getTransportControls|dispatchMediaKeyEvent|setStreamVolume)\s*\("
    for path in Path("app/src/main/java").rglob("*.java"):
        # Explanatory comments do not constitute calls to Android capabilities.
        code = re.sub(r"/\*.*?\*/|//[^\n]*", "", path.read_text(), flags=re.S)
        assert not re.search(forbidden, code), str(path)
    save("source.json", {"reference": BASE, "head": command("git", "rev-parse", "HEAD").strip(),
                         "changed_files": changed, "functional_source_bytes_unchanged": True,
                         "no_explicit_focus_or_prime_transport_calls": True})


def tests():
    """Require all seven AUDIO classes and actual Android lifecycle/view tests, with no fake green."""
    suites = []
    skipped = []
    for path in sorted(Path("app/build/test-results/testAudioexpUnitTest").glob("TEST-*.xml")):
        root = ET.parse(path).getroot()
        item = {key: int(root.get(key, "0")) for key in ("tests", "failures", "errors", "skipped")}
        item["name"] = root.get("name")
        assert item["failures"] == 0 and item["errors"] == 0, item
        for case in root.findall("testcase"):
            if case.find("skipped") is not None:
                skipped.append(root.get("name") + "." + case.get("name"))
        suites.append(item)
    assert suites, "No Gradle XML results"
    audio = [s for s in suites if ".audio." in s["name"]]
    assert len(audio) == 7 and sum(s["tests"] for s in audio) == 62, audio
    assert sum(s["skipped"] for s in audio) == 0, audio
    for name in ("TvNativeViewsTest", "TvFirstDefaultServiceTest"):
        matching = [s for s in suites if s["name"].endswith("." + name)]
        assert len(matching) == 1 and matching[0]["tests"] > 0 and matching[0]["skipped"] == 0, name
    # The historical private editorial fixture is intentionally unavailable in public CI.
    assert all(s == "com.scenevibe.tvcompanionpoc.M1CloudInteropTest.originalColumboProjectionIsInstallable"
               for s in skipped), skipped
    save("tests.json", {"total": {k: sum(s[k] for s in suites) for k in
                                  ("tests", "failures", "errors", "skipped")},
                        "audio_executed": 62, "skipped_test_names": skipped, "suites": suites})


def apk():
    """Inspect the built APK and compare its signer to the runner's disposable debug certificate."""
    path = Path("app/build/outputs/apk/audioexp/app-audioexp.apk")
    assert path.is_file(), str(path)
    tools = Path(os.environ["ANDROID_HOME"]) / "build-tools/35.0.0"
    badging = command(str(tools / "aapt"), "dump", "badging", str(path))
    (OUT / "apk-badging.txt").write_text(badging)
    match = re.search(r"package: name='([^']+)' versionCode='([^']+)' versionName='([^']+)'", badging)
    assert match, "No package identity"
    package, code, version = match.groups()
    assert package == "com.scenevibe.tvcompanionpoc.audioexp", package
    assert "application-debuggable" in badging, "Not debuggable"
    certs = command(str(tools / "apksigner"), "verify", "--verbose", "--print-certs", str(path))
    (OUT / "apk-signature.txt").write_text(certs)
    assert "Verified using v2 scheme (APK Signature Scheme v2): true" in certs, certs
    digests = re.findall(r"Signer #\d+ certificate SHA-256 digest: ([0-9a-fA-F]+)", certs)
    assert len(digests) == 1, certs
    digest = digests[0].lower()
    assert digest != M5, "Stable M5 certificate forbidden"
    assert "CN=Android Debug" in certs, "Not an Android debug signer"
    # AGP resolves the user directory from Android preferences; do not guess it.
    signing = (OUT / "signing-report.txt").read_text()
    section = re.search(r"Variant: audioexp\n(.*?)(?:\nVariant:|\Z)", signing, re.S)
    assert section, "No audioexp signing report"
    store = re.search(r"^Store: (.+)$", section.group(1), re.M)
    alias = re.search(r"^Alias: (.+)$", section.group(1), re.M)
    assert store and alias and alias.group(1).strip().lower() == "androiddebugkey", "Not debug signing"
    debug_store = Path(store.group(1).strip())
    assert debug_store.name == "debug.keystore" and debug_store.is_file(), "Missing runner debug key"
    public_cert = OUT / "debug-certificate.der"
    command("keytool", "-exportcert", "-alias", "androiddebugkey", "-keystore",
            str(debug_store), "-storepass", "android",
            "-file", str(public_cert))
    assert hashlib.sha256(public_cert.read_bytes()).hexdigest() == digest, "Not this runner's debug key"
    public_cert.unlink()  # Public certificate only; the keystore is never included in artifacts.
    lint = ET.parse("app/build/reports/lint-results-audioexp.xml").getroot()
    issues = list(lint.findall("issue"))
    assert not any(i.get("severity") in ("Error", "Fatal") for i in issues), "Lint errors"
    delivery = OUT / "delivery"
    delivery.mkdir(exist_ok=True)
    target = delivery / "scenevibe-audio003-audioexp-debug.apk"
    shutil.copyfile(path, target)
    sha = hashlib.sha256(target.read_bytes()).hexdigest()
    info = {"head": command("git", "rev-parse", "HEAD").strip(), "application_id": package,
            "version_code": int(code), "version_name": version,
            "min_sdk": re.search(r"sdkVersion:'([^']+)'", badging).group(1),
            "target_sdk": re.search(r"targetSdkVersion:'([^']+)'", badging).group(1),
            "apk": target.name, "size_bytes": target.stat().st_size, "sha256": sha,
            "signer_certificate_sha256": digest, "signing": "disposable Android debug; not M5",
            "lint_warning_count": sum(i.get("severity") == "Warning" for i in issues),
            "run_url": f"https://github.com/{os.environ['GITHUB_REPOSITORY']}/actions/runs/{os.environ['GITHUB_RUN_ID']}",
            "physical_audio_qualified": False}
    (delivery / "provenance.json").write_text(json.dumps(info, indent=2) + "\n")
    (delivery / "SHA256SUMS.txt").write_text(sha + "  " + target.name + "\n")
    save("apk.json", info)
    print(json.dumps(info, indent=2))


if __name__ == "__main__":
    {"source": source, "tests": tests, "apk": apk}[sys.argv[1]]()
