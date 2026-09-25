param(
    [Parameter(Mandatory=$true)][string]$TvIp,
    [Parameter(Mandatory=$true)][string]$ImagePath,
    [Parameter(Mandatory=$true)][string]$Token,
    [string]$Text = "SceneVibe image commentary",
    [string]$Id = "media-poc-001",
    [int]$DurationMs = 10000
)

$resolved = (Resolve-Path $ImagePath).Path
$extension = [IO.Path]::GetExtension($resolved).ToLowerInvariant()
$mime = switch ($extension) {
    ".jpg"  { "image/jpeg" }
    ".jpeg" { "image/jpeg" }
    ".png"  { "image/png" }
    default { throw "Only .jpg, .jpeg and .png are supported by the v0.3.0 POC." }
}

$imageBytes = [IO.File]::ReadAllBytes($resolved)
if ($imageBytes.Length -gt 2MB) {
    throw "Image is $($imageBytes.Length) bytes; v0.3.0 POC limit is 2 MiB."
}

$payload = @{
    type = "scenevibe.commentary.v1"
    id = $Id
    text = $Text
    durationMs = $DurationMs
    media = @{
        kind = "image"
        mimeType = $mime
        dataBase64 = [Convert]::ToBase64String($imageBytes)
    }
} | ConvertTo-Json -Depth 5 -Compress

Invoke-RestMethod -Method Post -Uri "http://${TvIp}:8765/commentary" -ContentType "application/json" -Body $payload `
    -Headers @{ Authorization = "Bearer $Token" }
