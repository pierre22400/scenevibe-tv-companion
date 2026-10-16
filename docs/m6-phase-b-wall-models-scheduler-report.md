# M6 Phase B — pure WALL models, temporal validation and scheduler

Date de préparation : 7 octobre 2026. GitHub est la source de vérité. Phase B construit uniquement le noyau WALL JDK pur ; les 68 contrats temporels et les gates locaux passent. Le PASS final exige ensuite les quatre workflows terminés et les résultats réellement exécutés sur le HEAD publié exact.

**Production SHADOW ; PR #16 OPEN / DRAFT / unmerged.** Aucun Banner live, capability WALL, driver Android, Cloud Banner ou nouveau Sony PASS n'est revendiqué. Phase C ne commence pas automatiquement.

## Identité et autorités

| Référence | Identité |
| --- | --- |
| HEAD de départ obligatoire | `92b77df8d62dc6098269f7d61c05f44f0aa0a188` |
| Tree de départ | `83ff488a1e1cb32f0966e77fcf41d596afe70d5b` |
| BASE_TV_M6 / main TV | `17cbe36ae99ac7f48aaf861e0d1feac702a9e521` |
| BASE_CLOUD_M6 / main Cloud | `5011c91aac61a0cc6dcc74c256a15b7dee03d785` |
| HEAD final / tree final | Manifestes content-addressed de PR #16 et du rapport final externe, après publication et audit GitHub |

Un commit ne peut contenir son propre SHA/tree sans changer cette identité. Ce rapport est donc la spécification et la preuve de préparation committées ; le rapport final externe et le manifeste courant de [PR #16](https://github.com/pierre22400/scenevibe-tv-companion/pull/16) donnent les SHA exacts, tous les runs/artifacts et les comptes effectivement exécutés sur le HEAD qui contient ces bytes. Aucun succès historique de `92b77df` n'est substitué à la qualification B.

Les deux autorités ont été relues intégralement au HEAD de départ : `docs/scenevibe-os-m6-banner-wall-clock-architecture.md` et `docs/m6-phase-a-architecture-report.md`, ainsi que le rapport post-M5 et ses seize conclusions. Elles restent byte-identiques et décrivent le snapshot Phase A réconcilié. L'état courant B est porté par le présent rapport et le manifeste de PR ; l'historique Phase A reste conservé.

WALL et MEDIA restent distincts. Les obligations d'owner commun, promotion pending/active, capture de génération, éligibilité du contrôleur, anchor, timer ticket, autostart, durable/ACK Banner et autorité Cloud demeurent C/D/E. Le POC Media Interlude reste séparé et n'est pas importé. La clôture M5, y compris `Last startup restore: -`, n'est pas réécrite.

## Delta exact depuis le HEAD de départ

**9 ajouts et 12 modifications, 21 chemins.** Aucun fichier existant sous `app/src/main/` n'est modifié. Les 61 fichiers de production/configuration hérités, dont 57 Java, restent byte-identiques ; seuls trois nouveaux fichiers Java WALL purs y sont ajoutés. Parmi les 278 blobs de départ, 266 restent directement identiques et douze se reconstituent par un inverse exact avant la chaîne historique.

| Action | Chemin |
| --- | --- |
| ADD | `app/src/main/java/com/scenevibe/tvcompanionpoc/wall/WallEvent.java` |
| ADD | `app/src/main/java/com/scenevibe/tvcompanionpoc/wall/WallCalendar.java` |
| ADD | `app/src/main/java/com/scenevibe/tvcompanionpoc/wall/WallCalendarScheduler.java` |
| ADD | `app/src/test/java/com/scenevibe/tvcompanionpoc/wall/M6WallContract.java` |
| ADD | `app/src/test/java/com/scenevibe/tvcompanionpoc/wall/M6WallCoreTest.java` |
| ADD | `tests/m6_phase_b_provenance.py` |
| ADD | `tests/test_m6_phase_b_boundary.py` |
| ADD | `.github/scripts/m6-phase-b-baseline.json` |
| ADD | `docs/m6-phase-b-wall-models-scheduler-report.md` |
| MODIFY | `tests/test_m4_phase_b_boundary.py` |
| MODIFY | `tests/test_m4_phase_c_boundary.py` |
| MODIFY | `tests/test_m4_phase_d_boundary.py` |
| MODIFY | `tests/test_m4_phase_e_boundary.py` |
| MODIFY | `tests/test_m4_phase_f_boundary.py` |
| MODIFY | `tests/test_m4_phase_g_boundary.py` |
| MODIFY | `tests/test_m4_sony_corrective_boundary.py` |
| MODIFY | `tests/test_m5_phase_b_boundary.py` |
| MODIFY | `tests/test_m5_phase_c_boundary.py` |
| MODIFY | `tests/test_m5_phase_d_boundary.py` |
| MODIFY | `tests/m5_phase_d_provenance.py` |
| MODIFY | `.github/scripts/m4-phase-a-test-summary.py` |

Les six inventaires de production M4 B à G et Sony/B M5 admettent les trois chemins Java littéraux. Les quatre inventaires complets admettent neuf additions littérales. Le gate D continue d'exécuter son assertion pour chaque blob non modifié par D : sa lecture normalise seulement les douze blobs B indépendamment pinnés, puis exige le hash historique ; aucune condition d'assertion n'est supprimée ou élargie. Le helper D appelle cet inverse B avant l'inverse de réconciliation. Le script de comptes conserve tout l'ancien inventaire et ajoute une seule famille exécutée `M6WallCoreTest: 68`.

Baselines historiques M4/M5 B/C/D, oracles, corpus, fixtures, scheduler/adapters MEDIA, Cloud/store/ACK, handlers/codecs, renderer, lifecycle, permissions, inputs build/signing et les quatre workflows restent inchangés. Aucun wildcard, gate supprimé, nouveau skip ou baseline historique réécrit.

## API et bornes finales

Les trois classes se trouvent dans `com.scenevibe.tvcompanionpoc.wall`, sans import MEDIA. Le JDK suffit. Le core n'importe que `java.util`, ne lit aucune horloge, ne programme rien, n'émet aucun callback et ne connaît aucun payload, Android, renderer, store ou ACK.

| Valeur / opération | Contrat |
| --- | --- |
| `new WallEvent(String eventId, long startEpochMs, long endEpochMs)` | Exactement trois champs privés finals : ID et endpoints ; validation avant soustraction |
| `eventId()`, `startEpochMs()`, `endEpochMs()` | Accès aux valeurs immuables, sans normalisation ni durée dérivée cachée |
| ID | ASCII `[A-Za-z0-9._:-]{1,128}`, conservé littéralement ; Unicode/whitespace/slash/control refusés pour ces IDs |
| Epoch | Entier `0..253402300799999`, pour endpoints, horizon et `nowEpochMs` |
| Durée d'événement | `250..3600000` ms inclus, `start < end` |
| `new WallCalendar(long horizonStartEpochMs, long horizonEndEpochMs, List<WallEvent> events)` | Horizon positif au plus `604800000` ms ; 1..256 événements non nulls, IDs uniques, fenêtres entièrement incluses |
| `events()` | Copie immutable, tri canonique par start puis ID ASCII ; l'ordre reçu ne décide jamais du gagnant |
| `horizonStartEpochMs()`, `horizonEndEpochMs()` | Endpoints UTC abstraits, sans timezone/DST ni renouvellement |
| `load(calendar, nowEpochMs, eligible)` | Remplacement explicite ; EXIT/CLEAR de l'ancien essai, puis sélection fraîche éventuelle, même à ID ou instance identiques |
| `evaluate(nowEpochMs, eligible)` | Une réévaluation synchrone de l'état civil courant ; entrée invalide refusée avant mutation |
| `clear()` | Retrait explicite EXIT/CLEAR au plus ; calendrier vide, aucun DUE |
| `Result.selected()` | `WallEvent` immutable sélectionné et éligible, ou null |
| `Result.effects()` | Liste immutable, au plus EXIT puis DUE ; chaque effet porte kind/ID et raison fermée pour EXIT uniquement |
| `Result.nextBoundaryEpochMs()` | Premier start/end strictement futur, ou `NO_BOUNDARY = -1` ; ce n'est pas un délai Android |

Les endpoints sont validés avant `end - start` : la différence de deux epochs dans le domaine est représentable en long. Aucun `start + duration` non vérifié, valeur epoch persistée, horizon infini ou débordement silencieux n'est nécessaire. Une liste d'entrée modifiée après construction n'altère pas le calendrier. Les effets restent valables après toute évaluation suivante.

L'éligibilité est une entrée de présentation explicite, indépendante de MediaSession. `false` retire l'essai sélectionné avec CLEAR tout en conservant le calendrier et ses frontières ; le regain sélectionne fraîchement la fenêtre encore active et peut rendre le même ID à nouveau. Une fenêtre expirée pendant l'inéligibilité n'est pas rejouée. La sélection mémorisée représente un essai DUE, sans preuve de succès de preflight/rendu ; le même candidat ne produit pas un retry à chaque heartbeat. Le futur driver C est responsable de n'utiliser l'entrée éligible qu'après promotion et de gérer les erreurs natives.

## Sélection, raisons et frontières

Activité exacte : `startEpochMs <= nowEpochMs < endEpochMs`. Parmi les fenêtres actives, retenir le début le plus récent, puis le plus petit ID ASCII lexicographique. Le scan porte au plus sur 256 événements. L'ordre canonique facilite l'inspection mais ne remplace pas les deux comparaisons explicites du scheduler.

Lors d'un changement, EXIT précède DUE dans la même liste synchrone. Seul l'ancien candidat sélectionné peut produire EXIT. Aucun cursor consumed, historique de lectures, batch d'expirations ou reprise des fenêtres ratées n'existe. Un saut arrière peut re-sélectionner une fenêtre déjà vue ; un saut avant peut sauter complètement une fenêtre.

| Raison EXIT | Sémantique effectivement portée par B |
| --- | --- |
| END | À l'epoch fourni, l'ancienne fenêtre a atteint/dépassé sa fin |
| SUPERSEDED | L'ancienne fenêtre est encore active, mais une fenêtre de priorité supérieure est choisie |
| CLOCK_REEVALUATED | L'epoch fourni est antérieur au start de l'ancienne fenêtre ; B sait qualifier ce retrait sans détecter une clock système |
| CLEAR | Clear/reload ou retrait explicite d'éligibilité ; ne prétend pas à la fin temporelle de la fenêtre |

La détection Android des corrections de clock, des signaux et de la dérive reste C. B ne possède pas de dernier epoch, seuil de saut, anchor, génération, timer ticket ou token d'installation. Revenir dans la même fenêtre sans changer le candidat ne crée pas d'effet supplémentaire.

La prochaine frontière est le minimum de tous les starts/ends strictement supérieurs à l'epoch reçu. Elle inclut conservativement une frontière de fenêtre perdante en overlap : la réévaluation peut rester idempotente. À une borne exacte, cette borne n'est plus future. Sans frontière, `-1` évite toute addition/sentinelle ambiguë dans le domaine autorisé. Le driver C devra relire l'heure et gérer heartbeat/retard/réveil ; cet epoch ne promet aucun réveil physique.

## Table de transitions qualifiées

Fixtures : A `[1000,5000)`, B `[2000,3000)` pour overlap ; A `[1000,2000)` pour les cas simples.

| Entrée / situation | Sélection et effets attendus |
| --- | --- |
| Avant start, `now=999` | Aucun candidat ; prochaine frontière 1000 |
| Start exact, `now=1000` | DUE A ; fenêtre active |
| Intérieur / évaluation répétée | A conservée ; aucun DUE répété |
| End exact 2000 / après end | EXIT A/END si sélectionnée ; sinon aucun effet |
| Fenêtres disjointes | Retrait à end, aucun candidat dans l'intervalle, puis DUE de la suivante |
| Fenêtres adjacentes à 2000 | EXIT A/END puis DUE B, deux effets seulement |
| Overlap à 2000 | EXIT A/SUPERSEDED puis DUE B |
| B expire à 3000, A toujours active | EXIT B/END puis DUE A à nouveau |
| Même start | Plus petit ID ASCII ; perdants sans DUE rétroactif ; fallback possible à la fin du gagnant |
| Réception à 1999 d'A `[1000,2000)` | DUE A avec end absolu toujours 2000 ; aucune durée totale relancée |
| Reload actif, même ID ou même instance | EXIT ancien/CLEAR puis DUE courant ; nouvelle évaluation ensuite idempotente |
| Load après toutes les fins | Aucun replay, aucun batch d'expirations |
| Saut avant A → C, B entièrement ratée | EXIT A/END puis DUE C ; aucun effet B |
| Saut arrière après fin d'A vers A | DUE A autorisé de nouveau |
| Saut arrière de B vers A avant start de B | EXIT B/CLOCK_REEVALUATED puis DUE A |
| Saut arrière avant toute fenêtre | EXIT ancienne/CLOCK_REEVALUATED, aucun DUE |
| Perte / regain d'éligibilité | EXIT/CLEAR puis DUE courant au regain, y compris même ID ; pas de replay si déjà expiré |
| Clear / clear répété / empty evaluate | EXIT/CLEAR au plus, puis zéro effet ; aucune frontière |
| Epoch MAX, fin exacte MAX | Fenêtre inactive et aucune frontière future |
| Entrée now/calendar invalide | IllegalArgumentException fixe ; ancien état inchangé |

## Tests exécutés et sensibilité négative

Le contrat standalone compile les trois vraies sources avec classpath/sourcepath vides et un runner de tests sans JUnit. Le wrapper JUnit exécute exactement les mêmes 68 noms comme 68 cas séparément comptés. Pas de hooks production, stubs Android, parsing produit ou oracle MEDIA copié.

Les 68 contrats couvrent les bornes d'IDs/epochs/durées/horizon/count, nulls/duplicates/contenance, immutabilité, starts/ends exacts, overlap/tie/fallback, late load/reload, sauts ±, clear, éligibilité, idempotence, invalidité sans mutation et prochaine frontière. Ils comprennent toutes les 24 permutations de quatre fenêtres face à 13 epochs explicites (**312 sélections**) et tous les 81 couples de neuf epochs face aux quatre combinaisons d'éligibilité (**324 transitions**). Deux instances recevant la même séquence explicite produisent des résultats identiques.

| Mutant compilé temporairement | Témoin exact qui doit échouer |
| --- | --- |
| start inclusif → exclusif | `startExact` |
| end exclusif → inclusif | `endExact` |
| newest start → oldest start | `overlap` |
| ID minimum → maximum | `equalStart` |
| DUE avant EXIT | `adjacent` |
| Retrait du prédicat end, replay de fenêtres passées | `noPastReplay` |
| Cursor monotone empêchant la re-sélection après saut arrière | `backwardReselect` |

Chaque original doit passer ; chaque mutant doit compiler puis produire l'AssertionError du témoin nommé. Une compilation échouée ne suffit pas comme preuve de détection. Les copies sont temporaires ; aucune mutation ni adaptation du core n'est committée pour ce mécanisme.

## Provenance et gates locaux

La nouvelle baseline **M6 B distincte** fige 278 starting blobs du HEAD exact, les neuf additions, leurs huit hashes (hors baseline auto-référentielle), les douze admissions et le bucket JUnit. Le hash canonique de l'inventaire initial est pinné indépendamment : `13e36b17bc273bc70b09c7f640a45f495232a55e4fc9371263dbefcc2f58c088`.

Chaque admission a un hash complet avant et après et des contextes littéraux uniques. Le helper possède indépendamment les douze couples de hashes. Unknown blob est refusé avant tout inverse ; chaque inverse restitue le blob initial entier. Les deux formes historiques des quatre boundary tests de clôture Sony restent reconnues exactement. Aucune exception n'existe pour un fichier runtime ou une fixture.

Chaîne : **M6 B → réconciliation M6 → clôture Sony M5 → D → C → B M5 → M4**. Les guards comportementaux lisent toujours les vraies sources. Le gate M6 B exige les bytes courants pour toutes les additions et tous les retained paths non admis. L'inventaire entier reste fini ; seuls les `.pyc` générés sous `__pycache__` sont exclus de l'inventaire de sources, pas des chemins documentaires ou Java.

Contrôles négatifs de provenance réellement exécutés : 60 mutations de blobs (5 × 12), 36 inverses absents/ambigus/inexacts (3 × 12), 12 retargetings de nouveaux pins refusés et un document temporaire rejeté par les quatre vrais inventaires hérités. Toutes les fonctions Python conservent une docstring et un teaching banner après imports.

Commandes locales :

```sh
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s tests
git diff --check
```

Préparation locale : **141 Python PASS / 0 FAIL / 0 SKIP**, dont 23 gates M6 B ; **68 contrats JDK PASS / 0 FAIL / 0 SKIP** ; sept mutants temporels détectés ; tous les gates hérités exécutés et verts ; diff/check PASS. Les essais intermédiaires d'inventaires non encore admis sont des failures de préparation conservées dans le rapport final ; aucun échec CI n'est masqué.

Qualification finale requise après publication : Android debug APK, Android 15 API35 smoke, Android M5 media scheduler differential API31/35 et Android installation disk/process durability API31/35, sur le HEAD exact. Comptes attendus à vérifier dans l'artifact exécuté : **1118 JVM PASS / 0 FAIL / 1 historical SKIP / 1119 total**, M5 B127/C103/D44 et M6 B68. Le seul skip attendu reste `M1CloudInteropTest.originalColumboProjectionIsInstallable`. Différentiels bruts et preuves natives de durabilité doivent être réaudités, pas simplement lus comme statuts SUCCESS. Les résultats finaux et identités seront dans le manifeste externe, sans modifier ce commit après sa qualification.

## Limites et travail réservé à C/D/E

Ce core qualifie une décision temporelle sur des entrées explicites. Il ne lit pas l'heure OS, n'émet pas de rendu, ne valide pas un bundle Banner/manifest, ne négocie aucune capability, n'installe ni n'ACKe de Banner et ne prouve pas une latence Sony. Les nouvelles classes compilées peuvent changer les bytes d'un APK, même sans consumer runtime : aucun APK B n'est présenté comme « Banner capable » ou byte-identique au Sony M5.

Phase C devra fournir owner commun, activation/génération capturée, promotion avant remise, éligibilité initiale/réactivation, anchor epoch/elapsed, lecture fresh/invalidité, wallGeneration/ticket, attente bornée et signaux Android, autostart Banner connu, handler/codec statiques, même store/installer et renderer natif. Phase D reste le resolver/publication/transport/ACK et l'autorité OS Cloud explicitement autorisés. L'intégration et la qualification Sony appartiennent à E. Aucun de ces chemins n'est commencé ici.

**STOP après l'audit Phase B.** Le verdict final autorisé sera donné après les preuves exactes : `M6 PHASE B: PASS` / `READY FOR M6 PHASE C`, ou `M6 PHASE B: BLOCKED` avec blocker précis. PR #16 reste draft et unmerged, production SHADOW, aucun merge ou Ready.
