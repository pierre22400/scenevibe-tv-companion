<#
.SYNOPSIS
Capture en lecture seule d'une campagne Netflix ou Disney+ SceneVibe.
.DESCRIPTION
Place ce script dans platform-tools, ou fournis un chemin ADB absolu.
Conserve les buffers disponibles et le suivi, sans clear, reset, transport,
installation, capture d'ecran/audio ou interrogation privee de la plateforme.
Chaque invocation cree des fichiers distincts ; le numero ADB n'est pas exporte.
.PARAMETER Platform
Campagne cible NETFLIX ou DISNEY_PLUS, identique au choix de la page telephone.
.PARAMETER DeviceSerial
Identifiant deja connu du proprietaire, transmis comme argument ADB distinct.
.PARAMETER AdbPath
Chemin deterministe vers adb.exe ; par defaut le fichier voisin du script.
.PARAMETER CampaignStartedMs
Campagne monotone optionnelle a conserver exactement. Sinon, la derniere campagne
rencontree de cette plateforme est retenue ; le brut conserve toutes les campagnes.
.PARAMETER OutputDirectory
Dossier de sortie ; les anciennes captures ne sont jamais remplacees.
.EXAMPLE
Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass
.\Collect-SceneVibeMultiPlatform.ps1 -Platform NETFLIX -DeviceSerial 'SERIAL_DEJA_CONNECTE'
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][ValidateSet('NETFLIX', 'DISNEY_PLUS')][string]$Platform,
    [Parameter(Mandatory = $true)][ValidatePattern('^[A-Za-z0-9._:-]+$')][string]$DeviceSerial,
    [string]$AdbPath = (Join-Path $PSScriptRoot 'adb.exe'),
    [string]$OutputDirectory = (Join-Path $PSScriptRoot 'scenevibe-ad-observation'),
    [long]$CampaignStartedMs = 0
)
$ErrorActionPreference = 'Stop'
if ($CampaignStartedMs -lt 0) { throw 'Campagne monotone invalide.' }
$campaign = $CampaignStartedMs
$campaignPinned = $CampaignStartedMs -gt 0
$adb = (Resolve-Path -LiteralPath $AdbPath).Path
if (-not (Test-Path -LiteralPath $adb -PathType Leaf)) { throw 'Chemin ADB introuvable.' }
New-Item -ItemType Directory -Force -Path $OutputDirectory | Out-Null
$prefix = 'scenevibe-{0}-{1}-{2}' -f $Platform.ToLowerInvariant(), (Get-Date -Format 'yyyyMMdd-HHmmss-fff'), ([Guid]::NewGuid().ToString('N').Substring(0, 8))
$rawPath = Join-Path $OutputDirectory ($prefix + '.log')
$jsonPath = Join-Path $OutputDirectory ($prefix + '.jsonl')
$contextPath = Join-Path $OutputDirectory ($prefix + '.context.json')
$errorPath = Join-Path $OutputDirectory ($prefix + '.adb-errors.txt')
$hashPath = Join-Path $OutputDirectory ($prefix + '.sha256.txt')
$utf8 = [System.Text.UTF8Encoding]::new($false)
$raw = [System.IO.StreamWriter]::new($rawPath, $false, $utf8)
$json = [System.IO.StreamWriter]::new($jsonPath, $false, $utf8)
$context = @{ platform = $Platform; hostStartedAt = (Get-Date -Format o); mode = 'LOGCAT_READ_ONLY_NO_TRANSPORT'; encoding = 'UTF-8_NO_BOM'; automaticConclusion = 'INSUFFICIENT_EVIDENCE' }
[System.IO.File]::WriteAllText($contextPath, ($context | ConvertTo-Json), $utf8)
Write-Host "Campagne $Platform. Active la meme campagne dans la page telephone."
Write-Host 'Capture avant/apres l evenement ; termine avec Ctrl+C. Aucun ancien journal efface.'
Write-Host $rawPath
try {
    & $adb -s $DeviceSerial logcat -v raw -s 'SceneVibeMultiAd:I' '*:S' 2> $errorPath |
        ForEach-Object {
            $line = ([string]$_).Replace($DeviceSerial, '[DEVICE_REDACTED]')
            $raw.WriteLine($line)
            $raw.Flush()
            try {
                $record = $line | ConvertFrom-Json -ErrorAction Stop
                if ($record.platform -eq $Platform -and $record.event -and $null -ne $record.elapsedMs -and $null -ne $record.campaignStartedMs) {
                    $candidate = [long]$record.campaignStartedMs
                    if ($candidate -le 0) { return }
                    if (-not $campaignPinned -and ($campaign -eq 0 -or ($record.event -eq 'ENABLED' -and $candidate -ne $campaign))) {
                        $campaign = $candidate
                        # Replace only this invocation's filtered output, never an original/old capture.
                        $json.Dispose()
                        $json = [System.IO.StreamWriter]::new($jsonPath, $false, $utf8)
                    }
                    if ($candidate -ne $campaign) { return }
                    $json.WriteLine($line)
                    $json.Flush()
                }
            } catch {
                # Keep headers/non-JSON lines in the raw log, never fabricate events.
            }
        }
    if ($LASTEXITCODE -ne 0) { Write-Warning 'ADB termine avec une erreur ; conserve les captures et le diagnostic.' }
} finally {
    $raw.Dispose()
    $json.Dispose()
    if (Test-Path -LiteralPath $errorPath) {
        $errors = [System.IO.File]::ReadAllText($errorPath).Replace($DeviceSerial, '[DEVICE_REDACTED]')
        [System.IO.File]::WriteAllText($errorPath, $errors, $utf8)
    }
    $context.campaignStartedMs = $campaign
    $context.campaignSelection = if ($campaignPinned) { 'EXPLICIT_MONOTONIC_CAMPAIGN' } else { 'LAST_SEEN_PLATFORM_CAMPAIGN_RAW_PRESERVED' }
    [System.IO.File]::WriteAllText($contextPath, ($context | ConvertTo-Json), $utf8)
    $hashes = foreach ($path in @($rawPath, $jsonPath, $contextPath, $errorPath)) {
        if (Test-Path -LiteralPath $path) { '{0}  {1}' -f (Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash.ToLowerInvariant(), (Split-Path -Leaf $path) }
    }
    [System.IO.File]::WriteAllLines($hashPath, [string[]]$hashes, $utf8)
}
