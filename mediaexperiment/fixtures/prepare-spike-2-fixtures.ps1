# SceneVibe Spike 2.0 — prepare real ten-second MP3 speech and MP4 motion fixtures.
# Requires PowerShell, ffmpeg, ffprobe and optionally adb. No production writes.
param([string]$OutputDir = "$PSScriptRoot\out", [switch]$PushToTv)
$ErrorActionPreference = "Stop"
foreach ($tool in @("ffmpeg", "ffprobe")) {
    if (-not (Get-Command $tool -ErrorAction SilentlyContinue)) { throw "$tool not found on PATH" }
}
New-Item -ItemType Directory -Path $OutputDir -Force | Out-Null
$mp3Source = Join-Path $OutputDir "samplelib_speech_source.mp3"
$mp4Source = Join-Path $OutputDir "big_buck_bunny_source.mp4"
$mp3 = Join-Path $OutputDir "scenevibe_voice_10s.mp3"
$mp4 = Join-Path $OutputDir "scenevibe_interlude_10s.mp4"
Invoke-WebRequest "https://samplelib.com/mp3/sample-speech-1m.mp3" -OutFile $mp3Source
Invoke-WebRequest "https://test-videos.co.uk/vids/bigbuckbunny/mp4/h264/720/Big_Buck_Bunny_720_10s_1MB.mp4" -OutFile $mp4Source
& ffmpeg -hide_banner -loglevel error -y -i $mp3Source -t 10 -ar 44100 -ac 1 -c:a libmp3lame -b:a 128k $mp3
if ($LASTEXITCODE -ne 0) { throw "MP3 preparation failed" }
# The online MP4 is video-only; attach diagnostic AAC audio without re-encoding H.264.
& ffmpeg -hide_banner -loglevel error -y -i $mp4Source -f lavfi -i "sine=frequency=440:sample_rate=48000" -map 0:v:0 -map 1:a:0 -t 10 -c:v copy -c:a aac -b:a 128k -movflags +faststart $mp4
if ($LASTEXITCODE -ne 0) { throw "MP4 preparation failed" }
foreach ($file in @($mp3, $mp4)) {
    $durationText = & ffprobe -v error -show_entries format=duration -of "default=noprint_wrappers=1:nokey=1" $file
    if ($LASTEXITCODE -ne 0) { throw "ffprobe failed: $file" }
    $duration = [double]::Parse(($durationText | Select-Object -First 1).Trim(), [System.Globalization.CultureInfo]::InvariantCulture)
    if ($duration -lt 9.5 -or $duration -gt 10.5) { throw "Incorrect duration: $file ($duration s)" }
}
$audioCodec = (& ffprobe -v error -select_streams a:0 -show_entries stream=codec_name -of "default=noprint_wrappers=1:nokey=1" $mp3).Trim()
$videoCodec = (& ffprobe -v error -select_streams v:0 -show_entries stream=codec_name -of "default=noprint_wrappers=1:nokey=1" $mp4).Trim()
$mp4Audio = (& ffprobe -v error -select_streams a:0 -show_entries stream=codec_name -of "default=noprint_wrappers=1:nokey=1" $mp4).Trim()
if ($audioCodec -ne "mp3" -or $videoCodec -ne "h264" -or $mp4Audio -ne "aac") { throw "Codec mismatch" }
Get-FileHash -Algorithm SHA256 $mp3, $mp4 | Select-Object Path, Hash | Format-Table -AutoSize
if ($PushToTv) {
    if (-not (Get-Command adb -ErrorAction SilentlyContinue)) { throw "adb not found on PATH" }
    $destination = "/sdcard/Android/data/com.scenevibe.tvcompanionpoc.mediaexperiment/files"
    & adb shell mkdir -p $destination
    if ($LASTEXITCODE -ne 0) { throw "ADB cannot create fixture destination" }
    & adb push $mp3 "$destination/scenevibe_voice_10s.mp3"
    if ($LASTEXITCODE -ne 0) { throw "MP3 transfer failed" }
    & adb push $mp4 "$destination/scenevibe_interlude_10s.mp4"
    if ($LASTEXITCODE -ne 0) { throw "MP4 transfer failed" }
}
