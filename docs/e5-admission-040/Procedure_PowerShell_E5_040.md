# E5-040 — collecte propriétaire, pas de candidate fonctionnelle

**BLOCKED — ROOT CAUSE NOT PROVEN.** Aucune APK E5-040 à installer ; E5-039 reste
PHYSICAL FAIL. Ne pas interpréter QUEUED_GUARDS_PENDING comme une réussite.
WORK n'a exécuté aucune opération ADB sur la Sony. Les commandes ci-dessous sont
réservées à ta décision explicite de poursuivre une collecte ; elles ne corrigent rien.

## Contexte sans quitter Prime

Dans le dossier platform-tools, remplace le numéro de série par celui déjà autorisé :

```powershell
$Sony = 'ADRESSE_SONY:5555'
$Dossier = Join-Path $PWD ('e5-040-contexte-' + (Get-Date -Format 'yyyyMMdd-HHmmss'))
New-Item -ItemType Directory -Path $Dossier -ErrorAction Stop | Out-Null
& .\adb.exe -s $Sony shell dumpsys package com.scenevibe.tvcompanionpoc |
    Set-Content -Encoding UTF8 (Join-Path $Dossier 'package.txt')
& .\adb.exe -s $Sony shell appops get com.scenevibe.tvcompanionpoc GET_USAGE_STATS |
    Set-Content -Encoding UTF8 (Join-Path $Dossier 'usage-access.txt')
& .\adb.exe -s $Sony shell dumpsys activity activities |
    Set-Content -Encoding UTF8 (Join-Path $Dossier 'activities.txt')
& .\adb.exe -s $Sony shell dumpsys media_session |
    Set-Content -Encoding UTF8 (Join-Path $Dossier 'media-session.txt')
& .\adb.exe -s $Sony shell dumpsys usagestats |
    Set-Content -Encoding UTF8 (Join-Path $Dossier 'usage-events.txt')
& .\adb.exe -s $Sony logcat -b all -d -v threadtime '*:V' |
    Set-Content -Encoding UTF8 (Join-Path $Dossier 'full-logcat.txt')
if ($LASTEXITCODE -ne 0) { throw 'Collecte logcat échouée : conserver les sorties précédentes.' }
```

Lecture seule : pas de broadcast, navigation, installation, effacement de logs, changement de
permission ni transport PLAY/PAUSE. Vérifie aussi la sortie et le code de chaque commande ;
un dumpsys refusé ou tronqué ne prouve pas l'absence de session/événement.
Les dumps peuvent contenir titres, historique et autres données d'applications : conserve
les originaux en privé, ne les publie pas automatiquement dans une PR.

Le résumé secondaire peut être extrait ainsi, en conservant le journal complet :

```powershell
Get-Content (Join-Path $Dossier 'full-logcat.txt') |
    Select-String 'SceneVibeE5|SceneVibeE5Adb' |
    Set-Content -Encoding UTF8 (Join-Path $Dossier 'e5-summary.txt')
```

Les dumps pris après l'échec ne révèlent pas forcément le motif exact au moment de choose.
Le prochain diagnostic applicatif doit distinguer les sorties de premier plan, accès,
concurrence et double lecture, comme précisé dans le rapport. Répéter seulement le même
broadcast sur E5-039 ne garantit pas que l'information manquante apparaisse.

## Futur test fonctionnel, après levée du blocage

Une candidate corrigée devra être identifiée par son propre versionCode >16, versionName,
SHA-256 et certificat compatible vérifiés, puis installée par toi avec `adb install -r`.
Pas d'uninstall/reset. Le déclenchement explicite attestera la VOD réellement en lecture.
Une capture tous buffers devra démarrer avant le déclenchement et conserver l'état terminal
de l'attemptId. La télécommande reste sous ton contrôle ; aucune commande de reprise de secours
n'est automatisée.

Tu devras observer les sept critères : Prime reste ouvert jusqu'au déclenchement, réception
ADB, admission acceptée, pause effective, interlude audiovisuel, reprise au bon endroit,
télécommande nominale. Un refus expliqué ou un ACK n'est pas un PASS fonctionnel.
