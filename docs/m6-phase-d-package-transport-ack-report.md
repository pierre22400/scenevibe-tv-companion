# M6 Phase D — transport Package et preuve d’ACK

Date : 2026-10-07. Verdict technique : **PASS**, pour la paire de code qualifiée ci-dessous et la composition explicite de qualification M6. Production **SHADOW**. PR [TV #16](https://github.com/pierre22400/scenevibe-tv-companion/pull/16) et [Cloud #28](https://github.com/pierre22400/interface-scenevibe/pull/28) OPEN/DRAFT/unmerged. Aucune Phase E, qualification Sony ou activation production.

## Identités et clôture

| Objet | HEAD exact | Tree exact |
| --- | --- | --- |
| TV avant Phase D | `1f1aa52d01cb5c8211a2956acfaa30c43708c8d2` | `7e6cce27be7f78a78b62bf468df94860c89f1f50` |
| Cloud main de départ | `5011c91aac61a0cc6dcc74c256a15b7dee03d785` | `15606778cb94dec92cac520be32891409cfd4cc4` |
| TV code qualifié | `82009870fafb0b1bb837361557e931459d284221` | `f749e9cf4c5564d21f731e9fb285cabe673d9c16` |
| Cloud code qualifié | `6ef03c3125c23aee8e88902da675b02e98e47bca` | `db5e8b19d51dc7911272c48708db5ce3b620fb51` |

Les workflows font checkout de `github.event.pull_request.head.sha` : les logs montrent ces HEAD, plutôt que le commit de merge synthétique GitHub. La clôture ajoute ce rapport et ses pins de provenance, sans modification du code applicatif. Elle est à nouveau soumise aux mêmes CI. L’attestation externe `scenevibe-m6-phase-d-qualification.json`, produite après ces CI et reprise dans les descriptions des deux PR, donne les HEAD/trees **finaux**, leurs runs et les digests des artifacts. Cette séparation évite une référence circulaire où un rapport prétend contenir le SHA de son propre commit.

## Chemin exécuté et contrat

Le Cloud résout l’entrée civile ou absolue, construit un candidat déterministe, utilise les primitives OS existantes PREPARE → SEAL → READY immuable, puis alloue une seule DeviceAssignment. GET transporte la publication déjà scellée. La TV suit son chemin PackageInstaller existant : validation → commit durable → readback → restore → ARM sur l’owner commun → preuve du snapshot durable → ACK serveur → confirmation locale. Aucun second store, compteur, poller, schéma de snapshot ou credential.

L’enveloppe fermée `scenevibe.cloud.package-assignment.v1` contient exactement `version, deviceId, revision, kind, codecId, codecVersion, body, deliveryDigest`. Elle ne publie aucun UUID de publication. La révision est un entier JSON positif sûr, ≤ 9 007 199 254 740 991. Le digest est SHA-256 de `codecId UTF-8 + NUL + codecVersion UTF-8 + NUL + body UTF-8 exact`, encodé en 64 caractères hexadécimaux minuscules. Le body reste une chaîne opaque, sans normalisation Unicode ni re-sérialisation avant stockage/proof.

| Profil | Codec transport, version | Artifacts durables |
| --- | --- | --- |
| Banner | `scenevibe.banner-wall-overlay.v1`, `1.0.0` | Un artifact `banner`, body exact |
| Video historique simple | `scenevibe.cloud.assignment.v1`, `1.0.0` | Un artifact `cloud-video`, enveloppe v1 exacte |
| Video avec manifest | Même codec transport Video | `cloud-video` exact + `manifest` vérifié |

Le codec durable Video reste celui du handler Video original. Le wrapper statique réutilise CloudV1InstallationAdapter et le handler original ; il produit leur projection en mémoire et conserve l’enveloppe de transport originale dans la canonical durable. Le second artifact du Video manifesté préserve son exigence historique de cardinalité. Aucun changement de TvCapabilities, InstallationBounds, PackageInstaller, InstallationStore, handlers Banner de Phase C, modèles Wall ou cœur Media.

Le body logique est limité à 1 048 576 octets UTF-8 et au plafond hérité de 800 000 unités UTF-16. UTF-8 invalide et surrogates isolés sont rejetés en mode Package. Les contrôles outer version/device/révision/champs fermés précèdent la politique de révision.

## Révisions, reprise et ACK

| Observation | Comportement qualifié |
| --- | --- |
| STALE | Aucun handler, ARM, changement durable ou ACK |
| SAME | Utilise uniquement codec/artifacts du durable ; ignore kind, body, codec et digest entrants ; restore/ARM puis preuve des bytes originaux ; aucun re-persist |
| NEW | Valide le profil connu et le digest, commit/readback/restore puis ARM avant tout ACK |
| Commit ou readback invalide | Aucun ARM/ACK ni confirmation locale |
| ARM échoue | Snapshot durable conservé pending ; ACK non avancé ; retry SAME peut restaurer |
| ACK réseau ou réponse fermée invalide | Révision locale confirmée inchangée ; retry utilise le durable |
| Confirmation locale échoue après ACK serveur | Pending local préservé ; retry idempotent de la même preuve |
| Cache corrompu | Fail-closed, aucune installation/ACK |
| Remplacement ou arrêt du client | Garde de lifetime et sérialisation owner bloquent un ACK local sur une autre installation |

`afterRevision` vient de `acknowledgedRevision`, jamais de la simple révision installée. La preuve est calculée, après ARM réussi, depuis le readback du snapshot durable avec binding codec/handler/révision. Le POST ACK porte exactement `revision, codecId, codecVersion, deliveryDigest`. Le serveur revalide authentification, owner, publication courante READY, body/profile et preuve sous le verrou de la ligne device, puis effectue son CAS et son mirror éventuel sur le même client PostgreSQL.

Les snapshots Video historiques qui ne contiennent que la projection runtime/manifest ne permettent pas de reconstituer les bytes exacts de l’ancienne enveloppe v1. Leur preuve générique est donc refusée. Le client historique conserve son comportement ; un Send M6 de révision supérieure constitue le chemin de transition qualifié.

Le constructeur Android utilisé en production conserve `VIDEO_V1`. `PACKAGE_V1` n’est sélectionné que par la composition explicite de qualification ; le même client et le même executor exécutent le poller. `supportsWallClockExecution=false` demeure vrai. La Phase E devra décider de l’activation live.

## Preuves réellement exécutées

Toutes les conclusions ci-dessous concernent le HEAD TV de code qualifié, avec checkout exact attesté dans les logs.

| Gate / CI | Preuve | Résultat |
| --- | --- | --- |
| Python et compilations pure-JDK incluses | [Run debug 37624950083](https://github.com/pierre22400/scenevibe-tv-companion/actions/runs/37624950083), job `112804239211` | 167 tests, PASS |
| JVM, lint, debug/LAN/Cloud APK | Même run/job, XML et résumé exécutés | 1 200 PASS / 0 FAIL / 1 SKIP / 1 201 TOTAL |
| M6 Package | `M6PackageTransportTest` dans les XML | 19 PASS, 0 FAIL, 0 SKIP |
| M6 Wall hérité | Résumé JVM, `m6PhaseBCases=68`, `m6PhaseCCases=63` | Counts exacts conservés |
| API35 smoke | [Run 37624950359](https://github.com/pierre22400/scenevibe-tv-companion/actions/runs/37624950359), job `112804240628` | SUCCESS, image standard Android |
| M5 differential API31 / API35 | [Run 37624950190](https://github.com/pierre22400/scenevibe-tv-companion/actions/runs/37624950190), jobs `112804240058` / `112804239758` | 82 comparaisons et 4 cas HashMap par API, 0 divergence |
| Durabilité native API31 / API35 | [Run 37624950080](https://github.com/pierre22400/scenevibe-tv-companion/actions/runs/37624950080), jobs `112804241245` / `112804241504` | SUCCESS, vrais XML préférences/processus distincts |
| Cloud/PostgreSQL réel | [Run 37624818846](https://github.com/pierre22400/interface-scenevibe/actions/runs/37624818846), job `112803786543` | Typecheck/build ; 1 426 PASS / 4 SKIP hérités ; M6 28 unit + 14 PG, aucun skip M6 |

Le seul skip JVM est `M1CloudInteropTest.originalColumboProjectionIsInstallable`. Aucun allowlist, compte historique, fixture gelée ou oracle n’a été relâché. Les étapes de signature APK sont restées dans le workflow existant.

| Artifact GitHub réel | ID | SHA-256 ZIP |
| --- | --- | --- |
| XML JVM + golden | `11484070914` | `880502f4c2a425c9fd9d8f3245b64762a762bdbdec5b6942a8c8d53406c7c267` |
| Résumé des counts/fixtures | `11483159299` | `81784b952e6fb249ecdd2f5d25508eb99c735b84cf22d45ef55d46531cfdd2e2` |
| M5 API31 | `11483204344` | `d5051451fea038750f4dd64336af1913ad389efba8460acf85fb26e3fb919330` |
| M5 API35 | `11483327975` | `0069e6157363c165a7cf292bfc4820244f537db575c36717a022e95532e368cb` |
| Durabilité API31 | `11483921828` | `9d4897efc0c1e9a42ddb2b22dec9b6fdfa29ced1a96e2603ccd5d71348cec296` |
| Durabilité API35 | `11483368115` | `57ac13b39310b2940d0a9537b8e65a3b3ac0d144574901b52e794aa647bfd777` |
| APK Cloud debug | `11483961199` | `894217ad342f95a09b50b12caa543f79447d5afec875090c9f72acb388637b7c` |
| APK Cloud stable | `11483293658` | `071e18db0f21f2bb57f567123977e71f67947898238afd12a0d409a0098a789b` |
| Cloud XML + golden | `11483961090` | `e315ec9436ad50077475ae544349196b9170eb9939dbdcfe78a4c88d38fd532f` |

Les ZIP XML/golden/résumé/différentiel ont été téléchargés, leurs digests contrôlés et leur contenu analysé. Les artifacts natifs sont collectés dans l’attestation finale. Le golden de 2 003 octets a SHA-256 `2762b877e60538cbaa455b8bc46016aa9c589a3b8c837515b0dbd0e92862a00a` ; son body fait 1 447 octets UTF-8, digest `ebae3544f6c4a49a5e118600d0c4c928501add5765c565f0ea964b637cd1cb62`. Il provient du véritable encodeur Cloud et passe le véritable adaptateur Package puis BannerInstallationHandler de Phase C, avec ASCII, accents composés/décomposés, emoji, groupe, texte et tableau.

Les 19 tests couvrent les deux Video, reprise SAME, STALE avant handler, failures commit/ARM/ACK, lifetime, fermeture outer/ACK, unsafe revisions, digests et corps invalides, bijection manifest/windows, cache corrompu, remplacement Video→Banner→Video et restauration offline fraîche. Les seules doublures remplacent les ports réseau/durabilité/native ; elles ne remplacent pas le parser, l’installer, les handlers ou la génération de preuve.

## Provenance finie

La baseline D est nouvelle. Elle contient les 316 blobs de départ et leur tree, les 9 ajouts littéraux, ainsi que **20 admissions existantes** complètes before/after et inverses exacts. SHA-256 canonique de l’inventaire de départ : `32e4e625b384deca2ea2da7c36c12d9a479de340bc37115630e90ce93c101ec0`. Les paires de blobs sont aussi des constantes indépendantes dans le helper D. Les baselines JSON historiques restent inchangées.

La reconstruction est D → C → B → M5 → M4. Les gates anciens exécutent leurs contrôles historiques sur les bytes restitués ; le gate D vérifie séparément les sources actuelles, le scope fermé et les pins. Les contrôles négatifs rejettent fichier non inventorié, retarget de baseline, mutation de source et inverse altéré. Les 12 gates d’inventaire anciens ajoutent seulement les paths D littéraux. Le gate F conserve ses assertions HTTP/auth et l’unique appel d’installation ; Package emprunte cet appel commun.

Les changements de provenance et workflows sont limités aux chemins inventoriés. Les méthodes historiques protégées, notamment HTTP Video v1 et authentification, se reconstruisent octet par octet avant D. Aucun wildcard, union ouverte, rewrite d’oracle ou ajout de skip.

## Autorité et rollback

La paire Cloud qualifiée alloue toutes les révisions sur DeviceAssignment sous le même device lock. Video et son mirror legacy sont atomiques ; Banner conserve l’ancien mirror Video inutilisé. Les anciennes façades ne livrent ni n’ACKent ce mirror lorsque Banner est courant. Le retour Video alloue N+1 et restaure un mirror exact, tout en conservant le marqueur durable d’autorité M6 de la publication.

Les writers SHADOW et l’ancien CUTOVER Video de cette paire refusent un device marqué M6. Le backfill s’arrête sur Banner ; après retour à Video avec parité exacte, il peut constater already-present sans remplacer la publication ni retirer le marqueur. Une incohérence bootstrap/parité s’arrête sans écrire.

Un ancien binaire antérieur à ces gardes ne sait pas reconnaître le marqueur. Une activation live future exige le drainage ou la mise à niveau de tous les writers avant premier Send M6. Revenir naïvement à cet ancien binaire/allocateur SHADOW après une révision M6 est interdit, même après retour à Video. Le rollback qualifié est l’arrêt fail-closed des façades gardées ; il ne remet pas à zéro des compteurs et ne réécrit aucune ACK. Aucune migration ou table nouvelle n’est nécessaire pour cette Phase D limitée à la qualification.
