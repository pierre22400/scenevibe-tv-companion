# E5-041 — une tentative de diagnostic Sony

Cette APK observe les gardes existantes. Elle ne corrige pas l'admission E5.
WORK n'a exécuté aucune opération ADB sur ta Sony.

## 1. Vérifier, installer, contrôler

Télécharge l'APK diagnostique et `Invoke-E5-041.ps1`, place-les avec `adb.exe`.
Verdict : **READY FOR SONY DIAGNOSTIC**. Signature stable vérifiée contre la référence E5-039.
Vérifie le SHA-256 ci-dessous avant toute installation.

```powershell
$Sony = 'ADRESSE_SONY:5555'
$Apk = '.\SceneVibe_E5_041_diagnostic_stable.apk'
$Attendu = 'a7c5ea934b0e3ab909eece48cc9233d5dde786a05594afed908d1e2d7aec01f6'
if ((Get-FileHash -Algorithm SHA256 -LiteralPath $Apk).Hash -ne $Attendu) {
  throw 'SHA-256 APK différent : arrêt.'
}
& .\adb.exe -s $Sony install -r $Apk
if ($LASTEXITCODE -ne 0) { throw 'Installation échouée : arrêt sans déclenchement.' }
& .\adb.exe -s $Sony shell dumpsys package com.scenevibe.tvcompanionpoc |
  Select-String 'versionCode=|versionName='
```

Le champ de log historique `profile=E5-NATIVE-035` reste inchangé ; il ne remplace pas le contrôle de version.

Attendu : versionCode **17**, versionName **0.8.2-tv-release-hardening-e5-native041-diagnostics**.
La signature doit permettre la mise à jour `-r` de l'installation existante. Pas d'uninstall,
reset, effacement de données ou modification de permission automatisée. Vérifie le résultat réel.

## 2. Déclencher une seule fois depuis Prime

Lance Columbo sur Prime Video et vérifie qu'il joue effectivement. Reste dans Prime.
Ne lance pas Companion/Diagnostics et n'envoie pas un autre broadcast en parallèle.

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\Invoke-E5-041.ps1 `
  -AdbPath .\adb.exe -Serial $Sony -VodConfirmed -OutputDirectory .\diagnostic-e5-041
```

Bypass ne s'applique qu'à ce processus PowerShell. Le script vérifie E5-041, démarre une
capture ciblée, envoie un seul broadcast attestant ta VOD puis attend 30 secondes.
Il arrête uniquement le processus logcat du PC. Il ne commande jamais PLAY, PAUSE ou la
télécommande et ne tente aucune relance. Une erreur conserve les traces ; ne répète pas la tentative.

## 3. Lire et transmettre le diagnostic

Le script affiche l'attemptId, les requêtes SELECT/OBSERVE/PAUSE/PLAY et le terminal réel :
`transport=TARGET ; selection=FOREGROUND_REFUSED ; foreground=NO_RELEVANT_EVENTS`, par exemple.
Cet exemple décrit le format, pas un nouveau résultat Sony.

Conserve et fournis :

- `*-diagnostic-logcat.txt` : tous les événements des deux tags E5, sans les autres journaux Android ;
- `*-attempt.json` : les événements de la seule nouvelle tentative, jusqu'à CLEANUP ;
- `*-broadcast.txt` et `*-capture-errors.txt` : livraison et éventuelles erreurs de capture.

`selection.code`, `selection.foreground`, `selection.read`, `selection.metadataDifference`,
`stage/readStep` et les deux séries de valeurs permettent d'expliquer le refus interne.
QUEUED_GUARDS_PENDING n'est toujours que l'ACK de mise en attente. Sans terminal, sans
sélection ou avec plusieurs IDs, le diagnostic est incomplet : garde les fichiers et signale-le.
Un événement de refus avant REQUEST peut aussi expliquer qu'aucun choose n'ait été exécuté.

Si SELECTED apparaît, note l'état terminal et tes observations sans déclarer le défaut corrigé.
Le verdict de ce kit est uniquement READY FOR SONY DIAGNOSTIC après qualification logicielle.
Les observations de pause/interlude/reprise et de télécommande restent des faits physiques à
rapporter séparément ; aucun script ne les déduit d'un ACK ou d'un test local.
