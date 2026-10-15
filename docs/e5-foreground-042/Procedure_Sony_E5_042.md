# E5-042 — une seule tentative fonctionnelle Sony

**READY FOR SONY FUNCTIONAL TEST. APK signée et vérifiée ; aucune qualification physique revendiquée.
WORK n’a effectué aucun accès à la Sony.**

## Vérifier et mettre à jour

Télécharge le [script à déclenchement unique](Invoke-E5-042.ps1). Place-le avec
`SceneVibe_E5_042_foreground_stable.apk` et `adb.exe` dans un dossier. Utilise la connexion ADB
déjà établie pour E5-041.
Vérifier le SHA-256 publié, puis installer par mise à jour, sans désinstaller ni effacer les données :

```powershell
$Sony = 'ADRESSE_SONY:5555'
$Apk = '.\SceneVibe_E5_042_foreground_stable.apk'
$Attendu = '5864262a1978313b1e0d04689ffe963d5fa77629de263fdaf3a0135155854318'
if ((Get-FileHash -Algorithm SHA256 -LiteralPath $Apk).Hash -ne $Attendu) {
  throw 'SHA-256 différent : arrêt.'
}
& .\adb.exe -s $Sony install -r $Apk
if ($LASTEXITCODE -ne 0) { throw 'Installation échouée : arrêt.' }
& .\adb.exe -s $Sony shell dumpsys package com.scenevibe.tvcompanionpoc |
  Select-String 'versionCode=|versionName='
```

Attendu : versionCode **18**, versionName **0.8.2-tv-release-hardening-e5-native042-foreground**.
Les autorisations déjà accordées restent sous le contrôle du propriétaire ; aucune permission
n'est automatiquement modifiée par le kit. Le script refuse une autre version.

## Déclencher une fois

Après installation, mettre manuellement la TV en veille puis la réveiller. Ce cycle fournit
un repère explicite de premier plan ; il ne faut ni désinstaller Companion ni effacer les données.
Lancer ensuite Columbo sur Prime Video, vérifier la VOD réellement en lecture, puis rester dans Prime
**au moins 45 secondes** sans ouvrir Companion/Diagnostics. Cela dépasse délibérément l'ancienne
fenêtre de 30 secondes. Garder la TV allumée et déverrouillée. Ne pas effectuer d'autre commande
ou navigation pendant cette unique tentative.

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\Invoke-E5-042.ps1 `
  -AdbPath .\adb.exe -Serial $Sony -VodConfirmed -OutputDirectory .\preuve-e5-042
```

Le Bypass concerne uniquement ce processus. Le script collecte les deux tags E5, livre un seul
broadcast, attend 30 secondes et conserve l'attemptId unique ainsi que le terminal. Il ne lance
aucune Activity et n'envoie directement aucune commande média. Il ne relance jamais la tentative.
Ne pas réutiliser le script041 : sa vérification de version est volontairement incompatible.

## Constater et transmettre

Noter la réalité observée : Columbo se met-il en pause, l'interlude de 10 secondes apparaît-il,
Columbo reprend-il au bon endroit ? Un ACK ou SELECTED ne constitue pas un succès physique.
Attendu uniquement si toutes les preuves sont valides : pause confirmée, interlude puis reprise
confirmée et terminal SUCCESS. Sinon, noter le refus et **arrêter sans deuxième tentative**.

Transmettre les fichiers `*-diagnostic-logcat.txt`, `*-attempt.json`, `*-broadcast.txt`,
`*-capture-errors.txt` et les observations visuelles/sonores. Un refus HISTORY_INCOMPLETE signifie
que l'historique retenu ne fournit pas le repère ou la cohérence requis ; il ne faut pas le forcer.
Les noms d'applications tierces et l'historique UsageEvents ne sont pas exportés par le kit.
Un blocage sans nouveau terminal doit aussi être rapporté avec les fichiers disponibles.
