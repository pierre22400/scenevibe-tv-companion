import hashlib
import json
from pathlib import Path

"""Finite E5 provenance admission preceding the unchanged M4/M5 historical scopes.

Historical byte/scope assertions reverse ONLY the reviewed E5 hunks to the exact
17cbe36 base. Actual JVM, Android and E5 boundary tests execute/check candidate
bytes. No old baseline, test method, assertion or skip is removed. This module
never obtains baseline content from a mutable branch or substitutes an unchecked
Git checkout: full after/before blob hashes and unique literal inverses are required.
"""

ROOT = Path(__file__).resolve().parents[1]
LEDGER = ROOT / '.github/scripts/e5-native-035-provenance.json'
BASE_HEAD = '17cbe36ae99ac7f48aaf861e0d1feac702a9e521'


def blob_hash(content):
    """Compute the full Git blob identity for bytes, including comments and whitespace."""
    return hashlib.sha1(b'blob ' + str(len(content)).encode() + b'\0' + content).hexdigest()


def ledger():
    """Load only this explicit immutable-base admission, never a namespace wildcard."""
    data = json.loads(LEDGER.read_text(encoding='utf-8'))
    if data['baseHead'] != BASE_HEAD:
        raise ValueError('Unknown E5 base')
    if any('*' in path for path in set(data['changes']) | set(data['additions'])):
        raise ValueError('Unbounded E5 scope')
    return data


def retained_from_bytes(path, content):
    """Reverse uniquely reviewed E5 literals and verify both whole-file identities."""
    try:
        relative = str(Path(path).resolve().relative_to(ROOT))
    except ValueError:
        return content
    edit = ledger()['changes'].get(relative)
    if edit is None:
        return content
    if blob_hash(content) != edit['afterSha']:
        raise ValueError('Unknown E5 candidate blob: ' + relative)
    text = content.decode('utf-8')
    for patch in reversed(edit['inverse']):
        if text.count(patch['after']) != 1:
            raise ValueError('Missing/ambiguous E5 inverse: ' + relative)
        text = text.replace(patch['after'], patch['before'], 1)
    previous = text.encode('utf-8')
    if blob_hash(previous) != edit['beforeSha']:
        raise ValueError('E5 inverse differs from immutable M5 base: ' + relative)
    return previous


def e5_retained_bytes(path):
    """Read and validate a candidate file before presenting its historical M4/M5 scope."""
    return retained_from_bytes(path, Path(path).read_bytes())


def e5_retained_text(path):
    """Decode only validated historical-scope bytes; preserve original UTF-8 exactly."""
    return e5_retained_bytes(path).decode('utf-8')


def e5_scope_files(directory, pattern, recursive=True):
    """Enumerate the previous scope excluding only explicitly inventoried E5 additions."""
    directory = Path(directory)
    data = ledger()
    added = set(data['additions']) | {data['ledgerPath']}
    result = []
    source = directory.rglob(pattern) if recursive else directory.glob(pattern)
    for path in source:
        try:
            relative = str(path.resolve().relative_to(ROOT))
        except ValueError:
            relative = None
        if relative not in added:
            result.append(path)
    return result
