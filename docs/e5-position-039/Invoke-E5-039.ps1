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

<#
Capture tous les buffers ADB, déclenche une seule demande E5 et extrait les décisions.
Ce script PC ne commande jamais PLAY, n'ouvre pas Companion et ne qualifie pas la TV.
#>

$ErrorActionPreference = 'Stop'
if (-not $VodConfirmed) {
  throw 'Confirme explicitement un épisode VOD avec -VodConfirmed.'
}
$adb = (Resolve-Path -LiteralPath $AdbPath).Path
$directory = (New-Item -ItemType Directory -Force -Path $OutputDirectory).FullName
$prefix = Join-Path $directory ('scenevibe-e5-039-' + (Get-Date -Format 'yyyyMMdd-HHmmss'))
$log = $prefix + '-full-logcat.txt'
$errors = $prefix + '-capture-errors.txt'
$capture = Start-Process -FilePath $adb `
  -ArgumentList @('-s', $Serial, 'logcat', '-b', 'all', '-v', 'threadtime', '-T', '1', '*:V') `
  -RedirectStandardOutput $log `
  -RedirectStandardError $errors `
  -NoNewWindow -PassThru
try {
  Start-Sleep -Milliseconds 500
  if ($capture.HasExited) {
    throw 'La capture ADB a quitté avant la tentative ; vérifie le serial et le fichier capture-errors.'
  }
  $reply = & $adb -s $Serial shell am broadcast `
    -n 'com.scenevibe.tvcompanionpoc/.E5AdbTriggerReceiver' `
    -a 'com.scenevibe.tvcompanionpoc.E5_ADB_VOD_INTERLUDE' `
    --ez e5VodConfirmed true 2>&1
  $reply | Out-File -FilePath ($prefix + '-broadcast.txt') -Encoding utf8
  $reply | Write-Output
  if ($LASTEXITCODE -ne 0 -or ($reply -join "`n") -notmatch 'QUEUED_GUARDS_PENDING') {
    throw 'Demande refusée ou non livrée. Aucun retry. Les traces sont conservées.'
  }
  Start-Sleep -Seconds 30
} finally {
  if (-not $capture.HasExited) {
    Stop-Process -Id $capture.Id
    $capture.WaitForExit()
  }
  if (Test-Path -LiteralPath $log) {
    Select-String -LiteralPath $log -Pattern 'SceneVibeE5|AndroidRuntime|FATAL EXCEPTION' |
      ForEach-Object { $_.Line } |
      Out-File -FilePath ($prefix + '-e5-summary.txt') -Encoding utf8
  }
}
$terminal = @()
Get-Content -LiteralPath $log | ForEach-Object {
  if ($_ -match 'SceneVibeE5.*?(\{.*\})\s*$') {
    try {
      $event = $Matches[1] | ConvertFrom-Json
      if ($event.event -eq 'CLEANUP' -and $event.terminal) {
        $terminal += $event
      }
    } catch {
      # Une ligne tronquée reste dans le journal brut ; elle n'est jamais une réussite.
    }
  }
}
if ($terminal.Count -eq 0) {
  Write-Output 'TERMINAL NON OBSERVE : résultat incomplet, aucune réussite déduite.'
} else {
  $terminal | Select-Object attemptId, phase, event, reason | Format-Table
}
Write-Output ('Journal complet : ' + $log)
Write-Output 'La qualification physique exige ton constat de pause, image+son et reprise.'
