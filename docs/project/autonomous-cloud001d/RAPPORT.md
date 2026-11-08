# AUTONOMOUS CLOUD 001-D — candidat TV v33, qualification HTTPS bloquée

Campagne du 8 novembre 2026. **Verdict global : BLOCKED. Aucun essai Sony exécuté ; le candidat n'est pas READY FOR PHYSICAL SONY QUALIFICATION.**

PR Draft [#46](https://github.com/pierre22400/scenevibe-tv-companion/pull/46), branche `work/scenevibe-autonomous-cloud-tv-001d`, issue de la référence documentaire v32 `c074d42371d24b1646c5ef065c534f70924ecda5`. Source logicielle finale : `28e7af87119001598cf6ec7a6e230fa31b047940`. La publication documentaire ultérieure est séparée de la source de compilation.

## Adaptation du Companion

L'APK v32 signé demeure inchangé. Son ancienne origine Preview n'héberge pas la composition publique de cette campagne : il n'est pas annoncé utilisable tel quel. La v31 reste la référence physique du parcours privé.

Un nouvel opt-in `scenevibeAutonomousHosted001d`, désactivé par défaut, sélectionne la v33 et active le public. Les variantes v31/v32 et les options historiques restent disponibles. La variante de livraison conserve le package `com.scenevibe.tvcompanionpoc` et le certificat M5.

| Métadonnée | Valeur |
| --- | --- |
| VersionCode | `33` |
| VersionName | `0.8.2-tv-release-hardening-autonomous-cloud001d-v33` |
| Origine figée | `https://scenevibe-autonomous001d-qual-preview.vercel.app` |
| Certificat SHA-256 exigé | `f908bf564ed97ba67e02b1ebc89eb0239cf980752587f55eb9ec0419791a2e9c` |
| APK signé | [scenevibe-autonomous-cloud001d-v33-signed.apk](https://chatgpt.com/library?file_id=libfile_f5c86bba44e88191a8672406eef02cec&account_id=personal) |
| SHA-256 APK | `668ee90c882237e4883689720a39fdda6543c026ca8422859a2f42dfbb8569d4` |
| Taille APK | 2 729 678 octets |

La signature CI `apksigner` vérifie les schémas v2 et v3, un seul signataire RSA 4096 et le certificat M5 exigé. L'archive GitHub de livraison a été recontrôlée par SHA-256 (`46a366c43cd768928c29d9a03e4d48442a1eaa2235c7168f724f130caf3bbbb7`), puis l'empreinte des octets APK comparée au reçu CI. Le package, le versionCode, le versionName, l'absence de `debuggable`, la source et la présence de l'origine dans le DEX ont été vérifiés. Voir `apksigner-verify.txt`, `apk-badging.txt`, `source-commit.txt` et `SHA256SUMS.txt`.

La version de journal reçue est contrôlée comme entier sûr, puis renvoyée dans les reçus et changements de préférences. Elle ne modifie pas l'identité immuable du cache. Les erreurs de type JSON refusées restent stables ; les assertions des deux nouveaux tests JVM ne sont pas affaiblies.

Le grant est revalidé dans le poll HTTPS existant ; sa nouvelle deadline monotone alimente la même référence runtime. Ce mécanisme n'ajoute ni code, ni compte, ni confirmation TV.

L'arrêt local d'une session publique est mémorisé dans un espace public séparé, avec la version observée. Les polls et la recréation du propriétaire ne redémarrent pas cette session. Une activation Web ultérieure, de version plus récente dans le protocole Hosted, permet de reprendre directement. Le test Android ajoute ce cas sans retirer les dix cas publics précédents. Les demandes de changement de Track continuent à préserver une session privée active.

## Essais et preuves

CI finale : [42527931204](https://github.com/pierre22400/scenevibe-tv-companion/actions/runs/42527931204). Les quatre jobs exécutés sont PASS ; le cinquième, `native_https`, est SKIPPED et reste NOT EXECUTED dans la réception. Résultats exacts consignés dans `preuves-ci.json` et `SHA256SUMS.txt`.

| Niveau | Verdict actuel | Limite |
| --- | --- | --- |
| JVM | PASS avec un cas optionnel NOT EXECUTED | 1 414 PASS / 1 415 ; `M1CloudInteropTest.originalColumboProjectionIsInstallable` ignoré faute de fixtures privées `SCENEVIBE_M1_SONY_FIXTURES` ; aucun test retiré |
| Robolectric | PASS | 25/25, XML vérifiés ; ne vaut pas exécution sur Sony |
| Compilation/lint et signature M5 | PASS | Variante v33 et variantes historiques compilées ; preuves de signature ci-dessus |
| Android public sur émulateur, vraie API Android et réseau fixture | PASS | Onze cas dont arrêt/poll/recréation, trois cas privés et trois phases de redémarrage ; propre MediaSession, pas Prime |
| Historique privé sur émulateur, public désactivé | PASS | 52 cas natifs, 3 cas REAL TRACK privés, 2 phases de redémarrage et 1 smoke de service inactif ; options public/Hosted désactivées ; aucune installation Sony |
| Capture Windows PowerShell 5/7 | PASS | Onze tests sur chaque moteur ; ADB synthétique possédé par le test, aucun appareil réel |
| Nouveau `PublicHostedAndroidTest` | NOT EXECUTED | Compilé ; job `native_https` optionnel laissé désactivé |
| Dialogue Companion contre l'origine fournisseur figée | NOT EXECUTED | Catalogue vide : pas de média synthétique fournisseur à télécharger |
| Sony BRAVIA / Prime | NOT EXECUTED | STOP installation physique maintenu |

Le test HTTPS préparé utilise le vrai transport `PublicCloudClient.Https`, l'Activity d'association, le service, AndroidKeyStore, le cache réel et MediaCodec. Il prévoit l'association par code, son refus en cas de réemploi, le téléchargement complet et trois TEXT/trois AUDIO dans une MediaSession de test. Il n'a pas été lancé : aucun de ces résultats fournisseur n'est annoncé PASS.

Les essais locaux Android prouvent les fenêtres attachées et les ressources audio démarrées sur émulateur. Ils ne constituent ni une preuve d'écoute physique, ni une qualification Prime, ni une preuve du trajet HTTPS fournisseur.

Le premier run intermédiaire a révélé un refus de type JSON dont l'exception différait du contrat de validation attendu ; il a été corrigé en conservant les assertions. Le correctif d'arrêt volontaire a ensuite été ajouté et qualifié dans la source finale. Les résultats intermédiaires ne sont pas présentés comme les preuves de cette source finale.

## Blocage Cloud et préservation

La PR [Cloud #57](https://github.com/pierre22400/interface-scenevibe/pull/57) fournit la composition isolée. Le nouveau Preview répond à GET catalogue avec une liste vide. Le contrôle automatique d'autorisation a refusé l'exécution de l'ensemencement synthétique en conservant le STOP précédent malgré le GO du fichier joint. Aucune mutation de contournement n'est tentée via le job Android HTTPS. La compatibilité opérationnelle Cloud/Companion reste donc non démontrée.

Les APK v31/v32, leurs références et les données privées existantes ne sont pas modifiés. Aucune installation physique, suppression de cache/données, fusion ou changement de Production existante. Voir [PROTOCOLE-SONY.md](PROTOCOLE-SONY.md) pour la réception future et la récupération : aucun downgrade forcé, éventuel build v31 de récupération signé M5 avec versionCode supérieur à 33, à préparer et qualifier séparément.
