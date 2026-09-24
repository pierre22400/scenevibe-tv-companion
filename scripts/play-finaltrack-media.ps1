param(
    [Parameter(Mandatory=$true)][string]$TvIp,
    [Parameter(Mandatory=$true)][string]$TrackPath,
    [Parameter(Mandatory=$true)][string]$AssetMapPath
)

$track = Get-Content $TrackPath -Raw | ConvertFrom-Json
$assetMap = Get-Content $AssetMapPath -Raw | ConvertFrom-Json -AsHashtable

if ($track.contract -ne "scenevibe.final-track" -or $track.schemaVersion -ne "1.1.0") {
    throw "Expected SceneVibe FinalTrack 1.1.0."
}

$assets = @{}
foreach ($asset in $track.assets) {
    if ($assets.ContainsKey($asset.id)) { throw "Duplicate asset id: $($asset.id)" }
    $assets[$asset.id] = $asset
}

$uri = "http://${TvIp}:8765/commentary"
$stopwatch = [Diagnostics.Stopwatch]::StartNew()

foreach ($comment in ($track.comments | Sort-Object {$_.schedule.idealStartSec})) {
    while ($stopwatch.Elapsed.TotalSeconds -lt [double]$comment.schedule.idealStartSec) {
        Start-Sleep -Milliseconds 50
    }

    $payload = @{
        type = "scenevibe.commentary.v1"
        id = [string]$comment.id
        text = [string]$comment.text
        durationMs = [int]([double]$comment.display.durationSec * 1000)
    }

    if ($null -ne $comment.media) {
        $assetRef = [string]$comment.media.assetRef
        if (-not $assets.ContainsKey($assetRef)) { throw "Unknown assetRef: $assetRef" }
        if (-not $assetMap.ContainsKey($assetRef)) { throw "No local binding for assetRef: $assetRef" }

        $asset = $assets[$assetRef]
        $path = [string]$assetMap[$assetRef]
        $resolved = (Resolve-Path $path).Path
        $bytes = [IO.File]::ReadAllBytes($resolved)

        if ($bytes.Length -gt 2MB) {
            throw "Asset $assetRef exceeds the 2 MiB TV POC limit."
        }

        $payload.media = @{
            kind = "image"
            mimeType = [string]$asset.mimeType
            dataBase64 = [Convert]::ToBase64String($bytes)
        }
    }

    Write-Host "$(Get-Date -Format HH:mm:ss) $($comment.id) @ $($comment.schedule.idealStartSec)s media=$($null -ne $comment.media)"
    $body = $payload | ConvertTo-Json -Depth 6 -Compress
    Invoke-RestMethod -Method Post -Uri $uri -ContentType "application/json" -Body $body
}
