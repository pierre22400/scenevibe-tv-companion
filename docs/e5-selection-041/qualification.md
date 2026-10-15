# E5-041 — qualification logicielle et APK diagnostique

Statut provisoire : CI et candidate signée à vérifier avant verdict READY FOR SONY DIAGNOSTIC.

Base : `0206a498c412dbf70535295e90426488cadede91`.

| Contrôle | Résultat local / état de publication |
|---|---|
| Anciens JVM E5 + nouveaux diagnostics | 86 PASS, dont les 80 anciens byte-identiques |
| Différentiel réel E5-040/E5-041 | 57 scénarios, zéro différence de décision/commande/requête critique |
| PowerShell réel avec faux ADB | 6 PASS ; formats brief et threadtime, corrélation et erreurs |
| Python : anciens 131 + nouveaux 4 | Rejoués avec inventaire final avant publication |
| Build/lint JVM OFF/ON | À consigner depuis le run CI du HEAD exact |
| Android API31 | À consigner : 20 anciennes assertions +10 nouvelles et 1 émetteur non autorisé |
| Signature stable, identité, payload, SHA-256 | À vérifier sur la candidate téléchargée |
| Sony par WORK | Non exécuté |

Aucun skip nouveau. Les tests automatisés ne constituent pas une qualification physique.
Les anciens seuils, transitions pause/reprise, ancre et gardes restent byte-identiques dans
leur propriétaire de transport et sa policy. Les codes de diagnostic n'ont aucune autorité.
