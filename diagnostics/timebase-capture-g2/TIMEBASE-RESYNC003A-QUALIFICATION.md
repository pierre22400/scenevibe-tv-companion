# TIMEBASE RESYNC 003-A — Qualification et non-régression

Alex — 1er novembre 2026. **PASS SOFTWARE 003-A**, sur contrôles synthétiques possédés uniquement. **Nouvelle qualification Sony/Netflix : NOT EXECUTED.**

## Traçabilité

- Audit demandé : code G2 `4213cb060bb77cd0cd3f986001861acd1ea3b987`.
- Base de mise à jour : `6100de73c02794e9eb55ebf8b472b74dc09ef85b`, PR #39 ouverte en brouillon, branche `work/scenevibe-timebase-resync-003`.
- Code et APK qualifiés : `753aa18de4c82d7d608a065cb517b6cb67b60471`.
- [Workflow final 41295948760](https://github.com/pierre22400/scenevibe-tv-companion/actions/runs/41295948760) : build + Android TV API31 + Android API35 standard, trois jobs SUCCESS.
- Premier passage `41295643268`, code `7e70f2b4f34b2d12afc272ee7697d7edad141b58` : également SUCCESS sur les trois jobs. Le deuxième commit distingue ensuite le timestamp non observé (null) du zéro réellement lu. Seule l'APK du passage final est livrée.
- Le SHA documentaire final est fourni dans `DELIVERY-TRACEABILITY.json` et la PR ; il ne change pas le code compilé.

Le journal brut Sony n'était pas disponible. Le rapport d'audit sépare l'extrait **rapporté par Pierre**, les calculs, le code observé et les hypothèses. La cause exacte du rejet Sony reste UNKNOWN ; aucun ancien événement n'a été reconstruit.

## Résultats réellement exécutés

| Contrôle | Résultat | Nature |
|---|---|---|
| G2 portable | 77 invariants PASS | 57 existants conservés + 20 sur premier timestamp et causes de refus ; données fabriquées |
| G1 portable | 47 invariants PASS | Réexécution des sources G1 inchangées |
| Frontières G2 | 7 tests PASS | Manifeste, autorité, isolation, inspection, documentation et build indépendant |
| Android TV 12/API31, x86, tv_1080p | G1 7/7 + G2 10/10 PASS | MediaProjection réel avec consentement système sur émulateur |
| Android API35 standard, x86_64, pixel_2 | G1 7/7 + G2 10/10 PASS | Image standard, **pas Android TV** |
| Lint | 0 erreur, 5 avertissements | Avertissements existants : targetSdk non dernier, trois dépendances de test, règles backup |
| Signature / identité / permissions | Vérifiées | APK exacte, versionCode 2, signature v2, deux permissions FGS seulement |
| Companion / Studio / Cloud / FinalTracks | Aucune mutation | Pas de nouvelle suite fonctionnelle Companion exécutée ; isolation vérifiée par diff et SHA-256 |
| Sony / Netflix / OCR / déploiement | NOT EXECUTED | Hors autorisation de cette mission |

Les neuf contrôles Android G2 existants sont conservés : refus, arrêt avant image, révocation en attente, absence de départ, retour à préparation, public/inspection/effacement, mixte, secure et expiration réelle de la ROI à 30 s. Le dixième, `staticFirstFrame`, affiche une Activity possédée immobile sans invalidation périodique, obtient le consentement système et vérifie le motif déterministe après fermeture des ressources. Aucun screenshot ADB n'est utilisé.

## Preuve du contrôle statique

Référence : génération **3** dans les journaux G2 de chaque banc ; test `staticFirstFrame` dans les journaux d'instrumentation. Les scripts conservent le nom hérité `g1-owned-controls.jsonl` dans le dossier **timebase-capture-g2/proofs** ; le package lu est bien G2 et le fichier interne `files/g2.jsonl`. Les journaux G1 sont séparés dans **timebase-capture-g1/proofs**.

| Mesure observée, génération 3 | API31 TV | API35 standard |
|---|---:|---:|
| ImageReader créé après déclenchement | 251 ms | 251 ms |
| VirtualDisplay créé après déclenchement | 253 ms | 277 ms |
| Première image reçue / acceptée après déclenchement | 283 ms | 310 ms |
| Fermeture après déclenchement | 306 ms | 372 ms |
| Images reçues / rejetées / fermées | 1 / 0 / 1 | 1 / 0 / 1 |
| Acquisitions logiques utiles | 1 | 1 |
| Première décision | ACCEPTED | ACCEPTED |
| Génération du lecteur / callback / tentative | 3 / 3 / 3 | 3 / 3 / 3 |
| Identifiant technique VirtualDisplay | 7 | 7 |
| Erreurs de fermeture et de journalisation | 0 | 0 |
| Motif synthétique | 4 marqueurs retrouvés | 4 marqueurs retrouvés |

Ce test démontre qu'**une seule Image reçue suffit** et qu'aucune deuxième réception n'est nécessaire. Il ne compte pas les compositions internes totales du système Android ni d'éventuels buffers abandonnés par acquireLatestImage. La fixture ne demande aucune animation ; aucune conclusion sur un lecteur tiers n'en découle.

Sur l'ensemble des cinq captures G2 de chaque suite, chacune accepte sa première image : 5 Images reçues, 5 fermées, zéro rejet, cinq tentatives distinctes. Le contrôle secure ne livre aucun marqueur exploitable. Tous les états terminaux journalisés ferment leurs ressources sans erreur ; aucun FATAL AndroidRuntime observé dans le passage final. Les copies de l'APK dans les deux artefacts de banc ont le même SHA-256 que la livraison.

Les timestamps réellement reçus sont non nuls sur ces émulateurs. Le premier timestamp zéro et les valeurs d'origine arbitraire sont vérifiés par tests portables, **pas par une capture Sony ni par une injection native de timestamp nul**. Les refus de génération/délai/doublon sont également des scénarios portables ; aucun rejet réel n'est inventé dans les journaux des contrôles positifs.

## Garanties et limites

La condition de 250 ms demeure. Le nouveau lecteur et le VirtualDisplay sont créés après cette barrière, avec une file neuve propre à la génération. L'échéance reste ancrée au déclenchement +10 s. L'identité du lecteur, la génération immuable du callback et le display actif sont contrôlés avant lecture. Aucun buffer d'une ancienne file n'est importé, aucun second consentement, retry ou polling n'est ajouté.

La fraîcheur attestée est la provenance du pipeline courant ; elle n'est pas une mesure de l'âge absolu des pixels ou une reconnaissance de l'application affichée. Le cadrage, le contenu du premier buffer et les chiffres doivent encore être constatés humainement lors d'un futur essai autorisé. Aucune image noire n'est transformée en conclusion Netflix.

ROI 30 s, effacement manuel/sortie/expiration, FLAG_SECURE, maxImages=2 et acquisition unique restent en place. Aucun réseau, audio, OCR, Accessibility ou commande de lecteur. Les pannes natives de VirtualDisplay, les callbacks de lecteur étranger injectés, le changement réel de résolution et la pression mémoire 4K n'ont pas été provoqués. Le compteur de 45 s rapporté sur Sony n'a pas été rejoué physiquement ; les essais Android utilisent le délai synthétique de 3 s. Les limites extrêmes de politique sont testées avec horloges fabriquées.

## APK livrée et compatibilité importante

`scenevibe-timebase-capture-g2-003a-private-diagnostic.apk` — **PRIVATE DIAGNOSTIC — NOT FOR DISTRIBUTION**, debuggable.

- Package : `com.scenevibe.timebasecaptureg2` ; versionCode **2** ; versionName `0.0.2-g2-003a-private-diagnostic` ; minSdk31 / targetSdk35 ; **57 850 octets**.
- SHA-256 APK : `4f46e085d0d5d5c04a1a3fb8d8ae6669d767cb3adc20669ebdb8743687f06b94`.
- Certificat Android Debug RSA-2048, SHA-256 : `67ab2f50df005d4695759e8da9d83b7dbda52040c54b2e68f2d9eee7760d2de0`.
- Signature : VERIFIES, v2=true ; v1/v3/v3.1/v4/SourceStamp=false. Aucun matériel de signature privé livré. Certificat distinct de M5.
- Permissions finales : `android.permission.FOREGROUND_SERVICE`, `android.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION`.

**Le certificat diffère de celui de la v1** (`37e5c807dfbdfbdd0ca834fa9afed384243a2a2d68f415e9c8a2c0a87aa4233a`). La clé de diagnostic éphémère de la v1 n'est pas disponible : une mise à jour `install -r` de cette ancienne sonde n'est donc pas compatible. Le versionCode supérieur ne contourne pas cette règle. Une future décision physique devra d'abord préserver les journaux de la sonde puis traiter séparément son installation ; aucune suppression ou installation n'est exécutée ici. Le Companion et sa signature M5 ne sont jamais concernés.

## Reproduction logicielle

JDK Temurin 17, Gradle 8.9, AGP 8.7.3, SDK/build-tools35.0.0, compileSdk35. Le code final est dans la PR #39. Le patch livré s'applique à la base 6100de7 et reconstruit le tree du code qualifié ; aucun dépôt complet ou dépendance n'est archivé.

```bash
export GITHUB_SHA=753aa18de4c82d7d608a065cb517b6cb67b60471
bash diagnostics/timebase-capture-g2/tests/core-checks.sh
python3 -B -m unittest discover -s diagnostics/timebase-capture-g2/tests -v
gradle -p diagnostics/timebase-capture-g2 --no-daemon --max-workers=2 :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug
bash diagnostics/timebase-capture-g2/tests/emulator-checks.sh
```

Le dernier script ne cible que emulator-5554 et exige ro.kernel.qemu=1 avant installation. L'instrumentation refuse les matériels autres que ranchu/goldfish. Le workflow reconstruit aussi G1 et exécute ses sept contrôles Android sans en modifier les sources. Un rebuild avec une nouvelle clé debug ne garantit pas l'identité octet par octet de l'APK ; l'artefact livré est précisément identifié par son SHA-256.

Les sources modifiées, patch, empreintes, journaux expurgés, rapports, inventaire et preuve d'isolation sont inclus. Les octets originaux des preuves reçues restent conservés ; les règles d'expurgation et les SHA-256 avant/après sont documentés dans l'archive. Le brut du journal Sony et son empreinte sont explicitement indisponibles.

**STOP après livraison logicielle. Aucun nouveau PASS physique Sony ou Netflix.**
