[CmdletBinding()]
param(
  [Parameter(Mandatory = $true)]
  [string]$AdbPath,
  [Parameter(Mandatory = $true)]
  [ValidatePattern('^[A-Za-z0-9_.:-]+$')]
  [string]$Serial,
  [switch]$VodConfirmed,
  [string]$OutputDirectory = '.'
)

<#+.SYNOPSIS
Vérifie E5-041, capture tous les événements des deux tags E5, puis déclenche une seule fois.
.DESCRIPTION
Le fichier conserve les décisions intermédiaires et terminales sans exporter les autres tags
Android ou les identifiants média du probe. Aucun retry, PLAY, PAUSE, Activity ou reset.
#>

$ErrorActionPreference = 'Stop'

function Read-E5Records {
  <#+  .SYNOPSIS
  Décode les événements structurés disponibles ; une ligne tronquée reste dans le fichier brut E5.
  #>
  param([string]$Path)
  if (-not (Test-Path -LiteralPath $Path)) { return }
  Get-Content -LiteralPath $Path | ForEach-Object {
    if ($_ -match 'SceneVibeE5(?:\s*\(\s*\d+\s*\))?\s*:\s*(\{.*\})\s*$') {
      try { $Matches[1] | ConvertFrom-Json } catch {
        # Une ligne partielle ne peut jamais servir de preuve de décision terminale.
      }
    }
  }
}

if (-not $VodConfirmed) { throw 'Confirme explicitement la VOD en lecture avec -VodConfirmed.' }
$adb = (Resolve-Path -LiteralPath $AdbPath).Path
$version = & $adb -s $Serial shell dumpsys package com.scenevibe.tvcompanionpoc 2>&1
if ($LASTEXITCODE -ne 0 -or ($version -join "`n") -notmatch 'versionCode=17\b' -or
    ($version -join "`n") -notmatch 'versionName=0\.8\.2-tv-release-hardening-e5-native041-diagnostics(?=\s|$)') {
  throw 'Version E5-041 non vérifiée ; aucun déclenchement effectué.'
}
$directory = (New-Item -ItemType Directory -Force -Path $OutputDirectory).FullName
$prefix = Join-Path $directory ('scenevibe-e5-041-' + (Get-Date -Format 'yyyyMMdd-HHmmss'))
$log = $prefix + '-diagnostic-logcat.txt'
$errors = $prefix + '-capture-errors.txt'
$capture = Start-Process -FilePath $adb `
  -ArgumentList @('-s', $Serial, 'logcat', '-b', 'main', '-b', 'system', '-v', 'threadtime',
    '-T', '1', 'SceneVibeE5:V', 'SceneVibeE5Adb:V', '*:S') `
  -RedirectStandardOutput $log -RedirectStandardError $errors -NoNewWindow -PassThru
try {
  Start-Sleep -Milliseconds 500
  if ($capture.HasExited) { throw 'Capture ADB arrêtée avant la tentative ; consulte capture-errors.' }
  $previousAttempts = @(Read-E5Records -Path $log |
    Where-Object { $_.event -eq 'REQUEST' } | ForEach-Object { $_.attemptId })
  $reply = & $adb -s $Serial shell am broadcast `
    -n 'com.scenevibe.tvcompanionpoc/.E5AdbTriggerReceiver' `
    -a 'com.scenevibe.tvcompanionpoc.E5_ADB_VOD_INTERLUDE' `
    --ez e5VodConfirmed true 2>&1
  $dispatchExit = $LASTEXITCODE
  $reply | Out-File -FilePath ($prefix + '-broadcast.txt') -Encoding utf8
  $reply | Write-Output
  if ($dispatchExit -ne 0 -or ($reply -join "`n") -notmatch 'QUEUED_GUARDS_PENDING') {
    throw 'Demande refusée ou non livrée. Aucun retry ; traces E5 conservées.'
  }
  Start-Sleep -Seconds 30
} finally {
  if (-not $capture.HasExited) {
    Stop-Process -Id $capture.Id
    $capture.WaitForExit()
  }
}
$events = @(Read-E5Records -Path $log)
$attempts = @($events | Where-Object {
    $_.event -eq 'REQUEST' -and $_.attemptId -notin $previousAttempts
  } | ForEach-Object { $_.attemptId } | Select-Object -Unique)
if ($attempts.Count -ne 1) {
  throw ('Tentative nouvelle non univoque : ' + $attempts.Count + '. Aucun retry ; conserve ' + $log)
}
$attempt = $attempts[0]
$current = @($events | Where-Object { $_.attemptId -eq $attempt })
$current | ConvertTo-Json -Depth 8 |
  Out-File -FilePath ($prefix + '-attempt.json') -Encoding utf8
$terminal = $current | Where-Object { $_.event -eq 'CLEANUP' -and $_.terminal } | Select-Object -Last 1
Write-Output ('Tentative : ' + $attempt)
$current | Where-Object { $null -ne $_.selection } |
  Group-Object { $_.selection.query } | ForEach-Object {
    $record = $_.Group[0]
    [PSCustomObject]@{
      Query = $record.selection.query
      Operation = $record.selection.operation
      Selection = $record.selection.code
      Foreground = $record.selection.foreground
      Reads = $record.selection.read
      Sessions = $record.selection.sessionCount
      Prime = $record.selection.primeCandidates
    }
  } | Format-Table -AutoSize
if ($null -eq $terminal) { throw ('TERMINAL NON OBSERVE : diagnostic incomplet. Conserve ' + $log) }
if ($null -eq $terminal.selection) {
  throw ('Aucune sélection observée : refus avant choose ou diagnostic incomplet. Conserve ' + $log)
}
Write-Output ('Terminal réel : ' + $terminal.phase + ' ; transport=' + $terminal.reason +
  ' ; selection=' + $terminal.selection.code + ' ; foreground=' + $terminal.selection.foreground +
  ' ; reads=' + $terminal.selection.read)
Write-Output ('Traces E5 : ' + $log)
Write-Output 'Diagnostic uniquement : aucun PASS fonctionnel ou physique déduit par ce script.'
