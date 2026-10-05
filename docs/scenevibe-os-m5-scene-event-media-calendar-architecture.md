# SCENEVIBE OS — M5 SceneEvent / MediaCalendar

## Statut, autorité et périmètre

Cette architecture est le livrable **M5 Phase A : architecture et caractérisation**.
Les types et interfaces proposés ci-dessous ne sont pas encore implémentés.
Elle décrit une extraction du moteur MEDIA avec équivalence du comportement Video,
sans migration de production dans cette phase. Production reste **SHADOW**.

| Référence vérifiée au départ | Valeur |
| --- | --- |
| Repository TV | `pierre22400/scenevibe-tv-companion` |
| BASE_TV_M5 / main TV | `67b81045258b1692073c6927b956db4899c6ad1a` |
| Origine | Merge final M4 / [PR #14](https://github.com/pierre22400/scenevibe-tv-companion/pull/14), CLOSED / merged |
| BASE_CLOUD_M5 / main Cloud | `5011c91aac61a0cc6dcc74c256a15b7dee03d785` |
| Branche M5 | `work/scenevibe-os-m5-scene-event-calendar-001` |
| Publication | Nouvelle PR DRAFT vers main ; aucune réutilisation de #14 |

L'[architecture M4](scenevibe-os-m4-tv-installation-architecture.md), le
[rapport correctif](m4-phase-g-sony-hard-reboot-corrective-report.md) et la
[clôture Sony finale](m4-final-sony-physical-closure.md) restent inchangés et
autoritaires pour M4. M4 est clos, intégré à main et physiquement PASS. La mention
« PR conservée Draft » dans la clôture décrit son instant de gel, antérieur au merge.
Le précédent échec Sony reste historique ; cette architecture ne le réécrit pas.

La qualification corrective antérieure est 776 PASS / 0 FAIL / 1 SKIP JVM,
86 PASS / 0 FAIL Python et native API 31 + 35. Ce sont des **preuves héritées**,
pas des exécutions M5. Sur main exact, l'audit Phase A a exécuté Python :
**85 PASS / 1 FAIL sur 86**, à cause du document final M4 absent de l'inventaire
figé du test correctif. Ce défaut de qualification documentaire, également
applicable aux deux nouveaux documents M5, est détaillé dans le
[rapport Phase A](m5-phase-a-architecture-report.md). Il doit être résolu dans un
périmètre explicitement autorisé avant de commencer l'implémentation M5 ; aucune
assertion métier, fixture ou garde de provenance ne doit être contournée.

## Runtime réellement présent à la base

Les liens de cette section désignent les sources du repository ; les assertions
de comportement portent sur BASE_TV_M5. Une proposition future ne doit pas être
confondue avec un comportement déjà qualifié.

| Composant actuel | Responsabilité et couplage à extraire ou conserver |
| --- | --- |
| [ScheduledTrack](../app/src/main/java/com/scenevibe/tvcompanionpoc/ScheduledTrack.java) | Copie immuable des commentaires, tri stable par `startMs`, identité Video et pause. `Event` porte id, texte, temps et `android.graphics.Bitmap` : ce modèle n'est pas un calendrier générique. |
| [TrackParser](../app/src/main/java/com/scenevibe/tvcompanionpoc/TrackParser.java) | Validation Video commune LAN/Cloud, IDs uniques et bornes. Il reste la frontière sémantique Video, sans parser générique supplémentaire. |
| [MediaSyncedTrackScheduler](../app/src/main/java/com/scenevibe/tvcompanionpoc/MediaSyncedTrackScheduler.java) | Éligibilité, ancre, consommation, rearm, DUE et fenêtres MEDIA. Dépend de `ScheduledTrack.Event`, de la sonde Android, de `PlaybackState`, du matcher et de `Log`. Il ne commande pas le player. |
| [MediaSessionProbe](../app/src/main/java/com/scenevibe/tvcompanionpoc/MediaSessionProbe.java) | Acquisition passive Android, sélection de session, observation et estimation de position. Échantillonnage actuel 1000 ms sur le owner Android. |
| [MediaIdentityMatcher](../app/src/main/java/com/scenevibe/tvcompanionpoc/MediaIdentityMatcher.java) | Décision Video d'identité ; package/plateforme, mediaId exact puis fallback titre/sous-titre et durée déjà qualifié. |
| [SceneRuntimeController](../app/src/main/java/com/scenevibe/tvcompanionpoc/SceneRuntimeController.java) | Index des scènes par id, garde de génération, ownership visuel, préflight, hide/show. Pas d'horloge ; son callback DUE dépend encore de `ScheduledTrack.Event`. |
| [OverlayManifest](../app/src/main/java/com/scenevibe/tvcompanionpoc/OverlayManifest.java) et [VideoOverlayManifestBridge](../app/src/main/java/com/scenevibe/tvcompanionpoc/VideoOverlayManifestBridge.java) | Payload graphique borné et bijection exacte commentaire/scène avec égalité des temps, source Video et clock MEDIA. Hors core temporel. |
| [SceneRenderer](../app/src/main/java/com/scenevibe/tvcompanionpoc/SceneRenderer.java) | Fenêtre native, préflight des ressources locales et animations. Hors scheduling. Aucun resolver réseau ajouté. |
| [OverlayRenderer](../app/src/main/java/com/scenevibe/tvcompanionpoc/OverlayRenderer.java) / [DisplayCountdown](../app/src/main/java/com/scenevibe/tvcompanionpoc/DisplayCountdown.java) | Chemin legacy : compteur de durée visuelle qualifié, freeze-aware, distinct de l'expiration des scènes manifestées. Ce comportement doit rester inchangé. |
| [OverlayService](../app/src/main/java/com/scenevibe/tvcompanionpoc/OverlayService.java) | Unique owner live ; restore avant démarrage probe/Cloud, sélection exclusive legacy/manifesté, handoff de révision, arrêt/reset. |
| [VideoPreparedState](../app/src/main/java/com/scenevibe/tvcompanionpoc/VideoPreparedState.java) et handlers Video | État mémoire validé et lié au canonical/handler/requirements. Les handlers possèdent la transformation sémantique, la cohérence et ARM. Aucun nouveau codec. |
| Socle [installation](../app/src/main/java/com/scenevibe/tvcompanionpoc/installation/) | `ExecutionRequirements`, `TvCapabilities`, `InstallRequest`, `PreparedInstallation`, `InstallationHandler`, registry statique, store, backend et installer conservent leurs responsabilités M4. |
| [CloudV1InstallationAdapter](../app/src/main/java/com/scenevibe/tvcompanionpoc/CloudV1InstallationAdapter.java) | Wire v1, sérialisation actuelle et binding `finalTrackId` séparé du request. Hors core temporel. |

La dette précise est le mélange des **temps** et du **contenu Video/Android** dans
`ScheduledTrack.Event`, puis l'utilisation de ce type dans les callbacks du
scheduler et du controller. Il faut extraire cette dépendance sans déplacer le
parser, le matcher, le rendu, la révision ou la persistance dans le core.

## Contrat de caractérisation : les vingt règles

Notation : `p` est la position choisie, `last` la dernière ancre valide ;
`C` est l'ensemble des IDs consommés et `W` la map des fenêtres positives.
« Source » signifie caractérisation statique, pas nouveau test exécuté ni PASS
physique. Les tests cités existent à la base ; les frontières non couvertes
doivent devenir des cas différentiels B/C avant toute intégration D.

| # | Comportement exact à conserver | Preuve actuelle / futur verrou |
| ---: | --- | --- |
| 1 | Tri croissant par `startMs`, stable à égalité : ordre initial conservé, sans tri secondaire par id. | `ScheduledTrack` / corpus B égalités et entrée non triée. |
| 2 | L'exécution exige `MediaIdentityMatcher.matches(...) == true`. Le mediaId exact est la preuve la plus forte ; le fallback qualifié existe aussi. | `MediaSyncedTrackSchedulerIdentityTest` / adapter C et journal d'identité. |
| 3 | Session indisponible ou matcher false : inéligible, ancre −1, `W` vidé, aucun DUE ; le chemin visuel est retiré. Si déjà inéligible, pas de nouvelle émission INELIGIBLE. | Tests Identity/Expiry et controller / corpus C unavailable et mismatch répétés. |
| 4 | Première ancre valide : consommer les événements dont `startMs < max(0, p−2000)`. Si PLAYING, un DUE au maximum, puis expiration. | `skipTooOld` / B/C première ancre, cutoff et position négative. |
| 5 | `MAX_LATE_MS = 2000` ; une lateness exactement 2000 reste candidate, 2001 est trop ancienne. | Source / B/C aux limites, sans fenêtre artificielle supplémentaire. |
| 6 | Forward seek seulement si `p−last > 5000` ; delta exactement 5000 suit le chemin ordinaire. | Source + `forwardSeekPastWindowEndExpiresScene` / B/C 4999, 5000, 5001. |
| 7 | Backward seek seulement si `p−last < −2000` ; delta exactement −2000 suit le chemin ordinaire. | Source + `backwardSeekBeforeStartReArmsScene` / B/C −1999, −2000, −2001. |
| 8 | Forward : consommer tous les `startMs <= p`, y compris à l'atterrissage ; mettre l'ancre à jour ; expirer les fenêtres échues, puis return sans DUE. Ce chemin expire même hors PLAYING. | Source / C forward PLAYING et PAUSED, atterrissage exact. |
| 9 | Backward : réarmer seulement les consommés dont `startMs >= p`. Retirer leur fenêtre et émettre EXPIRE si elle existait ; pas de DUE ni d'expiration ordinaire dans ce snapshot. | ExpiryTest / C rearm exact et seek à l'intérieur d'une fenêtre. |
| 10 | Un consommé ne rejoue pas sans rearm. Le chargement/clear réinitialise la consommation ; une perte d'éligibilité seule la conserve. | Source et tests rearm / C répétitions, load/clear, replay. |
| 11 | Durée positive : fenêtre créée avant DUE, expiration si `startMs + durationMs <= p`. En lecture ordinaire PLAYING, DUE précède EXPIRE. | `sceneExpiresWhenMediaWindowElapses` / C end−1/end/end+1 et DUE puis EXPIRE dans le même snapshot. |
| 12 | Durée <= 0 : DUE possible mais aucune fenêtre ni auto-expiration par le scheduler. Ne pas inventer une durée par défaut. | `nonPositiveDurationHasNoWindow` / B/C 0 et négatif synthétiques. Le parser Video continue de refuser ces durées. |
| 13 | Pause FREEZE : PLAYBACK(false, freeze) émis ; les snapshots ordinaires non-PLAYING n'exécutent ni DUE ni expiry. Aucun nouveau timer wall-clock. La branche seek conserve toutefois ses règles 8/9. | `pauseFreezeDoesNotExpireSceneWhilePaused`, controller et countdown / C pause avec position fixe puis seeks. |
| 14 | Resume : PLAYBACK(true, freeze), temps média courant et règles ordinaires/seek existantes ; pas d'horloge de rattrapage nouvelle. | Tests pause/countdown / C reprise et long écart d'observation. |
| 15 | Perte d'éligibilité : hide immédiat côté owner, `W.clear()` sans EXPIRE tardif, `C` conservé et ancre −1. | `eligibilityLossDropsPendingWindowWithoutLateExpire`, controller / C journaux et D visuel. |
| 16 | Retour : ELIGIBLE, ancre −1, puis rearm des `startMs >= p` si position connue, avant PLAYBACK. Retour avec position négative : pas de rearm ; la prochaine position valide ne répète pas ce rearm d'éligibilité. | Source / C retour connu/inconnu et IDs consommés. |
| 17 | `renderDue()` consomme/rend le premier candidat seulement puis return. D'autres candidats, même au même timestamp, attendent un autre snapshot PLAYING et restent soumis à MAX_LATE. | Source / B/C timestamps égaux, proches et retard progressif. |
| 18 | Aucune commande de lecture, pause, seek, transport player. | Sources scheduler/probe/controller / boundary B–D. |
| 19 | Scheduler passif : avance uniquement depuis observations, load, clear, unavailable. L'estimation Android existante reste à la frontière. | Sources scheduler/probe / core sans horloge, tests C séquences seules. |
| 20 | Un seul moteur temporel live MEDIA, sans seconde ancre ou horloge d'exécution. Compteur legacy, estimation probe et animations natives existantes restent hors core et inchangés. | Source/service/countdown / D owner unique et exclusion WALL. |

Deux formulations doivent être interprétées à partir du code réellement qualifié.
« Exact media identity » ne signifie pas inventer une nouvelle politique mediaId-only :
le matcher actuel accepte aussi certains titres/sous-titres avec la tolérance de
durée, **même si un mediaId non vide différent est présent**. M5 ne doit ni élargir
ni durcir ce matcher. « Aucun second clock » interdit d'ajouter une nouvelle
autorité temporelle ; supprimer le compteur legacy qualifié ou l'estimation de
la sonde changerait le runtime existant et n'est pas autorisé par cette extraction.

## État machine et ordre observable

Le futur `MediaCalendarScheduler` reproduira les états suivants, sans nouvelle
politique de scheduling :

| État / entrée | Transition et effets ordonnés |
| --- | --- |
| N'importe quel état + `load(calendar)` | INELIGIBLE émis avant remplacement ; nouveau calendrier, `C/W` vides, ancre −1, inéligible. Chargement reste ARM, jamais visible. |
| N'importe quel état + `clear()` | INELIGIBLE émis même si déjà vide ; aucun calendrier, `C/W` vides, ancre −1. |
| Aucun calendrier + observation/unavailable | Aucun effet. |
| Observation `null` | Aucun effet, même avec calendrier. Ne pas convertir null en unavailable. |
| Calendrier + unavailable ou observation inéligible | Transition règle 3/15 ; aucun PLAYBACK ni DUE. |
| Inéligible + observation éligible | ELIGIBLE, ancre −1, rearm éventuel avec EXPIRE en ordre de calendrier, puis PLAYBACK. |
| Toute observation éligible | PLAYBACK à **chaque** snapshot, même sans changement de state. `p < 0` arrête ensuite le traitement sans effacer une ancre précédente. |
| Première position valide | Ancre, skipTooOld ; si playing : renderDue puis expireElapsed. |
| Position valide suivante | PLAYBACK est déjà émis ; choisir forward, backward ou ordinaire selon les seuils stricts du tableau. |

Les callbacks font partie de la transition : les mutations et émissions gardent
l'ordre de l'ancien moteur. Un événement tardif dont la fenêtre est déjà terminée
peut produire DUE **puis EXPIRE dans le même snapshot**. Il ne faut pas supprimer
ce DUE au motif qu'il semblerait inutile. Les fenêtres peuvent inclure des IDs
qui ne sont plus visuellement actifs : seul le controller décide si une expiration
correspond au visuel courant. Le scheduler ne connaît pas le visuel.

`expireElapsed()` itère actuellement `HashMap.values()`, collecte les échéances,
puis retire et émet dans cet ordre. Cet ordre n'est pas un tri temporel garanti
entre JVM/Android différents. M5 doit conserver la même structure et les mêmes
mutations pour obtenir le même ordre dans un environnement donné. Comparer les
journaux **sans tri, regroupement ou conversion en set**, y compris collisions
d'IDs et resize de map. Un ordre canonical nouveau serait une décision produit
future ; il ne peut pas être introduit pour faire passer la migration.

Un delta très grand est traité comme un seek, même si la cause est un trou
d'échantillonnage. Le core n'ajoute ni mesure de temps écoulé ni classification
« vrai seek utilisateur ». Les valeurs hors PLAYING, notamment FAST_FORWARDING
et REWINDING, restent non-playing pour la branche ordinaire, même si la sonde
estime leur position. Aucun changement de cette distinction pendant M5.

## Modèles purs proposés

Le namespace prévu est `com.scenevibe.tvcompanionpoc.calendar`. Ses types seront
des valeurs Java immuables ou un moteur mémoire sérialisé. Aucun type graphique,
produit, transport ou persistance ne franchit cette frontière.

| Type futur | Données / responsabilité strictement nécessaires |
| --- | --- |
| `SceneEvent` | `eventId`, `startMs`, `durationMs` seulement. ID opaque exact et stable dans le calendrier, sans texte, Bitmap, primitives, sourceId, FinalTrack ni mediaId. |
| `MediaCalendar` | Liste immuable, stablement triée de SceneEvent et booléen générique `freezeOnPause` à transmettre dans PLAYBACK. Ne possède ni consommation, fenêtre, révision, codec, artifact, identité média ni stockage. |
| `MediaObservation` | `eligible`, `positionMs`, `playing`. Position déjà choisie par l'adapter ; toute valeur négative signifie position indisponible et garde le comportement décrit plus haut. Aucune estimation dans le core. |
| `MediaCalendarScheduler` | Calendrier actif, `C`, `W`, dernière position, éligibilité, et un token de génération externe. Entrées load/clear/observation/unavailable ; aucune activité autonome. |
| Sink abstrait | `ELIGIBLE/INELIGIBLE`, `PLAYBACK(playing, freezeOnPause)`, `DUE(eventId)`, `EXPIRE(eventId)`, chacun lié à la génération capturée. Aucune référence de payload ou d'objet Android. |

Le booléen pause décrit la politique déjà validée et passe à la frontière de
rendu ; il n'introduit pas de minuterie. Les requirements M4 restent MEDIA avec
FREEZE pour le profil manifesté et FREEZE/CONTINUE pour le legacy qualifié.
L'adapter Video réalise ce mapping sans importer les classes d'installation dans
le core. Ne pas dupliquer un registry de capacités dans `MediaCalendar`.

Le domaine de ce core M5 est MEDIA uniquement. Une interface WALL vide ou un
provider d'horloge universel ne sont pas nécessaires à l'extraction constatée ;
ils sont donc différés à M6. `TvCapabilities.current()` reste MEDIA-only et
`supportsWallClockExecution() == false`. Un manifest parsable WALL ne peut pas
être projeté/armé comme calendrier exécutable.

### Bornes et ownership mémoire

Le budget M5 est celui du Video qualifié : 1..256 événements, IDs uniques non
vides <=128 unités UTF-16, starts 0..43 200 000 ms, durée positive <=60 000 ms.
Les IDs déjà validés sont copiés sans nouvelle normalisation, trim ou substitution.
La liste est copiée et exposée immuable ; aucun payload n'est copié dans le core.
`C` et `W` sont bornés par le nombre d'événements, pas par la durée de lecture.
Le core conserve la branche durée <=0 pour le corpus historique synthétique,
sans calcul d'échéance dans cette branche. Cela **n'élargit pas** le parser Video,
dont les durées admises restent 1000..60 000 ms.

Une durée positive additionnée à un start valide reste sans overflow dans ces
bornes. L'observation conserve les positions non négatives fournies actuellement,
sans les plafonner au média attendu ; le scheduler ancien ne fait pas ce clamp.
Le delta entre deux ancres non négatives tient dans un long. Toute position
négative est traitée avant le delta/cutoff. Aucune allocation proportionnelle
aux millisecondes, aucun asset, aucune copie d'artifact ni index persistant.

Le calendrier est une **projection mémoire de l'état déjà validé**. Il ne
représente pas une seconde installation ni un package sérialisable. Il ne peut
être utilisé comme cache d'assets, base de données, objet Cloud ou autorité de
révision. Des futurs produits ne sont pas ajoutés pour démontrer sa neutralité.

## Les quatre frontières et la projection Video

1. **Acquisition Android** : `MediaSessionProbe` reste passive. Garder sélection
   actuelle de la première session, privilégiant la première PLAYING, fréquence,
   callbacks unavailable et estimation actuelle. Choisir `estimatedPositionMs`
   si >=0, sinon `positionMs`, exactement comme l'ancien scheduler. La sonde
   estime PLAYING/FAST_FORWARDING/REWINDING depuis l'âge et la vitesse ; le core
   ne reçoit ni âge, vitesse, `PlaybackState` ni horloge Android.
2. **Identité Video** : un adapter appelle l'actuel `MediaIdentityMatcher` sans
   nouvelle heuristique. Package exact et plateforme `prime_video`, priorité du
   mediaId exact, fallback existant et tolérance actuelle 180 000 ms restent
   inchangés. Le core reçoit seulement la décision booléenne. Une acquisition
   indisponible transmet unavailable ; un snapshot null reste le no-op actuel.
3. **Calendrier MEDIA** : consommer/réarmer/déclencher/expirer des IDs avec le
   contrat ci-dessus, en mémoire, sans connaître leur destination visuelle.
4. **Rendu** : le handler et le owner retrouvent le payload par ID, puis le
   controller ou le chemin legacy exerce l'ownership graphique actuel.

Le handler Video projette son **état préparé et validé**, en mémoire :

| Donnée validée Video | Projection / lieu où elle reste |
| --- | --- |
| `comments[].id/startMs/durationMs` | SceneEvent, valeurs exactement conservées et tri stable. |
| `pauseFreezesDisplay` / requirements | Booléen pause du calendrier, mêmes profils acceptés. |
| Target package et `mediaIdentity` | Binding Video de l'adapter, jamais dans SceneEvent/core. |
| Texte, Bitmap LAN éventuel, scènes du manifest | Map Video `eventId -> payload` / index du controller, jamais dans le calendrier. |
| Relation commentaire/scène | Bijection et temps exacts déjà vérifiés par `VideoOverlayManifestBridge`, sans nouvelle correspondance heuristique. |
| Canonical, codec, handler, révision | `PreparedInstallation` et socle M4 ; aucune nouvelle copie persistante. |
| Génération | Owner de l'activation/controller ; token opaque capturé pour les callbacks, jamais décision de révision du core. |

Projection **une fois par état préparé**, construite par le handler à partir du
`ScheduledTrack` déjà parsé. Le futur typed prepared state peut retenir ce
calendrier immuable et son binding Video. Ne pas reparser les artifacts dans
l'adapter session, le scheduler ou le controller. L'étape restore M4 reconstruit
l'état préparé à partir du readback canonique ; c'est cette projection restaurée,
et non la projection originale du caller avant commit, qui sera armée.
Cloud reste text-only via la préparation existante ; le chemin LAN qualifié
peut garder ses Bitmaps dans son binding Video extérieur au core.

Le controller reçoit à terme `onEventDue(eventId, generation)` et
`onEventExpired(eventId, generation)`, en plus de l'éligibilité et PLAYBACK.
Il lookup la scène par ID, conserve son préflight et ses gardes, hide l'ancien
avant de show le nouveau. Il ne lit pas les temps de SceneEvent et ne possède
pas de minuterie. L'overload `ScheduledTrack.Event` n'est pas une dépendance
à conserver indéfiniment si M5 prétend l'avoir supprimée. La migration des tests
doit être un changement de forme d'entrée recensé, sans suppression d'assertions.

Le controller actuel n'est **pas** l'autorité de rejet d'une révision inférieure :
`replaceRevision()` recharge un manifest. Le rejet stale/same-revision reste dans
`PackageInstaller`, et le choix manifesté exige la révision active exacte du
owner. La garde de génération du controller empêche les callbacks obsolètes de
ressusciter/cacher une scène ; elle ne remplace pas les guards M4.

## Sérialisation, génération et unique owner

Le core est synchrone et appelé sur **un seul owner sérialisé**. Il ne dépend
pas de Looper, ne crée pas de thread, n'emploie pas de timer et n'enqueue pas une
seconde boucle d'exécution. Le owner Android actuel adapte les callbacks vers son
thread et reste responsable du chargement, du clear et du handoff de révision.

L'ancien scheduler synchronise ses méthodes publiques et délivre ses callbacks
pendant la transition. Le nouveau moteur doit garder l'ordre mutation/callback,
y compris les frontières d'échec et de réentrance ; il ne doit pas calculer tous
les effets, terminer toute mutation, puis les livrer en bloc sans preuve
d'équivalence. Un sink mémoire en test enregistre les émissions à ces mêmes
points. Un sink de production ne déclenche pas de nouveau load/clear réentrant
hors des règles de l'owner actuel. Les cas de callback interrompu et de handoff
sont caractérisés dans le harness avant cutover ; aucun retry nouveau automatique.

Le token d'activation est externe au calendrier et capturé **à la production**
d'un callback. Toute frontière différée transporte ce token ; elle ne relit pas
la génération courante pour retagger un ancien événement. Le dispatch vérifie
le binding actif de l'owner puis la génération du controller. Clear/unload,
remplacement et arrêt rendent les anciens callbacks inutilisables. La génération
ne confirme pas une installation et ne peut jamais déclencher un ACK.

Le token de l'activation Video n'a pas besoin d'être le compteur interne du
controller. Dans le profil manifesté, l'owner lie ce token à la génération
effective lue sur le controller après `armPreparedManifest`, avant la sélection active finale.
Ce binding reste fixe pour l'activation ; il est invalidé à son retrait. Ainsi
l'ordre ARM M4 (retire -> load prepared track -> arm manifest -> select revision)
ne change pas, et il n'est pas nécessaire de prédire la prochaine génération du
controller. Aucun snapshot ne peut s'intercaler dans cette séquence sur l'owner
sérialisé. Le profil legacy garde un binding d'activation sans controller.

Pendant B/C, les nouveaux types et le harness ne sont pas câblés à un chemin
live. L'oracle et le candidat n'ont que des sinks d'enregistrement ; aucun appel
au renderer, à un service, au store ou à Cloud n'est permis par le harness.
Pas de shadow de production nécessaire dans ces phases. Un éventuel shadow
ultérieur doit rester strictement non-rendering, sans ARM/write/ACK/network.

Pendant D, `MediaSyncedTrackScheduler` peut devenir une **façade de compatibilité
Video** conservant les ports M4 actuels mais délégant à une seule instance du
core générique. Ses méthodes ne conservent alors aucune seconde copie de
consumed/windows/ancre ni de l'ancien algorithme. L'ancien moteur complet survit
seulement comme oracle de test figé. Aucun flag ne doit laisser deux moteurs
live rendre le même événement. Cette façade évite un changement gratuit des
interfaces d'installation M4 tout en supprimant l'autorité temporelle legacy.

## Persistance, ACK et reconstruction après reboot

M5 laisse intégralement au socle M4 : révision, validation, prepare, commit,
readback, restore, ARM, durable ACK, codecs/handlers, snapshot et transport Android.
Le calendrier n'a ni API write, ni API ACK, ni codec de sauvegarde.

Au démarrage : le service lit `InstallationStore`, sélectionne le handler durable
exact, restore via `PackageInstaller` sans réécriture, reconstruit la projection
mémoire depuis l'état validé, puis ARM sur l'owner. Seulement ensuite démarrent
la sonde et Cloud. Le calendrier recommence avec `C/W` vides et sans ancre ; ce
comportement reproduit le reload qualifié, sans persister un curseur média.
L'ARM reste invisible jusqu'au premier DUE éligible. Aucun Send ni redownload
n'est nécessaire à la reconstruction locale.

Même révision : restaurer les bytes/bindings durables et zéro repersist ; les bytes
incoming n'ont pas d'autorité. Stale : rejet avant parser/projection/mutation live.
Nouveau : validate -> prepare -> commit unique -> readback exact -> restore ->
ARM. Une panne ARM conserve le nouveau durable pending et l'ancien ACK confirmé,
sans rollback ou synthèse d'ACK. Un cache CORRUPT reste fermé, sans trim/réparation
ou fallback au tuple historique. Le transport correctif
`scenevibe.os.android-preference.v1:` + codec logique inchangé + `!` reste intact.

| Surface M4 protégée | Chemin de non-régression M5 |
| --- | --- |
| Store, snapshot, transport exact/Unicode, codecs et handlers | Identité des blobs hors migration autorisée, tests Store/Backend/codec, native durabilité API 31/35. Aucune migration de données. |
| Installer et révision / même / stale | `PackageInstallerTest`, `M4PhaseEVideoInstallerTest`, handoff D avec projection depuis restore réel et zéro write au reload. |
| Cloud v1, finalTrackId, ACK body/ordre/persistance | `M4PhaseFAdapterTest` et suites Cloud retenues ; aucun type core dans le wire ou ACK. |
| Reboot et ARMED != visible | `M4PhaseGStartupTest`, `OverlayArmedNotVisibleTest`, native puis Sony ciblée D. Restore local avant probe/Cloud conservé. |
| Un visuel, pause, seek, identité, générations, préflight | Journaux différentiels C, tests controller/renderer/countdown conservés, séquences intégrées D puis gate Sony ciblé. |
| Pairing, installation identity, credentials, reset/disconnect, autostart | Sources/guards M4 inchangés, suites retenues, upgrade Sony D sans clear/reset. |
| Signer, permissions, foreground service type, Consumer/LAN | Aucun drift build/manifest/ressources ; gates build/lint/signature/smoke retenus, certificat stable vérifié. |

## Oracle différentiel et corpus

B fige l'**ancien code réel** de BASE_TV_M5, avec commit et blobs inventoriés :
`ScheduledTrack`, scheduler, matcher et sonde/snapshot pour l'adapter de test.
L'oracle peut compiler dans un espace de test isolé avec les types Android
mockables nécessaires. Ce n'est pas une réécriture prétendument équivalente de
l'ancien algorithme. Le candidat core compile séparément sans classpath Android.
Les adaptations test-only sont minimales, explicitement listées et réversibles ;
aucune modification de constante, branche, ordre d'émission ou assertion oracle.

Le harness reçoit un calendrier Video valide et une séquence finie d'entrées
identiques : load/clear, snapshots Android normalisés par la frontière qualifiée,
unavailable, et handoffs externes de génération/révision. Il enregistre, dans
l'ordre et avec le token d'activation :

`INELIGIBLE`, `ELIGIBLE`, `PLAYBACK(playing, freeze)`, `DUE(eventId)`, `EXPIRE(eventId)`.

La comparaison est exacte, y compris les répétitions et les absences d'effet.
Pas de suppression de PLAYBACK, debounce, tri, tolérance temporelle ajoutée ni
fusion de DUE/EXPIRE. Les IDs restent exacts. Les journaux temporels sont séparés
du journal d'ownership visuel du test de controller : aucune normalisation des
callbacks temporaires ne peut cacher une double émission. Une divergence bloque
le cutover ; elle n'est pas résolue en modifiant l'oracle ou en sautant une fixture.

| Famille obligatoire B/C | Cas déterministes à comparer |
| --- | --- |
| Modèle / tri | Entrée non triée, égalités conservant l'ordre initial, un et 256 événements, IDs uniques, collisions HashMap, remplacement de calendrier. |
| Première ancre / retard | Start exact, p=0, lateness 0/1999/2000/2001, événement trop ancien, fenêtre déjà terminée : DUE puis EXPIRE. |
| Position absente | Snapshot null no-op, raw/estimated négatifs, premier match inconnu puis connu, observation inconnue après ancre existante. |
| Lecture / répétition | Snapshots normaux et même position répétés ; un DUE par snapshot ; consumed jamais rejoué sans rearm. |
| Pause / resume | FREEZE et CONTINUE transmis, aucune expiry ordinaire PAUSED même si p varie, resume, long gap classifié par delta existant. |
| Forward | 4999/5000/5001, start exactement à l'atterrissage, skip de tous les traversés, expiry positive, seek pendant PAUSED. |
| Backward / replay | −1999/−2000/−2001, avant start/à start/à l'intérieur de la fenêtre, EXPIRE de rearm, replay après prochain snapshot PLAYING. |
| Éligibilité / identité | Wrong package/platform/épisode, exact ID, fallback avec ID absent ou différent, durée tolérée/refusée ; unavailable et mismatch répétés ; retour connu/inconnu. |
| Durée / expiry | 0 et négative synthétiques, positive, end−1/end/end+1, plusieurs fenêtres simultanées et ordre brut de map, EXPIRE d'ID non visible. |
| Concurrence logique | Load et clear avec visuel actif, ancienne génération DUE/EXPIRE, même/stale/nouvelle révision par l'installer réel, arrêt puis callback, préflight rejeté. |
| Reconstruction | Restore pending/confirmed, manifesté/legacy, tuple historique/générique, mêmes bytes/IDs/temps/pause ; reconstruction zéro write et aucun DUE avant observation éligible. |

B établit l'oracle, les fixtures et le journal sans prétendre comparer un nouveau
moteur encore absent. C doit exécuter la comparaison complète candidat/oracle.
Pour les expirations simultanées, comparer aussi oracle/candidat dans le même
environnement Android API 31 puis 35 : les gates de durabilité M4 hérités ne
prouvent pas à eux seuls l'ordre de `HashMap` du nouveau moteur.
Le harness doit être sensible à un contrôle négatif test-only : par exemple une
candidate dont le seuil forward devient `>=5000`, ou qui rend tous les DUE par
snapshot, doit effectivement produire une divergence. Aucune telle mutation
n'entre dans les sources de production.

## Gates de frontière et de compatibilité

Le core doit compiler seul sur JVM avec JDK et tests, sans Android ni org.json.
Une compile Gradle avec framework mockable ne suffit pas à démontrer cette frontière.
Autoriser seulement les types core et les collections/valeurs Java indispensables.
Inspecter les imports, signatures, champs, corps exécutables et sorties compilées :
les noms de façade ne suffisent pas si un Object opaque transporte un payload.

Interdire dans le core : `android.*`, Bitmap, MediaSession/PlaybackState,
View/WindowManager, OverlayManifest/SceneRenderer, tous types Video/FinalTrack,
CloudControlClient/CloudProtocol, credentials, installation/persistance M4,
I/O/fichiers/preferences, networking, réflexion/dynamic loading, clock APIs,
threads/timers/executors et contenu graphique. Pas de dependency injectée
« générique » donnant indirectement accès au store, au réseau ou au player.

Les suites retenues restent obligatoires. Tout futur changement d'API test requis
par la disparition de `ScheduledTrack.Event` doit inventorier l'adaptation exacte
et prouver la conservation des assertions métier et fixtures. Il ne faut ni
geler l'ancien couplage pour contourner les tests, ni affaiblir la qualification
M4. Les gates de provenance M4 figés devront être prolongés explicitement pour
admettre les nouveaux fichiers et les seuls changements M5 approuvés, avec
comparaisons réversibles limitées et vérifications sémantiques sur les vraies
sources courantes. **Le blocage documentaire actuel est un préalable distinct** ;
aucune extension d'inventaire n'est implémentée dans cette Phase A docs-only.

| Frontière | Preuve attendue avant intégration |
| --- | --- |
| Handler -> calendrier | Projection fidèle des ids/temps/pause, tri stable, bijection, pas de reparse, reject WALL avant ARM, aucun nouveau codec. |
| Sonde / matcher -> observation | Exacte sélection de position/playing/éligibilité, null != unavailable, constantes et matcher conservés, aucun contrôle player. |
| Scheduler -> controller | ID et token seuls ; pas de temps/controller clock, même préflight et hide/show, stale DUE/EXPIRE ignorés. |
| Révision / génération | Same/stale décidés par installer, handoff owner exact, pas de retag, pas de double moteur/visuel, fautes ARM sans ACK. |
| Reboot | Reconstruction via readback/restore M4, Unicode exact, zéro repersist, ARMED invisible, une seule génération active. |
| Runtime intégré | Full JVM XML et inventaires réels, Python, lint, Consumer/LAN/Cloud builds, signer stable, smoke et native 31/35 retenus ; zéro skip supplémentaire. |

## Diagnostics et échecs

Les états de calendrier et effets sont testables sans `Log` ni diagnostics Android.
La frontière peut traduire des labels finis : `CALENDAR_LOADED/CLEARED`,
`MEDIA_ELIGIBLE/INELIGIBLE`, `CLOCK_ANCHORED`, `FORWARD_SEEK`, `BACKWARD_SEEK`,
`EVENT_DUE`, `EVENT_EXPIRED`. Cette taxonomy est proposée pour M5, pas ajoutée
au runtime en Phase A. Les diagnostics restent observationnels ; ils ne décident
ni l'éligibilité, ni une révision, ni une reprise/ACK.

En production, ne pas introduire de logs de payload, texte, mediaId/titre,
artifact, URL, secrets ou cause de parser. Les journaux différentiels à IDs
synthétiques sont test-only. Les erreurs de modèle ont un label borné, sans
copie d'entrée ; les erreurs d'installation gardent la vocabulary M4.

| Situation | Comportement requis |
| --- | --- |
| Projection/modèle invalide | Refuser en préparation selon les résultats M4 existants, sans nouvelle installation ou mutation visuelle. Restore invalide reste fermé ; ne pas récupérer avec un autre codec. |
| Session absente / identité refusée | INELIGIBLE si transition, hide par l'owner, abandon des fenêtres ; pas de retry réseau ou commande player. |
| Position inconnue | PLAYBACK/éligibilité selon le contrat puis arrêt temporel ; aucune horloge de substitution. |
| Callback ancienne génération / ID inconnu | Controller ignore ; ni show ni hide du nouveau visuel. |
| Préflight échoué | Ancien visuel retiré dans l'ordre actuel, aucun show partiel ni download de ressource. |
| ARM échoué / owner invalide | Socle M4 conserve durable pending, abort visuel actuel, pas d'ACK ni rollback. |
| Sink interrompu / exception | Ne pas ajouter catch/retry dans le core qui masquerait une divergence ; conserver les frontières de mutation/échec de l'oracle et le traitement actuel par l'owner. |
| Divergence différentielle | Gate FAIL, aucun cutover. Corriger le candidat ou revenir au dernier état qualifié ; pas de « meilleure » nouvelle politique. |
| Cache corrompu | Politique M4 CORRUPT, aucun calendrier reconstruit, aucune réparation implicite. |

## Découpage minimal M5

Le plan retenu est **A–D**, avec la qualification intégrée dans D. La façade
Video rend possible le cutover et la suppression de l'autorité temporelle
ancienne dans le même périmètre vérifiable. Aucune Phase E n'est nécessaire à
ce stade. Une dépréciation mécanique réellement restante devra être identifiée
après D ; elle ne doit pas servir à différer une preuve de non-régression ou le
gate Sony indispensable au M5 intégré.

| Phase | Travaux autorisés par son futur work order | Gates de sortie |
| --- | --- | --- |
| A, ce cycle | Deux documents, audit des sources/tests, aucune source/CI/build modifiée. | Auto-audit documentaire, base/branches vérifiées, qualification actuelle honnête, nouvelle PR Draft. Le défaut d'inventaire actuel empêche READY. |
| B | Modèles purs SceneEvent/MediaCalendar/observation, bornes, projection de test et oracle réel figé, harness déterministe. Aucun moteur candidat live. | Compile JVM sans Android, boundary core, immutabilité/tri/bornes, exact journal oracle, contrôles de provenance et inventaire préalablement réparés dans un périmètre autorisé. |
| C | Generic MEDIA scheduler et adapters session/identité ; toujours sans branchement live, sans WALL. | Corpus complet ancien/nouveau identique, contrôles négatifs de sensibilité, signatures/ownership/erreurs vérifiés, suites retenues et nouveaux cas comptés séparément. |
| D | Projection handler/restored state, callbacks controller par ID/token, façade Video à moteur générique unique, suppression de l'ancienne autorité live après preuve C. | Traces intégrées, guards M4 exacts, tous gates automatisés réels, build/signature/native, puis Sony ciblée sur l'APK exact ; aucune divergence ni défaut physique avant clôture M5. |

Le premier work order d'implémentation recommandé est **M5 Phase B — pure
SceneEvent / MediaCalendar models and frozen differential oracle**, sans
production cutover. Il reste bloqué par l'inventaire documentaire actuel ; il
ne faut pas commencer B dans ce cycle ni inventer une nouvelle phase pour
réparer cette qualification.

## Futur gate Sony ciblé, uniquement après D

A ne modifie aucun byte exécutable : aucun nouveau cycle Sony n'est requis ici
et aucun test JVM M5 ne constitue une qualification physique.

Après l'intégration D, qualifier l'APK stable exact (HEAD, sha APK et certificat
`f908bf564ed97ba67e02b1ebc89eb0239cf980752587f55eb9ec0419791a2e9c`) par upgrade
sans uninstall/clear/reset. Conserver l'identité installation/Cloud, autostart,
permissions et installation M4 existante. Observer restore du snapshot M4 avec
codec/handler/révision/ACK exacts et absence de migration/repersist. Si la fixture
M4 contient déjà les cinq Unicode, la réutiliser plutôt que la recréer.

Dans une séquence ciblée : vérifier ARMED sans visuel avant la première session
éligible/échéance, DUE/expiry MEDIA, pause/resume, forward suppression et backward
replay, sortie/retour Prime et un seul visuel ; exercer le remplacement pendant
un visuel et vérifier l'absence de resurrection d'ancienne génération avec les
gates automatisés et le résultat visible. Effectuer un vrai hard reboot sans
Send préalable, puis observer le restore local avant les callbacks Cloud et la
réapparition des commentaires persistés au DUE éligible, Unicode exact et logs
réussis sans CORRUPT/CACHE_FAILED/GENERIC_INVALID/crash/double owner.

Ce gate protège les surfaces réellement modifiées par D ; il ne rejoue pas les
vingt checks M4 sans justification. Same-revision reste testée automatiquement ;
sans trigger supporté, la règle NOT RUN du protocole M4 reste applicable au
contrôle physique, sans ajouter de bouton/endpoint ou modifier l'état privé.
Ne pas revendiquer une isolation réseau qui n'a pas été réalisée. Le protocole
du work order D devra distinguer source de restauration locale démontrée par
l'ordre des logs et éventuelle isolation ; aucune capability ou feature n'est
créée pour rendre le test exécutable.

## Exclusions et conditions d'arrêt

M6 reste propriétaire de Banner, WALL, anchors et programmation horaire.
M7 reste propriétaire des assets distants, HTTP/CDN, cache partagé, quotas,
prefetch et hashes. M8+ garde UX Banner/Language, APIs partenaires, SDK, Connect,
publication externe des capabilities, multi-tenant et abstractions MediaContext
non indispensables. Aucun de ces travaux n'est requis pour neutraliser le core.

M5 ne change ni Cloud wire/ACK/finalTrackId, ni durable snapshot/transport,
codec/handler, pairing/identity/credentials/reset, autostart/signature,
permissions ou type de foreground service. Si une implémentation exige l'un
de ces changements, une commande player, WALL, une nouvelle politique pause/
seek/replay, une heuristique d'identité supplémentaire ou une décision produit
nécessitant une qualification physique avant même de définir l'architecture,
**STOP et rapporter**. Aucun choix de ce type n'est nécessaire à l'architecture
retenue. Le défaut d'inventaire Python reste un blocage explicite de qualification,
pas une justification pour contourner ce périmètre.
