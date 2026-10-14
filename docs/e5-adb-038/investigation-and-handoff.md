# E5-038 — déclenchement ADB protégé

Date : 14 octobre 2026. Base immuable : `47f675dbd05218fb1caca2b090fd81c822e513e0`.
Branche isolée : `work/scenevibe-e5-adb-trigger-038`. Aucune fusion ni intervention Sony.

## Investigation

Le bouton `DiagnosticsActivity.requestE5Local` confirme la nature VOD puis envoie
`ACTION_E5_LOCAL` au service non exporté `OverlayService`. Ce service appelle
`requestE5Local`, configure les ports déjà existants et appelle
`SceneRuntimeController.requestLocal`. `NativeTransportOwner` conserve la sélection
Prime, les preuves foreground/session/identité/position, les capacités PAUSE/PLAY,
les confirmations et les règles de récupération. Le bouton ne réalise aucun
transport lui-même. Le propriétaire constate que lancer MainActivity fait sortir
Prime de la lecture ; remettre la tâche en arrière-plan ensuite ne restaure pas cet
état. Ce constat bloque le déclenchement, sans démontrer un défaut du renderer.

Les actions de service E5 existent mais `OverlayService` est non exporté. Aucune
entrée externe adaptée n'existe au HEAD vérifié. BootReceiver n'est pas une entrée
E5 et ne doit pas être détourné. Les rapports 035 restent des preuves historiques,
complétées par les commentaires ultérieurs de PR #18 ; ils ne sont pas réécrits.

## Choix Android et surface exposée

Un BroadcastReceiver existe uniquement dans le source set debug de qualification
explicitement opt-in. Aucun filtre d'intent : composant explicite obligatoire.
`android.permission.DUMP` protège sa réception côté système. Cette permission
plateforme signature/privileged/development est détenue par le shell ADB Android
12 et n'est pas demandée par Companion. Une application ordinaire ne peut pas
s'auto-accorder cette permission ; un acteur déjà privilégié ou autorisé par le
propriétaire via ADB appartient au périmètre de confiance. Ce n'est pas une
permission exclusivement réservée au shell. L'identité Binder dans `onReceive`
n'identifie pas l'émetteur original et n'est donc pas utilisée comme authentification.

L'action est fixe, la cible est fixe, le seul extra est le Boolean
`e5VodConfirmed=true`. URI, type, sélecteur, ClipData, catégories, extras inconnus
ou attestation absente/fausse sont refusés. Aucun chemin/fichier/commande arbitraire,
paramètre réseau ou ordre natif n'est accepté. Aucune Activity n'est lancée.

Diagnostics et le receiver emploient `E5LocalRequest.confirmedVod` : une seule
factory du même intent explicite vers OverlayService. Aucun nouveau moteur,
controller, decoder, renderer, calendrier, stockage ou chemin PAUSE/PLAY.
Le receiver vérifie le flag et l'autorisation overlay avant de demander le service.
Usage Access et notification listener restent requis par le chemin existant ;
leur absence ne déclenche aucune concession ou octroi automatique.

Android 12 autorise certains démarrages FGS depuis l'arrière-plan, notamment sous
SYSTEM_ALERT_WINDOW. Android peut néanmoins refuser le démarrage ; l'exception
est capturée comme `REFUSED_ANDROID_OR_PAYLOAD`, sans retry ni Activity de secours.
Android 15 impose des conditions supplémentaires ; le profil natif reste strictement
Sony/API31. Rien n'élargit cette cible. La commande reçoit `QUEUED_GUARDS_PENDING`
pour la réception et l'envoi au service, jamais une promesse de pause ou de succès.
Le diagnostic E5 est l'autorité pour les gardes et l'état final.

Sources :
- https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start
- https://developer.android.com/reference/android/Manifest.permission#DUMP
- https://android.googlesource.com/platform/frameworks/base/+/refs/tags/android-12.0.0_r1/packages/Shell/AndroidManifest.xml

## Sécurité et non-régression

La fixture, le renderer L0/L1, OverlayService, NativeTransportOwner,
NativeQualificationPolicy, SceneRuntimeController, les ports Android, les règles
pause/reprise, le manifeste main, les sources Cloud/LAN et toutes les autres sources
main restent byte-exact au HEAD E5, hormis la substitution de factory dans Diagnostics.
La nouvelle variante est versionCode 15 ; OFF/release gardent leur version historique
12 et aucun receiver de qualification. Aucun pairing/cache/configuration durable
n'est modifié par la correction. Le registre de provenance conserve toutes les
assertions anciennes et ajoute seulement des chemins et SHA précis et des inverses
littéraux exacts ; aucun wildcard ni skip ajouté.

La base E5 est dérivée du socle M5 ; elle ne contient pas le renderer Banner M6,
qui vit sur `work/scenevibe-os-m6-banner-wall-architecture-001`. E5-038 ne peut donc
pas revendiquer une qualification de Banner M6 dans cette APK. Aucune importation
M6 n'est autorisée ni effectuée. La branche M6 reste intacte. Une exigence de
coexistence effective E5/M6 dans un même binaire nécessite un mandat distinct ;
cette distinction de périmètre est conservée explicitement dans le verdict.
La non-régression du delta E5 est démontrée par les suites M4/M5/Companion,
les gardes E5 et les sources exclues byte-exact. M6 est contrôlé séparément au
HEAD `60d3e25c604cd1a0ad0c62f00d97b13c284ed103`, sans mutation de sa branche.

## Couverture et exécution

- Python : inventaire fini, voie commune Diagnostics/ADB, protection et fichiers
  exclus byte-exact. Première exécution complète : 128 tests PASS.
- JVM héritée : nominal PLAYING, PAUSED, sélection absente/ambiguë, capacités
  manquantes, autre application, double demande, flag OFF, changement token/média,
  intervention manuelle, perte session, cleanup et aucune reprise dangereuse.
  Les classes `NativeTransportOwnerTest`, `SceneRuntimeLocalInterludeTest` et
  `E5LocalPlanAndForegroundTest` restent inchangées (64 cas).
- Android : `E5AdbTriggerInstrumentation` teste 15 assertions d'enveloppe/protection
  et capture le même intent qu'envoie Diagnostics. Aucun transport réel.
- Android : `E5AdbSenderInstrumentation` s'exécute sous l'UID distinct du package de
  test, sans DUMP ; le système doit empêcher la livraison du même broadcast valide.
- Shell API31 : commande ADB réelle, témoin foreground Settings inchangé,
  refus normal de cible non Sony, commande malformée, endpoint absent APK OFF.
- Workflow isolé : OFF/ON, lint, tests, plateforme31 puis seulement signature avec
  la clé stable existante. Aucune génération/extraction de clé, aucun build Cloud,
  déploiement ou publication Play. Résultats finaux et preuves exactes ci-dessous.

## APK de référence

La référence exacte `SceneVibe_E5_47f675db_stable.apk` a été récupérée et vérifiée :
applicationId `com.scenevibe.tvcompanionpoc`, versionCode 14,
versionName `0.8.2-tv-release-hardening-e5-native035`.
Certificat SHA-256 :
`f908bf564ed97ba67e02b1ebc89eb0239cf980752587f55eb9ec0419791a2e9c`.
La pièce nommée `SceneVibe_Sony_installee.apk` est distincte : versionCode 13,
versionName `0.8.3-m6-phase-e`, même certificat. Elle n'est pas confondue avec E5.

La candidate n'est installée sur aucun appareil physique. La signature/version
rendent une future mise à jour en place admissible, mais la conservation physique
des données et des autorisations reste à constater par le propriétaire. Aucune
commande de désinstallation, clear data ou reset pairing n'est prévue.

## Verdict de livraison

**READY FOR OWNER PHYSICAL RETEST — profil E5 Sony/API31/VOD uniquement.**
Les gardes 035 restent inchangées et la commande implémentée a été exercée sur
Android 12 logiciel. Ce verdict autorise la présentation de la candidate au
propriétaire ; il ne déclenche ni mise à jour, installation, test Sony ou fusion.
La non-régression M6 porte sur sa référence séparée inchangée, pas sur une
coexistence de Banner et E5 dans un nouveau binaire. Cette coexistence ne fait
pas partie de la correction du chemin de déclenchement demandée.
SONY NOT EXECUTED. Aucune fusion, installation, production, Neon, Vercel ou Cloud.


## Résultats finaux et preuves reproductibles

HEAD logiciel qualifié : `9ce38a776bff6cef6ca4a329263095e220b757ef`.
PR Draft : https://github.com/pierre22400/scenevibe-tv-companion/pull/19
Base de la PR : `work/scenevibe-e5-native-l0-l1-035` au HEAD 47f675db.
La clôture documentaire ultérieure ne change aucun fichier logiciel de ce HEAD.

CI exacte : https://github.com/pierre22400/scenevibe-tv-companion/actions/runs/38632146364

| Contrôle | Résultat |
|---|---|
| Python E5-038 et historiques | 128 PASS, zéro échec |
| JVM OFF | 1 115 cas : 1 114 PASS, 1 SKIP historique, zéro échec |
| JVM ON | 1 115 cas : 1 114 PASS, 1 SKIP historique, zéro échec |
| Builds debug/release OFF, debug/AndroidTest ON ; lint | SUCCESS ; aucune erreur/fatal lint |
| Enveloppe réelle Android et même intent Diagnostics | 15 assertions PASS |
| Émetteur Android non autorisé, UID distinct 10146 | 1 PASS ; aucun résultat du receiver |
| Commande ADB API31 | `QUEUED_GUARDS_PENDING` puis refus natif normal de cible non Sony |
| Activité foreground témoin | Settings identique avant/après ; aucune ouverture Companion |
| Commande malformée | `REFUSED_MALFORMED`, aucun service dispatch |
| APK E5 OFF | Composant absent ; broadcast sans déclenchement |
| Signature stable après software/platform31 | SUCCESS, puis vérification indépendante locale PASS |
| Script PowerShell | ParseFile PASS, exécution argv exact en mock, refus sans retry PASS |
| M6 JVM, référence indépendante inchangée | 1 207 cas : 1 206 PASS, 1 SKIP historique, zéro échec ; build/lint PASS |

Les trois jobs `software`, `platform31`, `stable-candidate` sont SUCCESS.
Les deux archives logiciel/platform31 ont été téléchargées et leurs SHA-256
comparés aux digests GitHub. Les XML OFF/ON ont été reconcomptés localement.
La native Prime n'est pas simulée en application réelle ici : les suites JVM
exercent des observations indépendantes ; le shell Android exerce la livraison
réelle puis le refus de cible non Sony. Aucune prétention de nominal physique.

Artefacts :
- Logiciel : https://github.com/pierre22400/scenevibe-tv-companion/actions/runs/38632146364/artifacts/11927035491
- Android API31 : https://github.com/pierre22400/scenevibe-tv-companion/actions/runs/38632146364/artifacts/11926846099
- Candidate signée : https://github.com/pierre22400/scenevibe-tv-companion/actions/runs/38632146364/artifacts/11926467276

### Contrôles M6 et limites exactes

La branche M6 distante reste `60d3e25c604cd1a0ad0c62f00d97b13c284ed103`,
comme au départ. Un worktree détaché de cette référence a exécuté
`:app:testDebugUnitTest :app:assembleDebug :app:lintDebug` : SUCCESS.
Les XML comptent 1 207 cas, dont BannerOwner 15 et BannerHandler 23, tous PASS
hors le skip historique unique. Les fichiers suivis M6 n'ont aucun diff.
Aucune nouvelle APK M6 n'est livrée ni installée.

Le replay Python M6 local a exécuté 179 cas : 177 PASS et deux échecs d'inventaire,
causés par la présence du fichier temporaire de contrôle négatif
`docs/m6-phase-d-unlisted-negative-control.md` généré pendant ces tests.
Après retrait de cette seule pièce de test, les deux contrôles d'inventaire
ont été rejoués tels quels et passent (2/2). Ce résultat n'est pas présenté
comme un run global 179/179 vert. Aucun test, baseline ou source M6 n'a été
corrigé, masqué ou assoupli. Les traces brutes sont conservées dans la livraison.
La réserve concerne la composition locale du harness documentaire ; elle ne
constitue pas une régression introduite dans le runtime M6, dont la branche et
les bytes sont inchangés. Elle reste visible pour une éventuelle mission M6.

### Contrôles APK avant toute proposition de mise à jour

Fichier : `SceneVibe_E5_038_stable.apk`.

- applicationId identique : `com.scenevibe.tvcompanionpoc`.
- versionCode : **15 > 14**.
- versionName : `0.8.2-tv-release-hardening-e5-native038-adb`.
- certificat SHA-256 identique à la référence E5 installée :
  `f908bf564ed97ba67e02b1ebc89eb0239cf980752587f55eb9ec0419791a2e9c`.
- SHA-256 APK : `37e6d84ceafeb3d4ffd453e40907fd084fb880c48b1d5e3f963888cdcb971796`.
- référence E5 SHA-256 : `9605c5173a5241073739b87ad76b88bf647ab0ba72e2065c27dd545f8f9c4aac`.
- fixture inchangée : `c66ded0b6a2ea41ef34320a289f2c1ec37e2a87d34ef580d9ac45d08815682ec`.
- Cloud compilé vide ; LAN OFF ; qualification E5 ON ; APK debug explicite.
- Vérification `apksigner` effective pour API31, identité/manifest/flags DEX/fixture
  et égalité de **tout** le payload applicatif avant/après signature : PASS.

La même identité, le même certificat et un versionCode croissant permettent une
future mise à jour en place. Le code d'installation/stockage/pairing reste
byte-exact et aucune suppression/migration n'est ajoutée. La conservation des
données **sur la Sony** demeure un constat physique futur, pas une preuve logicielle.

### Reproduction logicielle

JDK 17, Gradle 8.9, Android SDK 35/build-tools 35.0.0 et 34.0.0.
Depuis le checkout exact du HEAD logiciel :

```bash
python3 -m unittest discover -s tests
gradle :app:assembleDebug :app:assembleRelease :app:lintDebug :app:lintRelease :app:testDebugUnitTest
SCENEVIBE_E5_NATIVE_QUALIFICATION=e5-native-035 gradle :app:clean
SCENEVIBE_E5_NATIVE_QUALIFICATION=e5-native-035 gradle --init-script .github/scripts/e5-adb-038.init.gradle :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug :app:testDebugUnitTest
```

Le script `.github/scripts/e5-adb-038-platform.sh` refuse tout serial non émulateur.
Il n'est **pas** une procédure Sony : il installe/downgrade uniquement un appareil
jetable et lance un témoin Settings. La procédure propriétaire de déclenchement
est le document séparé `Procedure_PowerShell_E5_038.md`.

### Échecs intermédiaires conservés

- Run 38630480007 : runner supplémentaire écrasé par la fusion du manifeste
  AndroidTest, avant assertions. Corrigé par l'init Gradle spécifique 038.
- Run 38631307279 : 15 assertions PASS ; test négatif expire dans la file de
  broadcasts background, sans preuve de livraison. Corrigé dans le **test** par
  flags foreground/include-stopped et diagnostic séparant timeout/livraison.
  Protection DUMP, receiver et transport applicatifs non assouplis.
- Run final 38632146364 : les trois jobs SUCCESS. Les signatures ont été
  conditionnées à ce succès, sans signer les candidats de runs en échec.
- Tentatives Gradle E5 locales perturbées par SDK/JDK puis confiance TLS du
  runtime de travail ; elles ne sont pas assimilées à un PASS. La CI du HEAD
  exact fournit les builds/lint/JVM retenus. M6 local a ensuite réussi avec
  le JDK complet et le truststore de l'environnement.

STOP après livraison. Aucun merge, opération Sony, Cloud, LAN, Neon ou Vercel.
