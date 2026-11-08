# PARCOURS AUTONOME 001-B — APK expérimental et qualification

8 novembre 2026. Ce rapport distingue le logiciel qualifié, les preuves d'émulateur et la campagne Sony ultérieure.

## Livraison

**READY FOR SONY PHYSICAL QUALIFICATION — candidat logiciel signé et qualifié sur émulateur, avec réserves Cloud préalables explicites.** Aucun PASS Sony/Prime public ni trajet fournisseur réel n’est acquis.

| Identifiant | Valeur |
| --- | --- |
| PR Android Draft | [#45](https://github.com/pierre22400/scenevibe-tv-companion/pull/45) |
| Branche | `work/scenevibe-parcours-autonome-tv-001b` |
| Commit logiciel exact | `f35943a84e619f7d0a486444edc5355e3d834b55` |
| CI | [42517899944](https://github.com/pierre22400/scenevibe-tv-companion/actions/runs/42517899944) |
| Artefact signé | [ZIP Actions exact](https://github.com/pierre22400/scenevibe-tv-companion/actions/runs/42517899944/artifacts/13632715572), SHA-256 `4c9b28c1b1c72ece7d247a927819bbe7710070290fbad7f749c611df438c56ac` ; rétention limitée à 14 jours |
| APK | `scenevibe-public-autonomous001b-v32-signed.apk` |
| Package | `com.scenevibe.tvcompanionpoc` |
| VersionCode | `32` |
| VersionName constaté | `0.8.2-tv-release-hardening-public-autonomous001b-v32` |
| APK SHA-256 | `4641026c866e891c95cd20f78d9266a3e67efd4975e2617c3f6206f9d2a1894b` |
| Certificat M5 SHA-256 constaté | `f908bf564ed97ba67e02b1ebc89eb0239cf980752587f55eb9ec0419791a2e9c` |
| Signature | `apksigner verify` : Verifies, v2=true, v3=true, un signataire ; empreinte M5 conforme |
| Debuggable | Non (`aapt` contrôlé ; aucune entrée `application-debuggable`) |
| Origine Cloud intégrée | `https://scenevibe-cloud-tv002-staging-enkt5v3ey-archc-ode.vercel.app` — Preview privé v31 existant, nouvelles routes publiques non déployées |

Le commit ci-dessus contient tout le logiciel de l'APK. Les commits suivants contenant seulement `docs/**` décrivent les résultats ; ils ne changent pas l'APK qualifié. Le workflow limite la signature historique à la branche expérimentale du dépôt de confiance. Aucun secret n'a été lu hors de cette chaîne, aucune nouvelle clé n'a été créée.

## Expérience implémentée

Catalogue PC ou mobile → Track publique → unique code affiché par la TV → téléchargement vérifié → préférences TEXT/AUDIO appliquées → restitution quand MEDIA reconnaît l'œuvre. Aucun compte humain, seconde saisie de code, dialogue « Autoriser cet appareil », confirmation TV par Track ou approbation créateur/administrateur.

L'action d'association est visible au-dessus de l'interface Companion conservée. Le code temporaire est affiché en grands caractères avec son échéance. Les permissions imposées par Android restent obligatoires si elles n'existent pas ; aucune demande applicative redondante n'est ajoutée.

L'APK ajoute transport HTTPS de possession, cache public distinct et propriétaire Android des commandes. Il réutilise les validateurs Studio/MP3, AndroidKeyStore, InstallationStore, OverlayService, TEXT/AUDIO, préférences, calendrier, MEDIA et gardes publicitaires existants. Le chemin privé garde ses contrôles historiques. L'[audit des raccords et contrats](AUDIT-CONTRATS.md) détaille les invariants et limites.

## Qualification exécutée

| Campagne sur le logiciel exact | Résultat constaté |
| --- | --- |
| JVM debug, historique et nouveau candidat | 1412 PASS / 1413 recensés, 0 échec, 1 SKIP historique explicitement optionnel : `M1CloudInteropTest.originalColumboProjectionIsInstallable`, fixture privée `SCENEVIBE_M1_SONY_FIXTURES` absente |
| Cas candidat public JVM | 8/8 PASS, inclus dans les 1412 ci-dessus |
| Panneau Android sous Robolectric | 25/25 PASS, aucun SKIP |
| Android TV API 31 x86, public activé | 10/10 PASS, aucun SKIP |
| Parcours privé sur le même build public | 3/3 PASS, aucun SKIP |
| Cache public : seed, nouveau processus, reboot Android | 3 phases indépendantes PASS : seed 1/1, relecture après force-stop 1/1, relecture après reboot 1/1 |
| Android TV API 31 x86, public désactivé | 52/52 PASS, smoke historique 1/1 PASS, seed/relecture du cache privé 1/1 + 1/1 PASS, puis parcours privé 3/3 PASS ; aucun SKIP |
| Scripts Windows et HTTP/Chromium Companion historiques | PASS : PowerShell 5 et 7, 11 cas Python Linux, 3 gardes G11, Chromium du panneau Companion sans requête externe |
| Compilation v32 + profils conservés v19/v18/v17, lint | PASS ; signature M5 et vérification version/package/non-debuggable exécutées |
| Cloud/Web, commit `80fcd465cae22435383756c0d62ac4f9ae3a7e65` | [CI PASS](https://github.com/pierre22400/interface-scenevibe/actions/runs/42517700845) : 809 cas, 20 observations PC/mobile, 12 invariants Java, typecheck et build |
| PC/téléphone physiques, fournisseur, Sony/Prime public | NON EXÉCUTÉ |

[Preuves Android publiques](https://github.com/pierre22400/scenevibe-tv-companion/actions/runs/42517899944/artifacts/13632108363), ZIP SHA-256 `c3a43a2a9d067698d09140575da1bb29a0f3b9ab36455de40b84d4528cce839a` : `textWindowsAttached=3`, `nativeAudioResourcesStarted=3`, `associationCodesAccepted=1`, `extraTvConfirmations=0`. Après redémarrage : `cacheEvents=6`, `nativeDecodedAudio=3`, `newCodes=0`, `extraDownloads=0`. Les compteurs TEXT suivent l’attachement réel des fenêtres et AUDIO l’état natif STARTED, sans constituer un constat physique Sony.

Les tests publics Android couvrent le code affiché dans l'Activity et sa consommation unique, erreur/rejeu/expiration, chiffrement AndroidKeyStore, trois MP3 distincts décodés nativement, engagement/relecture, interruption puis reprise, corruption et ressources étrangères, révision/génération incorrectes, application automatique des réglages, réglage local TV remonté au Cloud, identité média contradictoire et nouvelle demande différée pendant une session active. Les redémarrages relisent les fichiers et les trois MP3 avec un nouveau PID, la même possession, zéro nouveau code et zéro téléchargement.

Les tests de MEDIA utilisent une session réellement publiée par notre application et le renderer WindowManager/lecteur Android réels. Le port HTTP du banc est synthétique. Ils ne fabriquent pas une session Prime et ne prouvent pas un affichage sur Sony. L'APK signé refuse cette injection réservée au build DEBUG sans origine Cloud.

Les 20 observations navigateur proviennent de Chromium réellement exécuté en CI : PC 1440×1000 et mobile émulé 390×844. Le récepteur Web est un programme HTTP local distinct de l'émulateur. Le [rapport Cloud](https://github.com/pierre22400/interface-scenevibe/blob/work/scenevibe-parcours-autonome-cloud-001b/docs/project/autonomous001b/RAPPORT.md) contient la reproduction locale, la modification d'une Track publiée, les comptages et les preuves. Le blocage `socket() EPERM` du navigateur Work local n'est pas présenté comme un PASS.

Les [preuves historiques natives](https://github.com/pierre22400/scenevibe-tv-companion/actions/runs/42517899944/artifacts/13632069586) ont pour ZIP SHA-256 `2480534817e08aedd4b643e5f9ea8a4a7832a4cedfd1ce4b0a4e57b2ad4e80c2`. Les [compteurs natifs publics](service-proof.json), la [preuve de relecture après reboot](restart-proof.json) et la [sortie de vérification de signature](apksigner-verify.txt) sont conservés avec ce rapport.

## Défauts effectivement corrigés pendant la qualification

Le banc Android a détecté le refus du nouveau namespace par la whitelist `AndroidInstallationBackend`. L'ajout conserve le préfixe public distinct et l'exigence stricte des 32 caractères hexadécimaux ; aucun namespace privé n'est déplacé. Le contrôle historique de version G11 reste inchangé et retrouve son expression v31 d'origine, suivie seulement de la surcharge v32 explicite. Aucun test historique n'a été affaibli.

Le contrôle d'activité est rejoué avant l'engagement du cache pour tenir compte d'une session démarrée pendant le téléchargement. Les réglages TV appliqués sont remontés plutôt qu'écrasés à chaque poll. Enfin le délai de téléchargement de 15 minutes n'expire plus les contrôles d'un cache confirmé pendant le film : ils suivent l'échéance originale du grant ; le Cloud teste l'activation après une avance de 16 minutes, sans prolonger le grant.

## Reproduction Android

Depuis cette branche, utiliser JDK 17, Gradle 8.9, Android SDK 35/build-tools 34.0.0 et un émulateur Android TV API 31 jetable. Le workflow `.github/workflows/public-autonomous001b.yml` exécute la chaîne complète ; ses scripts refusent les appareils physiques par numéro de série et `ro.kernel.qemu`.

Le build de qualification est compilé avec `-PscenevibeRealTrack001=true -PscenevibePublicAutonomous001b=true -PscenevibeTvUx002NativeQualification=true`, origine Cloud vide. `.github/scripts/public-autonomous001b-native.sh` installe uniquement les APK DEBUG du banc sur cet émulateur, exécute les cas et redémarre son seul environnement. Le job `native_retained` désactive le flag public et rejoue les assertions historiques intactes. Le job `software` compile le candidat non debuggable avec l'origine Preview existante, puis le signe uniquement dans la CI autorisée. Aucun APK DEBUG du banc n'est le livrable Sony.

## Réserves et arrêt

1. **Raccord Cloud réel non exécuté.** Les nouvelles routes ne sont pas déployées au Preview intégré. Le mode local refuse Production/Vercel. Une composition isolée, ses origines HTTPS et la persistance des commandes doivent être préparées et qualifiées avant la campagne réelle. L'APK doit être recompilé si l'origine change.
2. **Pas encore de trajet complet Web→Cloud fournisseur→APK signé.** Les contrats sont exercés de chaque côté ; le banc Android remplace seulement la frontière HTTP. Il reste à vérifier TLS, réseau, Android signé et métadonnées Sony réelles ensemble.
3. **Prototype borné.** Une TV par cookie navigateur, un cache public courant par génération, commandes Cloud en mémoire, anciens ensembles disque conservés. Après redémarrage du coordinateur, une demande Web peut être nécessaire pour reconstruire le suivi, sans nouveau code si le grant est valide. Les nouveaux départs/reprises requièrent une revalidation Cloud ; aucun mode hors ligne durable n'est qualifié.
4. **Compatibilité média publique à vérifier.** Le candidat public exige une Track mixte TEXT+AUDIO, un mediaId exact non vide et reste limité à Prime. Les Tracks uniquement TEXT ou uniquement AUDIO ne sont pas admises par ce nouveau raccord. Une métadonnée manquante conserve `MEDIA_IDENTITY_BLOCKED`. Les garde-fous privés v31 restent présents ; leur qualification physique antérieure ne vaut pas qualification publique v32.
5. **Installation et récupération préparées seulement.** Le [protocole Sony](PROTOCOLE-SONY.md) prévoit PC et téléphone physiques, continuité du privé, absence d'autorisation redondante, publicités/pause/seek, mise à jour et redémarrages. Aucun downgrade automatique 32→31 : récupération éventuelle par build fonctionnel v31 avec certificat historique et versionCode supérieur, à qualifier séparément.

Aucune fusion, aucun déploiement Vercel, aucune mutation Neon/R2 réelle, aucun compte réel créé, aucune installation Sony et aucune modification de l'APK v31 de référence. Tous les STOP fournisseur et physiques restent en vigueur.
