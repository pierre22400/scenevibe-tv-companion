# SCENEVIBE OS — M5 Phase A : architecture et caractérisation

Date : 2026-10-05. Phase A initiale : deux documents uniquement. Un correctif de qualification tests-only a ensuite levé le défaut d’inventaire documentaire, sans modifier aucun byte de production M5/M4.
Production : **SHADOW**. Aucune Phase B commencée, aucune nouvelle qualification Sony.

## Bases et publication

| Référence | Valeur exacte |
| --- | --- |
| Repository TV | `pierre22400/scenevibe-tv-companion` |
| BASE_TV_M5 / main vérifié au départ | `67b81045258b1692073c6927b956db4899c6ad1a` |
| Tree de la base TV | `beab095d735b61f457a71336d95325c49a6c463b` |
| Repository Cloud | `pierre22400/interface-scenevibe` |
| BASE_CLOUD_M5 / main vérifié | `5011c91aac61a0cc6dcc74c256a15b7dee03d785` |
| Branche nouvelle, créée depuis la base exacte | `work/scenevibe-os-m5-scene-event-calendar-001` |
| Commit d'architecture | `71a45d3a99bcc4d2373d7db477c7c566622d56dc`, parent exact BASE_TV_M5 |
| Commit de ce rapport / HEAD documentaire final | [Manifest Git final de PR #15](https://github.com/pierre22400/scenevibe-tv-companion/pull/15) ; parent `71a45d3a99bcc4d2373d7db477c7c566622d56dc`. Un rapport ne peut contenir le SHA de son propre commit. |
| Nouvelle PR vers main | [PR #15](https://github.com/pierre22400/scenevibe-tv-companion/pull/15), OPEN / DRAFT / unmerged |

PR #14 est CLOSED, non-Draft et merged à la base TV ci-dessus. Elle n'a pas été
modifiée ni réutilisée. Sa clôture Sony finale est **PASS** et reste autoritative.
Le freeze M4 antérieur à son merge mentionne encore Draft/unmerged : c'est une
chronologie, pas une nouvelle remise en cause du PASS ou une réécriture à faire.

Deux ajouts seulement :

- [scenevibe-os-m5-scene-event-media-calendar-architecture.md](scenevibe-os-m5-scene-event-media-calendar-architecture.md)
- [m5-phase-a-architecture-report.md](m5-phase-a-architecture-report.md)

Publication par commits ordinaires à un parent, branche issue du main exact,
updates fast-forward sans force ni rebase. Les deux commits Phase A n’ont modifié
que les deux documents ci-dessus. Le correctif de qualification ultérieur modifie
uniquement `tests/test_m4_sony_corrective_boundary.py` pour admettre trois chemins
documentaires exacts ; aucun source production, fixture, build, workflow ou document
M4 n’est modifié. La nouvelle PR reste OPEN / DRAFT / unmerged ; aucun passage Ready
ou merge n'est demandé ni effectué. Le manifest final donne les SHAs exacts
et l'état des checks relus après publication, sans les inventer dans ce rapport.

## Sources et preuves auditées

L'audit de comportement porte sur les sources de BASE_TV_M5, pas sur une
implémentation M5 supposée. Les huit blobs suivants bornent les principales
décisions de l'extraction ; ils ne changent pas dans Phase A.

| Source dans `app/src/main/java/com/scenevibe/tvcompanionpoc/` | Blob Git à la base |
| --- | --- |
| `ScheduledTrack.java` | `38219f1130a0243272d3b1df5ad2e63461d7cc1e` |
| `MediaSyncedTrackScheduler.java` | `441d983409d94ae6d5e31198ec739dec86166754` |
| `MediaIdentityMatcher.java` | `f022a48c78151a11508d7feb12ba91239191a43a` |
| `MediaSessionProbe.java` | `9c205b09ff5ffd85977250175eaab5019984b1b1` |
| `SceneRuntimeController.java` | `c5f3b96606a65374417771261f6631f402902de9` |
| `VideoPreparedState.java` | `eb0fa40cca9dfd350af38319cc701778a4ccf358` |
| `OverlayService.java` | `c464baed64d353e6b065db8ccc6655a72277857a` |
| `installation/PackageInstaller.java` | `0f973271d1f6c133adddff6a8136ffb9eef19fd5` |

Implémentations également lues pour fixer les frontières : `TrackParser`,
`OverlayManifest`, `VideoOverlayManifestBridge`, `SceneRenderer`, `OverlayRenderer`,
`DisplayCountdown`, les deux handlers Video, leur préparation/registry/ports,
`CloudV1InstallationAdapter`, et le socle M4 `ExecutionRequirements`,
`TvCapabilities`, `InstallRequest`, `PreparedInstallation`, `InstallationHandler`,
`InstallationHandlerRegistry`, `InstallationStore`, `AndroidInstallationBackend`.
Le wiring service/ports établit le restore avant probe/Cloud et le choix exclusif
des owners visuels ; le core proposé n'a aucune de ces dépendances.

Tests examinés pour leur comportement réel :
`MediaSyncedTrackSchedulerIdentityTest` (5), `MediaSyncedTrackSchedulerExpiryTest`
(6), `SceneRuntimeControllerTest` (16), `SceneRendererPreflightTest` (5),
`VideoOverlayManifestBridgeTest` (7), `DisplayCountdownTest` (1),
`M4PhaseEVideoInstallerTest` (16), `M4PhaseFAdapterTest` (7),
`M4PhaseGStartupTest` (5 méthodes × 4 profils), `InstallationModelTest` (18),
`InstallationStoreTest` (22), `PackageInstallerTest` (27).
Ces nombres décrivent les suites auditées et ne constituent pas une nouvelle
exécution JVM. L'inventaire d'`OverlayArmedNotVisibleTest` et les suites figées
A–G/correctives ont été rapprochés des sources et des rapports hérités.

Documents rapprochés : architecture M4 intégrale, rapport correctif hard-reboot,
clôture Sony finale, caractérisation Phase A M4, rapports et règles M4
d'installation/adapter/restore pertinents, protocole Sony et inventaires de
qualification. Le cahier des charges M5 Phase A fourni par l'opérateur a été
lu intégralement. Les vingt règles MEDIA ont chacune une ligne normative dans
l'architecture, un lien de source/test actuel et un verrou différentiel futur.

## Contradictions et résolutions

| Point rencontré | Résolution fondée sur la base |
| --- | --- |
| Baseline annoncée Python 86/0, mais main exact ne passait plus l'inventaire | Le défaut était un gate de provenance trop fermé après la clôture M4, pas une régression runtime. Le correctif tests-only admet exactement la clôture M4 et les deux docs M5 Phase A ; le rerun exact-head revient à 86/0. |
| Formulation « exact media identity obligatoire » | Appeler le matcher qualifié inchangé. Il admet mediaId exact en priorité puis son fallback actuel, y compris après un ID différent. Pas de nouvelle politique ID-only ou de nouvelle heuristique. |
| « Aucun second clock » versus compteur legacy | Ne créer aucune autorité/horloge d'exécution supplémentaire. Conserver l'estimation passive de la sonde et la durée visuelle legacy actuelle ; les scènes manifestées gardent l'expiration MEDIA. |
| « Ordre exact » de plusieurs expirations | L'ancien moteur utilise `HashMap.values()`. Conserver les mêmes mutations et comparer les journaux bruts sur le même environnement ; ne pas prétendre à un tri temporel garanti cross-VM. |
| Durée <=0 historique versus parser | Le test scheduler synthétique 0 garde « aucune fenêtre ». L'ingress Video continue de refuser <1000 ms ; aucune extension du contrat Video. |
| Stale revision protection du controller | La garde de révision est composée : rejet stale par installer, sélection active exacte par owner, génération par controller. `replaceRevision()` seul ne compare pas les révisions. |

Autres détails source verrouillés : forward **>5000**, backward **<−2000**,
retard strict **>2000** ; un DUE seulement par snapshot ; DUE avant EXPIRE en
lecture ordinaire ; forward expiring même PAUSED ; backward rearm sans DUE dans
le même snapshot ; null snapshot != unavailable ; PLAYBACK à chaque match ;
retour éligible avec position inconnue sans rearm différé nouveau.
Ces cas ne sont pas tous déjà couverts par des assertions retenues. Leur statut
ici est **caractérisation statique** ; leur exécution différentielle est exigée
en C, sans prétendre à une couverture physique nouvelle en A.

## Décisions et travaux différés

SceneEvent futur porte seulement id/start/duration. MediaCalendar est une liste
immuable dérivée de l'état validé et sa pause policy, sans payload, identité
Video, codec, durable revision ou persistance. Le scheduler MEDIA garde seulement
consumed/windows/ancre/éligibilité et un token externe. Le core compile sans
Android, JSON, Cloud, I/O, player, store ni clock APIs.

Le handler reste propriétaire de la projection unique depuis son prepared state.
Le binding Video extérieur retrouve les payloads et applique le matcher actuel.
Le controller recevra ID + génération sans être propriétaire du temps. L'owner
lie le token de l'activation à la génération effective du controller sans retag
d'ancien callback ni changement de l'ordre ARM M4.

Le plan minimal est **A–D** : B modèles purs et oracle figé ; C scheduler MEDIA
et adapters non branchés, corpus différentiel complet ; D migration Video vers
un core live unique, façade de compatibilité sans ancienne autorité temporelle,
gates automatisés puis qualification Sony ciblée. Une Phase E artificielle
n'est pas créée : le cleanup indispensable et le gate physique doivent tenir
dans D. A ne remplace aucun scheduler et n'ajoute aucun type exécutable.

Premier work order recommandé après levée du blocage : **M5 Phase B — pure
SceneEvent / MediaCalendar models and frozen differential oracle**. Il exige
modèles bornés/immutables, compile core sans classpath Android, oracle constitué
du vrai code de la base avec hashes/adaptations recensés, journal exact, fixtures
de toute la table de corpus, et aucun branchement de production. La comparaison
du moteur candidat complet commence en C. Ce work order n'est pas exécuté ici.

Restent volontairement différés : toute nouvelle politique de multi-DUE, ordre
canonical d'expiry, nouvelle heuristique d'identité, durable cursor, API Cloud,
nouveau codec, et M6 WALL/Banner, M7 assets/cache, M8+ produits/APIs/SDK/Connect.
Ils ne sont pas des prérequis à la neutralité du core.

## Qualifications effectivement exécutées dans ce cycle

| Contrôle | Résultat actuel / portée |
| --- | --- |
| GitHub bases TV/Cloud et état merged #14 | SHAs attendus vérifiés ; branche M5 initialement absente, worktree isolé à la base exacte. |
| Python correctif ciblé sur main intact | 10 tests : **9 PASS / 1 FAIL**, même inventory case que la suite complète. |
| `python3 -m unittest discover -s tests` sur main intact | 86 tests : **85 PASS / 1 FAIL**, zéro SKIP. |
| Python complète après ajout des deux docs | 86 tests : **85 PASS / 1 FAIL**, zéro SKIP ; même échec sur le document M4. Les trois paths sont indépendamment confirmés non admis. |
| Auto-audit documentaire | Matrice vingt règles et dix contrôles ci-dessous ; corrections locales effectuées avant push. |
| Sources/fixtures/config/M4 docs | Phase A : deux nouveaux docs seulement. Correctif de qualification : un test de provenance seulement ; surfaces exécutables, fixtures, config, workflows et documents M4 inchangés. |
| JVM / lint / APK / signature / smoke / native / Sony | Après le correctif tests-only : CI exact-head rerun successful pour debug, lint/build/JVM, signature, API 35 smoke et native durability 31/35. Aucune nouvelle qualification Sony physique. |

Preuves héritées exactes :
[rapport correctif M4](m4-phase-g-sony-hard-reboot-corrective-report.md), section
« Actual software-head qualification », software HEAD
`ba714e3d9b18376f6df7e426a49133ef0ab5c07b`, 776/0/1 JVM et 86/0 Python,
avec [debug run 37311428285](https://github.com/pierre22400/scenevibe-tv-companion/actions/runs/37311428285),
[smoke run 37311428316](https://github.com/pierre22400/scenevibe-tv-companion/actions/runs/37311428316) et
[native run 37311428439](https://github.com/pierre22400/scenevibe-tv-companion/actions/runs/37311428439).
La clôture finale qualifie physiquement le corrective APK de
`94b2ad47640151cf592c27e720cea4a8fbd8cd7f`, SHA-256
`c4b1ce2bc266e1a191dee97be8cde46cd96bcab02b94a6bdbf0ffab07846b135`,
signer `f908bf564ed97ba67e02b1ebc89eb0239cf980752587f55eb9ec0419791a2e9c`,
Unicode/reboot revision 5 PASS. Le seul SKIP JVM historique reste
`M1CloudInteropTest.originalColumboProjectionIsInstallable` dépendant de la
fixture privée, pas un PASS. Les checks physiques same-revision NOT RUN acceptés
par le protocole M4 ne sont pas remplacés par un nouveau mécanisme produit.

## Correctif de qualification et levée du blocage

Le blocage initial était exactement
`M4SonyCorrectiveBoundaryTest.test_retained_java_tests_fixtures_and_prior_inventories_have_no_exception`
dans [tests/test_m4_sony_corrective_boundary.py](../tests/test_m4_sony_corrective_boundary.py).
Le gate correctif M4 parcourait `app/src`, `tests`, `.github` et `docs` et refusait
tout path postérieur à son inventaire, y compris la clôture physique M4 elle-même.

Un correctif tests-only minimal a été publié au commit
`03a46c2084f4a9cffb7519e6dceb7cb217760944`.

Il n'ajoute aucune whitelist globale. Il admet exactement trois chemins :

1. `docs/m4-final-sony-physical-closure.md`;
2. `docs/scenevibe-os-m5-scene-event-media-calendar-architecture.md`;
3. `docs/m5-phase-a-architecture-report.md`.

Tous les hashes, assertions métier, fixtures, baselines M4, sources production,
workflows et règles correctives restent inchangés. Aucun test n'est supprimé,
skippé ou rendu permissif sur un répertoire entier.

Qualification GitHub sur ce correctif :

- Android debug run **37413662557**, job **112107428969** : SUCCESS ;
  `Check POC boundary` SUCCESS, Python **86 PASS / 0 FAIL**, Gradle/lint/build SUCCESS,
  JVM **776 PASS / 0 FAIL / 1 SKIP historique documenté**, LAN/Cloud APK builds SUCCESS,
  signature stable vérifiée ;
- Android 15 smoke run **37413662563**, job **112107428954** : SUCCESS ;
- native durability run **37413662588** : API 31 job **112107428987** SUCCESS,
  API 35 job **112107429161** SUCCESS ;
- certificat SHA-256 vérifié :
  `f908bf564ed97ba67e02b1ebc89eb0239cf980752587f55eb9ec0419791a2e9c`.

Ce correctif ne constitue pas une Phase B et ne change aucune sémantique du produit.
Il lève uniquement le défaut de qualification documentaire identifié par Phase A.

## Auto-audit du même cycle

| Contrôle du cahier des charges | Conclusion documentaire |
| --- | --- |
| 1. Relire diff exact | Deux ajouts docs seulement ; aucune reprise d'une phase M4. |
| 2. Assertions rapprochées de main | Constantes, comparateurs, ordre des callbacks, matcher, parser et composition controller/installer vérifiés sur BASE_TV_M5. |
| 3. Pas de second store | Calendrier mémoire seulement ; restore par M4, zéro cursor persistant. |
| 4. Pas de WALL | Core MEDIA-only, capability WALL toujours false, pas de timer/provider ajouté. |
| 5. Pas de Banner/M6 | Exclusions explicites ; compteur legacy préservé sans nouveau produit. |
| 6. Révision/ACK restent M4 | Même ordre installer et ARM, same/stale, pending durable et ACK confirmé séparés. |
| 7. Pas de payload graphique core | ID/start/duration et observations scalaires seuls ; payloads/matcher hors core. |
| 8. Différentiel sans double rendu | B/C non branchés, recording-only ; D unique moteur derrière façade ; oracle test-only. |
| 9. Chemin de non-régression physique M4 | Table des surfaces protégées, corpus exact et Sony ciblée D ; aucune qualification Sony réinventée en A. |
| 10. Corriger les défauts locaux | Ordre de HashMap conservé, seuils stricts, cas unknown position/null, compteur legacy et garde de révision réelle explicités ; liaison génération clarifiée sans changer ARM. |

L'architecture ne demande aucun changement Cloud/store/codec/permission/player,
aucune politique pause/seek/replay nouvelle et aucune décision physique préalable.
Le défaut de provenance documentaire est désormais levé par le correctif tests-only
ci-dessus. L’architecture reste inchangée, la PR est conservée Draft, Production SHADOW,
et aucune Phase B ni gate Sony nouveau n’est lancé dans ce cycle.

**READY FOR M5 IMPLEMENTATION**
