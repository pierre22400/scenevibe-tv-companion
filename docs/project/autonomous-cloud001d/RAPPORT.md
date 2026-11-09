# AUTONOMOUS CLOUD 001-D — réception Android HTTPS, 9 novembre 2026

**READY FOR PHYSICAL SONY QUALIFICATION côté logiciel. Sony et Prime : NOT EXECUTED. STOP installation Sony maintenu.**

L'APK expérimental v33 signé M5 est compatible avec le contrat public de l'origine de campagne. Le banc Android réel confirme le code visible, son acceptation unique, le cache complet, l'application TEXT/AUDIO et trois fenêtres TEXT / trois ressources AUDIO démarrées. La MediaSession est possédée par le test ; ce résultat ne qualifie pas Prime.

## Sources et artefact

| Élément | Référence exacte |
| --- | --- |
| Source applicative v33 signée | `28e7af87119001598cf6ec7a6e230fa31b047940` |
| Réception HTTPS finale | `3a24849603f0e88f60946b870cb7b30b327da26c` ; branche `work/scenevibe-autonomous-https-001d` |
| CI Android HTTPS | [42574624772](https://github.com/pierre22400/scenevibe-tv-companion/actions/runs/42574624772), un scénario instrumenté complet PASS |
| Différence avec l'APK | Tests d'attente UI, workflow et documents ; **aucun changement de `app/src/main` ni de `app/build.gradle`**, comparaison Git effectuée |
| Origine | `https://scenevibe-autonomous001d-qual-preview.vercel.app` |
| Package / version | `com.scenevibe.tvcompanionpoc`, versionCode `33`, `0.8.2-tv-release-hardening-autonomous-cloud001d-v33` |
| Certificat M5 SHA-256 | `f908bf564ed97ba67e02b1ebc89eb0239cf980752587f55eb9ec0419791a2e9c` |
| SHA-256 APK | `668ee90c882237e4883689720a39fdda6543c026ca8422859a2f42dfbb8569d4` |
| APK | [scenevibe-autonomous-cloud001d-v33-signed.apk](https://chatgpt.com/library?file_id=libfile_f5c86bba44e88191a8672406eef02cec&account_id=personal) ; 2 729 678 octets |

L'APK livré n'est ni remplacé ni modifié. Les preuves `apksigner-verify.txt` et `apk-badging.txt` conservées vérifient v2/v3, un seul signataire M5, le package, la version et l'absence de debuggable. Les octets APK et l'origine compilée ont été vérifiés lors de la livraison. Le test utilise une variante native de qualification avec sa propre MediaSession ; il n'est pas présenté comme une installation du fichier signé sur une Sony.

## Résultats

| Niveau | Résultat et portée |
| --- | --- |
| JVM de la source applicative | 1 414 PASS / 1 415 ; un ancien cas optionnel privé `M1CloudInteropTest.originalColumboProjectionIsInstallable` NOT EXECUTED faute de `SCENEVIBE_M1_SONY_FIXTURES`, aucun test retiré |
| Robolectric | 25/25 PASS |
| Android local public | 11 cas PASS, 3 privés et 3 phases de redémarrage ; arrêt volontaire conservé ; réseau fixture distinct du fournisseur |
| Historique privé, options public/Hosted désactivées | 52 cas natifs, 3 cas REAL TRACK, 2 phases de redémarrage, 1 smoke de service inactif PASS |
| Windows | 11 cas PowerShell 5 et 11 PowerShell 7 PASS avec ADB synthétique |
| CI de cette référence applicative | [42527931204](https://github.com/pierre22400/scenevibe-tv-companion/actions/runs/42527931204), preuves déjà conservées dans `preuves-ci.json` |
| Android HTTPS réel | **PASS** : code visible, un code consommé, réemploi refusé, KeyStore/cache natifs, paquet SHA vérifié, 3 MP3 décodés, TEXT/AUDIO armés, 3 TEXT attachés / 3 AUDIO STARTED |
| Web HTTPS PC et viewport mobile | 20 observations PASS ; récepteur JS distinct de l'Android ; preuves dans le rapport Cloud |
| Sony / Prime / téléphone physique | **NOT EXECUTED** |

Le reçu `https-native-proof.json` et `https-native-instrumentation.log` ont été extraits après comparaison du digest ZIP GitHub `688a138d560bd036b725c67eb974c5f404b8c1f56335ad420073244eebc06015`. Le log contient `OK (1 test)` ; le JSON confirme `codeAccepted=1`, `extraApplicationApprovals=0`, `textWindowsAttached=3`, `audioResourcesStarted=3` et TLS Android par défaut, sans remplacement de confiance.

Les premiers essais ont échoué sur le chargement natif Cloud, puis sur la synchronisation d'une assertion de libellé TV. Le banc attend désormais le texte réellement affiché via son helper existant sur le Looper ; les assertions n'ont pas été retirées. Le résultat final est celui du run ci-dessus. Ces changements du banc n'ajoutent aucun écran ou geste spectateur.

Le correctif du banc et son workflow sont proposés dans la [PR Draft #47](https://github.com/pierre22400/scenevibe-tv-companion/pull/47), empilée sur cette PR #46 et non fusionnée. Les fichiers `preuves-ci.json` et `public-native-tests.log` conservent les références locales du 8 novembre ; `preuves-https.json` et `https-native-*` sont les nouvelles preuves fournisseur du 9 novembre.

## Cloud réellement utilisé

Le [rapport Cloud #57](https://github.com/pierre22400/interface-scenevibe/blob/work/scenevibe-autonomous-cloud-001d/docs/project/autonomous-cloud001d/RAPPORT.md) identifie le nouveau projet Neon, le bucket privé et le projet Vercel isolés. Le code servi est `f471e0147bf31ffae183f6a4c4d5809b40b684aa`, après corrections de résolution Node des outils gelés et chargement différé. CI Cloud finale `04ba25c…` : 820 tests PASS, zéro skip, TypeScript et deux builds PASS.

La Track Android stable est `track-724ebdcee0158a6220e6a85b351698528fec1086`, révision `pc3-revision-3a2c9dc0ad5bd152ff7ea42ff5709ea540e7e2dd`, SHA paquet `dc7cef89a7f5489afc6870e6c706f0b23fa88858346269b54581c59043bca8ea`. La Track Web a également reçu une nouvelle révision publique 4 TEXT/3 AUDIO après sauvegarde ordinaire, sans republication manuelle.

Les incidents de réception HTTPS et les expirations/révocations SQL de fixtures sont décrits avec leur portée exacte dans le rapport Cloud. Ils ne deviennent pas des essais physiques Android/Sony. Les objets persistés du bucket n'ont pas été corrompus pour ces tests.

## Limites et suite physique

- La référence v31 demeure celle du parcours privé physiquement qualifié. Sa compatibilité avec la distribution publique n'est pas annoncée. La v32 conserve son ancienne origine et n'est pas modifiée.
- Aucun effet réellement affiché sur Sony, entendu sur une TV physique ou synchronisé pendant Prime n'est annoncé PASS. Métadonnées, édition, focus, permissions, publicités et firmware doivent être observés physiquement.
- Les durées longues sont exercées par horloge injectée ou dates SQL de fixtures, pas par plusieurs mois d'attente. L'association peut expirer après trente jours d'inactivité ou quatre-vingt-dix jours absolus ; un grant court valide est renouvelé automatiquement avec preuve de possession.
- Aucune fusion, installation Sony, suppression de données/cache TV ou modification des ressources privées existantes.

Voir [PROTOCOLE-SONY.md](PROTOCOLE-SONY.md) : préserver et rejouer le parcours privé avant mise à jour ; ne pas désinstaller ou forcer un downgrade 33→31. Une éventuelle récupération fondée sur v31, même M5 et versionCode supérieur au maximum installé (au moins 34 après 33), reste à préparer et qualifier séparément. **Aucun APK de récupération déjà qualifié n'est livré.**
