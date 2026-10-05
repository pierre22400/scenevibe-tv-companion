# SCENEVIBE OS — M4 PHASE A CHARACTERIZATION

## Périmètre et provenance

Phase A uniquement, conformément au cahier des charges intégral
[`scenevibe-os-m4-tv-installation-architecture.md`](scenevibe-os-m4-tv-installation-architecture.md).
La Phase B n'est pas commencée. Production reste **SHADOW**.

Références GitHub vérifiées avant modification et avant publication :

| Référence | SHA |
| --- | --- |
| BASE_TV_M4 / main TV | `ecdf77bec9f93babf15239a63bf7f702fd7ca293` |
| BASE_CLOUD_M4 / main Cloud | `5011c91aac61a0cc6dcc74c256a15b7dee03d785` |
| HEAD de départ de la branche M4 | `d62865f6c8ccc5c90862a1e8a2c273cb9832e961` |
| Commit initial des tests | `1eb3dfb2c7516be9411e98c5f56a27ca77cdbfa0` |
| Compatibilité de l'itérateur JSON Android | `df62e29328b860c145dc7007dfaca23e7bb79108` |
| Compatibilité des exceptions JSON Android | `8f382a9c14cb0d682b9854c725b00847378aa430` |

Branche existante : `work/scenevibe-os-m4-tv-installation-001`.
[PR #14](https://github.com/pierre22400/scenevibe-tv-companion/pull/14)
conservée OPEN, DRAFT et non mergée. Publications en fast-forward, sans force.
Le SHA du commit documentaire et les checks de son HEAD sont consignés dans la PR.

## Résultats exécutés

| Suite | PASS | FAIL | SKIP | Total |
| --- | ---: | ---: | ---: | ---: |
| Baseline Python | 15 | 0 | 0 | 15 |
| Baseline JVM avant ajout | 178 | 0 | 1 | 179 |
| Nouveaux cas JVM Phase A | 30 | 0 | 0 | 30 |
| JVM complète après ajout | 208 | 0 | 1 | 209 |

Les 179 cas existants restent présents, dans 26 suites inchangées.
Le seul SKIP est `M1CloudInteropTest.originalColumboProjectionIsInstallable` :
qualification opt-in dépendant de `SCENEVIBE_M1_SONY_FIXTURES`, fixture éditoriale
privée non fournie à cette exécution. Aucun nouveau cas Phase A n'est sauté.

Les sous-corpus de 7 enveloppes et 94 cas sont inclus dans la suite JVM ; ils ne
doivent pas être additionnés aux 209 tests JUnit.

Exécution locale : Java 17, framework mockable produit par AGP 8.7.3 à partir du
SDK Android 35, classes de production inchangées, JUnit 4.13.2 et org.json
20240303. Compilation vérifiée contre l'API JSON Android et exécution avec la
bibliothèque JSON utilisée par les tests. Les builds Android complets, lint et
signature font autorité dans GitHub Actions.

Preuves CI sur le commit des tests corrigés : [Android debug APK — run 37202643383](https://github.com/pierre22400/scenevibe-tv-companion/actions/runs/37202643383), SUCCESS ; [Android 15 / API 35 — run 37202643384](https://github.com/pierre22400/scenevibe-tv-companion/actions/runs/37202643384), SUCCESS, tous deux sur `8f382a9c14cb0d682b9854c725b00847378aa430`.
Les checks finaux du HEAD documentaire sont vérifiés séparément et liés dans
la PR #14. Le décompte CI provient des fichiers JUnit XML réellement produits
par Gradle, avec vérification de l'inventaire des suites et des hashes des
fixtures. L'artefact `scenevibe-m4-phase-a-test-summary` contient uniquement des
compteurs, noms de cas et hashes.

| Gate GitHub sur les tests corrigés | Résultat |
| --- | --- |
| Python boundary | 15 PASS / 0 FAIL / 0 SKIP |
| JVM Gradle + inventaire + hashes | 208 PASS / 0 FAIL / 1 SKIP |
| `lintDebug` | PASS |
| `assembleDebug` Consumer / Cloud non configuré | PASS |
| LAN DEV compile | PASS |
| Consumer Cloud-origin compile | PASS |
| Signature stable + vérification du certificat | PASS |
| Android 15 / API 35, contrôles de plateforme effectivement exécutés | 6 PASS / 0 FAIL / 0 SKIP |

Certificat SHA-256 vérifié, identique au certificat SceneVibe qualifié :
`f908bf564ed97ba67e02b1ebc89eb0239cf980752587f55eb9ec0419791a2e9c`.
La continuité de signature est prouvée par la signature stable du workflow debug ;
le workflow manuel `assembleRelease` n'a pas été déclenché dans cette Phase A.
Les capacités Android TV, fenêtres natives et vrai boot ne sont pas qualifiées
par le smoke Android 15 : ses contrôles physiques requis restent explicitement
hors du décompte des six vérifications exécutées.

Deux premières exécutions CI ont révélé des incompatibilités de compilation
dans les nouveaux doubles de test JSON (`keySet()` et exceptions vérifiées).
Les deux corrections ci-dessus sont limitées aux tests ; les résultats finaux
remplacent ces runs en échec. Aucun changement de production n'a été requis.

## Matrice de caractérisation

Les noms abrégés ci-dessous correspondent aux méthodes JUnit ; `Installation`,
`Transport`, `AtomicStore` et `Identity` désignent les quatre suites
`M4PhaseA…Test`. Les autres suites sont celles de la baseline conservée.

| Comportement figé | Preuve déterministe | Résultat |
| --- | --- | --- |
| Validation avant toute écriture | Transport `invalidEnvelopeNeverInstallsOrAcknowledges`, `bridgeFailureNeverAcknowledges` ; Installation `timingMismatchIsRejectedBeforeDurableInstall` | PASS |
| Durable install → arm → ACK → persistance de l'ACK confirmé | Transport `realFetchOrdersValidateCommitArmAckAndUsesCanonicalWire` : véritable `fetchAssignment`, POST et état du cache observés | PASS |
| Refus d'ACK si commit échoue | Transport `durableCommitFailureNeverAcknowledges` ; AtomicStore `productionAdapterHonorsFalseCommitWithoutLoadingScheduler` | PASS |
| Refus d'ACK si activation échoue après commit | Transport `activationFailureAfterCommitNeverAcknowledges` : révision durable en attente, récupération après recréation | PASS |
| Remplacement de révision visible | Installation `manifestedReplacementNeutralizesOldDueAndExpiryCallbacks` ; baseline `AssignmentMutationGateTest.visibleReplacementAndLegacyHandoffFinishOnOwnerBeforeAck` | PASS |
| Stale revision | Installation `staleRevisionCannotReplaceOrArmOrAdvanceAck` ; Transport `staleEnvelopeNeverAcknowledgesOrReplaces` | PASS |
| Same-revision redelivery manifestée, à chaud et à froid | Installation `sameRevisionRedeliveryDoesNotRepersistOrReplaceVisibleScene`, `coldRedeliveryConfirmsTheDurableManifestedCopy` | PASS |
| Same-revision redelivery legacy et refus de confirmation manifestée sans manifeste durable | Installation `legacyRedeliveryAfterRestoreKeepsLegacyOwner` ; Transport `sameRevisionWithoutDurableManifestCannotAcknowledgeManifestedDelivery` | PASS |
| Cache runtime + manifest + revision atomique | AtomicStore `productionManifestedAdapterCommitsOneCompleteBatchBeforeLoad` : véritable adaptateur Context/SharedPreferences, un éditeur et un commit | PASS |
| Manifested N → legacy N+1 | Installation `manifestedToLegacyReplacementDisarmsBeforeAckEligibility` ; AtomicStore `productionLegacyAdapterRemovesManifestWithinTheReplacementBatch` | PASS |
| Recréation de processus / restauration armed-not-visible | Installation `manifestedCacheRestoresArmedNotVisibleAndPreservesUnicode`, `legacyCacheRestoresThroughLegacyOwnerWithoutClearingIt` | PASS |
| Continuité revision / acknowledged revision | Installation `acknowledgedRevisionAdvancesOnlyForTheInstalledRevision` ; Transport `failedAckCanBeRetriedIdempotentlyFromDurableCache`, `wrongAckConfirmationKeepsPreviousAcknowledgedRevision`, `failedAckPersistenceLeavesRevisionPending` | PASS |
| Aucun demi-cache corrompu restauré | Installation `corruptManifestOrRuntimeCannotHalfRestore` ; baseline `CloudManifestCacheTest` | PASS |
| Video manifesté via le scheduler média | Installation `manifestedCacheRestoresArmedNotVisibleAndPreservesUnicode` : événement réellement émis par `MediaSyncedTrackScheduler`, un show et zéro render legacy | PASS |
| Legacy sans manifest | Transport `legacyWireWithoutVisualModeOrManifestStillWorks` ; Installation `legacyCacheRestoresThroughLegacyOwnerWithoutClearingIt` | PASS |
| Unicité du visual owner | Remplacement/handoff ci-dessus, maximum d'une scène visible ; baseline `SceneRuntimeControllerTest.exactlyOneVisualPathSelectedDeterministically` et Python boundary | PASS |
| Neutralisation des callbacks périmés | Due/expiry d'ancienne génération après remplacement ; baseline `AssignmentMutationGateTest.interruptedQueuedMutationNeverRunsLater`, `stoppedClientCannotApplyQueuedRevision` | PASS |
| Unicode UTF-8, accents composés, ligature, apostrophe et emoji | Transport `unicodeSurvivesTheRealHttpCodecAndCacheRestore` : décodage HTTP réel, deux artefacts, parsing et recréation | PASS |
| Compatibilité de l'enveloppe Cloud v1 et de l'ACK | 7 enveloppes M1 intactes ; ancien envelope sans visualMode/manifest accepté ; POST contient exactement revision et finalTrackId | PASS |
| Identité / pairing / credentials distincts du cache | Identity `installationAndProcessRecreationPreserveIdentityPairingAndCredentials`, `corruptTrackCacheDoesNotResetIdentityPairingOrCredentials`, `disconnectPreservesPairingAndRestorableManifestedCache` ; baseline identity/reset | PASS |
| Pause, seek arrière, seek avant et perte d'éligibilité conservés | Baseline `MediaSyncedTrackSchedulerExpiryTest`, `MediaSyncedTrackSchedulerIdentityTest`, `SceneRuntimeControllerTest` | PASS |
| Diagnostics bornés, aucun secret/payload dans les nouvelles traces | Baseline `DiagnosticsNoSecretTest` ; traces ajoutées limitées aux étapes et révisions, assertions sensibles booléennes | PASS |

## Fixtures et compatibilité

Les nouvelles fixtures de cache utilisent exclusivement les clés historiques
`revision`, `runtime`, `manifest` lorsqu'il existe, et `ackRevision`.
Elles dérivent de la première enveloppe Cloud M1 déjà commise ; seul le texte
synthétique Unicode est substitué dans le commentaire et la primitive texte.
Il s'agit de fixtures représentatives du format qualifié, **pas d'une extraction
du cache privé Sony**. Aucune donnée éditoriale privée n'est publiée.

| Fixture | SHA-256 |
| --- | --- |
| 7 enveloppes Cloud M1, inchangées | `19ee6fc3e2eba7009c9d345c33a63ca5617621fac9c8f5618cb24ee2932d6eab` |
| Corpus OverlayManifest 94 cas, inchangé | `c78addf877b277e38f66c12274e4c8ed2ce12a503d883e27fd9950215cafbf30` |
| Cache manifesté : revision 13 / ackRevision 13 | `76d109c4b69f3e707912449f87e3fa6b9081e4564a12c77c633ae7ed7563beb1` |
| Cache legacy : revision 14 / ackRevision 13 | `1676ed2033ec0e589509da7bdab3294ed8b5949153eeb2cfdb539aa23409cc8c` |

L'inspection du diff et la comparaison des blobs Git confirment que tous les
sources de production, contrats, anciennes suites, fixtures M1, manifest Android,
ressources, configuration Gradle et signature restent inchangés. Le dépôt Cloud
n'est pas modifié. L'horloge Video reste `MediaSyncedTrackScheduler`.

## Sémantique de l'échec après commit

Un refus de validation ou de commit conserve le tuple durable antérieur.
Un échec de retrait/activation **après** le commit ne l'annule pas : le nouveau
tuple runtime/manifest/revision est durable, l'ancienne acknowledged revision
reste conservée et aucun POST d'ACK n'est autorisé. La recréation restaure le
nouveau tuple en armed-not-visible. Ce comportement a été exécuté et doit rester
explicite lors des futures phases d'installation ; aucune politique de rollback
future n'est choisie pendant Phase A.

La redelivery legacy caractérisée suit l'ordre réel de démarrage : restaurer le
scheduler avant le premier fetch. La redelivery manifestée reconfirme la copie
durable, même si les textes entrants diffèrent. ACK signifie installé et armé,
sans exiger une scène déjà visible.

## Fichiers ajoutés ou modifiés par Phase A

- Ajout : `app/src/test/java/com/scenevibe/tvcompanionpoc/M4PhaseAFixtures.java`.
- Ajout : `M4PhaseAInstallationTest.java`, `M4PhaseATransportTest.java`,
  `M4PhaseAAtomicStoreTest.java`, `M4PhaseAIdentityTest.java` dans ce même dossier.
- Ajout : `app/src/test/resources/m4/video-manifested-cache-v1.json` et
  `video-legacy-cache-v1.json`.
- Ajout : `.github/scripts/m4-phase-a-baseline.json` et
  `.github/scripts/m4-phase-a-test-summary.py`.
- Modification : `.github/workflows/android-debug.yml`, uniquement pour vérifier
  et conserver les compteurs et hashes de la caractérisation.
- Ajout : `docs/m4-phase-a-characterization-report.md`.

Le cahier des charges architectural préexistant n'est pas modifié.

## Gaps et limites avant Phase B

**Aucun blocker de caractérisation restant**. Les preuves CI et les checks
finaux sont consignés dans la PR. Les 30 nouveaux cas s'exécutent sans fixture privée.

Limites de la preuve, sans les présenter comme PASS physiques :

- « reboot » en JVM signifie recréation des cores sur le même stockage ; les
  fenêtres natives, le boot receiver Android réel et une coupure de disque ne
  sont pas simulés. La qualification Sony antérieure reste une observation
  distincte et aucun nouveau test Sony n'a été exécuté dans Phase A.
- Le double SharedPreferences prouve le batch effectivement demandé par le
  code de production et l'usage du résultat commit ; il ne prouve pas le
  comportement physique du stockage flash en cas de coupure électrique.
- Le transport .invalid remplace les connexions sans socket, pour exécuter le
  codec et la décision d'ACK du client réel ; il ne constitue pas un nouveau
  Send sur une TV ou une validation de disponibilité Cloud.
- Android 15 demeure un smoke de plateforme, pas une qualification physique TV.
- La fixture privée Columbo opt-in reste disponible pour une exécution équipée ;
  son SKIP n'affecte pas les 7 enveloppes publiques ni les 30 nouveaux cas.

Les tests des futurs modèles/capabilities, de PREPARE séparé, du store générique,
de sa migration et des handlers relèvent des phases B à G autorisées ensuite par
un autre cycle. La qualification Sony finale du M4 refactoré reste obligatoire
selon le protocole architectural. Aucun de ces composants n'est créé ici.

## Décision

**READY FOR M4 PHASE B**. Les gates des tests corrigés sont vertes ; les checks
du HEAD incluant ce rapport sont également contrôlés avant la clôture de la PR.
Phase A ne donne aucune autorisation de merge, de cutover Production ou de
démarrage automatique de Phase B dans cette session.
