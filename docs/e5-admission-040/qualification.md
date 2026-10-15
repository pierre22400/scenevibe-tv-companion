# E5-040 — qualification de l'investigation

Verdict fonctionnel : **BLOCKED — ROOT CAUSE NOT PROVEN**.
App inchangée par rapport à b97aa ; aucune candidate E5-040 ni versionCode incrémenté.

| Vérification | Résultat |
|---|---|
| Base GitHub PR18/19/20 et filiation 24a1 → b97aa | Conforme, PR toutes Draft/open/unmerged |
| Batterie Python locale avant ajouts | 131 PASS, 0 skip/échec |
| JVM E5 locaux, JUnit réel | 80 PASS, 0 skip/échec |
| Rejeu adaptateur/foreground/propriétaire inchangés | 21 scénarios PASS ; doubles d'API Android, pas appareil |
| Diff runtime/build/manifestes/tests Android/JVM | Aucun |
| CI E5-040 build/lint OFF/ON et sondes API31 | SUCCESS, run 38688155384 au HEAD a4453d2a0502a8ae9db750cc6c742eb29c5b3618 |
| Signature/APK E5-040 | Non produite : pas de correction causale démontrée |
| Sony par WORK | Non exécuté, interdit sans nouvelle autorisation |
| Qualification physique | E5-039 FAIL, aucune réussite E5-040 revendiquée |

La CI ne contient aucun job stable-candidate et n'accède pas aux secrets de signature.
Elle peut construire l'APK de test debug E5-039 inchangée pour exécuter les sondes sur
émulateur jetable. Cette APK de test n'est pas une nouvelle candidate propriétaire.

## Résultats exacts vérifiés

[CI 38688155384](https://github.com/pierre22400/scenevibe-tv-companion/actions/runs/38688155384) :
job software `116267681646` SUCCESS et platform31 `116268104033` SUCCESS.

- OFF : assembleDebug, assembleRelease, lintDebug, lintRelease, testDebugUnitTest conformes.
- ON : assembleDebug, assembleDebugAndroidTest, lintDebug, testDebugUnitTest conformes.
- Python CI : 131 PASS ; les 21 scénarios supplémentaires passent également dans la CI.
- JVM OFF **et** ON : 1131 cas par profil, **1130 PASS, 1 SKIP, 0 failure/error**.
  Les 89 XML par profil ont été téléchargés et recomptés indépendamment.
- Le seul skip est `M1CloudInteropTest.originalColumboProjectionIsInstallable`, conditionné
  historiquement par `SCENEVIBE_M1_SONY_FIXTURES`. Cette fixture privée n'est pas fournie.
  Aucun skip ajouté, aucune assertion retirée, aucun accès Cloud introduit.
- Android API31 émulé : 20 assertions de l'enveloppe/snapshot et 1 assertion de refus de
  l'émetteur non autorisé (UID 10146), zéro échec ; même activité témoin Android Settings,
  broadcast malformé refusé, endpoint absent OFF. Ce n'est pas un interlude Prime sur Sony.
- Le replay E5-038/E5-039 existant donne encore l'échec de référence attendu puis PASS ;
  cela vérifie le maintien de 039, sans prouver une correction fonctionnelle 040.
- Le PowerShell E5-039 existant est parsé en CI ; les commandes documentaires 040 de lecture
  seule ne sont pas exécutées par WORK, et ne sont pas présentées comme testées sur Sony.

Les deux ZIP d'artefacts ont été téléchargés, ouverts et leur SHA-256 comparé au digest GitHub :

| Artefact | ID | SHA-256 ZIP |
|---|---|---|
| software | 11950924387 | `b4d9ac24cc918b68ccdae7ae0330f85ccab97ed7144314a870a7353c3957ceca` |
| platform31 | 11951105384 | `8d748faf5fac897579c95b678288afcf5559b62abf0c7681c186fb2043709bd0` |

[qualification-proof.json](qualification-proof.json) contient les compteurs, références et
l'arbre Git app inchangé `5039f76a452640add65ad8517777ae3059a02df5`.
Les résultats Android complets proviennent de la CI, pas d'une compilation locale Gradle
inventée. Les tests JVM/Python et le replay ont bien été exécutés localement.

La clôture documentaire utilise `[skip ci]` uniquement pour ce dernier delta de rapport,
preuves et leurs hashes d'inventaire. Aucun source app, script de test ou workflow ne change
après le HEAD a445 qualifié. Cela ne retire aucun test ni aucun échec du run retenu.
La version des APK de test ON reste E5-039/versionCode 16 ; aucune compatibilité de signature
stable n'est revendiquée pour un nouveau binaire et aucune candidate n'est proposée à installer.

Le succès de ces vérifications ne modifie pas le verdict **BLOCKED — ROOT CAUSE NOT PROVEN**.
