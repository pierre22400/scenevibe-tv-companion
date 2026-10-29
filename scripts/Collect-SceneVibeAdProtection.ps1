<#
.SYNOPSIS
Capture continue des propres signaux et transitions de protection SceneVibe.
.DESCRIPTION
Lecture seule : buffers disponibles puis suivi jusqu'a Ctrl+C. Ne modifie aucun
lecteur, permission, cache ou processus ; ne capture ni ecran ni audio protege.
Les annotations du telephone ne sont jamais des entrees du detecteur automatique.
.PARAMETER DeviceSerial
Numero exact deja affiche dans adb devices. Aucune connexion a une Sony n'est creee.
.PARAMETER OutputDirectory
Dossier local pour une capture distincte horodatee, en UTF-8 explicite.
.EXAMPLE
.\Collect-SceneVibeAdProtection.ps1 -DeviceSerial 'SERIAL_DEJA_CONNECTE'
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$DeviceSerial,
    [string]$OutputDirectory = (Join-Path $PWD 'scenevibe-protection-diagnostic')
)
$ErrorActionPreference = 'Stop'
New-Item -ItemType Directory -Force -Path $OutputDirectory | Out-Null
$diagnosticFile = Join-Path $OutputDirectory ('scenevibe-ad-protection-' + (Get-Date -Format 'yyyyMMdd-HHmmss') + '.log')
('Capture hote : ' + (Get-Date -Format o)) | Out-File -LiteralPath $diagnosticFile -Encoding utf8
Write-Host 'Laisse tourner avant et apres la publicite. Termine avec Ctrl+C.'
Write-Host $diagnosticFile
& adb -s $DeviceSerial logcat -v threadtime -s 'SceneVibeAdGuard:I' 'SceneVibeAdProbe:I' 'SceneVibeRuntime:I' 'SceneVibeText009:I' 'SceneVibeAdSync:I' 'SceneVibeMedia:I' 'SceneVibeCache:I' '*:S' |
    ForEach-Object { $_ | Out-File -LiteralPath $diagnosticFile -Append -Encoding utf8 }
if ($LASTEXITCODE -ne 0) { throw 'Capture interrompue ; conserve les octets deja recus.' }
