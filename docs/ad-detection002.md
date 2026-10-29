# SCENEVIBE — AD-DETECTION 002

29 octobre 2026. Protection automatique expérimentale des seuls commentaires SceneVibe.

## Sources et objectif

Départ : `78d8c1ee75df6574f8963c27f36eb4ffb8f53927`, HEAD v25 vérifié de `work/scenevibe-ad-detection-001`, PR #32 en brouillon. Nouvelle branche `work/scenevibe-ad-detection-002`. `main` et l'archive v24 ne sont pas modifiés.

Les captures Sony v25 du propriétaire montrent `ABSENT → AD → ABSENT`. La première commence avec une publicité déjà en cours ; elle ne date pas son début. La seconde a la sonde active avant le SEEK. Ces observations corrèlent un marqueur public à des publicités vues, **pas une protection automatique v26 déjà réussie physiquement**.

| Source originale | Octets / SHA-256 | Preuve |
| --- | --- | --- |
| 215220(1).log | 53 / `33cace4b70f55e9ba491d39999d14df60cff145dc550cc928de66a7146237863` | Un en-tête, aucun événement exploitable. |
| 215514(2).log | 69 312 / `b9ed928b5260725e95fc58fd8934e3465bab248bdb1556708d15a98ced2dfa6a` | 104 lignes, 74 entrées publiques, 3 annotations humaines séparées. |
| 222334(2).log | 155 802 / `cb30ffddd50333cfd4974ffb448f059a293662da6951bb838fca760eb7e7d94d` | 204 lignes, 173 entrées publiques, 4 annotations humaines séparées. |

Toutes les sources sont UTF-8 avec BOM et ont été lues intégralement. Aucun doublon de message sonde observé entre les trois fichiers. `qualification/ad-detection002/extract_sony.py` génère les fixtures et projections expurgées, avec numéros de lignes et empreintes originales. Aucun échantillon intermédiaire non journalisé n'est interpolé. L'elapsedMs exporté sert de temps d'observation de fixture ; le capturedAtMs interne n'était pas exporté et n'est pas inventé.

## Chronologie corroborée

| Capture / heure Sony | Fait observable | Limite |
| --- | --- | --- |
| 21:56:56.794 | Sonde activée ; première lecture AD à 21:56:57.013, présence confirmée à .277 | Publicité déjà en cours, pas d'AD_START datable. |
| 21:56:59.058 | Annotation AD_VISIBLE | Référence humaine, pas entrée du détecteur. |
| 21:57:04.070 | Callback ABSENT | Ouvre une vérification ; pas un permis de reprendre. |
| 21:57:06.642 | Annotation CONTENT_VISIBLE | Retard humain possible. |
| 22:23:46.716 | Annotation NORMAL_SEEK | Un SEEK n'est pas une publicité. |
| 22:23:48 / 22:23:56 | PAUSED puis BUFFERING | Non discriminants seuls. |
| **22:23:57.838** | Callback AD | Apparition observée après des lectures ABSENT, signal positif préventif. |
| 22:23:58.135 / 22:23:59.694 | v25 AD_CONFIRMED puis annotation AD_VISIBLE | 1,856 s entre callback et annotation ; pas une mesure du délai réel d'affichage. |
| **22:25:35.226** | Callback ABSENT | Fin du signal, pas encore fin opérationnelle de protection. |
| 22:25:36.375 | MEDIA PLAYING et horloge exploitable | À corroborer par plusieurs captures progressives et une identité compatible. |
| 22:25:37.517 | Annotation CONTENT_VISIBLE | 2,291 s après disparition ; comprend la réaction humaine. |

La position 2 420 980 ms figée pendant la publicité et le retour vers 3 509 390 ms ne permettent pas de reconstituer une durée publicitaire : le SEEK antérieur peut expliquer le saut. Aucun offset n'est fabriqué.

## Architecture et transitions

Un seul MediaSessionProbe, un seul calendrier M5, les mêmes SceneRenderer et AndroidLocalAudioPort. Le lecteur passif v25 sert deux commutateurs indépendants : observation et protection. Lorsque les deux sont OFF, aucun lecteur expérimental n'est attaché. L'observation v25 reste disponible, avec ses décisions candidates distinctes du nouveau garde.

`AdProtectionController` ne possède aucun lecteur, cache, préférence ou horloge éditoriale. Il retourne un garde supplémentaire, jamais un permis MEDIA. Les contrôles d'identité, de consentement, d'intégrité, de permissions et la suspension volontaire restent obligatoires.

| État | Entrée / sortie | Effets SceneVibe |
| --- | --- | --- |
| NORMAL | Pas de positif connu ; ABSENT est normal sans prouver le film | Calendrier historique soumis à ses contrôles habituels. |
| AD_PENDING | Premier callback ou poll positif ; deux polls indépendants confirment | Inhibition immédiate, hide TEXT et stop MP3 local, pas de tick éditorial. |
| AD_GUARD | Deux captures positives, au moins 100 ms séparées | Aucun TEXT/AUDIO nouveau ; aucun événement consommé sur la position publicitaire. |
| EXIT_VERIFY | Marqueur non publicitaire ; identité/horloge compatibles | Maintien de l'inhibition jusqu'à preuve progressive. |
| UNCERTAIN | Perte de session, marqueur mal formé après AD, clock/identité invalide, observations périmées | Après positif, maintien de protection ; jamais une fin de publicité inventée. |

AD_START désigne l'apparition **observée** d'un positif après une absence récente dans cette session surveillée, sans exiger un zéro explicite ni prétendre que l'absence prouvait le film. Si la sonde arrive au milieu d'une publicité, AD_PRESENT constate sa présence sans inventer son début. AD_END est une **réadmission opérationnelle** après publicité confirmée, pas une mesure de la dernière image publicitaire.

Sortie : session cible unique, marqueur ABSENT ou zéro typé, identité compatible, PLAYING, positions non négatives, vitesse positive finie. Au moins trois captures distinctes sur **750 ms**, progression réelle d'au moins **500 ms**, écart au mouvement publié toléré à **500 ms**, fraîcheur maximale **2 500 ms**. Un saut réinitialise la séquence ; une réapparition AD la ferme immédiatement. Une position PLAYING figée échoue au critère de progression. Les seuils sont regroupés dans Policy et testables.

Un timeout, une pause, une destruction ou un changement de session ne libèrent jamais à eux seuls le garde. Un retour vers une nouvelle session doit satisfaire à nouveau toute la preuve de réadmission. Un signal isolé peut se rétablir après preuve sans générer AD_END confirmé. Aucune identité MEDIA n'est contournée.

## Raccordement aux commentaires

Le callback positif ferme la barrière avant le prochain passage du calendrier. Le même garde est revérifié dans les preflight TEXT et juste avant l'effet natif AUDIO différé. Il arrête/hide uniquement SceneVibe, sans commande PLAY/PAUSE/SEEK/STOP, volume ou audio focus vers Prime.

Pendant la retenue, aucune position publicitaire n'est transmise au calendrier ou au contrôleur AD-SYNC historique. La consommation et le cache sont préservés. Au retour, le calendrier existant traite l'horloge retrouvée : fenêtres expirées ignorées, pas de rafale, pas de redémarrage automatique d'un MP3 consommé, preuve de recul et SEEK direct conservés. Le calendrier lui-même n'est pas refondu.

AD-SYNC ON/OFF, TEXT/AUDIO et observation ne peuvent pas effacer une publicité détectée. La pause volontaire demeure conservée jusqu'à sa reprise demandée. Protection OFF est une sortie explicite vers v25, **sans promesse de protection pendant la publicité** ; les autres sécurités MEDIA/consentement restent actives.

La protection est **OFF au démarrage d'un nouveau service/processus** et n'est pas persistée. Un aller-retour d'Activity avec service vivant conserve son état en mémoire. La page téléphone expose l'état, le motif et le commutateur indépendant. Une attente persistante est visible ; l'utilisateur peut explicitement désactiver la protection, en connaissance du retour à v25. Aucun bouton de remise à zéro ou autorisation de lecture n'est ajouté.

## Lecture typée du marqueur et permissions

Le getLong framework transforme aussi une mauvaise valeur en zéro. Le petit adaptateur public AndroidX `media:1.7.0` permet de lire le type réel de cette seule clé via MediaMetadataCompat/getBundle : absent, Long zéro, Long non-zéro, invalide. Aucun Media3 Player, API cachée, permission ou protocole Prime ajouté. Les autres clés/valeurs ne sont pas exportées.

Références publiques : [MediaMetadataCompat](https://developer.android.com/reference/android/support/v4/media/MediaMetadataCompat), [MediaController.Callback](https://developer.android.com/reference/android/media/session/MediaController.Callback). Les callbacks personnalisés non possédés restent ignorés. Aucun accès Accessibility, capture protégée, modification DRM ou insertion publicitaire SceneVibe.

## Qualification et limites

Les résultats complets sont fournis dans le bilan et les artefacts de cette branche. Les rejeux reprennent les seuls scalaires réels ; les annotations restent hors entrées du détecteur. Les tests natifs utilisent le vrai service, cache, lecteur MP3 et des MediaSession appartenant au banc, sans Prime ni Sony. Les tests contrôlent les pauses/SEEK normaux, faux signal isolé, métadonnées mal formées, AD pendant vérification de sortie, identité/horloge, TEXT actif, les trois MP3 actifs, un MP3 à venir, les bascules indépendantes et les redémarrages.

La réadmission reste une hypothèse opérationnelle à qualifier physiquement sur cette version Prime/Sony. Un publisher qui retirerait son marqueur pendant plus de 750 ms tout en publiant une fausse identité compatible et un temps progressif pourrait tromper les seules données publiques : aucune analyse d'images ne prétend l'exclure. Les seuils ne prouvent pas une compatibilité universelle avec tous les publishers.

**Aucun PASS physique Sony dans cette mission.** Arrêt avant installation, fusion ou mutation Cloud.

## Procédure Sony ultérieure, après GO physique

Première commande Windows :

```powershell
Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass
adb devices
```

1. Après autorisation seulement, mettre à jour avec l'APK M5 livré, sans désinstallation ni effacement : `adb -s SERIAL_DEJA_CONNECTE install -r .\scenevibe-ad-detection002-stable.apk`. Retrouver le cache 16 TEXT + 3 AUDIO et l'activer par le parcours habituel.
2. Depuis les commandes téléphone, vérifier la référence avec protection OFF ; contrôler TEXT/AUDIO, SEEK direct et réécoute. Garder le film dans Prime.
3. Lancer avant l'essai : `.\Collect-SceneVibeAdProtection.ps1 -DeviceSerial 'SERIAL_DEJA_CONNECTE'`. Ce script lit seulement les propres tags SceneVibe et garde le suivi jusqu'à Ctrl+C.
4. Activer protection ON ; l'observation passive peut rester OFF. Pour corréler une campagne, activer aussi observation ON, puis annoter AD_VISIBLE / CONTENT_VISIBLE sans que ces boutons déclenchent la protection.
5. À une publicité réelle, vérifier AD_PENDING puis AD_GUARD, TEXT masqué et MP3 SceneVibe arrêté ; le son Prime doit continuer. Aucune nouvelle demande TEXT/AUDIO ne doit passer le garde pendant ce segment.
6. Au retour, attendre EXIT_VERIFY puis NORMAL avec FILM_CLOCK_REQUALIFIED et AD_END. Vérifier la reprise du calendrier sans rafale ni reprise d'un ancien MP3. Tester aussi publicité après SEEK et réécoute directe sur 01:30, 05:00 et 15:00.
7. Répéter sans publicité : lecture continue, pause, BUFFERING ordinaire et SEEK avant/arrière. Vérifier l'absence de faux AD_GUARD. Tester AD-SYNC ON/OFF pendant la protection, puis la pause volontaire (qui doit rester jusqu'à Reprendre).
8. En cas d'attente prolongée, conserver le journal avant de choisir explicitement protection OFF. Ne pas interpréter ce retour manuel à v25 comme une réussite de reprise automatique. Arrêter la capture par Ctrl+C après au moins 30 s de retour au film.
