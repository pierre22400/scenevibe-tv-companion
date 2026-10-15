# E5-041 — qualification logicielle et APK diagnostique

**READY FOR SONY DIAGNOSTIC.** Aucun verdict fonctionnel ou physique.

Base : `0206a498c412dbf70535295e90426488cadede91`.

| Contrôle | Résultat local / état de publication |
|---|---|
| Anciens JVM E5 + nouveaux diagnostics | 86 PASS, dont les 80 anciens byte-identiques |
| Différentiel réel E5-040/E5-041 | 57 scénarios, zéro différence de décision/commande/requête critique |
| PowerShell réel avec faux ADB | 6 PASS ; formats brief et threadtime, corrélation et erreurs |
| Python : anciens 131 + nouveaux 4 | 135 PASS locaux et CI, zéro nouveau skip |
| Build/lint JVM OFF/ON | SUCCESS ; 1137 cas par profil : 1136 PASS, 1 SKIP historique, 0 failure/error |
| Android API31 | SUCCESS : 20 anciennes assertions +10 nouvelles et 1 émetteur non autorisé |
| Signature stable, identité, payload, SHA-256 | Vérifiés sur les bytes téléchargés, conformes |
| Sony par WORK | Non exécuté |

Aucun skip nouveau. Les tests automatisés ne constituent pas une qualification physique.
Les anciens seuils, transitions pause/reprise, ancre et gardes restent byte-identiques dans
leur propriétaire de transport et sa policy. Les codes de diagnostic n'ont aucune autorité.

## Références exactes

HEAD logiciel et APK : `098f68aa522b100f441bf0aafa6242a1baaaf27b`.
[CI 38693178040](https://github.com/pierre22400/scenevibe-tv-companion/actions/runs/38693178040) :
software `116283577048`, platform31 `116284245577`, stable-candidate `116285133187`, tous SUCCESS.

Les 90 XML JVM de chaque profil ont été téléchargés et recomptés indépendamment. Le seul
skip reste `M1CloudInteropTest.originalColumboProjectionIsInstallable`, conditionné par la
fixture privée `SCENEVIBE_M1_SONY_FIXTURES` absente. Aucune assertion existante retirée.
Les builds complets et le lint retenus sont ceux de la CI, pas une compilation locale inventée :
OFF assembleDebug/assembleRelease/lintDebug/lintRelease/tests, ON debug/test APK/lint/tests.
Le XML lint ON contient zéro erreur et 25 avertissements, identiques par ID/message/fichier
à ceux de la CI E5-040 38688155384 : SetTextI18n 15, ApplySharedPref 5, IconLocation 2,
DataExtractionRules/IconColors/IconLauncherShape 1 chacun. Aucun avertissement nouveau.

Android 12 émulé : 30 assertions du runner, plus 1 refus de l'émetteur non autorisé UID 10146.
La même activité témoin Settings a été comparée avant/après. Broadcast malformé et endpoint
OFF refusés. Les lectures natives utilisent un MediaSession synthétique ; aucun Prime Sony
ou comportement physique de télécommande n'est déclaré qualifié.

PowerShell : script réel parsé et exécuté en PowerShell 7 avec faux ADB, zéro commande native/UI,
un broadcast au plus, ancien ID exclu, formats threadtime/brief, erreurs refusées sans retry.
Les attentes du test hôte concernent uniquement la publication de sa fixture. Le script
propriétaire conserve ses 30 secondes. Windows PowerShell 5.1 réel reste à confirmer par
l'exécution propriétaire ; fichier UTF-8 BOM et syntaxe compatible, aucun changement de
politique globale d'exécution. Aucun accès Sony local n'a été effectué.

## APK téléchargée et contrôlée

`SceneVibe_E5_041_diagnostic_stable.apk`, package `com.scenevibe.tvcompanionpoc`, versionCode **17**,
versionName `0.8.2-tv-release-hardening-e5-native041-diagnostics`.

- SHA-256 APK : `a7c5ea934b0e3ab909eece48cc9233d5dde786a05594afed908d1e2d7aec01f6`.
- Certificat SHA-256 : `f908bf564ed97ba67e02b1ebc89eb0239cf980752587f55eb9ec0419791a2e9c`.
- Vérification apksigner pour API31 ; identité/flags manifest et DEX ; payload applicatif
  identique à l'APK ON testée ; fixture inchangée `c66ded0b6a2ea41ef34320a289f2c1ec37e2a87d34ef580d9ac45d08815682ec`.
- Référence E5-039 téléchargée à nouveau : APK SHA `aacda02ac414e9d4e8a885870fcaf5f4e444eeb0db746a7c3aef80231d02db64`,
  même certificat vérifié. Aucune clé nouvelle ni clé privée extraite localement.
- Profil ON debug explicite, Cloud vide et LAN OFF ; profil OFF versionCode 12 inchangé,
  receiver/fixture absents. La conservation effective des données d'installation reste
  un constat propriétaire futur, sans uninstall/reset proposé.

[candidate-proof.json](candidate-proof.json) est la preuve d'inspection locale, identique à celle
du runner signé. [qualification-summary.json](qualification-summary.json) conserve compteurs,
provenance, comparaison de certificat et digests d'artefacts.

| ZIP téléchargé et vérifié | ID | SHA-256 ZIP |
|---|---|---|
| software | 11952744029 | `28f1deefd3161c09f855e3cc018f80ff93caed570b1e9f0f8e32c2cee71f2321` |
| platform31 | 11952354748 | `0d277bd64a8782b6a56cf0faad626d5e9b31714a0e60970a7f3360664656d629` |
| stable-candidate | 11952439950 | `820c9b4d91621d0194a67136f466547d68928404345bc6749557ede5c8744a88` |

La clôture documentaire ne modifie que ces rapports/procédure/preuves et leur inventaire
hashé. Aucun source logiciel, test ou workflow n'est changé après le HEAD qualifié.
Les SHA des ZIP sont distincts du SHA de l'APK ; aucun de ces succès n'est un PASS Sony.
