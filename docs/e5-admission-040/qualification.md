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
| CI E5-040 build/lint OFF/ON et sondes API31 | En cours de publication ; résultat exact à compléter |
| Signature/APK E5-040 | Non produite : pas de correction causale démontrée |
| Sony par WORK | Non exécuté, interdit sans nouvelle autorisation |
| Qualification physique | E5-039 FAIL, aucune réussite E5-040 revendiquée |

La CI ne contient aucun job stable-candidate et n'accède pas aux secrets de signature.
Elle peut construire l'APK de test debug E5-039 inchangée pour exécuter les sondes sur
émulateur jetable. Cette APK de test n'est pas une nouvelle candidate propriétaire.
