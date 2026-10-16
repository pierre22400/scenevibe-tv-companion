# SceneVibe OS — M6 : architecture Banner/WALL réconciliée après M5

Statut courant : réconciliation d'architecture et de provenance du 6 octobre 2026. M5 est CLOSED / MERGED / SONY PHYSICAL PASS / FINAL PR AUDIT PASS. Production : **SHADOW**. PR M6 #16 : **OPEN / DRAFT / unmerged**. Aucune Phase B ni implémentation Banner/WALL n'a commencé.

```text
M6 PHASE A ARCHITECTURE RECONCILED AGAINST MERGED M5
M6 PHASE B NOT STARTED — SEPARATE WORK ORDER REQUIRED
```

## 0. Autorité, base et portée

| Référence | Commit exact | Rôle |
| --- | --- | --- |
| TV `main`, merge M5 / `BASE_TV_M6` | `17cbe36ae99ac7f48aaf861e0d1feac702a9e521` | Référence obligatoire des fichiers réellement mergés |
| M5 software HEAD physiquement qualifié | `c9b0efd4acfaaae9ed7da13dcec505b2f653c548` | Identité du candidat Sony, conservée sans réécriture |
| M5 final audited HEAD / PR #15 mergée | `45c97782479f278632d5d19ec0723eb50af59e87` | Corrections de provenance uniquement après le candidat Sony ; arbre identique au merge M5 |
| Ancien HEAD M6 Phase A | `3f91d66ddfdcd95a4cd47a68ed0060e885ed716b` | Parent conservé ; documentation initialement basée sur M4 `67b81045258b1692073c6927b956db4899c6ad1a` |
| Cloud `main` / `BASE_CLOUD_M6` | `5011c91aac61a0cc6dcc74c256a15b7dee03d785` | Composition live toujours SHADOW et Video-only |

Branche : `work/scenevibe-os-m6-banner-wall-architecture-001`, PR #16 vers `main`. La réconciliation conserve l'ancien HEAD comme premier parent et intègre le merge M5 comme second parent, sans rebase ni force-push. Le HEAD publié, son arbre et les gates exécutés sont consignés dans le manifeste courant de PR et le rapport final externe : un commit ne peut contenir son propre SHA.

Le delta contre `BASE_TV_M6` est limité aux deux documents M6, aux admissions littérales de ces deux chemins dans quatre inventaires et à leur inverse fini dans `tests/m5_phase_d_provenance.py`. Aucun baseline, oracle, corpus, fixture, workflow, input build/signing ou fichier de production n'est modifié. Le rapport Phase A initial reste explicitement historique dans le rapport compagnon et dans Git au HEAD initial.

M4 conserve installation, capabilities, durable courant, validate/prepare/commit/readback/restore/ARM, révision et autorisation d'ACK. M5 conserve exclusivement les observations et sémantiques MEDIA, pause, seek et replay. M6 prévoit Banner et un calendrier WALL distinct. La trajectoire post-M6 reste : controlled-assets infrastructure prerequisite → Generic Multimedia Track Model → SceneVibe Public Track Platform → SceneVibe Studio → Creator / Identity / Publication Model. Aucun numéro post-M6 n'est figé. Les noms, routes et types M6 ci-dessous décrivent des travaux futurs ; aucune surface exécutable correspondante n'est créée ici.

## 1. WALL aujourd'hui : parser n'est pas exécuter — question 1

`OverlayManifest` et `OverlayManifestParser` savent représenter `clock.mode = wall`, `pauseBehavior = continue` et `product = banner`. Le parser applique une forme fermée, les identifiants uniques et des bornes ; il ne lit aucune horloge et ne définit aucun anchor. Le `startMs` d'une scène est actuellement borné à 43 200 000 ms : ce champ ne peut pas recevoir un epoch UTC ou un offset de plusieurs jours. La durée reste 250 à 3 600 000 ms.

`installation/ExecutionRequirements` contient déjà MEDIA/WALL. En revanche, `installation/TvCapabilities.current()` annonce MEDIA uniquement et `supportsWallClockExecution()` renvoie `false`. Les deux codecs autorisés sont `scenevibe.runtime-track-overlay.v1` et `scenevibe.runtime-track.v1`. Le premier exige un manifest MEDIA/FREEZE ; le second préserve les variantes MEDIA historiques. Les handlers Video ne rendent donc pas WALL exécutable. Le renderer ne constitue pas davantage une autorité temporelle.

Le contrôleur réellement mergé reçoit un `eventId` opaque et une génération gardée ; il ne possède aucune horloge. Ses méthodes sont compatibles avec la future remise Banner, sans constituer à elles seules un chemin WALL exécutable. La matrice de réconciliation du rapport compagnon identifie les extensions encore nécessaires.

La capability locale WALL ne pourra devenir vraie qu'après assemblage et qualification automatique du chemin complet : codec accepté, handler statique, validation croisée, même store/installer, restore, driver Android, scheduler WALL, binding owner/contrôleur, arrêt et neutralisation des callbacks, livraison et ACK exacts. Un parser, un modèle pur ou un test isolé ne suffit pas. Une composition de qualification peut alors annoncer sa capacité réellement testée ; la clôture Banner live exige ensuite Sony. Aucun flip, annonce publique ou déclaration Banner OS live n'est permis ici.

## 2. Modèle et responsabilités — question 2

Décision : calendrier WALL distinct. Au `BASE_TV_M6` mergé, `calendar/SceneEvent` borne `startMs` à douze heures, documente une position média et conserve une durée synthétique historique non positive ; sa durée positive est limitée à 60 000 ms. `MediaCalendar` porte `freezeOnPause`. Réutiliser ces valeurs pour un epoch ou allonger leurs bornes modifierait les garanties M5. Ni ces classes, ni `MediaObservation`, ni `MediaCalendarScheduler` ne changent pour faciliter Banner.

| Élément M6 conceptuel | Données et responsabilité | Exclusions |
| --- | --- | --- |
| `WallEvent` | `eventId` opaque, `startEpochMs`, `endEpochMs` ; fenêtre immuable | Texte, View, identité média, pause, seek |
| `WallCalendar` | 1 à 256 fenêtres uniques, ordre stable, horizon UTC borné | Révision, device, Cloud, fuseau à exécuter |
| Source de temps | Fournir un échantillon UTC civil et monotone | Modifier l'heure, contacter NTP |
| Anchor WALL | Couple epoch/elapsed et génération, uniquement en mémoire | État durable ou position player |
| Scheduler WALL pur | Déterminer le seul candidat actif, EXIT/DUE et prochaine frontière absolue | Android, timer, réseau, store, ACK, renderer |
| Driver Android | Échantillonner, détecter une discontinuité, gérer un seul réveil local en attente | Règles civiles, rendu direct |
| Owner commun | Sérialiser activation, retraite, gardes et remise au contrôleur | Nouvelle autorité temporelle |
| `SceneRuntimeController` | Index de scènes, génération, une présentation visible, preflight/hide/show | Horloge ou programmation civile |
| Renderer | Rendre la scène déjà sélectionnée dans le window owner existant | Décider DUE, déclencher une acquisition |

Les modèles et le scheduler WALL sont compilables en JDK pur. Leur entrée temporelle est explicite ; ils ne consultent pas une clock cachée. MEDIA reste passif, piloté par ses observations M5, sans heure civile, zone, DST ou règle Banner. WALL n'utilise ni MediaSession, PlaybackState, mediaId, Prime, position, commandes player, pause/resume ou seek.

Les primitives réellement partagées sont l'identifiant opaque, l'activation, la génération de présentation et le protocole de remise au contrôleur. Il n'est pas nécessaire de créer une superclass de calendriers ou un framework de schedulers.

## 3. Autorité temporelle et anchor — question 3

L'autorité est l'heure UTC de l'OS Android, échantillonnée par `System.currentTimeMillis()`. L'utilisateur ou l'OS peut la corriger ; SceneVibe n'ajoute ni NTP, ni horloge serveur faisant autorité, ni commande de réglage. La confiance dans une heure OS incorrecte est une limite explicite du produit.

Le driver lit `elapsedRealtime()` avant et après l'epoch et associe l'epoch au milieu de cet intervalle monotone. Un échantillon incohérent, un compteur décroissant ou un intervalle de lecture supérieur à 50 ms est refusé ; au plus deux tentatives immédiates sont permises avant échec fermé. Ces constantes proposées doivent être testées avec les sources simulées puis sur Android/Sony. L'anchor en mémoire comprend : epoch UTC, elapsed de référence, token d'activation et `wallGeneration`. La projection `anchorEpoch + elapsedDelta` sert uniquement à préparer une attente et à mesurer la dérive ; chaque callback relit l'heure civile avant toute sélection.

La documentation Android distingue bien les trois bases : l'heure civile peut sauter ; elapsed inclut le sommeil profond ; uptime, utilisé par Handler, l'exclut. Un timestamp elapsed ne doit donc jamais être passé comme timestamp uptime à `postAtTime`. Le driver peut calculer une durée relative puis employer `postDelayed`, en acceptant son retard pendant sommeil. Sources : [SystemClock](https://developer.android.com/reference/android/os/SystemClock), [Handler](https://developer.android.com/reference/android/os/Handler).

Une seule attente est programmée à la fois. Sa durée est le minimum entre la prochaine frontière WALL et 1 000 ms, avec calcul borné et sans boucle de délai nul. La vérification au plus chaque seconde lorsque le process/owner s'exécute borne la découverte d'une correction silencieuse ; ce n'est ni un réveil physique de la TV ni une promesse de temps réel Android. Une frontière à `now` est traitée dans l'évaluation courante, puis l'attente vise une frontière strictement future. Après fin de l'horizon, la vérification bornée permet aussi une réévaluation lors d'un retour d'heure ; elle ne crée aucune nouvelle occurrence.

Une dérive absolue d'au moins 1 000 ms entre échantillon civil et projection monotone invalide l'anchor. Un signal explicite de changement d'heure, changement de fuseau, reprise, réentrée du service ou réveil force aussi la réévaluation, même sous ce seuil. Toute évaluation emploie l'epoch frais, y compris pour une dérive plus petite. La correction significative annule la ticket timer, incrémente `wallGeneration`, prend un nouvel anchor et recalcule le candidat courant. La génération du manifest capturée à ARM reste fixe : corriger une clock ne recharge pas le manifest et ne retague pas une ancienne callback.

Au reboot ou à la recréation du process, aucun anchor/timer/génération antérieur n'est restauré. Le package durable est restauré, puis un nouvel échantillon et de nouvelles gardes sont créés. La clock continue pendant veille ; la présentation est suspendue et masquée lors d'un signal local de suspension, puis réévaluée au retour. Les broadcasts SCREEN_ON/OFF sont des indications d'interactivité, pas une preuve de l'état physique de la dalle, et se reçoivent via un receiver dynamique. Leur fiabilité Sony sera vérifiée ; la reprise et la prochaine callback relisent également l'heure. Source : [Intent](https://developer.android.com/reference/android/content/Intent).

Pas d'AlarmManager exact, nouvelle permission, wake lock, réveil de process ou Direct Boot. Si le process ne peut pas s'exécuter, il n'affiche rien pendant cette indisponibilité. Un service vivant mais retardé affiche seulement la fenêtre encore active au moment de sa reprise. Une clock impossible à lire ou hors domaine valide masque et disarme le chemin volatile, conserve le durable, produit un diagnostic borné et exige une restauration/réactivation réussie ; elle ne déclenche pas de nouveau commit ou ACK.

## 4. Où résoudre la programmation civile — question 4

| Architecture | Avantages | Coût/limite | Choix M6 |
| --- | --- | --- | --- |
| A : règles récurrentes et zones sur la TV | Récurrence offline sans horizon | Deux moteurs de règles et bases timezone, ambiguïtés DST et qualification plus large | Écartée |
| B : Cloud résout, TV reçoit des fenêtres UTC finies | Package immutable, restore déterministe, TV simple, même résultat offline | Horizon fini à renouveler explicitement | Retenue |
| C : auteur fournit seulement des fenêtres UTC | Exécution très simple | Ne suffit pas seul à « lundi–vendredi 09:00 Paris » | Entrée absolue possible dans B, pas un second moteur |

L'intention éditoriale appartient au producteur Banner Cloud. Le sous-ensemble minimal résout une heure locale, une sélection de jours de semaine, une zone IANA explicite, une durée écoulée et un horizon explicite. Il n'implémente pas RRULE, moteur généraliste de récurrence, fêtes, priorités ou éditeur complet. Exemple : lundi à vendredi, 09:00, `Europe/Paris`, cinq minutes, horizon choisi.

Le Cloud résout toutes les occurrences avant scellement. Heure locale inexistante au passage DST : occurrence omise. Heure répétée : première occurrence en UTC uniquement. La durée est une durée écoulée en millisecondes depuis le début retenu, pas une deuxième heure locale ambiguë. Une zone inconnue est refusée. Le résolveur et sa provenance timezone sont figés dans la publication ; une évolution de tzdata n'entraîne aucune recompilation d'une publication ready. Une règle qui produit zéro fenêtre est refusée dans le profil M6 minimal, dont le count existant doit rester positif.

Le terminal reçoit uniquement les fenêtres UTC déjà résolues, le manifest inline et une provenance bornée non décisionnelle. Il n'exécute ni zone ni DST. Changer le fuseau local de la TV ne déplace pas les fenêtres reçues. Un passage DST ne constitue pas en soi un saut d'epoch ; aucune double bannière n'est créée par l'heure locale répétée.

Bornes M6 proposées, plus étroites que les maxima génériques lorsqu'il le faut :

- Epoch entier entre 0 et `253402300799999`, avec additions vérifiées ; nombres JSON entiers sûrs.
- Horizon `[horizonStartEpochMs, horizonEndEpochMs)`, durée positive au plus `604800000` ms, soit sept fois 24 heures écoulées, sans assimilation à sept journées civiles DST.
- 1 à 256 fenêtres, entièrement incluses dans l'horizon ; chaque durée de 250 à 3 600 000 ms, chaque ID unique et sûr.
- Corps du profil au plus 1 048 576 octets UTF-8 ; dépassement ou 257e occurrence refusé, jamais tronqué silencieusement.

Offline, le calendrier stocké est autonome jusqu'à son horizon. Après l'horizon, il reste installé/ARMED mais invisible. Ni la TV ni GET ne prolongent sa programmation. Un nouveau planning, une nouvelle résolution ou un nouvel horizon produisent une nouvelle publication immutable puis un nouveau Send/révision. Redelivery et reboot utilisent les mêmes fenêtres. M6 ne crée ici ni automation ni job récurrent de publication.

## 5. Sémantique WALL déterministe — question 5

Une fenêtre est active exactement lorsque `startEpochMs <= now < endEpochMs`. Le scheduler examine au plus 256 événements à chaque évaluation et sélectionne au plus un candidat. Parmi plusieurs fenêtres actives : début le plus récent ; à début égal, ID opaque ASCII sûr le plus petit dans l'ordre lexicographique. L'ordre d'entrée ne départage jamais une égalité. Aucun champ priorité n'est ajouté.

| Situation | État / effet |
| --- | --- |
| Avant start | Aucun DUE pour cette fenêtre |
| Start exact | Candidat sélectionnable ; DUE si changement de candidat et présentation éligible |
| Dans la fenêtre | Un seul affichage, idempotent tant que le candidat reste le même |
| End exact / après end | Fenêtre inactive ; EXIT de la scène sélectionnée si elle était présentée |
| Réception ou reboot pendant une fenêtre | DUE de la fenêtre encore active après ARM, avec seulement son temps restant |
| Fenêtre entièrement passée pendant extinction | Aucun DUE, aucun replay ni batch des fenêtres ratées |
| A chevauchée par B plus récente | EXIT A puis DUE B ; si B finit alors qu'A reste active, DUE A à nouveau |
| Début de B exactement à fin de A | EXIT A avant DUE B sur le même owner |
| Même début | Règle d'ID ci-dessus ; les perdants ne produisent pas de DUE rétroactif |
| Nouvelle révision pendant affichage | Retraite immédiate de l'ancienne activation, puis évaluation du seul nouveau calendrier |
| Clear / arrêt service | Annulation, invalidation, retrait immédiat ; aucun nouveau DUE |

API conceptuelle : `DUE(eventId)` entre dans la fenêtre sélectionnée ; `EXIT(eventId, reason)` quitte la sélection. Les raisons bornées sont END, SUPERSEDED, CLOCK_REEVALUATED et CLEAR. END correspond à EXPIRE ; SUPERSEDED n'affirme pas que la fenêtre est déjà terminée. Le bridge peut transmettre tout EXIT à `SceneRuntimeController.onEventExpired(id, fixedGeneration)`, dont le code signifie bien « le scheduler demande de masquer ». Le contrôleur ne masque que cet ID/génération.

À chaque évaluation, il y a au plus deux effets synchrones : EXIT de l'ancien candidat, puis DUE du nouveau. Les expirations de fenêtres jamais sélectionnées ne produisent pas une série de callbacks. La prochaine frontière absolue est le prochain début/fin strictement futur pertinent dans le calendrier ; le driver réévalue à cette frontière. Pas de cursor durable, historique consumed ou rattrapage d'une succession d'événements.

Un saut en avant peut sauter une fenêtre entière sans la montrer. Un saut en arrière peut rendre active une fenêtre déjà vue ; elle peut alors être sélectionnée à nouveau. C'est la règle WALL explicite « état civil courant », indépendante du replay MEDIA. Un ancien timer ne peut pas justifier ce retour ; seul un échantillon frais sous les gardes courantes le peut.

La durée du manifest est une propriété validée de la fenêtre, pas un countdown relancé à sa durée totale au boot. La disparition est commandée par la fin absolue réévaluée. Si Android suspend l'owner, la latence physique reste celle de la plateforme ; à son retour aucune fenêtre expirée ne réapparaît. « Exact » signifie comparaison exacte aux bornes de l'échantillon, pas garantie de réveil à la milliseconde.

## 6. Présentation commune et ownership — question 6

Le contrôleur M5 peut rester commun : il indexe le manifest par ID, ignore ID inconnu et génération stale, fait preflight, remplace une scène et n'acquiert aucune clock. Son texte historique mentionne Video, mais les signatures et le code de remise sont utilisables pour Banner. La validation des scènes appartient au handler avant ARM.

Chemin futur : scheduler WALL pur → résultat ID sous token → owner Android commun → `onEventDue/onEventExpired(id, generation capturée)` → `SceneSink` existant → `SceneRenderer`. Les callbacks WALL asynchrones n'emploient jamais l'overload sans génération. L'éligibilité Banner est locale : activation courante, overlay disponible, état de présentation non suspendu ; elle ne dépend pas d'une identité MediaSession. Le bridge WALL ne transmet pas de playback ou de pause player.

Au `BASE_TV_M6` mergé, `OverlayService.LiveVideoRuntimePorts` possède pending/active/retiring, un token frais et une génération capturée une fois après ARM du manifest. M6 doit extraire/réutiliser cette mécanique d'activation au point de composition, puis fournir des ports Video et Banner statiques sous le même owner. Il ne faut pas copier une deuxième autorité LiveBanner parallèle. La projection Video, l'adapter MediaSession et les politiques MEDIA restent propres à Video.

Video manifested utilise déjà `SceneRenderer` ; Video legacy garde son renderer/countdown qualifié. Ce legacy n'est pas supprimé au nom de la généralité. La retraite existante des deux surfaces natives reste une obligation avant tout changement de kind. Banner n'ajoute aucune fenêtre concurrente, aucun renderer alternatif ni fade différé : son profil minimal impose les animations `none`, y compris récursivement, et retrait immédiat à EXIT. Les animations Video conservent leur comportement.

Un refus de preflight/render à DUE produit le diagnostic borné et aucun affichage partiel. Il ne crée pas une boucle de retries visuels à chaque heartbeat. Une réactivation/transition d'éligibilité valide réévalue la fenêtre courante ; une erreur native nécessitant abort masque et invalide le volatile. ACK atteste ARM réussi, pas preuve qu'une scène a été observée physiquement.

## 7. Video et Banner sont alternatifs — question 7

Un device possède une installation durable courante, une révision monotone, un handler actif et un owner. Le choix Video/Banner est un choix de kind du même package courant. Banner N+1 peut remplacer Video N ; Video N+2 peut remplacer Banner N+1. Il n'existe aucun compteur Banner séparé, priorité inter-produit, mixeur graphique ou installation parallèle.

Quand Banner est sélectionné, l'ancien calendrier MEDIA est vidé/inactif ; les nouvelles observations MEDIA ne peuvent pas rendre. Quand Video est sélectionné, le timer WALL est annulé et ses gardes invalidées avant chargement MEDIA. Des instances statiques peuvent exister à la composition, mais un seul chemin temporel possède le package actif. Le scheduler legacy `MediaSyncedTrackScheduler` est encore présent au `BASE_TV_M6` mergé comme définition conservée, sans instanciation de production après cutover ; il n'est pas une façade actuellement utilisée. Ne pas le réactiver.

Les invariants M4 rendent cette alternative possible sans changer la forme du store. Toute demande de coexistence Video+Banner, de store multi-installation ou de second owner impose STOP et un autre chantier.

## 8. Handler Banner dans le pipeline M4 — question 8

Les conventions auditées distinguent codec TV, codec de compatibilité Cloud et version de corps. Les noms suivants sont proposés à réserver dans le futur work order : codec TV `scenevibe.banner-wall-overlay.v1`, profil Cloud `scenevibe.banner.delivery.v1` / `1.0.0`, corps `scenevibe.banner.wall-package.v1`. Aucun n'est enregistré aujourd'hui. Les versions Video et `scenevibe.overlay-manifest.v1` ne changent pas.

Décision : un artifact UTF-8 `banner` contenant le corps exact du profil : version fermée, horizon, fenêtres WALL, manifest inline et provenance minimale bornée. Une scène correspond exactement à une occurrence : son ID égale l'eventId, son `startMs = 0` signifie offset local depuis l'anchor absolu de cette occurrence dans ce profil Banner, sa `durationMs = end - start`. Le manifest porte `product = banner`, `clock.mode = wall`, `pauseBehavior = continue`. Ce profil donne enfin un sens exécutable à WALL sans déplacer les bornes du parser existant ou utiliser son `startMs` comme epoch. Plusieurs occurrences d'un même contenu peuvent dupliquer un template inline sous des IDs différents, toujours dans les bornes.

Validation croisée obligatoire : bijection scènes/fenêtres, IDs uniques, counts égaux, durées exactes, toutes les scènes à offset zéro, domaine/horizon valides, rendering contract exact, primitives autorisées, absence d'asset et animation différée, aucune clé/type/version inconnue. Les bornes M4 restent applicables : package 3 000 000 octets, artifact 2 400 000 maximum, 256 scènes, profondeur de groupe 4, primitives bornées par scène, canvas 1920×1080, texte 2 000 codepoints. Le plafond Cloud de corps 1 MiB est plus strict. Les valeurs scalaires de `PreparedInstallation` restent au plus 256 × 4 000 caractères ; le bundle JSON n'y est pas caché. Le calendrier et manifest immuables appartiennent à un `InstallationHandler.PreparedState` typé.

Le registry actuel est statique, limité à deux entrées et aux codecs de `TvCapabilities.current()`. L'extension future est explicite et finie : les deux handlers Video existants plus un handler Banner distinct, count/whitelist concordants et attente d'un seul artifact pour ce codec. Une composition commune remplace la composition exclusivement Video. Pas de découverte, réflexion ou handler distant ; aucune branche Banner cachée dans les handlers Video.

| Étape | Obligation Banner |
| --- | --- |
| VALIDATE | Taille avant parse, forme fermée, clock/capability et contrat, validation croisée ; zéro écriture |
| PREPARE | Copies immuables typées, index d'IDs et requirements WALL/CONTINUE ; zéro écriture/clock/timer |
| ENCODE / COMMIT | Snapshot générique des bytes exacts et handlerId, une écriture atomique existante |
| READBACK / RESTORE | Relire et vérifier la liaison exacte révision/codec/handler/artifact ; reconstruire depuis le durable |
| ARM | Retirer ancien owner ; charger le calendrier/manifest durable sous pending ; échantillon et driver prêts ; capturer génération ; sélectionner une seule activation |
| ACK autorisable | Seulement résultat ARMED pour cette révision et ce client toujours courant |

La première évaluation d'affichage est calculée fraîchement et remise à l'owner seulement après promotion pending → active. Un DUE calculé/émis pendant pending serait refusé par M5 ; il ne doit ni être consommé puis perdu, ni être rejoué avec une génération relue au moment de livrer. Le futur driver doit programmer son attente sans livrer d'effet avant promotion, puis réévaluer l'epoch sous les gardes actives ; pending ne rend jamais. L'installation du prochain réveil doit avoir réussi avant le résultat ARMED ; une erreur de clock/registration initiale fait échouer ARM. Une fenêtre déjà active peut donc être montrée dès cette première évaluation fraîche, mais pas pendant validate/prepare/commit.

`InstallationStore`, `InstallationSnapshot`, `InstallationSnapshotCodec`, transport Android corrigé et `PackageInstaller` restent le chemin unique. Le format de snapshot sait déjà stocker un artifact opaque ; il ne nécessite pas de nouvelle version ni clé temporelle. Aucun second store, installer, journal d'occurrences ou persistance d'anchor.

Les failures reprennent les résultats M4 : rejet de validation/préparation sans commit ; CACHE_FAILED/readback invalide sans ARM ; ARM_FAILED après commit conserve le nouveau durable pending et l'ancien ACK, sans rollback ni ACK de la nouvelle révision. Échec de rendu ultérieur ne réécrit pas le package. Des codes WALL bornés complètent le diagnostic, pas une sémantique d'installation plus faible.

## 9. Banner démontrable sans assets distants — question 9

Le sous-ensemble exécutable local retenu est texte, rectangle/couleurs, table et group/layout, avec les règles de dimension et de profondeur existantes et les polices système. `SceneRenderer` possède déjà ces chemins natifs. Leur utilisation Banner sera qualifiée par les gates futurs ; lire leur code ne constitue pas une clôture physique Banner.

Le parser générique sait aussi représenter image, mais le profil Banner M6 refuse toute primitive image et toute référence d'asset, même présentée comme locale. Pas de URL, CDN, fetch, cache, police téléchargée, vidéo distante, WebView, ressource web ou data URI. Le résolveur de scène ne doit acquérir aucun contenu. Le digest de package décrit en section 10 est une intégrité de livraison héritée du Cloud OS, pas une gestion de hashes d'assets du chantier controlled-assets.

## 10. Frontière minimale Cloud/TV — question 10

### 10.1 Ce qui existe et bloque une livraison directe

Le Cloud possède déjà `DisplayPublication`, `RenderPackage`, `DeviceAssignment`, un engine OS et des repositories durables à une ligne courante par device. Ils sont product-neutral. Cependant la composition live et les routes v1 restent Video : GET/PUT `/api/v1/devices/[deviceId]/assignment`, POST `/ack`, FinalTrack, `finalTrackId`, `runtimeTrack`, optional manifest. `video/assignment-v1-facade.ts` reconstruit la compatibilité par champs nommés ; aucun identifiant interne de publication n'est exposé.

En production SHADOW, `services/video-assignment-service.ts` laisse `tv_assignments` allouer les révisions et miroir OS la révision exacte. `http/cloud-service.ts` fixe ce mode. `tv_assignments.final_track_id` est non nullable et référencé par FK : Banner ne peut pas y allouer une révision sans faux FinalTrack. Le cutover existant permet une autorité OS avec transaction Video/mirror, mais son coordinateur attend encore `finalTrackId` pour chaque opération. Enfin le codec de persistance injecté est Video-only : accepter le vocabulaire WALL de `RenderPackage` ne suffit pas à sceller/lire un package Banner.

| Alternative | Décision |
| --- | --- |
| Ajouter Banner à l'enveloppe Video v1 | Écarté : faux FinalTrack ou modification du contrat/goldens |
| Route Banner et compteur Banner autonomes | Écarté : deux révisions/store/pollers et remplacement incohérent |
| Livraison interne commune au-dessus de DeviceAssignment, compatibilité Video v1 conservée | Retenue : utilise le durable/revision OS déjà présents, extension finie de composition |

### 10.2 Publication minimale et livraison proposée

Le producteur Banner est statique. Une commande interne authentifiée par compte reçoit l'intention civile bornée ou les fenêtres absolues et le contenu inline. Elle valide/résout avant d'assigner, compile un candidat déterministe, prépare une publication sous la clé de dedup OS existante, scelle le RenderPackage, puis vérifie ready avant Send. Le `inputRef` opaque appartient à Banner ; il n'est ni un FinalTrack ni l'UUID interne de publication. Une nouvelle entrée/horizon/adapterVersion crée une nouvelle publication. Un Send même identique est une nouvelle révision, conformément au contrat OS ; une retry de transport doit pouvoir redeliver la révision existante sans se transformer en Send.

Il n'est pas nécessaire de créer une table d'éditeur Banner pour ce sous-ensemble. La publication ready conserve le bundle résolu et la provenance nécessaires. Une panne entre prepare et seal peut laisser une publication prepared non assignée ; elle n'est pas livrable. Rejouer la même soumission complète permet dedup puis seal. Banner GET exige une publication ready et ne tente jamais une compilation à partir d'une source éditoriale manquante. L'engine actuel compile via son producer à GET et son Send prépare puis assigne sans exiger ready : il ne fournit pas encore ces garanties Banner. La future composition Banner doit donc vérifier le sealed ready avant allocation, puis ne lire que ce sealed à GET, sans changer la branche Video. Un éditeur modifiable et des sources éditoriales persistées avancées appartiennent aux workstreams post-M6 non numérotés.

Le port `OsRenderPackageCodec` est déjà injecté dans les repositories OS. Le futur point de composition injecte une politique statique finie Video+Banner : décodeurs connus, vérification du corps réel contre execution requirements et digests. La branche Video existante reste byte-identique ; unknown codec reste refusé. Le core OS n'importe pas Banner ou Video. Le registry d'input adapters reçoit un second adapter au point de composition, sans découverte dynamique. Les tables `display_publications` et `device_assignments` existantes suffisent pour le profil minimal ; aucun relâchement de FK FinalTrack n'est requis.

Routes internes conceptuelles proposées : GET/PUT `/api/v1/devices/:deviceId/package-assignment`, POST `/api/v1/devices/:deviceId/package-ack`. PUT est la commande compte « publier/Send Banner » minimale ; GET et ACK utilisent le bearer du device. Il ne s'agit ni d'une Partner API, ni d'un SDK, ni d'une négociation publique de capabilities. Les noms définitifs devront être arrêtés au work order de contrat.

Enveloppe conceptuelle `scenevibe.cloud.package-assignment.v1` : version, deviceId, révision entière positive sûre, kind, codec/version, corps exact et digest de profil, métadonnées bornées nécessaires au transport. Branche Banner : bundle résolu, sans champ média. Branche Video : enveloppe Video v1 reconstruite inchangée et remise au `CloudV1InstallationAdapter` ; aucune modification de son contenu ni du codec Video. L'UUID `DisplayPublication.id`, le RenderPackage interne complet, ses requirements internes et ses digests internes ne sont pas exposés ; le seul digest livré est celui du profil de compatibilité sélectionné.

Le corps Banner est transporté comme chaîne UTF-8 logique exacte et bornée, puis encodé tel quel en artifact par l'adapter TV, sans reparsing/resérialisation à cet endroit. Le decoder du handler effectue ensuite la validation fermée. Digest proposé conforme à la convention existante du delivery profile : SHA-256 de `codecId UTF-8 + NUL + codecVersion UTF-8 + NUL + body UTF-8`. La livraison HTTP/enveloppe reste également sous le plafond d'entrée TV applicable, y compris les échappements JSON. Unicode composé/décomposé est conservé, sans normalisation.

Un seul poller TV remplace la composition v1 exclusivement Video pour les clients M6 qualifiés. Il utilise la nouvelle route pour les deux kinds et conserve les règles de `CloudControlClient` / `AssignmentMutationGate`. Les clients Video existants continuent leurs routes v1. Pas de polling Banner concurrent, port LAN Banner ou protocole de pairing nouveau.

### 10.3 Une seule autorité de révision, condition de qualification

Une route générique seule ne résout pas le SHADOW actuel. Avant le premier Send Banner, un work order distinct doit autoriser et qualifier la transition d'autorité sur le control plane concerné. Réutiliser le mode CUTOVER existant, son backfill/parité et la même transaction/lock device, puis étendre de façon finie la composition : `device_assignments` seul allocateur pour tous les Sends Video/Banner et tous les ACK. Le backfill doit préserver la plus grande révision observée et les ACK exacts ; aucun reset de compteur. Pas de mélange entre un writer Video SHADOW et un writer Banner OS, même via deux routes différentes.

Pour Video, allocation OS et miroir legacy restent atomiques comme dans le coordinateur qualifié. Pour Banner, le courant OS est remplacé dans la même autorité ; le miroir Video historique ne devient pas un Banner factice. Les façades/ACK v1 doivent consulter le kind courant OS dans ce mode : si Banner est courant, elles refusent de livrer/ACKer un ancien miroir Video. Elles ne ressuscitent jamais le FinalTrack précédent. Une future attribution Video crée une révision supérieure et renouvelle son miroir exact. Toutes les routes de Send/GET/ACK d'un control plane suivent le même mode.

Point sensible : le coordinateur actuel n'a pas de branche Banner et les handlers HTTP sont encore Video. Cette extension devra être démontrée par des gates transactionnels et concurrentiels réels ; elle n'est pas « déjà implémentée ». Aucun changement de mode, migration ou déploiement Cloud n'est effectué ou autorisé par cette Phase A. La production reste SHADOW. La portée et l'autorisation du futur déploiement sont un gate préalable, pas un défaut choisi implicitement ici.

Après un courant Banner, un simple retour au service Video-only SHADOW serait dangereux : son miroir peut être ancien et ne représente pas Banner. Ce rollback est interdit. Conserver l'autorité OS, ou effectuer un nouveau Send Video à N+1 sous OS puis qualifier explicitement une transition compatible après parité. Aucun fallback vers une révision ancienne, renumérotation ou faux ACK. Si cette transition contrôlée ne peut être autorisée, la livraison live Banner reste bloquée ; on ne contourne pas le problème par un compteur parallèle.

## 11. ACK et durable exacts — question 11

Le modèle interne courant `DeviceAssignment` convient aux deux clients ; seule la preuve de liaison transport diffère. Video conserve sa validation `revision + finalTrackId` et son envelope v1. Banner propose `revision + codecId + codecVersion + deliveryDigest`. Le serveur authentifie d'abord device/token et compte propriétaire, lit son courant OS, vérifie le profil sealed exact, puis applique le CAS révision/publication courant sous le lock partagé. Il ne fait pas confiance à un digest fourni sans cette comparaison. L'ID interne de publication reste serveur.

Sur la TV, la preuve ACK Banner provient des bytes du snapshot durable qui vient d'être restauré et armé, pas des champs d'une redelivery entrante. Elle se reconstruit depuis l'artifact ; aucune clé ACK produit supplémentaire dans le store. Le résultat ARMED, le token/client encore actif et la révision durable exacte sont requis. Après réponse serveur valide, `InstallationStore.markAcknowledged` n'avance que pour la révision encore courante. Arrêt/reset/interruption de la mutation owner annule l'autorisation ; un simple post sur main ne suffit pas.

| Révision entrante | Règle M4 préservée |
| --- | --- |
| Stale, inférieure au durable | Rejet avant handler ; zéro mutation, ARM et ACK de cette entrée interdits |
| Same revision | Ignorer bytes/kind entrants ; relire le binding durable, restore/ARM exact ; zéro repersist ; ACK seulement de ce binding |
| New revision | Validate/prepare, commit atomique, readback exact, restore, ARM ; ACK ensuite seulement |
| Commit réussi / ARM échoué | Nouveau durable pending ; ancien ACK conservé ; redelivery future peut retenter restore/ARM |
| ARM réussi / réseau ACK indisponible | Runtime utilisable localement ; ACK durable inchangé ; poll conditionné à acknowledgedRevision permet redelivery |
| ARM réussi sans fenêtre active | ACK autorisable : package installé et scheduler armé ; ACK ne signifie pas affichage |

GET conditionnel continue d'utiliser `afterRevision = acknowledgedRevision`, pas installedRevision. Même révision modifiée malicieusement ne remplace jamais le durable, et son corps ne sert pas de preuve ACK. Un ACK ancien reçu après un remplacement ne peut pas confirmer la nouvelle publication. Le serveur renvoie une confirmation bornée device/révision/status ; la TV vérifie ces champs avant marque locale. Aucun ACK après validate, prepare, commit seul, échec ARM ou clock initiale invalide.

## 12. Reboot et offline — question 12

Ordre conservé : ouvrir store générique → same-revision installer → restore/ARM du handler durable → nouveau driver/anchor → sélection courante → démarrer observation ou polling utile. Le startup restore est terminé sur l'owner avant Cloud et avant les observations MEDIA. Aucun Send, re-pairing, reset, téléchargement ou repersist n'est nécessaire.

Une fenêtre active au boot peut apparaître immédiatement après ARM sous la nouvelle activation, jusqu'à sa fin absolue. Une fenêtre passée reste invisible. Les identités installation/device, credentials, ACK courant et révision pending sont conservés. Un package pending non ACKé se restaure de la même manière ; le Cloud peut ensuite redeliver cette révision et obtenir ACK après ARM exact, ou proposer une révision supérieure qui retire l'ancien runtime. Une clock OS invalide reste une cause d'invisibilité, pas de changement du durable.

Le transport SharedPreferences corrigé M4 (`scenevibe.os.android-preference.v1:` et framing final `!`) et le snapshot logique exact restent indispensables au reboot Sony. Le générique courant, même corrompu, reste autoritaire : aucun fallback vers des anciennes clés Video. Les failures read-only et ACK-ahead ne sont pas réparées silencieusement.

Résidu réel à traiter dans un futur work order TV : `BootReceiver.decide` appelle aujourd'hui `AutostartPolicy` avec `mediaGranted` et un booléen cache, sans kind. Le policy exige l'accès Notification/MediaSession même pour un cache générique. M6 doit ajouter une décision bornée pour un snapshot de codec/handler Banner connu : opt-in, overlay et durable présent, sans grant MediaSession. Le receiver continue de lire uniquement les métadonnées ; validation/restore complets restent au service. Les chemins Video, inconnus, credentials seuls et permission ordering historique conservent leurs décisions. Pas de parser Banner dans le receiver, permission nouvelle ou Direct Boot. MainActivity ne doit pas acquérir une exigence MediaSession pour démarrer Banner.

## 13. Owner thread, générations et courses — question 13

Un seul owner main Android sérialise mutation d'installation, remplacement, clocks, retrait natif et scène. L'I/O peut préparer une demande hors owner ; `AssignmentMutationGate` attend son exécution réelle et reste annulable. Le driver ne modifie aucun controller depuis un thread timer parallèle.

Binding immuable d'activation : kind/handler/révision, token frais unique à chaque ARM même same revision, génération de présentation capturée après loadManifest. Binding d'attente : ce token, cette génération, `wallGeneration`, ticket de timer unique et deadline absolue. Capturer les gardes lors de la production/post ; ne jamais lire la nouvelle génération au moment de livrer une ancienne callback. Annuler invalide aussi le ticket, même si la queue Android livre quand même le runnable. Un timer valide relit l'epoch, puis le scheduler émet synchroniquement ses effets sous cette activation. Il ne remet pas en queue des DUE calculés avant une correction d'heure.

Retraite : invalider active/pending et les tickets avant hide/clear ; annuler l'attente ; vider le calendrier actif ; unload/hide immédiats via les ports existants ; préparer le nouveau pending ; capturer et vérifier les bindings ; promouvoir à la fin d'ARM. Les refus/throws exécutent les nettoyages bornés, y compris le comportement M5 qualifié de clear interrompu. Pas de restauration de l'ancien owner depuis une callback.

| Course | Résultat exigé |
| --- | --- |
| Replacement juste avant DUE | Token ancien invalidé avant la nouvelle sélection ; DUE ancien ignoré |
| Replacement juste après DUE / pendant affichage | Retrait immédiat de la scène ancienne, puis nouveau package seul |
| EXPIRE ancien après nouvel ARM, même eventId | Token + génération capturée empêchent de cacher la nouvelle scène |
| Clear pendant attente | Ticket invalidé et calendrier vide avant hide ; runnable livré sans effet |
| Service stop/start | Invalidation et retrait au stop ; nouvelle activation/anchor au start ; aucun token réutilisé |
| Reboot | Anciennes queues détruites ; état volatile neuf, durable seul restauré |
| Changement d'heure pendant attente | Nouveau wallGeneration/ticket, recalcul fresh ; ancienne deadline neutralisée |
| Timer remplacé sans changement de génération | Ticket différent suffit à neutraliser la callback annulée |
| Callback pending avant promotion | Refus de rendu ; seules les callbacks du binding actif sont honorées |

La génération WALL invalide la source temporelle et ses attentes ; la génération du contrôleur protège son manifest. Les deux ne sont pas confondues. Si un compteur de token/génération ne peut plus produire une valeur neuve sans overflow, ARM/driver échoue fermé ; jamais de réutilisation silencieuse.

## 14. Politique de clock jump — question 14

| Discontinuité | Recalcul borné |
| --- | --- |
| Heure avancée de 30 minutes | EXIT de l'ancien candidat si nécessaire ; au plus DUE du candidat actif au nouvel epoch ; pas de fenêtres intermédiaires |
| Correction réseau de −5 minutes | Nouveau candidat selon l'heure courante, éventuellement retour d'une fenêtre ; aucune logique seek MEDIA |
| Longue suspension / process retardé | Échantillon frais au retour, fenêtres passées omises, seulement le temps restant de l'actuelle |
| Fuseau local modifié | Invalidation/rééchantillonnage ; calendrier UTC inchangé |
| DST | Résolu au Cloud avant seal ; aucun moteur DST local |

Chaque correction annule l'attente devenue incohérente, réancre et réévalue au plus 256 fenêtres/une transition EXIT+DUE. Aucune deadline calculée une seule fois ne survit aveuglément. Les seuils de détection n'autorisent jamais un rendu fondé uniquement sur la projection monotone.

## 15. Diagnostics bornés — question 15

Les conventions actuelles sont des enums `RuntimeDiagnostics`, des setters observationnels `DiagnosticsStore` et une vue `DiagnosticsActivity`. Aucun texte d'erreur arbitraire n'est nécessaire. Taxonomy conceptuelle WALL, à ajouter dans ce style : NONE, WALL_CALENDAR_LOADED, WALL_ANCHORED, WALL_EVENT_DUE, WALL_EVENT_EXPIRED, WALL_EVENT_SUPERSEDED, WALL_CLOCK_REEVALUATED, WALL_CALENDAR_CLEARED, WALL_HORIZON_EXHAUSTED, WALL_DISPLAY_SUSPENDED, WALL_CLOCK_INVALID, WALL_DEADLINE_FAILED. Les résultats d'installation/read failure existants restent séparés.

Champs admissibles : clock kind, révision, génération, count borné, raison enum, état anchor/timer, dérive/retard scalaire borné et compteurs saturants. Pas de ring log non borné, texte Banner, contenu de table, eventId fourni par utilisateur, token, device credential, URL, payload, source éditoriale ou stack trace contenant des données. Le diagnostic « DUE » décrit l'effet runtime, pas une preuve Sony. Les diagnostics ne décident jamais de la sélection, de la clock ou de l'ACK.

## 16. Sécurité et continuités — question 16

Registry et adapters statiques ; tailles avant parsing, clés/versions/types fermés, UTF-8 valide, entiers sûrs et additions sans overflow. Authentifier le compte/device avant lectures ou mutations liées à leurs publications ; aucun accès cross-account, token révoqué ou ACK forgé. Les digests d'intégrité ne remplacent pas l'authentification TLS/bearer. Refuser les codecs inconnus et toute incohérence profil/requirements sur le durable Cloud comme TV.

Aucune commande player, injection dans une application Video, WebView, JavaScript distant, code dynamique, APK/plugin téléchargé, reflection de handler ou acquisition d'assets. Package Android, signer, installation identity, pairing, credentials, reset semantics, stockage après unlock et FGS `specialUse` restent ceux qualifiés. Permissions actuelles seulement : overlay, foreground service/specialUse, Internet, boot, et le service NotificationListener historique pour Video. Aucune permission alarm/clock/wake supplémentaire. Toute nécessité de permission, store multiple, assets distants, owner concurrent ou modification de M5 en cours impose STOP, diagnostic précis et nouveau périmètre.

## 17. Futur découpage M6, après réconciliation seulement

| Phase | Travail autorisable par un work order futur | Sortie/gate |
| --- | --- | --- |
| B | Modèles WALL, validator/profil proposés et scheduler pur ; fake-clock et jeux de frontières | JDK pur, bornes et déterminisme ; zéro changement du core MEDIA ; WALL non annoncé dans le runtime courant |
| C | Handler Banner statique, restore, composition/owner commun, driver Android et autostart Banner connu en qualification | Même installer/store, gardes, ACK post-ARM, équivalence Video complète ; pas de Cloud live ou capability publique |
| D | Résolveur Banner Cloud, codec statique, publication ready, transport interne et ACK ; autorité OS unique dans un cadre explicitement autorisé | Contrat/goldens Video, transactions/DB réelle, concurrence cross-kind, backfill/parité et refus de rollback dangereux ; production SHADOW protégée tant qu'aucun autre ordre ne l'autorise |
| E | Cutover live de qualification assemblant TV/Cloud, validation software finale, puis protocole Sony | Gate automatique exhaustif après cutover, capability locale véridique, upgrade/restore/temps/ACK physiques ; clôture Banner uniquement sur preuves |

Ce découpage garde le cutover séparé des primitives et prévoit un gate automatique final après l'intégration live. Une phase supplémentaire de déploiement d'autorité Cloud peut être nécessaire ; elle ne doit pas être dissimulée dans C/D. Aucun APK M6, source, timer, contrat ou handler n'est produit en Phase A.

## 18. Gates automatiques futurs

| Domaine | Preuves minimales, sans weakening des gates hérités |
| --- | --- |
| Frontières | Imports WALL sans Android/MediaSession/Cloud/store/renderer ; MEDIA sans WALL/zone ; handlers statiques ; permissions et FGS identiques ; une installation/un owner/un allocateur |
| Modèles / scheduling | Invalides, bornes, 256/257, epochs sûrs, durées, horizon, bijection ; start/end exacts, late boot, overlap/tie, ordre permuté, forward/backward, fuseau, suspension, idempotence et au plus EXIT+DUE |
| Driver | Fake sources epoch/elapsed/uptime ; drift ±, seuil, signal même petit saut, sleep inclus/exclu, registration échouée, callbacks annulées, tickets remplacés, compteurs et process restart |
| Ownership | Before/after DUE, pendant affichage, ancien EXPIRE au même ID après ARM, pending interdit, clear/stop/reboot, clock jump ; Video↔Banner retire tous les visuels et neutralise l'autre clock |
| Installation / ACK | Validate/prepare = zéro écriture ; same = zéro repersist ; stale = zéro mutation ; corrupt/readback/ARM fail = zéro ACK ; durable pending, ACK réseau tardif, binding exact et client remplacé/reset |
| Durabilité native | SharedPreferences réels APIs 31/35, Unicode cinq formes et bytes exacts, hard process restart, corruption/read-failure fail-closed ; aucune conversion JSONB/normalisation |
| Cloud | Règles civiles gap/fold, horizon stable, prepared non livrable, retry seal, aucun compile Banner à GET, unknown/digest/requirements, auth/revoked/cross-account ; profils Video/goldens et corpus inchangés |
| Révisions / transactions | DB réelle, première attribution concurrente, Video/Banner interleavés, reverse-order ACK, même publication Send répété, rollback injecté, miroir Video atomique, old v1 bloqué pendant Banner, reprise/backfill sans régression |
| Régression | Importer les gates effectivement fermés de M5 merged : projection, différentiels, pause/seek/replay, exact token/generation, durabilité M4 ; gate complet après cutover E |

La failure d'inventaire de la Phase A sur l'ancien socle M4 reste historique. M5 mergé contient ses admissions de clôture et leur inverse qualifié. La réconciliation ajoute exclusivement les deux chemins documentaires M6 dans les quatre inventaires finis ; les blobs avant/après complets et l'inverse unique sont vérifiés avant Sony → D → C → B → M4. Aucune protection historique n'est remplacée. Les résultats sur le nouveau HEAD exact sont rapportés séparément des preuves M5 historiques.
## 19. Futur protocole physique Sony minimum

Exécuter avec Pierre, une action et une observation à la fois, uniquement après gates software, autorisation du control plane et APK de qualification traçable. Relever préalablement version, signer, installationId/deviceId, pairing, opt-in, permissions, codec/handler, installed/acknowledged revision et read failure. Vérifier SHA-256/APK/signer et faire un upgrade sans uninstall, clear, reset ou re-pairing. Signer attendu du socle : `f908bf564ed97ba67e02b1ebc89eb0239cf980752587f55eb9ec0419791a2e9c`.

1. Confirmer identités, pairing, permissions et durable préservés, restore ARMED avant Cloud, sans nouveau Send. Qualifier les deux entrées Video conservées et Banner connu sans dépendance MediaSession ajoutée.
2. Publier/Send un package Banner inline avec fenêtre future et contenu Unicode `COMPOSÉ=é | DÉCOMPOSÉ=é | LIGATURE=œ | APOSTROPHE=’ | EMOJI=🙂`. Relever révision, codec, ARM puis ACK exact, absence de visuel avant start et absence d'asset distant.
3. Observer DUE à la borne programmée selon les timestamps capturés, un seul visuel puis expiry. Quantifier le retard de l'owner/dalle sans appeler cela une garantie temps réel. Tester remplacement de planning pendant affichage et l'ancien EXPIRE après remplacement.
4. Couper temporairement le réseau si le scénario local le permet, faire hard reboot sans Send et revenir dans une fenêtre encore active : restore local et temps restant seulement. Vérifier pairing/identités/révisions et zéro corruption. Refaire un retour après une fenêtre totalement passée : aucun replay.
5. Tester veille/réveil et retour tardif ; aucun affichage d'une fenêtre passée. Tester chevauchement/égalité selon la règle arrêtée et vérifier absence de double visuel.
6. Changement d'heure contrôlé ± et retour à l'heure réelle, seulement si le Sony fournit un réglage supporté et testable ; documenter les horaires avant/après. S'il est impossible, NOT RUN avec raison, sans route de réglage inventée ni clôture silencieuse de ce risque.
7. Remplacer Banner par Video puis Video par Banner avec révisions supérieures. Vérifier une autorité/ACK exacts, aucun ancien timer/visuel, comportement MEDIA Sony qualifié inchangé.
8. Conserver observations, logs bornés et identité de l'APK/control plane ; distinguer erreurs système étrangères et SceneVibe. Exiger absence de crash, CORRUPT, CACHE_FAILED et double ownership. Same-revision/redelivery physique uniquement par un mécanisme déjà supporté ; sinon NOT RUN explicite, sans invention de Send équivalent.

Une preuve instrumentée Android standard n'est pas une qualification Sony. Ce protocole n'est pas exécuté dans cette Phase A et n'ajoute aucune clôture à M5.

## 20. Réconciliation acquise et prochain périmètre

La clôture physique M5 et le merge ne sont plus pending. Les fichiers réels de `17cbe36ae99ac7f48aaf861e0d1feac702a9e521` ont été relus, les seize points du work order ont une décision explicite dans le rapport compagnon et les primitives MEDIA/Video sont héritées sans modification. La présente branche doit être descendante de ce main exact et de l'ancien HEAD M6, avec un delta documentaire/provenance fini. Les quatre workflows hérités sont réexécutés sur le HEAD publié ; les succès historiques de M5 ne leur servent pas de substitut.

Le prochain work order pourra autoriser **uniquement M6 Phase B** : valeurs immuables `WallEvent`/`WallCalendar`, validator JDK pur du domaine temporel, scheduler pur payload-free avec temps/éligibilité explicites, sélection half-open/latest-start/tie-ID, prochaine frontière, au plus EXIT+DUE, tests fake-clock/frontières/overlap/sauts/late-start et protection inchangée du MEDIA. Le profil Banner peut être spécifié et ses contraintes pures documentées ; aucun handler, codec enregistré, adapter Cloud/TV ou parsing branché au runtime n'appartient à B.

Owner partagé, token/anchor/ticket Android, autostart, renderer/composition et durable Banner appartiennent à C ; resolver/publication/transport/ACK et autorité OS Cloud à un ordre D distinct ; intégration live et Sony à E sous autorisation propre. WALL reste non annoncé par `TvCapabilities.current()` pendant B. Cette réconciliation n'autorise ni n'entame ce travail exécutable. PR #16 reste OPEN / DRAFT / unmerged.

## 21. Références de preuve et limites

- [Architecture M4 au socle réel](https://github.com/pierre22400/scenevibe-tv-companion/blob/67b81045258b1692073c6927b956db4899c6ad1a/docs/scenevibe-os-m4-tv-installation-architecture.md), [clôture Sony M4](https://github.com/pierre22400/scenevibe-tv-companion/blob/67b81045258b1692073c6927b956db4899c6ad1a/docs/m4-final-sony-physical-closure.md), [correctif durabilité Sony](https://github.com/pierre22400/scenevibe-tv-companion/blob/67b81045258b1692073c6927b956db4899c6ad1a/docs/m4-phase-g-sony-hard-reboot-corrective-report.md).
- [Architecture M5 au merge exact](https://github.com/pierre22400/scenevibe-tv-companion/blob/17cbe36ae99ac7f48aaf861e0d1feac702a9e521/docs/scenevibe-os-m5-scene-event-media-calendar-architecture.md), [SceneEvent réel](https://github.com/pierre22400/scenevibe-tv-companion/blob/17cbe36ae99ac7f48aaf861e0d1feac702a9e521/app/src/main/java/com/scenevibe/tvcompanionpoc/calendar/SceneEvent.java), [owner Video réel](https://github.com/pierre22400/scenevibe-tv-companion/blob/17cbe36ae99ac7f48aaf861e0d1feac702a9e521/app/src/main/java/com/scenevibe/tvcompanionpoc/OverlayService.java), [rapport cutover M5 D](https://github.com/pierre22400/scenevibe-tv-companion/blob/17cbe36ae99ac7f48aaf861e0d1feac702a9e521/docs/m5-phase-d-video-cutover-report.md).
- [Cloud SHADOW réel](https://github.com/pierre22400/interface-scenevibe/blob/5011c91aac61a0cc6dcc74c256a15b7dee03d785/packages/cloud/src/http/cloud-service.ts), [service d'autorité Video](https://github.com/pierre22400/interface-scenevibe/blob/5011c91aac61a0cc6dcc74c256a15b7dee03d785/packages/cloud/src/services/video-assignment-service.ts), [repositories OS](https://github.com/pierre22400/interface-scenevibe/blob/5011c91aac61a0cc6dcc74c256a15b7dee03d785/packages/cloud/src/os/postgres-repositories.ts), [codec de persistance Video-only](https://github.com/pierre22400/interface-scenevibe/blob/5011c91aac61a0cc6dcc74c256a15b7dee03d785/packages/cloud/src/video/render-package-codec.ts).

Les risques restants sont la latence/suspension Sony pour Banner, la confiance dans l'heure OS, l'horizon offline fini et la transition d'autorité Cloud à autoriser/qualifier. Ils sont des gates futurs explicites. La réconciliation ne démontre ni Banner live, ni WALL runtime, ni qualification Sony nouvelle.


## 22. Précisions issues des sources mergées

- Le token M5 est distinct de la révision et refuse son overflow. La génération de présentation est capturée après `replaceRevision`, puis fixée pour toute activation. Le controller incrémente actuellement `generation` sans vérification d'overflow ; une garantie générale d'overflow fail-closed n'est donc pas acquise par M5. C devra qualifier pour WALL la disponibilité de gardes fraîches et le headroom nécessaire aux load/unload, sans réutilisation ni changement opportuniste des politiques MEDIA. `wallGeneration` et timer ticket restent deux gardes volatiles supplémentaires indépendantes.
- Le controller conserve un flag `eligible` à travers les loads. M5 le rétablit au premier snapshot MEDIA éligible. Le futur chemin WALL doit donc initialiser explicitement l'éligibilité locale après promotion, masquer sur suspension/perte d'overlay et réévaluer le candidat courant au regain, même si son ID est identique. Un simple chargement de manifest ne suffit pas. Un refus de preflight n'est pas un succès visuel ; la sélection déjà tentée ne boucle pas en retries à chaque heartbeat.
- `CloudControlClient.fetchAssignment` garde actuellement un transport Video v1 et construit `finalTrackId` depuis l'Assignment adapté. Il n'implémente pas encore la preuve ACK Banner issue du durable. Réutiliser le gate owner, ARMED, client courant et révision exacte ne dispense pas d'ajouter la liaison codec/version/digest du snapshot dans le futur transport commun, en gardant Video v1 compatible.
- Les bornes WALL temporelles proposées ne sont pas des capacités existantes. Le corps 1 MiB, les artifacts 2,4 MB / package 3 MB et la taille HTTP/enveloppe s'appliquent cumulativement, échappements inclus ; le sous-ensemble effectivement livrable est leur intersection. Époque et horizon appartiennent au nouveau modèle, jamais au `startMs` MEDIA/manifest actuel.
- La clôture physique M5 est immuable : `Last startup restore: -`, `Last assignment revision: 0`, `Last successful ACK: 0` après reboot. Le `-` ne signifie pas ARMED. La survie et l'exécution de révision 6 sans Send prouvent la restauration fonctionnelle ; le logcat ne fournit pas de preuve explicite RESTORE/MediaCalendar/DUE/revision. Les mentions OPEN/DRAFT/unmerged de cette clôture sont l'état historique avant l'audit et le merge #15.
- Le POC Media Interlude au HEAD documentaire `96d1de5b555d52a88eda74fb45a6895d8599b316` établit uniquement VIDEO+PAUSE full interlude PASS et AUDIO+DUCK semantic FAIL sur Sony/Prime, malgré l'audio local fonctionnel en 0.1.4. Prime pause sur CAN_DUCK ; SceneVibe n'acquiert pas de pause ownership par cet effet de focus. Aucun fichier ni commit expérimental n'est importé. Banner/WALL reste indépendant ; aucune capacité universelle ni équation AUDIO=DUCK / VIDEO=PAUSE n'est déduite.
