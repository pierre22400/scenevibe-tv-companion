# E5-042 — qualification exécutée et livraison

**READY FOR SONY FUNCTIONAL TEST.** Aucune qualification physique revendiquée.

Base : `bb13d7cf7a1448fc23f6b5e0aebb5c6a1a022ec2`.
HEAD logiciel / APK : `d2c07c0260ab10bd27beaa43aa73abed0d7c4332`.
[CI 38771214069](https://github.com/pierre22400/scenevibe-tv-companion/actions/runs/38771214069) :
software `116547209126`, platform31 `116548185941`, stable-candidate `116549657940`, tous SUCCESS.

| Contrôle | Résultat exécuté |
|---|---|
| Python, local et CI | 138 PASS, aucun skip |
| JVM purs nouveaux, local et CI | 15 PASS |
| JVM complets OFF | 1152 exécutés : 1151 PASS, 1 SKIP historique, 0 failure/error |
| JVM complets ON | 1152 exécutés : 1151 PASS, 1 SKIP historique, 0 failure/error |
| Replay sources réelles E5-041 / E5-042 | Signature de refus avant ; 30 scénarios PASS après |
| OFF debug/release, lintDebug/lintRelease | BUILD SUCCESSFUL |
| ON debug, AndroidTest, lintDebug | BUILD SUCCESSFUL |
| PowerShell7 réel / faux ADB | 6 PASS ; un broadcast au plus, mauvais build sans broadcast |
| Android12 émulé | 30 contrôles historiques +1 émetteur non autorisé ; 2 phases de premier plan, 5 contrôles chacune, tous PASS |
| APK stable signée, API31, payload, manifest/DEX/fixture | Vérifiés en CI puis indépendamment sur les bytes téléchargés |
| Sony par WORK | Non exécuté ; aucun accès |
| Windows PowerShell5.1 réel | Non exécuté ; vérification propriétaire future |

Les 91 XML de chaque profil ont été téléchargés et recomptés localement. Le seul SKIP est
`M1CloudInteropTest.originalColumboProjectionIsInstallable` : fixture privée
`SCENEVIBE_M1_SONY_FIXTURES` absente. Aucun nouveau skip, aucun test JVM ancien retiré.

Le rapport lint ON contient zéro erreur et 25 avertissements. Comparaison indépendante avec
l'artefact logiciel E5-041 retéléchargé : égalité par ID, sévérité, message et fichier. IDs :
SetTextI18n 15, ApplySharedPref 5, IconLocation 2, DataExtractionRules/IconColors/IconLauncherShape 1.
Les résultats OFF debug/release et ON debug proviennent du job logiciel au HEAD exact ci-dessus.

## Preuve Android12 et limites de portée

Le banc conserve les 30 contrôles historiques et le refus de l'émetteur sans DUMP, UID10146.
Il établit ensuite un véritable repère SCREEN_NON_INTERACTIVE sur l'émulateur, réveille et
ouvre Settings, puis attend 35 secondes. La reconstruction reconnaît encore cette activité
sans nouveau RESUMED récent. Après HOME, elle refuse cette propriété. Les événements sont les
vrais UsageEvents API31 ; seul le package Settings est traduit en cible dans le test pur.
L'adapter applicatif réel continue de refuser Settings. Aucun Prime ou Sony n'est simulé comme
un résultat physique. Broadcast malformé, premier plan témoin inchangé et endpoint OFF passent.
Les sorties instrumentées sont conservées dans les fichiers foreground-042-stable/left.txt.

La base UsageStats du premier boot comporte une lacune observée : PAUSED du launcher sans
RESUMED préalable. Le refus de ce préfixe incomplet est conservé et reproduit en JVM. Le cycle
veille/réveil du banc fournit un nouveau repère observé, jamais une autorisation à lui seul :
un RESUMED explicite et un état unique sont ensuite indispensables. La procédure propriétaire
utilise cette même préparation avant une seule tentative de lecture prolongée.

Le test de diagnostic historique attend maintenant un échantillon cohérent de sa fixture
synthétique pendant au plus 2 secondes, comme awaitSnapshot le faisait déjà. Toutes ses
assertions d'origine restent présentes. La méthode snapshot, l'owner, la policy et les seuils
applicatifs restent inchangés ; aucun retry de sélection/transport n'existe dans l'application.
Le journal expérimental UsageEvents appartient uniquement à l'APK de test de l'émulateur :
son runner et son tag E5_FOREGROUND042 sont absents des DEX de la candidate livrée.

## APK vérifiée

`SceneVibe_E5_042_foreground_stable.apk`, package `com.scenevibe.tvcompanionpoc`, versionCode **18 >17**,
versionName `0.8.2-tv-release-hardening-e5-native042-foreground`.

- **SHA-256 APK : `5864262a1978313b1e0d04689ffe963d5fa77629de263fdaf3a0135155854318`.**
- Certificat SHA-256 : `f908bf564ed97ba67e02b1ebc89eb0239cf980752587f55eb9ec0419791a2e9c`.
- Signature vérifiée pour API31, identique au certificat de la référence E5-041 retéléchargée.
- Payload applicatif égal à l'APK ON testée, seules les signatures sont exclues de la comparaison.
- Manifest/DEX : identité/version exactes, debug de qualification, flag ON, Cloud vide, LAN OFF,
  receiver DUMP protégé ; profil OFF version12, flag OFF, sans receiver/fixture.
- Fixture exacte, 2146888 bytes, SHA `c66ded0b6a2ea41ef34320a289f2c1ec37e2a87d34ef580d9ac45d08815682ec`.
- Le proof local est identique au qualification-proof.json du job signé. Aucune clé de signature
  n'a été extraite de GitHub, créée ou remplacée.

[Artefact signé](https://github.com/pierre22400/scenevibe-tv-companion/actions/runs/38771214069/artifacts/11992321530).
[candidate-proof.json](candidate-proof.json), [qualification-summary.json](qualification-summary.json)
et [SHA256SUMS.txt](SHA256SUMS.txt) conservent les preuves. Les dix fichiers de sécurité/ownership
protégés sont vérifiés byte-identiques à E5-041 dans protected-runtime-sha256.json.

| ZIP final téléchargé et vérifié | ID GitHub | SHA-256 ZIP |
|---|---|---|
| software042 | 11991456975 | `d2b163228163c243daa9c7f6f5197a43124f8664f31af7210859c75361b38788` |
| platform042 | 11991447402 | `a2b47fb56f639c7c118b01702c18d7437357e1013c604f9c74f149ceda0c0ef7` |
| stable-candidate042 | 11992321530 | `185c73462131c7d1a0fee7151d8288bce620827bb8f80cb62cfaef2bcd9f8ad9` |

## Échecs antérieurs résolus, sans suppression des preuves

- Run `38768539361`, HEAD `6416bfb518dda094346471efdd20aa257895fbf9` : Python/JVM OFF/builds/lint
  PASS ; banc PowerShell pointant un mauvais dossier et annonçant encore17, corrigé vers le
  script042/version18. Le script livré vérifiait déjà18. Les six scénarios sont ensuite PASS.
- Run `38768828103`, HEAD `18b360f041c7537037e161591d6204b792800f94`, tentative1 : échec de
  l'assertion historique ANDROID_BOTH_READS_COHERENT. Une relance unique au même HEAD passe les
  30 contrôles historiques et l'émetteur non autorisé, puis refuse REAL_LIFECYCLE_STATE_stable.
- Run `38770272275`, HEAD `0e1c96c743d77de771a0f3f6357954b5f4b282d3` : même assertion historique
  instable avant le nouveau probe. Le dumpsys ajouté au banc démontre le préfixe incomplet
  de démarrage ; repère réel du banc et attente bornée de fixture sont ensuite corrigés.
- Le dernier run au HEAD logiciel indiqué en tête exécute tout sans échec. Aucune garde de
  l'application n'a été assouplie pour faire passer ces bancs.

| Preuve ancienne conservée | ID GitHub | SHA-256 ZIP vérifié |
|---|---|---|
| premier Android FAIL | 11989424097 | `297df99453b29964ac625f5b39c25f93e3f673d4a7fdc18885f24518f453131c` |
| témoin stable FAIL | 11991255698 | `f97a579c50324d5b7cf289098d1101073ba757be30d4fd7254db0e05e62df3de` |
| diagnostic démarrage FAIL | 11990548737 | `b76bbaf98be51b876c220fdeb28b465530569a246be09a82e1ec327b30eb436a` |
| référence APK041 | 11952439950 | `820c9b4d91621d0194a67136f466547d68928404345bc6749557ede5c8744a88` |
| référence logiciel041/lint | 11952744029 | `28f1deefd3161c09f855e3cc018f80ff93caed570b1e9f0f8e32c2cee71f2321` |

Source AOSP examinée : `MediaSessionRecord.getStateWithUpdatedPosition`,
https://android.googlesource.com/platform/frameworks/base/+/dfd7091ae8cb/services/core/java/com/android/server/media/MediaSessionRecord.java .
Le système projette position et timestamp pour PLAYING : deux appels peuvent donc différer.
La trace initiale ne conserve pas les valeurs de la lecture qui a échoué ; cette cause précise
reste une inférence cohérente avec le caractère non déterministe observé. Si la Sony refuse ensuite la
cohérence native, cette garde restera active et devra faire l'objet d'un diagnostic distinct.
E5-042 supprime l'expiration de la preuve de premier plan, pas tous les motifs possibles de TARGET.

## Clôture et étape suivante

Les commits de clôture documentaire peuvent ajouter rapports, preuves, procédure et hashes ;
aucun source, test ou workflow ne change après le HEAD logiciel qualifié.
PR Draft vers E5-041, non fusionnée. Aucun déploiement, accès Sony, Cloud, Vercel, Neon ou Production.
Étape suivante : procédure Procedure_Sony_E5_042.md, **une seule tentative physique propriétaire**.
