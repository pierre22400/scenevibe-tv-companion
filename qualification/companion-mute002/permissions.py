import argparse
import json
import subprocess
from pathlib import Path

"""Audit compiled permissions and the one explicitly authorized shortcut service."""


def dump(aapt, apk, kind):
    """Read actual packaged metadata through the pinned Android build tools."""
    return subprocess.check_output([aapt, "dump", kind, apk] if kind == "permissions" else [aapt, "dump", "xmltree", apk, "AndroidManifest.xml"], text=True)


def permissions(aapt, apk):
    """Compare declared and requested permissions without treating service binding protection as a user grant."""
    return sorted(line for line in dump(aapt, apk, "permissions").splitlines() if "permission:" in line)


def shortcut_service(aapt, apk):
    """Require the approved principal component, protected binding and no experimental package."""
    lines = dump(aapt, apk, "xmltree").splitlines()
    services = []
    for index, line in enumerate(lines):
        if line.lstrip().startswith("E: service"):
            indentation = len(line) - len(line.lstrip())
            end = index + 1
            while end < len(lines) and len(lines[end]) - len(lines[end].lstrip()) > indentation:
                end += 1
            services.append("\n".join(lines[index:end]))
    authorized = [service for service in services if "CompanionShortcutService" in service]
    if len(authorized) != 1 or "android.permission.BIND_ACCESSIBILITY_SERVICE" not in authorized[0] or "android.accessibilityservice" not in authorized[0]:
        raise AssertionError("Exactly one protected principal shortcut service is required")
    if any("BIND_ACCESSIBILITY_SERVICE" in service for service in services if service not in authorized):
        raise AssertionError("Unapproved additional accessibility service")
    if "netflixoverlay001" in authorized[0] or "NetflixShortcutService" in authorized[0]:
        raise AssertionError("Experimental component must not be imported")
    return "com.scenevibe.tvcompanionpoc/com.scenevibe.tvcompanionpoc.CompanionShortcutService"


def main():
    """Record unchanged uses-permissions and the permitted additional Android service declaration."""
    parser = argparse.ArgumentParser()
    parser.add_argument("aapt")
    parser.add_argument("baseline")
    parser.add_argument("candidate")
    parser.add_argument("output")
    args = parser.parse_args()
    before, after = permissions(args.aapt, args.baseline), permissions(args.aapt, args.candidate)
    if not before or before != after:
        raise AssertionError({"baseline": before, "candidate": after})
    component = shortcut_service(args.aapt, args.candidate)
    Path(args.output).write_text(json.dumps({"sameUsesPermissions": True, "baseline": before, "candidate": after, "approvedNewService": component, "bindingPermission": "android.permission.BIND_ACCESSIBILITY_SERVICE", "initialAndroidConfiguration": "AUTHORIZED_NONBLOCKING", "sonyConfiguration": "NOT_EXECUTED"}, indent=2) + "\n")


if __name__ == "__main__":
    main()
