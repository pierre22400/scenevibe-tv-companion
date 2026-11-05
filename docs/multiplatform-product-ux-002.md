# MULTIPLATFORM PRODUCT UX 002 : Note de livraison (v20)

Mandat section 13 / addendum 002-A section 6. Fichier unique, sans archive de rapport redondante. Il ne consigne que des résultats réellement exécutés dans le cloud sur la branche `kiro/scenevibe-multiplatform-product-ux-002` (HEAD `4b52d1c`, descendant du gel `867a286`). Aucune revendication de PASS pour ce qui n'a pas été exécuté.

## 1. Le panneau de recalage

Le panneau SceneVibe s'ouvre via le raccourci d'accessibilité MUTE. Il affiche, en grands chiffres haut contraste, la position COURANTE de la Track, projetée **uniquement** depuis l'horloge propre de SceneVibe : SceneVibe ne lit ni n'affirme jamais le temps du film. Sont présents :

- la position COURANTE de la Track ;
- l'instruction « Regle le temps SceneVibe sur le temps affiche par ton film. » ;
- un conseil invitant à préférer la mise en pause du film ;
- la position candidate NOUVELLE, clairement distinguée de la position courante, que le spectateur compose.

Le temps est formaté **MM:SS** en dessous d'une heure et **HH:MM:SS** à partir d'une heure (jamais de forme ambiguë du type 65:15).

Pas D-pad préservés :

| Touche | Effet |
| --- | --- |
| HAUT / BAS | +/- 60 s |
| GAUCHE / DROITE | +/- 10 s |
| OK (appui long) | bascule un pas fin de 1 s |
| OK | valide |
| BACK | annule |

C'est toujours la **Track** qui se recale sur le film, jamais le film sur la Track. « Recaler et reprendre » ré-ancre et relance la lecture de la Track ; « Recaler sans reprise » ré-ancre mais reste en pause ; BACK n'applique rien et ne provoque jamais de reprise involontaire.

Le scénario de référence de la coupure publicitaire de cinq minutes (Track 10:15, film 05:15 => on compose 05:15 sur la Track => ré-ancrage exact sur 05:15 puis reprise) est couvert par un test automatisé.

## 2. Résultats de vérification réels (FEAT-001)

Chaîne d'outils pré-provisionnée (JDK 17, Gradle 8.9, build-tools 35.0.0), builds en ligne, aucune installation.

- **Garde de non-régression POC** : `python3 -m unittest tests.test_poc_contract` => **Ran 15 tests ... OK** (15/15). Garde de référence respectée.
- **Suite JVM/Robolectric** : `:app:testNetflixmanual003bUnitTest` => **1425 tests, 1 échec, 1 ignoré**. Le seul échec est `MultimediaTransportOriginTest > debugQualificationAcceptsOnlyLoopbackHighPort` (`assertTrue(BuildConfig.DEBUG)`, ligne 21) : **hors périmètre UX 002** (ce n'est pas l'un des fichiers modifiés) et **pré-existant prouvé** au gel `867a286` (même échec, même ligne). Non corrigé et non `@Ignore` conformément aux contraintes. Les suites UX 002 sont vertes : `NetflixManualPanelTest` 42/42 (0 échec / 0 erreur), `NetflixManualLaunchActivityTest` 5/5. Tests référencés par chemin, non recopiés : `app/src/testNetflixmanual003b/.../NetflixManualPanelTest.java` et `.../NetflixManualLaunchActivityTest.java`.
- **Assemblage** : `:app:assembleNetflixmanual003b` => **BUILD SUCCESSFUL**.
- **APK** (aapt2 dump badging) : package `com.scenevibe.tvcompanionpoc.netflixoverlay001`, versionCode **20**, versionName `0.8.2-tv-release-hardening-multiplatform-product-ux002`, compileSdk/targetSdk **35**.
  - Chemin : `app/build/outputs/apk/netflixmanual003b/scenevibe-multiplatform-product-ux002-v20-unsigned.apk`
  - SHA-256 : `070f1f05ef67a08be43ece46cf208f979a2013e1460452222d899c3c0c58a038`
  - Statut : **UNSIGNED** (`apksigner verify` => ne vérifie pas ; `signingConfig` null pour netflixmanual003b, état correct et attendu).
  - `app/build` est gitignored : les sorties de build ne sont **pas** committées.

## 3. Conclusion de signature : PARTIAL, BLOCKED SIGNATURE

La signature de production avec le certificat historique est **BLOQUÉE** dans cet environnement. Dépendance manquante précise : la clé privée de release historique n'est atteignable que via les quatre secrets de dépôt GitHub Actions consommés par `.github/workflows/android-release.yml` (`SCENEVIBE_ANDROID_KEYSTORE_BASE64`, `..._KEYSTORE_PASSWORD`, `..._KEY_ALIAS`, `..._KEY_PASSWORD`). Aucun n'est présent dans ce sandbox cloud ; aucun keystore/.jks n'est suivi dans le dépôt. Créer une clé de remplacement est interdit, et l'empreinte publique du certificat n'octroie pas la clé privée. Aucun secret ni keystore n'a été créé, récupéré, committé ou journalisé. Le livrable est donc l'APK v20 **non signé**.

Valeurs de référence pour continuité uniquement (aucune matière secrète, non reproduites/validées ici) :

- empreinte attendue du certificat (SHA-256) : `e49c665500106afc7fc3638c71a30f5e9352dd1f05bbba871fbaf6f2570df02a`
- APK v19 antérieur (SHA-256) : `bbb290750796ae3e860f8419af548836cdf9115487d15e8c0ce0a9889e9c8a6f`

## 4. Procédure de qualification Sony : NON exécutée

Cette procédure est **NON exécutée** et conditionnée à une décision explicite du propriétaire. **Aucun PASS physique n'est revendiqué.**

1. Installer l'APK v20 sur Sony BRAVIA VH1 / Android TV 12.
2. Ouvrir une application de streaming et lancer une lecture.
3. Appuyer sur MUTE pour ouvrir le panneau SceneVibe.
4. Choisir « Recaler et reprendre ».
5. Composer, avec le D-pad, la position de la Track jusqu'au temps affiché par le film.
6. Valider avec OK.
7. Vérifier que les événements TEXT / AUDIO se déclenchent sur la timeline éditoriale.
8. Appuyer sur RETURN pour revenir au lecteur.

Préalable : l'APK v20 est non signé (voir section 3) ; toute installation physique dépend donc de la levée du blocage de signature.

## 5. Limites restantes

- APK **non signé** : signature de production bloquée (section 3).
- **Aucune exécution physique Sony** : la procédure de la section 4 n'a pas été lancée ; aucun PASS physique.
- Les gardes Python des jalons **M4/M5** restent des **échecs pré-existants hors périmètre** ; non corrigés, non touchés.
