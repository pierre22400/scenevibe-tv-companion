from pathlib import Path
import importlib.util
import json
import sys

"""
Reuse the unchanged 006 historical result counter on actual current compiled XML.
The frozen 003/006 authorization and Python assertion view are separate evidence;
this collector neither declares the changed tree to be 006 nor launches a build.
"""


def main():
    """Read the unmodified historical counting module and keep its exact per-profile requirements."""
    root = Path(__file__).resolve().parents[2]
    evidence = Path(sys.argv[1]).resolve()
    sys.path.insert(0, str(root / 'qualification/tvfirst006'))
    spec = importlib.util.spec_from_file_location('retained_006_counter', root / 'qualification/tvfirst006/run.py')
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    module.ROOT = root
    module.REPORTS = evidence / 'current-regression'
    module.REPORTS.mkdir(parents=True, exist_ok=True)
    result = module.jvm()
    (module.REPORTS / 'jvm-current.json').write_text(json.dumps(result, indent=2) + '\n')
    print(json.dumps(result, indent=2))


if __name__ == '__main__':
    main()
