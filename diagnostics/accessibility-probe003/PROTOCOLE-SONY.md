# Probe 003 — protocole propriétaire Sony, NON EXÉCUTÉ

Ce protocole n'autorise aucune intervention. Attendre un **GO physique distinct**
de Pierre. L'APK est une sonde privée debuggable, package indépendant, pas une
mise à jour du Companion. Aucune instruction ci-dessous ne doit être exécutée
par Work pendant la qualification logicielle.

## 1. Préparer et installer, après ce GO seulement

Placer l'APK et `adb.exe` dans le même dossier Windows. Ouvrir PowerShell 5.1
ou 7 dans ce dossier. Vérifier le SHA-256 du rapport avant installation.

```powershell
Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass

Get-FileHash ".\scenevibe-accessibility-probe003-private-diagnostic.apk" -Algorithm SHA256
.\adb.exe devices -l
$ProbeSerial = "IDENTIFIANT_ADB_EFFECTIVEMENT_DETECTE"
.\adb.exe -s $ProbeSerial install ".\scenevibe-accessibility-probe003-private-diagnostic.apk"
.\adb.exe -s $ProbeSerial shell dumpsys package com.scenevibe.accessibilityprobe003 |
  Select-String "versionCode|versionName"
```

Attendu : versionCode 1, versionName 0.0.3-private-diagnostic. Ne jamais remplacer
ce package par celui du Companion. Si une ancienne sonde existe, arrêter et
exporter ses journaux avant toute décision de remplacement. La signature privée
peut changer d'un build à l'autre : ne pas désinstaller automatiquement ni tenter
de contourner un refus de signature.

## 2. Activer et contrôler la sonde, 60–90 secondes

Ouvrir manuellement « SceneVibe Probe 003 — privé » sur la TV, avant toute lecture
à observer. Lire l'explication des données. Avec le bouton de réglages, activer
volontairement ce seul service dans les réglages Android, sans activer TalkBack.
Revenir à la sonde et Actualiser : CONNECTED_UNARMED attendu.

Choisir OWN_CONTROL, Armer, appuyer sur « Contrôle positif » deux fois à intervalles
visibles. Les mots « Publicité/Programme » sont **synthétiques** dans notre propre
Activity, jamais une publicité Netflix. Arrêter puis Exporter. Un succès confirme
le service/les projections, pas une capacité sur la cible. Si le contrôle échoue,
FAIL instrumentation et arrêt : ne pas conclure sur Netflix.

## 3. Netflix sans publicité, deux minutes maximum

Campagne distincte, profil NETFLIX, armement volontaire puis sortie manuelle de la
sonde vers la lecture. Aucun écran ni touche ne doit être déclenché par la sonde.
Noter lecture normale, action habituelle timeline+pause, reprise et disparition
des commandes. Le bouton connu **affiche la timeline et met en pause** : causes
confondues. Seulement si les gestes ordinaires le permettent, laisser disparaître
la timeline en restant PAUSED puis la réafficher sans changer cet état. Sinon
conserver CONFOUNDED_UI_AND_PAUSE ; ne pas inventer une séparation.

Arrêter/exporter depuis la sonde après cette fenêtre. Toute perte de focus ou
commande de lecteur non demandée par Pierre = STOP. Aucun gain après ce passage
ne justifie des variantes interminables de drapeaux.

## 4. Une occasion publicitaire Netflix, cinq minutes maximum

Seulement si le contrôle positif fonctionne, sans perturbation, et si la collecte
est saine : un signal nouveau est candidat, ou l'on teste explicitement le cas
encore inconnu d'un indicateur publié uniquement pendant la publicité. Campagne
neuve, ne pas réutiliser celle du contrôle synthétique.

Utiliser manuellement une seule fois le SEEK déjà connu. Noter avant, AD_VISIBLE,
pendant, CONTENT_VISIBLE et retour au programme, avec l'imprécision humaine.
Les boutons existants du téléphone v27.1 peuvent servir de labels si disponibles,
sans modifier le Companion ; sinon noter les heures. Rien n'est envoyé à un garde.
Conserver 30–60 s après retour si possible, sans dépasser les limites. Les positions
MEDIA ne sont utilisables que si les journaux existants les fournissent.

Pas de publicité = INCONCLUSIVE publicitaire, arrêt au plafond. Source nulle,
refresh échoué, journal tronqué ou BYTE_LIMIT = qualité insuffisante pour affirmer
l'absence d'un signal. Le plafond peut fermer le journal avant dix minutes.

## 5. Export local et empreintes

Après Arrêter puis Exporter dans la sonde, récupérer seulement son dossier local :

```powershell
Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass

$ProbeSerial = "IDENTIFIANT_ADB_EFFECTIVEMENT_DETECTE"
.\adb.exe -s $ProbeSerial pull "/sdcard/Android/data/com.scenevibe.accessibilityprobe003/files/Documents" ".\probe003-capture"
$ProbeHashes = Get-ChildItem ".\probe003-capture" -File -Recurse |
  ForEach-Object {
    $ProbeHash = Get-FileHash $_.FullName -Algorithm SHA256
    "{0}  {1}" -f $ProbeHash.Hash.ToLowerInvariant(), $_.Name
  }
[System.IO.File]::WriteAllLines(
  (Join-Path (Get-Location) "probe003-SHA256SUMS.txt"),
  [string[]]$ProbeHashes,
  (New-Object System.Text.UTF8Encoding($false))
)
```

Comparer le SHA-256 affiché par la sonde à celui du fichier récupéré. Conserver
les originaux et leur provenance. Sans SESSION_END, capture INCOMPLETE. Les
annotations humaines et l'uptime d'Android n'ont pas la même précision ni toujours
la même base de temps : aucune latence physique exacte n'est déduite de leur écart.

## 6. Désactiver et désinstaller

Arrêter, exporter et vérifier les empreintes. Dans Android, désactiver manuellement
le service. « Supprimer » dans la sonde retire ses propres journaux seulement.
Une copie déjà récupérée sur le PC doit être supprimée séparément après analyse.
Après décision propriétaire de retirer cette sonde, uniquement :

```powershell
Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass

$ProbeSerial = "IDENTIFIANT_ADB_EFFECTIVEMENT_DETECTE"
.\adb.exe -s $ProbeSerial uninstall com.scenevibe.accessibilityprobe003
```

La désinstallation efface les fichiers privés de la sonde. Aucun effacement du
Companion, de cache tiers, redémarrage TV ou `logcat -c` n'est prévu.

## Verdicts et Disney+

- PASS SOFTWARE : compilation/tests/isolation démontrés, résultat du rapport logiciel.
- PASS INSTRUMENTAL : nouvelle donnée exploitable réellement observée sur la cible,
  avec source claire ; le contrôle synthétique seul ne suffit pas.
- Candidat publicitaire : signal distinct pendant une publicité physiquement vue,
  disparition cohérente au retour, contrôles négatifs sans même signature. Cela
  ne constitue pas encore une détection fiable répétée.
- FAIL borné : service sain, états testés, aucune information utile discriminante.
- INCONCLUSIVE : pas de publicité, données absentes/ambiguës ou limites atteintes.

Disney+ seulement après une hypothèse précise et dans une campagne entièrement
séparée. Pas de modification d'abonnement, d'attente longue ou de publicité forcée.
La qualification répétée et toute protection des commentaires nécessitent un
autre mandat ; le présent logiciel ne les met pas en œuvre.
