# COMPANION INTEGRATION 001 — réception du candidat v32

10 novembre 2026. **READY FOR SONY — qualification logicielle et émulateur terminée. Aucun PHYSICAL PASS.**

Le panneau PRODUCT UX 002 est intégré au Companion principal et agit sur sa véritable projection de Track, commune aux TEXT et MP3. Il réutilise les fenêtres, le focus, le calendrier, le lecteur AUDIO et les caches existants. Aucun service d’accessibilité ni horloge Netflix supplémentaire n’est importé.

## Livraison exacte

| Élément | Valeur constatée |
| --- | --- |
| PR Draft, empilée sur #47 | [#48](https://github.com/pierre22400/scenevibe-tv-companion/pull/48) |
| Source compilée exacte | `a23f91a3250dc4156491428e60a5304dc61dbe2c` |
| Réception du candidat | [Actions 42783765046 — six jobs SUCCESS](https://github.com/pierre22400/scenevibe-tv-companion/actions/runs/42783765046) |
| APK principal | [scenevibe-companion-integration001-v32-signed.apk](https://chatgpt.com/library?file_id=libfile_659c1ba239188191a555215312c28636&account_id=personal) |
| Package | `com.scenevibe.tvcompanionpoc` |
| VersionCode / VersionName | **32** / `0.8.2-tv-release-hardening-companion-integration001-v32` |
| Variante | `tvux002`, profils privé REAL TRACK + public autonome + hosted 001-D + integration 001 activés |
| Origine HTTPS | `https://scenevibe-autonomous001d-qual-preview.vercel.app` |
| APK SHA-256 | `8fac9b4d8b852ad01d80ad0b416476e251097ac403736bcf205caf3420ccf047` |
| Taille | 2 733 774 octets |
| Certificat M5 SHA-256 | `f908bf564ed97ba67e02b1ebc89eb0239cf980752587f55eb9ec0419791a2e9c` |
| Signature | v2 **true**, v3 **true**, un signataire |
| Débogage | **Désactivé**, vérifié dans l’APK compilé |
| SDK | min 26, compile/target 35 |

L’APK est différent de l’ancien v32 autonome, dont le SHA-256 était `4641026c866e891c95cd20f78d9266a3e67efd4975e2617c3f6206f9d2a1894b`. Le certificat correspond à la v31 de référence, vérifiée par mise à jour réelle, et à la preuve de signature v33 conservée dans [autonomous-cloud001d/apksigner-verify.txt](../autonomous-cloud001d/apksigner-verify.txt). Le candidat conserve les corrections v33 de #47. Il est destiné à une Sony actuellement en **v31** ; aucun remplacement direct d’une v33 par v32 n’est annoncé.

Le workflow Actions existant réalise la signature avec les secrets M5 historiques. Aucune nouvelle clé, modification de secret, fusion, publication ou installation Sony n’a été effectuée. Le runner d’instrumentation signé sert uniquement à l’émulateur ; l’APK principal ci-dessus est le seul APK destiné à l’essai Sony.

## Amendement 001 — aucune nouvelle autorisation

**PASS logiciel.** Les permissions des manifestes compilés v31 et v32 sont strictement identiques : [comparaison exacte](permission-comparison.json). Aucun nouveau service d’accessibilité, écran d’onboarding, consentement ou association n’est ajouté pour le recalage.

L’ouverture réutilise le `PendingIntent` de la notification actuelle. Elle fonctionne avec le mode Développeur désactivé dans le scénario natif. Un bouton **Recaler pendant le film**, dans le Companion existant, réutilise aussi le lancement public normal du lecteur puis ouvre le panneau après l’arrêt de l’Activity SceneVibe. Ce second accès à la télécommande ne dépend pas de la visibilité des notifications. Aucun de ces chemins n’envoie PLAY, PAUSE ou SEEK au film.

La mise à jour a utilisé le véritable APK v31 signé de référence : source `1d642e70d1559e6d753476945dcb749036dc4266`, SHA-256 `98ac6b74f71f02cbb52bd09998565012c864ec61aeb0a094d610de614283710f`. Après `install -r` vers le v32 signé non débogable, les tests relisent le cache privé, ses trois MP3, l’association AndroidKeyStore, les préférences et les autorisations. Les réglages de superposition et de notification sont identiques avant/après ; aucun grant, reset ou nouvel appairage n’est exécuté après update.

Preuves : [seed v31](upgrade-v31-seed.log), [relecture v32](upgrade-v32-retained.log), [overlay avant](upgrade-overlay-before.txt)/[après](upgrade-overlay-after.txt), [listener avant](upgrade-listeners-before.txt)/[après](upgrade-listeners-after.txt).

## Réception exécutée

| Campagne | Résultat |
| --- | --- |
| JVM debug, historique et intégration | **1 430 PASS**, 0 échec, **1 NOT EXECUTED historique** sur 1 431 recensés |
| Contrôleur temporel et calendrier TEXT/AUDIO intégrés | **16/16 PASS**, déjà inclus dans les 1 430 |
| Panneau historique Robolectric | **25/25 PASS** |
| Panneau principal intégré Robolectric | **13/13 PASS** : CURRENT/NEW, format, pas 60/10/1 s, OK long, validation, annulation, perte de focus, permission absente, doubles ouvertures |
| PRODUCT UX 002, source #44 exacte rejouée séparément | **44/44 panneau + 5/5 launcher PASS**, 0 échec, 0 SKIP : [réception 42785111422](https://github.com/pierre22400/scenevibe-tv-companion/actions/runs/42785111422) |
| Android TV public, candidat intégré | **11/11 PASS**, 3 TEXT et 3 MP3 natifs |
| Parcours privé dans le même build intégré | **3/3 PASS**, comprenant le panneau réel, les touches, 20 s en pause puis 80 s validés, BACK, absence de commande vidéo et retour du focus |
| Cache public | Trois phases **1/1 PASS** : seed, nouveau processus, reboot ; zéro nouveau code/téléchargement |
| Android TV historique, profil privé conservé | **52/52 PASS**, smoke **1/1**, seed/relecture **1/1 + 1/1**, parcours privé **3/3** |
| Cloud HTTPS réel sur Android | **1/1 PASS** : code unique visible, cache natif complet, 3 TEXT + 3 AUDIO, recalage public et MP3 natif après recalage |
| Mise à jour réellement signée M5 v31 → v32 | **1/1 seed + 1/1 relecture PASS**, données et permissions conservées |
| Gardes POC | **15/15 PASS** |
| PowerShell Windows 5 / 7 | **11/11 + 11/11 PASS** |
| Capture/analyse Linux et gardes G11 | **11/11 + 3/3 PASS** |
| Chromium, compilation, lint et signature | **PASS** |
| Sony / Prime physique | **NOT EXECUTED** |

Le seul cas JVM non exécuté est `M1CloudInteropTest.originalColumboProjectionIsInstallable` : fixture privée historique `SCENEVIBE_M1_SONY_FIXTURES` absente. Ce skip est préexistant et explicite ; il n’est pas présenté comme PASS. Aucun test historique n’est supprimé, désactivé ou affaibli.

Les tests du panneau source utilisent leur décodeur FFmpeg réel. Le premier rejeu sans ce binaire a échoué ; le runner a ensuite été provisionné et les **49 assertions originales** sont vertes, sans modifier le source #44. La première campagne du candidat [42782935732](https://github.com/pierre22400/scenevibe-tv-companion/actions/runs/42782935732) reste conservée ; seul le candidat exact de la campagne verte ci-dessus est livré.

## Preuves et limites avant Sony

[Résumé machine](preuves-ci.json), [signature](apksigner-verify.txt), [SHA-256](sha256.txt), [source](source-commit.txt), [HTTPS](https-proof.json), [cache public](service-proof.json), [reboot public](restart-proof.json).

Artefacts du run qualifié : [logiciel/signature](https://github.com/pierre22400/scenevibe-tv-companion/actions/runs/42783765046/artifacts/13746719496), [Android public/privé intégré](https://github.com/pierre22400/scenevibe-tv-companion/actions/runs/42783765046/artifacts/13749155169), [HTTPS](https://github.com/pierre22400/scenevibe-tv-companion/actions/runs/42783765046/artifacts/13748435659), [privé historique](https://github.com/pierre22400/scenevibe-tv-companion/actions/runs/42783765046/artifacts/13748172086), [upgrade signé](https://github.com/pierre22400/scenevibe-tv-companion/actions/runs/42783765046/artifacts/13749630252). Leur rétention expire le 24 novembre 2026 ; les preuves textuelles essentielles sont conservées ici.

Les MediaSessions du banc appartiennent explicitement au banc. Elles ne prouvent ni un rendu Sony ni une écoute Prime. L’accès à la notification avec la télécommande Sony, le lancement normal de Prime, le maintien de la lecture native et le focus après fermeture restent à confirmer physiquement. Aucun fonctionnement MUTE sur Sony n’est revendiqué. Les contrôles d’identité et le périmètre d’admission des plateformes ne sont pas étendus.

Le [protocole Sony et la documentation d’intégration](../../companion-integration001.md) sont prêts. **STOP avant installation Sony.** Toute demande de permission, accessibilité, association ou configuration supplémentaire pendant cet essai vaut **FAIL** ; elle ne doit pas être validée pour poursuivre le scénario.

La compatibilité avec le Cloud de qualification est démontrée par le test HTTPS. [PR Cloud #61](https://github.com/pierre22400/interface-scenevibe/pull/61) consultée ; aucune modification de son code, déploiement Cloud, migration ou mutation administrative Neon/R2 n’est réalisée. Les essais HTTPS utilisent le parcours synthétique autorisé, sans modifier les Tracks personnelles.

