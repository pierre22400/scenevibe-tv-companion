# Pair only while the TV shows a temporary code. The token is returned to the caller,
# never written to a file by this script. Use only on a trusted local network (HTTP).
param(
    [Parameter(Mandatory=$true)][string]$TvIp,
    [Parameter(Mandatory=$true)][ValidatePattern('^[0-9]{6}$')][string]$Code,
    [string]$ClientName = "SceneVibe development sender"
)

$body = @{
    type = "scenevibe.pair.request.v1"
    code = $Code
    clientName = $ClientName
} | ConvertTo-Json -Compress
$ack = Invoke-RestMethod -Method Post -Uri "http://${TvIp}:8765/pair" `
    -ContentType "application/json" -Body $body
if ($ack.type -ne "scenevibe.pair.ack.v1" -or $ack.status -ne "paired" `
    -or [string]::IsNullOrWhiteSpace([string]$ack.token)) {
    throw "Unexpected pairing ACK from TV."
}
Write-Host "Paired with SceneVibe TV installation $($ack.deviceId)."
return [string]$ack.token
