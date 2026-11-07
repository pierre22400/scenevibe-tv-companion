import base64
import csv
import hashlib
import json
import shutil
from pathlib import Path

"""
SceneVibe REAL TRACK 001 : export pédagogique de la fixture Android native.
Aucun accès Cloud ni TV : les trois MP3 sont synthétiques et validés par SHA-256.
L'export conserve chaque JSON source et ajoute des présentations lisibles.
"""

ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / "app/src/androidTest/assets/realtrack001"
OUTPUT = ROOT / "fixture-export"


def digest(data: bytes) -> str:
    """Calcule l'empreinte immuable SHA-256 du contenu brut."""
    return hashlib.sha256(data).hexdigest()


def decode(profile: dict, field: str) -> tuple[bytes, object]:
    """Décode strictement les octets Base64 et leur JSON UTF-8."""
    raw = base64.b64decode(profile[field], validate=True)
    return raw, json.loads(raw.decode("utf-8"))


def clock(milliseconds: int) -> str:
    """Présente la position d'une intervention à la seconde ou milliseconde."""
    minutes, remainder = divmod(milliseconds, 60000)
    seconds, fraction = divmod(remainder, 1000)
    return f"{minutes:02d}:{seconds:02d}" + (f".{fraction:03d}" if fraction else "")


def export() -> None:
    """Vérifie et assemble exactement 3 TEXT, 3 MP3 et leurs documents sources."""
    profile = json.loads((SOURCE / "profile.json").read_text(encoding="utf-8"))
    facts = json.loads((SOURCE / "facts.json").read_text(encoding="utf-8"))
    if OUTPUT.exists():
        raise RuntimeError("STOP: export existant")
    (OUTPUT / "audio").mkdir(parents=True)
    for original in ("profile.json", "facts.json", "binding.json"):
        shutil.copyfile(SOURCE / original, OUTPUT / ("source-" + original))
    names = {"runtimeBase64": "runtime", "manifestBase64": "manifest",
             "trackBase64": "track", "resourceManifestBase64": "ressources"}
    parsed = {}
    for key, label in names.items():
        raw, document = decode(profile, key)
        (OUTPUT / (label + "-original.json")).write_bytes(raw)
        (OUTPUT / (label + "-lisible.json")).write_text(
            json.dumps(document, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        parsed[key] = document
        if key == "runtimeBase64" and digest(raw) != facts["runtimeSha256"]:
            raise RuntimeError("STOP: runtime invalide")
        if key == "manifestBase64" and digest(raw) != facts["manifestSha256"]:
            raise RuntimeError("STOP: manifest invalide")
    if profile["identity"]["snapshotSha256"] != facts["snapshotSha256"]:
        raise RuntimeError("STOP: snapshot incohérent")
    track = parsed["trackBase64"]
    events = sorted(track["events"], key=lambda e: (e["startMs"], e["id"]))
    resources = parsed["resourceManifestBase64"]
    if len(events) != 6 or sum(e["type"] == "TEXT" for e in events) != 3 or len(resources) != 3:
        raise RuntimeError("STOP: contenu 3 TEXT + 3 AUDIO non conforme")
    resource_names = {}
    for index, resource in enumerate(resources, 1):
        expected = resource["sha256"]
        data = (SOURCE / (expected + ".mp3")).read_bytes()
        if digest(data) != expected or len(data) != resource["byteLength"]:
            raise RuntimeError("STOP: MP3 source altéré")
        target = f"audio-{index}.mp3"
        (OUTPUT / "audio" / target).write_bytes(data)
        resource_names[resource["resourceId"]] = target
    if len({r["sha256"] for r in resources}) != 3:
        raise RuntimeError("STOP: MP3 non distincts")
    rows = []
    for event in events:
        kind = event["type"]
        if kind not in ("TEXT", "AUDIO"):
            raise RuntimeError("STOP: événement inconnu")
        if kind == "AUDIO" and event["resourceId"] not in resource_names:
            raise RuntimeError("STOP: référence MP3 manquante")
        rows.append({"début": clock(event["startMs"]), "type": kind,
                     "durée_ms": event["durationMs"], "id": event["id"],
                     "contenu": event["content"]["text"] if kind == "TEXT"
                       else "audio/" + resource_names[event["resourceId"]]})
    with (OUTPUT / "evenements.csv").open("w", encoding="utf-8-sig", newline="") as file:
        writer = csv.DictWriter(file, fieldnames=("début", "type", "durée_ms", "id", "contenu"), delimiter=";")
        writer.writeheader()
        writer.writerows(rows)
    lines = ["# SceneVibe — dernière fixture REAL TRACK 001",
             "", "**Fixture synthétique, non publiée dans le compte Sony.**",
             "", "Trois TEXT et trois MP3 distincts (3 secondes chacun).",
             "", "Référence : " + track["title"],
             "Track ID : " + track["trackId"], "",
             "| Début | Type | Durée | Texte / fichier audio |",
             "|---|---|---:|---|"]
    for row in rows:
        lines.append(f"| {row['début']} | {row['type']} | {row['durée_ms']} ms | {row['contenu']} |")
    lines += ["", "Le dossier contient les trois MP3 originaux dans audio/, le",
              "calendrier evenements.csv, les JSON décodés, les originaux JSON",
              "non transformés, et les empreintes sha256.txt.",
              "", "Aucune publication Cloud ni admission TEXT/AUDIO n'est créée.", ""]
    (OUTPUT / "TRACK-LISIBLE.md").write_text("\n".join(lines), encoding="utf-8")
    checksums = []
    for path in sorted(OUTPUT.rglob("*")):
        if path.is_file():
            checksums.append(digest(path.read_bytes()) + "  " + path.relative_to(OUTPUT).as_posix())
    (OUTPUT / "sha256.txt").write_text("\n".join(checksums) + "\n", encoding="utf-8")
    print("FIXTURE_EXPORT_PASS: 3 TEXT + 3 MP3 distincts")


if __name__ == "__main__":
    export()
