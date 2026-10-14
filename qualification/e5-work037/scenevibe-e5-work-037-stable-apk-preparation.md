# SceneVibe OS — WORK 037 — Préparation hors TV de l'APK E5 stable

Date : 14 octobre 2026. Autorisation : signature d'une copie de l'APK E5 immuable avec la clé stable déjà configurée dans GitHub Actions. Aucun accès Sony, aucune installation, aucun changement applicatif, aucune fusion ou publication de production.

**Résultat : APK E5 stable préparée et vérifiée — prête pour le préflight Sony non destructif.** La compatibilité avec l'installation Sony reste à vérifier sur place ; aucun PHYSICAL PASS n'est annoncé.

## Identités et livraison

- Candidat immuable : [`47f675dbd05218fb1caca2b090fd81c822e513e0`](https://github.com/pierre22400/scenevibe-tv-companion/commit/47f675dbd05218fb1caca2b090fd81c822e513e0).
- [PR #18](https://github.com/pierre22400/scenevibe-tv-companion/pull/18) : OPEN / DRAFT / UNMERGED ; son HEAD n'a pas été modifié.
- [Audit indépendant WORK 036](https://github.com/pierre22400/scenevibe-tv-companion/blob/7ba18d2e94eab1f31e6f1e942cb358e464230380/scenevibe-e5-work-036-independent-audit.md) : PASS pour la décision propriétaire d'une qualification Sony/API 31 ciblée.
- Branche distincte : `qualification/scenevibe-e5-stable-work-037`.
- Commit de procédure exécuté : [`a00683bc496dfcb71fb71dbd068a84a713686545`](https://github.com/pierre22400/scenevibe-tv-companion/commit/a00683bc496dfcb71fb71dbd068a84a713686545), dérivé directement du candidat.
- [Workflow de qualification 38529459557](https://github.com/pierre22400/scenevibe-tv-companion/actions/runs/38529459557) : **SUCCESS**, job [115737554559](https://github.com/pierre22400/scenevibe-tv-companion/actions/runs/38529459557/job/115737554559).
- **[Télécharger l'APK stable et ses preuves — artefact 11877412212](https://github.com/pierre22400/scenevibe-tv-companion/actions/runs/38529459557/artifacts/11877412212)**. Archive ZIP de qualification, rétention 30 jours, pas de GitHub Release.

| Paramètre contrôlé | Résultat réel |
|---|---|
| Fichier signé | `SceneVibe_E5_47f675db_stable.apk` |
| Taille du fichier signé | 2 315 216 octets |
| SHA-256 du fichier signé | `9605c5173a5241073739b87ad76b88bf647ab0ba72e2065c27dd545f8f9c4aac` |
| Certificat stable, SHA-256 | `f908bf564ed97ba67e02b1ebc89eb0239cf980752587f55eb9ec0419791a2e9c` |
| Package | `com.scenevibe.tvcompanionpoc` |
| VersionCode | `14` |
| VersionName | `0.8.2-tv-release-hardening-e5-native035` |
| Profil | debug opt-in E5 activé ; manifest debuggable |
| `E5_NATIVE_QUALIFICATION` compilé | `true` |
| `CLOUD_ORIGIN` compilé | chaîne vide |
| `ENABLE_LAN_DEV` compilé | `false` |
| Fixture | 2 146 888 octets ; SHA-256 `c66ded0b6a2ea41ef34320a289f2c1ec37e2a87d34ef580d9ac45d08815682ec` |
| Contenu applicatif avant/après | 11 entrées ZIP, mêmes chemins et bytes exacts, y compris tous les DEX, le manifeste, les ressources et la fixture |

L'APK E5 reste un build de qualification. Le remplacement du certificat debug du runner par le certificat stable ne constitue pas une conversion en release de production et n'active pas Cloud/LAN.

## Source exacte et signature

Source unique : [artefact 11873723967 du run 38521313109](https://github.com/pierre22400/scenevibe-tv-companion/actions/runs/38521313109/artifacts/11873723967), HEAD `47f675db…`.

- SHA-256 de l'archive source vérifiée : `20246a6601e0145fbbaa53834fb904db35397a74854a5056bc7228617f2f5b0b`.
- Entrée exacte : `scenevibe-tv-companion/scenevibe-tv-companion/app/build/outputs/apk/debug/app-debug.apk`.
- SHA-256 de l'APK source : `607d04f63d8fd3b9b653c5bc815f0b6569291c0bdb8e044b34b480df916f39e5` ; 2 305 017 octets.
- Certificat initial de cette source : `a118b2a9692468f04897d963b4e3d5c96fd6ef0aedc0e25f464da5b131509de8`, Android Debug du runner.

La procédure valide l'identité de l'artefact/run/HEAD, ses empreintes, le package/version et le manifeste via `aapt dump badging`, puis les constantes de BuildConfig présentes dans les DEX et la fixture. Elle vérifie la signature source, rend la copie source non inscriptible et signe un autre fichier. L'APK originale n'est ni reconstruite ni modifiée.

Le mécanisme est celui déjà présent dans [android-debug.yml au candidat](https://github.com/pierre22400/scenevibe-tv-companion/blob/47f675dbd05218fb1caca2b090fd81c822e513e0/.github/workflows/android-debug.yml) : secrets existants `SCENEVIBE_ANDROID_KEYSTORE_BASE64`, `SCENEVIBE_ANDROID_KEYSTORE_PASSWORD`, `SCENEVIBE_ANDROID_KEY_ALIAS`, `SCENEVIBE_ANDROID_KEY_PASSWORD` ; décodage dans RUNNER_TEMP, copie distincte puis `apksigner sign` avec mots de passe fournis via environnement.

Aucune nouvelle clé, aucun export de clé ou mot de passe, aucune clé transférée au poste local ou ajoutée au dépôt/artefact. Le keystore éphémère a été supprimé dans l'étape always, constatée SUCCESS. Le workflow ne réalise aucun build applicatif, aucune installation, aucun déploiement et ne demande pas de droit d'écriture GitHub. Les modifications de branche sont limitées à une procédure de qualification et ce rapport ; aucune source `app/`, aucun test historique et aucun workflow existant n'a changé.

## Contrôles et preuves réellement obtenus

| Contrôle | Exécution et résultat |
|---|---|
| Vérification source avant signature | CI, sortie 0 ; SHA/package/version/profil/fixture PASS |
| Signature stable | CI, sortie 0 ; une copie distincte |
| `apksigner verify --verbose --print-certs` | CI, sortie 0 ; un seul signer attendu ; v2 et v3 validés |
| Vérification ciblée API 31 | CI, sortie 0 : `--min-sdk-version 31 --max-sdk-version 31` ; v3 validé pour cette cible |
| Certificat attendu | Comparaison exacte de l'empreinte du seul signer : PASS ; un certificat différent aurait arrêté le workflow avant publication |
| Source après signature | Nouveau hash exact de la source inchangée : PASS |
| Payload après signature | Tous les chemins et SHA-256 des bytes décompressés identiques ; aucune nouvelle entrée applicative : PASS |
| Nouvelle APK et fixture | Nouvelle inspection après signature, sortie 0 ; mêmes paramètres et fixture |
| Publication | Artefact de qualification seulement, étape SUCCESS |
| Nettoyage des secrets | Étape always SUCCESS |
| Contrôle indépendant après téléchargement | Archive, APK, certificat public, DEX/paramètres, fixture et égalité des 11 payloads vérifiés localement : PASS |

Le contrôle de payload exclut exclusivement les entrées de signature JAR éventuelles de META-INF ; les blocs de signature APK externes à l'inventaire ZIP ne sont pas du contenu applicatif. Le nouveau hash du conteneur est attendu. L'égalité ne porte pas seulement sur classes.dex : tous les fichiers classesN.dex sont inclus.

Digest de l'archive signée téléchargée et vérifiée : `19007f8b9061a646ca64afc9bc82c2a11295b0ea7ad8616c7d2820577871fcc4`.

Contenu de l'artefact : APK signée, `SHA256SUMS.txt`, `source-evidence.json`, `signed-evidence.json`, `qualification-proof.json`, `source-signature.txt`, `stable-signature.txt`, `stable-signature-api31.txt`. Les JSON contiennent aussi l'inventaire complet des 11 payloads et leurs empreintes.

La vérification cryptographique a été exécutée par apksigner en CI. Le contrôle local après téléchargement recalcule les empreintes, extrait le certificat public et compare les bytes applicatifs ; il n'est pas présenté comme un deuxième apksigner local. Les avertissements d'actions Node 20/24, punycode et url.parse sont présents dans les logs du run et ne sont pas masqués. Aucun avertissement de signature bloquant n'a été ignoré. Aucune nouvelle suite logicielle générale n'était nécessaire pour une copie dont le payload est inchangé ; cette livraison ne réouvre pas l'audit logiciel 036.

## Préflight non destructif pour ce soir

**Aucune commande d'installation n'est incluse à ce stade.** Télécharger/extracter l'archive dans un dossier PC distinct. Conserver également l'APK source antérieure sans la renommer en candidate stable.

### 1. Vérifier le fichier téléchargé sur le PC

Depuis le dossier contenant la nouvelle APK :

```powershell
Get-FileHash -Algorithm SHA256 .\SceneVibe_E5_47f675db_stable.apk
```

Attendu : `9605c5173a5241073739b87ad76b88bf647ab0ba72e2065c27dd545f8f9c4aac`. Divergence : STOP.

### 2. Identifier explicitement la Sony et relever sa version

```powershell
adb devices -l
```

Choisir uniquement le numéro de série Sony affiché, puis utiliser `-s` pour toutes les lectures. Ne pas choisir automatiquement le premier appareil.

```powershell
$tvSerial = "REMPLACER_PAR_LE_SERIAL_SONY"
$packageSceneVibe = "com.scenevibe.tvcompanionpoc"

adb -s $tvSerial shell getprop ro.product.brand
adb -s $tvSerial shell getprop ro.product.model
adb -s $tvSerial shell getprop ro.build.version.release
adb -s $tvSerial shell getprop ro.build.version.sdk
adb -s $tvSerial shell dumpsys package $packageSceneVibe |
  Select-String -Pattern "versionCode=|versionName="
```

Attendu : Sony, Android 12, API 31. Relever le versionCode réellement installé. Si supérieur à 14, ou cible divergente : STOP ; ne pas forcer un downgrade.

### 3. Lire le chemin et copier l'APK installée sans toucher aux données

```powershell
adb -s $tvSerial shell pm path $packageSceneVibe
```

Identifier l'unique ligne correspondant à `base.apk`. Copier son chemin après `package:` dans la commande suivante :

```powershell
adb -s $tvSerial pull "CHEMIN_BASE_APK_AFFICHE" .\SceneVibe_installee_avant_E5.apk
```

Cette commande copie l'APK installée vers le PC ; elle ne désinstalle rien et ne lit pas le contenu privé de l'appairage. Absence d'APK ou chemin ambigu : STOP et transmettre le résultat.

Comparer le certificat de cette APK avec `f908bf564ed97ba67e02b1ebc89eb0239cf980752587f55eb9ec0419791a2e9c`. Si apksigner est déjà disponible sur le PC :

```powershell
apksigner.bat verify --verbose --print-certs .\SceneVibe_installee_avant_E5.apk
apksigner.bat verify --verbose --print-certs .\SceneVibe_E5_47f675db_stable.apk
```

Si cet outil n'est pas disponible, transmettre l'APK installée copiée et les résultats des lectures ci-dessus pour comparaison avant installation ; ne pas contourner la vérification. Un hash du fichier APK installé n'est pas le hash de son certificat.

### 4. Relever l'état existant avant toute mise à jour

Depuis Diagnostics, noter l'installation/appairage, les identifiants affichés, le cache et les révisions/compteurs existants, l'état du service et de l'autostart, les droits overlay et notification listener. Garder ces observations localement ; ne transmettre aucun code d'appairage, token ou secret. Aucun Send/ACK réel, reset, clear data, désinstallation ou réappairage.

**La signature stable obtenue ne prouve pas encore que cette même signature est installée sur la Sony.** L'installation demeure subordonnée à cette concordance et à la version compatible. Les paramètres de qualification ne sont pas modifiés pour faciliter un essai. Après préflight compatible, poursuivre sous le mandat physique déjà défini ; les trois nominales, confirmations, positions et refus seront consignés séparément. Les réserves NB01/API35 et NB02 restent celles de WORK 036 ; aucune correction applicative n'a été réalisée ici.

## Arrêt

Préparation hors TV terminée. Aucun accès Sony, installation, reset pairing, opération Cloud, merge, déploiement, qualification Prime réelle ou chantier L2–L8. Le candidat PR #18 reste immuable. Les observations physiques et le rapport `scenevibe-e5-work-037-sony-qualification.md` restent à réaliser lors de la session sur TV.
