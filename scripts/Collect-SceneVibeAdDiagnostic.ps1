<#
.SYNOPSIS
Capture ciblée des futures observations publicitaires et du calendrier SceneVibe.
.DESCRIPTION
Lecture seule des propres tags SceneVibe, à partir du dernier message existant
puis en continu. Aucun effacement du buffer, transport lecteur, capture d'écran,
installation, arrêt de service ou changement d'autorisation. Lancer avant la
publicité et conserver au moins 30 secondes après le retour au film. Les boutons
du téléphone fournissent des annotations du propriétaire, pas une détection.
.PARAMETER DeviceSerial
Identifiant de la télévision déjà connectée, affiché par adb devices.
.PARAMETER OutputDirectory
Dossier local de destination ; un fichier distinct est créé pour chaque capture.
.EXAMPLE
.\Collect-SceneVibeAdDiagnostic.ps1 -DeviceSerial 'adresse-deja-connectee:5555'
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$DeviceSerial,
    [string]$OutputDirectory = (Join-Path $PWD 'scenevibe-ad-diagnostic')
)
$ErrorActionPreference = 'Stop'
New-Item -ItemType Directory -Force -Path $OutputDirectory | Out-Null
$diagnosticFile = Join-Path $OutputDirectory ('scenevibe-ad-detection-' + (Get-Date -Format 'yyyyMMdd-HHmmss') + '.log')
('Capture hote : ' + (Get-Date -Format o)) | Out-File -LiteralPath $diagnosticFile -Encoding utf8
Write-Host 'Observe puis annote sur le telephone. Attends la reprise et termine avec Ctrl+C.'
Write-Host $diagnosticFile
# -T 1 garde le suivi continu tout en évitant de confondre une ancienne session avec cette capture.
& adb -s $DeviceSerial logcat -T 1 -v threadtime -s 'SceneVibeAdProbe:I' 'SceneVibeAdSync:I' 'SceneVibeMedia:I' 'SceneVibeText009:I' 'SceneVibeRuntime:I' 'SceneVibeCache:I' 'SceneVibeControls:I' '*:S' |
    ForEach-Object { $_ | Out-File -LiteralPath $diagnosticFile -Append -Encoding utf8 }
if ($LASTEXITCODE -ne 0) { throw 'Capture interrompue ou connexion indisponible ; conserve le fichier deja recu.' }
