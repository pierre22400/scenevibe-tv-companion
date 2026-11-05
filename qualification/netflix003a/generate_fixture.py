import hashlib
import io
import json
from pathlib import Path
import subprocess
from zipfile import ZipFile, ZipInfo

"""
SCENEVIBE 003-A — fixture indépendante de Netflix et de Prime.
Génère deux tonalités et deux commentaires originaux avec le contrat Studio.
Le digest externe représente la référence à confirmer au banc.
"""


def encoded(value):
    """Encode les documents de façon déterministe pour les empreintes."""
    return json.dumps(value, ensure_ascii=False, sort_keys=True).encode("utf-8")


def digest(data):
    """Calcule l'empreinte des octets exacts."""
    return hashlib.sha256(data).hexdigest()


def mp3(frequency):
    """Crée un MP3 avec en-tête seekable et mesure sa durée PCM décodée."""
    from tempfile import TemporaryDirectory
    with TemporaryDirectory() as folder:
        path = Path(folder) / "tone.mp3"
        subprocess.run(["ffmpeg", "-v", "error", "-f", "lavfi", "-i",
                        f"sine=frequency={frequency}:duration=0.5:sample_rate=44100",
                        "-b:a", "64k", str(path)], check=True)
        pcm = subprocess.check_output(["ffmpeg", "-v", "error", "-i", str(path),
                                       "-f", "s16le", "-ac", "1", "-ar", "44100", "pipe:1"])
        return path.read_bytes(), round(len(pcm) / 2 / 44100 * 1000)


def generate(destination, long_run=False):
    """Écrit le ZIP Studio synthétique et sa référence indépendante."""
    track_id = "qualification-netflix:003a"
    program_id = "scenevibe-netflix-fixture"
    title = "Banc Netflix synthétique SceneVibe"
    horizon = 8000
    program = {"programId": program_id, "title": title}
    platform = {"id": "netflix", "androidPackage": "com.netflix.ninja"}
    comments = [
        {"id": "text-1", "startMs": 1000, "durationMs": 1000, "text": "Repère de qualification A"},
        {"id": "text-2", "startMs": 4000, "durationMs": 1000, "text": "Repère de qualification B"},
    ]
    tones = [(1, 440, 2500), (2, 660, 5500)]
    manifest_id = "netflix003a"
    if long_run:
        track_id = "qualification-netflix:003c"
        program_id = "scenevibe-netflix-long-run-003c"
        title = "Netflix expérimental — repères synthétiques 003-C"
        program = {"programId": program_id, "title": title}
        horizon = 1920000
        manifest_id = "netflix003c"
        checkpoints = [(1, 440, 10000), (2, 550, 300000), (3, 660, 900000),
                       (4, 770, 960000), (5, 880, 1500000), (6, 990, 1860000)]
        comments = [
            {"id": f"text-{index}", "startMs": start, "durationMs": 2000,
             "text": f"003-C · Repère {index} · {start // 60000:02d}:{start // 1000 % 60:02d}"}
            for index, _, start in checkpoints
        ]
        tones = [(index, frequency, start + 3000) for index, frequency, start in checkpoints]
    scenes = []
    events = []
    for comment in comments:
        scene = {key: comment[key] for key in ("id", "startMs", "durationMs")}
        scene["elements"] = [{
            "id": comment["id"] + "-element", "type": "text",
            "frame": {"x": 100, "y": 780, "width": 1500, "height": 120},
            "zIndex": 0, "opacity": 1, "text": comment["text"],
            "style": {"color": "#000000", "backgroundColor": "#FFFFFF",
                      "fontSize": 36, "fontWeight": "normal", "textAlign": "start",
                      "padding": 12, "cornerRadius": 4}
        }]
        scenes.append(scene)
        events.append({**{k: scene[k] for k in ("id", "startMs", "durationMs")},
                       "type": "TEXT", "content": {"text": comment["text"]}})
    files, resources, descriptions = {}, [], []
    for index, frequency, start in tones:
        data, duration = mp3(frequency)
        name = f"audio/tone-{index}.mp3"
        resource = f"tone-{index}"
        sha = digest(data)
        payload = {"storageRef": "svasset:" + sha, "mime": "audio/mpeg",
                   "byteLength": len(data), "sha256": sha, "durationMs": duration, "bitrateKbps": 64}
        resources.append({"id": resource, "type": "AUDIO", "derivation": "PASSTHROUGH",
                          "original": payload, "distributed": payload})
        descriptions.append({"resourceId": resource, "path": name, "mime": "audio/mpeg",
                             "byteLength": len(data), "sha256": sha, "durationMs": duration})
        files[name] = data
        events.append({"id": f"audio-{index}", "type": "AUDIO", "resourceId": resource,
                       "startMs": start, "durationMs": duration, "presentation": {"gain": 0.5}})
    names = ("clock pause seek textAdmission audioSeek unknownPosition eligibilityLoss "
             "lateCallback advertisements audioOverlap visualOverlap").split()
    values = ("MEDIA_POSITION_MS FREEZE_AND_RESUME TYPE_AWARE_MEDIA_RECONCILIATION "
              "M5_QUALIFIED_NO_LATE_REPLAY RESUME_NATIVE_OFFSET_INSIDE_WINDOW STOP_ALL "
              "STOP_ALL REJECT_OLD_ACTIVATION UNQUALIFIED_STOP REJECT REJECT").split()
    files["track.json"] = encoded({
        "contract": "scenevibe.multimedia-track", "schemaVersion": "1.0.0-draft.1",
        "trackId": track_id, "revision": 1, "title": title, "editorialLanguage": "fr",
        "target": {"program": program, "platform": platform, "horizonMs": horizon,
                   "edition": {"status": "UNKNOWN"}, "mapping": {"status": "UNKNOWN"}},
        "synchronization": dict(zip(names, values)),
        "requirements": {"onUnsupported": "REJECT_WHOLE_TRACK", "imageMimes": [],
                         "eventTypes": ["TEXT", "AUDIO"], "audioMimes": ["audio/mpeg"]},
        "events": sorted(events, key=lambda event: event["startMs"]), "resources": resources,
    })
    files["source/runtime.json"] = encoded({
        "trackId": track_id, "revision": 1, "targetPackage": platform["androidPackage"],
        "mediaIdentity": {"platform": platform["id"], "videoId": program_id,
                          "title": title, "durationMs": horizon},
        "pauseFreezesDisplay": True, "comments": comments,
    })
    files["source/overlay.json"] = encoded({
        "type": "scenevibe.overlay-manifest.v1", "schemaVersion": "1.0.0",
        "manifestId": manifest_id, "source": {"product": "video", "sourceId": track_id},
        "canvas": {"width": 1920, "height": 1080}, "clock": {"mode": "media", "pauseBehavior": "freeze"},
        "scenes": scenes,
    })
    descriptors = {key: {"path": path, "sha256": digest(files[path])}
                   for key, path in [("track", "track.json"), ("originalRuntime", "source/runtime.json"),
                                     ("originalManifest", "source/overlay.json")]}
    files["manifest.json"] = encoded({
        "format": "scenevibe.studio-tv-qualification", "version": 1,
        "source": {"revision": 1}, **descriptors, "files": descriptions,
        "mediaAdmissionAuthorized": False, "cloudPublicationAuthorized": False,
    })
    output = io.BytesIO()
    with ZipFile(output, "w") as package:
        for name, data in sorted(files.items()):
            package.writestr(ZipInfo(name), data)
    destination.mkdir(parents=True, exist_ok=True)
    (destination / "track.zip").write_bytes(output.getvalue())
    (destination / "selection.json").write_bytes(encoded({
        "trackId": track_id, "programId": program_id, "revision": 1,
        "sha256": digest(output.getvalue()),
    }) + b"\n")


if __name__ == "__main__":
    generate(Path(__file__).resolve().parents[2] / "app/src/testNetflixadmission003a/resources/netflix003a")
    generate(Path(__file__).resolve().parents[2] / "app/src/netflixmanual003b/assets/netflix003c", long_run=True)
