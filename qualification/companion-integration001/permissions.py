import argparse
import json
import subprocess
from pathlib import Path

"""Compare the actual merged APK permissions and protected services before signing."""


def permissions(aapt, apk):
    """Read all permission declarations from the compiled manifest, never from a source-only approximation."""
    output = subprocess.check_output([aapt, "dump", "permissions", apk], text=True)
    return sorted(line for line in output.splitlines() if "permission:" in line)


def components(aapt, apk):
    """Read the merged component tree to reject any imported accessibility service."""
    output = subprocess.check_output([aapt, "dump", "xmltree", apk, "AndroidManifest.xml"], text=True)
    if "BIND_ACCESSIBILITY_SERVICE" in output or "android.accessibilityservice" in output:
        raise AssertionError("New accessibility service or configuration is forbidden")
    return output


def main():
    """Fail closed unless the signed v31 and candidate declare exactly the same permissions."""
    parser = argparse.ArgumentParser()
    parser.add_argument("aapt")
    parser.add_argument("baseline")
    parser.add_argument("candidate")
    parser.add_argument("output")
    args = parser.parse_args()
    baseline = permissions(args.aapt, args.baseline)
    candidate = permissions(args.aapt, args.candidate)
    if not baseline or baseline != candidate:
        raise AssertionError({"baseline": baseline, "candidate": candidate})
    components(args.aapt, args.baseline)
    components(args.aapt, args.candidate)
    Path(args.output).write_text(json.dumps({"sameMergedPermissions": True, "newPermissions": [], "newAccessibilityService": False, "baseline": baseline, "candidate": candidate}, indent=2) + "\n")


if __name__ == "__main__":
    main()
