# SCENEVIBE — AD-DETECTION 001 / corrigendum 001-A

## Résultat et périmètre

**PARTIAL : instrumentation et détecteur candidat en observation seule. La suspension/reprise publicitaire automatique sur Prime/Sony n'est pas démontrée.** Le contrôle manuel conserve sa valeur temporaire, sans clore cet objectif.

Référence effective : `3abc7520e40dadd119ff2dcf39ed8dc9babbc29b`, branche `work/scenevibe-ad-detection-001`. L'archive v24 `archive/scenevibe-v24-sony-qualified-20261028` et `main` ne sont pas modifiés. Même application `com.scenevibe.tvcompanionpoc`, candidat v25, même calendrier et même moteur MP3. Les observations physiques v24 du propriétaire sont acquises ; les essais ci-dessous ne les remplacent pas.

## A — Audit intégral du journal Sony

Source récupérée : `scenevibe-v24-publicite-20261028-163257.log`, **7 344 379 octets**, SHA-256 `154ccd3f11e8448c4d0bbb90fd439262391e071fcfb6311d16385bd1075ec806`, UTF-8 avec BOM. **60 316 lignes** examinées, 60 176 enregistrements reconnus, 2 386 lignes d'enregistrements identiques dédupliquées. Le buffer commence le 27 octobre : l'heure du nom de fichier n'est pas celle du début de tous ses messages.

L'analyse reproductible `qualification/ad-detection001/analyze_sony_log.py` lit le fichier entier. L'export `sony-observed-events.json` ne conserve que les scalaires SceneVibe d'une fenêtre bornée, les numéros de lignes source et les annotations physiques séparées. Les messages système/Prime, les textes libres et les valeurs privées sont exclus ; le journal original n'est pas publié dans GitHub.

| Heure locale du journal, 28 octobre | Preuve observée | Interprétation permise |
| --- | --- | --- |
| 16:33:02.619 | AUDIO3 dû à 900 000 ms, préparation demandée | Le calendrier SceneVibe ouvre sa propre fenêtre. |
| 16:33:02.663 | `AUDIO_PREPARED` | Préparation native ; pas encore une écoute. |
| **16:33:02.677** | `AUDIO_RESOURCE_STARTED positionMs=900123` et accusé natif AUDIO3 | Un démarrage natif, corroborant l'écoute pendant une publicité **déclarée par Pierre**. Les deux tags ne sont pas deux lectures. |
| 16:33:02.926 | `MEDIA_PLAYING`, 900 429 ms, AD-SYNC OFF | Une horloge MEDIA valide ne prouve pas que le film est affiché. |
| 16:33:05.520 | Arrêt de la fenêtre AUDIO | Pas un marqueur de fin publicitaire. |
| 16:33:31.474–.513 | PAUSED, 928 469 ms | Environ 29 s après le MP3 ; trop tard pour identifier son AD_START. Pause ordinaire également possible. |
| 16:33:31.764–.770 | STOPPED, 993 076 ms, horloge invalide | L'état Android est STOPPED ; le booléen de lecture AD-SYNC ne doit pas le rebaptiser pause publicitaire. |
| **16:33:32.030–.050** | PLAYING, 993 121 ms, AD-SYNC OFF, horloge valide | Une première récupération existe déjà ici, avant 16:33:41.599. Aucun AD_END démontré. |
| 16:33:38.254–16:33:41.599 | Nouvelle pause, STOPPED/horloge invalide, retour PLAYING à 988 353 ms | Variations compatibles avec navigation/reprise, sans sens publicitaire certain. |
| Avant environ 16:33:57 | Fin publicitaire déclarée moins d'une minute après le début de capture | Borne approximative du propriétaire, pas un horodatage Android exact. |
| 16:34:12.438 | AUDIO1/2/3 réarmés après recul confirmé | Préservation de la réécoute ; ce n'est pas une détection publicitaire. |

**AD_START réel : non identifié. AD_END réel : non identifié.** Aucun message exact AD_START/AD_END n'est présent dans ce journal. Les messages génériques AdvertisingIdClient/DynamiteModule ne prouvent pas une publicité du lecteur. La v24 ne collectait pas la présence du marqueur public décrit ci-dessous : son absence dans les anciens logs ne prouve pas son absence dans Prime.

## B — Comparaison des signaux et sources primaires

Sources consultées le 28 octobre 2026, sans copie de code de ces projets.

| Famille | Accessibilité Companion | Valeur discriminante et limite | Expérience livrée |
| --- | --- | --- | --- |
| MediaSession / PlaybackState : position, ancre, vitesse, état | Déjà lues par la sonde autorisée via l'accès MEDIA existant | Pauses, SEEK, changements de session produisent aussi des ruptures ; aucune preuve isolée d'AD_START/END | Scalaires et callbacks, sans commande de transport |
| Identité/métadonnées MEDIA | Déjà accessibles ; titres et ID restent en mémoire | Identité compatible ≠ film effectivement visible ; perte d'identité ≠ publicité | Booléen de compatibilité, changement d'identité, noms de clés standard et nombre d'autres clés ; aucune valeur éditoriale exportée |
| Métadonnée publique `android.media.metadata.ADVERTISEMENT` | Contrat AndroidX public ; publication effective par Prime Sony **non établie** | Clé présente : type long contractuel, 0 contenu / non-zéro publicité. Clé absente : getter retourne aussi 0, donc **UNCERTAIN**, jamais contenu prouvé. Fiabilité temporelle du publisher à corroborer | Présence vérifiée, lecture du long, callbacks ; candidat séparé |
| Extras de session et PlaybackState | Getters publics ; disponibilité variable | Pas de sémantique ad implicite pour une clé privée ou sa variation. Même clé standard dans un emplacement différent : inventaire seulement | Nombre de clés et catégorie de la seule clé standard ; aucun nom/objet privé sérialisé |
| `getSessionInfo()` | API 29+, information de création | Donnée statique, pas un flux de début/fin de pub | Inventaire une fois par session |
| Callbacks metadata/playback/extras/audio | API publique, prouvable sur une session propre | Indiquent une publication/changement ; pas sa cause publicitaire | Comptage et transitions de la clé standard |
| Événements personnalisés de session | Callback public, mais Android réserve leur traitement aux sessions possédées | Ne pas interpréter le protocole privé de Prime | Noms et payloads ignorés ; compteur uniquement ; test négatif `AD_START` synthétique |
| Route/audio PlaybackInfo | API publique | Route ou attribut audio non discriminants | Types/attributs numériques, pas de capture acoustique |
| Media3 Player : isPlayingAd / contentPosition | Disponibles au lecteur/contrôleur selon les commandes exposées | Ne donne pas accès par magie aux périodes internes de Prime. Le pont legacy lit la même clé ADVERTISEMENT et `getContentPosition()` y retourne `getCurrentPosition()` | Revue du code officiel, aucune dépendance/second lecteur ajouté |
| Morphe Android TV Patches | Description d'un moteur Prime/version précis et de périodes internes | Éclaire la distinction des temps ; ses patchs et son accès interne ne sont pas une API publique Companion | Étude seulement, aucune interception/modification Prime |
| Ad Break Mute | Compte à rebours DOM du lecteur web | Apparition/disparition utiles conceptuellement ; DOM absent de l'APK TV | Pas de code copié, pas de compatibilité TV déduite |
| iSponsorBlockTV | Communication propre à YouTube TV | Ce protocole n'est pas présumé disponible sur Prime | Comparaison seulement |
| SkipperKit / accessibilité | Projet indique TV non pris en charge | Aucun signal Sony démontré ; nouvelle permission sensible non justifiée ici | Aucune permission d'accessibilité ajoutée |
| Comskip | Fichiers vidéo enregistrés analysables | Ne fournit pas d'observation passive du lecteur Prime protégé | Aucune capture vidéo/audio protégée |

Références : [MediaMetadataCompat](https://developer.android.com/reference/android/support/v4/media/MediaMetadataCompat), [MediaConstants](https://developer.android.com/reference/androidx/media/utils/MediaConstants), [MediaController](https://developer.android.com/reference/android/media/session/MediaController), [Callback](https://developer.android.com/reference/android/media/session/MediaController.Callback), [PlaybackState](https://developer.android.com/reference/android/media/session/PlaybackState), [Player Media3](https://developer.android.com/reference/androidx/media3/common/Player), [AdPlaybackState](https://developer.android.com/reference/androidx/media3/common/AdPlaybackState), [pont legacy officiel](https://github.com/androidx/media/blob/release/libraries/session/src/main/java/androidx/media3/session/MediaControllerImplLegacy.java), [LegacyConversions](https://github.com/androidx/media/blob/release/libraries/session/src/main/java/androidx/media3/session/LegacyConversions.java), [Morphe](https://github.com/ajstrick81/morphe-androidtv-patches/blob/main/docs/PRIME_VIDEO_ATV_SYSTEM_DESIGN.md), [Ad Break Mute](https://github.com/lpawa/ad-break-mute/blob/main/extension/sites/primevideo/page.js), [iSponsorBlockTV](https://github.com/dmunozv04/iSponsorBlockTV), [SkipperKit](https://github.com/foodlbs/skipperkit), [Comskip](https://github.com/erikkaashoek/Comskip).

## C — Instrumentation expérimentale concrète

`AdObservationProbe` s'attache uniquement après l'action explicite **Observation publicitaire : ON**, sur la page téléphone déjà associée. La sonde MEDIA, la sélection, la cadence 250 ms et le calendrier existants sont conservés. Aucune capture supplémentaire n'est demandée au calendrier. Les lectures additionnelles sont bornées à une fois/seconde ; les callbacks mémorisent des compteurs et les transitions du marqueur. Au plus quatre sessions cibles sont retenues, une ambiguïté reste UNCERTAIN.

Le tag `SceneVibeAdProbe` distingue `PUBLIC_SAMPLE`, `METADATA_CALLBACK`, `CANDIDATE`, `OWNER_ANNOTATION`, `ENABLED` et `DISABLED`. Chaque message porte un temps monotone, une heure murale et une séquence. Les annotations « publicité visible », « film revenu », « déplacement normal », « pause normale » sont **exclues des entrées du détecteur**. Leur délai de réaction humaine doit être considéré lors de la corrélation.

Pas de nouveau serveur, lecteur, autorisation, accès Cloud, stockage de jeton ou collecte du flux protégé. Les callbacks et références sont libérés à OFF/arrêt de service. OFF est le défaut et restaure le chemin de restitution v24 ; l'état de la sonde n'est pas une préférence AD-SYNC et n'est pas restauré après arrêt. L'incertitude du détecteur n'efface aucun cache ni événement.

## D — Détecteur candidat isolé

`ad/AdEvidenceDetector` reçoit seulement la clé publique explicite et les scalaires déjà capturés. Il ne reçoit ni événement éditorial, ni position d'un MP3, ni bouton d'annotation physique.

- AD_START candidat : après du contenu explicitement confirmé, deux captures distinctes d'un flag non-zéro présent, dans une seule session cible, séparées d'au moins 100 ms et de moins de 2,5 s. Une sonde activée au milieu d'une publicité peut constater sa présence, sans inventer son début.
- AD_END candidat : après une publicité explicitement constatée dans la même session, deux flags zéro explicites, identité compatible et lecture/horloge cohérentes. Aucun AD_END initial, par disparition de clé, timeout ou remplacement de session ; un intervalle sans preuve rompt la continuité des frontières.
- Sans clé, session ambiguë ou preuve insuffisante : UNCERTAIN. MEDIA valide seul ne devient jamais CONTENT_CONFIRMED.
- Les états CONFIRMED désignent le **contrat public du publisher candidat**, pas une analyse d'image ou une validation physique Sony.

**Aucun branchement de ce candidat sur la suspension des commentaires** tant que la publication et la valeur discriminante du marqueur Prime ne sont pas démontrées. L'automatisme final reste à qualifier, puis à intégrer sur preuve. La simple commande manuelle n'est pas sa solution automatique.

## Qualification logicielle et livraisons

Les résultats exécutés, commit et empreinte APK seront consignés dans le bilan de livraison après la campagne de cette branche. Les tests moteur couvrent les événements ordinaires et les marqueurs explicites synthétiques. Les tests Android utilisent des MediaSession appartenant à SceneVibe, le véritable service/cache/MP3 et une Activity propre ; ils ne prouvent aucun comportement Prime/Sony. Les trois MP3 existants sont réutilisés, aucun audio créé.

## Protocole Sony ultérieur, après autorisation d'installation

1. Conserver l'APK v24 de référence. Installer le candidat M5 seulement après le GO physique ; retrouver le cache existant et activer la track par le parcours habituel, sans effacement ni retéléchargement imposé.
2. Ouvrir les commandes téléphone associées. Vérifier TEXT, AUDIO et la réécoute directe sur 01:30 avec la sonde OFF. Le film doit rester au lecteur.
3. Dans PowerShell, première commande : `Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass`. Puis lancer `scripts/Collect-SceneVibeAdDiagnostic.ps1 -DeviceSerial 'SERIAL_DEJA_CONNECTE'`. Aucune commande de modification Android n'est exécutée par ce script.
4. Activer « Observation publicitaire : ON » dans le détail d'investigation téléphone. Filmer/capturer le flux Prime n'est pas nécessaire ni demandé. Laisser au moins 30 s de lecture normale.
5. Faire des pauses et SEEK ordinaires avec la télécommande Prime ; annoter ensuite « pause » / « déplacement » sur le téléphone. Ces boutons n'envoient aucune commande au lecteur. Observer la stabilité des commentaires et l'absence d'AD_START/END sans preuve explicite.
6. À une vraie publicité, appuyer au plus vite sur « Je vois une publicité », puis sur « Je vois le film reprendre » au retour. Conserver au moins 30 s après le retour. Répéter sur plusieurs interruptions et noter séparément le délai humain approximatif.
7. Désactiver la sonde, vérifier que commandes téléphone, TEXT/AUDIO, AD-SYNC ON/OFF, SEEK direct et réécoute restent disponibles. Arrêter la capture par Ctrl+C.
8. Comparer chaque AD_START et AD_END candidat aux observations, mesurer avance/retard, clés absentes, faux positifs et changements de session. Un marqueur absent/constant ou un début tardif ne qualifie pas l'automatisme. Ne prononcer PASS automatique qu'après preuve discriminante des **deux** frontières et absence de faux positifs dans les contrôles ordinaires.

## Obstacles et recommandation

La mesure manquante est précise : Prime Android TV publie-t-il la clé standard, et ses transitions précèdent-elles le prochain commentaire puis identifient-elles correctement le retour au film ? Cette APK permet de répondre sans modifier Prime. Si la clé est absente/constante, les seuls clock/identity/callbacks déjà observés ne suffisent pas. Les inventaires montreront quelles familles publiques changent ; une nouvelle hypothèse devra être testée avec négatifs et observations physiques, sans rebaptiser une anomalie MEDIA « publicité ».

**Verdict AD-DETECTION 001 : PARTIAL.** Progrès concret par instrumentation désactivable et candidat testé ; objectif de suspension/reprise automatique non clos. Aucune installation Sony, fusion, mutation Cloud ou modification de l'archive v24 n'est effectuée.
