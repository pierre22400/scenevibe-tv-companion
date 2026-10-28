<#
.SYNOPSIS
Collecte complète et continue des diagnostics SceneVibe, sans mutation Android.
.DESCRIPTION
Lit les buffers existants puis les nouvelles transitions de SceneVibe. Commencer
avant l'action, laisser tourner après la reprise, arrêter avec Ctrl+C. Aucun
logcat -c, force-stop, commande de lecteur, effacement de données ou installation.
Les filtres excluent les journaux des plateformes vidéo. Ne pas publier le fichier
sans examen de confidentialité. UTF-8 explicite, y compris sous PowerShell 5.1.
.PARAMETER DeviceSerial
Identifiant exact de la télévision déjà connectée dans adb devices.
.PARAMETER OutputDirectory
Dossier local recevant un fichier horodaté distinct pour chaque capture.
.EXAMPLE
.\Collect-SceneVibeDiagnostic.ps1 -DeviceSerial 'adresse-connue:5555'
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$DeviceSerial,
    [string]$OutputDirectory = (Join-Path $PWD 'scenevibe-diagnostic')
)
$ErrorActionPreference = 'Stop'
New-Item -ItemType Directory -Force -Path $OutputDirectory | Out-Null
$diagnosticFile = Join-Path $OutputDirectory ('scenevibe-v22-' + (Get-Date -Format 'yyyyMMdd-HHmmss') + '.log')
Write-Host 'Capture SceneVibe : reproduis le problème puis attends la reprise. Ctrl+C pour terminer.'
Write-Host $diagnosticFile
# Aucun -d ou -t : lecture des buffers disponibles, puis suivi continu.
& adb -s $DeviceSerial logcat -v threadtime -s 'SceneVibeControls:I' 'SceneVibeAdSync:I' 'SceneVibeMedia:I' 'SceneVibeText009:I' 'SceneVibeCache:I' '*:S' |
    ForEach-Object { $_ | Out-File -LiteralPath $diagnosticFile -Append -Encoding utf8 }
if ($LASTEXITCODE -ne 0) { throw 'Capture adb interrompue ou connexion indisponible ; conserve le fichier déjà reçu.' }
