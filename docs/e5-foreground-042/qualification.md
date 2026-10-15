# E5-042 — qualification exécutée

**VERDICT EN ATTENTE — aucune installation Sony autorisée par ce document provisoire.**

Base : `bb13d7cf7a1448fc23f6b5e0aebb5c6a1a022ec2`.
HEAD logiciel : `18b360f041c7537037e161591d6204b792800f94`.
[CI 38768828103](https://github.com/pierre22400/scenevibe-tv-companion/actions/runs/38768828103).

| Contrôle | Résultat |
|---|---|
| Python, local et CI | 138 PASS, aucun skip |
| JVM purs nouveaux, local | 14 PASS |
| JVM complets OFF | 1151 exécutés : 1150 PASS, 1 SKIP historique, 0 failure/error |
| JVM complets ON | 1151 exécutés : 1150 PASS, 1 SKIP historique, 0 failure/error |
| Replay sources réelles E5-041 / E5-042 | Signature de refus avant ; 30 scénarios PASS après |
| OFF debug/release, lintDebug/lintRelease | BUILD SUCCESSFUL |
| ON debug, AndroidTest, lintDebug | BUILD SUCCESSFUL |
| PowerShell7 réel / faux ADB | 6 PASS ; un broadcast au plus, mauvais build sans broadcast |
| Android12 émulé | En attente après premier échec conservé |
| APK stable signée et bytes finaux | En attente, signature bloquée derrière Android12 |
| Sony par WORK | Non exécuté ; aucun accès |
| Windows PowerShell5.1 réel | Non exécuté ; vérification propriétaire future |

Les 91 XML de chaque profil ont été téléchargés et recomptés localement. Le seul SKIP est
`M1CloudInteropTest.originalColumboProjectionIsInstallable` : fixture privée
`SCENEVIBE_M1_SONY_FIXTURES` absente. Aucun nouveau skip, aucune assertion JVM ancienne retirée.

Le rapport lint ON contient zéro erreur et 25 avertissements. Comparaison indépendante avec
l'artefact logiciel E5-041 retéléchargé : égalité par ID, sévérité, message et fichier. IDs :
SetTextI18n15, ApplySharedPref5, IconLocation2, DataExtractionRules/IconColors/IconLauncherShape1.

## Échecs conservés

- CI `38768539361`, HEAD `6416bfb518dda094346471efdd20aa257895fbf9` : Python/JVM OFF/builds/lint
  PASS ; l'exécution hôte PowerShell échoue car le nouveau banc pointait un mauvais dossier et
  annonçait encore version17. Banc corrigé vers le script042/version18 ; le script livré vérifiait
  déjà18. L'exécution suivante au HEAD ci-dessus a validé les six cas. Pas de modification runtime.
- CI `38768828103`, tentative1, job Android `116540221674` : échec de l'assertion préexistante
  `ANDROID_BOTH_READS_COHERENT`, avant les nouveaux tests de premier plan. Ce test et la méthode
  snapshot restent byte-identiques à E5-041. Traces et ZIP conservés, digest vérifié.
  Le système Android peut projeter position et timestamp à chaque lecture PLAYING ; une course
  entre deux lectures est une explication plausible, non une valeur observée dans cette trace
  limitée. Une seule relance du job en échec, à HEAD identique, a été exécutée : les 30 assertions
  historiques et l'émetteur non autorisé passent, puis le nouveau test échoue sur
  `REAL_LIFECYCLE_STATE_stable`. Le repère ou la cohérence de l'historique doit encore être
  expliqué à partir des événements réels de l'émulateur. Instrumentation du banc uniquement
  ajoutée pour cette investigation causale ; aucune admission runtime assouplie. Aucun seuil,
  assertion ou retry runtime n'a été ajouté pour transformer le résultat.

Source AOSP examinée : `MediaSessionRecord.getStateWithUpdatedPosition` et l'appel de
`getPlaybackState`, https://android.googlesource.com/platform/frameworks/base/+/dfd7091ae8cb/services/core/java/com/android/server/media/MediaSessionRecord.java .
Ce point devra être suivi si une future tentative Sony refuse les lectures natives ; E5-042
ne modifie pas cette garde de cohérence et ne promet pas de supprimer tout autre refus TARGET.

## Artefacts vérifiés à ce stade

| ZIP | ID GitHub | SHA-256 |
|---|---|---|
| software042 | 11989783820 | `ddea93ccd911a98e3edfa1645311735b272449dd1e577a2ca8ccec9d09cc3cbd` |
| platform042 tentative1 FAIL | 11989424097 | `297df99453b29964ac625f5b39c25f93e3f673d4a7fdc18885f24518f453131c` |
| référence APK041 | 11952439950 | `820c9b4d91621d0194a67136f466547d68928404345bc6749557ede5c8744a88` |
| référence logiciel041/lint | 11952744029 | `28f1deefd3161c09f855e3cc018f80ff93caed570b1e9f0f8e32c2cee71f2321` |

APK ON non signée stable, manifest/DEX/fixture revérifiés localement : version18, Cloud vide,
LAN OFF, flagON ; SHA `1f94ea5eaced988dd0b77dc559e4fc652d8c299cfa262fd9594436d6f24127f5`.
Référence041 : signature vérifiée localement pour API31 ; certificat attendu
`f908bf564ed97ba67e02b1ebc89eb0239cf980752587f55eb9ec0419791a2e9c`.
Aucune clé de signature n'a été extraite de GitHub ni remplacée.
