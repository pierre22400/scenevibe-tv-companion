# Recette Sony après GO propriétaire. Le mode par défaut lit seulement l'APK et la TV.
# Utiliser platform-tools et build-tools officiels. Aucun appairage ni effacement automatique.
[CmdletBinding()]
param(
    [Parameter(Mandatory)][string]$Serial,
    [Parameter(Mandatory)][string]$Apk,
    [Parameter(Mandatory)][ValidatePattern('^[a-fA-F0-9]{64}$')][string]$Sha256,
    [Parameter(Mandatory)][string]$BuildTools,
    [switch]$Install
)
$ErrorActionPreference = 'Stop'
$Package = 'com.scenevibe.tvcompanionpoc'
$Certificate = 'f908bf564ed97ba67e02b1ebc89eb0239cf980752587f55eb9ec0419791a2e9c'
$Adb = (Get-Command adb -ErrorAction Stop).Source
$Aapt = Join-Path $BuildTools 'aapt.exe'
$Signer = Join-Path $BuildTools 'apksigner.bat'

# Exécute un outil Android et refuse un échec de commande avant toute autre opération.
function Invoke-Tool {
    param([string]$Executable, [string[]]$CommandArgs)
    $Result = & $Executable @CommandArgs 2>&1
    if ($LASTEXITCODE -ne 0) { throw "Commande Android refusée ($LASTEXITCODE). $Result" }
    return [string]($Result -join "`n")
}

# Vérifie package, VersionCode, certificat et schémas modernes du fichier exact.
function Assert-Apk {
    param([string]$File, [int]$Version)
    $Badging = Invoke-Tool $Aapt @('dump', 'badging', $File)
    if ($Badging -notmatch "package: name='$Package' versionCode='$Version'" -or $Badging -match 'application-debuggable') {
        throw "Package, VersionCode $Version ou type de build incompatible."
    }
    $Signing = Invoke-Tool $Signer @('verify', '--verbose', '--print-certs', $File)
    foreach ($Line in @(
        "Signer #1 certificate SHA-256 digest: $Certificate",
        'Number of signers: 1',
        'Verified using v2 scheme (APK Signature Scheme v2): true',
        'Verified using v3 scheme (APK Signature Scheme v3): true'
    )) {
        if (($Signing -split '?
') -notcontains $Line) { throw "Certificat M5 ou schéma de signature absent." }
    }
}

# Demande à Android le chemin de l'APK du package présent sur le seul appareil désigné.
function Get-InstalledBase {
    $Paths = Invoke-Tool $Adb @('-s', $Serial, 'shell', 'pm', 'path', $Package)
    $Base = ($Paths -split '?
' | Where-Object { $_ -match '^package:.+/base.apk$' } | Select-Object -First 1)
    if (-not $Base) { throw 'APK de base du Companion absent.' }
    return $Base.Substring(8).Trim()
}

# Capture les concessions système et l'UID pour détecter une perte après l'installation.
function Read-Grants {
    return [ordered]@{
        Uid = Invoke-Tool $Adb @('-s', $Serial, 'shell', 'pm', 'list', 'packages', '-U', $Package)
        Overlay = (Invoke-Tool $Adb @('-s', $Serial, 'shell', 'appops', 'get', $Package, 'SYSTEM_ALERT_WINDOW')) -replace ';.*',''
        Media = Invoke-Tool $Adb @('-s', $Serial, 'shell', 'settings', 'get', 'secure', 'enabled_notification_listeners')
        Accessibility = Invoke-Tool $Adb @('-s', $Serial, 'shell', 'settings', 'get', 'secure', 'enabled_accessibility_services')
        Shortcut = Invoke-Tool $Adb @('-s', $Serial, 'shell', 'settings', 'get', 'secure', 'accessibility_shortcut_target_service')
    }
}

if ((Get-FileHash -Algorithm SHA256 $Apk).Hash -ne $Sha256) { throw 'SHA-256 v40 différent de la preuve GitHub Actions.' }
Assert-Apk $Apk 40
$State = Invoke-Tool $Adb @('-s', $Serial, 'get-state')
if ($State -ne 'device') { throw 'Appareil ADB indisponible.' }
$Tv = Invoke-Tool $Adb @('-s', $Serial, 'shell', 'getprop', 'ro.build.characteristics')
if ($Tv -notmatch 'tv') { throw 'La cible ne se présente pas comme Android TV.' }
$Scratch = Join-Path ([System.IO.Path]::GetTempPath()) ("SceneVibeQ2-" + [guid]::NewGuid())
New-Item -Path $Scratch -ItemType Directory | Out-Null
try {
    $BeforeApk = Join-Path $Scratch 'before.apk'
    Invoke-Tool $Adb @('-s', $Serial, 'pull', (Get-InstalledBase), $BeforeApk) | Out-Null
    Assert-Apk $BeforeApk 38
    $Before = Read-Grants
    Write-Output "Précontrôle OK. v38 installée, APK v40 et certificat M5 vérifiés pour $Serial."
    if (-not $Install) { return }
    Invoke-Tool $Adb @('-s', $Serial, 'install', '-r', $Apk)
    $AfterApk = Join-Path $Scratch 'after.apk'
    Invoke-Tool $Adb @('-s', $Serial, 'pull', (Get-InstalledBase), $AfterApk) | Out-Null
    Assert-Apk $AfterApk 40
    if ((Get-FileHash -Algorithm SHA256 $AfterApk).Hash -ne $Sha256) { throw 'Le fichier effectivement installé diffère de l’APK qualifiée.' }
    $After = Read-Grants
    foreach ($Key in $Before.Keys) {
        if ($Before[$Key] -cne $After[$Key]) { throw "Repère Android modifié après installation. Contrôle physique requis pour $Key." }
    }
    Write-Output 'Mise à niveau constatée. Valider association, caches et restitution selon la recette.'
} finally {
    Remove-Item -LiteralPath $Scratch -Recurse -Force
}
