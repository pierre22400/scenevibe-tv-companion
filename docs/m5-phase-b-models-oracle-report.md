# M5 Phase B — modèles purs et oracle historique

**Qualification B PASS sur `9978884f75698138d3eae16e3d053922419b514f`**, avec
les mêmes sources que le HEAD logiciel `f563d5be3606ee1c5063958322f1e8f801788cf3`.
Les échecs historiques restent intégralement consignés ci-dessous. Le test retenu
a passé sans patch, skip ni changement de timeout. Le commit de ce rapport final
est documentaire seulement ; son SHA propre et les gates réels de ce SHA sont
scellés dans le manifeste de clôture PR #15. Aucun cutover, scheduler candidat ou
Sony physique B n'est revendiqué.
Production reste **SHADOW** ; PR #15 OPEN / DRAFT / unmerged.

## Références et commits

| Référence | SHA exact |
| --- | --- |
| HEAD initial B / Phase A qualifiée | `689eb3a0243522eea6690ea2af61913bdb64322e` |
| BASE_TV_M5 / M4 intégré | `67b81045258b1692073c6927b956db4899c6ad1a` |
| BASE_CLOUD_M5 | `5011c91aac61a0cc6dcc74c256a15b7dee03d785` |
| Branche | `work/scenevibe-os-m5-scene-event-calendar-001` |
| Ajout initial modèles/oracle | `a5b29d93681d604d1009ad318d3f059a5b49b4a7` |
| Correction harness JDK sous AGP | `f44907ec1ced9811de4e37386c877ada7c0b299e` |
| HEAD logiciel final / packaging resources | `f563d5be3606ee1c5063958322f1e8f801788cf3` |
| Qualification de clôture / mêmes sources, rapport diagnostic | `9978884f75698138d3eae16e3d053922419b514f` |

La Phase A finale lève le blocage documentaire historique mentionné dans
l'architecture. Ce blocage n'est pas rouvert. L'architecture et le rapport A restent
byte-identiques. Le SHA propre d'un document ne peut être inséré dans ses propres
bytes : le manifeste de clôture de la [PR #15](https://github.com/pierre22400/scenevibe-tv-companion/pull/15)
enregistrera le HEAD final exact, les commits B et les runs qualifiant ce HEAD.

## Modèles finaux et invariants

- `SceneEvent` : trois champs private final, `String eventId`, `long startMs`,
  `long durationMs`. ID non null/non vide, <=128 unités UTF-16, conservé exactement.
  Aucun trim, case conversion ou normalisation Unicode. Start 0..43 200 000 inclus ;
  durée positive <=60 000 ; toutes les durées <=0, y compris Long.MIN_VALUE, restent
  représentables. Aucun payload. L'ingress Video 1000..60000 reste inchangé.
- `MediaCalendar` : liste immuable de SceneEvent et booléen `freezeOnPause`
  seulement. Copie défensive, cardinalité 1..256, aucun null, unicité String exacte,
  tri stable croissant de startMs sans clé secondaire. Le HashSet local sert
  seulement au rejet des doubles ; la représentation canonique reste une liste.
- `MediaObservation` : trois champs private final, `eligible`, `positionMs`,
  `playing`. Toutes les valeurs long restent exactes, sans estimation, clamp,
  horloge ou durée du média. Toute position négative reste une indisponibilité.

Les classes sont final et leurs seules données sont private final. Aucun état
consumed, fenêtre, ancre, génération, révision, handler, codec ou durable n'y figure.
Les tests vérifient exactement é, e + U+0301, œ, ’, 🙂, espaces/casse et 128 UTF-16.

## Preuve du core pur et de l'absence de branchement

`test_core_compiles_with_jdk_only_empty_classpath_and_sourcepath` invoque un vrai
compilateur JDK (`javac`, ou le module `jdk.compiler/com.sun.tools.javac.Main`),
avec classpath **et** sourcepath vides et seulement les trois fichiers core.
Il exige exactement trois classfiles. Cette preuve est indépendante de Gradle.
Localement : OpenJDK 17.0.20, **PASS**.

Le gate inspecte imports, champs, références et tous les anciens callers.
Aucun Android/androidx, JSON, produit, payload bag, réseau, I/O, store, Cloud,
réflexion, dynamic loading, thread, timer ou API d'horloge n'est permis dans le core.
Les 52 fichiers Java production déjà présents à B restent byte-identiques ;
aucun n'importe/construit les nouveaux modèles. Build, manifeste, permissions,
workflows et signature sont inchangés. Les nouveaux modèles existent **NON-LIVE**.

## Vrai oracle historique et provenance

Les six fichiers suivants sont copiés **byte pour byte** depuis BASE_TV_M5 dans
`app/src/test/resources/m5-phase-b/oracle/` avec l’extension resource `.java.txt`.
L’extension conserve les bytes et évite le filtre AGP `.java` ; la copie temporaire
compilée reprend son nom Java original. Les pins Git sont indépendants dans le
baseline, le gate Python et le wrapper JVM. Toute référence divergente fait échouer
la qualification. Les sources de production correspondantes sont également figées.

| Source réelle BASE_TV_M5 | Git blob SHA-1 exact |
| --- | --- |
| `MediaIdentityMatcher.java` | `f022a48c78151a11508d7feb12ba91239191a43a` |
| `MediaSessionAccessService.java` | `c8728643be07f405a47b62a84f525302dbb73cab` |
| `MediaSessionProbe.java` | `9c205b09ff5ffd85977250175eaab5019984b1b1` |
| `MediaSyncedTrackScheduler.java` | `441d983409d94ae6d5e31198ec739dec86166754` |
| `NotificationAccess.java` | `b79a16d128c533169305e8a814b56488c3846559` |
| `ScheduledTrack.java` | `38219f1130a0243272d3b1df5ad2e63461d7cc1e` |

`M5FrozenLegacyOracle` invoque le module compilateur du JDK hôte dans un sous-processus
test-only borné à 30 secondes, avec ces six sources et le jar Android
mockable officiel utilisé par AGP. Son class loader charge exclusivement ces
classes et leurs classes internes depuis le répertoire compilé isolé ; il interdit
le fallback vers les classes production. Un test exige cette séparation effective.

Le wrapper construit les vrais ScheduledTrack/Event/MediaIdentity et les vrais
MediaSessionProbe.Snapshot par leurs constructeurs. Il appelle le vrai scheduler
load/clear/onPlaybackSnapshot/onPlaybackUnavailable. Un listener proxy traduit
les callbacks en vocabulaire temporel, immédiatement, sans algorithme réimplémenté.
Le probe n'est jamais construit/démarré ; aucune acquisition Android live n'a lieu.
NotificationAccess et MediaSessionAccessService sont les deux dépendances de
compilation nécessaires à ce probe réel. **Adaptations des bytes oracle : aucune.**

`M5VideoTestProjection` se trouve uniquement dans test : ScheduledTrack déjà
construit -> ID/start/duration exacts et pauseFreezesDisplay. Il ne parse pas JSON,
ne résout pas l'identité, ne construit pas de manifest, ne charge pas d'image et
ne lit ni store ni Cloud. Ce n'est pas l'adapter live C/D.
Le test de projection part aussi de la fixture legacy M4 figée, validée une fois
par TrackParser avant projection. Le helper de projection ne reparse jamais JSON.

## Journal, comparateur et contrôles négatifs

Format JSON exact : une séquence de frames `{input, token, effects}`. `input` est
l'index de l'entrée ; `token` est null ou un label opaque extérieur. Les effects sont
les tableaux ordonnés `[INELIGIBLE]`, `[ELIGIBLE]`, `[PLAYBACK, playing, freeze]`,
`[DUE, eventId]`, `[EXPIRE, eventId]`. Chaque entrée possède une frame, même vide.
Le token n'est ni une révision, ni une décision d'installation, ni une autorité ACK.
Le cas anciens/nouveaux tokens prépare les données C, sans implémenter de garde de
génération en B ; le scheduler historique reste ignorant de ces labels.

Le comparateur teste exactement nombre/indices de frames, tokens, nombre d'effets,
ordre, type, ID, playing et freeze. Aucune tolérance, tri, set, dedupe, debounce,
regroupement ou suppression de PLAYBACK. Dix contrôles négatifs passent : DUE
retiré, permutation, ID Unicode changé, playing changé, freeze changé, effet ajouté,
PLAYBACK répété retiré, token changé, frame vide retirée et index changé. Le contrôle
positif prouve aussi la conservation d'une frame vide au round-trip JSON.
Ces PASS signifient que les divergences ont bien été **détectées**.

## Corpus exécutable

`corpus.json` contient **92 cas** : 82 exécutent l'oracle réel, 10 appliquent les
entrées invalides à la frontière modèle. Parmi les 82 traces oracle, **78 traces
exactes déterministes** sont figées ; **4 traces dépendantes de HashMap/JVM** ont une
`referenceTrace` brute explicitement non portable, jamais un `expected` cross-JVM.
Les attentes ont été enregistrées depuis les bytes historiques réels, puis gelées ;
les tests ne les régénèrent pas. Les branches aux seuils, lateness, rearm, DUE/EXPIRE
et recovery ont aussi été relues contre le code historique.

Les quatre cas d'environnement sont `multiple-active-windows`,
`hashmap-collision-pair`, `hashmap-collision-tree-and-resize`,
`hashmap-unique-key-resize`. Ils exécutent deux fois le vrai oracle dans la même JVM
et comparent exactement leurs traces brutes sans les trier. Le journal effectivement
exécuté est écrit dans `app/build/reports/m5-phase-b-oracle-traces.json`. Les quatre
traces brutes et l'environnement sont conservés dans l'artefact de summary CI
`11391114397` du run `37419229594`, réellement téléchargé et lu : OpenJDK 64-Bit
Server VM 17.0.20.1, quatre cas bruts complets. Les précédents runs bloqués n'ont
pas atteint cet upload ; ils ne sont pas présentés comme preuve d'artefact.
Les références brutes figées et la sortie locale restent disponibles. La comparaison oracle/candidat dans le **même**
environnement et sur Android API31/API35 appartient à C ; B ne revendique pas cette
comparaison ni un ordre HashMap canonique.

| Famille requise | Contrat | Fixtures exécutables |
| ---: | --- | --- |
| 1 | entrée non triée | `unsorted-input` |
| 2 | égalité stable des starts | `stable-equal-starts`, `equal-starts-rearm-track-order` |
| 3 | 1 événement | `single-event`, `empty-calendar` |
| 4 | 256 événements | `max-256-events`, `257-event-calendar` |
| 5 | IDs uniques | `unsorted-input`, `max-256-events`, `exact-id-unicode-and-spaces` |
| 6 | duplicate rejeté | `duplicate-exact-id` |
| 7 | 128 unités UTF-16 | `id-128-ascii`, `id-128-utf16-emoji`, `id-129-ascii`, `id-130-utf16-emoji`, `id-empty`, `id-null`, `exact-id-unicode-and-spaces` |
| 8 | starts 0/max/hors borne | `start-zero`, `start-max`, `start-negative`, `start-over-max` |
| 9 | durées <=0/positive/max/hors borne | `duration--9223372036854775808`, `duration--1`, `duration-0`, `duration-1`, `duration-1000`, `duration-60000`, `duration-over-max` |
| 10 | première ancre/lateness 0/1999/2000/2001 | `start-zero`, `first-anchor-late-0`, `first-anchor-late-1999`, `first-anchor-late-2000`, `first-anchor-late-2001` |
| 11 | trop ancien | `first-anchor-late-0`, `first-anchor-late-1999`, `first-anchor-late-2000`, `first-anchor-late-2001` |
| 12 | DUE puis EXPIRE tardif | `duration--9223372036854775808`, `duration--1`, `duration-0`, `first-anchor-expired-window` |
| 13 | snapshot null | `null-with-and-without-track` |
| 14 | raw/estimated négatifs | `position-raw--1-estimated--1`, `position-raw--7-estimated--2`, `position-raw-10000-estimated--1`, `position-raw--1-estimated-10000`, `position-raw-9999-estimated-10000`, `position-raw--9223372036854775808-estimated--1` |
| 15 | inconnu puis connu | `unknown-first-then-known`, `recovery-unknown-keeps-consumed` |
| 16 | inconnu après ancre | `unknown-after-anchor-does-not-reset` |
| 17 | lecture ordinaire | `normal-playback`, `fast-forwarding-not-playing`, `rewinding-not-playing` |
| 18 | même position répétée | `repeated-position-consumed-no-replay` |
| 19 | un DUE par snapshot | `stable-equal-starts`, `max-256-events`, `unknown-first-then-known`, `one-due-close-events` |
| 20 | consommé sans replay | `max-256-events`, `repeated-position-consumed-no-replay`, `backward-inside-window`, `recovery-unknown-keeps-consumed` |
| 21 | pause FREEZE | `pause-freeze`, `pause-before-first-due` |
| 22 | pause CONTINUE legacy | `pause-continue` |
| 23 | resume | `pause-freeze`, `pause-continue`, `pause-before-first-due` |
| 24 | forward 4999/5000/5001 | `unknown-after-anchor-does-not-reset`, `forward-4999-state-3`, `forward-4999-state-2`, `forward-5000-state-3`, `forward-5000-state-2`, `forward-5001-state-3`, `forward-5001-state-2` |
| 25 | atterrissage forward exact | `forward-4999-state-3`, `forward-4999-state-2`, `forward-5000-state-3`, `forward-5000-state-2`, `forward-5001-state-3`, `forward-5001-state-2` |
| 26 | forward PAUSED | `forward-4999-state-3`, `forward-4999-state-2`, `forward-5000-state-3`, `forward-5000-state-2`, `forward-5001-state-3`, `forward-5001-state-2`, `fast-forwarding-not-playing` |
| 27 | backward -1999/-2000/-2001 | `backward--1999`, `backward--2000`, `backward--2001`, `rewinding-not-playing` |
| 28 | backward avant start | `backward-before-start` |
| 29 | backward start exact | `backward--1999`, `backward--2000`, `backward--2001`, `backward-exact-start` |
| 30 | backward dans fenêtre | `backward-inside-window` |
| 31 | replay après rearm | `backward--1999`, `backward--2000`, `backward--2001`, `backward-before-start`, `backward-exact-start`, `recovery-known-rearms-at-start`, `equal-starts-rearm-track-order` |
| 32 | unavailable | `unavailable-active-no-late-expiry`, `unavailable-without-track` |
| 33 | mismatch répété | `repeated-package-mismatch` |
| 34 | recovery connue/inconnue | `unavailable-active-no-late-expiry`, `repeated-package-mismatch`, `recovery-known-rearms-at-start`, `recovery-unknown-keeps-consumed` |
| 35 | mediaId exact | `exact-media-id-overrides-title-duration`, `cleaned-exact-media-id` |
| 36 | fallback ID absent | `fallback-absent-id` |
| 37 | fallback ID différent | `fallback-different-id` |
| 38 | titre/sous-titre/durée actuel | `fallback-duration-180000`, `fallback-duration-180001`, `fallback-duration--180000`, `fallback-duration--180001`, `fallback-duration-unknown`, `fallback-normalized-title`, `fallback-title-plus-subtitle`, `fallback-meaningful-episode`, `fallback-generic-series-refused`, `fallback-other-title-refused`, `wrong-platform-refused` |
| 39 | end-1/end/end+1 | `duration-1`, `duration-1000`, `duration-60000`, `normal-playback`, `expiry-end-1`, `expiry-end+0`, `expiry-end+1` |
| 40 | fenêtres simultanées | `multiple-active-windows`, `hashmap-collision-pair`, `hashmap-collision-tree-and-resize`, `hashmap-unique-key-resize` |
| 41 | même timestamp | `stable-equal-starts`, `exact-id-unicode-and-spaces`, `equal-starts-rearm-track-order` |
| 42 | DUE proches | `one-due-close-events` |
| 43 | load actif | `load-while-active` |
| 44 | clear actif | `clear-while-active` |
| 45 | remplacement | `replace-calendar-with-new-values` |
| 46 | tokens opaques extérieurs | `opaque-old-new-token-inputs` |
| 47 | reconstruction sans cursor | `reconstruct-from-same-state` |
| 48 | collisions/resize HashMap | `hashmap-collision-pair`, `hashmap-collision-tree-and-resize`, `hashmap-unique-key-resize` |

L'inventaire complet ordonné des 92 IDs et les SHA-256 de chaque resource sont dans
`.github/scripts/m5-phase-b-baseline.json`. Les entrées invalides ne sont pas présentées
comme un rejet du scheduler historique : ScheduledTrack n'est pas le parser Video.

## Inventaire exhaustif du diff B

| Nature | Chemin exact |
| --- | --- |
| Ajout production non-live | `app/src/main/java/com/scenevibe/tvcompanionpoc/calendar/MediaCalendar.java` |
| Ajout production non-live | `app/src/main/java/com/scenevibe/tvcompanionpoc/calendar/MediaObservation.java` |
| Ajout production non-live | `app/src/main/java/com/scenevibe/tvcompanionpoc/calendar/SceneEvent.java` |
| Ajout test/oracle | `app/src/test/java/com/scenevibe/tvcompanionpoc/M5FrozenLegacyOracle.java` |
| Ajout test/oracle | `app/src/test/java/com/scenevibe/tvcompanionpoc/M5JournalComparatorTest.java` |
| Ajout test/oracle | `app/src/test/java/com/scenevibe/tvcompanionpoc/M5LegacyOracleCorpusTest.java` |
| Ajout test/oracle | `app/src/test/java/com/scenevibe/tvcompanionpoc/M5TemporalJournal.java` |
| Ajout test/oracle | `app/src/test/java/com/scenevibe/tvcompanionpoc/M5VideoProjectionTest.java` |
| Ajout test/oracle | `app/src/test/java/com/scenevibe/tvcompanionpoc/M5VideoTestProjection.java` |
| Ajout test/oracle | `app/src/test/java/com/scenevibe/tvcompanionpoc/calendar/M5MediaCalendarTest.java` |
| Ajout test/oracle | `app/src/test/java/com/scenevibe/tvcompanionpoc/calendar/M5MediaObservationTest.java` |
| Ajout test/oracle | `app/src/test/java/com/scenevibe/tvcompanionpoc/calendar/M5SceneEventTest.java` |
| Ajout test/oracle | `app/src/test/resources/m5-phase-b/corpus.json` |
| Ajout test/oracle | `app/src/test/resources/m5-phase-b/oracle/MediaIdentityMatcher.java.txt` |
| Ajout test/oracle | `app/src/test/resources/m5-phase-b/oracle/MediaSessionAccessService.java.txt` |
| Ajout test/oracle | `app/src/test/resources/m5-phase-b/oracle/MediaSessionProbe.java.txt` |
| Ajout test/oracle | `app/src/test/resources/m5-phase-b/oracle/MediaSyncedTrackScheduler.java.txt` |
| Ajout test/oracle | `app/src/test/resources/m5-phase-b/oracle/NotificationAccess.java.txt` |
| Ajout test/oracle | `app/src/test/resources/m5-phase-b/oracle/ScheduledTrack.java.txt` |
| Ajout test/oracle | `tests/m5_phase_b_provenance.py` |
| Ajout test/oracle | `tests/test_m5_phase_b_boundary.py` |
| Ajout provenance | `.github/scripts/m5-phase-b-baseline.json` |
| Admission/comptage seulement | `.github/scripts/m4-phase-a-test-summary.py` |
| Admission/comptage seulement | `tests/sony_corrective_provenance.py` |
| Admission/comptage seulement | `tests/test_m4_phase_b_boundary.py` |
| Admission/comptage seulement | `tests/test_m4_phase_c_boundary.py` |
| Admission/comptage seulement | `tests/test_m4_phase_d_boundary.py` |
| Admission/comptage seulement | `tests/test_m4_phase_e_boundary.py` |
| Admission/comptage seulement | `tests/test_m4_phase_f_boundary.py` |
| Admission/comptage seulement | `tests/test_m4_phase_g_boundary.py` |
| Admission/comptage seulement | `tests/test_m4_sony_corrective_boundary.py` |
| Rapport | `docs/m5-phase-b-models-oracle-report.md` |

Les neuf edits d'anciens fichiers sont exclusivement des admissions d'inventaire
ou du comptage. B/C/D/E/F/G et Sony admettent les **trois chemins exacts** des valeurs ;
Sony admet aussi les additions B finies. Le summary ajoute les 127 cas B, vérifie les
fixtures B et conserve les traces HashMap. `m5_phase_b_provenance` inverse uniquement
ces edits, de façon unique, jusqu'aux blobs complets 689eb3a, puis l'inverse Sony
existant conserve toute la chaîne A–G. Aucun prédicat métier n'est changé et tous
les gates M4 s'exécutent encore. Aucun wildcard/global M5 whitelist n'est admis.
Le baseline correctif Sony reste byte-identique. **229 blobs de départ** sont protégés.

## Qualification et auto-audit avant publication

| Gate local réellement exécuté | Résultat |
| --- | --- |
| Python complet, avec ancien M4 + 12 nouveaux gates B | 98 PASS / 0 FAIL / 0 SKIP |
| JVM complet | 903 PASS / 0 FAIL / 1 SKIP historique / 904 total |
| JVM M5 seuls | 127 PASS / 0 FAIL / 0 SKIP |
| JDK-only core, classpath/sourcepath vides | PASS |
| Anciens JVM | 776 PASS / 0 FAIL / 1 SKIP ; aucun source/cas modifié |
| CI Python au HEAD logiciel final (chaque tentative) | 98 PASS / 0 FAIL / 0 SKIP |
| CI JVM au HEAD logiciel final (tentatives 1, 2, 3) | 902 PASS / 1 FAIL / 1 SKIP ; 904 total |
| CI M5 seuls au HEAD logiciel final | 127 PASS / 0 FAIL / 0 SKIP |
| assembleDebug | Exécuté sans erreur ; confirmé aussi par smoke/native SUCCESS |
| lintDebug / groupe debug complet | Pas de PASS complet ; testDebugUnitTest bloque le groupe |
| LAN DEV / Cloud qualification / stable signing au HEAD logiciel final | NOT RUN : étapes suivantes skipped après testDebugUnitTest FAIL |
| API35 smoke | SUCCESS, run 37417939965 |
| Native API31/API35 | SUCCESS, run 37417939904 |

La compilation initiale du nouveau harness a révélé les déclarations checked
JSONException du framework Android. Les nouvelles méthodes test ont été corrigées
dans ce cycle, sans toucher au core ou à l'ancien code ; les suites réelles ont
ensuite passé. La copie Calendar est également vérifiée après ownership afin de
conserver les bornes de la liste possédée. Ces corrections sont locales à B.

La première CI debug `37417067308`, HEAD `a5b29d93681d604d1009ad318d3f059a5b49b4a7`,
a réellement échoué à compiler le **nouveau** harness : `javax.tools` n’existe pas
dans l’image de compilation Android AGP, et Files.writeString n’est pas exposé.
La correction B invoque le module JDK séparément et écrit les bytes UTF-8 avec
Files.write ; aucun build.gradle/workflow ni source oracle n’est modifié. La
projection a aussi été renforcée avec l’ingress réel de la fixture M4 existante.
L’échec initial est conservé ; il ne constitue ni un PASS ni un échec logiciel M4.

Après correction, une première exécution locale complète a donné 902 PASS /
1 FAIL / 1 SKIP : seul `M4PhaseFOwnerGateTest.interruptedWaitCancelsLateOwnerInstallation`
a échoué (historique sensible au scheduling). Le rerun des **mêmes bytes inchangés**
a donné 903 PASS / 0 FAIL / 1 SKIP. Aucune assertion, timeout, source Cloud ou test
retenu n’a été changé. Les deux tentatives restent distinguées de l’erreur CI B.

Le debug `37417522815`, HEAD `f44907ec1ced9811de4e37386c877ada7c0b299e`,
a ensuite compilé mais échoué sur 83 nouveaux tests oracle : les resources `.java`
sont filtrées par AGP. Le seul autre échec était le test d’interruption historique
ci-dessus. Les six resources sont renommées `.java.txt` sans changer un seul byte,
blob oracle, seuil, branche ou callback. Aucun workflow/build/config n’est modifié.
Le filtre est documenté dans [PackagingUtils officiel](https://android.googlesource.com/platform/tools/base/+/HEAD/build-system/builder/src/main/java/com/android/builder/packaging/PackagingUtils.java).
Cette correction reste entièrement dans les nouvelles additions B.

L'auto-audit couvre le diff complet, l'inventaire fini, l'absence de callers, la
compilation JDK seule, imports/signatures, dépendances interdites, immutabilité,
tri stable, bornes, fidélité Unicode, véritable moteur oracle, blobs, ordre et
absences du journal, négatifs du comparateur et absence de candidat. Les pins
complets établissent Cloud/store/ACK/codec/handlers byte-identiques ; TvCapabilities,
permissions, probe, matcher, scheduler, owner et rendu restent inchangés. Aucun WALL.
Les nouvelles qualifications réelles et le diagnostic sont consignés ci-dessous.
Le seul SKIP historique est `M1CloudInteropTest.originalColumboProjectionIsInstallable`.

## Gate historique : échecs préservés et diagnostic, sans correction hors périmètre

Sur `f563d5be3606ee1c5063958322f1e8f801788cf3`, le run debug
[37417939947](https://github.com/pierre22400/scenevibe-tv-companion/actions/runs/37417939947)
a exécuté trois tentatives **sans aucun changement de code ni de HEAD** :

| Tentative | Job exact | Résultat JVM réel | Seul FAIL |
| ---: | ---: | --- | --- |
| 1 | 112120637648 | 902 PASS / 1 FAIL / 1 SKIP | `interruptedWaitCancelsLateOwnerInstallation` |
| 2 | 112121306910 | 902 PASS / 1 FAIL / 1 SKIP | même test |
| 3 | 112122253850 | 902 PASS / 1 FAIL / 1 SKIP | même test |

Aucune nouvelle source/test M5 n'échoue. Les assertions M4 restent effectives.
La condition d'arrêt pour un échec CI persistant a donc été appliquée : aucun
patch du test, aucun timeout augmenté, aucun skip, aucun changement Cloud.

Pins exacts, identiques au départ B :
- test M4 propriétaire : `68a94c47dc7f9ef6d75e858221acbec618202a0a` ;
- CloudControlClient : `48fccfe62b2964f792fabd8d750106c424df4957`.

Le test attend uniquement InterruptedException après stop et libération owner.
Le log CI réel donne CloudControlClient.CloudException à la ligne 256
(`Local installation refused`). Lecture du code retenu : le latch `dispatched`
est relâché **avant** owner.execute ; le test arrête io puis libère owner. Le
FutureTask peut terminer `ARM_FAILED` via la garde running=false avant que son
get observe l'interruption. Le client refuse alors l'installation avant ACK ;
le test n'accepte pas ce chemin d'exception. Il s'agit du diagnostic de la race
historique, pas d'une modification ou d'une nouvelle preuve d'acceptation.
L'ordre exact des threads n'a pas été instrumenté en CI ; cette explication est
une inférence depuis le code figé et l'exception observée. Six exécutions isolées
locales des six tests de cette classe ont chacune donné 6 PASS / 0 FAIL / 0 SKIP,
et un rerun local complet a donné 903/0/1. Ces résultats **ne remplacent pas**
les trois échecs du gate complet CI.

Un correctif de ce test historique exigerait une décision explicite hors B.
Aucun mécanisme de contournement n'est introduit. Sur le HEAD documentaire
`9978884f75698138d3eae16e3d053922419b514f`, avec **toutes les sources et tous les
tests byte-identiques à f563d5b**, le debug complet `37419229594` a passé dès sa
première tentative : 903 PASS / 0 FAIL / 1 SKIP. Le test retenu a bien été exécuté.
Cette réussite ne supprime ni ne remplace les trois échecs précédents ; elle
confirme la sensibilité historique au scheduling sans introduire de régression B.
Le gate est clos pour la qualification enregistrée ci-dessous, selon la règle
explicite autorisant le rerun inchangé du seul test historique.
Le verdict final de cette publication documentaire dépend des gates réels de
son propre HEAD consignés dans le manifeste PR, sans commencer C.

## Preuves Android et signature

- [Smoke 37417939965](https://github.com/pierre22400/scenevibe-tv-companion/actions/runs/37417939965) :
  SUCCESS sur le HEAD logiciel final ; image Android API35 standard, pas Sony/TV.
- [Native 37417939904](https://github.com/pierre22400/scenevibe-tv-companion/actions/runs/37417939904) :
  SUCCESS API31 + API35. Artefacts lus réellement : `11392068235` (31) et
  `11391414286` (35). Onze scénarios corrigés PASS par API, PIDs seed/reload tous
  distincts ; quatre contrôles négatifs historiques PASS supplémentaires en API31.
  Trente invocations API31 et vingt-deux API35. Ces comptes restent séparés des JVM.
- Les étapes de compilation LAN DEV, Cloud qualification et signature stable du
  debug sont NOT RUN sur ce HEAD car le test retenu bloque leur exécution. Le signer
  de la base Phase A est `f908bf564ed97ba67e02b1ebc89eb0239cf980752587f55eb9ec0419791a2e9c` ;
  config/signing byte-identiques, mais **aucun APK B signé n'est qualifié par héritage**.
- Les workflows du commit documentaire de ce rapport sont déclenchés normalement.
  Le SHA propre et leurs résultats finaux seront enregistrés dans le manifeste PR,
  sans un nouveau cycle de correction du runtime ni démarrage C.

## Qualification de clôture réellement PASS

HEAD exact qualifié : `9978884f75698138d3eae16e3d053922419b514f`.
Le diff depuis f563d5b ne change qu'un rapport Markdown ; aucun source, fixture,
assertion, baseline, build/config ou workflow n'a changé pour cette réussite.
Les trois runs ci-dessous portent exactement ce head_sha, event pull_request,
et ont conclu SUCCESS à leur première tentative.

| Gate de clôture | Résultat réel | Run / artefact |
| --- | --- | --- |
| Python complet | 98 PASS / 0 FAIL / 0 SKIP | debug 37419229594 |
| JDK-only core | PASS, compilation avec classpath/sourcepath vides dans le gate Python | même run |
| JVM complet, XML et map de suites validés | 903 PASS / 0 FAIL / 1 SKIP / 904 total | summary 11391114397 lu |
| JVM M5 seuls | 127 PASS / 0 FAIL / 0 SKIP | six suites B, summary m5PhaseBCases=127 |
| Anciens JVM | 776 PASS / 0 FAIL / 1 SKIP historique | mêmes suites/cas, aucun retrait |
| assembleDebug / lintDebug / testDebugUnitTest | SUCCESS | debug 37419229594 |
| LAN DEV compile | SUCCESS | étape réelle même run |
| Cloud qualification APK compile | SUCCESS | étape réelle même run |
| Stable signing / apksigner verify | SUCCESS | stable artifact 11391868938 |
| API35 platform smoke | SUCCESS | 37419229547 |
| Native disk/process API31 + API35 | SUCCESS | 37419229556 |

Signer certificate SHA-256 réellement vérifié dans ce run :
`f908bf564ed97ba67e02b1ebc89eb0239cf980752587f55eb9ec0419791a2e9c`.
APK stable téléchargé : 230202 bytes, SHA-256
`42643cc851a8020df871eaa2064a3748d017325ff10ce64b13cc25bdefb80c59`.
Le summary conserve les quatre traces HashMap brutes avec leur environnement.
Aucun nouveau SKIP, aucune qualification Sony B, aucune comparaison candidat
implémentée et aucun ordre portable HashMap revendiqué.

Le gate antérieur était effectivement bloqué à f563d5b ; cette dernière matrice
est la qualification ultérieure des **mêmes bytes source/test**, qui clôt ce gate.
Le manifeste PR complète le présent rapport avec son SHA documentaire propre,
les commits B exhaustifs et les nouveaux runs réels sur ce SHA final.

## Limites et suite autorisée

**NO PRODUCTION CUTOVER — NO WALL — NO CLOUD/DURABLE/ACK CHANGE.**
Aucun nouveau scheduler, aucun futur clock provider, aucun adapter live, aucun
shadow scheduling production. Aucun M6/M7/M8+, nouveau codec, Cloud API, asset,
permission ou player control. Pas de Sony physique B requis ni revendiqué.
PR #15 reste OPEN / DRAFT / unmerged, sans Ready, rebase, force-push ni merge.

Prochaine recommandation, après la vérification du HEAD documentaire final : **M5 PHASE C — GENERIC MEDIA
SCHEDULER + MEDIA OBSERVATION ADAPTERS + EXACT LEGACY/CANDIDATE DIFFERENTIAL**.
C n'est pas commencé dans ce cycle.

## Pre-Phase-C qualification corrective — historical owner-gate race

Ce cycle est **hors implémentation fonctionnelle Phase B**. Il corrige uniquement
la qualification du comportement fail-closed historique. Phase C est **NON
COMMENCÉE**, production **SHADOW**, PR #15 **OPEN / DRAFT / unmerged**.

### Échecs du HEAD final B conservés

Le HEAD documentaire final B `ce724e9058dbf56eee2b235174e18556807bcf1d` a échoué
deux fois sans changement des bytes, dans le run debug `37420016735` :

| Tentative | Job | Python | JVM | Échec unique |
| --- | --- | --- | --- | --- |
| 1 | `112127060876` | 98/0/0 | 902/1/1 | M4PhaseFOwnerGateTest.interruptedWaitCancelsLateOwnerInstallation |
| 2 | `112128254759` | 98/0/0 | 902/1/1 | Même test, même CloudException |

L'exception observée était exactement
`com.scenevibe.tvcompanionpoc.CloudControlClient$CloudException`, message
`Local installation refused`, à CloudControlClient.java:256. Le test n'acceptait
que InterruptedException. Les runs rouges précédents et la réussite distincte
sur `9978884f75698138d3eae16e3d053922419b514f` ci-dessus restent historiques ;
aucun PASS antérieur ne vaut qualification du nouveau HEAD.

### Cause et correction limitée

Après `stop()`, IO peut observer l'interruption avant l'owner : la tâche est
annulée et InterruptedException est valide. Si l'owner gagne la course, sa garde
`running && currentClient.getAsBoolean()` est false : aucune installation,
ARM_FAILED puis CloudException / `Local installation refused`. Le type unique
attendu était trop prescriptif ; les deux issues doivent échouer fermement.

Seule la méthode `interruptedWaitCancelsLateOwnerInstallation` est modifiée dans
le test Java existant. Son nom, ses trois secondes de timeout et sa frontière
owner restent identiques. Le catch supplémentaire vérifie **classe exacte ET
message exact** ; toute autre exception échoue. Après drainage via
`h.onOwner(() -> null)`, cinq assertions distinctes exigent : installCalls=0,
acks=0, candidateWrites=0, ackWrites=0 et acknowledgedRevision=0.

**ZERO PRODUCTION CHANGE** : aucun Java production, CloudControlClient,
AssignmentMutationGate, scheduler, installer/store, ACK, modèle Phase B, oracle,
corpus, timeout, workflow ou signature n'est modifié. Aucun skip ajouté.

### Provenance finie et réversible

Le manifeste M5 existant ajoute `qualificationCorrective`, référence
`ce724e9058dbf56eee2b235174e18556807bcf1d`, avec exactement quatre inverses :
le test Java, m5_phase_b_provenance.py, test_m4_sony_corrective_boundary.py et
test_m5_phase_b_boundary.py. Les seuls autres chemins autorisés sont le manifeste
M5 lui-même et ce rapport. Aucun wildcard ni nouvelle admission de runtime.
Les hashes historiques M4, le baseline Sony et les pins B existants sont
inchangés. La chaîne inverse qualification → B → Sony reconstruit les bytes
historiques complets, notamment blob test `68a94c47dc7f9ef6d75e858221acbec618202a0a`.
Le contrôle B nouveau vérifie cette admission exacte et les cinq assertions.
Les contrôles comportementaux/compilation exécutent toujours les bytes actuels.

### Résultats locaux avant la qualification complète

Sur les bytes correctifs destinés au commit, JDK17 et framework mockable Android
AGP officiel, sans modification des sources production :

| Contrôle exécuté | Résultat |
| --- | --- |
| Classe M4PhaseFOwnerGateTest, 1 exécution avant suite complète | 6 PASS / 0 FAIL / 0 SKIP |
| interruptedWaitCancelsLateOwnerInstallation, 100 exécutions finies mêmes bytes | 100 PASS / 0 FAIL / 0 SKIP |
| Bloc catch extrait du test et compilé dans un contrôle local temporaire | Deux issues valides acceptées ; trois invalides rejetées |
| Contrôles invalides | IllegalStateException avec message valide, CloudException avec message incorrect, Exception générique avec message valide |
| Python complet, incluant nouveau contrôle de provenance | 99 PASS / 0 FAIL / 0 SKIP |
| JDK-only core, classpath/sourcepath vides | PASS dans Python |
| JVM complet local | 903 PASS / 0 FAIL / 1 SKIP historique / 904 total |
| JVM M5 seuls local | 127 PASS / 0 FAIL / 0 SKIP |

Le SKIP reste M1CloudInteropTest.originalColumboProjectionIsInstallable.
Les répétitions naturelles ne prétendent pas mesurer la fréquence de chacun des
ordonnancements ; les contrôles synthétiques vérifient la stricte acceptation.
Pas de boucle permanente ajoutée au test ni de Thread.sleep.

La qualification Android/CI sur le SHA correctif est **PENDING** à ce commit :
assembleDebug, lintDebug, testDebugUnitTest, LAN DEV, Cloud qualification, signer,
API35 smoke et native durability API31/API35 doivent encore passer. Le manifeste
PR #15 enregistrera le SHA correctif et les résultats réels sur le HEAD final
exact après qualification. Verdict actuel : **NOT READY FOR M5 PHASE C**.

### Qualification complète réellement exécutée du correctif

HEAD correctif exact : `890c14b48eeb4a3c4d032459da413e9b079f3550`.
Son arbre Git `4b12e908438c18d26ededce465be6450a07e7e76` est exactement
l'arbre compilé et testé localement ci-dessus. Publication en fast-forward normal,
sans rebase, force-push, Ready ou merge. Les trois runs pull_request ont tous
conclu SUCCESS à leur première tentative :

| Gate | Résultat réel | Preuve |
| --- | --- | --- |
| Python complet | 99 PASS / 0 FAIL / 0 SKIP | debug 37425780909, job 112144982060 |
| JDK-only core | PASS, classpath/sourcepath vides | gate Python exécuté |
| JVM complet / map de suites | 903 PASS / 0 FAIL / 1 SKIP historique / 904 total | summary 11394783258 téléchargé et lu |
| M5 B seuls | 127 PASS / 0 FAIL / 0 SKIP | summary m5PhaseBCases=127, exécution locale indépendante 127/0/0 |
| assembleDebug / lintDebug / testDebugUnitTest | PASS | debug 37425780909 |
| LAN DEV compile | PASS | même run, étape exécutée |
| Cloud qualification compile | PASS | même run, étape exécutée |
| Stable signing + apksigner verify | PASS | même run, artifact 11394997804 téléchargé |
| API35 platform smoke | PASS | run 37425780928, job 112144982179 |
| Native durability API31 | PASS | run 37425780960, artifact 11394878218 téléchargé |
| Native durability API35 | PASS | même run, artifact 11395406284 téléchargé |

Le certificat signer SHA-256 lu dans le log est exactement
`f908bf564ed97ba67e02b1ebc89eb0239cf980752587f55eb9ec0419791a2e9c`.
APK stable réellement téléchargé : 230202 bytes, SHA-256
`42643cc851a8020df871eaa2064a3748d017325ff10ce64b13cc25bdefb80c59`, identique
à l'APK historique B qualifié : le test-only corrective ne change aucun APK.
Le summary confirme les six tests owner-gate, les 127 cas M5, le seul SKIP
historique et les quatre traces HashMap brutes conservées.

Les artefacts native ont été décompressés ; le script de validation inchangé a
été réexécuté localement sur les traces brutes. Onze scénarios corrigés PASS par
API, PIDs seed/reload distincts dans chaque cas ; quatre contrôles négatifs du
baseline historique supplémentaires sur API31. Trente invocations API31,
vingt-deux API35. Smoke API35 qualifie uniquement la plateforme standard Android,
pas le runtime TV ni Sony. Aucun test Sony physique n'est requis ou revendiqué
pour cette correction test-only/provenance/documentaire.

L'auto-audit confirme six chemins exacts, les quatre inverses vers les blobs
ce724e9, les baselines historiques M4/Sony et tout le manifeste B originel
inchangés (seul nouveau champ qualificationCorrective), zéro Java production,
modèles/oracle/corpus/resources/instrumentation/workflows byte-identiques et
aucun timeout, skip ni exception supplémentaire admise. **ZERO PRODUCTION CHANGE**.

Le correctif `890c14b48eeb4a3c4d032459da413e9b079f3550` est donc qualifié :
**READY FOR M5 PHASE C** sur ce SHA précis. Le présent ajout final est strictement
documentaire ; son HEAD propre doit également passer la qualification complète,
et ses résultats exacts seront scellés dans le manifeste PR #15 sans modifier
encore ce rapport ni extrapoler les PASS. **NO PHASE C STARTED**, SHADOW,
PR #15 OPEN / DRAFT / unmerged. STOP après cette clôture.
