# SCENEVIBE OS — M6 POST-M5 RECONCILIATION REPORT

État courant au 6 octobre 2026 : réconciliation d'architecture/provenance sur le M5 réellement mergé. **Production SHADOW ; PR #16 OPEN / DRAFT / unmerged ; Phase B non commencée.** Les résultats finaux CI et le SHA du commit publié sont consignés dans le manifeste courant de PR et le rapport utilisateur, afin d'éviter un SHA auto-référentiel.

## Références et méthode Git

| Référence | Identité vérifiée |
| --- | --- |
| `BASE_TV_M6` / main TV / merge #15 | `17cbe36ae99ac7f48aaf861e0d1feac702a9e521` |
| M5 final audited HEAD | `45c97782479f278632d5d19ec0723eb50af59e87` ; même arbre `1bd87638937462296d3a32d207995149aaed7f53` que le merge |
| M5 software HEAD physiquement qualifié | `c9b0efd4acfaaae9ed7da13dcec505b2f653c548` |
| Ancien HEAD M6 conservé | `3f91d66ddfdcd95a4cd47a68ed0060e885ed716b` ; ancien parent M4 `67b81045258b1692073c6927b956db4899c6ad1a` |
| `BASE_CLOUD_M6` / main Cloud | `5011c91aac61a0cc6dcc74c256a15b7dee03d785` ; SHADOW |
| Roadmap Draft #24 relue | `d9eabae89377bd3cb72e1b51524fd989fbccc473`, `docs/project/scenevibe-os-roadmap.md` |
| POC lu uniquement comme preuve future | `96d1de5b555d52a88eda74fb45a6895d8599b316`, `docs/experiments/media-interlude-poc-report.md` |

GitHub est la source de vérité. Les refs et PR ont été vérifiées ; les fichiers TV sont relus depuis le merge réel, les contrats Cloud depuis le main exact. Méthode : merge à deux parents, ancien M6 en premier parent, main M5 en second parent ; publication en fast-forward avec `expected_sha` sur l'ancien M6, force=false. Aucun rebase, réécriture M5 ou import de branche expérimentale. La PR reste sur main ; elle n'est ni mergée ni convertie en Ready.

Les deux documents Phase A ont été relus intégralement. La section historique ci-dessous conserve la caractérisation avant merge : ses mentions M5 pending/open/unmerged, chiffres M4 et numéros M7/M8/M9 ne décrivent pas l'état courant. Les anciens numéros sont des aliases historiques ; la trajectoire actuelle post-M6 reste organisée en workstreams non numérotés.

## Matrice des seize points confrontés au code réel

Préfixe TV : `app/src/main/java/com/scenevibe/tvcompanionpoc/`, au `BASE_TV_M6` ci-dessus. Les lectures des grands services ciblent leurs méthodes d'installation, d'activation, de rendu et d'ACK, ainsi que les tests correspondants ; elles ne prétendent pas être une lecture exhaustive de tout le repository.

| # | Décision après confrontation | Sources/garanties réelles et conséquence |
| --- | --- | --- |
| 1 | CONFIRMÉ : WALL distinct de MEDIA | `calendar/SceneEvent`, `MediaCalendar`, `MediaObservation` portent positions/pause/éligibilité MEDIA. Le futur domaine UTC ne leur est pas ajouté. |
| 2 | CONFIRMÉ : scheduler M5 inchangé | `MediaCalendarScheduler` passif : late 2000 ms, forward >5000, backward <−2000, une première DUE, callbacks synchrones et ordre HashMap historique. Une seule instanciation production dans `OverlayService`, aucune instanciation du legacy. Corpus/oracle/différentiels inchangés. |
| 3 | CONFIRMÉ : WALL peut être payload-free | Les fenêtres/IDs/epoch explicites suffisent à la sélection. `VideoPreparedState` sépare déjà projection et payload index ; Banner aura sa projection distincte. Aucun texte/renderer/store dans le futur core WALL. |
| 4 | CONFIRMÉ AVEC EXTENSION C : owner commun | `LiveVideoRuntimePorts` possède réellement pending/active/retiring, invalidation avant retraite et nettoyage complet. Il reste typé Video et dépend de `VideoPreparedState`/scheduler MEDIA. Extraire la mécanique commune sous un seul main owner, sans copier un LiveBanner concurrent ; la factorisation n'est pas réalisée ici. |
| 5 | PRÉCISÉ : gardes M5 nécessaires, pas suffisantes pour timer WALL | `matching(token)` accepte active uniquement ; capture génération une fois après `replaceRevision`. Token neuf à chaque ARM, même same-revision, overflow token fermé. WALL ajoute `wallGeneration` et timer ticket ; la génération controller `++` n'a pas de garde overflow actuelle, à qualifier finitement en C pour WALL. |
| 6 | CONFIRMÉ AVEC INITIALISATION C : controller sans clock | `onEventDue(id,gen)` ignore stale/inconnu/inéligible ; `onEventExpired` masque l'ID exact ; preflight/show/hide via sink. Le flag eligible survit aux loads : WALL devra le régler après promotion. Regain/suspension exigent une réévaluation fraîche du même candidat, sans horloge dans le controller. |
| 7 | CONFIRMÉ : renderer commun possible | `SceneRenderer` possède texte/rectangle/table/group ; preflight local, retrait immédiat `dismissNow`, fade Video préservé. Banner refusera assets et animations différées récursivement ; legacy renderer/countdown Video reste intact et retrait des deux surfaces obligatoire. Aucune preuve Sony Banner n'est acquise. |
| 8 | CONFIRMÉ AVEC ADAPTERS FUTURS : installation/restore/ARM/ACK | `PackageInstaller` stale avant handler ; same ignore l'entrée et restaure durable sans repersist ; new validate/prepare/commit/readback/restore/arm. Store courant autoritaire, corrupt fermé, markAcknowledged exact. Service restaure avant probe/Cloud. Registry est encore borné à deux codecs ; CloudControlClient ACK reste Video v1. Banner nécessite handler/ports statiques et preuve ACK durable, sans nouveau store/installer. |
| 9 | CONFIRMÉ : deux kinds alternatifs | Un snapshot courant dans `InstallationStore`, un activeRevision sélectionné, un owner, un poller. DeviceAssignment Cloud une ligne/device. Alternance Video N → Banner N+1 → Video N+2 ; coexistence/compteur Banner séparé exclus. |
| 10 | CONFIRMÉ AVEC EXTENSION D : autorité OS unique | `cloud-service.ts` fixe shadow ; `video-assignment-service.ts` alloue legacy en SHADOW. `cutover-coordinator.ts` attend finalTrackId et miroir Video ; FK tv_assignments non nullable. Banner ne peut utiliser ces writers sans extension OS contrôlée, ready avant Send et GET sealed-only. Mode/cutover non autorisé ici ; old v1 doit refuser le miroir Video stale quand Banner est courant. |
| 11 | CONFIRMÉ SOUS INTERSECTION DES BORNES | Parser manifest start ≤12 h, durée 250..3 600 000 ms, 256 scènes, groupe 4, texte 2000 codepoints. MEDIA positif ≤60 s reste intact. WALL propose epoch ≤253402300799999, horizon ≤604800000 ms, 1..256, durées 250..1 h ; profil Cloud ≤1 MiB cumulatif avec artifact 2,4 MB/package 3 MB et HTTP enveloppe échappée. Epoch jamais dans startMs actuel. |
| 12 | CONFIRMÉ AVEC GARDE C : anchor/générations orthogonaux | Activation token et présentation capturée ne changent pas sur correction clock. Epoch/elapsed volatile prépare une attente, chaque callback relit epoch ; wallGeneration invalide anchor et timer ticket invalide une attente annulée. Pending ne rend pas : première évaluation fraîche seulement après promotion, pas replay d'un DUE consommé avant ARM. |
| 13 | CONFIRMÉ : aucune persistance temporelle WALL | Snapshot artifact opaque contient horizon/fenêtres/manifest ; reboot reconstruit handler puis anchor neuf sans cursor/ticket/génération durable. BootReceiver/AutostartPolicy exigent encore media grant : C doit ajouter une décision metadata-only pour Banner connu, opt-in/overlay/durable, Video et inconnus inchangés. |
| 14 | CONFIRMÉ PAR PROVENANCE EXÉCUTÉE : admissions finies | Les quatre inventaires admettent exactement les deux docs. L'inverse M6 vérifie quatre blobs avant/après complets et contexte unique, restitue les blobs merged M5 avant l'inverse Sony et D→C→B→M4. Baselines JSON, starting blobs historiques, assertions, oracles/fixtures et skips inchangés. Gates et mutations négatives sont réexécutés, résultat final distinct de l'historique. |
| 15 | CORRIGÉ : M5 closed/merged | Architecture et état courant PR mis à jour ; ancienne Phase A conservée sous historique. La clôture M5 immuable décrit son état pre-merge ; `Last startup restore: -` reste littéral et ne signifie pas ARMED. Révision 6 survivante/exécutée sans Send prouve le restore fonctionnel ; logcat sans preuve explicite RESTORE/MediaCalendar/DUE/revision. |
| 16 | ARRÊTÉ : Phase B pure uniquement, non commencée | Modèles/validation temporelle/scheduler WALL JDK pur, fake clocks et frontières ; aucun driver Android, handler/codec enregistré, adapter Cloud/TV, changement MEDIA, capability flip, APK Banner ou Sony. C/D/E restent soumis à leurs work orders et gates propres. |

## Hypothèses corrigées et risques conservés

La base initiale M4 est remplacée par M5 merged, ses 118 gates Python et ses gates JVM/native réels. L'admission Sony et l'inverse de clôture M5 sont hérités, pas recréés. Le protocole/rapport Phase D pending est historique ; la clôture physique finale et le merge sont courants.

Les signatures ID/génération du controller sont réutilisables, mais les ports live ne sont pas déjà génériques. L'éligibilité initiale Banner, le DUE après promotion, les gardes timer/anchor et leur overflow restent des obligations C. L'ACK Banner durable n'est pas déjà implémenté par le client Video ; le Cloud Send/GET actuel n'est pas ready-only. Ces écarts ne bloquent pas les primitives pures B, mais bloquent une revendication Banner live tant que C/D/E ne sont pas qualifiés.

Risques futurs : heure OS erronée, lecture anchor/refus, latence/suspension/FGS/dalle Sony, horizon offline fini sans renouvellement automatique, retrait natif refusé, transition d'autorité Cloud et rollback dangereux après un Banner courant. Aucune permission, asset distant, nouvelle identité, multi-installation ou nouvelle autorité concurrente n'est introduite.

Le POC distinct confirme VIDEO+PAUSE full interlude PASS Sony/Prime ; AUDIO+DUCK est semantic FAIL malgré le cue ADTS local fonctionnel en 0.1.4. Prime choisit une pause sur CAN_DUCK sans ownership SceneVibe. Ces preuves alimentent seulement la capability matrix post-M6 ; aucun code/branche expérimental n'est importé, et aucune politique media-type implicite n'est fixée.

## Diff documentaire/provenance exact contre BASE_TV_M6

```text
ADD    docs/scenevibe-os-m6-banner-wall-clock-architecture.md
ADD    docs/m6-phase-a-architecture-report.md
MODIFY tests/test_m4_sony_corrective_boundary.py
MODIFY tests/test_m5_phase_b_boundary.py
MODIFY tests/test_m5_phase_c_boundary.py
MODIFY tests/test_m5_phase_d_boundary.py
MODIFY tests/m5_phase_d_provenance.py
```

Les quatre modifications de tests ne changent que leurs inventaires par les deux chemins exacts. Le helper ajoute uniquement la couche inverse M6 whole-blob avant le helper Sony existant ; les deux blobs antérieurs déjà admis (candidat Sony et clôture mergée) restent reconnus exactement. Une mutation inconnue échoue avant l'inverse. Aucun wildcard, skip, suppression d'assertion, seuil relâché ou baseline réécrit.

Les gates courants portent sur le nouveau HEAD exact : Python complet, provenance positive et négative, diff/tree complet ; quatre workflows hérités debug APK, API35 smoke, M5 differential API31/35 et disk/process durability API31/35. Les résultats GitHub réellement terminés et leurs artifacts sont consignés au manifeste final externe. Les comptes M5 hérités attendus restent 118 Python PASS, JVM 1050 PASS / 0 FAIL / 1 historical SKIP / 1051 total ; B127/C103/D44 ; seul skip `M1CloudInteropTest.originalColumboProjectionIsInstallable`. Ils ne constituent pas une qualification du runtime Banner absent.

Le canon de roadmap est relu : Banner distinct WALL, post-M6 workstreams non numérotés. Une correction exclusivement documentaire du statut M5/du gate de réconciliation dans la Draft #24 est justifiée par ses mentions courantes devenues fausses ; elle ne change ni Cloud main, ni code, ni mode, ni order post-M6. Le HEAD éventuel de cette correction est consigné dans le rapport final.

La caractérisation initiale suivante est conservée comme histoire fermée de la Phase A avant merge. Elle ne remplace pas les conclusions actuelles ci-dessus.

<details>
<summary>Historique intégral de M6 Phase A au HEAD 3f91d66, avant merge M5</summary>

# M6 Phase A — rapport de caractérisation et d'architecture

Date : 6 octobre 2026. Auteur d'exécution : Alex. Périmètre : documentation uniquement, sur GitHub comme source de vérité. L'architecture arrêtée est [scenevibe-os-m6-banner-wall-clock-architecture.md](scenevibe-os-m6-banner-wall-clock-architecture.md).

```text
M6 PHASE A ARCHITECTURE COMPLETE
IMPLEMENTATION BLOCKED PENDING M5 PHYSICAL CLOSURE / MERGE / RECONCILIATION
```

## 1. Références effectivement contrôlées

| Surface | Référence exacte contrôlée | État initial |
| --- | --- | --- |
| `pierre22400/scenevibe-tv-companion` / `main` | `67b81045258b1692073c6927b956db4899c6ad1a` | M4 mergé ; base Git réelle |
| Arbre de cette base TV | `beab095d735b61f457a71336d95325c49a6c463b` | Base des deux ajouts documentaires |
| PR TV #15 / branche M5 | `c9b0efd4acfaaae9ed7da13dcec505b2f653c548` | OPEN / DRAFT / unmerged ; base `main` au SHA M4 |
| `pierre22400/interface-scenevibe` / `main` | `5011c91aac61a0cc6dcc74c256a15b7dee03d785` | Lecture seule ; composition SHADOW |
| Arbre de cette base Cloud | `15606778cb94dec92cac520be32891409cfd4cc4` | Découverte des chemins réels, arbre non tronqué |

Branche M6 : `work/scenevibe-os-m6-banner-wall-architecture-001`, créée depuis le SHA M4 exact, indépendamment de M5. Aucune source M5 n'est importée dans la branche ; les sources M5 sont consultées dans une copie d'audit au commit exact. Les fichiers Cloud consultés proviennent de GitHub avec vérification du blob SHA contre l'arbre découvert, sans écriture dans le repository Cloud.

Deux documents ont été lus intégralement comme prérequis : l'architecture M4 sur la base ci-dessus et l'architecture M5 au HEAD ci-dessus. Ont aussi été lus le rapport du correctif hard-reboot M4, la clôture physique Sony M4, le rapport du cutover M5 D et le protocole ciblé Sony M5. Les architectures historiques sont distinguées du code réellement présent au HEAD : par exemple, le scheduler legacy n'est pas une façade active après cutover, et les classes M5 existent déjà même si leur document Phase A présentait alors une implémentation future.

Les tableaux suivants recensent les fichiers et méthodes nécessaires à la décision, pas une affirmation de lecture exhaustive de chaque fichier du repository. Les grands services, handlers et suites de tests ont fait l'objet de lectures ciblées des méthodes/validations concernées et de leurs usages. Les rapports CI hérités sont des preuves documentaires consultées ; leurs artifacts n'ont pas été reconstruits ou requalifiés dans cette Phase A. Le Cloud n'a pas été exécuté contre une DB ou un service live.

## 2. Inventaire TV : installation, durable et capabilities

Préfixe `TV/` dans les deux inventaires TV : `app/src/main/java/com/scenevibe/tvcompanionpoc/`. Tous les chemins ci-dessous existent sur M4 ; les différences M5 sont signalées. « Étendre » signifie une décision pour un futur work order après réconciliation, aucune mutation de cette Phase A.

| Chemins réels | État/responsabilité actuelle | Propriétaire | Traitement M6 futur |
| --- | --- | --- | --- |
| `TV/installation/TvCapabilities.java`, `ExecutionRequirements.java` | Codecs Video fermés ; MEDIA exécutable ; WALL représentable mais support false ; manifested MEDIA/FREEZE | M4 | Étendre finitement un profil Banner/WALL seulement après chemin qualifié ; conserver les profils MEDIA |
| `TV/installation/InstallationBounds.java`, `InstallRequest.java` | IDs sûrs, révision positive, bytes copiés ; deux artifacts max ; 3 MB total / 2,4 MB artifact | M4 | Réutiliser inchangé ; bundle Banner unique sous plafond plus strict |
| `TV/installation/PreparedInstallation.java`, `InstallationHandler.java` | Requirements, valeurs bornées, markers PreparedState/RuntimePorts ; validate/prepare/cache/restore/arm | M4 | Réutiliser les interfaces ; état typé Banner distinct, aucun JSON massif dans les scalaires |
| `TV/installation/InstallationHandlerRegistry.java`, `TV/VideoInstallationHandlers.java` | Deux handlers eager, codec whitelist current, composition statique Video | M4 | Composition commune finie trois handlers ; count et whitelist explicitement étendus ; pas de registre dynamique |
| `TV/VideoManifestInstallationHandler.java`, `VideoLegacyInstallationHandler.java` | Parsing/bridge propres Video ; compatibilité historique isolée | Video sur M4 | Strictement préserver l'algorithme et les codecs ; aucune branche Banner cachée |
| `TV/installation/InstallationSnapshot.java`, `InstallationSnapshotCodec.java` | Snapshot générique immuable ; framing exact `scenevibe.os.installation-store.v1`, artifacts triés/Base64 ; max encodé 4 001 024 caractères | M4 | Réutiliser format/comportement inchangés, artifact Banner opaque |
| `TV/installation/InstallationStore.java` | Un snapshot courant autoritaire, ACK séparé, commit atomique/readback ; same/stale ; corrupt fail-closed, aucune résurrection legacy | M4 | Réutiliser inchangé ; aucune seconde installation ni cursor temporel |
| `TV/installation/AndroidInstallationBackend.java` | Transport SharedPreferences `cloud_track`, framing XML corrigé, fin `!` ; bytes logiques stricts | M4 correctif Sony | Préserver exactement ; gate natif reboot/Unicode repris |
| `TV/installation/PackageInstaller.java` | Read durable, stale avant handler, same restaure binding durable sans repersist, new validate→prepare→commit→readback→restore→arm ; ACK hors installer | M4 | Même pipeline et failures ; aucun installer Banner |
| `TV/VideoInstallationRuntimePorts.java`, `VideoPreparedState.java` | M4 ports Video/track ; M5 projection calendar immutable et état partagé sur bind | Video / M5 | Conserver projection/adaptation ; ports Banner distincts sous même owner |
| `TV/BootReceiver.java`, `AutostartPolicy.java`, `AutostartPreference.java` | Après unlock uniquement, métadonnées store, opt-in ; policy exige encore grant MediaSession et overlay | M4 lifecycle | Étendre décision pour codec/handler Banner connu sans grant MediaSession ; conserver Video/inconnu ; aucune permission |
| `TV/InstallationIdentity.java`, `CloudDeviceCredentials.java`, `PairingRuntime.java`, `PairingPolicy.java` | Identité et credentials durables, pairing/reset existants | TV/Cloud lifecycle | Strictement inchangés ; aucune réinstallation ou nouvelle identité |

Points observés dans le store/installer : le snapshot générique demeure autoritaire même s'il est invalide ; l'ACK ne peut avancer au-delà du durable ; ARM_FAILED après commit conserve un package pending et l'ancien ACK. `markAcknowledged` vérifie la révision exacte. Le handler Banner peut donc être ajouté sans nouveau durable ou modification des garanties M4.

## 3. Inventaire TV : rendu, runtime, transport et M5 réel

| Chemins réels | État/responsabilité actuelle | Propriétaire | Traitement M6 futur |
| --- | --- | --- | --- |
| `TV/OverlayManifest.java`, `OverlayManifestParser.java` | `source.product` inclut banner ; clock media/wall ; bornes scènes/primitives/group/text ; start 0–12 h, durée 250 ms–1 h ; aucun anchor | M1/M4 contrat de rendu | Réutiliser parser/contrat ; profil Banner croise calendrier et scènes à offset zéro ; ne pas élargir start pour epoch |
| `TV/SceneRuntimeController.java` | M4 remise d'événements Video ; M5 ID + génération capturée, un visibleScene, preflight/hide/show, aucune clock | Présentation OS / adaptation M5 | Contrôleur commun sous l'owner ; noms/documentation Video éventuellement ajustés sans changement MEDIA |
| `TV/SceneRenderer.java` | Views natives/WindowManager ; texte, image locale via resolver, rectangle, table, group ; animations ; pas d'acquisition | Présentation | Réutiliser texte/rectangle/table/group ; Banner refuse image et exige animation none/retrait immédiat ; pas de second renderer |
| `TV/OverlayRenderer.java`, `DisplayCountdown.java` | Renderer/countdown legacy Video qualifié | Video historique | Strictement préserver et retirer avant Banner ; jamais deux visuels actifs |
| `TV/OverlayService.java` | Owner main, restore avant probe/Cloud ; M5 `LiveVideoRuntimePorts` pending/active/retiring, token neuf et génération fixe ; un MediaCalendarScheduler | Composition TV et owner | Extraire la mécanique commune d'activation, dispatcher des ports statiques Video/Banner ; driver WALL unique, aucune copie de deuxième owner |
| `TV/CloudControlClient.java` | GET après ACK, mutation owner attendue, ACK après ARMED/client courant, stop/reset/interrupt gardés ; transport v1 encore Video | Transport TV / M4 | Réutiliser garanties du client avec adapter/enveloppe interne commune ; un poller |
| `TV/AssignmentMutationGate.java` | FutureTask owner synchrone attendue ; queued work annulé si interruption | M4 sérialisation | Préserver ; pas d'ACK sur simple runnable posté |
| `TV/CloudProtocol.java`, `CloudV1InstallationAdapter.java` | v1 revision/finalTrackId/runtime/manifest ; adapter JSON vers artifacts ; liaison ACK FinalTrack hors InstallRequest | Compatibilité Video | Strictement préserver branche Video ; nouvel adapter Banner exact, sans faux FinalTrack |
| `TV/MediaSessionProbe.java`, `MediaIdentityMatcher.java`, `MediaSessionAccessService.java` | Observation passive/identité/grant Video ; aucun contrôle player | Video | Inchangés ; aucun accès depuis le core WALL |
| `TV/calendar/SceneEvent.java`, `MediaCalendar.java`, `MediaObservation.java` au M5 exact | Valeurs MEDIA : start ≤12 h, durée positive ≤60 s ou cas synthétique legacy ; calendar freezeOnPause ; observation éligible/position/playing | M5 | Strictement inchangées ; nouveau modèle WallEvent/WallCalendar |
| `TV/calendar/MediaCalendarScheduler.java` au M5 exact | Scheduler passif synchrone, consommations/windows, token, policies pause/seek/replay ; pas de timer Android | M5 autorité MEDIA | Strictement inchangé ; calendrier vidé quand Banner prend le courant |
| `TV/VideoMediaObservationAdapter.java`, `VideoPreparedState.java` au M5 exact | Projection Video unique au prepare ; index payload hors core ; matcher MediaSession et position estimée/raw | Video / M5 | Réutiliser branche Video sans y ajouter WALL |
| `TV/MediaSyncedTrackScheduler.java` | Définition production historique conservée, algorithme M4 intact ; zéro instanciation de production après cutover M5 | Legacy/oracle Video | Ne pas réactiver ou présenter comme façade effectivement branchée |
| `TV/RuntimeDiagnostics.java`, `DiagnosticsStore.java`, `DiagnosticsActivity.java` | Enums et scalaires observationnels, installation/restore/read failure, Cloud/manifest/scene ; label moteur MEDIA en M5 | Observabilité | Extension WALL finie sans contenu/secret/URL et sans piloter l'état |
| `app/src/main/AndroidManifest.xml`, `app/build.gradle`, `docs/stable-signing.md`, `docs/signature-continuity-protocol.md` | Package, permissions, FGS specialUse, minSdk 26, compile/target 35 et signature stable | Plateforme/continuité | Strictement inchangés ; toute permission nouvelle = STOP |

La lecture de `LiveVideoRuntimePorts` vérifie l'invalidation avant retrait natif, le refus des callbacks pending, le token d'activation frais, la capture unique de `currentGeneration()` après manifest ARM et le forward gardé DUE/EXPIRE. Le mécanisme est réutilisable comme owner commun ; les règles MEDIA ne le sont pas. Les overloads du controller sans génération existent encore : le futur driver WALL asynchrone doit utiliser exclusivement la forme gardée.

## 4. Tests/provenance TV consultés et gates à conserver

| Chemins réels | Preuve / lecture concernée | Traitement futur |
| --- | --- | --- |
| `tests/test_m4_phase_b_boundary.py` à `test_m4_phase_g_boundary.py`, `tests/test_m4_sony_corrective_boundary.py`, `tests/sony_corrective_provenance.py` | Inventaires finis, hashes retenus et inverse des seuls changements admis ; lecture complète du gate Sony corrective et exécution de toute la suite Python M4 | Préserver assertions et provenance ; pas de skip/wildcard pour admettre M6 |
| `.github/scripts/m4-phase-a-baseline.json` à `m4-phase-g-baseline.json`, `m4-phase-g-sony-corrective-baseline.json` | Baselines historiques immuables et starting blobs | Importer la protection réelle, ne pas remplacer hashes par le résultat candidat |
| `app/src/test/java/com/scenevibe/tvcompanionpoc/installation/PackageInstallerTest.java`, `PackageInstallerFailureTest.java`, `PackageInstallerReadbackTest.java`, `InstallationStoreTest.java`, `M4SonyPreferenceTransportTest.java`, `M4SonyReadFailureTest.java` | Installer/store/readback/transport/corruption ; signatures et cas pertinents audités | Garder garanties et ajouter cas Banner dans un ordre futur explicite |
| `app/src/test/java/com/scenevibe/tvcompanionpoc/calendar/M5SceneEventTest.java`, `M5MediaCalendarTest.java`, `M5MediaObservationTest.java` | Bornes MEDIA, immutabilité et observations | Ne pas élargir pour WALL ; ajouter suite WALL distincte |
| `app/src/test/java/com/scenevibe/tvcompanionpoc/M5SchedulerDirectTest.java`, `M5VideoProjectionTest.java`, `M5VideoObservationAdapterTest.java`, `M5VideoCutoverTest.java` | Cas direct scheduler, projection/adaptation, restore/ARM/ownership et guards ; lectures ciblées | Conserver équivalence Video lors de l'extraction de l'owner |
| `app/src/test/java/com/scenevibe/tvcompanionpoc/M5FrozenLegacyOracle.java`, `M5LegacyOracleCorpusTest.java`, `M5CandidateDifferentialTest.java`, `M5NegativeSensitivityTest.java`, `M5SinkFailureDifferentialTest.java` | Oracle/corpus et sensibilité négative ; usages et cas inspectés, pas rerun ici | Importer gates réellement clos après M5, sans redéfinir oracle |
| `app/src/test/resources/m5-phase-b/corpus.json`, `app/src/test/resources/m5-phase-b/oracle/` | Corpus gelé et sources historiques oracle | Strictement préserver |
| `tests/test_m5_phase_b_boundary.py`, `test_m5_phase_c_boundary.py`, `test_m5_phase_d_boundary.py`, `tests/m5_phase_b_provenance.py`, `m5_phase_c_provenance.py`, `m5_phase_d_provenance.py` | Gate D lu intégralement ; filiation C→B→M4, inventaires/patches finis et 269 retained starting blobs | Réconcilier admissions documentaires finies sur le vrai M5 merged |
| `.github/scripts/m5-phase-b-baseline.json`, `m5-phase-c-baseline.json`, `m5-phase-d-baseline.json`, `m5-media-differential.sh`, `m5-media-differential.init.gradle`, `m5-media-differential-summary.py` | Définition de gates de projection/différentiels et vérification des comptes | Reprendre tels que réellement fermés ; aucune copie de preuve PASS non exécutée |
| `app/src/androidTest/java/com/scenevibe/tvcompanionpoc/M4SonyDurabilityInstrumentation.java`, `.github/scripts/m4-sony-durability.sh`, `.github/scripts/m4-sony-durability.init.gradle`, `.github/scripts/m4-sony-durability-summary.py` | Gate natif persistance/read failure/process restart | Requalifier bundle Banner APIs 31/35 puis Sony distinctement |
| `.github/workflows/android-debug.yml`, `android-installation-durability.yml`, `android-15-smoke.yml` | Workflows existants et déclenchement PR vers main, sans exclusion docs | Inchangés ; aucune invocation manuelle de build ou dispatch en Phase A |

La clôture M4 rapporte le Sony final avec révision installée/ACK 5, restore local avant probe/Cloud et Unicode exact. Les checks de same-revision physique 10/18 ont été explicitement NOT RUN faute de trigger produit supporté ; leur acceptation M4 ne crée aucun nouveau mécanisme de redelivery.

Le rapport M5 D au HEAD exact rapporte 118 Python PASS, 1 050 JVM PASS / 0 FAIL / 1 SKIP historique, avec familles B/C/D 127/103/44, différentiels natifs API 31/35 et durabilité native. Son protocole Sony indique encore PHYSICAL SONY PENDING. Ces nombres sont des résultats hérités lus, pas une nouvelle exécution de cette tâche, ni une déclaration de clôture M5.

## 5. Inventaire Cloud exact et choix de frontière

Tous ces chemins ont été découverts dans l'arbre GitHub de `5011c91aac61a0cc6dcc74c256a15b7dee03d785`. Au total, 66 fichiers de contrats, code, routes, migrations et tests concernés ont été récupérés pour audit ; les responsabilités ci-dessous reposent sur lectures de contrats et méthodes ciblées, pas sur le seul nom d'un fichier.

| Chemins réels | État/responsabilité actuelle | Propriétaire | Traitement M6 futur |
| --- | --- | --- | --- |
| `packages/cloud/src/domain/types.ts` | Device account/installation/token, TvAssignment couplé FinalTrack | Device / Video historique | Device, identité et tokens inchangés ; ne pas faire de Banner un TvAssignment factice |
| `packages/contracts/src/os/device-assignment.ts` | Une ligne courante/device, account-scope, publication interne, révision monotone, chaque Send alloue | OS révisions | Réutiliser comme courant unique pour les deux kinds |
| `packages/contracts/src/os/display-publication.ts`, `render-package.ts`, `execution-requirements.ts` | Publication ready immutable, dedup prepare, profils opaques/validés et bornés, vocabulaire wall déjà représentable | OS publication/contrats | Réutiliser ; profil Banner statique réel et requirements vérifiés ; pas de nouvelle forme OS générale |
| `packages/contracts/src/os/overlay-manifest.ts`, `overlay-manifest-validation.ts` | Contrat commun de scène, clocks media/wall, pas de scheduling runtime | Rendu commun | Réutiliser ; croisement de profil Banner ; pas de contrat Video v1 modifié |
| `packages/cloud/src/os/assignment-engine.ts`, `repositories.ts` | Auth device avant get/ack, producer ports, prepare sans Send ; get compile-on-demand Video, ACK courant/publication CAS | OS core / ports | Réutiliser garanties ; Banner ready-only et dispatch statique au point de composition |
| `packages/cloud/src/os/postgres-repositories.ts` | Publication TEXT exact/digest via codec injecté, ready CAS, assign lock stable devices row puis revision, mirror exact, ACK courant | OS durable | Réutiliser schéma/transactions ; gate cross-kind réel, pas JSONB |
| `packages/cloud/src/os/unit-of-work.ts` | Client Postgres partagé BEGIN/COMMIT/ROLLBACK ; InMemory exige staging des mutations pour rollback | OS transaction | Réutiliser ; les tests DB-free ne prouvent pas rollback réel |
| `packages/cloud/src/os/input-adapter-registry.ts`, `video-adapter-registration.ts` | Registry en mémoire, wiring statique Video au point de composition | OS composition / Video | Second adapter Banner explicite ; pas de plugin/discovery |
| `packages/cloud/src/services/video-assignment-service.ts` | SHADOW : legacy seul allocateur, mirror OS exact ; réparation get sans nouvelle allocation ; CUTOVER distinct | Video compat / autorité | SHADOW inchangé maintenant ; futur mode OS unique pour tous les routes/kinds, explicitement autorisé |
| `packages/cloud/src/services/cutover-coordinator.ts` | UOW atomique allocation OS/miroir Video et ACK ; finalTrackId attendu actuellement | Composition de transition | Extension Banner finie nécessaire ; conserver rollback et atomicité Video |
| `packages/cloud/src/postgres/repositories.ts` | Composition durable mixte et allocator legacy ; lock devices row avant tv_assignments | Compatibilité historique | Ne pas l'utiliser comme compteur Banner ; garder Video miroir seulement sous autorité OS |
| `packages/cloud/src/http/cloud-service.ts` | Factory production impose `revisionAuthorityMode: "shadow"`, codec Video injecté, routes/services Video | Composition live | Read-only Phase A ; transition future contrôlée bloquée tant que non autorisée/qualifiée |
| `packages/cloud/src/video/render-package-codec.ts`, `delivery-profile.ts` | Codec durable Video-only ; known codecs, semantic/body digest, validation requirements vs vrais artifacts | Producteur Video / trust boundary | Politique composite statique via port injecté ; branche Video byte-identique, unknown refusé |
| `packages/cloud/src/video/assignment-v1-facade.ts`, `services/assignment-service.ts` | Enveloppe v1 reconstruite champ par champ ; FinalTrack/runtimeTrack, optional overlay absent réellement en legacy | Video transport | Strictement préserver bytes/structure pour Video ; refuse ancien miroir quand Banner courant sous OS |
| `packages/cloud/src/http/handlers.ts` | Auth compte/device, validation GET/PUT/ACK et FinalTrack actuel | HTTP interne | Nouvelle frontière interne minimale discriminée ; aucun Partner API |
| `apps/web/app/api/v1/devices/[deviceId]/assignment/route.ts`, `ack/route.ts`, `assignment-status/route.ts` | Routes v1 et statut encore Video, thin handlers | Compatibilité Video | Réutiliser v1 pour Video ; extension composition/guard courant OS, pas Banner dans finalTrackId |
| `apps/web/app/api/v1/devices/route.ts`, `apps/web/app/api/v1/devices/[deviceId]/route.ts`, `apps/web/app/api/v1/finaltracks/route.ts`, `apps/web/app/api/v1/finaltracks/[id]/route.ts` | Devices, suppression et FinalTracks account-scoped | Device / Video | Préserver identité/revocation/delete/Video ; pas de faux FinalTrack Banner |
| `packages/cloud/migrations/0001_init.sql`, `0002_account_identities.sql`, `0003_os_publications_assignments.sql`, `0004_device_assignment_account_fk.sql` | tv_assignments FinalTrack FK non nullable ; publications et assignments OS, FK account/device/publication | Persistance | Tables OS suffisantes pour bundle ready ; pas de migration/schéma relâché pour contourner FinalTrack |
| `apps/web/preview-harness.tsx`, `apps/web/lib/control-plane/api-client.ts` | Preview/control plane actuels Video/FinalTrack | Produit Video | Pas de refonte UX en M6 A ; minimum publish/Send interne futur ; éditeur avancé M8+ |

Constat central : le durable OS Cloud est générique, mais la composition live, la validation des profils et l'allocator SHADOW sont encore Video. Une nouvelle route seule ne suffit pas. Le choix retenu étend la composition finie et utilise l'autorité OS existante dans un futur cadre explicitement autorisé ; il n'introduit ni compteur indépendant, ni table Banner d'éditeur, ni FinalTrack factice, ni assouplissement de validation des codecs.

### Tests Cloud correspondants, consultés sans exécution live

| Chemins réels sous `backend-tests/` | Garanties concernées | Gate M6 futur |
| --- | --- | --- |
| `cloud-device.test.ts`, `cloud-assignment.test.ts`, `cloud-assignment-status.test.ts`, `cloud-finaltrack.test.ts` | Device/auth/account, afterRevision, Send/ACK et contrat FinalTrack v1 | Régression v1 exacte ; cross-account et old v1 pendant Banner |
| `os-assignment-engine.test.ts`, `os-persistence.test.ts`, `os-synthetic-publication.test.ts` | Publication/generic assignment, ready/dedup, durable | Banner réel ready-only, pas seulement producteur synthétique |
| `os-cutover.test.ts`, `shadow-concurrency.test.ts`, `os-backfill.test.ts`, `os-backfill-atomic.test.ts` | Autorité, locks, ordre concurrent, parité/backfill et rollback | Une autorité Video/Banner et ACK reverse-order, première allocation concurrente, rollback DB réel |
| `os-input-adapter-registry.test.ts`, `os-import-boundary.test.ts` | Composition statique, core product-neutral | Ajouter Banner sans import producteur dans le core |
| `os-render-package-validation.test.ts`, `os-render-package-seal.test.ts`, `video-publication-packaging.test.ts` | Known codec/digests/requirements, seal immutable, bytes/profils Video | Profil Banner strict ; conserver golden Video et échec des inconnus |
| `cloud-real-columbo-postgres.test.ts`, `cloud-real-corpus-postgres.test.ts`, `tv-cloud-qualification.test.ts`, `fixtures/m3-baseline-golden-envelopes.json` | Corpus réel, interop v1, sept envelopes golden et corpus 94 du socle | Reprendre preuve réelle avec DB configurée ; zéro modification fixture/oracle |

Les suites conditionnées par `TEST_DATABASE_URL` ne deviennent pas PASS par simple lecture, typecheck ou skip. Aucun endpoint Cloud, device, publication, assignment, ACK, compte, DB ou mode de production n'a été muté dans cette Phase A.

## 6. Décisions arrêtées et alternatives écartées

| Décision | Motif issu du code actuel |
| --- | --- |
| WALL séparé de MEDIA | SceneEvent/calendrier M5 portent bornes/politiques MEDIA ; aucune extension opportuniste |
| Cloud résout les règles civiles en fenêtres UTC finies | Terminal sans zone/DST/récurrence ; package immutable, restore déterministe et offline borné |
| Anchor epoch/elapsed en mémoire, fresh wall à chaque callback | Heure civile peut sauter ; Handler uptime ne représente pas elapsed ; reboot/veille obligent réévaluation |
| Fenêtres half-open, latest-start puis ID, au plus EXIT+DUE | Un seul visuel et aucun replay des fenêtres ratées, comportement d'overlap déterministe |
| Handler Banner distinct et un bundle artifact | Même snapshot/installer M4 ; startMs du manifest ne reçoit pas un epoch ; typed state et validation croisée |
| Un owner d'activation commun | Le mécanisme token/génération M5 est réel et réutilisable ; aucun owner parallèle |
| Video/Banner alternatifs, révision commune | Store unique M4 et DeviceAssignment courant ; aucun mixeur/priorité inter-produit |
| Rendu inline natif seulement | Renderer texte/rectangle/table/group existant ; aucune image/police/data URI pour contourner M7 |
| Livraison interne commune, v1 Video conservé | SHADOW legacy ne sait pas allouer Banner ; autorité OS unique et codec composite statique nécessaires |
| ACK exact du bundle durable après ARM | Protection same/stale/new existante ; incoming same-revision ne doit pas fabriquer la preuve ACK |

L'architecture répond individuellement aux seize questions du work order. Elle fixe les bornes, UTC/DST, callbacks/générations/tickets, failures et redelivery, l'autostart résiduel Video, l'autorité Cloud et son rollback dangereux, diagnostics/sécurité, les phases B/C/D/E et les gates software/physiques.

## 7. Vérification exécutée en Phase A et anomalie héritée

Commande sur la base TV exacte, avant les deux ajouts :

```sh
PYTHONDONTWRITEBYTECODE=1 python -m unittest discover -s tests
```

Résultat réel : 86 tests, 85 PASS, 1 FAIL, 0 SKIP, exit 1. Failure :

`test_m4_sony_corrective_boundary.M4SonyCorrectiveBoundaryTest.test_retained_java_tests_fixtures_and_prior_inventories_have_no_exception`

Le test frozen refuse `docs/m4-final-sony-physical-closure.md`, document déjà présent dans `67b81045258b1692073c6927b956db4899c6ad1a` mais absent de l'inventaire autorisé. Cette failure est préexistante, indépendante d'un changement exécutable M6. Les deux nouveaux documents M6 ne figurent pas non plus dans ce freeze historique. Ils sont autorisés par le work order courant, pas par ce test M4 immuable. L'inspection complète de l'inventaire après rédaction doit donc retrouver exactement ces trois documents hors inventaire, sans autre addition.

La suite a été réexécutée après rédaction des deux documents : 86 tests, 85 PASS, 1 FAIL, 0 SKIP, exit 1, même test et même premier document M4 refusé. Le test s'arrête à cette première assertion. L'inspection complète du freeze retrouve exactement les trois documents annoncés, sans autre fichier hors inventaire. Aucun test/assertion, baseline, hash, allowlist, seuil ou skip n'est modifié pour obtenir artificiellement du vert. La réconciliation sur M5 merged et un work order futur devront traiter les admissions documentaires de façon finie en conservant tous les starting blobs et gates.

Contrôles locaux de portée : `git diff --cached --check` PASS ; inventaire complet : exactement deux ajouts autorisés ; les 227 blobs hérités et leurs modes sont identiques à la base Git. Les chemins de code/tests cités ont été vérifiés contre les inventaires réels TV/Cloud. Aucune exécution Gradle/JVM/native/Cloud/physique, génération manuelle d'APK, installation, workflow dispatch ou test Sony n'est nécessaire ou effectué pour cette architecture docs-only. Les tests Python existants caractérisent le socle ; ils ne qualifient pas WALL.

Les workflows existants sont inchangés et peuvent se déclencher automatiquement sur la création d'une PR Draft vers main, même documentaire. Leur statut doit être rapporté honnêtement dans le manifeste de PR ; un job de build du code M4 inchangé ne devient pas un APK Banner/WALL M6 ou une nouvelle preuve Sony. Ne pas utiliser un skip CI, changer un workflow ou télécharger/livrer un artefact comme qualification M6 pour cette Phase A.

## 8. Hypothèses à réconcilier après la clôture M5

| Hypothèse au HEAD `c9b0efd4acfaaae9ed7da13dcec505b2f653c548` | Contrôle obligatoire sur `BASE_TV_M6` réellement mergée |
| --- | --- |
| SceneEvent reste MEDIA, bornes et synthetic legacy inchangés | Relire les classes et tests réels ; WALL reste distinct |
| Un seul MediaCalendarScheduler branché ; legacy dormant | Vérifier toutes les instanciations/usages après derniers correctifs Sony |
| Projection Video immuable unique, payload hors core | Vérifier prepare/cache/restore/bind et identité de projection |
| Pending/active/retiring, token frais, génération capturée | Relire ports/owner et tests de refus/cleanup, pas uniquement la signature |
| DUE/EXPIRE ID vers le contrôleur sans retag | Vérifier forwarding de callbacks et guards exacts |
| Restore M4 précède probe/Cloud et ne repersist pas | Vérifier service et gates native/Sony réellement clos |
| Un controller/un owner natif, legacy compatible | Relire retraite immédiate et failures lors de Video↔Banner |
| Autostart demeure dépendant du grant média | Reconfirmer le résidu avant extension Banner connue |
| Suite/provenance finies et résultats reportés | Importer les commits, artifacts, counts et tests réellement clos ; pas les résultats provisoires de Phase D seuls |
| Cloud toujours au SHA de référence et SHADOW | Réauditer si le Cloud a avancé, figer baseline Cloud du futur cycle et autorisation d'autorité séparée |

Gate cumulatif : Sony M5 fermé → M5 mergé → nouveau main exact figé `BASE_TV_M6` → architecture relue → chaque hypothèse vérifiée → deltas explicitement réconciliés → gates M5 clos intégrés à la baseline. Aucune Phase B ne commence avant ces sept conditions. Ni cherry-pick ni dépendance Git à la branche M5 ne remplace ce gate.

Risques/inconnues résiduels : corrections éventuelles du Sony M5, comportement de suspension/FGS/dalle Sony et latence, clock OS erronée, horizon offline fini à renouveler, choix/déploiement autorisé de l'autorité OS Cloud et rollback après Banner. Ils sont des points de qualification future ; aucune permission, asset, multi-installation ou seconde autorité visuelle n'est actuellement nécessaire au choix retenu.

## 9. Clôture documentaire, publication et auto-audit

Le diff autorisé est exactement :

```text
ADD docs/scenevibe-os-m6-banner-wall-clock-architecture.md
ADD docs/m6-phase-a-architecture-report.md
```

Zéro modification Java, Kotlin, Python, TS/JS, JSON, DB/migration, API, workflows, Gradle, Manifest, ressources ou runtime ; aucun flip capability ni implémentation timer/handler/WALL/Banner. Aucun merge, Ready, force push, modification de la branche/PR #15 ou écriture Cloud.

Le manifeste final dans la description de la nouvelle PR Draft est le support de clôture content-addressed : il donne son numéro/URL, HEAD documentaire exact, tous les commits documentaires, parent exact, tree/blob SHA des deux documents, résultat Python final et auto-audit GitHub. Le commit ne peut pas contenir son propre SHA sans changer ce SHA ; l'identité finale est donc volontairement consignée dans ce manifeste externe et le rapport utilisateur, sans troisième fichier ni commit de code.

Avant de terminer l'exécution, les preuves suivantes sont obligatoires :

1. Relire la branche depuis GitHub et son commit : unique parent `67b81045258b1692073c6927b956db4899c6ad1a`, aucun parent/commit M5 importé.
2. Relire intégralement les deux documents depuis GitHub au HEAD exact ; vérifier leurs blobs et contenu exact contre les fichiers publiés.
3. Inspecter le compare/diff complet depuis `BASE_TV_M6_PHASE_A` : exactement deux fichiers ajoutés ci-dessus ; tous les blobs hérités inchangés.
4. Recontrôler TV main et Cloud main à leurs SHA initiaux, M5 branch/head à `c9b0efd4acfaaae9ed7da13dcec505b2f653c548` et PR #15 OPEN/DRAFT/unmerged, sans mutation de sa description.
5. Recontrôler nouvelle PR M6 OPEN/DRAFT/unmerged et son exact head/base ; consigner les checks automatiques observés sans inventer de PASS.

Ces contrôles clôturent la publication documentaire uniquement. Le futur protocole physique est défini dans l'architecture ; aucun APK n'est installé, aucun test Sony exécuté et M5 n'est pas déclaré terminé.

```text
M6 PHASE A ARCHITECTURE COMPLETE
IMPLEMENTATION BLOCKED PENDING M5 PHYSICAL CLOSURE / MERGE / RECONCILIATION
```

</details>
