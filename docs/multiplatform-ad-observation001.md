# SCENEVIBE — Multi-platform AD-OBSERVATION 001

29 octobre 2026. Companion expérimental v27 : observation **Netflix / Disney+ uniquement**, sans nouvelle protection sur ces plateformes.

## Référence préservée

Références vérifiées avant écriture : PR #33 fusionnée ; `main` à `c8778c8789523920358397c980a2b5b0eab995de` ; archive `archive/scenevibe-v26-sony-qualified-20261029` à `82874ef3fa1fdbefe95a25824549a2c878900080`. Les deux commits portent le même arbre `9583a210132a6c3bb3f65baf3ebba5e4d8ab4245`. La branche `work/scenevibe-multiplatform-ad-observation-001` part de ce main vérifié et laisse ces références intactes.

Le propriétaire déclare la v26 Prime physiquement qualifiée le 29 octobre : protection pendant une publicité et reprise autonome, commentaire à 15:00 avec environ 0,16 s d'écart logiciel après SEEK/publicité. Ce sont des observations/propos du propriétaire ; les nouvelles simulations ne les remplacent pas. La coupure d'un MP3 déjà en cours à l'instant exact du début publicitaire reste reportée et n'est pas rouverte par cette mission.

## Modification minimale et séparation des autorités

La v27 garde le package `com.scenevibe.tvcompanionpoc`, incrémente le versionCode à 27 et conserve le dispositif de signature M5 existant. L'archive v26 reste la référence stable. Les vérifications effectives du nouvel APK, les empreintes et les résultats exécutés accompagnent le rapport de livraison.

Le `MediaSessionProbe` existant possède un canal diagnostic supplémentaire, distinct de l'observateur Prime. Chaque contrôleur et snapshot sont déjà lus par le même sampler. Le diagnostic ne modifie ni le prédicat de sélection, ni le listener du calendrier, ni l'horloge. Une exception de diagnostic reste contenue.

`MultiPlatformAdObservationProbe` réutilise les lecteurs typés et inventaires publics v26, avec une campagne explicite Netflix **ou** Disney+. Il n'a aucune référence au garde publicitaire, renderer, lecteur AUDIO, cache, préférence ou serveur Cloud. Il ne détient et n'appelle aucun contrôle de transport. `MultiPlatformObservationLog` n'accepte que des scalaires, types et vocabulaires fermés. Aucune transition PAUSED, BUFFERING, SEEK ou session perdue ne devient une preuve publicitaire.

Avec une track active, le canal utilise exactement son sampler. Sans track, il utilise la même classe de probe dans l'OverlayService déjà existant : aucune admission, aucun téléchargement, aucun second service. Lors d'une activation de track, la sonde idle est arrêtée **avant** le démarrage de la sonde multimédia, puis le diagnostic s'y rattache. Une erreur de rattachement ne peut pas faire échouer la track. L'ancien mode TEXT seul est explicitement refusé pour cette investigation afin de ne pas créer deux sondes concurrentes.

OFF, arrêt du service, désactivation ou expiration des commandes locales détachent les callbacks. L'observation n'est pas persistée et revient OFF au nouveau service/processus. Sa durée est bornée par les commandes locales existantes (120 min, arrêt de diagnostic dans la seconde suivant l'expiration). Un retour d'Activity avec service vivant ne change pas le réglage.

Une frontière explicite `AdProtectionPlatformScope` limite la protection au package Prime TV qualifié `com.amazon.amazonvideo.livingroom`. Netflix `com.netflix.ninja` et Disney+ `com.disney.disneyplus` en sont toujours exclus, même en debug. Le seul cas de qualification supplémentaire est le publisher appartenant au Companion, dans un build debug sans origine Cloud. Le contrôleur `AdProtectionController`, le calendrier et le lecteur AUDIO v26 restent inchangés.

## Informations exportées et confidentialité

Chaque événement JSON porte `platform`, le package public de campagne, `campaignStartedMs`, `sequence`, `elapsedMs`, `wallTimeMs`, `event` et une portée explicite. Les jetons réels de MediaSession restent uniquement en mémoire ; un ordinal par session remplace toute identité sérialisée.

| Famille | Informations retenues | Interprétation autorisée |
| --- | --- | --- |
| Sessions | Nombre API total, nombre cible, quatre sessions retenues maximum, troncature/ambiguïté explicite, sélection historique | Présence et provenance publiques ; aucun choix du lecteur modifié. |
| Marqueur | ABSENT / CONTENT (zéro explicite) / AD (non-zéro Long) / INVALID et type réel ABSENT/NULL/LONG/STRING/BOOLEAN/OTHER/UNREADABLE | Lecture de la clé publique, sans preuve physique automatique. ABSENT ne prouve pas le programme. |
| Métadonnées | Présence, noms standard allowlistés, nombre d'autres clés, identité présente/changée | Titres, identifiants, URI, images et noms inconnus ne sont jamais exportés. |
| Identité | MATCH / MISMATCH avec la track admise, ou NOT_EVALUATED sans track | Un programme Netflix/Disney+ peut naturellement ne pas correspondre à Columbo/Prime ; cela n'interdit pas le diagnostic. |
| Lecture | Tous les états Android documentés, position et estimation existante, vitesse, âge d'ancre, différence de position/temps et discontinuité | Indices exploratoires ; aucune publicité déduite d'un saut, d'une pause ou d'un buffering. |
| Autres familles | Compteurs callbacks, extras session/PlaybackState : nombre de clés et catégorie de la même clé standard ; événements personnalisés ignorés | Une clé dans extras ou un événement privé n'est pas promu en protocole publicitaire. |
| Humain | AD_VISIBLE, CONTENT_VISIBLE, NORMAL_SEEK, NORMAL_PAUSE | Événements distincts, jamais entrées d'un détecteur ou du garde Prime. |

Les changements significatifs sont journalisés immédiatement ; les captures stables sont bornées à une par seconde et par session. Les extras nécessitant un appel supplémentaire sont relus au maximum une fois par seconde. L'état téléphone montre la campagne demandée **et** la plateforme effectivement observée, zéro session ou ambiguïté, la fraîcheur et le marqueur/type. Une observation récente n'est pas un verdict de compatibilité.

Sources publiques consultées : [MediaSessionManager et accès autorisé aux sessions](https://developer.android.com/reference/android/media/session/MediaSessionManager), [MediaController.Callback](https://developer.android.com/reference/android/media/session/MediaController.Callback), [Netflix Android TV](https://play.google.com/store/apps/details?id=com.netflix.ninja), [Disney+](https://play.google.com/store/apps/details?id=com.disney.disneyplus). Ces fiches confirment les références publiques de package ; elles ne prouvent ni les packages effectivement présents ni les marqueurs publiés sur la Sony du propriétaire.

## Qualification logicielle et preuves

Le bilan final donne les nombres effectivement exécutés et les liens des runs. La suite conserve les tests v26 : 16 TEXT, 3 MP3, 19 événements, cache/processus, SEEK/réécoute, pause/reprise, sécurité, contrôleur publicitaire Prime et téléphone. Les nouveaux tests couvrent séparément les deux plateformes synthétiques, présence/absence/type erroné, pause/SEEK/buffering, sessions multiples/remplacées/perdues, OFF et annotations sans effet. La page exacte est testée sous Chromium avec le vrai serveur local et un propriétaire de commandes de banc.

Les tests instrumentés utilisent de vrais MediaSession/MediaController et le vrai service Android TV API 31, mais les publishers appartiennent au banc. Le seam est refusé par l'APK non debuggable ; ses sorties portent `OWN_FIXTURE_NOT_REAL_PLATFORM`. **Aucun test ne prouve que Netflix ou Disney+ expose effectivement un marqueur.** Les tests ne contactent ni Sony, ni fournisseurs Cloud, ni comptes streaming.

`qualification/multiplatform001/cases.json` contient seulement des fixtures synthétiques, exécutées par les tests JVM. L'analyse des journaux et le script PowerShell sont testés avec des sources UTF-8/UTF-16 et un exécutable ADB local factice, pas une télévision. Les chevauchements sont dédupliqués ; aucune annotation ni valeur intermédiaire n'est inventée.

## Campagnes physiques ultérieures — pas exécutées par Work

Préconditions : ton GO d'installation, une app officielle et un abonnement/session affichant réellement des publicités. Aucun achat, changement de compte ou abonnement n'est demandé. Une offre sans publicité ou un essai sans publicité donne **INSUFFICIENT_EVIDENCE**, et non une absence universelle de signal.

Place l'APK et `Collect-SceneVibeMultiPlatform.ps1` dans ton dossier `platform-tools`. Première commande PowerShell :

```powershell
Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass
.\adb.exe devices
Get-FileHash .\scenevibe-multiplatform-ad-observation001-stable.apk -Algorithm SHA256
$SceneVibeDevice = 'IDENTIFIANT_SONY_DEJA_CONNU'
# Uniquement apres ton GO physique, sans desinstallation ou effacement :
.\adb.exe -s $SceneVibeDevice install -r .\scenevibe-multiplatform-ad-observation001-stable.apk
```

Ouvre SceneVibe, autorise l'accès MEDIA déjà prévu si nécessaire, active le mode Développeur puis les commandes téléphone habituelles. Aucune nouvelle permission n'est requise. Une track active n'est pas nécessaire pour observer ; sa restitution reste gouvernée par ses contrôles existants. Une fois appairée, la page expose **Observation multi-plateforme — expérimental**. Le choix de plateforme est verrouillé pendant une campagne ; OFF puis sélection permet d'en commencer une autre sans mélanger les états.

### Campagne A — Netflix

```powershell
.\Collect-SceneVibeMultiPlatform.ps1 -Platform NETFLIX -DeviceSerial $SceneVibeDevice -AdbPath "$PWD\adb.exe"
```

1. Démarrer la capture avant le visionnage. Depuis le téléphone, choisir Netflix et activer l'observation. Vérifier le nombre de sessions et le statut effectif ; un zéro reste une observation utile, pas une preuve de compatibilité.
2. Ouvrir Netflix manuellement avec la télécommande. Observer le programme normalement, puis une publicité réelle si l'offre en propose. Annoter AD_VISIBLE au moment où tu la vois, CONTENT_VISIBLE au retour. Les annotations ne commandent jamais la lecture.
3. Hors publicité, effectuer une pause normale et un SEEK, puis annoter NORMAL_PAUSE/NORMAL_SEEK. Noter un buffering éventuel et un changement de programme sans forcer de problème réseau ou modifier le compte.
4. Conserver au moins trente secondes avant/après les frontières. Désactiver l'observation à la fin, attendre le DISABLED, puis Ctrl+C. Garder tous les fichiers produits et les empreintes.

### Campagne B — Disney+

Commencer une **nouvelle** capture et une nouvelle campagne téléphone :

```powershell
.\Collect-SceneVibeMultiPlatform.ps1 -Platform DISNEY_PLUS -DeviceSerial $SceneVibeDevice -AdbPath "$PWD\adb.exe"
```

Reprendre les quatre étapes ci-dessus dans l'app Disney+ officielle : programme, publicité réellement vue, retour, pause, SEEK, buffering/changement éventuels. Ne pas réutiliser la conclusion Netflix. Si aucune publicité n'est vue, inscrire la durée/conditions et garder INSUFFICIENT_EVIDENCE.

Le script appelle un chemin ADB explicite avec des arguments séparés, lit uniquement le tag SceneVibeMultiAd et ne fait aucun clear, install, reset, capture protégée ou transport. Les fichiers JSONL filtrent strictement la campagne choisie. Le `.log` source peut conserver les propres messages d'anciennes campagnes encore présents dans les buffers ; ils portent plateforme/campagne/séquence et doivent être séparés/dédupliqués lors de l'analyse. Les noms uniques empêchent l'écrasement de captures. Le numéro ADB est expurgé des messages d'erreur et n'est pas ajouté au contexte.

Pour l'analyse locale optionnelle, sans modifier les sources :

```powershell
python .\analyze_capture.py .\scenevibe-ad-observation\CAPTURE_NETFLIX.jsonl --output .\bilan-netflix.json
python .\analyze_capture.py .\scenevibe-ad-observation\CAPTURE_DISNEY.jsonl --output .\bilan-disney.json
```

L'analyse calcule les SHA-256 des octets sources, détecte l'encodage, sépare les plateformes, déduplique les chevauchements et conserve les annotations à part. Les transitions de marqueur listées ne sont ni AD_START physique ni AD_END physique. La conclusion reste réservée à l'analyse des futures observations Sony.

## Matrice physique à renseigner après tes essais

| Critère | Netflix | Disney+ |
| --- | --- | --- |
| Application officielle identifiée sur la Sony | À qualifier | À qualifier |
| Session Android exploitable | À qualifier | À qualifier |
| Marqueur publicitaire standard présent | À qualifier | À qualifier |
| Corrélation publicité visible / marqueur | À qualifier | À qualifier |
| Transition vers le programme | À qualifier | À qualifier |
| Signal alternatif potentiel | À qualifier | À qualifier |
| Faux positifs SEEK / PAUSE / BUFFERING | À qualifier | À qualifier |
| Fiabilité suffisante pour protection future | À décider | À décider |
| Conclusion actuelle | INSUFFICIENT_EVIDENCE | INSUFFICIENT_EVIDENCE |

Les conclusions futures possibles sont PUBLIC_AD_MARKER_CONFIRMED, ALTERNATIVE_SIGNAL_OBSERVED, NO_USABLE_PUBLIC_SIGNAL_OBSERVED ou INSUFFICIENT_EVIDENCE. Un résultat négatif reste borné aux conditions/durées réellement essayées. Aucun PASS physique Netflix/Disney+ ni développement de leur protection n'est prononcé ici.

**STOP avant installation Sony, fusion, mutation/déploiement Cloud et publication.**
