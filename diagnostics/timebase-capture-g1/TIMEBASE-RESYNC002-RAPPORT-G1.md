# TIMEBASE RESYNC 002 — Rapport de qualification G1

1er novembre 2026 — Alex pour Pierre. **PASS SOFTWARE G1 sur contrôles synthétiques possédés. G2 NOT EXECUTED. Aucun PASS Sony, Netflix, OCR ou resynchronisation.**

## Résultat vérifié

L'instrument autonome obtient une Image par MediaProjection consentie, extrait la ROI contenant notre compteur fabriqué et ses quatre repères, respecte les protections des contrôles et ferme ses ressources. La comparaison porte sur des pixels de motifs connus, pas sur une lecture des chiffres par OCR. La réussite ne démontre ni les pixels Netflix, ni le firmware Sony, ni une autorisation contractuelle de capture tierce.

Code et APK qualifiés : **`33e59cf26e5f2b3c077e978c797b0b3b2834706e`**. Branche `work/scenevibe-timebase-resync-002`, [PR Draft #38](https://github.com/pierre22400/scenevibe-tv-companion/pull/38), dépendante de #35, base `90b55c1c9dcb6894a8285c6a1dbe830a584bba8c`. Le commit final de documentation est identifié séparément dans la livraison ; aucun changement de code n'est inclus après le commit APK.

[Workflow final 41272817005](https://github.com/pierre22400/scenevibe-tv-companion/actions/runs/41272817005) : trois jobs SUCCESS, au commit APK ci-dessus.

| Vérification réellement exécutée | Résultat et portée |
|---|---|
| Cœur Java portable | 47 invariants PASS ; un programme de test, pas 47 tests Android |
| Contrats Python | 6/6 PASS : permissions, autorité, isolation, build, acquisition unique, documentation |
| Build et lint Android | APK et APK de tests compilées ; lint 0 erreur, 5 avertissements |
| Android TV12 API31, émulateur x86 tv_1080p | 7/7 JUnit PASS, vraie projection et ImageReader |
| Android15 API35 standard, émulateur x86_64 pixel_2 | 7/7 JUnit PASS ; ce banc n'est pas une TV |
| Signature et métadonnées | apksigner vérifie v2 ; hash APK recalculé après récupération, égal au hash CI |
| Isolation du code qualifié | 537 fichiers suivis de la base comparés octet pour octet, aucun changement |

Les sept tests natifs de chaque banc : publicPixels, mixedComposition, secureWindow, deniedConsent, stopBeforeImage, leaveFixture et projectionRevoked. L'automatisation accepte le vrai dialogue système ; aucun appop, faux jeton de capture ou capture UiAutomation ne remplace MediaProjection. Le test de révocation lit par réflexion un champ de **notre propre service**, puis appelle l'API publique stop() sur **notre propre projection**. Aucun accès à un lecteur tiers.

## Preuves précises des acquisitions finales

Références : `proofs/api31/g1-owned-controls.jsonl` et `proofs/api35/g1-owned-controls.jsonl`, sept lignes chacun. Les identifiants complets sont conservés dans les journaux et FINAL-MEASUREMENTS.json.

| Contrôle | Ligne | API31 | API35 |
|---|---:|---|---|
| Révocation après première image avec ROI négative | 1 | SESSION_REVOKED ; 1 image reçue/fermée | Identique |
| Arrêt avant image | 2 | OWNER_STOP ; 0 image | Identique |
| Refus de consentement | 3 | CONSENT_DENIED ; aucune projection | Identique |
| Départ de la fixture | 4 | OWNER_STOP ; 0 image | Identique |
| MIXED : compteur ordinaire + SurfaceView protégée | 5 | 4 repères, ROI_VISIBLE, surface créée, magenta protégé absent | Identique |
| PUBLIC : compteur et fond non protégés | 6 | 4 repères, ROI_VISIBLE, magenta ordinaire présent | Identique |
| SECURE : fenêtre entière FLAG_SECURE | 7 | 0 repère, ROI_NOT_VISIBLE | Identique |

Toutes les lignes de fin indiquent resourcesClosed=true, sans erreur de libération. Pour les quatre acquisitions avec image par banc, received=imagesClosed ; aucune image n'est persistée. Les deux arrêts pré-acquisition ferment la session sans image. Le refus est journalisé sans démarrer un service de capture.

Mesures uniques, pas des percentiles ni des performances Sony : demande→buffer PUBLIC/MIXED/SECURE = **1066/1092/1032ms sur API31**, **141/159/133ms sur API35**. Buffer→fin =4/5/3ms et10/5/4ms respectivement. Ces coordonnées sont elapsedRealtime des deux côtés ; aucune soustraction avec uptime ou timestamp Image. Les délais de consentement et de préparation ne sont pas inclus. CPU et pic mémoire natif/graphique ne sont pas mesurés.

## APK privée livrée

- Fichier : `scenevibe-timebase-capture-g1-private-diagnostic.apk`, **42118 octets**.
- Package : `com.scenevibe.timebasecaptureg1` ; versionCode1 ; versionName `0.0.1-g1-private-diagnostic` ; minSdk31, target/compile35.
- SHA-256 : **`c94437e9126b8e68d0bd1ab22230937e003b2597ca7a0d342ace82b0dfb0ea18`**.
- Certificat diagnostic SHA-256 : **`10257edd410ce2028e55e97d24dff7028d02ac73a21aec6a617c7b5167af3ecd`**. Android Debug RSA2048, distinct de M5. Clé privée non livrée.
- APK **debuggable**, volontairement privée pour le banc et run-as ; NOT FOR DISTRIBUTION, ne remplace pas le Companion. Signature v2 valide ; v1/v3/v3.1/v4 et SourceStamp absents, sans les présenter comme validés.
- Permissions finales : FOREGROUND_SERVICE et FOREGROUND_SERVICE_MEDIA_PROJECTION seulement. Aucun Internet, microphone, stockage partagé, overlay, root ou accessibilité.

## Non-régression et préservation

Les seules modifications concernent le nouveau diagnostic et son workflow indispensable à la compilation/qualification. Aucun changement de manifestes/build Companion, Prime, calendrier TEXT/AUDIO, cache, admission, Studio, FinalTracks, Cloud, branches historiques ou PR35/36/37. Aucune suppression de fichier existant. La comparaison distante et l'inventaire de livraison détaillent chaque ajout.

Les suites historiques complètes Companion/M5 ne sont pas rejouées comme condition de ce projet sans dépendance ; aucun ancien compte de tests n'est recopié comme résultat nouveau. Les contrats de frontière et la conservation des octets constituent la preuve de non-régression applicable ici, pas une nouvelle qualification physique du Companion.

## Corrections et échecs conservés

| Exécution | Résultat historique, correction |
|---|---|
| Local initial | javac absent de PATH : compilation portable via java com.sun.tools.javac.Main ; erreur de bord flottant ROI détectée puis corrigée avant premier push |
| 41271648307 / 8163981 | Build/lint réussis ; aapt refusait une icône système non résolue sur hôte : icône locale ajoutée |
| 41271795200 / 43a1aca | Banc relisait parfois une preuve de tentative précédente ; navigation négative pouvait fermer l'Activity : preuves réinitialisées et navigation corrigée |
| 41272107223 / 5e68d6d | API35 7 tests réussis mais script attendait la sortie brute sans -r ; API31 révélait une course FGS sur arrêt immédiat : promotion déplacée dans onCreate, fermeture sérialisée |
| 41272427383 / 650757a | API35 SUCCESS ; API31 6/7 : le test révoquait parfois avant VirtualDisplay, résultat sûr PROJECTION_UNAVAILABLE et ressources fermées. Le test attend maintenant une première Image avant révocation active |
| 41272817005 / 33e59cf | Build, API31 et API35 SUCCESS ; refus de consentement désormais journalisé sans service |

Les échecs ne sont pas effacés ou convertis en PASS. Les journaux initiaux sont livrés expurgés avec SHA originaux et transformations. Les images des contrôles n'ont pas été exportées.

## Limites et qualification bornée

La matrice de sécurité distingue tests natifs, invariants sur buffers fabriqués et branches non injectées. Les pannes spécifiques ImageReader/driver, une exception forcée de création VirtualDisplay, une rotation réelle pendant acquisition et une Image Android tardive forcée après arrêt ne sont pas qualifiées par injection exhaustive. Ne pas annoncer ces cas PASS. Le traitement d'erreur est présent et les chemins exercés ferment leurs ressources.

La limite10s est une échéance logicielle sur le worker ; les appels système synchrones ne sont pas préemptibles par l'app. Les contrôles finaux sont restés dans cette borne. Aucune performance à la seconde près ni comportement OEM n'est garanti.

Le garde G1 interdit de suivre un autre écran que notre fixture. Une future tentative Netflix exige un nouveau mandat G2, l'examen d'admissibilité et une modification explicitement cadrée de ce garde. Aucun OCR ni recalage du Companion ne découle du PASS présent.

**Décision proposée : GO G2 SONY REQUESTED, sous prérequis distincts. G2 NOT EXECUTED. STOP après livraison.**
