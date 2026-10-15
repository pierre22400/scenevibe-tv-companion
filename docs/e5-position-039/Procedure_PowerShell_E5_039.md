# E5-039 — nouveau test Sony, après décision de Pierre

Ce document prépare le test ; aucune commande n'a été exécutée sur la Sony.
L'APK garde l'identité `com.scenevibe.tvcompanionpoc`, passe à versionCode **16** et
versionName `0.8.2-tv-release-hardening-e5-native039-position`.
SHA-256 exact :
`aacda02ac414e9d4e8a885870fcaf5f4e444eeb0db746a7c3aef80231d02db64`.
Certificat identique à la signature E5 installée :
`f908bf564ed97ba67e02b1ebc89eb0239cf980752587f55eb9ec0419791a2e9c`.

1. Vérifie le SHA-256 de l'APK avec `Get-FileHash .\SceneVibe_E5_039_stable.apk -Algorithm SHA256`.
2. Fais une mise à jour en place, sans désinstaller ni effacer les données :

```powershell
.\adb.exe -s "ADRESSE_SONY:5555" install -r .\SceneVibe_E5_039_stable.apk
.\adb.exe -s "ADRESSE_SONY:5555" shell dumpsys package com.scenevibe.tvcompanionpoc |
  Select-String 'versionCode=|versionName='
```

Conserve les autorisations existantes. Cloud non configuré reste attendu.
Retourne sur Prime, lance l'épisode VOD et laisse-le progresser. N'ouvre pas Companion.

3. Lance une seule tentative avec le script livré, depuis le PC, en adaptant le serial :

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass `
  -File .\Invoke-E5-039.ps1 `
  -AdbPath .\adb.exe `
  -Serial "ADRESSE_SONY:5555" `
  -VodConfirmed
```

Le paramètre Bypass s’applique à ce seul processus PowerShell. Le script n'envoie qu'un broadcast, attend 30 secondes et arrête seulement le processus
PC qui capture les logs. Il n'envoie aucune commande PLAY/PAUSE directe, n'installe
rien et n'ouvre aucune Activity. Aucune relance automatique.
`QUEUED_GUARDS_PENDING` signifie réception, pas réussite.

4. Observe toi-même : Prime se met en pause, l'interlude image+son apparaît environ
10 secondes, puis l'épisode reprend au bon endroit. Si un refus ou RECOVERY_REQUIRED
survient, conserve les traces et reprends manuellement avec la télécommande si besoin.
Même un terminal logiciel SUCCESS ne remplace pas ces trois observations physiques.

Le fichier `*-full-logcat.txt` est complet : collecte de tous les buffers, sans filtre
par tag ou PID. La synthèse `*-e5-summary.txt` est un extrait secondaire. Le brut peut
contenir des informations d'autres applications : conserve-le pour diagnostic, sans
publication publique automatique.

Extraction complète manuelle, sans déclencher une nouvelle tentative :

```powershell
.\adb.exe -s "ADRESSE_SONY:5555" logcat -b all -v threadtime -d '*:V' |
  Out-File -FilePath .\scenevibe-e5-039-full-logcat.txt -Encoding utf8
```

À transmettre : le journal et ton constat pause / interlude audiovisuel / reprise.
Le statut E5 PHYSICALLY QUALIFIED reste réservé à cette observation propriétaire.
