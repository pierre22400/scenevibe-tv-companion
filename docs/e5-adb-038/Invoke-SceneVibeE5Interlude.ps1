param(
  [string]$Serial = ""
)

<#
.SYNOPSIS
  Déclenche uniquement la qualification E5-038 déjà installée, sans ouvrir Companion.
.DESCRIPTION
  À exécuter seulement après décision physique du propriétaire et installation
  distincte de la candidate compatible. Prime doit lire une VOD. Cette commande
  atteste volontairement la nature VOD et laisse toutes les gardes E5 décider.
  Aucun octroi de permission, installation, reset, simulation télécommande ou retry.
#>

$ErrorActionPreference = "Stop"
$targetArguments = @()
if ($Serial) {
  $targetArguments = @("-s", $Serial)
}

$triggerResult = & adb @targetArguments shell am broadcast `
  -n com.scenevibe.tvcompanionpoc/.E5AdbTriggerReceiver `
  -a com.scenevibe.tvcompanionpoc.E5_ADB_VOD_INTERLUDE `
  --ez e5VodConfirmed true

$triggerResult | Write-Output
if ($LASTEXITCODE -ne 0 -or (($triggerResult -join "`n") -notmatch "QUEUED_GUARDS_PENDING")) {
  throw "Déclenchement refusé ou endpoint absent. Aucun nouvel essai automatique."
}

Write-Output "Demande transmise ; validation des gardes E5 en cours. Observe la TV."
Start-Sleep -Seconds 25
& adb @targetArguments logcat -d -t 300 -v time `
  "SceneVibeE5Adb:I" "SceneVibeE5:I" "*:S"
if ($LASTEXITCODE -ne 0) {
  throw "Lecture du diagnostic impossible. Aucun changement de transport."
}
