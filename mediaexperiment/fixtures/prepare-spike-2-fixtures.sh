#!/usr/bin/env bash
# SceneVibe Spike 2.0: generate package-local diagnostic fixtures only.
# Kept separate from production :app and frozen Spike 1.0 assets.
set -euo pipefail

# ------------------ Safe defaults and validation ------------------
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
ASSETS="$ROOT/mediaexperiment/src/main/assets"
OUT="$ROOT/mediaexperiment/fixtures/out"
mkdir -p "$ASSETS" "$OUT"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

for binary in curl ffmpeg ffprobe python3 sha256sum; do
  command -v "$binary" >/dev/null || { echo "MISSING_TOOL: $binary" >&2; exit 2; }
done

curl --fail --location --silent --show-error --retry 3 --retry-delay 2 --connect-timeout 20 --max-time 150 \
  "https://samplelib.com/mp3/sample-speech-1m.mp3" -o "$TMP/voice_source.mp3"
curl --fail --location --silent --show-error --retry 3 --retry-delay 2 --connect-timeout 20 --max-time 150 \
  "https://test-videos.co.uk/vids/bigbuckbunny/mp4/h264/720/Big_Buck_Bunny_720_10s_1MB.mp4" -o "$TMP/video_source.mp4"

# Speech: 10s voice, mono, MP3. Video: actual motion, 720p30 H.264 Main L3.1
# and audible AAC 440Hz tone; never a static pattern or silent-video substitution.
ffmpeg -hide_banner -loglevel error -y -i "$TMP/voice_source.mp3" -t 10 \
  -vn -ar 44100 -ac 1 -c:a libmp3lame -b:a 128k \
  "$ASSETS/scenevibe_voice_10s.mp3"
ffmpeg -hide_banner -loglevel error -y -i "$TMP/video_source.mp4" \
  -f lavfi -i "sine=frequency=440:sample_rate=48000" \
  -map 0:v:0 -map 1:a:0 -t 10 \
  -c:v libx264 -preset veryfast -crf 23 -r 30 -pix_fmt yuv420p \
  -profile:v main -level:v 3.1 -vf "scale=1280:720" \
  -c:a aac -b:a 128k -ac 1 -movflags +faststart \
  "$ASSETS/scenevibe_interlude_10s.mp4"

# Refuse an incomplete/wrong fixture before it can enter an APK.
python3 - "$ASSETS/scenevibe_voice_10s.mp3" "$ASSETS/scenevibe_interlude_10s.mp4" <<'PY'
"""Validate both CI-generated multimedia fixtures, never production media."""
import json
import subprocess
import sys
from pathlib import Path

def inspect(path):
    """Retrieve exact codec/duration facts from ffprobe without executing media."""
    result = subprocess.run(
        ["ffprobe", "-v", "error", "-show_streams", "-show_format",
         "-of", "json", path],
        check=True, capture_output=True, text=True
    )
    return json.loads(result.stdout)

voice, video = [Path(p) for p in sys.argv[1:]]
a, b = inspect(str(voice)), inspect(str(video))
for path, metadata in [(voice, a), (video, b)]:
    duration = float(metadata["format"]["duration"])
    if not 9.5 <= duration <= 10.5 or path.stat().st_size < 10000:
        raise SystemExit(f"INVALID_FIXTURE duration={duration} path={path}")
    print(f"FIXTURE_OK name={path.name} bytes={path.stat().st_size} duration={duration:.3f}s")
voice_streams = [s for s in a["streams"] if s["codec_type"] == "audio"]
video_streams = [s for s in b["streams"] if s["codec_type"] == "video"]
video_audio = [s for s in b["streams"] if s["codec_type"] == "audio"]
if len(voice_streams) != 1 or voice_streams[0]["codec_name"] != "mp3":
    raise SystemExit("INVALID_MP3")
if not video_streams or not video_audio:
    raise SystemExit("MISSING_VIDEO_OR_AUDIO")
if video_streams[0]["codec_name"] != "h264" or video_audio[0]["codec_name"] != "aac":
    raise SystemExit("INVALID_MP4_CODECS")
if video_streams[0]["width"] != 1280 or video_streams[0]["height"] != 720:
    raise SystemExit("INVALID_VIDEO_DIMENSIONS")
print("FIXTURE_CODEC_GATE_PASS")
PY
sha256sum "$ASSETS/scenevibe_voice_10s.mp3" "$ASSETS/scenevibe_interlude_10s.mp4" > "$OUT/fixture-sha256.txt"
cat "$OUT/fixture-sha256.txt"
