# TIMEBASE RESYNC 003 — Rapport logiciel G2

Alex — 1er novembre 2026. **PASS SOFTWARE G2 PREPARATION**. **G2 physique : NOT EXECUTED**. Aucune conclusion Sony ou Netflix, aucun OCR.

## Références et artefact qualifié

Base intacte : `fe3a797197ed4bc36459f1cb4e7fc8627bf88dc4` (PR #38). Code et APK qualifiés : `4213cb060bb77cd0cd3f986001861acd1ea3b987`. Branche : `work/scenevibe-timebase-resync-003`. [PR Draft #39](https://github.com/pierre22400/scenevibe-tv-companion/pull/39), dépendante de G1. [Workflow final 41286895277](https://github.com/pierre22400/scenevibe-tv-companion/actions/runs/41286895277) : trois jobs réussis. Le SHA documentaire final est fourni séparément dans la traçabilité de livraison ; aucun rebuild implicite de l'APK.

APK `scenevibe-timebase-capture-g2-private-diagnostic.apk`, **52 170 octets**, package `com.scenevibe.timebasecaptureg2`, versionCode 1, versionName `0.0.1-g2-private-diagnostic`, minSdk 31, targetSdk 35. APK **debuggable**, privée de laboratoire, non productive et non distribuable.

- SHA-256 APK : `9a58b141a0ce5e4a024fbb2765e68b82fab8f43d5855907d9d7c4967131064fd`.
- Certificat de diagnostic RSA-2048 / Android Debug, SHA-256 : `37e5c807dfbdfbdd0ca834fa9afed384243a2a2d68f415e9c8a2c0a87aa4233a`, distinct de M5. Clé éphémère du build, non livrée ; un rebuild peut avoir une autre signature et un autre hash.
- `apksigner verify` : VERIFIES, v2=true ; v1/v3/v3.1/v4/SourceStamp=false. V2 est le schéma présent pour cet artefact minSdk31.
- Permissions finales : `FOREGROUND_SERVICE` et `FOREGROUND_SERVICE_MEDIA_PROJECTION` seulement. Aucun Internet, audio, Accessibility, overlay ou stockage partagé.

Les copies de l'APK contenues dans les deux artefacts de capture ont le même SHA-256 que la livraison. Les journaux portent le commit exact. Les ZIP Actions téléchargés ont été vérifiés contre leurs digests publiés.

## Résultats exécutés

| Banc / contrôle | Résultat final | Nature de preuve |
|---|---|---|
| G2 portable | 57 invariants PASS | Java pur, horloges et buffers fabriqués ; pas une capture Android |
| Frontières G2 | 7 tests PASS | Manifeste, absence d'autorité, isolation, inspection, documentation et build indépendant |
| G1 portable réexécuté | 47 invariants PASS | Sources G1 inchangées ; résultats nouveaux du workflow |
| Android TV 12 API31, x86, tv_1080p | G1 7/7 et G2 9/9 PASS | MediaProjection réel et consentement système sur émulateur possédé |
| Android API35 standard, x86_64, pixel_2 | G1 7/7 et G2 9/9 PASS | Image **non TV**, MediaProjection réel et consentement système |
| Build / lint | PASS, 0 erreur, 5 avertissements | Gradle autonome : targetSdk non dernier, trois versions de dépendances de test, règles backup à moderniser |
| Companion / Prime / TEXT-AUDIO | Diff et empreintes inchangés | Pas de nouvelle suite fonctionnelle Companion exécutée ; les anciens résultats ne sont pas recopiés comme nouveaux |
| Sony / Netflix / application tierce | NOT EXECUTED | Interdits dans cette mission |

Les neuf scénarios G2 réellement exécutés sur chaque banc sont : arrêt avant image, expiration réelle 30 s avec zéro du tableau, refus réel du consentement et label SIMULATED_C sans ROI, composition mixte, absence de départ de préparation, fenêtre FLAG_SECURE, révocation réelle pendant l'attente, capture d'une seconde Activity possédée avec inspection/effacement et label SIMULATED_A, retour à la préparation pendant l'attente. Les motifs publics/mixes sont comparés en mémoire par le test ; le service n'en tire aucun classement A/B.

Les sept contrôles Android G1 sont réexécutés intégralement et sans modification : public, mixte, secure, refus, arrêt avant image, sortie de fixture et révocation. L'ancien test de diff global G1, conçu avant le nouveau projet G2, n'est pas réinterprété : son périmètre est remplacé par la preuve d'isolation depuis fe3a797.

## Mesures observées G2

Données tirées des journaux de tentatives, pas d'estimations Sony. Une « frame utile » signifie une ROI technique retenue, même noire, et **ne signifie pas horloge acquise**.

| Mesure | API31 TV | API35 standard |
|---|---:|---:|
| Buffer du display | 1920 × 1080 | 1080 × 1920 |
| Captures logiques retenues sur l'ensemble de la suite | 4, chacune dans une tentative distincte | 4, chacune dans une tentative distincte |
| Buffers reçus / fermés | 16 / 16 | 15 / 15 |
| Buffers rejetés d'amorçage | 12 | 11 |
| Projection pour les quatre captures | 3263–3387 ms | 3263–3372 ms |
| Déclenchement → fermeture, captures | 374–435 ms | 304–390 ms |
| Erreurs de libération journalisées | 0 | 0 |
| Crash AndroidRuntime final | Aucun observé | Aucun observé |

Références exactes : générations 2 (expiration), 4 (mixte), 6 (secure), 8 (public/inspection). Générations 1 et 9 : OWNER_STOP, zéro buffer ; 3 : CONSENT_DENIED ; 5 : ARM_EXPIRED_OR_PREPARATION_VISIBLE, zéro buffer ; 7 : SESSION_REVOKED, zéro buffer. Les motifs des générations publiques/mixes ont quatre marqueurs ; le contrôle secure n'en a aucun, vérifié par assertions Android. La durée détaillée du recadrage, le pic mémoire et le coût de fermeture isolé n'ont pas été instrumentés : **non mesurés**.

Les journaux G2 exportés par le script de banc conservent le nom hérité `g1-owned-controls.jsonl` **dans le dossier `timebase-capture-g2/proofs`** ; ils lisent bien le package G2 et `files/g2.jsonl`. Les vrais journaux G1 restent dans `timebase-capture-g1/proofs`. Ne pas les fusionner. `QUALIFICATION-OBSERVED.json` fournit les lignes techniques G2 séparées par API et génération.

## Défauts trouvés et corrigés

1. Premier workflow `41285987209`, commit c71e49e : crash lors de `eraseColor` sur un Bitmap immuable ; sur API35, l'arrêt propriétaire pouvait être enregistré comme révocation système. Correction : bitmap mutable, propriétaire de fermeture sérialisé ; inspection défilable pour atteindre les commandes.
2. Deuxième workflow `41286468908`, commit 5351027 : API31 entièrement PASS ; API35 signalait une ROI encore présente après une vérification Retour à 300 ms. Correction : effacement dès onPause et test de l'action explicite « Effacer et fermer ». Le constat C ne dépend plus d'une comparaison de ROI inexistante ; A/B restent conditionnés à une ROI valide et à une comparaison humaine déclarée.
3. Troisième workflow `41286895277`, commit 4213cb0 : build et deux bancs PASS. Les journaux des échecs sont conservés et identifiés comme tels ; aucune ancienne APK intermédiaire n'est présentée comme livraison.

## Limites et portée

Les mauvaises générations, doubles armements, délais extrêmes, refus d'ancien consentement, buffers nuls/tronqués, strides invalides et ROI hors bornes sont testés avec données fabriquées dans les 57 invariants. Ce ne sont pas des injections de buffers invalides dans le système Android. Aucune panne native de création du VirtualDisplay, suspension longue du système, changement réel de résolution/orientation ou pression mémoire 4K n'a été provoqué. Les scripts ne testent que les émulateurs et refusent les appareils physiques. La navigation est exercée par UIAutomator sur les contrôles possédés ; aucune télécommande Sony n'est qualifiée.

La capture utilise un display transitoire entier, puis une copie ROI. L'échelle/cadrage Netflix est inconnu. Une interface statique sans nouveau buffer après amorçage peut produire IMAGE_TIMEOUT. L'application ne sait pas vérifier l'identité du lecteur : le contexte et la comparaison sont humains. Les délais sont logiques et bornés, soumis à l'ordonnancement Android ; aucune garantie temps réel matériel n'est annoncée. L'expiration est vérifiée à chaque accès par elapsedRealtime et par timers, avec effacement explicite ; cela ne prouve pas l'effacement forensique des buffers système/GPU.

## Isolation et confidentialité

Les 562 fichiers de la référence ont été contrôlés par SHA-256, aucun modifié ou supprimé. Le diff distant du code ne comporte que 22 ajouts dans G2 et son workflow dédié ; les rapports dédiés sont ajoutés ensuite. Les HEAD de main, archive v26 et branches PR35–38 sont restés identiques. Aucun build du Companion ni aucune mutation Cloud. Aucune clé privée livrée.

Les exports sont UTF-8 sans BOM. `EXPORT-INTEGRITY.json` indique les SHA-256 avant/après et les comptes de chaque transformation ; la politique d'expurgation décrit les règles exactes. Aucun fichier image, dépôt complet ou dépendance n'est inclus. Les données de motifs sont fabriquées, les labels SIMULATED ne sont pas des constats physiques de Pierre.

## Décision

L'instrument est qualifié pour préparer une expérience, pas pour lire Netflix. **LEGAL REVIEW REQUIRED** avant tout projet d'essai physique. Le protocole Sony est documentaire ; installation, capture Netflix, OCR, intégration et fusion restent non exécutés et non autorisés par cette livraison.
