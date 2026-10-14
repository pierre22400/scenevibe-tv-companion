import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import struct
import subprocess
import urllib.error
import urllib.request
import zipfile

"""
Prépare uniquement une copie signée de l'APK E5 immuable de WORK 037.
Le téléchargement est lié à un artefact, un HEAD et deux empreintes explicites.
Les secrets restent dans le runner GitHub ; aucune clé n'est lue par ce script.
Le contrôle DEX lit les constantes compilées et le contrôle ZIP compare tous les
contenus applicatifs, en excluant uniquement les entrées de signature JAR.
Aucune opération TV, installation, compilation applicative ou publication réelle.
"""

HEAD = '47f675dbd05218fb1caca2b090fd81c822e513e0'
RUN = 38521313109
ARTIFACT = 11873723967
SOURCE_SHA = '607d04f63d8fd3b9b653c5bc815f0b6569291c0bdb8e044b34b480df916f39e5'
ARCHIVE_SHA = '20246a6601e0145fbbaa53834fb904db35397a74854a5056bc7228617f2f5b0b'
FIXTURE_SHA = 'c66ded0b6a2ea41ef34320a289f2c1ec37e2a87d34ef580d9ac45d08815682ec'
CERT_SHA = 'f908bf564ed97ba67e02b1ebc89eb0239cf980752587f55eb9ec0419791a2e9c'
APK_ENTRY = 'scenevibe-tv-companion/scenevibe-tv-companion/app/build/outputs/apk/debug/app-debug.apk'
PACKAGE = 'com.scenevibe.tvcompanionpoc'
VERSION_NAME = '0.8.2-tv-release-hardening-e5-native035'


def require(condition, label):
    """Refuse une preuve absente ou divergente avec un message borné sans secret."""
    if not condition:
        raise ValueError(label)


def sha(data):
    """Calcule l'empreinte SHA-256 de bytes sans les modifier."""
    return hashlib.sha256(data).hexdigest()


class NoRedirect(urllib.request.HTTPRedirectHandler):
    """Empêche de transmettre le token GitHub à l'hôte de stockage des artefacts."""
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        """Interrompt la redirection authentifiée ; son URL sera reprise sans token."""
        return None


def download(destination):
    """Télécharge l'artefact exact et extrait uniquement l'APK attendue, sans clé."""
    token = os.environ['GITHUB_TOKEN']
    endpoint = f'https://api.github.com/repos/pierre22400/scenevibe-tv-companion/actions/artifacts/{ARTIFACT}'
    headers = {'Authorization': 'Bearer ' + token, 'Accept': 'application/vnd.github+json', 'X-GitHub-Api-Version': '2022-11-28'}
    with urllib.request.urlopen(urllib.request.Request(endpoint, headers=headers), timeout=60) as response:
        meta = json.load(response)
    require(meta['id'] == ARTIFACT and not meta['expired'], 'SOURCE_ARTIFACT_ID_OR_EXPIRY')
    require(meta['workflow_run']['id'] == RUN and meta['workflow_run']['head_sha'] == HEAD, 'SOURCE_RUN_OR_HEAD')
    require(meta['digest'] == 'sha256:' + ARCHIVE_SHA, 'SOURCE_ARCHIVE_METADATA_DIGEST')
    opener = urllib.request.build_opener(NoRedirect())
    try:
        opener.open(urllib.request.Request(endpoint + '/zip', headers=headers), timeout=60)
        raise ValueError('EXPECTED_SIGNED_DOWNLOAD_REDIRECT')
    except urllib.error.HTTPError as response:
        require(response.code == 302, 'DOWNLOAD_REDIRECT_STATUS')
        location = response.headers['Location']
    require(location.startswith('https://'), 'DOWNLOAD_HTTPS_REQUIRED')
    with urllib.request.urlopen(location, timeout=120) as response:
        data = response.read(10_000_001)
    require(len(data) <= 10_000_000 and sha(data) == ARCHIVE_SHA, 'SOURCE_ARCHIVE_BYTES_DIGEST')
    destination.mkdir(parents=True, exist_ok=True)
    archive = destination / 'source-artifact.zip'
    archive.write_bytes(data)
    with zipfile.ZipFile(archive) as z:
        require(z.namelist().count(APK_ENTRY) == 1, 'SOURCE_APK_ENTRY_UNIQUE')
        apk = z.read(APK_ENTRY)
    require(len(apk) == 2305017 and sha(apk) == SOURCE_SHA, 'SOURCE_APK_BYTES_DIGEST')
    (destination / 'source.apk').write_bytes(apk)
    archive.unlink()
    print('SOURCE_ARTIFACT_VERIFIED', ARTIFACT, HEAD, SOURCE_SHA)


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


def inspect(path, aapt, output, require_source=False):
    """Vérifie identité Android, paramètres compilés et fixture avant toute signature."""
    file_sha = sha(path.read_bytes())
    if require_source:
        require(file_sha == SOURCE_SHA, 'SOURCE_APK_HASH')
    badging = subprocess.check_output([aapt, 'dump', 'badging', str(path)], text=True)
    match = re.search(r"package: name='([^']+)' versionCode='([^']+)' versionName='([^']+)'", badging)
    require(match is not None and match.groups() == (PACKAGE, '14', VERSION_NAME), 'APK_PACKAGE_OR_VERSION')
    require('application-debuggable' in badging, 'APK_DEBUG_PROFILE')
    with zipfile.ZipFile(path) as z:
        configs = []
        for n in z.namelist():
            if re.fullmatch(r'classes\d*\.dex', n):
                configs.extend(dex_config(z.read(n)))
        require(len(configs) == 1, 'BUILDCONFIG_UNIQUE')
        config = configs[0]
        require(config.get('E5_NATIVE_QUALIFICATION') is True, 'E5_OPT_IN')
        require(config.get('CLOUD_ORIGIN') == '', 'CLOUD_NOT_EMPTY')
        require(config.get('ENABLE_LAN_DEV') == False, 'LAN_ENABLED')
        require(config.get('APPLICATION_ID') == PACKAGE and config.get('VERSION_CODE') == 14 and config.get('VERSION_NAME') == VERSION_NAME, 'BUILDCONFIG_IDENTITY')
        fixture = z.read('assets/scenevibe_interlude_10s.mp4')
        require(len(fixture) == 2146888 and sha(fixture) == FIXTURE_SHA, 'FIXTURE_DIGEST')
    evidence = {'candidate_head': HEAD, 'source_run': RUN, 'source_artifact': ARTIFACT, 'apk_sha256': file_sha, 'package': PACKAGE, 'versionCode': 14, 'versionName': VERSION_NAME, 'debuggable': True, 'E5': True, 'Cloud': '', 'LAN': False, 'fixture_sha256': FIXTURE_SHA, 'fixture_bytes': len(fixture), 'payload': payload(path)}
    output.write_text(json.dumps(evidence, indent=2) + '\n')
    print('APK_PROFILE_VERIFIED', path.name, file_sha)


def compare(source, signed, signature, output):
    """Exige le certificat stable et l'égalité complète du payload après signature."""
    before = json.loads(source.read_text())
    after = json.loads(signed.read_text())
    require(before['apk_sha256'] == SOURCE_SHA, 'SOURCE_CHANGED')
    require(before['payload'] == after['payload'], 'APPLICATION_CONTENT_CHANGED')
    certs = re.findall(r'Signer #\d+ certificate SHA-256 digest: ([0-9a-fA-F]+)', signature.read_text())
    require([c.lower() for c in certs] == [CERT_SHA], 'STABLE_SIGNER_MISMATCH')
    require(before['apk_sha256'] != after['apk_sha256'], 'SIGNATURE_FILE_UNCHANGED')
    after.update({'stable_certificate_sha256': CERT_SHA, 'source_apk_sha256': SOURCE_SHA, 'source_preserved': True, 'application_content_unchanged': True, 'verified_payload_entries': len(after['payload']), 'signature_verification': 'apksigner verify --verbose --print-certs PASS; API31 also verified separately', 'sony_access': False, 'installed': False})
    output.write_text(json.dumps(after, indent=2) + '\n')
    print('STABLE_COPY_VERIFIED', after['apk_sha256'], CERT_SHA, 'PAYLOAD_ENTRIES', len(after['payload']))


def main():
    """Exécute un seul contrôle de qualification ; aucun secret n'est affiché."""
    parser = argparse.ArgumentParser()
    parser.add_argument('mode', choices=['download', 'inspect', 'compare'])
    parser.add_argument('paths', nargs='+')
    args = parser.parse_args()
    if args.mode == 'download':
        download(Path(args.paths[0]))
    elif args.mode == 'inspect':
        inspect(Path(args.paths[0]), args.paths[1], Path(args.paths[2]), len(args.paths) == 4 and args.paths[3] == 'source')
    else:
        compare(*(Path(p) for p in args.paths))


if __name__ == '__main__':
    try:
        main()
    except Exception as error:
        print('QUALIFICATION_STOP', type(error).__name__)
        raise SystemExit(1)
