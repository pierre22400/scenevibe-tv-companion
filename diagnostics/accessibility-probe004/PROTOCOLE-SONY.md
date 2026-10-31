# Probe 004 — protocole Sony futur, NON EXÉCUTÉ

Nécessite un nouveau GO explicite de Pierre pour l'installation et l'activation sur appareil physique. Aucun script de test d'émulateur ne doit être exécuté sur la TV. Ce protocole ne qualifie aucune détection publicitaire.

## Préparer le PC DENIS

Place scenevibe-accessibility-probe004-private-diagnostic.apk dans C:\Users\DENIS\Documents\Kiro\platform-tools. Préserve l'APK et les journaux Probe003 ; ne désinstalle pas cette application.

Dans Windows PowerShell 5.1 ou PowerShell 7, après le futur GO uniquement :

```powershell
Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass
Set-Location 'C:\Users\DENIS\Documents\Kiro\platform-tools'
.\adb.exe devices -l
$serialTV = Read-Host 'Identifiant exact de la Sony affiché par adb devices'
.\adb.exe -s $serialTV shell getprop ro.build.version.release
.\adb.exe -s $serialTV shell settings get secure enabled_accessibility_services
Get-FileHash '.\scenevibe-accessibility-probe004-private-diagnostic.apk' -Algorithm SHA256
```

L'ancien identifiant était 192.168.1.183:5555 ; utilise celui réellement affiché. Compare le SHA-256 au rapport de livraison. Dans Probe003, vérifier volontairement arrêt et export de sa campagne, puis désactiver son service dans les réglages Android. Ne pas laisser deux sondes armées. Aucun effacement ou désinstallation de Probe003 n'est requis.

## Installer et activer, après GO seulement

```powershell
Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass
Set-Location 'C:\Users\DENIS\Documents\Kiro\platform-tools'
$serialTV = Read-Host 'Identifiant ADB exact de la Sony'
.\adb.exe -s $serialTV install '.\scenevibe-accessibility-probe004-private-diagnostic.apk'
.\adb.exe -s $serialTV shell dumpsys package com.scenevibe.accessibilityprobe004 |
  Select-String 'versionCode|versionName'
```

Installation indépendante, jamais par remplacement du Companion. Ouvre manuellement « SceneVibe Probe 004 — privé ». Lis l'information de collecte. Ouvre volontairement les réglages d'accessibilité depuis l'Activity, active seulement le service 004, reviens et actualise. Il doit être CONNECTED_UNARMED. Aucune commande settings put n'est fournie pour forcer son activation physique.

## Phase A — contrôle synthétique autonome

Profil OWN_CONTROL. Première campagne EVENT_ONLY_CONTROL, armer, attendre la stabilité initiale, puis demander le changement silencieux dans cinq secondes. Attendre environ dix secondes, arrêter et exporter. Deuxième campagne, nouvelle ouverture manuelle de l'Activity pour réinitialiser la fixture ; sélectionner NORMAL, répéter. Les journaux doivent distinguer le marqueur/phase synthétique et des nœuds frais périodiques. L'absence effective de callback après mutation doit être vérifiée dans l'analyse ; un clic sur le bouton n'est pas lui-même une mutation silencieuse.

Les tests logiciels fournissent déjà cette comparaison sur émulateur. Sur Sony, un contrôle sans ticks, avec refresh en échec, retards ou OVERLOAD est un défaut instrumental à traiter avant Netflix, pas un résultat sur ses publicités. Les lectures doivent rester dans les bornes et l'export contenir SESSION_END.

## Phase B — Netflix sans publicité

Après succès instrumental et autorisation de campagne : profil NETFLIX, NORMAL, armer puis quitter manuellement la sonde et ouvrir Netflix sans commande injectée. Observer environ une minute de lecture, PAUSE/timeline et reprise. Annoter les actions dans le panneau téléphone v27.1 existant si la collecte MEDIA est également activée volontairement. Revenir manuellement à la sonde, arrêter et exporter.

Aucun écran de sonde ne s'ouvre automatiquement pendant Netflix. Ces manipulations finales changent naturellement la fenêtre active : leurs lignes FOREIGN_PACKAGE de fin ne sont pas une fin publicitaire.

## Phase C — publicité Netflix réelle

Uniquement après GO de cette phase. Une campagne bornée de 3–5 min, maximum absolu dix minutes ; garder du contexte avant et ~20–30 s après le retour au programme. Un SEEK volontaire peut être tenté selon le scénario autorisé ; aucun SEEK automatique ni essais en boucle pour forcer une publicité. Noter le début et la fin physiques, idéalement avec les annotations humaines existantes du téléphone si MEDIA est actif.

Si aucune publicité n'arrive : conserver le contrôle et conclure INSUFFICIENT_EVIDENCE pour la sémantique publicitaire, pas FAIL détecteur. Si une publicité arrive : ne pas assimiler un changement de nœuds, un silence ou une racine nulle à AD_START/AD_END. Identifier la fraîcheur des mesures aux deux frontières.

## Phase D — corrélation MEDIA facultative

Observer avec le diagnostic v27.1 existant, sans changer le Companion et sans track nécessaire. Les deux journaux restent séparés. Corréler hors ligne les elapsedRealtime de réception du même appareil, avec couples uptime/elapsed et frontières de reboot. Les annotations humaines restent retardées et non automatiques. Vérifier séparément la position du programme après retour ; RangeInfo conserve une unité UNKNOWN tant que non calibrée. Aucune restitution TEXT/AUDIO n'est commandée par la sonde.

## Phase E — Disney+

Décision distincte seulement ; nouvelle campagne DISNEY_PLUS et aucun mélange Netflix. Ne pas modifier l'abonnement pour obtenir une publicité. Une absence de publicité ne qualifie ni échec technique ni absence générale de marqueur.

## Arrêt, export et récupération

Dans l'Activity ouverte volontairement : Arrêter, attendre la fin de tout Binder déjà engagé, vérifier le motif, puis Exporter. Désactive ensuite le service 004 dans Android. L'arrêt ne supprime pas les captures ; il ne réarme jamais automatiquement.

Récupération des exports locaux après le futur GO :

```powershell
Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass
Set-Location 'C:\Users\DENIS\Documents\Kiro\platform-tools'
$serialTV = Read-Host 'Identifiant ADB exact de la Sony'
$destinationProbe004 = Join-Path $PWD ('probe004-' + (Get-Date -Format 'yyyyMMdd-HHmmss'))
New-Item -ItemType Directory -Path $destinationProbe004 | Out-Null
.\adb.exe -s $serialTV pull '/sdcard/Android/data/com.scenevibe.accessibilityprobe004/files/Documents/.' $destinationProbe004
Get-ChildItem $destinationProbe004 -File | Get-FileHash -Algorithm SHA256
```

Ne pas nettoyer logcat, caches, stockage du Companion ou fichiers Probe003. Un journal sans SESSION_END reste INCOMPLETE. Rétention interne 24 h seulement lorsque l'app s'exécute ; conserver les exports utiles avec empreintes puis supprimer volontairement les captures privées selon leur finalité.

## Désinstaller 004, après récupération et accord

Désactive d'abord le service dans les réglages Android ; récupère les journaux voulus. Cette commande supprime uniquement l'application 004 et ses données, sur décision volontaire :

```powershell
Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass
Set-Location 'C:\Users\DENIS\Documents\Kiro\platform-tools'
$serialTV = Read-Host 'Identifiant ADB exact de la Sony'
.\adb.exe -s $serialTV uninstall com.scenevibe.accessibilityprobe004
```

Probe003, SceneVibe v27.1 et Prime restent disponibles. Ne pas activer automatiquement une ancienne campagne en compensation. STOP si lecture perturbée, prise de focus inattendue, collecte privée, perte des bornes ou signature/package non conformes.
