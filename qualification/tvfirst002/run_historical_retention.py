"""Run unmodified historical assertions on the exactly retained historical source view.

This is a retention proof, not a claim that the old fixed inventory accepts new UI files.
The current-tree integrity gate must pass first. All current legacy Java, fixtures, tests,
baselines and workflows are copied byte-for-byte; only the two declared presentation edits
are projected to their pinned original bytes and the explicitly new UI files are omitted.
The actual current Android application is separately compiled and tested by Gradle.
"""

from pathlib import Path
import os
import subprocess
import sys
import tempfile
from verify_delivery import BASE, MODIFIED, ROOT, git, verify


def run():
    """Seal the retained tree, then execute every original Python assertion without filtering."""
    result = verify()
    print("Current-tree integrity:", result["verdict"], "protected files:", result["protectedFiles"], flush=True)
    with tempfile.TemporaryDirectory(prefix="scenevibe-tv-first-002-retained-") as directory:
        destination = Path(directory)
        for name in git("ls-tree", "-r", "--name-only", BASE).decode().splitlines():
            target = destination / name
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(git("show", BASE + ":" + name) if name in MODIFIED else (ROOT / name).read_bytes())
        return subprocess.run([sys.executable, "-m", "unittest", "discover", "-s", "tests", "-v"],
                              cwd=destination, env=os.environ.copy()).returncode


if __name__ == "__main__":
    raise SystemExit(run())
