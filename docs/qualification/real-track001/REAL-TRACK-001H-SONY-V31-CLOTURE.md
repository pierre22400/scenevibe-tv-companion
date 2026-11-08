# SCENEVIBE — REAL TRACK E2E 001-H — clôture physique Sony v31

**Date : 8 novembre 2026 — statut : PASS PHYSIQUE FONCTIONNEL (6/6), déclaré par le propriétaire.**

**Portée :** chaîne privée Studio → Cloud Preview → cache du Companion Sony BRAVIA → restitution TEXT/AUDIO pendant Prime Video. Cette clôture n'est **ni** une recette générale de toutes les plateformes/éditions, **ni** une autorisation de fusion, de déploiement Production ou de publication publique.

## 1. Décision et provenance des observations

Le propriétaire a confirmé sur la Sony BRAVIA VH1, sous Prime Video et sur l'épisode **Columbo — Eaux troubles**, une restitution effective et comptabilisée des **3 commentaires TEXT et 3 commentaires AUDIO**, soit **6 événements sur 6**, pour la Track privée **révision 4** issue de Studio.

Après redémarrage physique de la télévision, le propriétaire a de nouveau confirmé **PASS** pour la reprise du fonctionnement. Cette confirmation atteste la continuité observée après reboot ; **une seconde comptabilisation séparée des six événements après redémarrage n'a pas été consignée**. Ne pas la fabriquer.

Ce sont des **constats manuels du propriétaire dans ChatGPT le 8 novembre 2026**, pas des traces instrumentées Prime/Sony collectées automatiquement. La CI Android utilise une MediaSession et des médias **détenus par le banc** ; elle ne prouve pas, à elle seule, le fonctionnement sur Prime.

| Vérification physique déclarée | Résultat |
|---|---|
| Track privée révision 4 distribuée et cache local rechargé | PASS |
| Mise à jour Sony v30 → v31 avec `adb install -r` | PASS |
| VersionCode du Companion principal après mise à jour | **31** |
| Companion Netflix expérimental indépendant | **20**, inchangé |
| Session Prime Video reconnue et restitution sur l'épisode | PASS |
| TEXT, événements individuellement comptabilisés | **3/3 PASS** |
| AUDIO, événements individuellement comptabilisés | **3/3 PASS** |
| Redémarrage physique Sony, reprise observée | **PASS déclaré** |

Avant le lancement de Prime, l'UI indiquait `MEDIA_UNAVAILABLE` / « En attente d'une session MEDIA reconnue » et « TEXT : ON — session armée » : **état d'attente normal**, non assimilé à un échec. Le cache privé était rechargé. En lecture Prime, le propriétaire a ensuite confirmé les six restitutions.

## 2. Régression v30, correctif v31 et limites

Sur v30, le Companion refusait la Track Studio réelle avec `MEDIA_IDENTITY_BLOCKED` / « Épisode non reconnu ». La cause technique identifiée était l'admission de provenance qui acceptait l'ancien préfixe `studio-manual:` mais pas l'identité réellement distribuée `studio-imported:`.

La branche v31 adapte ce chemin d'admission et distingue un refus de source (`CLOUD_SOURCE_ADMISSION_BLOCKED`) d'un véritable échec d'identité MEDIA. Le **PASS physique 6/6** établit que le parcours utilisateur concerné n'est plus bloqué dans cette configuration. Il ne démontre ni une reconnaissance universelle de tous les épisodes ni une compatibilité avec toutes les éditions ou plateformes.

Aucun effacement des données n'a été demandé. Le cache et l'association ont été conservés selon le retour utilisateur. La sauvegarde locale de l'APK v30 a été réalisée **avant** installation, sous le nom `scenevibe-v30-sony-backup-20261108-134129.apk` ; elle n'est ni copiée ni publiée ici. Une sauvegarde APK ne contient pas à elle seule les données privées d'application.

## 3. Références figées et provenance technique

| Élément | Référence |
|---|---|
| Dépôt TV | [scenevibe-tv-companion](https://github.com/pierre22400/scenevibe-tv-companion) |
| PR TV | [#40 — Draft](https://github.com/pierre22400/scenevibe-tv-companion/pull/40) |
| Branche source | `work/scenevibe-real-track-tv-001` |
| **Commit fonctionnel/CI figé pour le candidat v31** | [`1d642e70d1559e6d753476945dcb749036dc4266`](https://github.com/pierre22400/scenevibe-tv-companion/commit/1d642e70d1559e6d753476945dcb749036dc4266) |
| Qualification CI correspondante | [GitHub Actions #42480014146](https://github.com/pierre22400/scenevibe-tv-companion/actions/runs/42480014146), **SUCCESS**, HEAD exact `1d642e70...` |
| Archive APK signée et rapports | [Artefact #13617051785](https://github.com/pierre22400/scenevibe-tv-companion/actions/runs/42480014146/artifacts/13617051785) |
| Journaux / preuves natifs | [Artefact #13617591285](https://github.com/pierre22400/scenevibe-tv-companion/actions/runs/42480014146/artifacts/13617591285) |
| Contrôles Windows | [Artefact #13617335351](https://github.com/pierre22400/scenevibe-tv-companion/actions/runs/42480014146/artifacts/13617335351) |
| APK dans l'archive | `tv-ux002-signed/scenevibe-real-track001-v31-stable.apk` |
| Package / versionCode | `com.scenevibe.tvcompanionpoc` / **31** |
| SHA-256 APK | `98AC6B74F71F02CBB52BD09998565012C864EC61AEB0A094D610DE614283710F` |
| Certificat de signature M5, SHA-256 attendu vérifié par la CI | `f908bf564ed97ba67e02b1ebc89eb0239cf980752587f55eb9ec0419791a2e9c` |
| PR Cloud associée | [interface-scenevibe #52 — Draft](https://github.com/pierre22400/interface-scenevibe/pull/52) |
| Branche Cloud | `work/scenevibe-real-track-e2e-001` |

L'empreinte SHA-256 ci-dessus a été **comparée sur le PC Windows avant installation par le propriétaire, verdict PASS**. La CI avait également effectué une signature et une vérification du certificat M5. Les artefacts du workflow ont une rétention annoncée au **22 novembre 2026** ; préserver les copies nécessaires avant expiration.

Ce commit v31 est la **base logicielle qualifiée**. Les commits documentaires de cette clôture ne doivent pas être confondus avec une nouvelle compilation ni un changement de binaire.

## 4. Résultats automatisés sur le commit v31 figé

[Workflow #42480014146](https://github.com/pierre22400/scenevibe-tv-companion/actions/runs/42480014146) : jobs `software`, `native` et `windows_capture` tous **SUCCESS** le 8 novembre 2026.

| Sous-campagne | Observation CI |
|---|---|
| Tests Android TV historiques + navigation G11 | **52/52 PASS** |
| Deux phases de reprise du cache en processus/redémarrage émulateur | **2/2 PASS** |
| Tests natifs de la Track manuelle (admission, cache, restitution) | **3/3 PASS** |
| Smoke natif sans Track | **1/1 PASS** |
| Tests logiciels, Robolectric, contrôles HTTP/Chromium et Windows | **PASS** |
| Compilation/lint candidat v31 et variantes historiques conservées | **PASS** |
| Signature du candidat avec clé historique M5 et contrôle de certificat | **PASS** |

Historique récent, sans dissimuler les échecs :
- [#42475246553](https://github.com/pierre22400/scenevibe-tv-companion/actions/runs/42475246553) : **51/52** natifs, échec prématuré du test de disponibilité de relecture locale asynchrone.
- [#42479156255](https://github.com/pierre22400/scenevibe-tv-companion/actions/runs/42479156255) : **52/52** natifs, mais job FAIL à cause du script de CI qui exigeait encore exactement 51 cas.
- [#42480014146](https://github.com/pierre22400/scenevibe-tv-companion/actions/runs/42480014146) : **PASS intégral** après attente bornée du worker dans le test et adaptation du compteur CI, conservant l'exigence historique 51 et l'exigence REAL TRACK 52.

Les changements correctifs finaux portent uniquement sur `RecoveryCacheAndroidTest.java` et `.github/scripts/tv-ux002-native.sh` depuis `77c95332e2fad8fa6f4da133bb272ca100250ae6`. Les assertions métier et les chemins de production ne sont pas supprimés pour obtenir le vert.

## 5. Frontières et décision de sortie

**Clôturé :** qualification fonctionnelle réelle de cette Track privée et de ses six commentaires, sur cette Sony, cet épisode Prime et la v31, plus confirmation de reprise après reboot.

**Non qualifié par cette clôture :**
- Toutes les plateformes (Netflix/Disney+), toutes les éditions et tous les épisodes ;
- Installation grand public sans ADB, première association sans assistance et parcours autonome de bout en bout ;
- Une deuxième Track indépendante, représentativité statistique, endurance prolongée et gestion exhaustive des publicités ;
- Fusion vers `main`, déploiement Production, publication publique ou nouvelle migration SQL/R2 ;
- Un journal instrumenté post-reboot attestant une seconde restitution détaillée 6/6.

**Décision : référence technique v31 conservée ; PR TV #40 et Cloud #52 maintenues en Draft ; aucune fusion, installation supplémentaire, mise à jour de secrets ou intervention fournisseur dans cette clôture documentaire.**

**Prochaine porte produit recommandée :** parcours TV autonome sans PC/ADB, puis une seconde Track réelle indépendante avant bêta fermée.

## 6. Règles de conservation

Conserver l'archive signée, son SHA-256, les preuves CI et les métadonnées minimales de qualification. Ne jamais publier d'IP locale, d'ID de device, de token, d'URL à secrets, de HMAC, de cache multimédia privé ou d'audio personnel. Ne pas remplacer rétroactivement les anciens rapports v29/v30 : ils décrivent leur époque ; le présent addendum porte le verdict final v31.
