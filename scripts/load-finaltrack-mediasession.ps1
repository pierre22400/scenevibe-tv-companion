param(
    [Parameter(Mandatory=$true)][string]$TvIp,
    [Parameter(Mandatory=$true)][string]$TrackPath,
    [Parameter(Mandatory=$true)][string]$AssetMapPath,
    [string]$TargetPackage = "com.amazon.amazonvideo.livingroom"
)

$track = Get-Content $TrackPath -Raw | ConvertFrom-Json
$assetMapJson = Get-Content $AssetMapPath -Raw | ConvertFrom-Json
$assetMap = @{}
foreach ($property in $assetMapJson.PSObject.Properties) {
    $assetMap[$property.Name] = $property.Value
}

if ($track.contract -ne "scenevibe.final-track" -or $track.schemaVersion -ne "1.1.0") {
    throw "Expected SceneVibe FinalTrack 1.1.0."
}

$assets = @{}
foreach ($asset in $track.assets) {
    if ($assets.ContainsKey($asset.id)) { throw "Duplicate asset id: $($asset.id)" }
    $assets[$asset.id] = $asset
}

$runtimeComments = @()
foreach ($comment in ($track.comments | Sort-Object {$_.schedule.idealStartSec})) {
    $runtime = @{
        id = [string]$comment.id
        text = [string]$comment.text
        startMs = [int64]([Math]::Round([double]$comment.schedule.idealStartSec * 1000))
        durationMs = [int64]([Math]::Round([double]$comment.display.durationSec * 1000))
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

        $runtime.media = @{
            kind = "image"
            mimeType = [string]$asset.mimeType
            dataBase64 = [Convert]::ToBase64String($bytes)
        }
    }

    $runtimeComments += $runtime
}

$payload = @{
    type = "scenevibe.track.v1"
    trackId = [string]$track.trackId
    targetPackage = $TargetPackage
    comments = $runtimeComments
}

$uri = "http://$($TvIp):8765/track"
Write-Host "Loading FinalTrack $($track.trackId) on TV for MediaSession package $TargetPackage"
$body = $payload | ConvertTo-Json -Depth 8 -Compress
Invoke-RestMethod -Method Post -Uri $uri -ContentType "application/json" -Body $body
