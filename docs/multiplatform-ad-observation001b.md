# SceneVibe v27.1 — MultiPlatform Observation 001-B

Cette évolution part exclusivement de `003fdeadb2e57e1cd69aee0db132cd95656afce2` (v27, PR #34). Elle est proposée dans une nouvelle branche et une PR dépendante en brouillon. Elle observe Netflix et Disney+ ; elle ne protège pas automatiquement leurs publicités. Aucun nouveau résultat physique n'est revendiqué.

## Architecture et frontières

Le sampler `MediaSessionProbe` existant, son sélectionneur et son estimation de position restent inchangés. Le canal diagnostic `MultiPlatformAdObservationProbe` enrichit uniquement `MultiPlatformObservationLog`. `MultiPlatformTransitionFacts` compare des scalaires déjà lus, sans second lecteur, horloge, service permanent ou pouvoir sur TEXT/AUDIO. Le garde Prime, le calendrier, le port AUDIO, le cache et l'admission ne sont pas modifiés.

L'observation fonctionne sans track, dans l'OverlayService existant, ou se rattache au sampler de la track. L'ancien sampler sans track est détaché avant rattachement. OFF, fermeture du service et expiration des commandes locales détachent les callbacks. Le diagnostic expire dans la seconde suivant la limite existante de 120 minutes. Aucun opt-in d'observation ne survit au redémarrage.

Une seule plateforme/campagne est active. Le téléphone et le propriétaire de commandes refusent maintenant un changement de plateforme avant OFF (`CAMPAIGN_STOP_REQUIRED`). Répéter ON sur la même plateforme ne recrée pas une campagne. Les quatre annotations historiques restent séparées, avec campagne, séquence, temps monotone, heure murale et nombre de sessions ; elles ne sont jamais fournies au garde.

## Modifications

- Boutons explicites : « Je vois une publicité commencer », « Le film ou l'épisode reprend », « Je viens d'avancer ou de reculer dans la vidéo », « Je viens de mettre la vidéo en pause ». Aide visible et confirmation d'enregistrement ; boutons inactifs hors campagne.
- Événements observés : `PLAYBACK_CALLBACK`, `PUBLIC_TRANSITION`, `SESSION_DISAPPEARED`, `SESSION_REPLACED`, ainsi que les événements v27 conservés. Les changements restent des faits (`PLAYBACK_RESUMED`, `POSITION_RESET`, etc.), jamais `AD_START`/`AD_END`.
- Champs de comparaison additifs, sous `sample.comparison` : `deltaPositionMs`, `deltaElapsedMs`, `expectedPositionDeltaMs`, `positionResidualMs`, `comparable`, `reason`. Les anciennes projections restent présentes.
- `markerStatus` distingue UNAVAILABLE (métadonnées absentes/illisibles), ABSENT (clé absente), CONTENT (Long zéro), AD (Long non nul), INVALID (type incorrect). Le champ historique `marker` reste inchangé ; une absence de clé n'est jamais une preuve négative.
- Le téléphone affiche la campagne choisie, l'application réellement observée ou NONE, le nombre de sessions et leur ambiguïté, la fraîcheur, le type de marqueur, l'identité compatible/non évaluée, le nombre d'annotations et le dernier changement. Aucun titre ou identifiant n'est affiché.
- L'analyse hors ligne conserve les sorties agrégées v27 et ajoute des campagnes indépendantes, chronologies, fenêtres ±30 s, intervalles annotés, contrôles négatifs, motifs de non-comparabilité et Markdown.

### Calculs de diagnostic

La position comparée est **l'estimation déjà fournie par le sampler historique**, pas une nouvelle horloge. Les ancres brutes sont conservées séparément. Pour deux captures d'une même session et d'une même identité, dans un état stable PLAYING :

`deltaPositionMs = current.estimatedMs - previous.estimatedMs`

`deltaElapsedMs = current.capturedAtMs - previous.capturedAtMs`

`expectedPositionDeltaMs = deltaElapsedMs × previous.speed`

`positionResidualMs = deltaPositionMs - expectedPositionDeltaMs`

PAUSED/STOPPED stables ont une progression attendue nulle. BUFFERING, changement d'état/vitesse/identité/durée, position manquante, temps non croissant ou écart >5 000 ms rendent la comparaison indisponible. Capture >2 500 ms ou ancre >120 000 ms : non comparable. Les valeurs absentes sont null, jamais des zéros inventés. Un résidu absolu >5 000 ms est une discontinuité **numérique**, sans signification publicitaire établie. Un passage d'une position brute >10 000 ms à ≤1 000 ms est un reset observé, même lors d'un changement d'état.

Les seuils sont constants, documentés et testés ; ils n'affectent aucun calendrier. Le calcul en ligne compare les polls existants ; le calcul hors ligne compare les échantillons effectivement émis. Ces cadences diffèrent : les deux résultats ne doivent pas être présentés comme des mesures identiques. Échantillons stables à environ 1 Hz ; changements significatifs et callbacks publics distincts peuvent apparaître entre eux. Aucun polling supplémentaire.

## Sources publiques étudiées le 30 octobre 2026

1. [MediaMetadataCompat](https://developer.android.com/reference/android/support/v4/media/MediaMetadataCompat) : convention ADVERTISEMENT et getter par défaut à zéro. Le lecteur typé existant vérifie la présence et le type avant lecture ; il préserve donc la distinction ABSENT/CONTENT.
2. [Media3 Player](https://developer.android.com/reference/androidx/media3/common/Player) : `isPlayingAd`, indices, positions et `COMMAND_GET_CURRENT_MEDIA_ITEM` doivent être interprétés selon les commandes réellement disponibles.
3. [Media3 MediaController](https://developer.android.com/reference/androidx/media3/session/MediaController) : compatibilité avec les sessions historiques, indices publicitaires non pris en charge sur ce chemin.
4. [Source officielle MediaControllerImplLegacy](https://github.com/androidx/media/blob/release/libraries/session/src/main/java/androidx/media3/session/MediaControllerImplLegacy.java) et [LegacyConversions](https://github.com/androidx/media/blob/release/libraries/session/src/main/java/androidx/media3/session/LegacyConversions.java) : le bridge historique reconstruit `isPlayingAd` à partir du même ADVERTISEMENT ; `getContentPosition()` réutilise la position courante ; les deux indices publics renvoient INDEX_UNSET. Un false de compatibilité ne prouve pas l'absence de publicité.
5. [Morphe — Netflix ATV System Design](https://github.com/ajstrick81/morphe-androidtv-patches/blob/main/docs/NETFLIX_ATV_SYSTEM_DESIGN.md) : hypothèses documentaires sur `adverts.adBreaks`, `ads[]`, `getAdMetadata`, `adBreakHydrator`, `StatefulAdBreak`, SSAI et publicité de pause. Ce sont des structures internes, pas des contrats MediaSession publics. Une coupure planifiée ne prouve pas une annonce effectivement diffusée.
6. [Morphe DisneyPatch.kt](https://github.com/ajstrick81/morphe-androidtv-patches/blob/main/patches/src/main/kotlin/ajstrick81/morphe/patches/disney/DisneyPatch.kt) et [Fingerprints.kt](https://github.com/ajstrick81/morphe-androidtv-patches/blob/main/patches/src/main/kotlin/ajstrick81/morphe/patches/disney/Fingerprints.kt) : les containers Insertion legacy DSS et `com.disney.dmp` décrits par le projet exposent en interne points/ranges d'insertion SGAI/SSAI ; la publicité de pause relève d'un autre mécanisme. Rien n'établit leur export public vers un Companion tiers.

Aucun code de suppression n'est repris. Aucun APK officiel n'a été ouvert/modifié, aucune interception ou capture protégée n'a été réalisée.

### Faisabilité Media3

Ajouter un MediaController Media3 au-dessus de ces mêmes sessions historiques ne crée pas de nouvelles données publicitaires : la conversion officielle réutilise le marqueur déjà collecté et perd la distinction absence/zéro dans son booléen. Cela justifie de ne pas ajouter une dépendance et une connexion redondantes. Le mapping numérique/comptable de cette limite est documentaire, pas un essai de connexion à Netflix/Disney+.

Une session Media3 native pourrait théoriquement négocier des informations plus riches. Sa présence, ses commandes accordées et la fidélité des informations publicitaires ne sont **pas établies** par ces captures Android historiques. Aucune connexion native ou requête de service supplémentaire n'est intégrée. Il serait incorrect de déclarer toute exposition future impossible ; la piste reste non démontrée. Aucun false synthétique, INDEX_UNSET ou défaut de bibliothèque n'est utilisé comme preuve négative.

## Captures initiales réellement analysées

Le journal brut et le JSONL fournis sont lus intégralement, sans modification. Les trois empreintes correspondent au manifeste original (suffixe `(1)` des copies ignoré pour la correspondance des noms). Le manifeste référence aussi un `adb-errors.txt` vide ; ce fichier n'était pas joint et n'est pas inventé.

| Campagne | Événements uniques | PUBLIC_SAMPLE | Sessions | Marqueurs | Annotations | Clôture |
|---|---:|---:|---|---|---|---|
| Netflix 95069341 | 125 | 114 | 1, 2, 3 | 114 ABSENT | AD_VISIBLE, CONTENT_VISIBLE | ENABLED + DISABLED |
| Disney+ 95234607 | 144 | 137 | 1, 2 | 137 ABSENT | Aucune | Pas de DISABLED dans cette capture |

Les 125 événements Netflix recopiés dans le JSONL et le brut sont dédupliqués ; toutes leurs références source:ligne sont conservées. Aucun événement Disney+ n'entre dans une conclusion Netflix. Les nouveaux champs n'existaient pas dans v27 : l'analyse recalcule hors ligne seulement ce que les scalaires enregistrés permettent.

AD_VISIBLE à 95171367 ms, CONTENT_VISIBLE à 95201485 ms : **30 118 ms entre les annotations**, pas une durée physique indépendante. PLAYING→PAUSED à 95160660 ms, puis retour PLAYING/reset à 95169706 ms, précèdent l'annotation. Des SEEK ont été faits sans annotation : aucune signature publicitaire discriminante n'est validée. La campagne Disney+ n'a pas comporté de publicité observée par Pierre ; ses variations ne peuvent servir de vérité terrain publicitaire.

Résultat pour les deux plateformes : **INSUFFICIENT_EVIDENCE**. Les rapports détaillés livrés distinguent OBSERVATIONS, CORRÉLATIONS, HYPOTHÈSES, LIMITES et CONCLUSION. Ni précision ni sensibilité statistiques ne sont calculées.

## Confidentialité et intégrité

Seuls nombres finis, booléens, codes fermés et inventaires de clés publiques allowlistées sont exportés. Titres, identifiants média, tokens, noms/valeurs arbitraires d'extras et noms d'événements privés sont exclus. Les originaux restent disponibles en lecture seule ; leurs SHA-256 et les transformations éventuelles des copies livrées sont recensés dans le manifeste. UTF-8 sans BOM pour les nouveaux exports, lecture UTF-8/UTF-16 avec BOM conservée. Aucune permission, dépendance ou autorité nouvelle.

## Matrice de qualification logicielle

Les résultats réellement exécutés et leurs liens CI sont recensés dans la PR et le rapport final de livraison. Les chiffres de la v27 restent des références historiques, jamais recopiés comme résultats v27.1.

| Famille | Vérification reproductible |
|---|---|
| Annotations | Navigateur Chromium sur les assets exacts + HTTP réel avec owner de banc ; quatre codes inchangés, libellés explicites, OFF, verrouillage plateforme, confirmation visible |
| Arithmétique | JVM : jitter, vitesse ×2, PAUSE/BUFFERING/reprise, SEEK avant/arrière, reset, identité/durée/session, données absentes/anciennes |
| Cycle de vie réel Android | API 31 Android TV, publishers possédés par le banc ; callbacks, types, changement refusé avant OFF, service sans track, conservation/reprise et redémarrages |
| Capture/analyse | Python, dix scénarios synthétiques explicitement identifiés ; UTF-8/UTF-16, chevauchements, conflits de séquence, journaux partiels, fenêtres, confidentialité |
| Windows | Script exact exécuté sous Windows PowerShell 5 et 7, ADB factice possédé, chemins avec espaces, capture non destructive, sélection de campagne automatique et explicite |
| Non-régression | Suite JVM actuelle complète et contrats actuels, tests de vues, frontières Cloud locales, M5 différentiel et durabilité existants |

Les anciens contrôles Python exact-tree M4/M5 échouent déjà sur la source v27 (118 cas explorés : 28 échecs, 12 erreurs) : inventaires figés d'autres jalons. Ils sont conservés sans affaiblissement ; le workflow actuel les distingue déjà des 15 contrats de sécurité applicables. La suite JVM complète, les contrats actuels et les bancs M5 existants restent les vérifications pertinentes. Ce constat initial n'est pas une régression introduite par 001-B.

Les native tests et le navigateur utilisent des publishers/owners simulés, jamais Netflix/Disney officiels ou une Sony physique. Ils ne confirment aucune signature publicitaire réelle.

## APK candidat

- Nom : `scenevibe-v27-1-multiplatform-observation001b-stable.apk`.
- Package : `com.scenevibe.tvcompanionpoc` ; versionCode **28**.
- versionName : `0.8.2-tv-release-hardening-multiplatform-ad-observation001b`.
- Build propriétaire non debuggable, certificat stable M5 attendu : `f908bf564ed97ba67e02b1ebc89eb0239cf980752587f55eb9ec0419791a2e9c`.
- SHA-256, commit exact et preuves de signatures applicables : rapport final et artefacts CI après vérification des octets produits. Aucun octet installé sur Sony n'est vérifié ici.

## Protocoles Sony ultérieurs — après nouvelle autorisation propriétaire

Aucune commande de ce protocole n'est exécutée par Work sur la Sony. Aucune installation ou publication n'est autorisée ici. Mise à jour prévue sans effacement, après GO, sur la v27 ; ne pas utiliser `uninstall`, `pm clear`, `logcat -c` ou reset.

Première commande dans Windows PowerShell 5.1 comme dans PowerShell 7 :

```powershell
Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass
```

Après GO d'installation explicite uniquement, dans le dossier contenant platform-tools et l'APK :

```powershell
.\adb.exe devices
$Sony = "IDENTIFIANT_REEL_DEJA_CONNECTE"
.\adb.exe -s $Sony install -r .\scenevibe-v27-1-multiplatform-observation001b-stable.apk
.\adb.exe -s $Sony shell dumpsys package com.scenevibe.tvcompanionpoc |
  Select-String -Pattern "versionCode=|versionName="
```

Pour chaque terminal de collecte, initialiser d'abord la politique de processus ci-dessus. Place le script `Collect-SceneVibeMultiPlatform.ps1` près de `adb.exe` (chemins avec espaces acceptés). Dans le navigateur téléphone appairé, sélectionner la plateforme et activer l'observation. La collecte ne donne aucune commande de lecture :

```powershell
.\Collect-SceneVibeMultiPlatform.ps1 `
  -Platform NETFLIX `
  -DeviceSerial $Sony
```

1. Démarrer avant les événements et conserver environ 30 secondes de contexte.
2. Netflix : regarder normalement ; annoter juste après chaque publicité réelle puis reprise. Hors publicité, faire un SEEK volontaire, l'annoter ; faire une PAUSE volontaire, l'annoter.
3. Arrêter l'observation sur le téléphone (OFF), puis la capture avec Ctrl+C après collecte du DISABLED.
4. Conserver `.log`, `.jsonl`, contexte, erreurs ADB et manifeste. Le brut peut contenir d'anciennes campagnes. Le JSONL choisit la dernière campagne de la plateforme rencontrée ; le contexte inscrit son identifiant. Pour un choix explicite, ajouter `-CampaignStartedMs IDENTIFIANT_MONOTONE_AFFICHE_DANS_LES_TRACES` (sans inventer de valeur).
5. Recommencer séparément pour Disney+, sans modifier l'abonnement ni forcer une publicité :

```powershell
.\Collect-SceneVibeMultiPlatform.ps1 `
  -Platform DISNEY_PLUS `
  -DeviceSerial $Sony
```

Même syntaxe sous PowerShell 5.1 et 7 ; pour un ADB ailleurs, conserver `-AdbPath "C:\chemin avec espaces\adb.exe"`. Sans publicité Disney+ observée : résultat expérimental insuffisant, pas échec logiciel. Les annotations possèdent un délai humain inconnu ; une seule publicité ne généralise pas une signature.

Analyse hors ligne, sans accès TV :

```powershell
python .\qualification\multiplatform001\analyze_capture.py `
  .\capture.log .\capture.jsonl `
  --output .\campagnes.json `
  --markdown .\campagnes.md
```

L'analyseur nécessite son fichier voisin `campaign_analysis.py`. Vérifier `OWNER_ANNOTATION` (codes fermés), `PUBLIC_SAMPLE`, `PUBLIC_TRANSITION`, `PLAYBACK_CALLBACK`, sélection/session et typed marker. Aucune transition diagnostique n'est une commande ni une protection automatique.

## Matrice de décision physique

| Critère | Netflix | Disney+ |
|---|---|---|
| Application officielle observée | Oui, capture propriétaire initiale | Oui, journal propriétaire initial |
| Session Android publique disponible | Oui | Oui |
| Marqueur AD standard publié dans ces captures | Aucun | Aucun |
| Publicité physique documentée | Oui, observation Pierre | Non |
| Transitions observables | Oui, ambiguës avec SEEK | Oui, non publicitaires établies |
| Signature publicitaire validée | Non | Non |
| Contrôles SEEK/PAUSE annotés exploitables | Non établis | Non établis |
| Informations Media3 supplémentaires | Bridge historique redondant ; natif non démontré | Idem |
| Nouvelle qualification physique v27.1 | À effectuer par Pierre après GO | À effectuer séparément après GO |
| Protection automatique autorisée | Non | Non |

Recommandation après PASS des vérifications logicielles et signature : **GO_PHYSICAL_QUALIFICATION** pour observation seule. La découverte d'un marqueur n'est pas une condition de PASS logiciel. Aucun PASS physique ne peut être déduit de la CI.
