import argparse
import hashlib
import json
from pathlib import Path
import re
import struct
import subprocess
import zipfile

"""Inspect actual OFF/ON APK bytes and stable certificate without any device or secret.

The DEX reader is reused verbatim from WORK 037. Explicit component/permission,
compiled flags, immutable fixture and signed payload equality are independently checked.
"""

PACKAGE = 'com.scenevibe.tvcompanionpoc'
FIXTURE_SHA = 'c66ded0b6a2ea41ef34320a289f2c1ec37e2a87d34ef580d9ac45d08815682ec'
CERT_SHA = 'f908bf564ed97ba67e02b1ebc89eb0239cf980752587f55eb9ec0419791a2e9c'


def require(condition, label):
    """Refuse une preuve absente ou divergente avec un message borné sans secret."""
    if not condition:
        raise ValueError(label)


def sha(data):
    """Calcule l'empreinte SHA-256 de bytes sans les modifier."""
    return hashlib.sha256(data).hexdigest()


def uleb(data, offset):
    """Lit un entier DEX ULEB128 borné à cinq octets et retourne le nouvel offset."""
    value = 0
    for shift in range(0, 35, 7):
        byte = data[offset]
        offset += 1
        value |= (byte & 127) << shift
        if byte < 128:
            return value, offset
    raise ValueError('DEX_ULEB_TOO_LONG')


def dex_config(data):
    """Lit les valeurs statiques de BuildConfig depuis le vrai DEX non obfusqué."""
    require(data[:4] == b'dex\n', 'DEX_MAGIC')
    count, start = struct.unpack_from('<II', data, 56)
    strings = []
    for index in range(count):
        position = struct.unpack_from('<I', data, start + 4 * index)[0]
        _, position = uleb(data, position)
        strings.append(data[position:data.index(b'\0', position)].decode('utf-8', errors='replace'))
    count, start = struct.unpack_from('<II', data, 64)
    types = [strings[struct.unpack_from('<I', data, start + 4 * index)[0]] for index in range(count)]
    _, fields_start = struct.unpack_from('<II', data, 80)
    count, start = struct.unpack_from('<II', data, 96)
    result = []
    for index in range(count):
        entry = struct.unpack_from('<8I', data, start + 32 * index)
        if types[entry[0]] != 'Lcom/scenevibe/tvcompanionpoc/BuildConfig;':
            continue
        static_count, position = uleb(data, entry[6])
        for _ in range(3):
            _, position = uleb(data, position)
        names = []
        field_index = 0
        for _ in range(static_count):
            difference, position = uleb(data, position)
            _, position = uleb(data, position)
            field_index += difference
            name_index = struct.unpack_from('<I', data, fields_start + 8 * field_index + 4)[0]
            names.append(strings[name_index])
        values = []
        if entry[7]:
            value_count, position = uleb(data, entry[7])
            for _ in range(value_count):
                header = data[position]
                position += 1
                kind, argument = header & 31, header >> 5
                if kind == 31:
                    require(argument in (0, 1), 'DEX_BOOL')
                    value = bool(argument)
                elif kind == 30:
                    value = None
                else:
                    size = argument + 1
                    raw = data[position:position + size]
                    position += size
                    require(kind in (4, 23), 'DEX_BUILDCONFIG_VALUE_TYPE')
                    value = int.from_bytes(raw, 'little', signed=kind == 4)
                    if kind == 23:
                        value = strings[value]
                values.append(value)
        require(len(values) <= len(names), 'DEX_STATIC_VALUE_COUNT')
        result.append(dict(zip(names, values + [0] * (len(names) - len(values)))))
    return result


def payload(path):
    """Inventorie les bytes applicatifs, en excluant exclusivement les signatures JAR."""
    with zipfile.ZipFile(path) as z:
        names = z.namelist()
        require(len(set(names)) == len(names), 'APK_DUPLICATE_ZIP_ENTRY')
        return {n: sha(z.read(n)) for n in names if not re.fullmatch(r'META-INF/(MANIFEST\.MF|[^/]+\.(SF|RSA|DSA|EC))', n, flags=re.IGNORECASE)}

def inspect(mode, path, tools, output, source=None):
    """Require exact Android identity, packaged admission, flags and optional stable signature."""
    enabled = mode != 'off'
    badging = subprocess.check_output([str(tools / 'aapt'), 'dump', 'badging', str(path)], text=True)
    match = re.search(r"package: name='([^']+)' versionCode='([^']+)' versionName='([^']+)'", badging)
    expected = (PACKAGE, '15', '0.8.2-tv-release-hardening-e5-native038-adb') if enabled else (PACKAGE, '12', '0.8.2-tv-release-hardening')
    require(match is not None and match.groups() == expected, 'APK_IDENTITY')
    xml = subprocess.check_output([str(tools / 'aapt'), 'dump', 'xmltree', str(path), 'AndroidManifest.xml'], text=True)
    with zipfile.ZipFile(path) as z:
        configs = []
        for name in z.namelist():
            if re.fullmatch(r'classes\d*\.dex', name):
                configs.extend(dex_config(z.read(name)))
        require(len(configs) == 1, 'CONFIG_UNIQUE')
        config = configs[0]
        # DEBUG is initialized in <clinit>, not a compile-time static encoded Boolean.
        # Read the actual debuggable manifest instead of reporting its DEX default false.
        config.pop('DEBUG', None)
        require('application-debuggable' in badging, 'QUALIFICATION_DEBUG_MANIFEST')
        require(config.get('E5_NATIVE_QUALIFICATION') == enabled, 'FLAG')
        require(config.get('CLOUD_ORIGIN') == '' and config.get('ENABLE_LAN_DEV') == False, 'LOCAL_ONLY')
        component = 'com.scenevibe.tvcompanionpoc.E5AdbTriggerReceiver'
        if enabled:
            require(xml.count(component) == 2, 'RECEIVER_UNIQUE')
            start = xml.index('E: receiver', xml.index('E: application'))
            # Locate the receiver block by its fixed name, allowing other historical receivers.
            receiver_blocks = re.split(r'(?m)^\s*E: receiver ', xml)[1:]
            block = next(b for b in receiver_blocks if component in b)
            block = re.split(r'(?m)^\s*E: (?:receiver|service|activity) ', block)[0]
            require('android.permission.DUMP' in block and '0xffffffff' in block, 'RECEIVER_PROTECTION')
            require('E: intent-filter' not in block, 'NO_IMPLICIT_FILTER')
            fixture = z.read('assets/scenevibe_interlude_10s.mp4')
            require(len(fixture) == 2146888 and sha(fixture) == FIXTURE_SHA, 'FIXTURE')
        else:
            require(component not in xml, 'OFF_COMPONENT_ABSENT')
            require('assets/scenevibe_interlude_10s.mp4' not in z.namelist(), 'OFF_FIXTURE_ABSENT')
    cert = None
    if mode == 'signed':
        signature = subprocess.check_output([str(tools / 'apksigner'), 'verify', '--verbose', '--print-certs',
                                             '--min-sdk-version', '31', '--max-sdk-version', '31', str(path)], text=True)
        certs = re.findall(r'Signer #\d+ certificate SHA-256 digest: ([0-9a-fA-F]+)', signature)
        require([c.lower() for c in certs] == [CERT_SHA], 'CERTIFICATE_CONTINUITY')
        cert = CERT_SHA
        require(source is not None and payload(source) == payload(path), 'SIGNED_PAYLOAD_UNCHANGED')
    evidence = {'apk_sha256': sha(path.read_bytes()), 'package': match[1], 'versionCode': int(match[2]),
                'versionName': match[3], 'debuggable': True, 'compiledConfig': config, 'certificate_sha256': cert,
                'fixture_sha256': FIXTURE_SHA if enabled else None, 'mode': mode,
                'git_head': subprocess.check_output(['git', 'rev-parse', 'HEAD'], text=True).strip(),
                'sony_executed': False, 'installed': False}
    output.write_text(json.dumps(evidence, indent=2) + '\n')
    print(json.dumps(evidence))


def main():
    """Run a single local byte inspection, optionally comparing the pre-signing APK payload."""
    parser = argparse.ArgumentParser()
    parser.add_argument('mode', choices=['off', 'on', 'signed'])
    parser.add_argument('apk', type=Path)
    parser.add_argument('tools', type=Path)
    parser.add_argument('output', type=Path)
    parser.add_argument('--source', type=Path)
    args = parser.parse_args()
    inspect(args.mode, args.apk, args.tools, args.output, args.source)


if __name__ == '__main__':
    main()
